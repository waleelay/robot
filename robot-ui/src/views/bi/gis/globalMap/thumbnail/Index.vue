<template>
  <div class="overview-container" id="overviewContainer">
    <div class="overview-header">
      <span>🗺️ 缩略图（鹰眼图）</span>
      <button class="toggle-btn" @click="toggleVisible">
        {{ visible ? '收起' : '展开' }}
      </button>
    </div>
    <div id="overviewMap" v-show="visible"></div>
  </div>
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
export default {
  name: 'Thumbnail',
  props: {
    centerPoint: {
      type: Object,
      default: () => ({})
    },
    markers: {
      type: Array,
      default: () => []
    },
    mainMap: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      map: null,
      rectangle: null,
      visible: true,
      pointMarkers: [],
      points: [],
      polyline: null
    }
  },
  methods: {
    initMap() {
      this.map = L.map('overviewMap', {
        zoomControl: false,
        attributionControl: false,
        fadeAnimation: false,
        zoomAnimation: false
      }).setView([this.centerPoint.lat, this.centerPoint.lng], 15);

      // 使用离线地图瓦片 - 根据您的实际路径和级别
      L.tileLayer('/tdt/tiles/new/{z}/{x}/{y}.png', {
        attribution: '离线地图',
        maxZoom: 20,
        minZoom: 17,
        opacity: 0.7,
        errorTileUrl: ''
      }).addTo(this.map);

      this.updateRectangle();
      this.syncZoom();

      this.map.on('click', (e) => {
        this.mainMap.setView(e.latlng, this.mainMap.getZoom());
      });
    },
    updateRectangle() {
      const bounds = this.mainMap.getBounds();
      if (this.rectangle) {
        this.rectangle.setBounds(bounds);
      } else {
        this.rectangle = L.rectangle(bounds, {
          color: '#f00',
          weight: 2,
          fill: false,
          dashArray: '5, 5'
        }).addTo(this.map);
      }
    },
    syncZoom() {
      const mainZoom = this.mainMap.getZoom();
      const zoom = Math.max(10, mainZoom - 2);
      this.map.setZoom(zoom);
    },
    toggleVisible() {
      this.visible = !this.visible;
      setTimeout(() => {
        if (this.visible) {
          this.map.invalidateSize();
          this.updateRectangle();
          this.syncZoom();
        } else {
          this.map.invalidateSize();
        }
      }, 200);
    },
    initMovingMarker() {
      this.points = this.markers.map((point, index) => {
        const { lat, lng } =  point.getLatLng()
        this.pointMarkers.push(L.marker([lat, lng], {
          icon: L.divIcon({
              html: '<div style="background-color: #ff4444; width: 8px; height: 8px; border-radius: 50%; border: 1px solid white;"></div>',
              iconSize: [8, 8]
          })
        }).addTo(this.map));
        return [lat, lng]
      })
      this.updatePolyline();
    },
    updatePolyline() {
      if (this.polyline) {
        this.polyline.setLatLngs(this.points)
      } else {
        this.polyline = L.polyline(this.points, { color: '#2196F3', weight: 2, opacity: 0.6, dashArray: '5, 10' }).addTo(this.map);
      }
    }
  },
  watch: {
    markers: {
      handler(newVal) {
        this.initMovingMarker()
      },
      // immediate: true
    },
  }
}
</script>

<style lang="scss" scoped>
@import './index.scss';
</style>
