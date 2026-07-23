<template>
  <div class="container">
    <header>
      <h1>{{ title }}</h1>
      <p class="description">{{ description }}</p>
    </header>

    <div class="map-container">
      <div id="map"></div>
      <div class="status-panel">
        <h3>点状态信息</h3>
        <div class="point-info point-a">
          <strong>A点 (移动点)</strong>
          <div class="coordinates">纬度: {{ pointA.lat.toFixed(6) }}, 经度: {{ pointA.lng.toFixed(6) }}</div>
        </div>
        <div class="point-info point-b">
          <strong>B点 (固定点)</strong>
          <div class="coordinates">纬度: {{ pointB.lat.toFixed(6) }}, 经度: {{ pointB.lng.toFixed(6) }}</div>
        </div>
        <div class="point-info">
          <strong>距离:</strong>
          <div class="coordinates">{{ distance }} 米</div>
        </div>
        <div class="point-info">
          <strong>移动次数:</strong>
          <div class="coordinates">{{ moveCount }} 次</div>
        </div>
      </div>
    </div>

    <div class="controls">
      <button 
        @click="startMovement" 
        :class="{ active: isMoving }"
        :disabled="isMoving"
      >
        开始移动
      </button>
      <button 
        @click="stopMovement" 
        :class="{ active: !isMoving }"
        :disabled="!isMoving"
      >
        停止移动
      </button>
      <button @click="resetPoints">重置点位置</button>
      <button 
        @click="togglePath" 
        :class="{ active: showPath }"
      >
        {{ showPath ? '隐藏' : '显示' }}移动轨迹
      </button>
      <button 
        @click="toggleSetPointB" 
        :class="{ active: settingPointB }"
      >
        {{ settingPointB ? '点击地图设置B点' : '设置B点位置' }}
      </button>
    </div>

    <div class="info-panel">
      <h2>功能说明</h2>
      <div class="features">
        <div class="feature">
          <h3>点标记</h3>
          <p>A点为红色移动点，B点为蓝色固定点</p>
        </div>
        <div class="feature">
          <h3>自动移动</h3>
          <p>A点每隔1秒自动移动，模拟动态位置更新</p>
        </div>
        <div class="feature">
          <h3>实时距离</h3>
          <p>实时计算并显示A点和B点之间的距离</p>
        </div>
        <div class="feature">
          <h3>移动轨迹</h3>
          <p>可显示A点的移动轨迹路径</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
