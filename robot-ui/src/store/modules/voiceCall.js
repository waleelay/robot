import store from "@/store";
import { Message } from "element-ui";
import { v4 as uuidv4 } from "uuid"; // 引入 UUID 库
import { VoiceWebSocket } from "../../utils/voiceWebsocket";

const state = {
  websocket: null, // 存储 WebSocket 实例
  audioChunkInterval: 20,
  reconnectAttempts: 3000, // 记录重连次数
  maxReconnectAttempts: 5, // 最大重连次数
  isConnected: false, // 标识是否已经连接
  isRecording: false,
  audioContext: null,
  audioStream: null,
  audioProcessor: null, // 新增：存储 ScriptProcessorNode
  retryCount: 0,
  maxRetries: 5,
  retryInterval: 2000,
  audioBufferQueue: [], // 音频缓冲队列
  isPlaying: false, // 播放状态
};

const mutations = {
  // 设置 WebSocket 实例
  setWebSocket(state, websocket) {
    state.websocket = websocket;
  },
  // 添加接收到的消息到消息列表
  addMessage(state, message) {
    const { code, jsonObject } = message;
    // 机器狗发的基础信息
    if (code === 0 && jsonObject) {
      const { type } = jsonObject;
      switch (type) {
        case 1002:
          state.basic.push(jsonObject);
          break;
        default:
          console.warn(`Unknown message type: ${type}`);
      }
    }
    //点名与巡航的信息
    else if (code === 1 && jsonObject) {
      const { type } = message;
      // console.log('收到点名信息：')
      // console.log(message)
      if (type === 1) {
        // 点名的进度
        // console.log(jsonObject)
        state.rollCall = { ...state.rollCall, ...jsonObject };
        store.dispatch("bigScreen/updateVariables", state.rollCall);
        if (jsonObject.needTime === 0) {
          Message.success("本次点名任务已顺利结束");
        }
      } else if (type === 0) {
        //巡航的进度
        //   console.log(jsonObject)
        state.cruiseDate = { ...state.cruiseDate, ...jsonObject };
        store.dispatch("bigScreen/updateCruiseDate", state.cruiseDate);
        if (jsonObject.needTime === 0) {
          Message.success("本次巡逻任务已顺利结束");
        }
      }
    }
    //异常
    else if (code === 2 && jsonObject) {
      //异常
      const { type, items } = jsonObject;
      // console.log(jsonObject)
      let alertMessage = "";
      let alertType = "error";
      switch (type) {
        case 2001:
          //网络异常
          if (items === 0) {
            alertMessage =
              "检测到机器狗离线，请检查机器狗是否开机或网络是否断开！";
            state.onlineState = 0;
          } else if (items === 1) {
            state.onlineState = 1;
            return;
          }
          break;
        // case 2002:
        //   //长期不动
        //   // console.log('长期不动')
        //   if (items === 0) {
        //     // state.nomoveTimes ++
        //     // if (state.nomoveTimes > 30)  {
        //     //   alertMessage = '检测到机器狗已经60s不动，请检查是否遇到障碍或地图飘了!';
        //     // } else {
        //     //   return;
        //     // }
        //   } else if (items === 1) {
        //     // state.nomoveTimes = 0
        //     return;
        //   }
        //   break;
        case 2003:
          //  开始导航时，狗不在充电桩上
          alertMessage =
            "准备开始执行任务...检测到机器狗不在充电桩上！请手动将机器狗遥控回到充电桩上再开始执行任务。";
          break;
        case 2004:
          //下发导航任务的时候上一个导航任务还没有结束
          alertMessage =
            "当前正在执行导航任务，不能开启新的导航任务！请等待机器狗完成本轮任务再执行任务";
          break;
        case 2005:
          //  执行本次导航任务时判断电量还够不够
          alertMessage =
            "当前机器狗电量不足20%，不能执行任务！请确保电量充足后执行任务。";
          break;
        default:
          console.warn(`Unknown message type: ${type}`);
      }
      // 触发全局警告
      store.dispatch("alert/showAlert", {
        message: alertMessage,
        type: alertType,
      });
    }
    //实时发送点到的人员以及异常信息
    else if (code === 3 && jsonObject) {
      //  点名监控
      state.personRollCall.push(jsonObject);
    } else if (code === 4 && jsonObject) {
      //异常监控
      console.log("发现异常！！！！！");
      console.log(jsonObject);
      state.statistics = {
        severity: jsonObject.serverCounts,
        normal: jsonObject.normCounts,
      };
      store.dispatch("bigScreen/updateStatistics", state.statistics);
      state.firePersonError.push(jsonObject);
    } else {
      console.warn(`Unknown message source: ${code}`);
    }
  },
  setConnected(state, status) {
    state.isConnected = status;
  },
  incrementReconnectAttempts(state) {
    state.reconnectAttempts++;
  },
  resetReconnectAttempts(state) {
    state.reconnectAttempts = 0;
  },
};

const actions = {
  initWebsocket({ commit, state, dispatch }) {
    console.log("=============================连接语音", state.websocket);
    // 初始化 WebSocket，使用唯一 clientId
    const clientId = `A-${uuidv4()}`;
    if (state.websocket) {
      state.websocket.close(); //关闭已有的 WebSocket 实例，防止重复连接
    }
    const websocket = new VoiceWebSocket(clientId, {
      serverUrl:
        process.env.NODE_ENV === 'development' ? process.env.VUE_APP_VOICEWEBSOCKET_URL : `wss://${location.hostname}/voice`,
      targetId: "C",
      onOpen: () => {
        commit("setConnected", true);
        commit("resetReconnectAttempts");
      },
      onMessage: (event) => {
        if (typeof event.data === "string") {
          const message = JSON.parse(event.data);
          commit("addMessage", message); // 将消息添加到状态中
        } else if (typeof event.data === "object") {
          // console.log(event.data)
          commit("addMessage", event.data);
        }
      },
      onError: (error) => {
        console.error("WebSocket 出现错误：", error);
        commit("setConnected", false);
      },
      onStatusChange: (status) => {
        console.log("WebSocket 状态:", status);
        if (status === "disconnected") {
          this.$message.warning("语音对讲连接断开，正在尝试重连...");
          this.isTalk = false;
          this.isDisabled = false;
        }
      },
      onConnect: (targetId) => {
        this.$message.success(`已连接到 ${targetId}`);
      },
      onClose: () => {
        if (state.reconnectAttempts < state.maxReconnectAttempts) {
          commit("incrementReconnectAttempts");
          // setTimeout(() => {
          //   console.log(`正在尝试重连第 ${state.reconnectAttempts} 次...`);
          //   dispatch('initWebSocket');
          // }, 2000); // 重连间隔 2 秒
        } else {
          commit("setConnected", false);
          store.dispatch("alert/showAlert", {
            message: "语音连接提示：网络断开！请确认服务器是否正常运行！",
            type: "error",
          });
        }
      },
    });
    websocket.init();
    // 将 WebSocket 实例保存到状态中
    commit("setWebSocket", websocket);
  },
};

const getters = {
  // 获取所有接收到的消息
  getConnected: (state) => state.isConnected,
  getVoiceWebSocket: (state) => state.websocket,
};

// 导出 WebSocket 模块
export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters,
};
