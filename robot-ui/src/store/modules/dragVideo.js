import store from '@/store/index.js';
/*
 * @Author: dengxumei
 * @Date: 2026-04-01 10:13:09
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-01 15:55:03
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\store\modules\dragVideo.js
 * @Version: 
 */
const state = {
  splitType: 6,
  isDragging: false,
  sourceData: null,
  sourceComponent: null,
  sourceSlotKey: null,
  dropResult: null
};

const mutations = {
  SET_DRAG_START(state, payload) {
    state.isDragging = true,
    state.sourceData = payload.data,
    state.sourceComponent = payload.componentId,
    state.sourceSlotKey = payload.slotKey,
    state.dropResult = 'pending'
  },
  SET_DROP_RESULT(state, payload) {
    state.dropResult = payload
    state.isDragging = false
    // state.splitType = payload.splitType || 1;
  },
  CLEAR_DRAG_STATE(state, payload) {
    state.splitType = payload.splitType || 1;
    state.isDragging = false;
    state.sourceData = null;
    state.sourceComponent = null;
    state.dropResult = null
  },
  SET_SPLIT_TYPE(state, payload) {
    state.splitType = payload || 1;
  }
};

const actions = {
  dragStart({ commit }, data) {
    commit('SET_DRAG_START', data);
  },
  dropComplete({ commit, state }, result) {
    // 可选，通知原组件
    if (state.sourceComponent && state.sourceComponent === result.componentId) {
      // 可以通过事件或直接返回结果
      commit('SET_DROP_RESULT', result);
    }
    return result
  },
  resetDrag({ commit }, result) {
    commit('CLEAR_DRAG_STATE', result || {});
  },
  setSplitType({ commit }, payload) {
    commit('SET_SPLIT_TYPE', payload);
  }
};
// 处理循环引用
function getCircularReplacer() {
  const seen = new WeakSet();
  return (key, value) => {
    if (typeof value === 'object' && value !== null) {
      if (seen.has(value)) {
        return '[Circular]'; // 替换循环引用
      }
      seen.add(value);
    }
    return value;
  };
};
// 拖拽开始
export function onDragStart(event, info, componentId, slotKey) {
  console.log('拖拽开始', info, componentId);
  // 设置拖拽数据 - 使用纯文本格式
  // const newInfo = { ...info, remoteAudioTrack: null, remoteVideoTrack: null, room: null }
  event.dataTransfer.setData('text/plain', JSON.stringify({ data: info, componentId, slotKey }, getCircularReplacer()));
  // 设置拖拽效果
  event.dataTransfer.effectAllowed = 'move';
  // 添加拖拽中的样式
  event.target.classList.add('dragging');
  store.dispatch('dragVideo/dragStart', { data: info, componentId, slotKey })
}
// 拖拽结束
export function onDragEnd(event) {
  event.target.classList.remove('dragging');
}

const getters = {
  isDragging: state => state.isDragging,
  getSourceData: state => state.sourceData,
  getSourceComponent: state => state.sourceComponent,
  dropResult: state => state.dropResult,
  splitType: state => state.splitType,
}

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
};
