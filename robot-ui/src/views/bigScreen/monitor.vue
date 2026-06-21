<template>
  <div class="bottom_right" ref="bottomRight">
    <div id="divPlugin3" class="plugin1" :style="{ height: pluginHeight, width: pluginWidth }"></div>
  </div>
</template>

<script>
var g_iWndIndex3 = 0;
import 'webrtc-adapter';

export default {
  data() {
    return {
      ip: '192.168.1.202',
      port: '80',
      userName: 'admin',
      password: 'yoseen2018',
      pluginHeight: '200px',
      pluginWidth: '280px',
    };
  },
  props: {
    dogId: {
      type: [Number, String],
      required: true,
    }
  },
  watch: {
    //检测到机器人改变
    dogId(newVal) {
      console.log("当前机器人 ID 变化:", newVal);
      // 可以在这里调用 API 加载该机器人对应的数据
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.updatePluginSize();
      window.addEventListener('resize', this.updatePluginSize);
      this.init();
    });
  },
  methods: {
    updatePluginSize() {
      const container = this.$refs.bottomRight;
      if (container) {
        this.pluginHeight = `${container.clientHeight * 0.7}px`;
        this.pluginWidth = `${container.clientWidth * 0.7}px`;
      } else {
        this.pluginHeight = '200px';
        this.pluginWidth = '280px';
      }
    },
    init() {
      WebVideoCtrl.I_InitPlugin({
        bWndFull: true,
        iWndowType: 1,
        cbSelWnd: (xmlDoc) => {
          g_iWndIndex3 = parseInt($(xmlDoc).find('SelectWnd').eq(0).text(), 10);
        },
        cbDoubleClickWnd: function() {},
        cbEvent: (iEventType, iParam1) => {
          if (iEventType === 2) {
            console.log('窗口' + iParam1 + '回放结束！');
          } else if (iEventType === -1) {
            console.log('设备' + iParam1 + '网络错误！');
            alert('网络错误，请检查设备连接！');
          }
        },
        cbInitPluginComplete: () => {
          WebVideoCtrl.I_InsertOBJECTPlugin('divPlugin3').then(() => {
            WebVideoCtrl.I_CheckPluginVersion().then((bFlag) => {
              if (bFlag) {
                alert('检测到新的插件版本，双击开发包目录里的HCWebSDKPlugin.exe升级！');
              } else {
                console.log('初始化成功');
                this.login();
              }
            });
          }, () => {
            alert('插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！');
          });
        }
      });
    },
    login() {
      WebVideoCtrl.I_Login(this.ip, 1, this.port, this.userName, this.password, {
        timeout: 3000,
        success: function(xmlDoc) {
          this.getDevicePort(`${this.ip}_${this.port}`);
        }.bind(this),
        error: function(error) {
          console.log(error);
          // alert('登录失败：' + error);
        }
      });
    },
    getDevicePort(szDeviceIdentify) {
      if (!szDeviceIdentify) return;
      WebVideoCtrl.I_GetDevicePort(szDeviceIdentify).then(
        (oPort) => {
          console.log('登录成功', oPort);
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
            console.log('开始预览成功！');
          },
          error: function(oError) {
            console.log('开始预览失败！', oError.errorMsg);
          }
        });
      };
      if (oWndInfo != null) {
        WebVideoCtrl.I_Stop({
          iWndIndex: g_iWndIndex3,
          success: () => {
            startRealPlay();
          }
        });
      } else {
        startRealPlay();
      }
    },
    stopRealPlay() {
      WebVideoCtrl.I_Stop({
        iWndIndex: g_iWndIndex3,
        success: () => {
          console.log('停止播放成功！');
        },
        error: function(oError) {
          console.log('停止播放失败！', oError.errorMsg);
        }
      });
    }
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.updatePluginSize);
    WebVideoCtrl.I_Stop({
      iWndIndex: g_iWndIndex3,
      success: () => {
        WebVideoCtrl.I_RemoveOBJECTPlugin('divPlugin3');
        console.log('销毁成功');
      }
    });
    WebVideoCtrl.I_Logout(`${this.ip}_${this.port}`).then(
      () => {
        console.log('退出成功');
        WebVideoCtrl.I_DestroyPlugin();
      },
      () => {
        console.log('退出失败！');
      }
    );
  }
};
</script>

<style lang="scss" scoped>
.bottom_right {
  height: 100%;
  padding: 5%;
}
</style>
