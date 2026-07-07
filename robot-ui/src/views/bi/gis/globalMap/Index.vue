<template>
  <!-- filter: invert(100%) hue-rotate(200deg); background: transparent; -->
    <!-- <div id="map" style="width: 100%; height: 100vh;"></div> -->
  <div style="position: relative; width: 100%; height: 100%;" class="flx-center">
    <!-- <MapTool style="z-index: 1;" /> -->
    <!-- <SlamMap :dogId="'dogId'" :style="{ display: isSlam ? 'block' : 'none' }" /> -->
    <template>
      <div id="map" class="w100 h100" style="z-index: 0;" :style="{ display: !isSlam ? 'block' : 'none' }"></div>
      <Thumbnail v-if="showThumbnail" :centerPoint="centerPoint" :markers="pointMarkers" :mainMap="map" ref="thumbnailRef" />
    </template>
    <!-- 远程控制 -->
    <!-- <el-dialog
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
    > -->
      <!-- <NewDialog v-if="dialogVisible" ref="newDialogRef" @handleClose="handleClose" /> -->
    <!-- </el-dialog> -->
    <!-- 模态框 -->
    <TaskAdd ref="taskAddRef" />
    <RobotControlPart ref="robotControlPartRef" />
    <RobotCarControlPart ref="robotCarControlPartRef" />
    <Robot1 :style="popupStyle" ref="robot1Ref" @showControlPart="showControlPart" @showPath="showPathArea" @showSlam="showSlam" @showArea="showDashedArea" @clear="clear" />
    <Slam ref="slamRef" />
  </div>
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
import 'leaflet-rotate';
import { Map, View, Feature } from 'ol'
import TileLayer from "ol/layer/Tile";
import XYZ from "ol/source/XYZ";
import VectorLayer from 'ol/source/Vector';
import { defaults as defaultControls } from "ol/control";
import { fromLonLat } from "ol/proj";
import Robot from './popup/Robot.vue';
import Robot1 from './popup/Robot1.vue';
import RobotControlPart from './popup/RobotControlPart.vue';
import RobotCarControlPart from './popup/RobotCarControlPart.vue';
import Vue from 'vue';
import TaskAdd from './../../components/modal/TaskAdd.vue'
import Uav from './popup/Uav.vue';
import UavPort from './popup/UavPort.vue';
import Battery from './popup/Battery.vue';
import Slam from './popup/Slam.vue'
import Thumbnail from './thumbnail/Index.vue'
import SlamMap from './slam/Index.vue'
import { mapActions, mapState } from 'vuex';
import { ROBOT_TYPE_INFO } from '../../../../constants/robot.js';

