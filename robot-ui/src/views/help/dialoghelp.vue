<template>
  <div class="dialog">
    <!--    <img :src="require('../../assets/bigScreen/dialog.png')" class="image"/>-->
    <!--  机器人信息  -->
    <dv-border-box-11 title="控制界面">
      <div class="dialog-content">
        <div class="left-dialog">
          <div class="inform">
            <div class="dog">
              <div class="doginform">
                <img :src="require('../../assets/bigScreen/icon/dog.png')" class="image" />
              </div>
              <div class="doginform">
                <ItemSelect v-model="dogValue" :dataList="dogList"/>
              </div>
              <div class="doginform">
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
            <div class="go_point">

            </div>
          </div>
          <!--      这里是要写语音的，但是要测试四个按钮，所以用一下-->
          <div class="sound">
            <el-button @click="getDogStateApi">获取基础信息</el-button>
            <el-button @click="issueTaskApi">下发导航任务</el-button>
            <el-button @click="cancelTaskApi">取消导航任务</el-button>
            <el-button @click="searchTaskApi">查询任务状态</el-button>
          </div>
        </div>
        <!--  操纵台  -->
        <div class="right-dialog">
          <!--      监控区域,先用一下-->
          <div class="monitor">
            <div>
              当前位置在：(X:{{PosX}},Y:{{PosY}},Z:{{PosZ}})
            </div>
            <el-button @click="confirmPoint">确认打点</el-button>
            <div>
              <ItemSelect v-model="pointValue" :dataList="pointList"/>
              <el-button @click="goToPoint">go！</el-button>
            </div>
          </div>
          <!--      操作区域-->
          <div class="operate">
            <div class="item"></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(1)">前进</button></div>
            <div class="item"></div>
            <div class="item"></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(6)">踏步</button></div>
            <div class="item"></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(11)">左移</button></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(15)">停止</button></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(12)">右移</button></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(3)">左转</button></div>
            <div class="item"><button class="circle-button"
                                      @click="">充电</button></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(4)">右转</button></div>
            <div class="item"></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(2)">后退</button></div>
            <div class="item"></div>
            <div class="item"></div>
            <div class="item"><button class="circle-button"
                                      @click="getMotionControlApi(15)">先不管</button></div>
            <div class="item"></div>
          </div>
        </div>
      </div>
    </dv-border-box-11>
  </div>
