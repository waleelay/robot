<template>
  <div id="map1" class="w100 h100"></div>
  <!-- <div id="map1" style="width: 1000px; height: 1000px;"></div> -->
</template>

<script>
import L from 'leaflet'
require('leaflet/dist/leaflet.css')
import iconError from '../../../assets/images/new-bi/icon_error.png'
import iconBgError from './../../../assets/images/new-bi/icon_bg_error.png'
export default {
  name: 'warningMap',
  data () {
    return {
      map: null,
      // centerPoint: [30.745482638228058, 106.03737831115724]
    }
  },
  props: {
    centerPoint: {
      type: Array,
      default: () => [],
    }
  },
  methods: {
    initMap() {
      
      // 初始化地图
      const map = L.map("map1", {
        center: this.centerPoint, // 北京坐标
        zoom: 18,
        attributionControl: false,
        zoomControl: false, // 隐藏默认的缩放控件
        tileSize: 256
        // 注意：Leaflet本身没有直接的backgroundColor选项
        // 需要通过其他方式设置
      });
      // 设置地图容器背景色（瓦片加载前的底色）
      document.getElementById("map1").style.backgroundColor = "#001529";

      // 注意：实际离线地图需要使用本地瓦片或离线瓦片服务
      L.tileLayer(
        // "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        `${process.env.VUE_APP_BASE_ORIGIN || location.origin || ''}/tdt/latest/{z}/{x}/{y}.png`,
        {
          attribution:
            '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
          maxZoom: 18
        }
      ).addTo(map);
      // 使用CSS滤镜调整颜色
      // osmLayer.getContainer().style.filter =
      //   "hue-rotate(180deg) saturate(1.5) brightness(0.9)";

      // 设置地图初始背景色
      const mapElement = document.getElementById('map');
      mapElement.style.backgroundColor = '#001529';
      
      // 添加标记示例
      L.marker(this.centerPoint, {
        icon: L.icon({
          iconUrl: iconError,
          iconSize: [65, 65],
        }),
        zIndexOffset: 1000 
      }).addTo( map);
      L.marker(this.centerPoint, {
        icon: L.icon({
          iconUrl: iconBgError,
          iconSize: [12, 17],
        }),
        zIndexOffset: 1000
      }).addTo(map)
      // marker.bindPopup("<b>南充</b><br>中国西部").openPopup();

      // 响应窗口大小变化，保持地图正确显示
      window.addEventListener("resize", () => {
        map.invalidateSize();
      });
    }
  },
  mounted() {
    this.initMap()
  }
}
</script>