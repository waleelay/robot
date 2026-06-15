<template>
  <div class="middle_center p0 pr20 pl20">
   <!-- <el-select v-model="selectedMapId" placeholder="选择地图" class="item-select" @change="changeMap">
     <el-option
       v-for="(image, index) in MapIdOptions"
       :key="index"
       :label="image.label"
       :value="image.value">
     </el-option>
   </el-select> -->
   <!-- <ItemSelect v-model="selectedMapId" :dataList="MapIdOptions" @change="changeMap" class="item-select"/> -->
   <!-- <div class="state">
     <div class="state_title">
       <img src="../../assets/bigScreen/icon/robot-dog.png" alt="机器狗"/>
       <pre> 设备状态：</pre>
     </div>
     <div class="state_item">
       <div class="info">
         <div class="green_cricle"></div>
         <pre> 在线</pre>
       </div>
       <div>{{state.onLineNum}}</div>
     </div>
     <div class="state_item">
       <div class="info">
         <div class="red_cricle"></div>
         <pre> 离线</pre>
       </div>
       <div>{{state.outLineNum}}</div>
     </div>
     <div class="state_item">
       <div class="info">
         <div class="blue_cricle"></div>
         <pre> 充电</pre>
       </div>
       <div>{{state.chargeNum}}</div>
     </div>
   </div> -->

    <img :src="currentImage" alt="地图" class="image" @load="getMapSize" ref="mapImage">

    <div class="robot"
      slot="reference"
      style="cursor: pointer;"
      :style="getRobotStyle(robot)">
      <el-popover
        v-if="!isDialog"
        :width="300"
        :visible-arrow="false"
        trigger="hover"
        popper-class="test-popover"
        @show="onPopoverShow"
      >
        <template #reference>
          <img src="../../assets/bigScreen/icon/robot-dog.png" alt="机器狗详情"/>
        </template>
        <template #default>
          <div class="box p10">
            <div class="title flx-justify-between">
              <span>{{ deviceData.deviceName }}</span>
              <el-button type="primary" class="wp42">{{ deviceData.dogState }}</el-button>
            </div>
            <div class="content mt10 pr10 pl10">
              <div>装备类型：{{ deviceData.dogType }}</div>
              <div class="mt10">装备型号：{{ deviceData.model }}</div>
              <div class="mt10">控制模式：{{ deviceData.controlMode }}</div>
              <div class="mt10">当前速度：{{ deviceData.speed }}m/s</div>
              <div class="mt10">当前电量：{{ deviceData.battery }}%</div>
              <div class="mt10">调度任务：{{ deviceData.task }}</div>
              <div class="mt10">是否告警：{{ deviceData.isWarning }}</div>
            </div>
            <div class="footer mt20 flx-center">
              <el-button type="primary" class="wp60" @click="openModal()">远程控制</el-button>
              <el-button type="primary" class="wp60 gray" @click="controlDevice('shutdown')">一键返航</el-button>
              <el-button type="primary" class="wp60" @click="controlDevice('startup')">退出充电桩</el-button>
            </div>
          </div>
        </template>
      </el-popover>
    </div>

    <!-- <el-dialog
      v-dialogDrag
      width="1630px"
      :visible.sync="dialogVisible"
      ref="dialogRef1"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      :before-close="handleClose"
      align-center
      class="no-header"
    >
      <NewDialog :dogId="dogId" :dogName="dogName" />
    </el-dialog> -->

<!--    <el-popover-->
<!--      width="250"-->
<!--      height="200"-->
<!--      trigger="hover"-->
<!--      placement="top"-->
<!--      :content="''"-->
<!--      popper-class="custom-popover"-->
<!--      @show="onPopoverShow"-->
<!--    >-->
<!--      <div class="popover">-->
<!--        <img :src="require('../../assets/bigScreen/decocate.png')" alt="装饰"/>-->
<!--        <div class="popover-contain">-->
<!--          <div>{{dogName}}</div>-->
<!--          <div>巡逻任务：{{ taskName }}</div>-->
<!--          <button @click="pointClick(robot)" class="popover-but">-->
<!--            <img :src="require('../../assets/bigScreen/icon/control.png')"/>控制界面-->
<!--          </button>-->
<!--        </div>-->
<!--        <img :src="require('../../assets/bigScreen/decocate.png')" alt="装饰"/>-->
<!--      </div>-->
<!--      <div class="robot"-->
<!--           slot="reference"-->
<!--           :style="getRobotStyle(robot)">-->
<!--      </div>-->
<!--    </el-popover>-->
  </div>
</template>

