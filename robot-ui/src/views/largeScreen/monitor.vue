<template>
  <div class="monitor" ref="bottomRight">
    <div id="divPlugin3"
         class="plugin1"
         style="height: 64%;width: 70%;margin-top: -70px;margin-left:-130px "
         @click="toggleDialog"></div>
    <el-dialog
      class="dialog-wrapper"
      v-dialogDrag
      width="1270px"
      height="912px"
      :visible.sync="dialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      :before-close="handleClose"
      title="视频监控"
    >
      <div class="dialog-content">
        <div class="control-box">
          <div
            class="control-item"
            :title="item.label"
            v-for="(item, index) in controlList"
            :key="index"
            @click="handleControl(item)"
          ></div>
          <div class="label">视角</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
var g_iWndIndex3 = 0;
import "webrtc-adapter";

export default {
  data() {
    return {
      ip: "192.168.1.202",
      port: "80",
      userName: "admin",
      password: "yoseen2018",
      dialogVisible: false,
      controlList: [
        {
          label: "上",
          action: "up",
        },
        {
          label: "下",
          action: "down",
        },
        {
          label: "左",
          action: "left",
        },
        {
          label: "右",
          action: "right",
        },
      ],
    };
  },
  mounted() {
    this.init();
  },
  methods: {
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
            // alert("网络错误，请检查设备连接！");
            console.log("网络错误，请检查设备连接！");
          }
        },
        cbInitPluginComplete: () => {
          WebVideoCtrl.I_InsertOBJECTPlugin("divPlugin3").then(
            () => {
              WebVideoCtrl.I_CheckPluginVersion().then((bFlag) => {
                if (bFlag) {
                  // alert(
                  //   "检测到新的插件版本，双击开发包目录里的HCWebSDKPlugin.exe升级！"
                  // );
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
    stopRealPlay() {
      WebVideoCtrl.I_Stop({
        iWndIndex: g_iWndIndex3,
        success: () => {
          console.log("停止播放成功！");
        },
        error: function (oError) {
          console.log("停止播放失败！", oError.errorMsg);
        },
      });
    },
    toggleDialog() {
      this.dialogVisible = true;
    },
    handleClose() {
      this.dialogVisible = false;
    },
    handleControl(item) {
      console.log(item);
    },
  },
  beforeDestroy() {
    window.removeEventListener("resize", this.updatePluginSize);
    WebVideoCtrl.I_Stop({
      iWndIndex: g_iWndIndex3,
      success: () => {
        WebVideoCtrl.I_RemoveOBJECTPlugin("divPlugin3");
        console.log("销毁成功");
      },
    });
    WebVideoCtrl.I_Logout(`${this.ip}_${this.port}`).then(
      () => {
        console.log("退出成功");
        WebVideoCtrl.I_DestroyPlugin();
      },
      () => {
        console.log("退出失败！");
      }
    );
  },
};
</script>

<style lang="scss" scoped>
.monitor {
  height: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  // 待对接（假数据）
  &::before {
    content: "";
    width: 426px;
    height: 232px;
    position: absolute;
    background-image: url("../../assets/images/largescreen/demo-img0.png");
    background-size: 100% 100%;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    margin: auto;
    z-index: 1;
    pointer-events: none;
    cursor: pointer;
  }
  .plugin1 {
    width: 426px;
    height: 232px;
  }
}
.dialog-wrapper {
  ::v-deep .el-dialog {
    background-color: transparent;
    .el-dialog__header {
      height: 62px;
      position: relative;
      text-align: center;
      .el-dialog__title {
        color: #fff;
        font-size: 32px;
        font-weight: bold;
        position: absolute;
        left: 0;
        right: 0;
        top: 40px;
        margin: 0 auto;
      }
      .el-dialog__headerbtn {
        width: 40px;
        height: 40px;
        background-image: url("../../assets/images/largescreen/dialog-close-btn.png");
        background-size: 100% 100%;
        top: 0;
        bottom: 0;
        margin: auto 0;
      }
    }
    .el-dialog__body {
      height: 850px;
      background-image: url("../../assets/images/largescreen/dialog-bg.png");
      background-size: 100% 100%;
    }
  }
  .dialog-content {
    height: 100%;
    padding: 30px 10px 0 10px;
    box-sizing: border-box;
    position: relative;
    // 待对接（假数据）
    &::before {
      content: "";
      width: 1186px;
      height: 708px;
      position: absolute;
      //background-image: url("../../assets/images/largescreen/demo-img3.png");
      background-size: 100% 100%;
    }

    .control-box {
      position: absolute;
      width: 148px;
      height: 148px;
      background-image: url("../../assets/images/largescreen/control-bg.png");
      background-size: 100% 100%;
      right: 24px;
      bottom: 24px;
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
  }
}
</style>
