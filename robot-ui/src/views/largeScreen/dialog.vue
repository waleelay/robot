<!--选择dog，如果被选的dog是自动状态，则需要点击按钮，如果是手动状态，就可以进行操控-->
<template>
  <div class="container">
    <div class="control_dialog">控制界面</div>
    <svg viewBox="0 0 100 100" preserveAspectRatio="none" class="svg-border">
      <polygon points="5,0 30,0 35,5 65,5 70,0 95,0 100,5 100,95 95,100 5,100 0,95 0,5" fill="none" stroke="#1FC6FF" stroke-width="0.5"/>
    </svg>
    <div class="dialog">
      <div class="control_content">
        <div class="control_choose">
          <el-select v-model="selectedDogId" :popper-append-to-body="false" @change="handleChange">
            <el-option
              v-for="item in dogList"
              :key="item.dogId"
              :label="item.dogName"
              :value="item.dogId">
            </el-option>
          </el-select>
        </div>
        <div class="control_view">
          <div class="monitor">
            <div id="divPlugin1" class="plugin1" style="height: 65%;width: 35%;margin-top: -50px"></div>
            <div id="divPlugin2" class="plugin1" style="height: 65%;width: 35%;margin-top: -50px;margin-left: 125px "></div>
          </div>
          <div class="control">
            <div class="control_info">
              <div class="info_item">
                <div class="electric_line">
                  <div v-if="selectedDog.online == 0" class="dog_line">
                    <div class="outline"></div><pre>  掉线</pre>
                  </div>
                  <div v-if="selectedDog.online == 1" class="dog_line">
                    <div class="online"></div><pre>  在线</pre>
                  </div>
                  <!--            电量展示-->
                  <div class="electric-panel" :class="bgClass">
                    <div class="panel">
                      <div class="remainder" :style="{width: quantity +'%'}" />
                    </div>
                    <div :style="{marginLeft: '10px'}"
                         class="text">{{ quantity }}%</div>
                  </div>
                </div>
              </div>
              <div class="info_item">
                <img src="../../assets/bigScreen/icon/robot-dog.png" alt="机器狗"/>
                <pre>  {{selectedDog.dogName}}</pre>
              </div>
              <div class="info_item">
                <img src="../../assets/bigScreen/icon/route.png" alt="路线"/>
                <pre>  巡逻任务：{{ taskName }}</pre>
              </div>
            </div>
            <dv-decoration-2 :reverse="true" style="width:5px;height:200px;" />
            <div class="control_look">
              <div class="look_cricle">
                <div class="item"></div>
                <div class="item">
                  <button class="circle-button" @mousedown="mouseDownPTZControl(1)" @mouseup="mouseUpPTZControl()">上</button>
                </div>
                <div class="item"></div>
                <div class="item">
                  <button class="circle-button" @mousedown="mouseDownPTZControl(3)" @mouseup="mouseUpPTZControl()">左</button>
                </div>
                <div class="item">
                  <div class="circle_text">视角</div>
                </div>
                <div class="item">
                  <button class="circle-button" @mousedown="mouseDownPTZControl(4)" @mouseup="mouseUpPTZControl()">右</button>
                </div>
                <div class="item"></div>
                <div class="item">
                  <button class="circle-button" @mousedown="mouseDownPTZControl(2)" @mouseup="mouseUpPTZControl()">下</button>
                </div>
                <div class="item"></div>
              </div>
            </div>
            <div class="control_voice">
              <button
                @mousedown="startRecording"
                @mouseup="stopRecording"
                class="voice_but">
                <img src="../../assets/bigScreen/icon/voice.png" alt="语音"/>
                <div>按下说话</div>
              </button>
            </div>

              <!-- 如果auto=0，则表示该狗处在自动操控时候，需要点击按钮才可以进行手动控制-->
            <div v-if="handelcontrol == 0" class="auto_button">
              <div>
                机器人自动运行中，如需手动控制，请点击：
              </div>
                <button class="auto_control" @click="handleControl">
                  <img src="../../assets/bigScreen/icon/click.png" alt="点击"/>
                  <div>手动控制</div>
                </button>
              </div>
              <!-- 如果auto=1，则表示该狗处在手动操控时候，此时可以控制狗-->
            <div v-if="handelcontrol == 1" class="auto_control_dog">
                <div class="control_dog">
                  <div class="control_cricle">
                    <div class="item"></div>
                    <div class="item">
                      <button class="circle-button" @mousedown="getMotionControlApi(1)" @mouseup="getMotionControlApi(14)">前</button>
                    </div>
                    <div class="item"></div>
                    <div class="item">
                      <button class="circle-button" @mousedown="getMotionControlApi(11)" @mouseup="getMotionControlApi(14)">左</button>
                    </div>
                    <div class="item">
                      <div class="circle_text">移动</div>
                    </div>
                    <div class="item">
                      <button class="circle-button" @mousedown="getMotionControlApi(12)" @mouseup="getMotionControlApi(14)">右</button>
                    </div>
                    <div class="item"></div>
                    <div class="item">
                      <button class="circle-button" @mousedown="getMotionControlApi(2)" @mouseup="getMotionControlApi(14)">后</button>
                    </div>
                    <div class="item"></div>
                  </div>
                </div>
              <dv-decoration-2 :reverse="true" style="width:5px;height:200px;" />
                <div class="quick_control">
                  <button class="but_item" @click="getMotionControlApi(6)">原地踏步</button>
                  <button class="but_item" @click="getMotionControlApi(15)">趴下</button>
