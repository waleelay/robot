import { Room, RoomEvent, Track } from 'livekit-client';
import { Message } from 'element-ui';
import { resolveLiveKitUrl } from '../../utils/livekitUrl';

function cameraKeyForCall(callId) {
  return `field-call:${callId}`;
}

const state = {
  // 并入大屏 /ws/bigscreen 后始终启用；来电由 Control 经 BFF 推送
  enabled: true,
  connected: false,
  incomingCalls: [],
  activeIncomingCall: null,
  callOperationPending: false,
  /** @type {Record<string, any>} */
  sessions: {}
};

const getters = {
  fieldCallEnabled: s => s.enabled,
  fieldCallConnected: s => s.connected,
  fieldIncomingCalls: s => s.incomingCalls,
  fieldActiveIncomingCall: s => s.activeIncomingCall,
  fieldCallOperationPending: s => s.callOperationPending,
  fieldRemoteVideoTrack: s => {
    const active = s.activeIncomingCall;
    if (!active) return null;
    return (s.sessions[active.callId] && s.sessions[active.callId].remoteVideoTrack) || null;
  },
  fieldSession: s => {
    const active = s.activeIncomingCall;
    if (!active) return null;
    return s.sessions[active.callId] || null;
  }
};

const mutations = {
  SET_CONNECTED(s, connected) {
    s.connected = connected;
  },
  SET_OPERATION_PENDING(s, pending) {
    s.callOperationPending = pending;
  },
  UPSERT_INCOMING(s, call) {
    const list = s.incomingCalls.filter(item => item.callId !== call.callId);
    s.incomingCalls = [...list, call];
  },
  REMOVE_INCOMING(s, callId) {
    s.incomingCalls = s.incomingCalls.filter(item => item.callId !== callId);
  },
  SET_INCOMING(s, calls) {
    s.incomingCalls = Array.isArray(calls) ? calls : [];
  },
  SET_ACTIVE(s, call) {
    s.activeIncomingCall = call;
  },
  UPDATE_ACTIVE(s, patch) {
    if (!s.activeIncomingCall) return;
    s.activeIncomingCall = { ...s.activeIncomingCall, ...patch };
  },
  SET_SESSION(s, { callId, session }) {
    s.sessions = { ...s.sessions, [callId]: session };
  },
  PATCH_SESSION(s, { callId, patch }) {
    const prev = s.sessions[callId] || {};
    s.sessions = { ...s.sessions, [callId]: { ...prev, ...patch }};
  },
  CLEAR_SESSION(s, callId) {
    if (!s.sessions[callId]) return;
    const next = { ...s.sessions };
    delete next[callId];
    s.sessions = next;
  }
};

