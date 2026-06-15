<template>
  <div class="middle_center">
    <img :src="data1.currentImage" alt="地图" class="image" @load="getMapSize" ref="mapImage">
    <div class="robot"
      slot="reference1"
      :style="getRobotStyle(robot)">
    </div>
  </div>
</template>

<script>
import { chargeStateObj, controlModeObj, locationObj, motionStateObj, motorStateObj } from './dog';
export default {
  props: {
    data1: {
      type: Object,
      default: () => ({
        currentImage: '',
        dogId: '',
        dogName: '',
        endpoint: '',
        X0: 0.0,
        Y0: 0.0,
        Res: 0.1,
        deviceData: {
          deviceName: '',
          gearState: '-空闲中-',
          motionState: '',
          sumOdom: 0,
          curOdom: 0,
          location: '',
          motorState: '',
          chargeState: '',
          controlMode: '',
          speed: 0,
          dogState: '正常',
          battery: 0,
          status: "离线",
          dogType: '',
          controlMode: '',
          model: '绝影X30 Pro',
          task: '无',
          isWarning: '否',
          onDockState: ''
        }
      })
    }
  },
  data() {
    return {
      //以右上角为坐标中心的
      robot: { id: 1, x: 50, y: 50, state: 'normal'},
      currentImage: ``,
      //地图的长宽
      mapHeight: 0,
      mapWidth: 0,
      displayWidth: 0,
      displayHeight: 0,
    };
  },
  async mounted() {
    await this.getMapSize()
  },
  computed: {
    //获取基础信息
    basicMessage() {
      // this.$emit('getDeviceData', this.data1.deviceData)
      return this.$store.getters['websocket/getBasic'];
    },
  },
  watch: {
    data1(newVal) {
      console.log("当前机器人 ID 变化:", newVal);
      // 可以在这里调用 API 加载该机器人对应的数据
    },
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          // const message = newMessage[newMessage.length - 1];
          // if (message) {
          //   this.handlebasicMessage(message)
          // }
          const arr = newMessage.filter(item => item.endpoint === this.data1.endpoint)
          if (arr.length) {
            this.handlebasicMessage(arr[0])
          }
        }
      },
      deep: true,
      immediate: true
    },
  },
  methods: {
    //获取地图的像素长宽
    async getMapSize() {
      const img = this.$refs.mapImage;
      await img.onload;
      this.mapWidth = img.naturalWidth;
      this.mapHeight = img.naturalHeight;
      // 获取图片在页面上的实际显示大小
      this.displayWidth = img.clientWidth;
      this.displayHeight = img.clientHeight;
      // console.log('计算地图的长宽')
    },
    //获取到基础信息后处理步骤
    handlebasicMessage(message) {
      //拿里面的电池信息
      if (message && message.items) {
        const obj = {}
        if (message.items.electricity != null) {
          console.log('xxxxxxx', message.items);
          
          const { motionState, sumOdom, curOdom, location, motorState, chargeState, controlMode, speed, electricity, onDockState } = message.items
          obj['deviceData'] = {
            status: '在线',
            sumOdom: sumOdom,
            curOdom: curOdom,
            motionState: motionStateObj[motionState],
            location: locationObj[location],
            motorState: motorStateObj[motorState],
            chargeState: chargeStateObj[chargeState],
            controlMode: controlModeObj[controlMode],
            speed: speed,
            battery: electricity,
            onDockState: onDockState
          }
        } else {
          console.error("电量信息格式不正确", message);
        }
        if (message.items.posX != null) {
          const PosX = message.items.posX;
          const PosY = message.items.posY;
          obj['Res'] = message.items.res;
    
          let PixelX = (PosX - (this.data1.X0))/(0.1);
          let PixelY = this.mapHeight - (PosY - (this.data1.Y0))/(0.1)
          this.robot.x = PixelX/this.mapWidth*100
          this.robot.y = PixelY/this.mapHeight*100
        }
        if (obj.deviceData) {
          this.$set(this.data1, 'deviceData', { ...this.data1.deviceData, ...obj.deviceData })
        }
        if (obj.Res !== undefined) {
          this.$set(this.data1, 'Res', obj.Res)
        }
        this.$emit('getDeviceData', this.data1.deviceData)
      }
    },
    //控制机器人样式（正常or异常，以及位置）
    getRobotStyle(robot) {
      let imagePath;
      switch(this.data1.deviceData.status) {
        case '在线':
          imagePath = require('../../assets/bigScreen/icon/robot_normal.png');
          break;
        case '离线':
          imagePath = require('../../assets/bigScreen/icon/robot_error.png');
          break;
        default:
          imagePath = require('../../assets/bigScreen/icon/robot_normal.png')
      }
      return {
        left: robot.x+'%',
        top: robot.y+'%',
        backgroundImage:`url(${imagePath})`,
      }
    },
  },
};
</script>
<style lang="scss">
.middle_center {
  position: relative;
  height: 100%;
  width: 100%;
  padding: 3%;

  .item-select {
    width: 200px;
    height: 50px;
    position: absolute;
    top: 10px;
    left: 20px;
    z-index: 2;
  }
  .state {
    position: absolute;
    top: 15px;
    right: 20px;
    z-index: 2;
    display: flex;
    flex-direction: column;
    width: 160px;
    height: 140px;
    .state_title {
      height: 20%;
      color: white;
      background: #135389;
      opacity: 0.9;
      display: flex;
      justify-content: left;
      align-items: center;
    }
    .state_item {
      height: 20%;
      color: white;
      background-color: black;
      opacity: 0.5;
      padding-left: 10px;
      padding-right: 5px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      .info {
        display: flex;
        justify-content: left;
        align-items: center;
        .green_cricle {
          width: 10px;
          height: 10px;
          border-radius: 50%;
          background-color: greenyellow;
        }
        .red_cricle {
          width: 10px;
          height: 10px;
          border-radius: 50%;
          background-color: palevioletred;
        }
        .blue_cricle {
          width: 10px;
          height: 10px;
          border-radius: 50%;
          background-color: dodgerblue;
        }
      }
    }
  }
  .image {
    width: 100%;
    height: 100%;
  }
  .robot {
    position: absolute;
    width: 35px;  /* 点的宽度 */
    height: 35px; /* 点的高度 */
    background-size: cover;
    background-repeat: no-repeat;
    transform: translate(-50%, -50%); /* 使点的中心与指定的位置对齐 */
    z-index: 3; /* 保证点显示在图片之上 */
  }
}
</style>
