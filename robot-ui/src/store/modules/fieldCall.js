import { Room, RoomEvent, Track } from 'livekit-client';
import { Message } from 'element-ui';
import { resolveLiveKitUrl } from '../../utils/livekitUrl';
import {
  connectRoomWithCancellation,
  disconnectRoomSafely,
  enableLocalMicrophone,
  releaseLocalMicrophone
} from '../../utils/livekit-local-media';
import { IntercomOperationRegistry } from '../../utils/intercom-operation-registry';
import { mediaCallCoordinator } from '../../utils/media-call-coordinator';
import { CallTerminalRegistry } from '../../utils/call-terminal-registry';

const fieldCallStartOperations = new IntercomOperationRegistry();
const fieldCallLeases = new Map();
const fieldCallAcceptTimers = new Map();
const CALL_ACCEPT_TIMEOUT_MS = 15000;
const CALL_TERMINAL_RETENTION_MS = CALL_ACCEPT_TIMEOUT_MS * 4;
const FIELD_CALL_TERMINAL_STATUSES = ['ENDED', 'FAILED', 'REJECTED', 'TIMEOUT', 'CANCELED'];
const fieldCallTerminals = new CallTerminalRegistry(CALL_TERMINAL_RETENTION_MS);

function fieldOperationKey(callId) {
  return `field:${callId}`;
}

function isCurrentSession(state, callId, session) {
  return Boolean(session && state.sessions[callId] === session);
}

async function disposeFieldSession(session) {
  if (!session || session.disposed) return;
  session.disposed = true;
  await releaseLocalMicrophone(session.room);
  if (session.remoteVideoTrack && typeof session.remoteVideoTrack.detach === 'function') {
    try { session.remoteVideoTrack.detach(); } catch (err) { console.warn('[fieldCall] detach video', err); }
  }
  if (session.remoteAudioTrack && typeof session.remoteAudioTrack.detach === 'function') {
    try { session.remoteAudioTrack.detach(); } catch (err) { console.warn('[fieldCall] detach audio', err); }
  }
  if (session.remoteAudioElement && session.remoteAudioElement.parentNode) {
    try { session.remoteAudioElement.parentNode.removeChild(session.remoteAudioElement); } catch (err) {
      console.warn('[fieldCall] remove audio element', err);
    }
  }
  if (session.room) {
    await disconnectRoomSafely(session.room, { context: '现场呼叫 LiveKit Room' });
  }
}