export default {
  name: 'GisSimulateMove',
  data() {
    return {
      title: 'Vue2移动点标记演示',
      description: '在地图上标记A点（红色，可移动）和B点（蓝色，固定），A点每隔一秒钟自动移动位置',
      map: null,
      pointA: { lat: 30.745482638228058, lng: 106.03737831115724 },
      pointB: { lat: 30.748000, lng: 106.040000 },
      pointAMarker: null,
      pointBMarker: null,
      movementPath: null,
      pathPoints: [],
      isMoving: false,
      showPath: true,
      settingPointB: false,
      moveCount: 0,
      movementInterval: null,
      distance: 0
    }
  },
  computed: {
    // 计算两点之间的距离
    calculatedDistance() {
      if (!this.map || !this.pointA || !this.pointB) return 0;
      const latLngA = L.latLng(this.pointA.lat, this.pointA.lng);
      const latLngB = L.latLng(this.pointB.lat, this.pointB.lng);
      return this.map.distance(latLngA, latLngB).toFixed(2);
    }
  },
  mounted() {
    this.initMap();
    this.initPoints();
  },
  methods: {
    initMap() {
      // 初始化地图
      this.map = L.map('map', {
        center: [this.pointA.lat, this.pointA.lng],
        zoom: 17,
        zoomControl: false,
      });

      // 方法1：CSS 设置背景色
      // document.getElementById('map').style.backgroundColor = '#f00';
      // 方法2：通过地图容器的父元素设置
      // this.map.getContainer().style.backgroundColor = '#f00';
      // 上述方法都不能该片瓦片颜色
      /* 
        图层控制
        // 获取当前层层级
        this.map.getZoom();
        // 放大层级
        this.map.zoomIn(1);
        // 缩放层级
        this.map.zoomOut(1);
      */
      // 添加离线地图图层
      // L.tileLayer('/tdt/tiles/new/latest/{z}/{x}/{y}.png', {
      // // const customTileLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      //   attribution: '&copy; OpenStreetMap contributors',
      //   maxZoom: 18,
      //   // 允许跨域请求
      //   // crossOrigin: true
      // }).addTo(this.map);

      // 添加自定义缩放控件
      // L.control.zoom({
      //   position: "topright"
      // }).addTo(this.map);

      // 添加比例尺
      // L.control.scale({
      //   imperial: false,// 是否显示英制比例线（英里/英尺）
      //   position: "bottomleft"
      // }).addTo(this.map);

      // 绑定地图点击事件
      this.map.on('click', this.handleMapClick);
    },
    initPoints() {
      // 创建自定义图标
      const pointAIcon = L.divIcon({
        html: `<div style="background-color: #e74c3c; color: white; border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; font-weight: bold; border: 3px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.3);">A</div>`,
        className: 'point-a-icon',
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });

      const pointBIcon = L.divIcon({
        html: `<div style="background-color: #3498db; color: white; border-radius: 50%; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; font-weight: bold; border: 3px solid white; box-shadow: 0 2px 8px rgba(0,0,0,0.3);">B</div>`,
        className: 'point-b-icon',
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });

      // 创建点标记
      this.pointAMarker = L.marker([this.pointA.lat, this.pointA.lng], { 
        icon: pointAIcon,
        zIndexOffset: 1000 
      }).addTo(this.map);
      
      this.pointBMarker = L.marker([this.pointB.lat, this.pointB.lng], { 
        icon: pointBIcon,
        zIndexOffset: 900 
      }).addTo(this.map);

      // 初始化移动轨迹
      this.pathPoints = [[this.pointA.lat, this.pointA.lng]];
      this.movementPath = L.polyline(this.pathPoints, {
        color: '#e74c3c',
        weight: 3,
        opacity: 0.6,
        className: 'movement-path'
      }).addTo(this.map);

      this.updatePopups();
    },
    updatePopups() {
      // 更新标记的弹出窗口
      if (this.pointAMarker) {
        this.pointAMarker.bindPopup(`
          <b>A点 (移动点)</b><br>
          纬度: ${this.pointA.lat.toFixed(6)}<br>
          经度: ${this.pointA.lng.toFixed(6)}<br>
          状态: ${this.isMoving ? '移动中' : '静止'}<br>
          移动次数: ${this.moveCount}
        `);
      }

      if (this.pointBMarker) {
        this.pointBMarker.bindPopup(`
          <b>B点 (固定点)</b><br>
          纬度: ${this.pointB.lat.toFixed(6)}<br>
          经度: ${this.pointB.lng.toFixed(6)}
        `);
      }
    },
    handleMapClick(e) {
      if (this.settingPointB) {
        this.setPointB(e.latlng);
        this.settingPointB = false;
      }
    },

    setPointB(latlng) {
      this.pointB = {
        lat: latlng.lat,
        lng: latlng.lng
      };
      this.pointBMarker.setLatLng([this.pointB.lat, this.pointB.lng]);
      this.updatePopups();
    },

    movePointA() {
      // 生成随机移动方向和小距离
      const randomLat = (Math.random() - 0.5) * 0.001;
      const randomLng = (Math.random() - 0.5) * 0.001;
      
      // 更新点A位置
      this.pointA = {
        lat: this.pointA.lat + randomLat,
        lng: this.pointA.lng + randomLng
      };
      
      // 移动标记
      this.pointAMarker.setLatLng([this.pointA.lat, this.pointA.lng]);
      
      // 更新移动轨迹
      this.pathPoints.push([this.pointA.lat, this.pointA.lng]);
      this.movementPath.setLatLngs(this.pathPoints);
      
      // 更新计数
      this.moveCount++;
      
      this.updatePopups();
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
      this.pointA = { lat: 30.745482638228058, lng: 106.03737831115724 };
      this.pointB = { lat: 30.748000, lng: 106.040000 };
      
      // 更新标记位置
      this.pointAMarker.setLatLng([this.pointA.lat, this.pointA.lng]);
      this.pointBMarker.setLatLng([this.pointB.lat, this.pointB.lng]);
      
      // 重置移动轨迹
      this.pathPoints = [[this.pointA.lat, this.pointA.lng]];
      this.movementPath.setLatLngs(this.pathPoints);
      
      // 重置计数
      this.moveCount = 0;
      
      this.updatePopups();
    },

    togglePath() {
      this.showPath = !this.showPath;
      if (this.showPath) {
        this.movementPath.addTo(this.map);
      } else {
        this.map.removeLayer(this.movementPath);
      }
    },

    toggleSetPointB() {
      this.settingPointB = !this.settingPointB;
    }
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
    }
  },
  beforeDestroy() {
    this.stopMovement();
  },
}
</script>

<style scoped lang="scss">
@import './index.scss';
</style>