const actions = {
  /** 保留空实现，兼容 Bi.vue；实际连接复用 websocketRobot 媒体通道 */
  connectFieldCall({ commit, rootState }) {
    const socket = rootState.websocketRobot && rootState.websocketRobot.mediaSocket;
    commit('SET_CONNECTED', Boolean(socket && socket.readyState === WebSocket.OPEN));
  },

  disconnectFieldCall({ state, commit, dispatch }) {
    if (state.activeIncomingCall) {
      dispatch('hangupFieldCall');
    }
    commit('SET_CONNECTED', false);
    commit('SET_INCOMING', []);
  },

  syncFieldCallEvent({ commit, dispatch, state }, event) {
    if (!event) return;
    if (event.type === 'video.field.call.list') {
      const list = (Array.isArray(event.payload) ? event.payload : []).map(normalizeIncoming);
      commit('SET_INCOMING', list);
      return;
    }
    if (event.event === 'video.field.call.incoming' && event.data) {
      commit('UPSERT_INCOMING', normalizeIncoming(event.data));
      return;
    }
    if (event.event === 'video.field.call.status' && event.data) {
      if (event.data.status === 'RINGING') {
        commit('UPSERT_INCOMING', normalizeIncoming(event.data));
      } else {
        commit('REMOVE_INCOMING', event.data.callId);
      }
      if (state.activeIncomingCall &&
          state.activeIncomingCall.callId === event.data.callId &&
          ['ENDED', 'FAILED', 'REJECTED', 'TIMEOUT', 'CANCELED'].includes(event.data.status)) {
        dispatch('cleanupFieldSession', event.data.callId);
        commit('SET_ACTIVE', null);
      }
      return;
    }
    if (event.type === 'video.field.call.accepted' && event.payload) {
      commit('SET_OPERATION_PENDING', false);
      commit('REMOVE_INCOMING', event.payload.call && event.payload.call.callId);
      dispatch('activateFieldCall', event.payload);
      return;
    }
    if (event.type === 'video.field.call.rejected') {
      commit('SET_OPERATION_PENDING', false);
      const callId = event.payload && (event.payload.callId || (event.payload.call && event.payload.call.callId));
      if (callId) commit('REMOVE_INCOMING', callId);
      return;
    }
    if (event.type === 'video.field.call.ended') {
      commit('SET_OPERATION_PENDING', false);
      const callId = event.payload && event.payload.callId;
      if (callId) {
        dispatch('cleanupFieldSession', callId);
        if (state.activeIncomingCall && state.activeIncomingCall.callId === callId) {
          commit('SET_ACTIVE', null);
        }
        commit('REMOVE_INCOMING', callId);
      }
      return;
    }
    if (event.type === 'video.field.call.operation-failed') {
      const shouldNotify = state.callOperationPending;
      commit('SET_OPERATION_PENDING', false);
      if (shouldNotify) {
        Message.error((event.payload && event.payload.message) || '现场呼叫操作失败');
      }
    }
  },

  sendFieldViaMedia({ rootState }, { type, callId }) {
    const socket = rootState.websocketRobot && rootState.websocketRobot.mediaSocket;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      Message.error('控制通道未连接');
      return false;
    }
    socket.send(JSON.stringify({
      type,
      requestId: `field-${Date.now()}`,
      payload: { callId }
    }));
    return true;
  },

  acceptFieldCall({ commit, dispatch }, callId) {
    commit('SET_OPERATION_PENDING', true);
    dispatch('sendFieldViaMedia', { type: 'video.field.call.accept', callId });
  },

  rejectFieldCall({ commit, dispatch }, callId) {
    commit('SET_OPERATION_PENDING', true);
    dispatch('sendFieldViaMedia', { type: 'video.field.call.reject', callId });
    commit('REMOVE_INCOMING', callId);
    commit('SET_OPERATION_PENDING', false);
  },

  async activateFieldCall({ commit, dispatch }, payload) {
    const callRaw = payload.call || {};
    const sessionInfo = payload.session || {};
    const callId = callRaw.callId || payload.callId;
    const call = {
      ...normalizeIncoming(callRaw),
      callId,
      sessionId: sessionInfo.roomName || callRaw.roomName,
      videoEnabled: false,
      videoLoading: false,
      micMuted: false,
      speakerMuted: false,
      connectedAtEpochMillis: Date.now()
    };
    commit('SET_ACTIVE', call);
    try {
      await dispatch('connectFieldLiveKit', {
        callId,
        livekitUrl: sessionInfo.livekitUrl,
        token: sessionInfo.token
      });
      commit('UPDATE_ACTIVE', { videoLoading: false });
    } catch (err) {
      console.error('[fieldCall] livekit', err);
      Message.error(err.message || '现场通话连接失败');
      commit('UPDATE_ACTIVE', { videoLoading: false });
      dispatch('hangupFieldCall');
    }
  },

  async connectFieldLiveKit({ commit, state }, { callId, livekitUrl, token }) {
    if (!livekitUrl || !token) {
      throw new Error('缺少 LiveKit 地址或 Token');
    }
    const room = new Room();
    const session = {
      room,
      remoteVideoTrack: null,
      remoteAudioTrack: null,
      remoteAudioElement: null
    };
    commit('SET_SESSION', { callId, session });

    const attachRemoteVideo = (track) => {
      if (!track || (track.kind !== Track.Kind.Video && track.kind !== 'video')) return;
      commit('PATCH_SESSION', {
        callId,
        patch: { remoteVideoTrack: track }
      });
      const active = state.activeIncomingCall;
      if (active && active.callId === callId && active.videoEnabled) {
        commit('UPDATE_ACTIVE', { videoLoading: false });
      }
    };

    const attachRemoteAudio = (track) => {
      if (!track || (track.kind !== Track.Kind.Audio && track.kind !== 'audio')) return;
      const existing = state.sessions[callId];
      if (existing && existing.remoteAudioElement && existing.remoteAudioTrack === track) return;
      if (existing && existing.remoteAudioElement && existing.remoteAudioElement.parentNode) {
        existing.remoteAudioElement.parentNode.removeChild(existing.remoteAudioElement);
      }
      const el = track.attach();
      el.autoplay = true;
      el.dataset.fieldCallId = callId;
      document.body.appendChild(el);
      commit('PATCH_SESSION', {
        callId,
        patch: { remoteAudioTrack: track, remoteAudioElement: el }
      });
    };

    room.on(RoomEvent.TrackSubscribed, (track) => {
      attachRemoteVideo(track);
      attachRemoteAudio(track);
    });
    room.on(RoomEvent.TrackUnsubscribed, (track) => {
      if (typeof track.detach === 'function') track.detach();
      if (track.kind === Track.Kind.Video || track.kind === 'video') {
        commit('PATCH_SESSION', { callId, patch: { remoteVideoTrack: null }});
      }
    });

    await room.connect(resolveLiveKitUrl(livekitUrl), token);
    await room.localParticipant.setMicrophoneEnabled(true, {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true
    });

    room.remoteParticipants.forEach((participant) => {
      participant.trackPublications.forEach((publication) => {
        if (publication.track) {
          attachRemoteVideo(publication.track);
          attachRemoteAudio(publication.track);
        }
      });
    });
  },

  async hangupFieldCall({ state, commit, dispatch }) {
    const active = state.activeIncomingCall;
    if (!active) return;
    dispatch('sendFieldViaMedia', { type: 'video.field.call.hangup', callId: active.callId });
    await dispatch('cleanupFieldSession', active.callId);
    commit('SET_ACTIVE', null);
    commit('REMOVE_INCOMING', active.callId);
  },

  async cleanupFieldSession({ state, commit }, callId) {
    const session = state.sessions[callId];
    if (!session) return;
    try {
      if (session.room && session.room.localParticipant) {
        await session.room.localParticipant.setMicrophoneEnabled(false).catch(() => {});
      }
      if (session.remoteVideoTrack && typeof session.remoteVideoTrack.detach === 'function') {
        session.remoteVideoTrack.detach();
      }
      if (session.remoteAudioTrack && typeof session.remoteAudioTrack.detach === 'function') {
        session.remoteAudioTrack.detach();
      }
      if (session.remoteAudioElement && session.remoteAudioElement.parentNode) {
        session.remoteAudioElement.parentNode.removeChild(session.remoteAudioElement);
      }
      if (session.room) await session.room.disconnect();
    } catch (err) {
      console.error('[fieldCall] cleanup', err);
    }
    commit('CLEAR_SESSION', callId);
  },

  async toggleFieldMic({ state, commit }) {
    const active = state.activeIncomingCall;
    const session = active && state.sessions[active.callId];
    if (!active || !session || !session.room) return;
    const muted = !active.micMuted;
    await session.room.localParticipant.setMicrophoneEnabled(!muted);
    commit('UPDATE_ACTIVE', { micMuted: muted });
  },

  async toggleFieldSpeaker({ state, commit }) {
    const active = state.activeIncomingCall;
    const session = active && state.sessions[active.callId];
    if (!active || !session) return;
    const muted = !active.speakerMuted;
    if (session.remoteAudioElement) session.remoteAudioElement.muted = muted;
    commit('UPDATE_ACTIVE', { speakerMuted: muted });
  },

  enableFieldVideo({ state, commit }) {
    const active = state.activeIncomingCall;
    if (!active) return;
    const session = state.sessions[active.callId];
    const hasTrack = Boolean(session && session.remoteVideoTrack);
    commit('UPDATE_ACTIVE', {
      videoEnabled: true,
      videoLoading: !hasTrack
    });
  },

  disableFieldVideo({ state, commit }) {
    const active = state.activeIncomingCall;
    if (!active) return;
    commit('UPDATE_ACTIVE', { videoEnabled: false, videoLoading: false });
  }
};

function normalizeIncoming(call) {
  return {
    ...call,
    source: 'mobile-app',
    cameraKey: cameraKeyForCall(call.callId),
    reason: call.reason || '现场 App 邀请你进行视频通话'
  };
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions
};
