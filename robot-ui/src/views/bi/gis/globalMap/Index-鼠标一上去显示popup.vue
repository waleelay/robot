<template>
  <!-- filter: invert(100%) hue-rotate(200deg); background: transparent; -->
    <!-- <div id="map" style="width: 100%; height: 100vh;"></div> -->
  <div style="position: relative; width: 100%; height: 100%;" class="flx-center">
    <!-- <MapTool style="z-index: 1;" /> -->
    <SlamMap :dogId="'dogId'" :style="{ display: isSlam ? 'block' : 'none' }" />
    <template>
      <div id="map" class="w100 h100" style="z-index: 0;" :style="{ display: !isSlam ? 'block' : 'none' }"></div>
      <Thumbnail v-if="showThumbnail" :centerPoint="centerPoint" :markers="pointMarkers" :mainMap="map" ref="thumbnailRef" />
    </template>
    <div class="oper" style="display: none">
      <el-select
        tt="primary"
        v-model="selectedMapId"
        placeholder="选择地图"
        style="min-width: 124px;"
        popper-class="custom-select control-select-popper p10"
        @change="changeMap"
      >
        <el-option
          v-for="(image, index) in MapIdOptions"
          :key="index"
          :label="image.label"
          :value="image.value">
        </el-option>
      </el-select>
      <!-- <el-checkbox v-model="showPath" class="custom-checkbox ml20" size="large">显示路径</el-checkbox>
      <button tt="primary" @click="startMovement">开始移动</button>
      <button tt="primary ml20" @click="stopMovement">停止移动</button> -->
    </div>
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
// import { Point, LineString } from 'ol/geom';
// import { Icon, Style, Stroke, Fill } from 'ol/style'
// import { ScaleLine, Zoom } from 'ol/control'
// import { fromLonLat, toLonLat } from 'ol/proj'
import { chargeStateObj, controlModeObj, locationObj, motionStateObj, motorStateObj } from '../../js/constants/robot-dog-constants.js';
import { controlDevice } from '../../../../api/bi.js';
import { listMapInfo } from '../../../../api/rsp/map.js';
import { listMotion } from '../../../../api/rsp/equipment.js';
import { getBasicMessage } from "@/api/menu"
import Robot from './popup/Robot.vue';
import Vue from 'vue';
// import MapTool from './MapTool.vue';
import TaskAdd from './../../components/modal/TaskAdd.vue'
import Uav from './popup/Uav.vue';
import UavPort from './popup/UavPort.vue';
import Battery from './popup/Battery.vue';
import Thumbnail from './thumbnail/Index.vue'
import SlamMap from './slam/Index.vue'
import RobotControlPart from '../../components/modal/RobotControlPart.vue';