export default {
  name: 'GisGlobalMap',
  components: { TaskAdd, Thumbnail, SlamMap, RobotControlPart, RobotCarControlPart, Robot1, Slam },
  props: {
    showTool: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      map: null,
      pointA: { lat: 30.7472254, lng: 106.03737831115724 },
      pointB: { lat: 30.7472254, lng: 106.040000 },
      pointMarkers: [],
      markersLayer: null,
      selectionLayer: null,
      dashedPolygonLayer: null,
      activeMarkerIndex: null,
      currentRectangle: null,
      waypoints: [
        { lat: 30.74858822373491, lng: 106.03506952591954, desc: '起始地' },
        { lat: 30.748379525423942, lng: 106.03487979487186 },
        { lat: 30.748229520051265, lng: 106.03472042553155 },
        { lat: 30.74812517003875, lng: 106.03462176616857 },
        { lat: 30.747603565260476, lng: 106.03519074431382 },
        { lat: 30.747271049335637, lng: 106.03561559199737 },
        { lat: 30.747140651711415, lng: 106.03579008416635 },
        { lat: 30.7478613352993, lng: 106.03655278081857 },
        { lat: 30.7483112149064, lng: 106.03603690298392 },
        { lat: 30.748686106849632, lng: 106.0355627433318 },
        { lat: 30.748881699556307, lng: 106.03529721123913, desc: '目的地' }
      ],
      pathLayers: [],
      movementPath: null,
      isMoving: false,
      showPath: false,
      settingPointB: false,
      moveCount: 0,
      movementInterval: null,
      distance: 0,
      centerPoint: { lat: 30.7472254, lng: 106.03737831115724 },
      markers: [
        // { lat: 30.7472254, lng: 106.03737831115724, pathPoints: [], movementPath: null },
        // { lat: 30.7472254, lng: 106.040000, pathPoints: [], movementPath: null }
      ],
      dialogVisible: false,
      taskName: undefined,
      endpoint: '',
      //以右上角为坐标中心的
      robot: {},
      loadMap: false,
      currentImage: ``,
      //地图的长宽
      mapHeight: 0,
      mapWidth: 0,
      displayWidth: 0,
      displayHeight: 0,
      X0: 0.0,
      Y0: 0.0,
      Res: 0.1,
      isSlam: false,
      // 显示缩略图
      showThumbnail: false,
      testA: { lat: 30.7472254, lng: 106.03737831115724 },
      testB: { lat: 30.7472254, lng: 106.040000 },
      distance1: 0,
      layerB: null,
      layerA: null,
      dogList: [],
      popupVisible: false,
      popupOffset: { x: 0, y: 0 },
      timer: null
    }
  },
  computed: {
    popupStyle() {
      return this.$route.name === 'biIndex' ? {
        left: this.popupOffset.x + 'px',
        top: this.popupOffset.y + 'px',
        display: this.popupVisible ? 'block' : 'none'
      } : {};
    },
    // 计算两点之间的距离
    calculatedDistance() {
      if (!this.map || !this.pointA || !this.pointB) return 0;
      const latLngA = L.latLng(this.pointA.lat, this.pointA.lng);
      const latLngB = L.latLng(this.pointB.lat, this.pointB.lng);
      return this.map.distance(latLngA, latLngB).toFixed(2);
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras'];
    },
    ...mapState('websocketExtraData', ['robotLocation', 'robotBaseInfo', 'robotList', 'robotAlarmObj', 'taskData', 'mapSearchValue'])
  },
  watch: {
    // 监听距离变化
    calculatedDistance: {
      handler(newVal) {
        this.distance = newVal;
      },
      immediate: true
    },
    // 监听设置B点模式
    settingPointB(newVal) {
      if (newVal) {
        this.map.getContainer().style.cursor = 'crosshair';
      } else {
        this.map.getContainer().style.cursor = '';
      }
    },
    robotList: {
      handler(newVal, oldVal) {
        // if (this.pointMarkers.length) return
        this.timer = setTimeout(() => {
          if (this.map) {
            this.initPoints()
          }
        }, 1000);
      },
      immediate: true
    },
    robotLocation: {
      handler(newVal, oldVal) {
        if (Object.keys(newVal || {}).length) {
          this.pointMarkers.map(marker => {
            const { lat, lng } = marker.getLatLng()
            // console.log(123, newVal)
            if (lat !== newVal[marker.meta?.robot?.robotId]?.lat || lng !== newVal[marker.meta?.robot?.robotId]?.lng) {
              // console.log('===========更新了==========');
              marker.setLatLng({
                lat: newVal[marker.meta?.robot?.robotId]?.lat,
                lng: newVal[marker.meta?.robot?.robotId]?.lng
              })
            }
          })
        }
      },
      deep: true,
      immediate: true
    },
    robotBaseInfo: {
      handler(newVal, oldVal) {
        if (Object.keys(newVal || {}).length) {
          this.pointMarkers.map(marker => {
            if (newVal?.[marker.meta?.robot?.robotId]) {
              this.updateIconBaseInfo(newVal[marker.meta.robot.robotId])
            }
          })
        }
      },
      deep: true,
      immediate: true
    },
    robotAlarmObj: {
      handler(newVal, oldVal) {
        this.pointMarkers.map((marker, index) => {
          const { robot, alarmId } = marker?.meta || {}
          // 如果存在，新值没返，删除meta.alarmId
          // if (alarmId) {
          //   if (newVal[robot.robotId])
          // } else {

          // }
          this.pointMarkers[index].setIcon(this.getIcon(marker?.meta?.robot || {}, marker.getIcon().options))
          // if (marker.meta?.robot?.robotId) {
          //   this.updateIconAlarm(newVal[marker.meta?.robot?.robotId])
          // }
        })
        // if (Object.keys(newVal || {}).length) {
        //   console.log('robotAlarmObj', newVal);
          
        // }
      },
      deep: true,
      immediate: true
    },
    mapSearchValue: {
      handler(newVal, oldVal) {
        if (newVal && this.map) {
          const keywords = newVal.split('_timestamp_')[0].toLowerCase()
          const robot = this.getSearchRobot()
          if (robot) {
            this.map.setView([this.robotLocation[robot.robotId].lat, this.robotLocation[robot.robotId].lng])
          } else {
            this.$message.error('未找到相关装备')
          }
        }
      },
      immediate: true
    }
  },
  async created() {
    window.openModal = this.openModal;
    window.closePopup = this.closePopup;
    window.controlDevice = this.controlDevice;
  },
  async mounted(){
    // await this.getMapIdOptions()
    // // await this.getMapSize()
    // await this.changeMap()
    this.initMap();
    // this.initPoints()

    // this.initPoints();
    // setTimeout(() => {
    //   this.startMovement()
    // }, 2000);
    // setTimeout(() => {
    //   this.stopMovement()
    // }, 30000);

    // setTimeout(() => {
    //   console.log('变化了');
    //   const randomLat = (Math.random() - 0.5) * 0.001;
    //   const randomLng = (Math.random() - 0.5) * 0.001;
    //   const lat = 30.7478613352993 + randomLat
    //   const lng = 106.03655278081857 + randomLng
    //   this.setRobotLocation({ robotId: this.robots?.[0].robotId, location: { lat, lng } })
    // }, 3000);
  },
  methods: {
    ...mapActions('websocketExtraData', ['setRobotLocation']),
    getSelectedStatus(robotId) {
      return this.$route.name !== 'biIndex' && Object.keys(this.activeCameras || {}).find(key => this.activeCameras[key].robot.robotId === robotId)
    },
    // getRobotStatus(robotId) {
    //   const { status, task = [] } = this.robotBaseInfo?.[robotId] || {}      
    //   const runningTask = Array.isArray(task) ? task : [task].map(item => this.taskData?.[item.taskId] || item)?.find(item => item.status === 'running') || null
    //   const customStatusName = status === 'online' ? runningTask ? '任务中' : '空闲中' : status === 'offline' ? '离线' : '故障'
    //   const statusClass = status === 'online' ? runningTask ? 'blue' : 'green' : status === 'offline' ? 'gray' : 'orange'
    //   return { customStatusName, statusClass }
    // },
    changeMapType() {
      this.isSlam = !this.isSlam
    },
    initMap(){
      // 只能看到18层
      this.map = L.map('map', {
        center: [this.centerPoint.lat, this.centerPoint.lng],
        zoom: 12,
        maxZoom: 18,
        minZoom: 12,
        rotate: true,
        rotateControl: false, // 显示旋转控制按钮
        // 提高渲染能力
        // preferCanvas: true,
        // attributionControl: '版权',
        zoomControl: false,
        // markerZoomAnimation: false,
        zoomSnap: 0.1,   // 允许更精细的粒度
        // zoomDelta: 0.25  // 设置每次zoomIn/Out的步长为0.25
        zoomDelta: 6  // 设置每次zoomIn/Out的步长为0.25
      });
      // 关键：手动添加旋转控件到地图
      L.control.rotate().addTo(this.map);
      // 地图底图
      this.layerA = L.tileLayer('/tdt/tiles/new/latest/{z}/{x}/{y}.png', {
      // this.layerA = L.tileLayer('/tdt/tiles/12/{z}/{x}/{y}.png', {
        maxZoom: 12,
        minZoom: 12,
      });

      this.layerB = L.tileLayer('/tdt/tiles/new/latest/{z}/{x}/{y}.png', {
        maxZoom: 18,
        minZoom: 17,
        keepBuffer: 300,
        updateWhenIdle: false
      });

      if (!this.map.hasLayer(this.layerB) && !this.map.hasLayer(this.layerA)) {
        this.map.setBearing(this.$route.name === 'biIndex' ? 0 : -45)
        this.map.setView([30.7478613352993, 106.03655278081857], this.$route.name === 'biIndex' ? 12 : 18)
        this.map.addLayer(this[this.$route.name === 'biIndex' ? 'layerA' : 'layerB'])
      }

      

      // 设置地图初始背景色
      // const mapElement = document.getElementById('map');
      // mapElement.style.backgroundColor = '#001529';
      // 添加简单的缩放控件
      // L.control.zoom({
      //     position: 'topright'
      // }).addTo(map);

      // 添加比例尺
      L.control.scale({
          imperial: false,
          position: 'bottomleft'
      }).addTo(this.map);
      // 绑定地图点击事件
      
      // this.map.on('click', this.handleMapClick);

      // 处理瓦片加载错误
      // L.offlineTiles.on('tileerror', function (error) {
      //     console.warn('无法加载瓦片:', error);
      // });

      // 手动设置旋转角度（单位：度）
      // this.map.setBearing(-45);  // 顺时针旋转45度

      // 获取当前旋转角度
      const bearing = this.map.getBearing();
      // console.log('当前旋转角度：', bearing);
      
      // 初始化图层组
      this.markersLayer = L.layerGroup().addTo(this.map);
      this.selectionLayer = L.layerGroup().addTo(this.map);
      this.pathLayers = L.layerGroup().addTo(this.map);
      this.dashedPolygonLayer = L.layerGroup().addTo(this.map);

      this.map.on('unload', () => {
        for (let i in this.pointMarkers) {
          if (this.pointMarkers[i]._vueInstance) {
            this.pointMarkers[i]._vueInstance.$destroy()
          }
        }
      })

      this.map.on('move zoom', (e) => {
        // console.log('move zoom', e);
        
        const { lat, lng } = this.map.getCenter()
        if (this.$refs.thumbnailRef) {
          this.$refs.thumbnailRef.updateRectangle();
          this.$refs.thumbnailRef.syncZoom();
          // TODO:动态修改中心点，似乎不需要
          // this.$refs.thumbnailRef.map.setView([lat, lng]);
        }
      });
      
      // 监听缩放事件，更新图标大小
      // 缩放到停止
      this.map.on('zoomend', () => {
        // console.log('zoomend');
        

        // 监听 zoomend 事件
        var currentZoom = this.map.getZoom();
        // console.log('当前缩放级别:', currentZoom); // 使用 map.getZoom() 获取当前级别 [citation:1]

        // 根据缩放级别显示/隐藏图层
        if (currentZoom > 17 && currentZoom < 18) {
          this.map.setBearing(0)
          this.map.setView([30.7478613352993, 106.03655278081857], 12)
          if (!this.map.hasLayer(this.layerA)) {
            this.layerA.addTo(this.map);
          }
          if (this.map.hasLayer(this.layerB)) {
            this.map.removeLayer(this.layerB);
          }
          // 缩小时，显示 layerB，隐藏 layerA
        } else if(currentZoom > 12) {
          this.map.setBearing(-45)
          this.map.setView([30.7478613352993, 106.03655278081857], 18)
          
          if (!this.map.hasLayer(this.layerB)) {
            this.layerB.addTo(this.map);
          }
          if (this.map.hasLayer(this.layerA)) {
            this.map.removeLayer(this.layerA);
          }
        }
        this.pointMarkers.map((marker, index) => {
          // console.log(111111111, marker.getIcon().options, this.getIcon(marker?.meta?.robot || {}, marker.getIcon().options))
          this.pointMarkers[index].setIcon(this.getIcon(marker?.meta?.robot || {}, marker.getIcon().options))
        })
      });



      // 监听地图事件
      this.map.on('move', this.updatePopupPosition);
      this.map.on('moveend', this.updatePopupPosition);
      this.map.on('click', this.closeAll);


      // 绑定预加载
      this.map.on('movestart', this.preloadTiles);
      this.map.on('zoomstart', this.preloadTiles);
      this.map.on('load', this.preloadTiles);
      
      this.$nextTick(() => {
        // 初始更新
        this.updatePopupPosition();
        if (this.$refs.thumbnailRef) {
          this.$refs.thumbnailRef.initMap()
        }
      })

      

      // setTimeout(() => {
      //   const newPoint = [30.744399, 106.036241]
      //   this.pointMarkers[0].setLatLng(newPoint);
      //   if (this.$refs.thumbnailRef) {
      //     this.$refs.thumbnailRef.pointMarkers[0].setLatLng(newPoint);
      //     this.$refs.thumbnailRef.points[0] = newPoint
      //     this.$refs.thumbnailRef.updatePolyline()
      //   }
      // }, 6000);
    },

    preloadTiles() {
      if (!this.map || !this.tileLayer) return;
      const map = this.map;
      const tileLayer = this.tileLayer;
      const zoom = map.getZoom();
      const bounds = map.getBounds();
      const tileSize = tileLayer.getTileSize();
      
      // 计算当前视口覆盖的瓦片范围
      const nwPoint = map.project(bounds.getNorthWest(), zoom);
      const sePoint = map.project(bounds.getSouthEast(), zoom);
      const tileBounds = L.bounds(nwPoint, sePoint);
      
      // 转换为瓦片坐标
      const minX = Math.floor(tileBounds.min.x / tileSize.x);
      const maxX = Math.floor(tileBounds.max.x / tileSize.x);
      const minY = Math.floor(tileBounds.min.y / tileSize.y);
      const maxY = Math.floor(tileBounds.max.y / tileSize.y);
      
      // 扩展预加载范围
      const radius = this.preloadRadius;
        
      // 预加载周边瓦片
      for (let i = minX - radius; i <= maxX + radius; i++) {
        for (let j = minY - radius; j <= maxY + radius; j++) {
          const coords = { x: i, y: j, z: zoom };
          const key = tileLayer._tileCoordsToKey(coords);
          
          // 只加载尚未加载的瓦片
          if (!tileLayer._tiles[key]) {
            tileLayer._loadTile(coords, () => {
              // 加载完成回调（可选）
            });
          }
        }
      }
    },

    closeAll() {
      this.$refs.robot1Ref?.show(null)
      this.closePopup()
    },
    // 自动适应所有点的边界
    setCenter() {
      if (!this.map) return
      const markers = this.pointMarkers.map(marker => [marker.getLatLng().lat, marker.getLatLng().lng])
      const bounds = L.latLngBounds(markers);
      this.map.fitBounds(bounds, { padding: [20, 20] });
    },
    // 创建自定义图标
    getIcon(item, options = {}) {
      const sizeObj = ROBOT_TYPE_INFO
      const { typeName, name, type, status, robotId, customStatusName, statusClass } = item
      // console.log(item.robotId, item.status, item.customStatusName);
      
      const { width, height, img } = sizeObj[type] || sizeObj.default  
      const zoom = this.map.getZoom();
      const scale = 66 / 93
      const scaleAnchor = 33.5 / 85
      const iconSize = [(zoom * 5 + 3) * scale, zoom * 5 + 3]
      const iconAnchor = [(zoom * 4 + 13) * scaleAnchor, zoom * 4 + 13]
      
      // console.log('+++++++++', robotId, this.robotAlarmObj?.[robotId]);
      return L.divIcon(
        // options.html ? { ...options } : {
        {
          // ${this.robotAlarmObj?.[robotId] ? `
          //   <div class="robot-warning flx-center" style="height: fit-content;">
          //     <svg xmlns="http://www.w3.org/2000/svg" width="16" h eight="16" viewBox="0 0 16 16" fill="none">
          //       <path d="M15.6585 13.1145L9.38682 2.25252C8.6197 0.924516 7.36601 0.924516 6.59909 2.25252L0.327353 13.1145C-0.439564 14.4438 0.187856 15.5282 1.72062 15.5282H14.2652C15.798 15.5282 16.4248 14.4437 15.6585 13.1145ZM7.13152 5.33448C7.35689 5.09081 7.64342 4.96896 7.99287 4.96896C8.3425 4.96896 8.62877 5.08953 8.85438 5.32961C9.07852 5.57023 9.19055 5.87115 9.19055 6.233C9.19055 6.54434 8.7227 8.8337 8.56664 10.4992H7.43975C7.30289 8.83368 6.79524 6.54434 6.79524 6.233C6.79527 5.87664 6.90748 5.57696 7.13152 5.33448ZM8.8386 13.254C8.60154 13.4849 8.31945 13.6 7.99295 13.6C7.66653 13.6 7.38436 13.4849 7.14735 13.254C6.91098 13.0237 6.7935 12.7447 6.7935 12.4171C6.7935 12.0911 6.91098 11.8091 7.14735 11.5727C7.38436 11.3363 7.66653 11.2181 7.99295 11.2181C8.31945 11.2181 8.60154 11.3363 8.8386 11.5727C9.0748 11.8091 9.19256 12.0911 9.19256 12.4171C9.19256 12.7447 9.0748 13.0237 8.8386 13.254Z" fill="#FFDD00"/>
          //     </svg>
          //     <span class="ml5">告警事件：${this.robotAlarmObj[robotId].categoryName}：${this.robotAlarmObj[robotId].title}</span>
          //   </div>` : ''}
          html: `<div class="custom-point-img flx-center flex-column" style="flex-wrap: nowrap;">
            <img class="wp${width} hp${height}" src="${require(`@/assets/images/new-bi/${img}.png`)}" />
            <img src="${require(`@/assets/images/new-bi/robot_foot.png`)}" style="margin-top: -5px;" />
            <div class="custom-point-name mt2" style="">${name}</div>
            <div class="custom-point-status mt4 pr10 pl10">${customStatusName}</div>
          </div>`,
          className: `custom-point ${this.getSearchRobot(item) ? 'max-zoom' : ''} ${(this.selectedRobot.robotId === robotId || this.getSelectedStatus(robotId)) ? `show-icon show-icon-${width}-${height}` : ''} ${type} ${statusClass}` ,
          // className: `custom-point ${type} ${statusClass}` ,
          iconSize: null,
          // 偏移量
          // iconAnchor: [name.length * 7 > 24 ? name.length * 7 : 24, height],
          iconAnchor: [0, height]
      });
    },
    getSearchRobot(robot) {
      const keywords = this.mapSearchValue.split('_timestamp_')[0].toLowerCase()
      if (robot) {
        return robot.name.toLowerCase().includes(keywords) || robot.type.toLowerCase().includes(keywords) || robot.typeCode.toLowerCase().includes(keywords)
      } else {
        return this.robotList.find(item => item.name.toLowerCase().includes(keywords) || item.type.toLowerCase().includes(keywords) || item.typeCode.toLowerCase().includes(keywords))
      }
    },
    initPoints() {
      if (!this.map) return
      // const obj = 
      // Robot, Uav, UavPort, Battery
      // this.dogList = [
      //   // { ...this.dogList[0], status1: 1, type: 'robot1', typeName: '机器人' },
      //   { lat: 30.7478613352993, lng: 106.03655278081857, status1: 1, type: 'robot1', robotId: 'robot-001', typeName: '机器人001' },
      //   { lat: 30.746587087515316, lng: 106.03824884204943, status1: 2, type: 'robot_uav', robotId: 'robot-002', typeName: '无人机002' },
      //   { lat: 30.7469491, lng: 106.0344109, status1: 0, type: 'robot_car', robotId: 'robot-003', typeName: '轮式机器车003' },
      //   { lat: 30.745330, lng: 106.039428, type: 'robot_dog', robotId: 'robot-004', status1: 3, typeName: '机器狗' }
      // ]
      const latLngs = [
        { lat: 30.7478613352993, lng: 106.03655278081857, status1: 1 },
        { lat: 30.746587087515316, lng: 106.03824884204943, status1: 2 },
        { lat: 30.7469491, lng: 106.0344109, status1: 0 },
        { lat: 30.745330, lng: 106.039428, status1: 3 },
      ]
      this.robotList.map((r, index) => {
        const item = Object.assign({}, this.robotBaseInfo?.[r.robotId] || r)
        // item.points = L.latLng(latLngs[index].lat || 39.54, latLngs[index].lng || 116.23)
        const lat = this.robotLocation[item.robotId].lat || 39.54
        const lng = this.robotLocation[item.robotId].lng || 116.23
        item.points = L.latLng(lat, lng)
        const existingIndex = this.pointMarkers.findIndex(m => m.meta?.robot?.robotId === item.robotId);
        if (existingIndex >= 0) {
          // console.log(1);
          // // TODO:模拟移动
          // const randomLat = (Math.random() - 0.5) * 0.001;
          // const randomLng = (Math.random() - 0.5) * 0.001;
          // const { lat, lng } = this.pointMarkers[existingIndex].getLatLng()
          // this.pointMarkers[existingIndex].setLatLng(L.latLng(lat + randomLat, lng + randomLng))
          // this.pointMarkers[existingIndex].meta = { index, robot: { ...item, points: L.latLng(lat + randomLat, lng + randomLng) }};
          // 存在则更新 icon
          // this.pointMarkers[existingIndex].setIcon(this.getIcon(item));
          // 更新 meta 数据
          // this.pointMarkers[existingIndex].meta = { index, robot: { ...item }};

        } else {
          // console.log(2);
          // 创建点标记
          // const defaultIcon = L.icon({
          //   iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
          //   shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
          //   iconSize: [25, 41],
          //   iconAnchor: [12, 41],
          //   // popupAnchor: [1, -34]
          // })
          // L.marker(item.points, { icon: defaultIcon }).addTo(this.markersLayer)
          const marker = L.marker(item.points, { 
            icon: this.getIcon(item),
            zIndexOffset: 1000,
            // riseOnHover: true
          }).addTo(this.markersLayer)
          // 存储扩展数据，方便后续使用
          marker.meta = { index, robot: { ...item }, alarmId: this.robotAlarmObj?.[item.robotId] };
          // 不存在则添加新标记
          this.pointMarkers.push(marker);
        }
        
        // 初始化移动轨迹
        item.pathPoints = [item.points]
        item.movementPath = L.polyline(item.pathPoints, {
          // color: '#e74c3c',
          fillColor: 'linear-gradient(180deg, rgba(1, 144, 244, 0) 0%, rgba(11, 163, 245, 1) 5.51%, rgba(4, 23, 62, 0.16) 51.52%, rgba(50, 237, 250, 1) 82.62%, rgba(17, 176, 246, 1) 82.62%, rgba(7, 156, 245, 0) 100.03%)',
          weight: 6,
          // opacity: 0.6,
          className: 'movement-path'
        }).addTo(this.map);
        this.updatePopups(index);
        return item
      })
    },
    updateIconBaseInfo(info) {
      const robotId = info.robotId
      const existingIndex = this.pointMarkers.findIndex(m => m.meta?.robot?.robotId === robotId);
      if (existingIndex < 0) return
      // const { lat, lng } = this.pointMarkers[existingIndex].getLatLng()
      this.pointMarkers[existingIndex].setLatLng(L.latLng(this.robotLocation?.[robotId]?.lat, this.robotLocation?.[robotId]?.lng))
      this.pointMarkers[existingIndex].meta = { index: existingIndex, robot: { ...info, points: L.latLng(this.robotLocation?.[robotId]?.lat, this.robotLocation?.[robotId]?.lng) }};
      // 存在则更新 icon
      this.pointMarkers[existingIndex].setIcon(this.getIcon(info));
      // 更新 meta 数据
      this.pointMarkers[existingIndex].meta = { index: existingIndex, robot: { ...info }};
    },
    updatePopups(index) {
      this.setupHoverWithDebounce(this.pointMarkers[index], 300)
      return
      // 更新标记的弹出窗口
      if (this.pointMarkers[index]) {
        const dog = this.dogList[index]
        this.selectedEndPoint = dog.endpoint
        const info = this.allRobotInfo.filter(item => item.endpoint === dog.endpoint)
        const currentInfo = info.length ? info[0].customDevInfo : {}
        if (info.length) {
          // const wgs84Result = info[0].wgs84Result
          // getRecentTaskInfo(info[0].equipment.id).then(res =>{
          //   // console.log(res)
          //   this.taskName = res.data ? res.data.TaskName : ''
          // }).catch(error => {
          // })
        }
        // 纬: ${this.pointA.lat.toFixed(6)}，经: ${this.pointA.lng.toFixed(6)}
        if (this.pointMarkers[index].getPopup()) {
          // 更新数据---直接修改
          this.pointMarkers[index]._vueInstance.currentInfo = currentInfo
          this.pointMarkers[index]._vueInstance.deviceInfo = dog
        } else {          
          const popupInfo = this.getPopupContainer({ currentInfo, deviceInfo: dog, index })
          this.pointMarkers[index].bindPopup(popupInfo.popup, {
            minWidth: '394px',
            keepInView: true,
          });
          this.pointMarkers[index]._vueInstance = popupInfo.popupInstance
          this.setupHoverWithDebounce(this.pointMarkers[index], 300)
        }
      }
    },

    // 添加带防抖的鼠标悬停事件
    setupHoverWithDebounce(marker, delay = 200) {
      let hoverTimer;
      // 先移除之前绑定的事件，防止重复绑定
      marker.off('click');
      marker.off('mouseover');
      marker.off('mouseout');
      // 添加点击事件
      marker.on('click', (e) => {
        L.DomEvent.stopPropagation(e);
        if (this.$route.name === 'biIndex') {
          this.showPopup(marker);
        }
        this.$refs.robot1Ref.show(e.originalEvent, marker.meta.robot)
        // this.showDashedArea(marker.meta.index);
        if (this.activeMarkerIndex === marker.meta.index) {
          this.activeMarkerIndex = ''
        } else {
          this.activeMarkerIndex = marker.meta.index
        }
        this.clearLayer(null)
      });
    },

    // ----- 弹框控制 -----
    showPopup(marker) {
      if (this.activeMarkerIndex === marker.meta.index) {
        this.closePopup()
        return
      };
      this.popupVisible = true;
      setTimeout(() => this.updatePopupPosition())
    },
    closePopup() {
      this.popupVisible = false;
    },
    updatePopupPosition(e) {
      if (this.$route.name !== 'biIndex') return;
      const marker = this.pointMarkers[this.activeMarkerIndex]
      if (!this.popupVisible || !this.map || !marker) return;
      const latLng = marker.getLatLng();      
      if (!latLng) return;
      const point = this.map.latLngToContainerPoint(latLng);
      const robotRef1 = this.$refs.robot1Ref.$el.getBoundingClientRect()
      
      this.popupOffset = { x: point.x + 29, y: point.y - robotRef1.height - 28 };
      // this.popupOffset = { x: point.x, y: point.y };
    },
    getPopupContainer(data) {
      const components = [Robot, Uav, UavPort, Battery]
      const PopupConstructor = Vue.extend(components[data.index])
      const popupInstance = new PopupConstructor({
        propsData: {
          ...data,
          className: data.deviceInfo.status1 === 0 ? 'normal' : data.deviceInfo.status1 === 1 ? 'active' : data.deviceInfo.status1 === 2 ? 'error' : 'off',
          onShutdown: () => this.controlDevice('shutdown', this.selectedEndPoint),
          onStartup: () => this.controlDevice('startup', data.dog.id),
          onClose: () => this.closePopup(data.index),
          onCustomEvent: eventData => {
            // 监听组件事件
            // console.log(111, data, this.selectedEndPoint);
            // if (this.selectedEndPoint !== '1') {
              this.selectedEndPoint = new Date().toLocaleTimeString()
            // }
            this.closePopup(data.index);
            this.$refs.robotControlPartRef.showModal()
          },
          onAddTask: () => {
            this.closePopup(data.index);
            this.$refs.taskAddRef.showModal()
          }
        }
      })
      // 挂载到容器
      const popup = L.popup().setContent(popupInstance.$mount().$el)
      // 销毁 popup 时也销毁 Vue 组件
      popup.on('remove', () => {
          // popupInstance.$destroy();
      });
      return { popup, popupInstance };
    },
    showPathArea(flag) {
      if (flag) {
        this.redrawPolyline()
      } else {
        if (this.pathLayers.getLayers().length) {
          this.pathLayers.clearLayers()
        }
      }
    },
    // 区域图层
    showDashedArea(flag) {
      if (!flag) {
        if (this.dashedPolygonLayer.getLayers().length) this.clearLayer(null)
        return
      }
      const polygonPoints = [
        // [30.747402094262892, 106.03720949762425],  // 点1 (上偏左)
        // [30.746587087515316,106.03824884204943],  // 点2 (右上)
        // [30.745824237436622,106.03739157519864],  // 点3 (下偏右)
        // [30.746639250628817,106.03635981721821]   // 点4 (左侧)
        [ 30.748881699556307, 106.03529721123913 ],
        [ 30.7478613352993, 106.03655278081857 ],
        [ 30.747140651711415, 106.03579008416635 ],
        [ 30.74812517003875, 106.03462176616857 ],
      ];
      // 如果已有虚线多边形，移除
      if (this.dashedPolygonLayer.getLayers().length) {
        this.clearLayer('dashedPolygonLayer')
        return;
      }
      L.polygon(polygonPoints, {
        color: "#0BF9FE",          // 边框红色虚线醒目
        weight: 2,
        // opacity: 0.9,
        fillColor: "#00B2FF",      // 浅橙色半透明填充，类似背景高亮
        fillOpacity: 0.2,
        dashArray: "6, 6",         // 虚线样式: 8px实线,6px空白
        lineCap: "round",
        lineJoin: "round",
        className: "dashed-select-area"
      }).addTo(this.dashedPolygonLayer);
      // 加一个平滑的flyToBounds，提高体验，但不强制刷新map，如果超出范围则适应。
      const bounds = L.latLngBounds(polygonPoints);
      if (!this.map.getBounds().contains(bounds)) {
        this.map.flyToBounds(bounds, { padding: [30, 30], duration: 0.6 });
      }
    },
    clearLayer(key) {
      if (!key) {
        this.dashedPolygonLayer.clearLayers();
        this.pathLayers.clearLayers();
      } else {
        this[key].clearLayers();
      }
    },

    // TODO:点击地图打点-未使用
    handleMapClick(e) {
      
      // if (!this.distance1) {
      //   this.testA = { lat: e.latlng.lat, lng: e.latlng.lng };
      //   this.distance1 = 1
      // } else {
      //   this.testB = { lat: e.latlng.lat, lng: e.latlng.lng };
      //   const latLngA = L.latLng(this.testA.lat, this.testA.lng);
      //   const latLngB = L.latLng(this.testB.lat, this.testB.lng);
      //   const distance = this.map.distance(latLngA, latLngB).toFixed(2);
      //   console.log('点击了B点位置', distance);
      // }
      // if (this.settingPointB) {
      //   this.pointB = {
      //     lat: e.latlng.lat,
      //     lng: e.latlng.lng
      //   };
      //   this.pointBMarker.setLatLng([this.pointB.lat, this.pointB.lng]);
        // this.updatePopups();
      //   this.settingPointB = false;
      // }
      
      // 监听地图点击事件，添加路径点 (打点)
      // const { lat, lng } = e.latlng;
      // this.waypoints = []
      // this.addWaypoint({ lat, lng });
      // this.redrawPolyline()
    },
    // 添加路径点 (统一逻辑)
    addWaypoint(latlng) {
        if (!this.map) return;
        
        // 创建标记 (漂亮的可视化标记)
        const marker = L.marker([latlng.lat, latlng.lng], {
            draggable: false,
            riseOnHover: true,
            autoPan: true
        }).addTo(this.pathLayers);
        
        // 绑定弹出信息 (显示序号和坐标)
        const pointIndex = this.waypoints.length + 1;
        marker.bindPopup(`
            <b>📍 路径点 #${pointIndex}</b><br>
            纬度: ${latlng.lat.toFixed(6)}<br>
            经度: ${latlng.lng.toFixed(6)}
        `);
        // 新添加的点自动打开popup方便查看
        // marker.openPopup();
        
        // 存储数据
        // this.pathMarkers.push(marker);
        this.waypoints.push({ lat: latlng.lat, lng: latlng.lng });
        // 每次添加完都重新绘制折线 (连成直线)
        this.redrawPolyline();
        // 每次添加完计算总距离
        this.calcTotalDistance();
    },
    // 绘制折线 (基于 waypoints 顺序连线)
    redrawPolyline() {
      if (!this.map) return;
      // 移除旧折线
      if (this.pathLayers.getLayers().length) {
        this.pathLayers.clearLayers();
        return
      }
      const pointsCount = this.waypoints.length;
      if (pointsCount < 2) {
        return; // 不足两点无法连线
      }
      
      // 将 waypoints 转换成 Leaflet 需要的 LatLng 数组
      const latlngs = this.waypoints.map(p => {
        this.drawMarkers(p);
        return L.latLng(p.lat, p.lng);
      });
      
      // 是否闭合
      const needClose = false;
      if (needClose && pointsCount >= 3) {
        L.polygon(latlngs, {
          color: '#0090FF',
          weight: 3,
          opacity: 1,
          fillColor: 'transparent',
          fillOpacity: 0.35,
          lineJoin: 'round',
          smoothFactor: 1.2,
          dashArray: "6, 6",
          className: 'closed-polygon'
        }).addTo(this.pathLayers);
        // this.polylineLayer.bindTooltip(`🔷 闭合区域 | 周长: ${this.totalPerimeter.toFixed(2)} km | 面积: ${this.totalArea.toFixed(2)} km²`, {
        //   sticky: true,
        //   offset: [0, -10]
        // });
      } else {
        // 创建折线样式: 醒目橙色系路径
        const arr1 = latlngs.slice(0, 4);
        const arr2 = latlngs.slice(3, latlngs.length);
        L.polyline(arr1, {
          color: '#0D9F31',
          weight: 3,
          opacity: 1,
          lineCap: 'round',
          lineJoin: 'round',
          smoothFactor: 1.2,
        }).addTo(this.pathLayers);
        L.polyline(arr2, {
          color: '#0090FF',
          weight: 3,
          opacity: 1,
          lineCap: 'round',
          lineJoin: 'round',
          smoothFactor: 1.2,
        }).addTo(this.pathLayers);
        // 添加路径tooltip显示总距离
        if (pointsCount >= 2 && this.totalDistance > 0) {
          // this.polylineLayer.bindTooltip(`🚏 路径总长 ≈ ${this.totalDistance.toFixed(2)} km`, { 
          //   sticky: true, 
          //   offset: [0, -12] 
          // });
        }
      }
    },

    drawMarkers({ lat, lng, desc }) {
      const latLng = [lat, lng]
      // 使用 CircleMarker 半径设置较大 (9~11像素)，带明亮的红色/橙色填充，加白边和阴影
      const circleMarker = L.circleMarker(latLng, {
        radius: 5,               // 较大半径，突出显示
        color: '#ffffff',         // 白色边缘
        weight: 2.5,              // 边缘宽度
        opacity: 1,
        fillColor: '#2CADFF',     // 醒目的红色
        fillOpacity: 1,
      }).addTo(this.pathLayers);
      // 增强突出感: 附加一个半透明的外层光晕 Circle (半径稍大, 低透明度)
      // 这形成一个类似光晕的“远点”突出效果，更有视觉冲击
      const glowCircle = L.circleMarker(latLng, {
        radius: 9,
        color: '#0070C6',
        weight: 1,
        opacity: 0.4,
        fillColor: '#72bbf3',
        fillOpacity: 0.2,
        interactive: false      // 避免干扰点击
      }).addTo(this.pathLayers);
      const icon = L.divIcon({
        html: desc === '目的地' ? `<div class="custom-point-img1 flx-center flex-column">
          <img class="wp32 hp32" src="${require(`@/assets/images/new-bi/address.png`)}" />
          <svg-icon src="${require(`@/assets/images/new-bi/robot_foot.png`)}" style="margin-top: -5px;"></svg-icon>
          <div class="custom-point-status blue">${desc}</div>
        </div>` : `
          <div class="custom-point-img1 flx-center flex-column">
            <div class="custom-point-status green">${desc}</div>
          </div>
        `,
        className: 'custom-point-path wp50' ,
        iconSize: null,
        // 偏移量，自定义icon默认[0, 0]在左上角，需要手动设置
        iconAnchor: desc === '目的地' ? [25, 25] : [25, 11]
      })
      if (desc) {
        L.marker(latLng, { 
          icon,
          // riseOnHover: true
          interactive: false,               // 文案不交互，避免遮挡点击圆点
          zIndexOffset: 200                 // 确保显示在圆点之上
        }).addTo(this.pathLayers)
      }
      // 添加悬停提示 (tooltip) 显示坐标序号和经纬度，提升用户体验
      // const tooltipContent = `📍 点 ${idx + 1}<br/>纬度: ${point.lat.toFixed(6)}<br/>经度: ${point.lng.toFixed(6)}`;
      // circleMarker.bindTooltip(tooltipContent, {
      //     permanent: false,
      //     direction: 'top',
      //     offset: [0, -8],
      //     className: 'custom-tooltip'
      // });
        
      // 也可以增加交互点击事件，弹出更详细信息（选做）
      // circleMarker.on('click', () => {
      //     // 简单弹窗提醒，或者显示更友好信息
      //     this.map.openPopup(`<b>坐标点 ${idx+1}</b><br/>lat: ${point.lat.toFixed(6)}<br/>lng: ${point.lng.toFixed(6)}`, latLng);
      // });
  },
    
    // 计算总距离 (基于waypoints经纬度，使用haversine公式)
    calcTotalDistance() {
        if (this.waypoints.length < 2) {
            this.totalDistance = 0;
            return 0;
        }
        let total = 0;
        for (let i = 0; i < this.waypoints.length - 1; i++) {
            const p1 = this.waypoints[i];
            const p2 = this.waypoints[i+1];
            total += this.haversineDistance(p1.lat, p1.lng, p2.lat, p2.lng);
        }
        this.totalDistance = total;
        return total;
    },

    // 哈弗辛公式计算两点之间球面距离 (单位: km)
    haversineDistance(lat1, lon1, lat2, lon2) {
        const R = 6371; // 地球半径 km
        const dLat = this.toRad(lat2 - lat1);
        const dLon = this.toRad(lon2 - lon1);
        const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(this.toRad(lat1)) * Math.cos(this.toRad(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2);
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    },
    
    toRad(deg) {
        return deg * (Math.PI / 180);
    },
    
    // 模拟点位移动
    movePointA() {
      // 生成随机移动方向和小距离
      const randomLat = (Math.random() - 0.5) * 0.001;
      const randomLng = (Math.random() - 0.5) * 0.001;
      
      // 更新点A位置
      this.dogList[0].points = L.latLng({
        lat: this.dogList[0].lat + randomLat,
        lng: this.dogList[0].lng + randomLng
      });
      
      // 移动标记
      this.pointMarkers[0].setLatLng(this.dogList[0].points);
      
      // 更新移动轨迹
      this.dogList[0].pathPoints.push(this.dogList[0].points);
      this.dogList[0].movementPath.setLatLngs(this.dogList[0].pathPoints);
      
      // 更新计数
      this.moveCount++;
      this.updatePopups(0);
    },

    startMovement() {
      if (this.isMoving) return;
      
      this.isMoving = true;
      this.movementInterval = setInterval(() => {
        this.movePointA();
      }, 1000);
    },

    stopMovement() {
      if (!this.isMoving) return;
      
      this.isMoving = false;
      if (this.movementInterval) {
        clearInterval(this.movementInterval);
        this.movementInterval = null;
      }
      this.updatePopups();
    },

    resetPoints() {
      this.stopMovement();
      
      // 重置点位置
      this.pointA = { lat: this.centerPoint.lat, lng: this.centerPoint.lng };
      // this.pointB = { lat: 30.748000, lng: 106.040000 };
      
      // 更新标记位置
      this.pointAMarker.setLatLng([this.pointA.lat, this.pointA.lng]);
      // this.pointBMarker.setLatLng([this.pointB.lat, this.pointB.lng]);
      
      // 重置移动轨迹
      this.pathPoints = [[this.pointA.lat, this.pointA.lng]];
      this.movementPath.setLatLngs(this.pathPoints);
      
      // 重置计数
      this.moveCount = 0;
      
      this.updatePopups();
    },
    // 切换轨迹显示隐藏
    togglePath() {
      this.showPath = !this.showPath;
      if (this.showPath) {
        // 默认第一个
        this.dogList[0].movementPath.addTo(this.map);
      } else {
        this.map.removeLayer(this.dogList[0].movementPath);
      }
    },
    toggleSetPointB() {
      this.settingPointB = !this.settingPointB;
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
        // backgroundImage: require('../../assets/bigScreen/icon/robot_normal.png')
      }
    },
    // 一键开关机
    controlDevice(type, dogId) {
      controlDevice(type, dogId).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        }
      })
    },
    // 打开模态框
    openModal(endpoint) {
      this.selectedEndPoint = endpoint
      this.dialogVisible = true;
      // 关闭一级页面的视频监控连接
    },
    // closePopup(index) {
    //   this.pointMarkers[index].closePopup()
    //   // 关掉视频
    // },
    handleOpen() {
      this.$nextTick(() => {
        const info = this.allRobotInfo.filter(item => item.endpoint === this.selectedEndPoint)[0]
        // this.$refs.newDialogRef.open({
        //   currentImage: this.currentImage,
        //   dogId: info.id || '',
        //   dogName: info.name || '',
        //   Res: this.Res,
        //   X0: this.X0,
        //   Y0: this.Y0,
        //   deviceData: info.customDevInfo || {},
        //   dogInfo: info.equipment || {},
        //   endpoint: info.endPoint || ''
        // })
      })
    },
    // 关闭模态框
    handleClose() {
      // 打开一级页面的视频监控连接
      this.dialogVisible = false;
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
    },
    showControlPart(visible) {
      if (this.selectedRobot.type === '四足机器狗') {
        this.$refs.robotControlPartRef.show(visible)
      } else {
        this.$refs.robotCarControlPartRef.show(visible)
      }
    },
    showSlam(visible) {
      this.$refs.slamRef.show(visible)
    },
    clear() {
      this.clearLayer(null)
      this.showControlPart(false)
      this.showSlam(false)
    },
  },
  beforeDestroy() {
    this.stopMovement();
    if (this.map) {
      this.map.off('move', this.updatePopupPosition);
      this.map.off('moveend', this.updatePopupPosition);
      this.map.off('click', this.closeAll);

      
      this.map.off('movestart', this.preloadTiles);
      this.map.off('zoomstart', this.preloadTiles);

      this.map.remove();
    }
    if (this.timer) {
      clearTimeout(this.timer)
    }
  },
}
</script>

<style lang="scss" scoped>
  @import "./index.scss";
</style>