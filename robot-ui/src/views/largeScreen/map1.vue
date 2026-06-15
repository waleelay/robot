<template>
  <div class="custom-map flx-center h100">
    <div class="map-select pt10 pb10 pl20">
      <span class="mr10" style="color: #333;">地图：</span>
      <el-select v-model="selectedMapId" placeholder="选择地图" class="item-select" @change="changeMap">
        <el-option
          v-for="(image, index) in MapIdOptions"
          :key="index"
          :label="image.label"
          :value="image.value">
        </el-option>
      </el-select>
      <!-- <span class="mr10 ml20" style="color: #333;">装备：</span>
      <el-select v-model="dogId" placeholder="选择装备" class="item-select" @change="changeDog">
        <el-option
          v-for="dog in dogList"
          :key="dog.id"
          :label="dog.name"
          :value="dog.id">
        </el-option>
      </el-select> -->
      <el-checkbox v-model="showPath" class="ml20" size="large">显示路径</el-checkbox>
    </div>
    <div class="middle_center p0" style="width: calc(100% - 40px);">
      <img :src="currentImage" alt="地图" class="image screen-img" @load="getMapSize" ref="mapImage">
      <!-- 多个机器狗 -->
      <div
        v-for="(dog, index) in dogList"
        :key="dog.id"
        :style="getRobotStyle(dog.endpoint)"
        class="robot"
        slot="reference"
        style="cursor: pointer;">
        <el-popover
          :width="300"
          :visible-arrow="false"
          trigger="hover"
          popper-class="test-popover"
          @show="onPopoverShow(dog)"
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
      <CustomLine ref="lineRef" :multiple="true" className="screen-img" id="drawingCanvas" style="z-index: 2;" :style="{ opacity: showPath ? 1 : 0 }" />
      <template v-for="(path, pathIndex) in mapPoints || []">
        <!-- <CustomLine :ref="`lineRef${pathIndex}`" className="screen-img" :id="'drawingCanvas' + pathIndex" style="z-index: 2;" :style="{ opacity: showPath ? 1 : 0 }" /> -->
        <el-tooltip v-for="(point, index) in path"
          :key="point.pointId" effect="dark"
          :content="getAddStatus(point, index)"
          placement="top" style="z-index: 3;" :style="{ opacity: showPath ? 1 : 0 }">
          <div
            :style="getPointStyle(point)"
            :class="'point-marker pathIndex' + pathIndex"
          >
          </div>
        </el-tooltip>
      </template>
    </div>

    <!-- 远程控制 -->
    <el-dialog
      v-dialogDrag
      ref="dialogRef"
      width="1630px"
      :visible.sync="dialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      destroy-on-close
      @open="handleOpen"
      @close="handleClose"
      align-center
      class="no-header map-dialog"
    >
      <NewDialog v-if="dialogVisible" ref="newDialogRef" @handleClose="handleClose" />
    </el-dialog>
  </div>
</template>

<script>
import NewDialog from './dialog/index'
import { getRecentTaskInfo } from '@/api/bigScreen.js'
import { chargeStateObj, controlModeObj, locationObj, motionStateObj, motorStateObj, onDockStateObj } from './dog';
import { controlDevice } from '../../api/bi';
import { listMapInfo } from '../../api/rsp/map';
import { listMotion } from '../../api/rsp/equipment';
import { getBasicMessage } from "@/api/menu"
import CustomLine from './../path/line.vue'

