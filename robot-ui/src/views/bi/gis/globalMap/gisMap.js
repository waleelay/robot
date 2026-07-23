
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
export default {
  data() {
    return {

    }
  },
  methods: {
    initMap(){
      this.map = L.map('map', {
        center: [this.centerPoint.lat, this.centerPoint.lng],
        zoom: 17,
        maxZoom: 18,
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

      this.map.on('unload', () => {
        for (let i in this.pointMarkers) {
          if (this.pointMarkers[i]._vueInstance) {
            this.pointMarkers[i]._vueInstance.$destroy()
          }
        }
      })

      this.map.on('move zoom', () => {
        console.log(111);
        
        const { lat, lng } = this.map.getCenter()
        if (this.$refs.thumbnailRef) {
          this.$refs.thumbnailRef.updateRectangle();
          this.$refs.thumbnailRef.syncZoom();
          // TODO:动态修改中心点，似乎不需要
          // this.$refs.thumbnailRef.map.setView([lat, lng]);
        }
      });

      
      // 监听缩放事件，更新图标大小
      this.map.on('zoomend', () => {
        console.log(222);
        const zoomLevel = this.map.getZoom();
        // this.movingMarker.setIcon(createCustomIcon(zoomLevel));
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
    initPoints() {
      // 创建自定义图标
      const pointAIcon = (type, status) => L.divIcon({
        html: `<div class="custom-point-img">
          <img src="${require(`@/assets/images/new-bi/${type}-${status === 0 ? 'normal' : status === 1 ? 'active' : status === 2 ? 'error' : 'off'}.png`)}" />  
        </div>`,
        className: `custom-point ${status === 0 ? 'normal' : status === 1 ? 'active' : status === 2 ? 'error' : 'off'}` ,
        iconSize: [66,93],
        iconAnchor: [33.5, 85]
      });
      // const obj = 
      // Robot, Uav, UavPort, Battery
      this.dogList = [
        { ...this.dogList[0], status1: 1, type1: 'robot' },
        { lat: 30.745809, lng: 106.038408, status1: 2, type1: 'robot-uav' },
        { lat: 30.747294, lng: 106.036885, type1: 'robot-airpot', status1: 1 },
        { lat: 30.745330, lng: 106.039428, type1: 'robot-charge', status1: 3 }
      ]
      this.dogList.map((item, index) => {
        item.points = L.latLng(item.lat || 39.54, item.lng || 116.23)
        // 创建点标记
        const marker = L.marker(item.points, { 
          icon: pointAIcon(item.type1, item.status1),
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
        if (this.pointMarkers[index].getPopup()) {
          // 更新数据---直接修改
          this.pointMarkers[index]._vueInstance.currentInfo = currentInfo
          this.pointMarkers[index]._vueInstance.deviceInfo = dog
        } else {
          const popupInfo = this.getPopupContainer({ currentInfo, deviceInfo: dog, index })
          this.pointMarkers[index].bindPopup(popupInfo.popup, {
            minWidth: '394px',
            keepInView: true,
            // autoClose: false
          });
          this.pointMarkers[index]._vueInstance = popupInfo.popupInstance
          this.pointMarkers[index].on('popupopen', () => {
            console.log('打开');
            // this.pointMarkers[index].setPopupContent(this.pointMarkers[index]._vueInstance.$el)
            this.pointMarkers[index].getElement().classList.add('open')
          })
          this.pointMarkers[index].on('popupclose', () => {
            console.log('清空实例');
            this.pointMarkers[index].getElement().classList.remove('open')
            // 销毁后点击无响应
            // this.pointMarkers[index]._vueInstance.$destroy()
            // this.pointMarkers[index]._vueInstance = null
          })
          // console.log(111, this.pointMarkers[index].getPopup().getContent(), this.pointMarkers[index]._vueInstance);
          // console.log(this.pointMarkers[index]);
        }
      }
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
          onCustomEvent: data => {
            
            // 监听组件事件
            // console.log(111, data, this.selectedEndPoint);
            // if (this.selectedEndPoint !== '1') {
              this.selectedEndPoint = new Date().toLocaleTimeString()
            // }
          },
          onAddTask: () => {
            this.closePopup(data.index);
            this.$refs.taskAddRef.showModal()
          }
        }
      })
      // 挂载到容器
      const popup = L.popup().setContent(popupInstance.$mount().$el)
      return { popup, popupInstance };
    },
  }
}