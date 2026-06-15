import store from "@/store";
import bigScreen from "./bigScreen"; // 导入 store 实例
import { Message } from "element-ui";

// 定义 WebSocket 模块的初始状态
const state = {
  websocket: null, // 存储 WebSocket 实例
  manualClosing: false, // 主动关闭标记（防止替换连接时触发重复重连）
  reconnectAttempts: 0, // 记录重连次数
  maxReconnectAttempts: 5, // 最大重连次数
  isConnected: false, // 标识是否已经连接
  basic: [], // 存储实时基础状态
  issue_task: [], // 下发导航任务
  cancel_task: [], // 取消导航任务
  search_task: [], // 查询导航任务状态
  motion_control: [], // 运动控制
  power: [], // 电量信息
  videoBlob: null, // 存储接收的视频流数据
  rollCall: {}, // 存储大屏左边的信息
  cruiseDate: {},
  onlineState: 1,
  nomoveTimes: 0,
  personRollCall: [], //显示三秒的点名信息
  firePersonError: [], //显示三秒的异常信息
  lastConsumedFirePersonErrorId: null, // 最近一次已弹出的告警ID，避免页面切换后重复弹窗
  refreshWarningTime: "",
  statistics: {},
  videoUrl: "",
  vehicle: {} //车辆管控信息
};