export default {
  name: "map1",
  components: { NewDialog, CustomLine },
  dicts: ['qh_motion_equipment_type', 'qh_motion_equipment_model_number'],
  data() {
    return {
      dialogVisible: false,
      dogId: '',
      dogName: undefined,
      taskName: undefined,
      dogList: [],
      dogInfo: {},
      //以右上角为坐标中心的
      robot: {},
      loadMap: false,
      currentImage: ``,
      MapIdOptions:[],
      mapPoints: [],
      selectedMapId: undefined,
      selectedMap:{},
      //地图的长宽
      mapHeight: 0,
      mapWidth: 0,
      displayWidth: 0,
      displayHeight: 0,
      X0: 0.0,
      Y0: 0.0,
      Res: 0.1,
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
      showPath: false,
      // type, command, time, items, equipment, endpoint
      allRobotInfo: [],
      selectedEndPoint: ''
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
  mounted() {},
  watch: {
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          // const message = newMessage[newMessage.length - 1];
          // if (message) {
          //   this.handlebasicMessage(message)
          // }
          this.allRobotInfo = newMessage
          if (!this.loadMap) return
          newMessage.map(item => {
            if (item.items && item.items.posX != null) {
              const { posX, posY, res } = item.items
              this.Res = res
              let PixelX = (posX - (this.X0))/(0.1);
              let PixelY = this.mapHeight - (posY - (this.Y0))/(0.1)
              this.robot[item.endpoint] = {
                x: PixelX/this.mapWidth*100,
                y: PixelY/this.mapHeight*100,
                // status: items.electricity !== null ? '在线' : '离线',
                imagePath: require(`../../assets/bigScreen/icon/robot_${items.electricity === null ? 'error' : 'normal'}.png`)
              }
            }
          })
        }
      },
      immediate: true
    },
    selectedMapId: {
      handler(newMessage) {
        if (this.selectedMapId !== undefined) {
          getBasicMessage(this.selectedMapId)
        }
      }
    }
  },
  methods: {
    // handleClose() {
    //   this.$refs.dialogRef.destoryPlugin();
    //   this.dialogVisible = false;
    //   history.go(0);
    // },
    //获取地图列表
    async getMapIdOptions() {
      const res = await listMapInfo({ status: '1' })
      this.MapIdOptions = res.rows.map(item=>({
        ...item,
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
      this.loadMap = false
      const img = this.$refs.mapImage;
      await img.onload;
      this.mapWidth = img.naturalWidth;
      this.mapHeight = img.naturalHeight;
      // 获取图片在页面上的实际显示大小
      this.displayWidth = img.clientWidth;
      this.displayHeight = img.clientHeight;
      this.loadMap = true
      // console.log('计算地图的长宽')

      setTimeout(() => {
        // this.$refs.lineRef.init()
        this.renderPath()
      }, 100);
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
        this.getDogList(this.selectedMapId)
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
    // 选择机器狗
    changeDog(e) {
      // setTimeout(() => {
      //   this.$refs.lineRef.init()
      //   this.renderPath()
      // }, 100);
      this.$emit('getDogList', { dogList: this.dogList })// dogId: e
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
        // if (message.items.posX != null) {
        //   const PosX = message.items.posX;
        //   const PosY = message.items.posY;
        //   this.Res = message.items.res;
    
        //   let PixelX = (PosX - (this.X0))/(0.1);
        //   let PixelY = this.mapHeight - (PosY - (this.Y0))/(0.1)
        //   this.robot.x = PixelX/this.mapWidth*100
        //   this.robot.y = PixelY/this.mapHeight*100
        // }
      }
    },
    //控制机器人样式（正常or异常，以及位置）
    getRobotStyle(endpoint) {
      const isExist = Boolean(this.robot[endpoint])
      return isExist ? {
        left: this.robot[endpoint].x+'%',
        top: this.robot[endpoint].y+'%',
        backgroundImage:`url(${this.robot[endpoint].imagePath})`,
      } : {
        left: '50%',
        top: '50%',
        backgroundImage: require('../../assets/bigScreen/icon/robot_normal.png')
      }
    },
    getDogList(mapInfoId) {
      let queryParams = {dogState: 1, pageNum: 1, pageSize: 50,}
      listMotion({ mapInfoId, state: '1' }).then(res => {
        this.dogList = res.rows
        console.log('根据mapId查机器狗列表', res);
        if (this.dogList.length > 0) {
          this.dogId = this.dogList[0].id; // 默认选中第一个机器人
          this.dogName = this.dogList[0].name; // 默认选中第一个机器人
          this.dogInfo = this.dogList[0]
          this.changeDog(this.dogId)
          // 拿到路径点位信息
        }
      })
    },
    onPopoverShow(dog) {
      this.selectedEndPoint = dog.endpoint
      this.dogInfo = dog
      const info = this.allRobotInfo.filter(item => item.endpoint === dog.endpoint)
      if (info && info.length) {
        const { id, name, type, dogState, modelNumber } = info[0]
        this.dogId = id
        this.dogName = name
        this.deviceData.dogType = this.selectDictLabel(this.dict.type.qh_motion_equipment_type, type)
        this.deviceData.model = this.selectDictLabel(this.dict.type.qh_motion_equipment_model_number, modelNumber)
        this.deviceData.deviceName = name
        this.deviceData.dogState = '正常'
        this.handlebasicMessage(info[0])
        getRecentTaskInfo(id).then(res =>{
          // console.log(res)
          this.taskName = res.data ? res.data.TaskName : ''
        }).catch(error => {
        })
      }
    },
    // 一键开关机
    controlDevice(type) {
      controlDevice(type, this.dogId).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        }
      })
    },
    // 打开模态框
    openModal() {
      this.dialogVisible = true;
      // 关闭一级页面的视频监控连接
    },
    handleOpen() {
      this.$nextTick(() => {
        this.$refs.newDialogRef.open({
          currentImage: this.currentImage,
          dogId: this.dogId,
          dogName: this.dogName,
          Res: this.Res,
          X0: this.X0,
          Y0: this.Y0,
          deviceData: this.deviceData,
          dogInfo: this.dogInfo,
          endpoint: this.selectedEndPoint
        })
      })
      this.$emit('changeWebrtcServer', 'close')
    },
    // 关闭模态框
    handleClose() {
      // 打开一级页面的视频监控连接
      this.dialogVisible = false;
      this.$emit('changeWebrtcServer', 'open')
    },
    //获取每个点的位置样式
    getPointStyle(point) {
      return {
        left: point.x+'%',
        top: point.y+'%',
      }
    },
    getAddStatus(point, index) {
      const text = point.pointInfo === 3 ? '（充电点）' : ''
      return index === 0 ? `起始点${text}` :  point.pointInfo === 3 ? '充电点' : '路径点'
    },
    renderPath() {
      let selectedMapInfo = this.MapIdOptions.find(m => m.value === this.selectedMapId)
      this.mapPoints = []
      selectedMapInfo.paths.map((path, index) => {
        this.mapPoints.push({})
        this.mapPoints[index] = path.points.map(item => {
          let PixelX = (item.posX - this.X0) / 0.1;
          let x = (PixelX / this.mapWidth) * 100;
          let PixelY = this.mapHeight - (item.posY - this.Y0) / 0.1;
          let y = (PixelY / this.mapHeight) * 100;
          return {
            ...item,
            x,
            y,
          };
        })
      })
      
      setTimeout(() => {
        const colors = ['#0079fe', '#eab516', '#55ce9f', '#e74c3c', '#586b8c', '#5689ee']
        this.$refs.lineRef.init()
        this.$refs.lineRef.pointGroups = []
        selectedMapInfo.paths.map((path, pathIndex) => {
          this.$refs.lineRef.pointGroups.push({ points: [], color: colors[pathIndex] })
          const linePoints = []
          const markers = document.getElementsByClassName(`point-marker pathIndex${pathIndex}`)
          if (!markers.length) return
          for (let index = 0; index < markers.length; index++) {
            const item = markers[index];
            linePoints.push({ x: item.offsetLeft, y: item.offsetTop, radius: 6 })
            if (index === markers.length - 1) {
              this.$refs.lineRef.pointGroups[pathIndex].points = linePoints
              if (pathIndex === selectedMapInfo.paths.length -1) {
                this.$refs.lineRef.redrawCanvas()
              }
            }
          }
        })
      }, 500);
    }
  },
};
</script>

<style lang="scss">


.custom-map {
  // position: absolute;
  // top: 84px;
  // right: 0;
  // // bottom: 0;
  // left: 0;
  // width: calc(100% - 920px);
  // height: calc(100% - 104px);
  // margin: auto;
  // z-index: 3;
}
.map-select {
  width: calc(100% - 40px);
  background: #CDCDCD;
}

.middle_center {
  position: relative;
  // height: 100%;
  height: calc(100% - 56px);
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

.point-marker {
  position: absolute;
  // width: 6px;
  // height: 6px;
  // background-color: #4d995f;
  border-radius: 50%;
  cursor: pointer;
}
</style>