export default {
  name: 'GisGlobalMap',
  components: { TaskAdd, Thumbnail, SlamMap, RobotControlPart },
  dicts: ['qh_motion_equipment_type', 'qh_motion_equipment_model_number'],
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
      dogList: [],
      endpoint: '',
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
      deviceDataList: [],
      deviceObj: {
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
      selectedEndPoint: '',
      isSlam: false,
      // 显示缩略图
      showThumbnail: false
    }
  },
  computed: {
    // 计算两点之间的距离
    calculatedDistance() {
      if (!this.map || !this.pointA || !this.pointB) return 0;
      const latLngA = L.latLng(this.pointA.lat, this.pointA.lng);
      const latLngB = L.latLng(this.pointB.lat, this.pointB.lng);
      return this.map.distance(latLngA, latLngB).toFixed(2);
    },
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
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
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        this.setCenter()
        if (newMessage && newMessage.length > 0) {
          // TODO:增加经纬度定位
          // const message = newMessage[newMessage.length - 1];
          // if (message) {
          //   this.handlebasicMessage(message)
          // }
          this.allRobotInfo = newMessage.map(item => {
            if (item.items && item.items.posX != null) {
              const { posX, posY, res } = item.items
              this.Res = res
              let PixelX = (posX - (this.X0))/(0.1);
              let PixelY = this.mapHeight - (posY - (this.Y0))/(0.1)
              this.robot[item.endpoint] = {
                x: PixelX/this.mapWidth*100,
                y: PixelY/this.mapHeight*100,
                lat: item.wgs84Result.latitude,
                lng: item.wgs84Result.longitude
                // status: items.electricity !== null ? '在线' : '离线',
                // imagePath: require(`../../assets/bigScreen/icon/robot_${items.electricity === null ? 'error' : 'normal'}.png`)
              }
            }
            if (item.items && item.items.electricity != null) {
              const { motionState, sumOdom, curOdom, location, motorState, chargeState, controlMode, speed, electricity, onDockState } = item.items
              const { type, modelNumber, name } = item.equipment
              const { latitude, longitude } = item.wgs84Result || {}
              this.dogList.filter((dog, index) => {
                if (item.endpoint === dog.endpoint) {
                  // item.lat = latitude || '30.748000';
                  // item.lng = longitude || '106.040000'
                  item.lat = latitude;
                  item.lng = longitude
                }
                this.pointMarkers[index].g
                this.pointMarkers[index].setLatLng([latitude, longitude])
                this.updatePopups(index)
                return item.endpoint === dog.endpoint
              })
              return {
                ...item,
                customDevInfo: {
                  ...JSON.parse(JSON.stringify(this.deviceObj)),
                  status: '在线',
                  sumOdom: sumOdom,
                  curOdom: curOdom,
                  motionState: motionStateObj[motionState],
                  location: locationObj[location],
                  motorState: motorStateObj[motorState],
                  chargeState: chargeStateObj[chargeState],
                  controlMode: controlModeObj[controlMode],
                  onDockState: onDockState,
                  speed: speed,
                  battery: electricity,
                  dogType: this.selectDictLabel(this.dict.type.qh_motion_equipment_type, type),
                  model: this.selectDictLabel(this.dict.type.qh_motion_equipment_model_number, modelNumber),
                  deviceName: name,
                  dogState: '正常'
                },
                lat: latitude || '30.748000',
                lng: longitude || '106.040000'
              }
            }
          })
          if (!this.loadMap && isSlam) return
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
    },
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
    this.initPoints()

    // this.initPoints();
    // setTimeout(() => {
    //   this.startMovement()
    // }, 2000);
    // setTimeout(() => {
    //   this.stopMovement()
    // }, 30000);
  },
  methods: {
    changeMapType() {
      this.isSlam = !this.isSlam
    },
    initMap(){
      // 只能看到18层
      this.map = L.map('map', {
        center: [this.centerPoint.lat, this.centerPoint.lng],
        zoom: 18,
        maxZoom: 18,
        minZoom: 17,
        rotate: true,
        rotateControl: true, // 显示旋转控制按钮
        // 提高渲染能力
        // preferCanvas: true,
        // attributionControl: '版权',
        zoomControl: false,
        // markerZoomAnimation: false,
        zoomSnap: 0.1,   // 允许更精细的粒度
        zoomDelta: 0.25  // 设置每次zoomIn/Out的步长为0.25
      });
      // 关键：手动添加旋转控件到地图
      L.control.rotate().addTo(this.map);
      // 地图底图
      L.tileLayer('/tdt/tiles/new/latest/{z}/{x}/{y}.png', {
      // L.tileLayer('/tdt/tiles/light/{z}/{x}/{y}.jpg', {
        maxZoom: 18,
        minZoom: 17,
      }).addTo(this.map);

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
      this.map.setBearing(-45);  // 顺时针旋转45度

      // 获取当前旋转角度
      const bearing = this.map.getBearing();
      // console.log('当前旋转角度：', bearing);
      
      // 初始化图层组
      this.markersLayer = L.layerGroup().addTo(this.map);
      this.selectionLayer = L.layerGroup().addTo(this.map);
      this.pathLayers = L.layerGroup().addTo(this.map);

      this.map.on('unload', () => {
        for (let i in this.pointMarkers) {
          if (this.pointMarkers[i]._vueInstance) {
            this.pointMarkers[i]._vueInstance.$destroy()
          }
        }
      })

      this.map.on('move zoom', () => {
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
        this.pointMarkers.map((marker, index) => {          
          this.pointMarkers[index].setIcon(this.getIcon(null, marker.getIcon().options))
        })
      });


      this.$nextTick(() => {
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
    // 自动适应所有点的边界
    setCenter() {
      if (!this.map) return
      const markers = this.pointMarkers.map(marker => [marker.getLatLng().lat, marker.getLatLng().lng])
      const bounds = L.latLngBounds(markers);
      this.map.fitBounds(bounds, { padding: [20, 20] });
    },
    // 创建自定义图标
    getIcon(item, options = {}) {
      const sizeObj = {
        'robot1': { width: 24, height: 39 },
        'robot_uav': { width: 43, height: 20 },
        'robot_car': { width: 30, height: 28 },
        'robot_dog': { width: 38, height: 28 },
      }
      
      const { typeName, type1: type, status1: status } = item
      const zoom = this.map.getZoom();
      const scale = 66 / 93
      const scaleAnchor = 33.5 / 85
      const iconSize = [(zoom * 5 + 3) * scale, zoom * 5 + 3]
      const iconAnchor = [(zoom * 4 + 13) * scaleAnchor, zoom * 4 + 13]
      return L.divIcon(
        options.html ? { ...options, iconSize, iconAnchor } : {
          html: `<div class="custom-point-img flx-center flex-column">
            <img class="wp${sizeObj[type]?.width} hp${sizeObj[type]?.height}" src="${require(`@/assets/images/new-bi/${type}.png`)}" />
            <img src="${require(`@/assets/images/new-bi/robot_foot.png`)}" style="margin-top: -5px;" />
            <div class="custom-point-name mt2">${typeName}</div>
            <div class="custom-point-status mt4 pr10 pl10">${status === 0 ? '空闲中' : status === 1 ? '任务中' : status === 2 ? '故障' : '离线'}</div>
          </div>`,
          className: `custom-point ${type} ${status === 0 ? 'green' : status === 1 ? 'blue' : status === 2 ? 'orange' : 'gray'}` ,
          iconSize: null,
          // 偏移量
          iconAnchor
      });
    },
    initPoints() {
      // const obj = 
      // Robot, Uav, UavPort, Battery
      this.dogList = [
        // { ...this.dogList[0], status1: 1, type1: 'robot1', typeName: '机器人' },
        { lat: 30.7472254, lng: 106.03737831115724, status1: 1, type1: 'robot1', typeName: '机器人' },
        { lat: 30.7484721, lng: 106.0336125, status1: 2, type1: 'robot_uav', typeName: '无人机' },
        { lat: 30.7469491, lng: 106.0344109, status1: 0, type1: 'robot_car', typeName: '轮式机器车' },
        { lat: 30.745330, lng: 106.039428, type1: 'robot_dog', status1: 3, typeName: '机器狗' }
      ]
      this.dogList.map((item, index) => {
        item.points = L.latLng(item.lat || 39.54, item.lng || 116.23)
        // 创建点标记
        const marker = L.marker(item.points, { 
          icon: this.getIcon(item),
          zIndexOffset: 1000,
          // riseOnHover: true
        }).addTo(this.markersLayer)
        // 存储扩展数据，方便后续使用
        marker.meta = { index, points: item.points };
        this.pointMarkers.push(marker)
        
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

    updatePopups(index) {
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
            // 设置没效果
            // offset: L.point(155, 150), // x: 向右偏移, y: 向上偏移（相对右下角）
            // autoPan: true,
            // autoPanPadding: L.point(50, 50),
            // closeButton: false, // 隐藏默认关闭按钮，使用组件内的
            // className: 'vue-custom-popup'
          });
          this.pointMarkers[index]._vueInstance = popupInfo.popupInstance
          this.setupHoverWithDebounce(this.pointMarkers[index], 300)
        }
      }
    },

    // 添加带防抖的鼠标悬停事件
    setupHoverWithDebounce(marker, delay = 200) {
      let hoverTimer;
      // 添加点击事件
      marker.on('click', (e) => {
        L.DomEvent.stopPropagation(e);
        this.showDashedArea(marker.meta.index);
        this.redrawPolyline()
        marker.openPopup();
      });

      // marker.on('mouseover', () => {
      //   clearTimeout(hoverTimer);
      //   // 立即显示
      //   marker.openPopup();
      // });
      
      // marker.on('mouseout', () => {
      //   hoverTimer = setTimeout(() => {
      //     marker.closePopup();
      //   }, delay);
      // });
      
      // 处理 popup 内部的鼠标事件（防止鼠标移到 popup 上时关闭）
      marker.on('popupopen', () => {
        const popupElement = marker.getPopup().getElement();
        if (popupElement) {
          // popupElement.addEventListener('mouseenter', () => {
          //   clearTimeout(hoverTimer);
          // });
          // popupElement.addEventListener('mouseleave', () => {
          //   marker.closePopup();
          // });
          marker.getElement().classList.add('open')
        }
      });
      // marker.on('popupopen', () => {
      //   console.log('打开');
      //   // marker.setPopupContent(marker._vueInstance.$el)
      //   marker.getElement().classList.add('open')
      // })
      marker.on('popupclose', () => {
        console.log('清空实例');
        marker.getElement().classList.remove('open')
        // 销毁后点击无响应
        // this.pointMarkers[index]._vueInstance.$destroy()
        // this.pointMarkers[index]._vueInstance = null
      })
      // console.log(111, this.pointMarkers[index].getPopup().getContent(), this.pointMarkers[index]._vueInstance);
      // console.log(this.pointMarkers[index]);
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

    // 区域图层
    showDashedArea(markerIndex) {
      if (this.activeMarkerIndex !== markerIndex && this.dashedPolygonLayer) {
        this.clearLayer()
      }
      this.activeMarkerIndex = markerIndex
      const polygonPoints = [
        [30.747402094262892, 106.03720949762425],  // 点1 (上偏右)
        [30.746587087515316,106.03824884204943],  // 点2 (右上)
        [30.745824237436622,106.03739157519864],  // 点3 (下偏右)
        [30.746639250628817,106.03635981721821]   // 点4 (左侧)
      ];
      // 如果已有虚线多边形，移除
      if (this.dashedPolygonLayer) {
        this.clearLayer()
        return;
      }
      this.dashedPolygonLayer = L.polygon(polygonPoints, {
        color: "#0BF9FE",          // 边框红色虚线醒目
        weight: 2,
        // opacity: 0.9,
        fillColor: "#00B2FF",      // 浅橙色半透明填充，类似背景高亮
        fillOpacity: 0.2,
        dashArray: "6, 6",         // 虚线样式: 8px实线,6px空白
        lineCap: "round",
        lineJoin: "round",
        className: "dashed-select-area"
      }).addTo(this.map);
      // 加一个平滑的flyToBounds，提高体验，但不强制刷新map，如果超出范围则适应。
      const bounds = L.latLngBounds(polygonPoints);
      if (!this.map.getBounds().contains(bounds)) {
        this.map.flyToBounds(bounds, { padding: [30, 30], duration: 0.6 });
      }
    },
    clearLayer() {
      this.map.removeLayer(this.dashedPolygonLayer);
      this.dashedPolygonLayer = null;
    },

    // TODO:点击地图打点-未使用
    handleMapClick(e) {
      // if (this.settingPointB) {
      //   this.pointB = {
      //     lat: e.latlng.lat,
      //     lng: e.latlng.lng
      //   };
      //   this.pointBMarker.setLatLng([this.pointB.lat, this.pointB.lng]);
      //   this.updatePopups();
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
        // 偏移量
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
    // TODO:获取地图的像素长宽-未使用
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

      // setTimeout(() => {
      //   // this.$refs.lineRef.init()
      //   this.renderPath()
      // }, 100);
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
      this.$emit('getDogList', { dogList: this.dogList })
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
        // backgroundImage: require('../../assets/bigScreen/icon/robot_normal.png')
      }
    },
    getDogList(mapInfoId) {
      let queryParams = {dogState: 1, pageNum: 1, pageSize: 50,}
      listMotion({ mapInfoId, stateIn: '1,2,3' }).then(res => {
        this.dogList = res.rows
        console.log('根据mapId查机器狗列表', res);
        if (this.dogList.length > 0) {
          this.dogList[0] = { ...this.dogList[0], lat: 30.7472254, lng: 106.03737831115724, pathPoints: [], movementPath: null, points: null }
          // this.dogList.push({ ...this.dogList[0], id: 38, name: 'XXXXXXXX', lat: 30.7472254, lng: 106.040000, pathPoints: [], movementPath: null, points: null })
          // this.dogList.push({ ...this.dogList[0], qhMountedEquipmentList: [], id: 38, lat: '', lng: '', name: 'XXXXXXXX', pathPoints: [], movementPath: null, points: null })
          this.changeDog(this.dogList[0].id)
          // 拿到路径点位信息
        }
        this.initPoints()
      })
    },
    onPopoverShow(dog) {
      this.selectedEndPoint = dog.endpoint
      const info = this.allRobotInfo.filter(item => item.endpoint === dog.endpoint)
      if (info && info.length) {
        // getRecentTaskInfo(info[0].id).then(res =>{
        //   // console.log(res)
        //   this.taskName = res.data ? res.data.TaskName : ''
        // }).catch(error => {
        // })
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
    closePopup(index) {
      this.pointMarkers[index].closePopup()
      // 关掉视频
    },
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
  beforeDestroy() {
    this.stopMovement();
  },
}
</script>

<style lang="scss" scoped>
  @import "./index.scss";
</style>