// 定义用于修改状态的 mutations，通过这个地方判断是什么类型的信息，然后页面调用不同的信息
const mutations = {
  // 设置 WebSocket 实例
  setWebSocket(state, websocket) {
    state.websocket = websocket;
  },
  setManualClosing(state, value) {
    state.manualClosing = value;
  },
  setLastConsumedFirePersonErrorId(state, id) {
    state.lastConsumedFirePersonErrorId = id;
  },
  // 添加接收到的消息到消息列表
  addMessage(state, message) {
    const { code, jsonObject, type, success, msg, data } = message;
    // 机器狗发的基础信息
    if (code === 0 && jsonObject) {
      const { type, command, time, items, equipment } = jsonObject;
      switch (type) {
        case 1002:
          let index = null
          const arr = state.basic.filter((item, i) => {
            if (item.endpoint === equipment.endpoint) {
              index = i
            }
            return item.endpoint === equipment.endpoint
          })

          if (arr.length) {
            state.basic.splice(index, 1, { ...jsonObject, endpoint: equipment.endpoint })
            // 直接修改索引对应项无法监听到数据变化
            // state.basic[index] = { ...jsonObject, endpoint: equipment.endpoint }
            // console.warn('更新', arr.length, { ...jsonObject, endpoint: equipment.endpoint });
          } else {
            state.basic.push({ ...jsonObject, endpoint: equipment.endpoint });
            // console.warn('添加', arr.length, { ...jsonObject, endpoint: equipment.endpoint });
          }
          break;
        case 1003:
          state.issue_task.push(jsonObject);
          // console.log(jsonObject)
          if (jsonObject.items.errorCode === 1) {
            store.dispatch("alert/showAlert", {
              message: "设备执行导航任务失败，请检查前进路径是否存在障碍物！",
              type: "error",
            });
          }
          break;
        case 1004:
          state.cancel_task.push(jsonObject);
          break;
        case 1007:
          state.search_task.push(jsonObject);
          break;
        case 2:
          state.motion_control.push(jsonObject);
          break;
        case 2001:
          state.power.push(jsonObject);
          break;
        default:
          console.warn(`Unknown message type: ${type}`);
      }
    }
    //点名与巡航的信息
    else if (code === 1 && jsonObject) {
      const { type } = message;
      // console.log("收到点名信息：")
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
      const { type, items, errorMsg } = jsonObject;
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
        case 2002:
          //长期不动
          console.log("长期不动");
          if (items === 0) {
            state.nomoveTimes++;
            if (state.nomoveTimes > 30) {
              alertMessage =
                "检测到机器狗停止位移时间超过60s，请检查前进路径是否存在障碍物！";
            } else {
              return;
            }
          } else if (items === 1) {
            state.nomoveTimes = 0;
            return;
          }
          break;
        case 2003:
          //  开始导航时，狗不在充电桩上
          alertMessage = errorMsg ||
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
      // console.log("发现异常！！！！！")
      // console.log(jsonObject)
      state.statistics = {
        severity: jsonObject.serverCounts,
        normal: jsonObject.normCounts,
      };
      store.dispatch("bigScreen/updateStatistics", state.statistics);
      state.firePersonError.push(jsonObject);
    } else if (code === 5) {
      console.log("websocket-code5", message);
      state.videoUrl = message.videoUrl;
    } else if(['detect_driver', 'detect_license_plate', 'arrived'].includes(type)) {
      state.vehicle = { type, success, msg, data };
      // commit('setVehicleInfo', { type, success, msg, data })
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
  refreshWarning(state) {
    state.refreshWarningTime = +new Date();
  },
  setVehicleInfo: (state, data) => {
    state.vehicle = data
  },
};

// 定义 actions 以便于进行异步操作
const actions = {
  // 初始化 WebSocket 连接
  initWebSocket({ commit, state, dispatch }) {
    console.log("=============================连接WebSocket", state.websocket);

    if (state.websocket) {
      commit("setManualClosing", true);
      state.websocket.close(); //关闭已有的 WebSocket 实例，防止重复连接
    }
    // const websocket = new WebSocket('ws://192.168.1.4:8080/websocket');
    const websocket = new WebSocket(
      // 'wss://192.168.124.204/websocket'
      `wss://${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_HOSTNAME.replaceAll("'", '') : location.hostname}/websocket`
    );

    // 打开 websocket
    websocket.onopen = () => {
      console.log("WebSocket 连接成功！");
      commit("setConnected", true);
      commit("resetReconnectAttempts");
      commit("setManualClosing", false);
    };

    // 监听 WebSocket 的消息事件
    websocket.onmessage = (event) => {
      // console.log('-------------收到消息------------------', event);

      if (typeof event.data === "string") {
        const message = JSON.parse(event.data);
        commit("addMessage", message); // 将消息添加到状态中
      } else if (typeof event.data === "object") {
        // console.log(event.data)
        commit("addMessage", event.data);
      }
    };

    // 关闭 websocket
    websocket.onclose = () => {
      if (state.manualClosing) {
        commit("setManualClosing", false);
        return;
      }
      if (state.reconnectAttempts < state.maxReconnectAttempts) {
        commit("incrementReconnectAttempts");
        setTimeout(() => {
          console.log(`正在尝试重连第 ${state.reconnectAttempts} 次...`);
          dispatch("initWebSocket");
        }, 2000); // 重连间隔 2 秒
      } else {
        console.log("WebSocket 已关闭！");
        commit("setConnected", false);
        store.dispatch("alert/showAlert", {
          message: "机器狗连接提示：网络断开！请确认服务器是否正常运行！",
          type: "error",
        });
        // dispatch('showConnectionError'); // 弹出 WebSocket 连接失败提示
      }
    };

    // websocket 出错
    websocket.onerror = (error) => {
      console.error("WebSocket 出现错误：", error);
      commit("setConnected", false);
      websocket.close();
    };

    // 将 WebSocket 实例保存到状态中
    commit("setWebSocket", websocket);
  },
  showConnectionError() {
    alert("WebSocket 连接失败，请检查网络或服务器！");
  },
  sendMessage({ state }, message) {
    if (state.websocket && state.websocket.readyState === WebSocket.OPEN) {
      state.websocket.send(message);
    } else {
      console.error("WebSocket is not connected.");
    }
  },
  refreshData() {
    commit("refreshWarning");
  },
  setVehicle({ commit, state, dispatch }, data) {
    console.log('setVehicle', data);
    commit('setVehicleInfo', data)
  }
};

// 定义 getters 以便于从状态中获取数据
const getters = {
  // 获取所有接收到的消息
  getBasic: (state) => state.basic,
  getIssueTask: (state) => state.issue_task,
  getCancelTask: (state) => state.cancel_task,
  getSearchTask: (state) => state.search_task,
  getMotionTask: (state) => state.motion_control,
  getPowerNum: (state) => state.power,
  getVideoBlob: (state) => state.videoBlob,
  getRollCall: (state) => state.rollCall,
  getPersonRollCall: (state) => state.personRollCall,
  getFirePersonError: (state) => state.firePersonError,
  getLastConsumedFirePersonErrorId: (state) => state.lastConsumedFirePersonErrorId,
  getRefreshWarningTime: (state) => state.refreshWarningTime,
  getVideoUrl: (state) => state.videoUrl,
  getVehicle: (state) => state.vehicle,
};

// 导出 WebSocket 模块
export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters,
};