function releaseFieldCallLease(callId) {
  const timer = fieldCallAcceptTimers.get(callId);
  if (timer) clearTimeout(timer);
  fieldCallAcceptTimers.delete(callId);
  const lease = fieldCallLeases.get(callId);
  if (lease) mediaCallCoordinator.release(lease);
  fieldCallLeases.delete(callId);
}

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
    const prev = s.sessions[callId];
    if (!prev) return;
    Object.assign(prev, patch);
    s.sessions = { ...s.sessions, [callId]: prev };
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

  async disconnectFieldCall({ state, commit, dispatch }) {
    const activeCallId = state.activeIncomingCall && state.activeIncomingCall.callId;
    const pendingCallIds = new Set([
      ...fieldCallLeases.keys(),
      ...Object.keys(state.sessions)
    ]);
    if (activeCallId) pendingCallIds.add(activeCallId);
    // 退出态先落地，保证后续清理期间到达的 accepted 只会触发远端收口。
    commit('SET_CONNECTED', false);
    fieldCallTerminals.markAll(pendingCallIds, 'page-leave');
    pendingCallIds.forEach(callId => {
      if (callId !== activeCallId) {
        dispatch('sendFieldViaMedia', { type: 'video.field.call.hangup', callId, silent: true });
      }
    });
    if (state.activeIncomingCall) {
      await dispatch('hangupFieldCall');
    }
    await Promise.all([...fieldCallStartOperations.keys()]
      .map(key => fieldCallStartOperations.cancel(key, 'disconnect')));
    // 活动通话之外仍可能存在连接失败或迟到事件留下的 session，全部幂等清理。
    await Promise.all(Object.keys(state.sessions).map(callId => dispatch('cleanupFieldSession', callId)));
    [...fieldCallLeases.keys()].forEach(releaseFieldCallLease);
    commit('SET_ACTIVE', null);
    commit('SET_OPERATION_PENDING', false);
    commit('SET_INCOMING', []);
  },

  syncFieldCallEvent({ commit, dispatch, state }, event) {
    if (!event) return;
    if (event.type === 'video.field.call.list') {
      const list = (Array.isArray(event.payload) ? event.payload : [])
        .filter(call => !fieldCallTerminals.has(call.callId))
        .map(normalizeIncoming);
      commit('SET_INCOMING', list);
      return;
    }
    if (event.event === 'video.field.call.incoming' && event.data) {
      if (fieldCallTerminals.has(event.data.callId)) return;
      commit('UPSERT_INCOMING', normalizeIncoming(event.data));
      return;
    }
    if (event.event === 'video.field.call.status' && event.data) {
      const terminal = FIELD_CALL_TERMINAL_STATUSES.includes(event.data.status);
      if (terminal) fieldCallTerminals.mark(event.data.callId, event.data.status);
      if (event.data.status === 'RINGING' && !fieldCallTerminals.has(event.data.callId)) {
        commit('UPSERT_INCOMING', normalizeIncoming(event.data));
      } else {
        commit('REMOVE_INCOMING', event.data.callId);
      }
      if (terminal) {
        dispatch('cleanupFieldSession', event.data.callId);
        if (state.activeIncomingCall && state.activeIncomingCall.callId === event.data.callId) {
          commit('SET_ACTIVE', null);
        }
      }
      return;
    }
    if (event.type === 'video.field.call.accepted' && event.payload) {
      commit('SET_OPERATION_PENDING', false);
      const callId = event.payload.call && event.payload.call.callId;
      if (!callId) return;
      commit('REMOVE_INCOMING', callId);
      if (!state.connected || fieldCallTerminals.has(callId)) {
        fieldCallTerminals.mark(callId, state.connected ? 'late-accepted' : 'page-inactive');
        dispatch('sendFieldViaMedia', { type: 'video.field.call.hangup', callId, silent: true });
        releaseFieldCallLease(callId);
        return;
      }
      if ((state.activeIncomingCall && state.activeIncomingCall.callId === callId) ||
          fieldCallStartOperations.has(fieldOperationKey(callId))) return;
      dispatch('activateFieldCall', event.payload);
      return;
    }
    if (event.type === 'video.field.call.rejected') {
      commit('SET_OPERATION_PENDING', false);
      const callId = event.payload && (event.payload.callId || (event.payload.call && event.payload.call.callId));
      if (callId) {
        fieldCallTerminals.mark(callId, 'rejected');
        commit('REMOVE_INCOMING', callId);
        releaseFieldCallLease(callId);
      }
      return;
    }
    if (event.type === 'video.field.call.ended') {
      commit('SET_OPERATION_PENDING', false);
      const callId = event.payload && event.payload.callId;
      if (callId) {
        fieldCallTerminals.mark(callId, 'ended');
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
      const callId = event.payload && event.payload.callId;
      if (callId) {
        fieldCallTerminals.mark(callId, 'operation-failed');
        releaseFieldCallLease(callId);
      }
    }
  },

  sendFieldViaMedia({ rootState }, { type, callId, silent = false }) {
    const socket = rootState.websocketRobot && rootState.websocketRobot.mediaSocket;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      if (!silent) Message.error('控制通道未连接');
      return false;
    }
    socket.send(JSON.stringify({
      type,
      requestId: `field-${Date.now()}`,
      payload: { callId }
    }));
    return true;
  },

  async acceptFieldCall({ commit, dispatch }, callId) {
    const lease = mediaCallCoordinator.acquire(fieldOperationKey(callId), 'field-call');
    if (!lease) {
      Message.warning('当前正在通话，请先结束当前通话');
      return;
    }
    fieldCallLeases.set(callId, lease);
    fieldCallAcceptTimers.set(callId, setTimeout(() => {
      if (fieldCallLeases.get(callId) !== lease || fieldCallStartOperations.has(fieldOperationKey(callId))) return;
      fieldCallTerminals.mark(callId, 'accept-timeout');
      releaseFieldCallLease(callId);
      commit('SET_OPERATION_PENDING', false);
      Message.error('现场呼叫接听超时，请重试');
    }, CALL_ACCEPT_TIMEOUT_MS));
    commit('SET_OPERATION_PENDING', true);
    const sent = await dispatch('sendFieldViaMedia', { type: 'video.field.call.accept', callId });
    if (sent === false) {
      commit('SET_OPERATION_PENDING', false);
      releaseFieldCallLease(callId);
    }
  },

  rejectFieldCall({ commit, dispatch }, callId) {
    fieldCallTerminals.mark(callId, 'local-reject');
    releaseFieldCallLease(callId);
    commit('SET_OPERATION_PENDING', true);
    dispatch('sendFieldViaMedia', { type: 'video.field.call.reject', callId });
    commit('REMOVE_INCOMING', callId);
    commit('SET_OPERATION_PENDING', false);
  },

  async activateFieldCall({ commit, dispatch, state }, payload) {
    const callRaw = payload.call || {};
    const sessionInfo = payload.session || {};
    const callId = callRaw.callId || payload.callId;
    const operationKey = fieldOperationKey(callId);
    if (fieldCallStartOperations.has(operationKey)) return;
    const lease = fieldCallLeases.get(callId) || mediaCallCoordinator.acquire(operationKey, 'field-call');
    const acceptTimer = fieldCallAcceptTimers.get(callId);
    if (acceptTimer) clearTimeout(acceptTimer);
    fieldCallAcceptTimers.delete(callId);
    const operation = lease && fieldCallStartOperations.begin(operationKey);
    if (!operation) {
      if (lease) mediaCallCoordinator.release(lease);
      dispatch('sendFieldViaMedia', { type: 'video.field.call.hangup', callId });
      Message.warning('当前正在通话，请先结束当前通话');
      return;
    }
    fieldCallLeases.set(callId, lease);
    const call = {
      ...normalizeIncoming(callRaw),
      callId,
      sessionId: sessionInfo.roomName || callRaw.roomName,
      // 现场视频呼叫接听后直接进视频态，不展示装备类型图标的音频条。
      videoEnabled: true,
      videoLoading: true,
      micMuted: false,
      speakerMuted: false,
      connectedAtEpochMillis: Date.now()
    };
    commit('SET_ACTIVE', call);
    try {
      await dispatch('connectFieldLiveKit', {
        callId,
        livekitUrl: sessionInfo.livekitUrl,
        token: sessionInfo.token,
        operation
      });
      if (operation.cancelled) throw new Error('对讲启动已取消');
      mediaCallCoordinator.activate(lease);
      const session = state.sessions && state.sessions[callId];
      const hasTrack = Boolean(session && session.remoteVideoTrack);
      commit('UPDATE_ACTIVE', { videoLoading: !hasTrack });
    } catch (err) {
      fieldCallTerminals.mark(callId, 'activation-failed');
      console.error('[fieldCall] livekit', err);
      dispatch('sendFieldViaMedia', { type: 'video.field.call.hangup', callId });
      if (state.activeIncomingCall && state.activeIncomingCall.callId === callId) {
        commit('SET_ACTIVE', null);
      }
      commit('REMOVE_INCOMING', callId);
      releaseFieldCallLease(callId);
      if (!operation.cancelled) Message.error(err.message || '现场通话连接失败');
    } finally {
      fieldCallStartOperations.finish(operationKey, operation);
    }
  },

  async connectFieldLiveKit({ commit, dispatch, state }, { callId, livekitUrl, token, operation }) {
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
      if (!isCurrentSession(state, callId, session) || session.disposed) return;
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
      if (!isCurrentSession(state, callId, session) || session.disposed) return;
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
      if (!isCurrentSession(state, callId, session) || session.disposed) return;
      if (typeof track.detach === 'function') track.detach();
      if (track.kind === Track.Kind.Video || track.kind === 'video') {
        commit('PATCH_SESSION', { callId, patch: { remoteVideoTrack: null }});
      }
    });
    room.on(RoomEvent.Disconnected, () => {
      if (!isCurrentSession(state, callId, session) || session.disposed) return;
      dispatch('cleanupFieldSession', callId);
      if (state.activeIncomingCall && state.activeIncomingCall.callId === callId) {
        commit('SET_ACTIVE', null);
      }
    });

    try {
      await connectRoomWithCancellation(room, resolveLiveKitUrl(livekitUrl), token, operation);
      await enableLocalMicrophone(room, operation);
      if (!isCurrentSession(state, callId, session)) throw new Error('对讲启动已取消');

      room.remoteParticipants.forEach((participant) => {
        participant.trackPublications.forEach((publication) => {
          if (publication.track) {
            attachRemoteVideo(publication.track);
            attachRemoteAudio(publication.track);
          }
        });
      });
    } catch (error) {
      await disposeFieldSession(session);
      if (isCurrentSession(state, callId, session)) commit('CLEAR_SESSION', callId);
      throw error;
    }
  },

  async hangupFieldCall({ state, commit, dispatch }) {
    const active = state.activeIncomingCall;
    if (!active) return;
    fieldCallTerminals.mark(active.callId, 'local-hangup');
    dispatch('sendFieldViaMedia', { type: 'video.field.call.hangup', callId: active.callId });
    await dispatch('cleanupFieldSession', active.callId);
    if (state.activeIncomingCall && state.activeIncomingCall.callId === active.callId) {
      commit('SET_ACTIVE', null);
    }
    commit('REMOVE_INCOMING', active.callId);
  },

  async cleanupFieldSession({ state, commit }, callId) {
    fieldCallTerminals.mark(callId, 'cleanup');
    await fieldCallStartOperations.cancel(fieldOperationKey(callId), 'lifecycle');
    const session = state.sessions[callId];
    if (session) {
      await disposeFieldSession(session);
      if (isCurrentSession(state, callId, session)) commit('CLEAR_SESSION', callId);
    }
    releaseFieldCallLease(callId);
  },

  async toggleFieldMic({ state, commit }) {
    const active = state.activeIncomingCall;
    const session = active && state.sessions[active.callId];
    if (!active || !session || !session.room) return;
    const muted = !active.micMuted;
    try {
      if (muted) {
        await session.room.localParticipant.setMicrophoneEnabled(false);
      } else {
        await enableLocalMicrophone(session.room);
      }
      const current = state.activeIncomingCall;
      if (!current || current.callId !== active.callId || !isCurrentSession(state, active.callId, session)) {
        if (!muted) await releaseLocalMicrophone(session.room);
        return;
      }
      commit('UPDATE_ACTIVE', { micMuted: muted });
    } catch (error) {
      Message.error(muted ? '麦克风静音失败' : '麦克风恢复失败');
    }
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
    reason: call.reason || '邀请你进行视频通话'
  };
}

export default {
  namespaced: true,
  state,
  getters,
  mutations,
  actions
};
