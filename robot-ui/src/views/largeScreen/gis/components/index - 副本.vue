<template>
  <!-- filter: invert(100%) hue-rotate(200deg); background: transparent; -->
    <!-- <div id="map" style="width: 100%; height: 100vh;"></div> -->
    <div id="map" style="width: 100%; height: 100%;">
      <!-- <div class="custom-modal">
        <div class="title flx-justify-between">
          <div class="text">监狱点名机器人</div>
          <span class="status">运行中</span>
        </div>
        <div class="list">
          <div class="item">装备类型：四足机器狗</div>
          <div class="item">装备型号：绝影X30PRO</div>
          <div class="item">控制模型：自动控制</div>
          <div class="item">当前速度：1.5m/s</div>
          <div class="item">当前电量：98%</div>
          <div class="item">调度任务：一监区监舍人员清点</div>
          <div class="item">是否告警：否</div>
        </div>
        <div class="operation pl17 mt10">
          <el-button tt1="primary">远程控制</el-button>
          <el-button tt1="primary" class="pl10">一键开机</el-button>
          <el-button tt1="primary" class="pl10">一键关机</el-button>
        </div>
      </div> -->
    </div>
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
// import geoJson from '@/components/geojson/nanchong'
export default {
  name: 'GisMap',
  data () {
    return {
      map: null,
      pointA: { lat: 30.745482638228058, lng: 106.03737831115724 },
      pointB: { lat: 30.745482638228058, lng: 106.040000 },
      pointAMarker: null,
      pointBMarker: null,
      movementPath: null,
      pathPoints: [],
      isMoving: false,
      showPath: true,
      settingPointB: false,
      moveCount: 0,
      movementInterval: null,
      distance: 0,
      centerPoint: { lat: 30.745482638228058, lng: 106.03737831115724 }
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
  mounted(){
    this.initMap();
    this.initPoints();
    // setTimeout(() => {
    //   this.startMovement()
    // }, 2000);
    // setTimeout(() => {
    //   this.stopMovement()
    // }, 30000);
  },
  methods: {
    initMap(){
      this.map = L.map('map', {
        center: [this.centerPoint.lat, this.centerPoint.lng],
        zoom: 17,
        maxZoom: 18,
        minZoom: 12,
        // 提高渲染能力
        // preferCanvas: true,
        // attributionControl: '版权',
        zoomControl: true,
        // markerZoomAnimation: false,
      });
      // 地图底图
      // L.tileLayer('/tdt/tiles/black/{z}/{x}/{y}.png', {
      //   maxZoom: 18,
      //   minZoom: 17,
      // }).addTo(this.map);

      // 设置地图初始背景色
      const mapElement = document.getElementById('map');
      mapElement.style.backgroundColor = '#001529';
      // const marker1 = L.marker([this.centerPoint.lat, this.centerPoint.lng]).addTo(this.map);
      // marker1.setIcon(L.icon({   // 标记配置-详见leaflet官网
      //   iconUrl: require('@/assets/images/new-bi/icon.png'), // 使用require加载标记图
      //   iconSize: [66,93],
      //   iconAnchor: [33.5, 85] //设置偏移量会将图片左上角当作零点
      // }))
      // let bindPopup = L.popup({
      //   direction:'auto',//工具提示框弹出位置
      //   permanent:false,//是否永久打开，默认为false，只有在鼠标移动时打开
      //   sticky:false,//工具框是否随着鼠标移动
      //   // opacity: 0, //透明度
      //   className: 'custom-popup',
      // }).setContent('<div class="div-custom-popup"></div>')
      // marker1.bindPopup(bindPopup)


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
    },
    initPoints() {
      // 创建自定义图标
      const pointAIcon = L.divIcon({
        // <img src="@/assets/images/new-bi/icon_bg.png" alt="" width="227px" height="227px" class="icon-bg">  
        // html: `<div><div class="icon-bg"></div></div>`,
        html: `<div class="custom-point-img"></div>`,
        className: 'custom-point',
        iconSize: [66,93],
        iconAnchor: [33.5, 85]
      });
      // let bindPopup = L.popup({
      //   direction:'auto',//工具提示框弹出位置
      //   permanent:false,//是否永久打开，默认为false，只有在鼠标移动时打开
      //   sticky:false,//工具框是否随着鼠标移动
      //   // opacity: 0, //透明度
      //   className: 'custom-popup',
      // }).setContent('<div class="div-custom-popup"></div>')
      // marker1.bindPopup(bindPopup)

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
        // color: '#e74c3c',
        fillColor: 'linear-gradient(180deg, rgba(1, 144, 244, 0) 0%, rgba(11, 163, 245, 1) 5.51%, rgba(4, 23, 62, 0.16) 51.52%, rgba(50, 237, 250, 1) 82.62%, rgba(17, 176, 246, 1) 82.62%, rgba(7, 156, 245, 0) 100.03%)',
        weight: 6,
        // opacity: 0.6,
        className: 'movement-path'
      }).addTo(this.map);

      this.updatePopups();
    },

    updatePopups() {
      // 更新标记的弹出窗口
      if (this.pointAMarker) {
        // 纬: ${this.pointA.lat.toFixed(6)}，经: ${this.pointA.lng.toFixed(6)}
        this.pointAMarker.bindPopup(`
          <div class="custom-modal">
            <div class="title flx-justify-between">
              <div class="text">监区点名机器人</div>
              <span class="status">运行中</span>
            </div>
            <div class="list">
              <div class="item">装备类型：四足机器狗</div>
              <div class="item">装备型号：绝影X30PRO</div>
              <div class="item">控制模型：自动控制</div>
              <div class="item">当前速度：1.5m/s</div>
              <div class="item">当前电量：98%</div>
              <div class="item">调度任务：一监区监舍人员清点</div>
              <div class="item">是否告警：否</div>
            </div>
            <div class="operation pl17 mt10">
              <button tt1="primary">远程控制</span>
              <button tt1="primary" class="pl10">一键开机</span>
              <button tt1="primary" class="pl10">一键关机</span>
            </div>
          </div>
        `, {
          minWidth: '394px'
        });
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
      this.pointA = { lat: this.centerPoint.lat, lng: this.centerPoint.lng };
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
  beforeDestroy() {
    this.stopMovement();
  },
}
</script>

<style lang="scss" scoped>
#map {
  /* filter: invert(100%) hue-rotate(200deg); background: transparent;
  filter: grayscale(100%) invert(100%) sepia(15%) hue-rotate(180deg) saturate(1040%) brightness(70%) contrast(90%); */
}
.my-custom-icon {
  width: 66px !important;
  height: 93px !important;
  background: rgba(255, 0, 0, 0.5);
}
.custom-tooltip.leaflet-tooltip {
  background: transparent;
  border: none;
  box-shadow: none;
  pointer-events: auto
}
.custom-tooltip::before {
  /* display: none !important; */
}
.div-custom-tooltip, .div-custom-popup {
  width: 354px;
  height: 309px;
  background: url(~@/assets/images/new-bi/tooltip.png) no-repeat center center;
}
.custom-popup .leaflet-popup-content {
  width: 354px !important;
  height: 309px !important;
  margin: 0 !important;
  margin-top: 20px !important;
  background: url(~@/assets/images/new-bi/tooltip.png) no-repeat center center;
}
.leaflet-popup-tip-container {
  display: none;
}

::v-deep .custom-modal1 {
  position: relative;
  width: 396px;
  height: 400px;
  background: url(~@/assets/images/new-bi/tooltip.png) no-repeat top left;
  // border: 1px solid #f00;
  // position: absolute;
  // top: 400px;
  // left: 600px;
  // z-index: 500;
  &::after {
    content: "";
    width: 50px;
    height: 76px;
    position: absolute;
    background: url(~@/assets/images/new-bi/tooltip-line.png) no-repeat bottom right;
    background-size: 100% 100%;
    right: 0;
    bottom: 0;
  }
  .title {
    width: 330px;
    margin: 18px 9px 0 14px;
    .text {
      color: #FFF;
      font-family: "Alibaba PuHuiTi";
      font-size: 18px;
      font-style: normal;
      font-weight: 400;
      line-height: normal;
    }
    .status {
      padding: 6px 12px;
      border-radius: 2px;
      border: 1px solid #4AB8FF;
      background: rgba(17, 69, 108, 0.50);
      color: #6AC5FF;
      text-align: center;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      font-style: normal;
      font-weight: 400;
      line-height: 12px; /* 100% */
      letter-spacing: 0.857px;
    }
  }
  .list {
    margin-top: 18px;
    margin-left: 17px;
    .item {
      color: #FFF;
      font-family: Inter;
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: normal;
      & + .item {
        margin-top: 11px;
      }
    }
  }
  .operation {
    .span {
      &[tt1="primary"] {
        padding: 10px !important;
        border-radius: 2px;
        border: 1px solid #74EEFF;
        color: #159AFF;
        text-align: center;
        font-family: "Alibaba PuHuiTi";
        font-size: 12px;
        font-style: normal;
        font-weight: 400;
        line-height: 12px; /* 100% */
        letter-spacing: 0.857px;
        * {
          color: #159AFF;
          font-size: 12px;
        }
        &.is-disabled {
          color: #326e9c;
          background: rgba(9, 45, 72, 0.50);
          box-shadow: 0 0 4px 0 #224D69 inset;
        }
      }
    }
  }
}

::v-deep .leaflet-map-pane {
  .leaflet-popup-content-wrapper, .leaflet-popup-tip {
    background: transparent;
    box-shadow: none;
  }
  .leaflet-popup {
    right: 30px;
    bottom: 2px !important;
    left: unset !important;
  }
  .leaflet-popup-content-wrapper, .leaflet-popup-content {
    margin: 0;
  }
  .leaflet-marker-pane {
    .custom-point {
      position: relative;
      background: transparent;
      border: none;
      .custom-point-img {
        width: 100%;
        height: 100%;
        background: url(~@/assets/images/new-bi/icon.png) no-repeat center center;
      }
      &::before {
        position: absolute;
        opacity: 0;
        top: -67px;
        left: -80.5px;
        width: 227px;
        height: 227px;
        background: url(~@/assets/images/new-bi/icon_bg.png) no-repeat center center;
        transition: all linear 0.3s;
        content: '';
        z-index: -1;
      }
      &:hover {
        &::before {
          opacity: 1;
        }
      }
    }
  }

  .leaflet-popup-pane {
    .custom-modal {
      position: relative;
      width: 396px;
      height: 400px;
      background: url(~@/assets/images/new-bi/tooltip.png) no-repeat top left;
      // border: 1px solid #f00;
      // position: absolute;
      // top: 400px;
      // left: 600px;
      // z-index: 500;
      &::after {
        content: "";
        width: 50px;
        height: 76px;
        position: absolute;
        background: url(~@/assets/images/new-bi/tooltip-line.png) no-repeat bottom right;
        background-size: 100% 100%;
        right: 0;
        bottom: 0;
      }
      .title {
        width: 354px;
        padding: 18px 9px 0 14px;
        .text {
          color: #FFF;
          font-family: "Alibaba PuHuiTi";
          font-size: 18px;
          font-style: normal;
          font-weight: 400;
          line-height: normal;
        }
        .status {
          padding: 6px 12px;
          border-radius: 2px;
          border: 1px solid #4AB8FF;
          background: rgba(17, 69, 108, 0.50);
          color: #6AC5FF;
          text-align: center;
          font-family: "Alibaba PuHuiTi";
          font-size: 12px;
          font-style: normal;
          font-weight: 400;
          line-height: 12px; /* 100% */
          letter-spacing: 0.857px;
        }
      }
      .list {
        margin-top: 18px;
        margin-left: 17px;
        .item {
          color: #FFF;
          font-family: Inter;
          font-size: 14px;
          font-style: normal;
          font-weight: 400;
          line-height: 17px;
          & + .item {
            margin-top: 11px;
          }
        }
      }
      .operation {
        button {
          &[tt1="primary"] {
            padding: 10px;
            color: #FFF;
            text-align: center;
            font-family: "Alibaba PuHuiTi";
            font-size: 12px;
            font-style: normal;
            font-weight: 400;
            line-height: 12px; /* 100% */
            letter-spacing: 0.857px;
            border-radius: 2px;
            border: 1px solid #74EEFF;
            background: rgba(9, 45, 72, 0.50);
            box-shadow: 0 0 20px 0 #69C4FF inset;
            & + button {
              margin-left: 10px;
            }
          }
        }
      }
    }
  }

}
</style>