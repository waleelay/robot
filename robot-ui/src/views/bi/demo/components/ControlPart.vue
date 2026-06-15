<template>
  <div class="flx-center mt30">
    <div class="outer wp120 hp120">
      <div class="inner flx-center wp120 hp120 m0">
        <div class="circle flx-center">移动</div>
      </div>
      <div v-for="item in controlList" :key="item.action" :class="['arrow', item.action]" @mousedown="handleControlRotation(item)">
        <svg-icon icon-class="control-arrow" />
      </div>
    </div>
    <div class="control-btns mt28">
      <div class="btn-box flx-justify-between flex-column wp150">
        <div class="flx-justify-between w100">
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp70" @click="handleControl(btnObj['zuoyi'].value)">左平移</el-button>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp70 ml20" @click="handleControl(btnObj['youyi'].value)">右平移</el-button>
        </div>
        <div class="flx-justify-between mt10 w100">
          <el-button type="primary" plain class="wp70" @click="controlDevice('shutdown')">一键返航</el-button>
          <el-button type="primary" plain class="wp70" @click="controlDevice('startup')">退出充电</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
var WebVideoCtrl = window.WebVideoCtrl
var g_iWndIndex3 = 0
import { motionControl } from '@/api/login'
import axios from "axios";
import { getRecentTaskInfo } from '@/api/bigScreen.js'
import { v4 as uuidv4 } from 'uuid'; // 引入 UUID 库
import { controlDevice } from '../../../../api/bi';
import Cookies from 'js-cookie'
import control from './control';
import SpeedDialog from './modal/Speed.vue';

