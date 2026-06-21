<template>
  <div class="deviceTalk" @click.stop>
<!--    basic info-->
    <div class="item-box">
      <div class="info-box">
        <div
          class="info-item"
          v-for="(item, index) in deviceInfo"
          :key="index"
          :class="{
            item_0: index == 0 && deviceData[item.key] < 50,
            item_1: index == 1 && deviceData[item.key] == '离线',
          }"
        >
          <span class="value">{{
            index !== 1 ? deviceData[item.key] : ""
          }}</span>
          <span class="unit" v-if="index == 0">%</span>
        </div>
      </div>
    </div>
<!--    机器人控制-->
    <div class="item-box">
      <div id="divPlugin3"
           class="plugin1"
           style="height: 0px;width: 0px;"></div>
      <div class="title">机器犬控制</div>
      <div class="item-inner-box">
        <div class="control-box">
          <div
            class="control-item"
            :title="item.label"
            v-for="(item, index) in controlList"
            :key="index"
            @mousedown="handleControl(item.value)"
          ></div>
          <div class="label">移动</div>
        </div>
        <div class="btn-box">
          <div
            class="btn-item"
            :title="item.label"
            v-for="(item, index) in btnList"
            :key="index"
            @click="handleControl(item.value)"
          >
            {{ item.label }}
          </div>
        </div>
      </div>
    </div>
<!--    设备对讲-->
    <div class="item-box">
      <div class="title">设备对讲</div>
      <div
        class="talk-box"
        :title="isTalk ? '结束对讲' : '开始对讲'"
        @click="handleTalk"
        :class="{ 'is-talk': isTalk,'disabled': isDisabled  }"
      ></div>
    </div>
<!--    云台控制-->
    <div class="item-box">
      <div class="title">云台控制</div>
      <div class="control-box">
        <div
          class="control-item"
          :title="item.label"
          v-for="(item, index) in controlList2"
          :key="index"
          @mousedown="mouseDownPTZControl(item.value)"
          @mouseup="mouseUpPTZControl()"
        ></div>
        <div class="label">视角</div>
      </div>
    </div>
  </div>
</template>

<script>
var WebVideoCtrl = window.WebVideoCtrl
var g_iWndIndex3 = 0
import {motionControl} from '@/api/login'
import axios from "axios";
import {getRecentTaskInfo} from '@/api/bigScreen.js'

