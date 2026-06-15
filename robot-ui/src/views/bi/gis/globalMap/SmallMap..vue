<template>
  <div style="position: relative" class="w100 h100 flx-center p1" :style="{ boxShadow: selectedRobot.robotId ? 'none' : '0 0 4px 0 rgba(0, 166, 255, 0.50) inset'  }">
    <div id="small-map" class="w100 h100" style="z-index: 0;"></div>
  </div>
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
import Vue from 'vue';
import SlamMap from './slam/Index.vue'

export default {
  name: 'GisGlobalSmallMap',
  data () {
    return {
      map: null,
      pointMarkers: [],
      markersLayer: null,
      centerPoint: { lat: 30.7472254, lng: 106.03737831115724 },
      timer: null,
      movementInterval: null
    }
  },
  computed: {
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
    robots() {
      return this.$store.getters['websocketRobot/getRobots'];
    },
  },
  async mounted(){
    this.initMap();
    // this.initPoints()
  },
  methods: {
    initMap(){
      // 只能看到18层
      this.map = L.map('small-map', {
        center: [this.centerPoint.lat, this.centerPoint.lng],
        zoom: 16.2,
        maxZoom: 18,
        minZoom: 15,
        // 提高渲染能力
        // preferCanvas: true,
        // attributionControl: '版权',
        zoomControl: false,
        // markerZoomAnimation: false,
        zoomSnap: 0.1,   // 允许更精细的粒度
        zoomDelta: 0.25,  // 设置每次zoomIn/Out的步长为0.25
        zoomControl: false, // 禁用缩放控件（可选）
        touchZoom: false, // 禁止触摸缩放
        scrollWheelZoom: false, // 禁止滚轮缩放
        doubleClickZoom: false, // 禁止双击缩放
        boxZoom: false, // 禁止框选缩放
        dragging: false // 禁止拖动
      });
      // 地图底图
      L.tileLayer('/tdt/tiles/new/small/{z}/{x}/{y}.png', {
      // L.tileLayer('/tdt/tiles/light/{z}/{x}/{y}.jpg', {
        maxZoom: 18,
        minZoom: 15,
      }).addTo(this.map);
      // 初始化图层组
      this.markersLayer = L.layerGroup().addTo(this.map);
      this.map.on('unload', () => {
        for (let i in this.pointMarkers) {
          if (this.pointMarkers[i]._vueInstance) {
            this.pointMarkers[i]._vueInstance.$destroy()
          }
        }
      })
      // 禁用滚轮缩放
      this.map.scrollWheelZoom.disable()
    },
    // 自动适应所有点的边界
    setCenter() {
      if (!this.map) return
      const markers = this.pointMarkers.map(marker => [marker.getLatLng().lat, marker.getLatLng().lng])
      const bounds = L.latLngBounds(markers);
      // 会自动修改层级
      this.map.fitBounds(bounds, { padding: [20, 20] });

      // if (this.selectedRobot.robotId && this.pointMarkers?.[0]) {
      //   this.map.setView(this.pointMarkers?.[0]?.getLatLng())
      // }
    },
    // 创建自定义图标
    getIcon(item, options = {}) {      
      const sizeObj = {
        四足机器狗: {
          width: 38,
          height: 28,
          img: 'robot_dog'
        },
        轮式机器人: {
          width: 30,
          height: 28,
          img: 'robot_car'
        },
        ROBOT: {
          width: 24,
          height: 39,
          img: 'robot1'
        },
        UAV: {
          width: 43,
          height: 20,
          img: 'robot_uav'
        },
      }
      const { typeName, type, status1: status } = item      
      const zoom = this.map.getZoom();
      const scale = 66 / 93
      const scaleAnchor = 33.5 / 85
      const iconSize = [(zoom * 5 + 3) * scale, zoom * 5 + 3]
      const iconAnchor = [(zoom * 4 + 13) * scaleAnchor, zoom * 4 + 13]
      return L.divIcon(
        options.html ? { ...options, iconSize, iconAnchor } : {
          html: `<div class="custom-point-img flx-center flex-column">
            <img class="wp${sizeObj[type]?.width} hp${sizeObj[type]?.height}" src="${require(`@/assets/images/new-bi/${sizeObj[type]?.img}.png`)}" />
            ${this.selectedRobot.robotId ? `<img src="${require(`@/assets/images/new-bi/robot_foot1.png`)}" style="margin-top: -5px;" />` : ''}
          </div>`,
          className: `custom-point ${this.selectedRobot.robotId ? 'show-icon' : ''} ${type} ${status === 0 ? 'green' : status === 1 ? 'blue' : status === 2 ? 'orange' : 'gray'}` ,
          iconSize: null,
          // 偏移量
          iconAnchor: [sizeObj[type].width / 2, sizeObj[type].height]
      });
    },
    initPoints() {
      if (!this.map) return
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
      // TODO:默认坐标，后期需要实时坐标
      this.robots.filter(item => this.selectedRobot.robotId ? item.robotId === this.selectedRobot.robotId : item.robotId).map((item, index) => {
        item.points = L.latLng(latLngs[index].lat || 39.54, latLngs[index].lng || 116.23)
        // 检查是否已存在相同 robot.name 的标记
        const existingIndex = this.pointMarkers.findIndex(m => m.meta?.robot?.robotId === item.robotId);
        if (existingIndex >= 0) {
          // TODO:模拟移动
          const randomLat = (Math.random() - 0.5) * 0.001;
          const randomLng = (Math.random() - 0.5) * 0.001;
          const { lat, lng } = this.pointMarkers[existingIndex].getLatLng()
          this.pointMarkers[existingIndex].setLatLng(L.latLng(lat + randomLat, lng + randomLng))
          // 存在则更新 icon
          this.pointMarkers[existingIndex].setIcon(this.getIcon(item));
          // 更新 meta 数据
          this.pointMarkers[existingIndex].meta = { index, robot: { ...item, points: L.latLng(lat + randomLat, lng + randomLng) }};

        } else {
          // 创建点标记
          const marker = L.marker(item.points, { 
            icon: this.getIcon(item),
            zIndexOffset: 1000,
            // riseOnHover: true
          }).addTo(this.markersLayer)
          // 存储扩展数据，方便后续使用
          marker.meta = { index, robot: { ...item }};
          // 不存在则添加新标记
          this.pointMarkers.push(marker);
        }
        // 初始化移动轨迹
        // item.pathPoints = [item.points]
        // item.movementPath = L.polyline(item.pathPoints, {
        //   // color: '#e74c3c',
        //   fillColor: 'linear-gradient(180deg, rgba(1, 144, 244, 0) 0%, rgba(11, 163, 245, 1) 5.51%, rgba(4, 23, 62, 0.16) 51.52%, rgba(50, 237, 250, 1) 82.62%, rgba(17, 176, 246, 1) 82.62%, rgba(7, 156, 245, 0) 100.03%)',
        //   weight: 6,
        //   // opacity: 0.6,
        //   className: 'movement-path'
        // }).addTo(this.map);
        // console.log(`%c++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++${item.name} ${item.type} ${this.robots.length}`, 'color: #00f');
        return item
      })
      // 每5秒更新中心点
      // this.timer = setInterval(() => {
      //   this.setCenter1()
      // }, 30 * 1000);
      // this.startMovement()
    },
    setCenter1() {
      // 1. 计算平均中心
      let sumLat = 0, sumLng = 0;
      const points = this.pointMarkers.map(m => m.getLatLng());
      points.forEach(p => {
        sumLat += p.lat;
        sumLng += p.lng;
      });
      const center = L.latLng(sumLat / points.length, sumLng / points.length);
      // 2. 创建一个包含所有 marker 的 LatLngBounds 对象
      const bounds = L.latLngBounds(points);
      // 3. 获取在当前地图容器大小下，能够完整显示该 bounds 的缩放级别
      const zoom = this.map.getBoundsZoom(bounds, true); // true 表示考虑容器内边距
      // 4. 以平均中心为地图中心，并应用计算出的缩放级别
      // this.map.setView(center, zoom);
      this.map.setView(center);
    },
    // 模拟点位移动
    movePoints() {
      // 生成随机移动方向和小距离
      const randomLat = (Math.random() - 0.5) * 0.001;
      const randomLng = (Math.random() - 0.5) * 0.001;

      this.pointMarkers.map(marker => {
        const { lat, lng } = marker.getLatLng()
        
        marker.setLatLng({
          lat: lat + randomLat,
          lng: lng + randomLng
        })
      })
    },
    startMovement() {
      this.movementInterval = setInterval(() => {
        this.movePoints();
      }, 1000);
    },
  },
  watch: {
    robots: {
      handler(newVal, oldVal) {
        this.initPoints()
      },
      immediate: true
    },
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
    }
    if (this.movementInterval) {
      clearInterval(this.movementInterval)
    }
  },
}
</script>

<style lang="scss" scoped>
  @import "./index.scss";
</style>