export default {
  name: "Control",
  props: {
    dogId: {
      type: [Number, String],
      // required: true,
    },
    dogName: {
      type: [String],
      // required: true,
    },
    endpoint: {
      type: String,
      // required: true,
    },
    motionId: {
      type: [Number],
      required: false
    }
  },
  mixins: [control],
  components: { SpeedDialog },
  data() {
    return {
      butaiValue: 0,
      //云台
      ip: '192.168.1.202',
      port: '80',
      userName: 'admin',
      password: 'yoseen2018',
      //语音对讲
      isTalk: false,
      isDisabled: false,
      voiceWebSocket: null,
      deviceInfo: [
        {
          key: "battery",
          unit: "%",
        },
        {
          key: "status",
        },
        {
          key: "deviceName",
        },
      ],
      deviceData: {
        battery: 0,
        status: "离线",
        deviceName: "",
        onDockState: ''
        // LineName: "路径1XXX区域",
      },
      controlList: [
        {
          label: "上",
          action: "up",
          value: 1
        },
        {
          label: "下",
          action: "down",
          value: 2
        },
        {
          label: "左",
          action: "left",
          value: 3
        },
        {
          label: "右",
          action: "right",
          value: 4
        },
      ],
      btnObj: {
        zuoyi: { label: "左移", value: 11 },
        youyi: { label: "右移", value: 12 },
        zhanli: { label: "站立", value: 1 },//跟前进一致
        paxia: { label: "趴下", value: 15 },
        tztb: { label: "停止踏步", value: 14 },
        jiting: { label: "急停", value: 13 },
        // 步态：行走0 普通楼梯1 斜坡/防滑步态2 感知楼梯步态 4
        qhbt: { label: "切换步态", value: 20 },
      },
      speed: ''
    };
  },
  computed: {
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
  },
  watch: {
    dogId(newVal) {
      // console.log("当前机器人 ID 变化:", newVal);
      //routeName
      this.getRobotBasicInfo(newVal)
    },
    dogName(newVal) {
      this.deviceData.deviceName = newVal
    },
    motionId(newVal) {
      if (newVal === 100) {
        this.mouseUpPTZControl()
      } else {
        this.mouseDownPTZControl(newVal)
      }
    },
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          // console.log("获取基础信息");
          // const message = newMessage[newMessage.length - 1];
          // if (message) {
          //   // console.log(JSON.stringify(message, null, 2));
          //   //获取基础信息后的行动
          //   this.handleBasicMessage(message);
          // }
          const arr = newMessage.filter(item => item.endpoint === this.endpoint)
          if (arr.length) {
            this.handlebasicMessage(arr[0])
          }
        }
      },
      immediate: true
    },
  },
  mounted() {
    // 下面是暂时注释的：
    // this.init()
    // 初始化 WebSocket，使用唯一 clientId
    const clientId = `A-${uuidv4()}`;
    // 初始化 WebSocket
    // this.voiceWebSocket = new VoiceWebSocket(clientId, {
    //   serverUrl: process.env.VUE_APP_VOICEWEBSOCKET_URL || 'ws://192.168.1.2:8000/voice',
    //   targetId: 'C',
    //   onError: (error) => {
    //     this.$message.error(`语音对讲错误: ${error.message || '连接失败'}`);
    //     this.isTalk = false;
    //     this.isDisabled = false;
    //   },
    //   onStatusChange: (status) => {
    //     console.log('WebSocket 状态:', status);
    //     if (status === 'disconnected') {
    //       this.$message.warning('语音对讲连接断开，正在尝试重连...');
    //       this.isTalk = false;
    //       this.isDisabled = false;
    //     }
    //   },
    //   onConnect: (targetId) => {
    //     this.$message.success(`已连接到 ${targetId}`);
    //   }
    // });
    // this.voiceWebSocket.init();
  },
  beforeDestroy() {
    // 清理资源
    if (this.voiceWebSocket) {
      this.voiceWebSocket.close();
    }
  },
  methods: {
    //取到基础信息以后
    handleBasicMessage(message) {
      // console.log(message)
      //拿里面的电池信息
      if (message && message.items && message.items.electricity != null) {
        this.deviceData.battery = message.items.electricity
        this.deviceData.onDockState = message.items.onDockState
        this.deviceData.status = '在线'
      } else {
        console.error("电量信息格式不正确", message);
      }
      //其他操作-----------------------------------------------
    },
    getRobotBasicInfo(dogId) {
      // console.log(dogId)
      getRecentTaskInfo(dogId).then(res => {
        // console.log(res)
        if (res.data.LineName) {
          this.deviceData.LineName = res.data.LineName
        } else {
          this.deviceData.LineName = '当前未执行任务'
        }
      }).catch(error => {
        this.deviceData.LineName = '当前未执行任务'
      })
    },
    // 设定速度
    setSpeed() {
      this.$refs.speedRef.dialogVisible = true
    },
    // 开机
    open() { },
    // 关机
    close() { },
    controlDevice(type) {
      controlDevice(type, Cookies.get('targetId')).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        } 
      })
    },
    // 退出控制
    exit() {
      this.$emit('handleClose')
    },
    // 控制转向
    handleControlRotation(item) {
      if (this.deviceData.onDockState === '1') return
      if (item.label === '上' || item.label === '下') {
        this.getMotionControlApi(item.value, this.speed === '' ? -1 : this.speed)
      } else {
        this.getMotionControlApi(item.value)
      }
    },
    handleControl(command) {
      this.getMotionControlApi(command)
    },
    // 切换步态
    handleChangebutai(commandValue) {
      this.getMotionControlApi(this.btnObj['qhbt'].value, commandValue)
    },
    //调用控制行动的接口
    getMotionControlApi(command, commandValue) {
      this.motion = command
      motionControl(command, commandValue, this.endpoint).then(res => {
        // console.log("调用控制行动的接口"+res)
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        }
      })
    },

    async handleTalk() {
      if (this.isDisabled) {
        this.$message.warning('请勿重复点击！间隔3s后可再次点击。');
        return;
      }
      this.isTalk = !this.isTalk;
      this.isDisabled = true;
      setTimeout(() => {
        this.isDisabled = false;
      }, 3000);

      if (this.isTalk) {
        await this.startRecording();
        this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
      } else {
        await this.stopRecording();
        this.voiceWebSocket.sendMessage({ type: 'stopTalk', clientId: this.voiceWebSocket.clientId });
      }
    },
    async startRecording() {
      try {
        await this.voiceWebSocket.startRecording();
        console.log('A: 语音对讲已开启');
      } catch (error) {
        console.error('开启语音对讲失败:', error);
        this.$message.error('无法开启语音对讲，请检查麦克风权限');
        this.isTalk = false;
      }
    },
    async stopRecording() {
      try {
        this.voiceWebSocket.stopRecording();
        console.log('A: 语音对讲已关闭');
      } catch (error) {
        console.error('关闭语音对讲失败:', error);
        this.$message.error('关闭语音对讲失败');
      }
    },
    init() {
      WebVideoCtrl.I_InitPlugin({
        bWndFull: true,
        iWndowType: 1,
        cbSelWnd: (xmlDoc) => {
          g_iWndIndex3 = parseInt($(xmlDoc).find("SelectWnd").eq(0).text(), 10);
        },
        cbDoubleClickWnd: function () { },
        cbEvent: (iEventType, iParam1) => {
          if (iEventType === 2) {
            console.log("窗口" + iParam1 + "回放结束！");
          } else if (iEventType === -1) {
            console.log("设备" + iParam1 + "网络错误！");
            console.log("网络错误，请检查设备连接！");
          }
        },
        cbInitPluginComplete: () => {
          WebVideoCtrl.I_InsertOBJECTPlugin("divPlugin3").then(
            () => {
              WebVideoCtrl.I_CheckPluginVersion().then((bFlag) => {
                if (bFlag) {
                  console.log(
                    "检测到新的插件版本，双击开发包目录里的HCWebSDKPlugin.exe升级！"
                  );
                } else {
                  console.log("初始化成功");
                  this.login();
                }
              });
            },
            () => {
              // alert(
              //   "插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！"
              // );
              console.log(
                "插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！"
              );
            }
          );
        },
      });
    },
    login() {
      WebVideoCtrl.I_Login(
        this.ip,
        1,
        this.port,
        this.userName,
        this.password,
        {
          timeout: 3000,
          success: function (xmlDoc) {
            this.getDevicePort(`${this.ip}_${this.port}`);
          }.bind(this),
          error: function (error) {
            console.log(error);
            // alert('登录失败：' + error);
          },
        }
      );
    },
    getDevicePort(szDeviceIdentify) {
      if (!szDeviceIdentify) return;
      WebVideoCtrl.I_GetDevicePort(szDeviceIdentify).then(
        (oPort) => {
          console.log("登录成功", oPort);
          this.startRealPlay();
        },
        (oError) => {
          console.log(oError.errorMsg);
        }
      );
    },
    startRealPlay() {
      var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex3);
      var startRealPlay = () => {
        WebVideoCtrl.I_StartRealPlay(`${this.ip}_${this.port}`, {
          iStreamType: 1,
          iChannelID: 1,
          bZeroChannel: false,
          iWndIndex: g_iWndIndex3,
          success: () => {
            console.log("开始预览成功！");
          },
          error: function (oError) {
            console.log("开始预览失败！", oError.errorMsg);
          },
        });
      };
      if (oWndInfo != null) {
        WebVideoCtrl.I_Stop({
          iWndIndex: g_iWndIndex3,
          success: () => {
            startRealPlay();
          },
        });
      } else {
        startRealPlay();
      }
    },
    mouseDownPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex, 0)
    },
    mouseUpPTZControl() {
      this.yuntaiControl(23, 1)
    },
    async yuntaiControl(dwPTZCommand, dwStop) {
      try {
        const response = await axios.get(process.env.NODE_ENV === 'development' ? process.env.VUE_APP_YUNTAI_CONTROL : `${location.origin}/yuntai`, {
          params: {
            dwPTZCommand,
            // dwStop: dwStop,
          },
        })
      } catch (err) { }
    },
    //  运动控制
    // mouseDownPTZControl(iPTZIndex) {
    //   var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex3),
    //     // bZeroChannel = $("#channels option").eq($("#channels").get(0).selectedIndex).attr("bZero") == "true" ? true : false,
    //     iPTZSpeed = $("#ptzspeed").val();
    //   // if (bZeroChannel) {// 零通道不支持云台
    //   //   return;
    //   // }
    //   if (oWndInfo != null) {
    //     if (9 == iPTZIndex && g_bPTZAuto) {
    //       iPTZSpeed = 0;// 自动开启后，速度置为0可以关闭自动
    //     } else {
    //       g_bPTZAuto = false;// 点击其他方向，自动肯定会被关闭
    //     }
    //     WebVideoCtrl.I_PTZControl(iPTZIndex, false, {
    //       iPTZSpeed: iPTZSpeed,
    //       success: function (xmlDoc) {
    //         if (9 == iPTZIndex && g_bPTZAuto) {
    //           console.log(oWndInfo.szDeviceIdentify + " 停止云台成功！");
    //         } else {
    //           console.log(oWndInfo.szDeviceIdentify + " 开启云台成功！");
    //         }
    //         if (9 == iPTZIndex) {
    //           g_bPTZAuto = !g_bPTZAuto;
    //         }
    //       },
    //       error: function (oError) {
    //         showOPInfo(oWndInfo.szDeviceIdentify + " 开启云台失败！", oError.errorCode, oError.errorMsg);
    //       }
    //     });
    //   }
    // },
    //  停止运动控制
    // mouseUpPTZControl() {
    //   var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex3);
    //
    //   if (oWndInfo != null) {
    //     WebVideoCtrl.I_PTZControl(1, true, {
    //       success: function (xmlDoc) {
    //         console.log(oWndInfo.szDeviceIdentify + " 停止云台成功！");
    //       },
    //       error: function (oError) {
    //         console.log(oWndInfo.szDeviceIdentify + " 停止云台失败！", oError.errorCode, oError.errorMsg);
    //       }
    //     });
    //   }
    // }
    changeSpeed(speed) {
      this.speed = speed
    }
  },
};
</script>
<style lang="scss" scoped>
@keyframes fade-scale {
  0% {
    opacity: 0.8;
    transform: scale(0.95);
  }
  50% {
    opacity: 1;
    transform: scale(1.005);
  }
  100% {
    opacity: 0.8;
    transform: scale(0.95);
  }
}
.deviceTalk {
  height: 100%;
  display: flex;
  justify-content: space-around;
  .item-box {
    display: flex;
    flex-direction: column;
    justify-content: space-around;
    align-items: center;
    position: relative;
    // &:not(:last-child) {
    //   &:before {
    //     content: "";
    //     width: 1px;
    //     height: 198px;
    //     position: absolute;
    //     right: 0;
    //     background: linear-gradient(
    //       180deg,
    //       rgba(2, 152, 230, 0) 0%,
    //       #027cd8 50%,
    //       rgba(2, 152, 230, 0) 100%
    //     );
    //   }
    // }
    .title {
      width: 138px;
      height: 32px;
      line-height: 32px;
      background: linear-gradient(
        270deg,
        rgba(2, 152, 230, 0) 0%,
        rgba(2, 124, 216, 0.5) 50%,
        rgba(2, 152, 230, 0) 100%
      );
      font-size: 14px;
      color: #c5e5ff;
      text-align: center;
    }
    .info-box {
      display: flex;
      justify-content: space-around;
      align-items: center;
      flex-direction: column;
      height: 198px;
      .info-item {
        width: 100%;
        position: relative;
        padding-left: 25px;
        &::before {
          content: "";
          width: 16px;
          height: 16px;
          position: absolute;
          left: 0;
          top: 0;
          bottom: 0;
          margin: auto 0;
        }
        &:nth-child(1) {
          &:before {
            background-image: url("../../../../assets/images/largescreen/device-icon0.png");
            background-size: 100% 100%;
          }
          &.item_0 {
            &:before {
              background-image: url("../../../../assets/images/largescreen/device-icon0_0.png");
              background-size: 100% 100%;
            }
          }
        }
        &:nth-child(2) {
          &:before {
            background-image: url("../../../../assets/images/largescreen/device-icon1.png");
            background-size: 100% 100%;
          }
          &::after {
            content: "";
            width: 62px;
            height: 20px;
            background-image: url("../../../../assets/images/largescreen/device-status.png");
            background-size: 100% 100%;
            position: absolute;
            left: 25px;
            top: 0;
            bottom: 0;
            margin: auto 0;
          }
          &.item_1 {
            &:before {
              background-image: url("../../../../assets/images/largescreen/device-icon1_0.png");
              background-size: 100% 100%;
            }
            &::after {
              background-image: url("../../../../assets/images/largescreen/device-status0.png");
              background-size: 100% 100%;
            }
          }
        }
        &:nth-child(3) {
          &:before {
            background-image: url("../../../../assets/images/largescreen/device-icon2.png");
            background-size: 100% 100%;
          }
        }
        &:nth-child(4) {
          &:before {
            background-image: url("../../../../assets/images/largescreen/device-icon3.png");
            background-size: 100% 100%;
          }
        }
      }
    }
    .item-inner-box {
      display: flex;
      justify-content: space-around;
      align-items: center;
      width: 100%;
    }
    .control-box {
      width: 138px;
      height: 138px;
      background-image: url("../../../../assets/images/largescreen/control-bg.png");
      background-size: 100% 100%;
      position: relative;

      .control-item {
        width: 32px;
        height: 32px;
        cursor: pointer;
        border-radius: 50%;
        position: absolute;
        background-image: url("../../../../assets/images/largescreen/control-btn.png");
        background-size: 100% 100%;
        &:nth-child(1) {
          left: 0;
          right: 0;
          top: 10px;
          margin: 0 auto;
        }
        &:nth-child(2) {
          left: 0;
          right: 0;
          bottom: 10px;
          margin: 0 auto;
          transform: rotate(180deg);
        }
        &:nth-child(3) {
          left: 10px;
          top: 0;
          bottom: 0;
          margin: auto 0;
          transform: rotate(-90deg);
        }
        &:nth-child(4) {
          right: 10px;
          top: 0;
          bottom: 0;
          margin: auto 0;
          transform: rotate(90deg);
        }
        &:hover {
          background-image: url("../../../../assets/images/largescreen/control-btn1.png");
          background-size: 100% 100%;
        }
        &.is-disabled {
          cursor: not-allowed !important;
          // pointer-events: none;
          opacity: 0.7;
          background-image: url("../../../../assets/images/largescreen/control-btn.png");
          &:hover {
            cursor: not-allowed;
            background-image: url("../../../../assets/images/largescreen/control-btn.png");
          }
        }
      }
      .label {
        width: 44px;
        height: 44px;
        line-height: 44px;
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        margin: auto auto;
        font-weight: bold;
        font-size: 12px;
        color: #ffffff;
        text-align: center;
      }
    }
    .talk-box {
      width: 138px;
      height: 138px;
      background-image: url("../../../../assets/images/largescreen/talk-bg.png");
      background-size: 100% 100%;
      cursor: pointer;
      &.is-talk {
        background-image: url("../../../../assets/images/largescreen/talk-bg1.png");
        background-size: 100% 100%;
        animation: fade-scale 2s ease-in-out infinite;
      }
    }
    .btn-box {
      height: 100%;
      display: flex;
      flex-direction: column;
      justify-content: space-around;
      .btn-item {
        width: 90px;
        height: 24px;
        line-height: 24px;
        text-align: center;
        font-size: 14px;
        color: #c5e5ff;
        cursor: pointer;
        background-image: url("../../../../assets/images/largescreen/device-bg.png");
        background-size: 100% 100%;
        &:hover {
          background-image: url("../../../../assets/images/largescreen/device-bg0.png");
          background-size: 100% 100%;
        }
      }
    }
    &:nth-child(1) {
      width: 177px;
    }
    &:nth-child(2) {
      width: 308px;
    }
    &:nth-child(3) {
      width: 198px;
    }
    &:nth-child(4) {
      width: 208px;
    }
    &.item-box-new {
      width: 100%;
      .btn-box {
      }
    }
  }
  .control-box.custom {
    width: 200px;
    height: 200px;
    background-image: url("../../../../assets/images/largescreen/control-bg1.png");
  }
}

.arrow {
  &.up, &.down {
    left: 51px !important;
  }
  &.right, &.left {
    top: 51px !important;
  }
}
</style>