</template>
<script>
import {issueTaskbyId,getDogState,getPower,motionControl,issueTask,cancelTask,searchTask,addPoint,getPointList} from '@/api/login'
export default {
  name: "Dialog",
  data() {
    return {
      power: {
        value: 66,
      },
      motion: 0,
      dogValue:0,
      dogList: [
        {
          value: 1,
          label: '机器狗1',
        }, {
          value: 2,
          label: '机器狗2',
        }, {
          value: 3,
          label: '机器狗3',
        }
      ],
      pointValue: 0,
      pointList:[
        {
          value: 1,
          label: '一号点',
        }, {
          value: 2,
          label: '二号点',
        }, {
          value: 3,
          label: '三号点',
        }, {
          value: 4,
          label: '四号点',
        }
      ],
      quantity: 66,
      PosX: 0.0,
      PosY: 0.0,
      PosZ: 0.0,
      AngleYaw:0.0,
      pointNum: 1,
    }
  },
  computed: {
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
    //下发导航任务
    issueTaskMessage() {
      return this.$store.getters['websocket/getIssueTask']
    },
    //取消导航任务
    cancelTaskMessage() {
      return this.$store.getters['websocket/getCancelTask']
    },
    //查询导航任务状态
    searchTaskMessage() {
      return this.$store.getters['websocket/getSearchTask']
    },
    //运动控制
    motionControlMessage() {
      return this.$store.getters['websocket/getMotionTask']
    },
    //电量
    powerMessage() {
      return this.$store.getters['websocket/getPowerNum'];
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
          console.log("获取基础信息");
          const message = newMessage[newMessage.length - 1];
          if (message) {
            console.log(JSON.stringify(message, null, 2));
            //获取基础信息后的行动
            this.handleBasicMessage(message);
          }
        }
      },
      immediate: true
    },
    // 下发导航任务
    issueTaskMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          console.log("下发导航任务");
          const message = newMessage[newMessage.length - 1];
          if (message) {
            console.log(JSON.stringify(message, null, 2));
          }
        }
      },
      immediate: true
    },
    // 取消导航任务
    cancelTaskMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          console.log("取消导航任务");
          const message = newMessage[newMessage.length - 1];
          if (message) {
            console.log(JSON.stringify(message, null, 2));
          }
        }
      },
      immediate: true
    },
    // 查询导航任务状态
    searchTaskMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          console.log("查询导航任务状态");
          const message = newMessage[newMessage.length - 1];
          if (message) {
            console.log(JSON.stringify(message, null, 2));
          }
        }
      },
      immediate: true
    },
    // 运动控制
    motionControlMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          console.log("运动控制");
          const message = newMessage[newMessage.length - 1];
          if (message) {
            console.log(JSON.stringify(message, null, 2));
          }
        }
      },
      immediate: true
    },
    // 电量
    powerMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          const message = newMessage[newMessage.length - 1];
          if (message) {
            console.log(JSON.stringify(message, null, 2));
            this.handlePowerMessage(message);
          }
        }
      },
      immediate: true
    },
    //监控pointValue是否改变
    // pointValue(newValue) {
    //   console.log(newValue)
    //   this.goToPoint(newValue)
    // }
  },

  created() {
    //生命周期函数，调用获取电量的函数
    this.getPowerApi();
    //获取点的编号
    this.getPoint();

  },
  methods: {
    //获取点的名称以及id
    getPoint() {
      getPointList().then(res=>{
        console.log(res)
        //有待更改
        this.pointList = res
      })
    },
    //获取到了websocket传来的电池信息后的操作
    handlePowerMessage(message) {
      console.log("获取到了电量信息", message);
      if (message && message.items && message.items.batteryLevel != null) {
        this.quantity = message.items.batteryLevel
      } else {
        console.error("电量信息格式不正确", message);
      }
    },
    //取到基础信息以后
    handleBasicMessage(message) {
      //拿里面的电池信息
      if (message && message.items && message.items.electricity != null) {
        this.quantity = message.items.electricity
      } else {
        console.error("电量信息格式不正确", message);
      }
      //获取到了点的信息以后传给后端去存储打点
      if (message && message.items && message.items.posX != null && message.items.posY != null && message.items.posZ != null) {
        this.PosX = message.items.posX;
        this.PosY = message.items.posY;
        this.PosZ = message.items.posZ;
        this.AngleYaw = message.items.angleYaw
      } else {
        console.error("位置信息格式不正确", message);
      }
    },
    // 点击确认打点后
    confirmPoint(){
      console.log("("+this.PosX+","+this.PosY+","+this.PosZ+")")
      addPoint(this.PosX,this.PosY,this.PosZ,this.AngleYaw).then(res=>{
        console.log(res)
      })
    },
    // 选择去这个点
    goToPoint() {
      issueTaskbyId(this.pointValue).then(res=> {
        console.log(res)
      })
    },
    //调用查看电池的接口
    getPowerApi() {
      getPower().then(res=> {
        console.log("调用查看电池的接口"+res)
      })
    },
    //调用控制行动的接口
    getMotionControlApi(value) {
      this.motion = value
      motionControl(this.motion).then(res=>{
        console.log("调用控制行动的接口"+res)
      })
    },
    //查询基础状态
    getDogStateApi() {
      getDogState().then(res=> {
        console.log("调用查询基础状态接口"+res)
      })
    },
    //下发导航任务
    issueTaskApi() {
      issueTask().then(res=> {
        console.log("调用下发导航任务的接口"+res)
      })
    },
    // 取消导航任务
    cancelTaskApi() {
      cancelTask().then(res=> {
        console.log("调用取消导航任务的接口"+res)
      })
    },
    // 查询任务状态
    searchTaskApi() {
      searchTask().then(res=> {
        console.log("调用查询任务状态的接口"+res)
      })
    },
  }
}

</script>
<style lang="scss" scoped>
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
.electric-panel {
  margin-top: 15%;
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

.dialog {
  height: 100%;
  width: 100%;
  padding-bottom: 5%;
  display: flex;
  //.dialog-content {
  //  background-color: rgba(9, 31, 44, 0.95);
  //
  //}
  .left-dialog {
    width: 40%;
    display: flex;
    flex-direction: column;
    .inform {
      height: 40%;
      display: flex;
      flex-direction: column;
      .dog {
        height: 30%;
        display: flex;
        justify-content: center;
        .doginform {
          width: 30%;
          align-items: center;
          text-align: center;
          //background-color: #ff4949;
          .image {
            width: 90%;
            height: 90%;
            object-fit: contain;
          }
          .dv-percent-pond {
            margin-top: 10%;
            margin-left: 5%;
          }
        }
      }
      .go_point {
        height: 50%;

      }
    }
    .sound {
      height: 60%;
      text-align: center;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      .el-button {
        width: 50%;
      }
    }
  }
  .right-dialog {
    width: 60%;
    display: flex;
    flex-direction: column;

    .monitor {
      height: 40%;
      display: flex;
      text-align: center;
      //.plugin1 {
      //  height: 60%;
      //  width: 30%;
      //}
      //.plugin2 {
      //  height: 60%;
      //  width: 30%;
      //}
    }
    .operate {
      height: 60%;
      display: grid;
      grid-template-columns: repeat(6,1fr);
      gap: 2px;
      .item {
        padding: 5%;
        .circle-button {
          background-color: #007bff;
          border: none;
          border-radius: 50%;
          color: white;
          text-align: center;
          font-size: 16px;
          cursor: pointer;
          display: inline-block;
          width: 100%;
          height: 100%;
        }
        .circle-button:hover {
          background-color: #0056b3;
        }
      }
    }
  }

}

</style>
