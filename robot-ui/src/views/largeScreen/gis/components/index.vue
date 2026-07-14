<template>
  <!-- filter: invert(100%) hue-rotate(200deg); background: transparent; -->
    <!-- <div id="map" style="width: 100%; height: 100vh;"></div> -->
    <div style="position: relative; width: 100%; height: 100%;">
      <div id="map" class="w100 h100"></div>
    </div>
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
export default {
  name: 'GisMap',
  data () {
    return {
      map: null,
      pointA: { lat: 30.745482638228058, lng: 106.03737831115724 },
      pointB: { lat: 30.745482638228058, lng: 106.040000 },
      pointMarkers: [],
      movementPath: null,
      isMoving: false,
      showPath: false,
      settingPointB: false,
      moveCount: 0,
      movementInterval: null,
      distance: 0,
      centerPoint: { lat: 30.745482638228058, lng: 106.03737831115724 },
      markers: [
        // { lat: 30.745482638228058, lng: 106.03737831115724, pathPoints: [], movementPath: null },
        // { lat: 30.745482638228058, lng: 106.040000, pathPoints: [], movementPath: null }
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
      isSlam: false
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
  },
  async created() {
    window.openModal = this.openModal;
    window.closePopup = this.closePopup;
    window.controlDevice = this.controlDevice;
  },
  async mounted(){
    this.initMap();
    // this.initPoints();
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
        maxZoom: 20,
        minZoom: 17,
        // 提高渲染能力
        // preferCanvas: true,
        // attributionControl: '版权',
        zoomControl: false,
        // markerZoomAnimation: false,
      });
      // 地图底图
      // L.tileLayer('/tdt/tiles/new/{z}/{x}/{y}.png', {
      //   maxZoom: 20,
      //   minZoom: 17,
      // }).addTo(this.map);

      // 设置地图初始背景色
      const mapElement = document.getElementById('map');
      mapElement.style.backgroundColor = '#001529';
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
        html: `<div class="custom-point-img"></div>`,
        className: 'custom-point',
        iconSize: [66,93],
        iconAnchor: [33.5, 85]
      });
      
      this.dogList.map((item, index) => {
        console.log(item.lat, item.lng);
        
        item.points = L.latLng(item.lat || 39.54, item.lng || 116.23)
        // 创建点标记
        const marker = L.marker(item.points, { 
          icon: pointAIcon,
          zIndexOffset: 1000,
          // riseOnHover: true
        }).addTo(this.map)
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
        this.pointMarkers[index].bindPopup(`
          <div class="custom-modal">
            <div class="title flx-justify-between">
              <div class="text">${currentInfo.deviceName || '-'}</div>
              <div>
                <span class="status">${currentInfo.dogState || '-'}</span>
                <span class="close ml10 pr11" onclick="closePopup(${index})" title="关闭">x</span>
              </div>
            </div>
            <div class="list">
              <div class="item">装备类型：${currentInfo.dogType || '-'}</div>
              <div class="item">装备型号：${currentInfo.model || '-'}</div>
              <div class="item">控制模型：${currentInfo.controlMode || '-'}</div>
              <div class="item">当前速度：${currentInfo.speed || '-'}m/s</div>
              <div class="item">当前电量：${currentInfo.battery || '-'}%</div>
              <div class="item">调度任务：${currentInfo.task || '-'}</div>
              <div class="item">是否告警：${currentInfo.isWarning || '-'}</div>
            </div>
            <div class="operation pl17 mt10">
              <button tt1="primary" onclick="openModal('${dog.endpoint}')">远程控制</span>
              <button tt1="primary" class="pl10" onclick="controlDevice('startup', ${dog.id})">一键开机</span>
              <button tt1="primary" class="pl10" onclick="controlDevice('shutdown', ${dog.id})">一键关机</span>
            </div>
          </div>
        `, {
          minWidth: '394px',
          keepInView: true,
          // autoClose: false
        });
      }
    },
    // TODO:点击地图打点-未使用
    handleMapClick(e) {
      if (this.settingPointB) {
        this.pointB = {
          lat: e.latlng.lat,
          lng: e.latlng.lng
        };
        this.pointBMarker.setLatLng([this.pointB.lat, this.pointB.lng]);
        this.updatePopups();
        this.settingPointB = false;
      }
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
    },
    handleOpen() {
      this.$nextTick(() => {
        const info = this.allRobotInfo.filter(item => item.endpoint === this.selectedEndPoint)[0]
        this.$refs.newDialogRef.open({
          currentImage: this.currentImage,
          dogId: info.id || '',
          dogName: info.name || '',
          Res: this.Res,
          X0: this.X0,
          Y0: this.Y0,
          deviceData: info.customDevInfo || {},
          dogInfo: info.equipment || {},
          endpoint: info.endPoint || ''
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
  beforeDestroy() {
    this.stopMovement();
  },
}
</script>

<style lang="scss" scoped>
  @import "./index.scss";
</style>