<!--                  <button class="but_item" @click="cricle">巡视一圈</button>-->
                  <button class="but_item">返回充电</button>
                </div>
              </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { getDogList  } from '@/api/robot/map.js'
import { getRobotList } from '@/api/robot/robotMessage.js'
import { cricleVideo } from '@/api/menu'
import Vue from 'vue/dist/vue.esm.js'
var WebVideoCtrl = window.WebVideoCtrl
var g_iWndIndex = 0
var g_iWndIndex2 = 0
var g_bPTZAuto = false;
import {motionControl} from '@/api/login'
import axios from 'axios'
export default {
  name: "Dialog",
  data() {
    return {
      ip: '192.168.1.202',
      port: '80',
      userName: 'admin',
      password: 'yoseen2018',
      dogList: [],
      //被选中的dog的id
      selectedDog: {},
      selectedDogId: undefined,
      quantity: 66,
      handelcontrol: 0,
      queryParams: {
        dogState: 1,
        dogName: "",
        pageNum: 1,
        pageSize: 50,
      },
    }
  },
  computed: {
    // 可以添加计算属性来处理状态
    baseUrl() {
      return process.env.VUE_APP_API_BASE_URL; // 从 .env 文件中获取 baseUrl
    },
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
    //运动控制
    motionControlMessage() {
      return this.$store.getters['websocket/getMotionTask']
    },
    bgClass() {
      if (this.quantity >= 30) {
        return 'success'
      } else if (this.quantity >= 15) {
        return 'warning'
      } else if (this.quantity >= 1) {
        return 'danger'
      } else {
        return 'danger'
      }
    }
  },
  watch: {
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
    // 运动控制
    motionControlMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          // console.log("运动控制");
          const message = newMessage[newMessage.length - 1];
          if (message) {
            // console.log(JSON.stringify(message, null, 2));
          }
        }
      },
      immediate: true
    }
  },
  mounted() {
    this.init()
    this.beforeDogList()
  },
  methods: {
    //开启语音对讲
    async startRecording() {
      try {
        axios.get(`http://192.168.1.5:10000/serverAudio/openAudio`);
        console.log('已成功调用 openAudio，等待 1s...');
        await new Promise(resolve => setTimeout(resolve, 1000));
        const response = await axios.get(`http://192.168.1.5:10000/serverAudio/playAudio`);
        console.log('播放成功', response.data);
      } catch (error) {
        console.error('音频操作失败:', error);
      }
    },
    async stopRecording() {
      try {
        axios.get(`http://192.168.1.5:10000/serverAudio/stopAudio`);
        console.log('已成功调用 openAudio，等待 1s...');
        await new Promise(resolve => setTimeout(resolve, 2000));
        const response = await axios.get(`http://192.168.1.5:10000/serverAudio/closeAudio`);
        console.log('播放成功', response.data);
      } catch (error) {
        console.error('音频操作失败:', error);
      }
    },
    cricle() {
      cricleVideo()
    },
    //左侧选择部分
    beforeDogList() {
      getRobotList(this.queryParams).then(res => {
          console.log(res)
          this.dogList = res.rows.map(row =>{
            return {

            }
          })
        }
      );
      //模拟数据
      this.dogList = [{dogId: 0,dogName: '机器狗一号',auto:1,online:1},{dogId: 1,dogName: '机器狗er号',auto:0,online:1}]
      this.selectedDog = this.dogList[0]
      this.selectedDogId = this.selectedDog.dogId

    },
    handleChange(selectedValue) {
      // console.log('用户选中的值:', selectedValue);
      this.selectedDog = this.dogList.find(dog => dog.dogId === selectedValue)
      this.handelcontrol = 0
    },
    handleControl() {
      this.handelcontrol = 1
    },
    //取到基础信息以后
    handleBasicMessage(message) {
      //拿里面的电池信息
      if (message && message.items && message.items.electricity != null) {
        this.quantity = message.items.electricity
      } else {
        console.error("电量信息格式不正确", message);
      }
      //其他操作-----------------------------------------------
    },
    //调用控制行动的接口
    getMotionControlApi(value) {
      this.motion = value
      motionControl(this.motion).then(res=>{
        console.log("调用控制行动的接口"+res)
      })
    },
    //云台部分
    init() {
      WebVideoCtrl.I_InitPlugin({
        bWndFull: true,
        iWndowType: 1,
        cbSelWnd: (xmlDoc)=> {
          g_iWndIndex = parseInt($(xmlDoc).find('SelectWnd').eq(0).text(), 10)
        },
        cbDoubleClickWnd: function() {},
        cbEvent: (iEventType, iParam1) => {
          if (iEventType === 2) {
            console.log('窗口' + iParam1 + '回放结束！')
          } else if (iEventType === -1) {
            console.log('设备' + iParam1 + '网络错误！')
          }
        },
        cbInitPluginComplete: ()=> {
          WebVideoCtrl.I_InsertOBJECTPlugin('divPlugin1').then(() => {
            // 检查插件是否最新
            WebVideoCtrl.I_CheckPluginVersion().then((bFlag) => {
              if (bFlag) {
                alert('检测到新的插件版本，双击开发包目录里的HCWebSDKPlugin.exe升级！')
              } else {
                console.log('初始化成功')
                // this.login()
                this.getDevicePort(`${this.ip}_${this.port}`)
              }
            })
          }, () => {
            alert('插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！')
          })
        }
      })
    },
    init2() {
      WebVideoCtrl.I_InitPlugin({
        bWndFull: true,
        iWndowType: 1,
        cbSelWnd: (xmlDoc) => {
          g_iWndIndex2 = parseInt($(xmlDoc).find('SelectWnd').eq(0).text(), 10);
        },
        cbDoubleClickWnd: function() {},
        cbEvent: (iEventType, iParam1) => {
          if (iEventType === 2) {
            console.log('窗口' + iParam1 + '回放结束！');
          } else if (iEventType === -1) {
            console.log('设备' + iParam1 + '网络错误！');
          }
        },
        cbInitPluginComplete: () => {
          WebVideoCtrl.I_InsertOBJECTPlugin('divPlugin2').then(() => {
            WebVideoCtrl.I_CheckPluginVersion().then((bFlag) => {
              if (bFlag) {
                alert('检测到新的插件版本，双击开发包目录里的HCWebSDKPlugin.exe升级！');
              } else {
                console.log('初始化成功');
                this.startRealPlay2();
              }
            });
          }).catch(() => {
            alert('插件初始化失败，请确认是否已安装插件；如果未安装，请双击开发包目录里的HCWebSDKPlugin.exe安装！');
          });
        }
      });
    },
    login() {
      WebVideoCtrl.I_Login(this.ip, 1, this.port, this.userName, this.password, {
        timeout: 3000,
        success: function(xmlDoc) {
          this.getDevicePort(`${this.ip}_${this.port}`)
        }.bind(this),
        error: function(error) {
          console.log(error)
        }
      })
    },
    // 获取端口
    getDevicePort(szDeviceIdentify) {
      if (!szDeviceIdentify) {
        return
      }
      WebVideoCtrl.I_GetDevicePort(szDeviceIdentify).then((oPort) => {
        console.log('登录成功', oPort)
        this.startRealPlay()
      }, (oError) => {
        console.log(oError.errorMsg)
      })
    },
    //开始播放
    startRealPlay() {
      var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex)
      var startRealPlay = () => {
        WebVideoCtrl.I_StartRealPlay(`${this.ip}_${this.port}`, {
          iStreamType: 1,
          iChannelID: 1,
          bZeroChannel: false,
          iWndIndex: g_iWndIndex, // 明确指定窗口索引
          success: () =>{
            console.log('开始预览成功！')
            this.init2()
          },
          error: function(oError) {
            console.log('开始预览失败！', oError.errorMsg)
          }
        })
      }
      if (oWndInfo != null) {
        // 已经在播放了，先停止
        WebVideoCtrl.I_Stop({
          iWndIndex: g_iWndIndex,
          success: () => {
            startRealPlay()
          }
        })
      } else {
        startRealPlay()
      }
    },

    startRealPlay2() {
      var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex2)
      var startRealPlay2 = () => {
        WebVideoCtrl.I_StartRealPlay(`${this.ip}_${this.port}`, {
          iStreamType: 1,
          iChannelID: 2,
          bZeroChannel: false,
          iWndIndex: g_iWndIndex2, // 明确指定窗口索引
          success: () => {
            console.log('开始预览成功！')
          },
          error: (oError) => {
            console.log('开始预览失败！', oError.errorMsg)
          }
        })
      }
      if (oWndInfo != null) {
        WebVideoCtrl.I_Stop({
          iWndIndex: g_iWndIndex2,
          success: () => {
            startRealPlay2()
          }
        })
      } else {
        startRealPlay2()
      }
    },
    // 格式化时间
    dateFormat(oDate, fmt) {
      var o = {
        'M+': oDate.getMonth() + 1,
        'd+': oDate.getDate(),
        'h+': oDate.getHours(),
        'm+': oDate.getMinutes(),
        's+': oDate.getSeconds(),
        'q+': Math.floor((oDate.getMonth() + 3) / 3),
        'S': oDate.getMilliseconds()
      }
      if (/(y+)/.test(fmt)) {
        fmt = fmt.replace(RegExp.$1, (oDate.getFullYear() + '').substr(4 - RegExp.$1.length))
      }
      for (var k in o) {
        if (new RegExp('(' + k + ')').test(fmt)) {
          fmt = fmt.replace(RegExp.$1, (RegExp.$1.length === 1) ? (o[k]) : (('00' + o[k]).substr(('' + o[k]).length)))
        }
      }
      return fmt
    },
    //销毁
    destoryPlugin() {
      console.log('divPlugin2 销毁');
      // 继续之前的退出逻辑
      // WebVideoCtrl.I_Logout(`${this.ip}_${this.port}`).then(() => {
      //   console.log('退出成功');
      //   WebVideoCtrl.I_DestroyPlugin();
      //   WebVideoCtrl.I_DestroyPlugin();
      // }, () => {
      //   console.log('退出失败！');
      // });
      // 确保在组件销毁前停止播放并移除插件
      WebVideoCtrl.I_Stop({
        iWndIndex: g_iWndIndex,
        success: () => {
          WebVideoCtrl.I_RemoveOBJECTPlugin('divPlugin');
        }
      });
      WebVideoCtrl.I_Stop({
        iWndIndex: g_iWndIndex2,
        success: () => {
          WebVideoCtrl.I_RemoveOBJECTPlugin('divPlugin2');
        }
      });
    },
    //  运动控制
    mouseDownPTZControl(iPTZIndex) {
      // console.log(iPTZIndex)
      var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex),
        // bZeroChannel = $("#channels option").eq($("#channels").get(0).selectedIndex).attr("bZero") == "true" ? true : false,
        iPTZSpeed = $("#ptzspeed").val();
      // if (bZeroChannel) {// 零通道不支持云台
      //   return;
      // }
      if (oWndInfo != null) {
        if (9 == iPTZIndex && g_bPTZAuto) {
          iPTZSpeed = 0;// 自动开启后，速度置为0可以关闭自动
        } else {
          g_bPTZAuto = false;// 点击其他方向，自动肯定会被关闭
        }
        WebVideoCtrl.I_PTZControl(iPTZIndex, false, {
          iPTZSpeed: iPTZSpeed,
          success: function (xmlDoc) {
            if (9 == iPTZIndex && g_bPTZAuto) {
              console.log(oWndInfo.szDeviceIdentify + " 停止云台成功！");
            } else {
              console.log(oWndInfo.szDeviceIdentify + " 开启云台成功！");
            }
            if (9 == iPTZIndex) {
              g_bPTZAuto = !g_bPTZAuto;
            }
          },
          error: function (oError) {
            showOPInfo(oWndInfo.szDeviceIdentify + " 开启云台失败！", oError.errorCode, oError.errorMsg);
          }
        });
      }
    },
    //  停止运动控制
    mouseUpPTZControl() {
      var oWndInfo = WebVideoCtrl.I_GetWindowStatus(g_iWndIndex);

      if (oWndInfo != null) {
        WebVideoCtrl.I_PTZControl(1, true, {
          success: function (xmlDoc) {
            console.log(oWndInfo.szDeviceIdentify + " 停止云台成功！");
          },
          error: function (oError) {
            console.log(oWndInfo.szDeviceIdentify + " 停止云台失败！", oError.errorCode, oError.errorMsg);
          }
        });
      }
    }
  }
}

