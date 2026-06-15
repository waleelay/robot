import { MQTTClient } from "mqtt"

/*
 * @Author: dengxumei
 * @Date: 2026-05-14 11:06:32
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-05-14 11:07:23
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\store\modules\video.js
 * @Version: 
 */
export default {
  namespaced: true,
  state: {
    mqttConnected: false,
    activeStreams: [],
    streamStatus: {} // { streamId: 'connecting'|'playing'|'error' }
  },
  mutations: {
    SET_MQTT_STATUS(state, status) {
      state.mqttConnected = status
    },
    ADD_STREAM(state, streamId) {
      if (!state.activeStreams.includes(streamId)) {
        state.activeStreams.push(streamId)
      }
    },
    REMOVE_STREAM(state, streamId) {
      const index = state.activeStreams.indexOf(streamId)
      if (index > -1) {
        state.activeStreams.splice(index, 1)
      }
    },
    UPDATE_STREAM_STATUS(state, { streamId, status }) {
      state.streamStatus[streamId] = status
    }
  },
  actions: {
    async connectMQTT({ commit }, config) {
      await MQTTClient.connect(config)
      commit('SET_MQTT_STATUS', true)
    },
    subscribeStream({ commit }, { streamId, topic }) {
      commit('ADD_STREAM', streamId)
      commit('UPDATE_STREAM_STATUS', { streamId, status: 'connecting' })
      // 实际订阅逻辑...
    }
  }
}