export default {
  name: "deviceTalk",
  props: {
    dogId: {
      type: [Number, String],
      required: true,
    },
    dogName: {
      type: [String],
      required: true,
    },
    motionId: {
      type: [Number],
      required: false
    }
  },
   computed: {
    // 语音系统是否连接
    voiceConnect() {
      return this.$store.getters['voiceCall/getConnected'];
    },
    // 语音websocket
    voiceWebSocket() {
      return this.$store.getters['voiceCall/getVoiceWebSocket'];
    },
  },
  data() {
    return {
      //云台
      ip: '192.168.1.202',
      port: '80',
      userName: 'admin',
      password: 'yoseen2018',
      //语音对讲
      isTalk: false,
      isDisabled: false,
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
      btnList: [
        { label: "切换步态", action: "stand",value: 20 },
        { label: "停止踏步", action: "crouch",value: 14  },
        { label: "趴下", action: "return",value: 15  },
        // { label: "需要换", action: "return" },
      ],
      controlList2: [
        {
          label: "上",
          action: "up",
          value: 21
        },
        {
          label: "下",
          action: "down",
          value: 22
        },
        {
          label: "左",
          action: "left",
          value: 23
        },
        {
          label: "右",
          action: "right",
          value: 24
        },
      ],

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
      // console.log(newVal)
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
          const message = newMessage[newMessage.length - 1];
          if (message) {
            // console.log(JSON.stringify(message, null, 2));
            //获取基础信息后的行动
            this.handleBasicMessage(message);
          }
        }
      },
      immediate: true
    },
  },
  mounted() {
    // 下面是暂时注释的：
    // this.init()
  },
  beforeDestroy() {
    // 清理资源
    if (this.voiceConnect) {
      this.voiceWebSocket.stopRecording();
    }
  },
  methods: {
    //取到基础信息以后
    handleBasicMessage(message) {
      // console.log(message)
      //拿里面的电池信息
      if (message && message.items && message.items.electricity != null) {
        this.deviceData.battery = message.items.electricity
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
    handleControl(item) {
      this.getMotionControlApi(item)
    },
    //调用控制行动的接口
    getMotionControlApi(value) {
      // console.log(value)
      this.motion = value
      motionControl(this.motion).then(res=>{
        // console.log("调用控制行动的接口"+res)
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

      if (this.voiceConnect) {
        if (this.isTalk) {
          await this.startRecording();
          this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
        } else {
          await this.stopRecording();
          this.voiceWebSocket.sendMessage({ type: 'stopTalk', clientId: this.voiceWebSocket.clientId });
        }
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
        cbDoubleClickWnd: function () {},
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
      this.yuntaiControl(iPTZIndex,0)
    },
    mouseUpPTZControl() {
      this.yuntaiControl(23,1)
    },
    async yuntaiControl(dwPTZCommand,dwStop) {
      try{
        const response = await axios.get(process.env.NODE_ENV === 'development' ? process.env.VUE_APP_YUNTAI_CONTROL : `${location.origin}/yuntai`, {
          params: {
            dwPTZCommand,
            dwStop: dwStop,
          },
        })
      }catch (err) {}
    }
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
    &:not(:last-child) {
      &:before {
        content: "";
        width: 1px;
        height: 198px;
        position: absolute;
        right: 0;
        background: linear-gradient(
          180deg,
          rgba(2, 152, 230, 0) 0%,
          #027cd8 50%,
          rgba(2, 152, 230, 0) 100%
        );
      }
    }
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
            background-image: url("../../assets/images/largescreen/device-icon0.png");
            background-size: 100% 100%;
          }
          &.item_0 {
            &:before {
              background-image: url("../../assets/images/largescreen/device-icon0_0.png");
              background-size: 100% 100%;
            }
          }
        }
        &:nth-child(2) {
          &:before {
            background-image: url("../../assets/images/largescreen/device-icon1.png");
            background-size: 100% 100%;
          }
          &::after {
            content: "";
            width: 62px;
            height: 20px;
            background-image: url("../../assets/images/largescreen/device-status.png");
            background-size: 100% 100%;
            position: absolute;
            left: 25px;
            top: 0;
            bottom: 0;
            margin: auto 0;
          }
          &.item_1 {
            &:before {
              background-image: url("../../assets/images/largescreen/device-icon1_0.png");
              background-size: 100% 100%;
            }
            &::after {
              background-image: url("../../assets/images/largescreen/device-status0.png");
              background-size: 100% 100%;
            }
          }
        }
        &:nth-child(3) {
          &:before {
            background-image: url("../../assets/images/largescreen/device-icon2.png");
            background-size: 100% 100%;
          }
        }
        &:nth-child(4) {
          &:before {
            background-image: url("../../assets/images/largescreen/device-icon3.png");
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
      background-image: url("../../assets/images/largescreen/control-bg.png");
      background-size: 100% 100%;
      position: relative;

      .control-item {
        width: 32px;
        height: 32px;
        cursor: pointer;
        border-radius: 50%;
        position: absolute;
        background-image: url("../../assets/images/largescreen/control-btn.png");
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
          background-image: url("../../assets/images/largescreen/control-btn1.png");
          background-size: 100% 100%;
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
      background-image: url("../../assets/images/largescreen/talk-bg.png");
      background-size: 100% 100%;
      cursor: pointer;
      &.is-talk {
        background-image: url("../../assets/images/largescreen/talk-bg1.png");
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
        background-image: url("../../assets/images/largescreen/device-bg.png");
        background-size: 100% 100%;
        &:hover {
          background-image: url("../../assets/images/largescreen/device-bg0.png");
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
  }
}
</style>