</script>
<style lang="scss" scoped>

// 修改input默认值颜色 兼容其它主流浏览器
::v-deep input::-webkit-input-placeholder {
  color: rgba(255, 255, 255, 0.50);
}
::v-deep input::-moz-input-placeholder {
  color: rgba(255, 255, 255, 0.50);
}
::v-deep input::-ms-input-placeholder {
  color: rgba(255, 255, 255, 0.50);
}

/* custom theme color */
$color-primary: #447ced;
$color-success: #13ce66;
$color-warning: #ffba00;
$color-danger: #ff4949;
$color-info: #909399;
$color-white: #fff;

@mixin panel($color) {
  .panel {
    border-color: #{$color};
    &:before {
      background: #{$color};
    }
    .remainder {
      background: #{$color};
    }
  }
  .text {
    color: #{$color};
  }
}
.container {
  position: relative;
  height: 100%;
  width: 100%;
}
.svg-border {
  pointer-events: none;
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  z-index: 2;
}
.control_dialog {
  color: white;
  font-size: 32px;
  font-weight: bold;
  text-align: center;
  line-height: 1px;
}
.dialog {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 100%;
  background: linear-gradient(180deg, #024466 0%, #023C59 0%, #01121B 100%);
  clip-path: polygon(5% 0%, 30% 0%, 35% 5%, 65% 5%, 70% 0%, 95% 0%, 100% 5%, 100% 95%, 95% 100%, 5% 100%, 0% 95%, 0% 5%);
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  .control_content {
    height: 90%;
    display: flex;
    justify-content: space-evenly;

    .control_choose {
      width: 20%;
      background-color: rgba(54, 94, 114, 0.50);
      position: relative; /* 需要确保它是相对定位 */
      .el-select {
        width: 100%;
        display: flex;
        align-items: center;
        position: relative;

        // input框
        ::v-deep .el-select,
        ::v-deep .el-input,
        ::v-deep .el-input__inner {
          background-color: rgba(7,52,89,0.3);
          color: rgb(255, 255, 255);
          text-align: left;
          border: 1px solid rgba(37,214,221,0.5);
        }
        // option选项 上面的箭头
        ::v-deep .el-popper[x-placement^="bottom"] .popper__arrow::after {
          border-bottom-color: rgba(255, 255, 255,0.04);
          z-index: 9999;
        }
        ::v-deep .popper__arrow {
          border: none;
        }
        // option选项 最外层
        ::v-deep .el-select-dropdown {
          width: 100% !important; /* 确保宽度与el-select一致 */
          left: 0 !important;     /* 确保左对齐 */
          position: absolute !important; /* 确保绝对定位 */
          border: none !important;
          background: rgba(7,52,89,0.3) !important;
          z-index: 9999;
        }
        // option选项 文字样式
        ::v-deep .el-select-dropdown__item {
          color: rgb(255, 255, 255) !important;
          z-index: 9999;
        }
        ::v-deep .el-select-dropdown__item.selected span{
          color: rgb(255, 255, 255) !important;
          z-index: 9999;
          height: 100px;
        }
        // 移入option选项 样式调整
        ::v-deep .el-select-dropdown__item.hover{
          background-color: rgba(255, 255, 255, 0.06);
          color: rgba(255, 255, 255, 0.8) !important;
          z-index: 9999;
        }
      }

    }
    .control_view {
      width: 75%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;

      .monitor {
        height: 60%;
        display: flex;
        align-items: center;
      }
      .control {
        height: 35%;
        padding: 1%;
        background-color: rgba(54, 94, 114, 0.50);
        display: flex;
        align-items: center;

        .control_info {
          width: 25%;
          height: 80%;
          display: flex;
          flex-direction: column;
          justify-content: space-evenly;
          .info_item {
            height: 15%;
            display: flex;
            align-items: center;
            text-align: center;
            font-size: 16px;
            .electric_line {
              width: 100%;
              display: flex;
              justify-content: space-between;
              align-items: center;
              //电池的相关样式
              .electric-panel {
                display: flex;
                justify-content: center;
                align-items: center;
                .panel {
                  box-sizing: border-box;
                  width: 30px;
                  height: 14px;
                  position: relative;
                  border: 2px solid #ccc;
                  padding: 1px;
                  border-radius: 3px;

                  &::before {
                    content: '';
                    border-radius: 0 1px 1px 0;
                    height: 6px;
                    background: #ccc;
                    width: 4px;
                    position: absolute;
                    top: 50%;
                    right: -4px;
                    transform: translateY(-50%);
                  }

                  .remainder {
                    border-radius: 1px;
                    position: relative;
                    height: 100%;
                    width: 0%;
                    left: 0;
                    top: 0;
                    background: #fff;
                  }
                }
                .text {
                  text-align: left;
                  width: 42px;
                }
                &.success {
                  @include panel($color-success);
                }
                &.warning {
                  @include panel($color-warning);
                }
                &.danger {
                  @include panel($color-danger);
                }
              }
              .dog_line {
                display: flex;
                justify-content: center;
                align-items: center;
              }
              .outline {
                width: 20px;
                height: 18px;
                border: 5px solid red;
                border-radius: 50%;    /* 使 div 变成圆形 */
                box-sizing: border-box;   /* 让边框在元素范围内 */
              }
              .online {
                width: 20px;
                height: 20px;
                border: 5px solid green;
                border-radius: 50%;    /* 使 div 变成圆形 */
                box-sizing: border-box;   /* 让边框在元素范围内 */
              }
            }
          }
        }
        .control_look {
          width: 25%;
          height: 90%;
          display: flex;
          align-items: center;
          justify-content: center;
          .look_cricle {
            height: 76%;
            width: 80%;
            display: grid;
            grid-template-columns: repeat(3,1fr);
            gap: 2px;
            border-radius: 50%;
            background: linear-gradient( 180deg, #357288 0%, #245268 100%);
            box-shadow: inset 0px 4px 2px 0px rgba(255,255,255,0.26), inset 0px -4px 3px 0px rgba(0,0,0,0.2);
            border: 1px solid #DAE6FF;
            .circle-button {
              width: 90%;
              height: 90%;
              border-radius: 50%;
              background: linear-gradient( 180deg, #A1B9E0 0%, #6883BD 100%);
              box-shadow: inset 0px 2px 5px 0px rgba(255,255,255,0.5);
            }
            .circle_text {
              width: 90%;
              height: 90%;
              border-radius: 50%;
              background: #5682A3;
              display: grid;
              place-items: center;  /* 同时水平和垂直居中 */
            }
          }
        }
        .control_voice {
          width: 10%;
          height: 100%;
          display: flex;
          align-items: center;
          .voice_but {
            width: 100%;
            height: 36%;
            display: flex;
            flex-direction: column;
            align-items: center;
            background: #2B5E77;
            border-radius: 4px;
            color: white;
            border: 0px;
          }
          .voice_but:active {
            background-color: #06435a;   /* 按下时改变背景颜色 */
            transform: scale(0.95);      /* 按下时按钮略微缩小 */
            box-shadow: inset 0 3px 5px rgba(0, 0, 0, 0.2); /* 添加按压效果 */
          }
        }
        .auto_button {
          width: 45%;
          padding: 5%;
          .auto_control {
            width: 80px;
            height: 80px;
            display: flex;
            flex-direction: column;
            align-items: center;
            background: #2B5E77;
            border-radius: 4px;
            color: white;
            border: 0px;
          }
          .auto_control:active {
            background-color: #06435a;   /* 按下时改变背景颜色 */
            transform: scale(0.95);      /* 按下时按钮略微缩小 */
            box-shadow: inset 0 3px 5px rgba(0, 0, 0, 0.2);
          }
        }
        .auto_control_dog {
          width: 45%;
          height: 90%;
          display: flex;
          .control_dog {
            width: 60%;
            display: flex;
            align-items: center;
            justify-content: center;
            .control_cricle {
              height: 76%;
              width: 80%;
              display: grid;
              grid-template-columns: repeat(3,1fr);
              gap: 2px;
              border-radius: 50%;
              background: linear-gradient( 180deg, #357288 0%, #245268 100%);
              box-shadow: inset 0px 4px 2px 0px rgba(255,255,255,0.26), inset 0px -4px 3px 0px rgba(0,0,0,0.2);
              border: 1px solid #DAE6FF;
              .circle-button {
                width: 90%;
                height: 90%;
                border-radius: 50%;
                background: linear-gradient( 180deg, #A1B9E0 0%, #6883BD 100%);
                box-shadow: inset 0px 2px 5px 0px rgba(255,255,255,0.5);
              }
              .circle_text {
                width: 90%;
                height: 90%;
                border-radius: 50%;
                background: #5682A3;
                display: grid;
                place-items: center;  /* 同时水平和垂直居中 */
              }
            }
          }
          .quick_control {
            width: 40%;
            height: 100%;
            display: flex;
            flex-direction: column;
            justify-content: space-evenly;
            align-items: center;
            .but_item {
              width: 70%;
              height: 40px;
              color: #1FC6FFFF;
              background-color: rgba(34, 73, 92, 0.8);
              border-radius: 2px;
              border: 1px solid #1FC6FFFF;
              border-image: linear-gradient(180deg, rgba(31, 198, 255, 0), rgba(31, 198, 255, 1), rgba(31, 198, 255, 0)) 1 1;
            }
            .but_item:active {
              transform: scale(0.95);      /* 按下时按钮略微缩小 */
              box-shadow: inset 0 3px 5px rgba(0, 0, 0, 0.2); /* 添加按压效果 */
            }
          }
        }
      }
    }
  }
}


</style>
