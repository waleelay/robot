<!-- 全景地图界面-小窗口slam -->
<template>
  <div class="machine-container slam" :class="{ visible }">
    <div class="decoration wp167 hp5">
      <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
    </div>
    <div class="box wp446 hp365">
      <div class="top m4 flx-justify-between">
        <div class="title ml10">SLAM地图</div>
        <div class="close mr10" @click="visible = false">
          <svg-icon icon-class="close"></svg-icon>
        </div>
      </div>
      <div class="info-content pt10 pb20 pl10 flex" style="align-items: flex-start">
        <div ref="slamContainerRef" class="mt7 p10 slam-container">
          <div class="header flx-justify-between">
            <span>机器狗001</span>
            <!-- <span @click="zoomChange" class="curp"> -->
            <span @click="toggleFullscreen(fullscreen, 'slamImg')" class="curp">
              <svg-icon :icon-class="fullscreen ? 'close-fullscreen' : 'fullscreen1'" style="font-size: 12px"></svg-icon>
            </span>
          </div> 
          <div class="img wp412 hp232 mt10" id="slamImg" style="background: #ff0;">
            <SlamM :map="slamInfo.map" :points="slamInfo.points" :pathPointIds="slamInfo.pathPointIds" :show-labels="true" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import SlamM from './../../../patrol/slam/Index.vue'
import mapInfo from './../../../patrol/slam/mapInfo.json'
import pathInfo from './../../../patrol/slam/pathInfo.json'
import mapPoints from './../../../patrol/slam/map-points.json'
import { toggleFullscreen, getFullscreenStatus } from '../../../../../utils/fullscreen.js';
export default {
  name: 'Slam',
  components: {SlamM},
  data() {
    return {
      visible: false,
      fullscreen: getFullscreenStatus(),
      clonedContainer: null,
      slamInfo: {
        map: mapInfo.data,
        points: mapPoints.data,
        pathPointIds: this.detailPointId(),
        showLabels: true
      }
    }
  },
  methods: {
    toggleFullscreen,
    detailPointId() {
      const record = pathInfo.data
      return [...(record?.points || [])].sort((a, b) => a.pointOrder - b.pointOrder).map((item) => item.mapPointId)
    },
    show(visible) {
      this.fullscreen = false;
      this.visible = visible;
      // 清理克隆元素
      this.removeClonedContainer();
    },
    // zoomChange() {
    //   this.fullscreen = !this.fullscreen
    //   if (this.fullscreen) {
    //     // 全屏时克隆元素放到body下
    //     this.createClonedContainer();
    //   } else {
    //     // 还原时移除克隆元素
    //     this.removeClonedContainer();
    //   }
    // },
    createClonedContainer() {
      const original = this.$refs.slamContainerRef;
      if (!original) return;
      
      // 克隆元素
      this.clonedContainer = original.cloneNode(true);
      this.clonedContainer.classList.add('fullscreen-clone');
      
      // 移除scoped样式限制，添加全局样式
      this.clonedContainer.setAttribute('style', `
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%) scale(2);
        z-index: 9999;
        margin: 0;
        border-radius: 4px;
        border: 1px solid #005FCF;
        background: linear-gradient(180deg, rgba(4, 60, 149, 0.40) 0.01%, rgba(4, 33, 68, 0.30) 5.51%, rgba(4, 23, 62, 0.32) 51.52%, rgba(7, 45, 94, 0.31) 92.62%, rgba(4, 62, 151, 0.40) 100.03%);
        backdrop-filter: blur(15px);
        transition: all 0.3s ease;
      `);
      
      // 克隆的全屏元素点击关闭按钮也应该关闭全屏
      const closeBtn = this.clonedContainer.querySelector('.header span:last-child');
      if (closeBtn) {
        closeBtn.onclick = () => this.zoomChange();
      }
      
      document.body.appendChild(this.clonedContainer);
      // 隐藏原元素
      original.style.visibility = 'hidden';
    },
    removeClonedContainer() {
      if (this.clonedContainer) {
        document.body.removeChild(this.clonedContainer);
        this.clonedContainer = null;
      }
      // 显示原元素
      if (this.$refs.slamContainerRef) {
        this.$refs.slamContainerRef.style.visibility = 'visible';
      }
    }
  },
  beforeDestroy() {
    this.removeClonedContainer();
  },
}
</script>

<style lang="scss" scoped>
.machine-container.slam {
  position: fixed;
  top: 175px;
  left: 390px;
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: scale(0);
  &.visible {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: scale(1);
  }
  .box {
    // width: 944px;
    width: auto;
    background: linear-gradient(180deg, rgba(4, 60, 149, 0.40) 0.01%, rgba(4, 33, 68, 0.30) 5.51%, rgba(4, 23, 62, 0.32) 51.52%, rgba(7, 45, 94, 0.31) 92.62%, rgba(4, 62, 151, 0.40) 100.03%);
    border-color: #2A86F3;
    .slam-container {
      border-radius: 4px;
      border: 1px solid #005FCF;
      background: rgba(4, 24, 65, 0.20);
      // transition: all 0.3s ease;
      .header {
        color: #FFF;
        font-family: "Microsoft YaHei";
        font-size: 14px;
        line-height: 18px;
      }
    }
  }
}
</style>