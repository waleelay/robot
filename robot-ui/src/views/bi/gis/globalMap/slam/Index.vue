<template>
  <div class="middle_center p0 pr20 pl20">
    <img :src="currentImage" alt="地图" class="image" ref="mapImage">
    <div class="robot"
      slot="reference"
      style="cursor: pointer;"
      :style="getRobotStyle(robot)">
    </div>
  </div>
</template>

<script>
export default {
  // components: { NewDialog },
  data() {
    return {
      //以右上角为坐标中心的
      robot: { id: 1, x: 50, y: 50, state: 'normal'},
      currentImage: ``,
      state:{
        onLineNum: 1,
        outLineNum: 0,
        chargeNum: 0,
      },
      MapIdOptions:[],
      selectedMapId: undefined,
      selectedMap:{},
      //地图的长宽
      mapHeight: 0,
      mapWidth: 0,
      displayWidth: 0,
      displayHeight: 0,
      PosX: 0.0,
      PosY: 0.0,
      X0: 0.0,
      Y0: 0.0,
      Res: 0.1,
      dogName: undefined,
      taskName: undefined,
      queryParams: {
        dogState: 1,
        dogName: "",
        pageNum: 1,
        pageSize: 50,
      },
      deviceData: {},
      dialogVisible: false
    };
  },
  async created() {
  },
  computed: {
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
  },
  watch: {
    dogId(newVal) {
      console.log("当前机器人 ID 变化:", newVal);
      // 可以在这里调用 API 加载该机器人对应的数据
    },
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          const message = newMessage[newMessage.length - 1];
          if (message) {
            this.handlebasicMessage(message)
          }
        }
      },
      immediate: true
    },
  },
  methods: {
    //控制机器人样式（正常or异常，以及位置）
    getRobotStyle(robot) {
      let imagePath;
      switch(this.deviceData.status) {
        case '在线':
          imagePath = require('../../../../../assets/images/new-bi/robot_normal.png');
          break;
        case '离线':
          imagePath = require('../../../../../assets/images/new-bi/robot_error.png');
          break;
        default:
          imagePath = require('../../../../../assets/images/new-bi/robot_normal.png')
      }
      return {
        left: robot.x+'%',
        top: robot.y+'%',
        backgroundImage:`url(${imagePath})`,
      }
    },
    //点击点（机器人），控制弹窗出现，获取该机器人的状态信息
    pointClick(point) {
      // console.log(point)
      this.$emit('toggle-dialog',)
    },
    openModal() {

      this.$emit('openModal')

      // console.log('打开模态框====', item);
      
      // this.dogId1 = item.dogId
      // // console.log("选中的机器人 ID:", value);
      // this.selectedRobot = item.dogId;
      // this.selectedRobotName = item.dogName;
      // 可以在这里进行其他逻辑处理，比如获取该机器人的详细信息
      // this.dialogVisible = true;
    },
    handleClose() {
      this.dialogVisible = false;
    }
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
.custom-popover {
  background-color: rgba(54, 59, 10, 0) !important; /* 修改背景颜色 */
  border: 0px ;
}
.popover {
  width: 100%;
  height: 100%;
  background-color: rgba(10,30,59) !important; /* 修改背景颜色 */
  border: 2px yellow solid;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  .popover-contain {
    padding: 5%;
    width: 100%;
    height: 100%;
    color: white;
    .popover-state {
      background: rgba(0,181,120,0.63);
      box-shadow: 0px 2px 12px 0px rgba(0,0,0,0.3);
      border-radius: 4px;
      border: 1px solid #00B578;
    }
    .popover-but {
      background: rgba(74,161,255,0.63);
      box-shadow: 0px 2px 12px 0px rgba(0,0,0,0.3);
      border-radius: 4px;
      border: 1px solid #6CB0FF;
      color: white;
    }
  }
}
</style>