<script>
import { getRobotList} from '@/api/robot/robotMessage.js'
import {getRecentTaskInfo} from '@/api/bigScreen.js'
import { getMapIdOptions } from '@/api/robot/point'
import error from "./error";
import NewDialog from './dialog/index'
import { chargeStateObj, controlModeObj, locationObj, motionStateObj, motorStateObj, onDockStateObj } from './dog';
import { controlDevice } from '../../api/bi';
import { listMapInfo } from '../../api/rsp/map';
export default {
  props: {
    dogId: {
      type: [Number, String],
      required: true,
    },
    isDialog: {
      type: Boolean,
      default: false
    }
  },
  components: { NewDialog },
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
      deviceData: {
        // 装备名称
        deviceName: "",
        // 装备状态
        gearState: '-空闲中-',
        // 运动状态
        motionState: '',
        // 累计里程
        sumOdom: 0,
        // 当次里程
        curOdom: 0,
        // 定位状态
        location: '',
        // 电机状态
        motorState: '',
        // 自主充电状态
        chargeState: '',
        // 控制模式
        controlMode: '',
        // 速度
        speed: 0,
        dogState: '正常',
        // 电量
        battery: 0,
        status: "离线",
        dogType: '',
        controlMode: '',
        model: '绝影X30 Pro',
        task: '无',
        isWarning: '否'
      },
      dialogVisible: false
    };
  },
  async created() {
    await this.getMapIdOptions()
    await this.getMapSize()
    await this.changeMap()
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
    //popover出现
    onPopoverShow() {
      getRobotList(this.queryParams).then(res => {
        const { dogId, dogName, dogType, dogState } = res.rows[0]
        this.dogId = dogId
        this.dogName = dogName
        this.deviceData.dogType = dogType === 0 ? '人形机器狗' : dogType === 1 ? '犬形机器狗' : '轮式机器狗'
        this.deviceData.deviceName = dogName
        this.deviceData.dogState = dogState === 1 ? '正常' : '禁用'
        getRecentTaskInfo(this.dogId).then(res =>{
          // console.log(res)
          this.taskName = res.data ? res.data.TaskName : ''
        }).catch(error => {
        })
      })
    },
    //获取地图列表
    async getMapIdOptions() {
      // const res = await getMapIdOptions()
      // // console.log(res)
      // // res.data = [
      // //   {
      // //     MapID: 0,
      // //     MapName: 'cehsi',
      // //     dogId: 1,
      // //     dogName: '502机器狗',
      // //     TDmodel: null,
      // //     X0: 1.0,
      // //     Y0: 1.0,
      // //     ResolutionRatio: null
      // //   }
      // // ]
      // this.MapIdOptions = res.data.map(item=>({
      //   value: item.MapID,
      //   label: item.MapName,
      //   X0: item.X0,
      //   Y0: item.Y0,
      // }))
      // if (this.MapIdOptions.length) {
      //   this.selectedMapId = this.selectedMap = this.MapIdOptions[0].value
      // }
      const res = await listMapInfo()
      this.MapIdOptions = res.rows.map(item=>({
        value: item.id,
        label: item.name,
        X0: item.startXCoordinate,
        Y0: item.startYCoordinate,
        mapFileUrl: item.mapFileUrl
      }))
      if (this.MapIdOptions.length) {
        this.selectedMapId = this.MapIdOptions[0].value
      }
    },
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
    //地图变了！！！
    async changeMap() {
      let selectedMapInfo = this.MapIdOptions.find(m => m.value === this.selectedMapId)      
      if (selectedMapInfo) {
        // this.currentImage = `${process.env.VUE_APP_BASE_API}/maps/${selectedMapInfo.label}.jpg`
        // this.currentImage = require('D:/resources/robot/maps/地图1.jpg')
        this.currentImage = `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${selectedMapInfo.mapFileUrl}`
        this.X0 = selectedMapInfo.X0
        this.Y0 = selectedMapInfo.Y0
      }

      // 测试
      // setTimeout(() => {
      //   this.PosX = 30;
      //   this.PosY = 30;
      //   let PixelX = (this.PosX - (this.X0))/(0.1);
      //   let PixelY = this.mapHeight - (this.PosY - (this.Y0))/(0.1)
      //   this.robot.x = PixelX/this.mapWidth*100
      //   this.robot.y = PixelY/this.mapHeight*100
      // }, 5000);
    },
    //获取到基础信息后处理步骤
    handlebasicMessage(message) {
      //拿里面的电池信息
      if (message && message.items) {
        if (message.items.electricity != null) {
          const { motionState, sumOdom, curOdom, location, motorState, chargeState, controlMode, speed, electricity, onDockState } = message.items
          this.deviceData.status = '在线'
          this.deviceData.sumOdom = sumOdom
          this.deviceData.curOdom = curOdom
          this.deviceData.motionState = motionStateObj[motionState]
          this.deviceData.location = locationObj[location]
          this.deviceData.motorState = motorStateObj[motorState]
          this.deviceData.chargeState = chargeStateObj[chargeState]
          this.deviceData.controlMode = controlModeObj[controlMode]
          // this.deviceData.onDockState = onDockStateObj[onDockState]
          this.deviceData.onDockState = onDockState
          this.deviceData.speed = speed
          this.deviceData.battery = electricity
        } else {
          console.error("电量信息格式不正确", message);
        }
        if (message.items.posX != null) {
          this.PosX = message.items.posX;
          this.PosY = message.items.posY;
          this.Res = message.items.res;
    
          let PixelX = (this.PosX - (this.X0))/(0.1);
          let PixelY = this.mapHeight - (this.PosY - (this.Y0))/(0.1)
          this.robot.x = PixelX/this.mapWidth*100
          this.robot.y = PixelY/this.mapHeight*100
        }
      }
    },
    //控制机器人样式（正常or异常，以及位置）
    getRobotStyle(robot) {
      let imagePath;
      switch(this.deviceData.status) {
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
    //点击点（机器人），控制弹窗出现，获取该机器人的状态信息
    pointClick(point) {
      // console.log(point)
      this.$emit('toggle-dialog',)
    },
    controlDevice(type) {
      controlDevice(type).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        }
      })
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
