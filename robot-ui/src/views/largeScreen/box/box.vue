<!--
 * @Author: dengxumei
 * @Date: 2025-10-27 18:10:50
 * @LastEditors: dengxumei
 * @LastEditTime: 2025-11-25 09:56:09
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\largeScreen\box\box.vue
 * @Version: 
-->
<template>
  <div class="video-div">
    <!-- <div class="t3">周界巡逻机器狗上装设备-视频监控</div> -->
    <!-- <MonitorRstp ref="monitorRspRef" @change="videoMotionControl($event)" :info="getMountedEquipmentInfo(robotTabs[tabIndex].qhMountedEquipmentList, 'video')" style="height: 246px; background: #797979;" idName="monitor-video" /> -->
    <div :id="`${prefixId}video-container`" class="video-container four-video flx-align-center w100 h100" :class="{'flx-center': deviceList.length === 1}">
      <!-- <div :class="`video-box w100 ${prefixId}video-box ${prefixId}video-box${index}`" v-for="(item, index) in deviceList" :key="item.id"> -->
      <div :class="`video-box w100 ${prefixId}video-box ${prefixId}video-box${index}`" v-for="(item, index) in deviceList" :key="item.id" :style="{ height: getHeight(`${prefixId}video-box${index}`, item.draw_size, index) }">
        
        <div class="tips" :id="`${prefixId}tip${index}`">
          <template v-if="item !== 'stop'">
            <div class="icon-dot" v-if="item === 'loading'"></div>
            <div class="deviceoffline" v-if="item === 'off'">
              <i class="z-icon-jiankonglixian" style="font-size: 40rem"></i>
              <span>离线</span>
            </div>
          </template>
        </div>
        <!-- flx-justify-between -->
        <div class="title-container" :id="`${prefixId}video-title${index}`" v-if="ZQL_sources[ZQL_playingSource[index]]" style="position: relative; z-index: 2; font-size: 22px">
          <!-- <div class="camera">{{ ZQL_sources[ZQL_playingSource[index]].desc }}</div> -->
          <!-- <div class="alg d-flex" style="justify-content: flex-end">       -->
            <!-- <div class="algname">算法: {{ ZQL_playingSource[index].alg ? ZQL_sources[ZQL_playingSource[index]].alg[alg].reserved_args.ch_name : '' }}</div> -->
            <!-- <el-radio-group v-model="selectAlg" @change="($event) => handleClickAlg($event, index)">
              <el-radio v-for="alg in ZQL_sources[ZQL_playingSource[index]].alg" :key="alg.alg_type" :label="alg.alg_type" style="color: #fff; font-size: 20px;">
                {{ alg.reserved_args.ch_name }}
              </el-radio>
            </el-radio-group> -->
            <el-select :popper-append-to-body="false" v-model="selectAlg" clearable placeholder="请选择算法" @change="($event) => handleClickAlg($event, index)">
              <el-option
                v-for="alg in revertAlgs(index)"
                :key="alg.customKey"
                :label="alg.reserved_args.ch_name"
                :value="alg.customKey"
              />
            </el-select>
          <!-- </div> -->
          <!-- <div @click="closeVideo(index)">关闭</div> -->
        </div>
        <video ref="video" muted :id="`${prefixId}video${index}`" class="video " autoplay="autoplay" preload="auto"></video>
        <canvas class="canvas-shuju" :id="`${prefixId}canvas${index}`" style="z-index: 1; position: absolute;cursor: pointer;" @click="handleClickVideo(index)"></canvas>
        <button class="control-btn full-btn"
          @click="toggleFullscreen(index)"
          title="全屏"
          :style="{ display: isFullScreen ? 'block' : 'none', zIndex: 2 }">
          <i>⛶</i>
        </button>
      </div>
    </div>
    <!-- <div id="video-container" class="video-container one-video">
      <div class="video-box w100" id="video-box" :class="{ 'controls-visible': isFullScreen }" style="cursor: pointer;">
        <div class="title-container" id="video-title1">
          <div class="title-container flx-justify-between" style="position: relative; z-index: 2; font-size: 22px; color: red;">
            <div class="alg d-flex flx-align-center">
              <el-select v-model="selectDevice" style="width: 120px;" placeholder="请选择算法">
                <el-option :value="1">测试1</el-option>
                <el-option :value="2">测试2</el-option>
                <el-option :value="3">测试3</el-option>
              </el-select>
            </div>
          </div>
        </div>
        <video ref="video" muted id="video1" class="video w100" autoplay="autoplay" preload="auto" style="border: 1px solid #f00;" @click="handleClickVideo()"></video>
        <canvas class="canvas-shuju" id="canvas1"></canvas>
        <button class="control-btn full-btn"
          @click="toggleFullscreen"
          title="全屏"
          :style="{ display: isFullScreen ? 'block' : 'none' }">
          <i>⛶</i>
        </button>
      </div>
    </div> -->
    <!-- <div class="right-container ml50" style="display: block;">
      <el-radio-group v-model="selectDevice" @change="handleClickDevice">
        <el-radio v-for="item in deviceList" :key="item.sourceId" :label="item.sourceId" style="color: #fff; font-size: 20px;">{{item.desc}}</el-radio>
      </el-radio-group>
    </div> -->
  </div>
</template>

<script>
import live from './box-live.js'
export default {
  name: 'box',
  mixins: [live],
  props: {
    prefixId: {
      type: String,
      default: '',
      required: true
    },
    dogId: {
      type: [String, Number],
      default: '',
    }
  },
  data() {
    return {
      checkboxList: [],
      selectDevice: '',
      selectAlg: '',
      clickCount: 0,
      isFullScreen: false,
      resizeObserver: null
    }
  },
  methods: {
    handleClickDevice(selectDevice) {
      this.selectDevice = selectDevice
      const device = this.deviceList.filter(item => item.sourceId === selectDevice)[0]
      if (device.sourceId) {
        let key = device.sourceId;
        if (this.ZQL_sources[key].checked == false) {
          this.ZQL_sources[key].checked = true
          if (this.ZQL_playingSource.videoNum == 1) {
            this.ZQL_playingSource[0] = key;
            this.subscribeLive(key, 0);
            this.setAlgList(0);
          } else {
            for (let i = 0; i < 4; i++) {
              if (!this.ZQL_playingSource[i]) {
                this.ZQL_playingSource[i] = key;
                this.subscribeLive(key, i);
                this.setAlgList(i)
                break;
              }
            }
          }
        } else {
          this.ZQL_sources[key].checked = false
          if (this.ZQL_playingSource.videoNum == 1) {
            this.ZQL_playingSource[0] = null;
            this.destoryVideoByIndex(0);
            this.clearAlgList(0);
            this.liveStopLoading(0);
          } else {
            for (let i = 0; i < 4; i++) {
              if (this.ZQL_playingSource[i] == key) {
                this.ZQL_playingSource[i] = null;
                this.destoryVideoByIndex(i);
                this.clearAlgList(i)
                this.liveStopLoading(i);
              }
            }
          }
        }
      }
    },
    revertAlgs(index) {
      const arr = [];
      Object.keys(this.ZQL_sources[this.ZQL_playingSource[index]].alg).map(key => {
        arr.push({ customKey: key, ...this.ZQL_sources[this.ZQL_playingSource[index]].alg[key] })
      })
      return arr
    },
    handleClickAlg(alg, index) {
      this.selectAlg = alg;
      this.ZQL_videosInfos[index].alg = alg
      // Object.keys(this.ZQL_sources[this.ZQL_playingSource[index]].alg).map(key => {
      //   if (this.ZQL_sources[this.ZQL_playingSource[index]].alg[key].alg_type === alg) {
          // this.ZQL_videosInfos[index].alg = key
      //   }
      // })
      // console.log(222, alg, this.ZQL_sources[this.ZQL_playingSource[index]].alg);
      // const algItem = Object.values(this.ZQL_sources[this.ZQL_playingSource[index]].alg).filter(item => item.alg_type === alg)[0]
      // this.ZQL_videosInfos[index].alg = algItem.alg_type;
    },
    closeVideo(index) {
      this.clearAlgList(index);
      this.liveStopLoading(index);
      this.destoryVideoByIndex(index);
      this.ZQL_playingSource[index] = null;
      this.ZQL_videosInfos[index] = null;
    },
    dbClick() {
      this.clickCount++
      if (this.clickCount === 2) {
        this.btnFull()
        this.clickCount = 0
      }
      setTimeout(() => {
        if (this.clickCount === 1) {
          this.clickCount = 0
        }
      }, 250)
    },
    btnFull() {
      // const elVideo = this.$refs.video
      const elVideo = document.getElementById(`${this.prefixId}video-container`)
      if (elVideo.webkitRequestFullScreen) {
        elVideo.webkitRequestFullScreen()
      } else if (elVideo.mozRequestFullScreen) {
        elVideo.mozRequestFullScreen()
      } else if (elVideo.requestFullscreen) {
        elVideo.requestFullscreen()
      }
    },
    // 检查元素是否全屏
    isElementFullscreen(element) {
      return (
        document.fullscreenElement === element ||
        document.webkitFullscreenElement === element ||
        document.mozFullScreenElement === element ||
        document.msFullscreenElement === element
      );
    },
    handleClickVideo(index) {
      // console.log('handleClickVideo', `${this.prefixId}video-container`);
      const elVideo = document.getElementById(`${this.prefixId}video-container`)
      if (this.isElementFullscreen(elVideo)) return
      if (elVideo.requestFullScreen) {
        elVideo.requestFullScreen()
      } else if (elVideo.webkitRequestFullScreen) {
        elVideo.webkitRequestFullScreen()
      } else if (elVideo.mozRequestFullScreen) {
        elVideo.mozRequestFullScreen()
      } else if (elVideo.requestFullscreen) {
        elVideo.requestFullscreen()
      }
      this.handleRefresh(index)
    },
    toggleFullscreen(index) {
      // console.log('toggleFullscreen');
      
      this.isApiFullScreen = false
      // const videoContainer = document.getElementById(this.idName)
      // if (!document.fullscreenElement) {
      //   if (videoContainer.requestFullscreen) {
      //       videoContainer.requestFullscreen();
      //   } else if (videoContainer.mozRequestFullScreen) {
      //       videoContainer.mozRequestFullScreen();
      //   } else if (videoContainer.webkitRequestFullscreen) {
      //       videoContainer.webkitRequestFullscreen();
      //   } else if (videoContainer.msRequestFullscreen) {
      //       videoContainer.msRequestFullscreen();
      //   }
      // } else {
      if (document.exitFullscreen) {
        document.exitFullscreen();
      } else if (document.mozCancelFullScreen) {
        document.mozCancelFullScreen();
      } else if (document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
      } else if (document.msExitFullscreen) {
        document.msExitFullscreen();
      }
      // }
      // this.handleRefresh(index)
    },
  },
  async mounted() {
    await this.getBoxHost();
    this.getSources();
    this.sysArgs();
    const elVideo = document.getElementById(`${this.prefixId}video-container`)
    // console.log(123, this.dogId, elVideo, this.prefixId);
    this.resizeObserver = new ResizeObserver(entries => {
      for (let entry of entries) {
        // console.log('尺寸变化', entry.contentRect);
        //   setTimeout(() => {
        this.isFullScreen = this.isElementFullscreen(elVideo)
        // }, 500);
      }
    })
    this.resizeObserver.observe(elVideo)
  },
  watch: {
    dogId: {
      handler(newVal) {
        if (newVal) {
          this.getSources();
          this.sysArgs();
          const elVideo = document.getElementById(`${this.prefixId}video-container`)
          // console.log(123, this.dogId, elVideo, this.prefixId);
          this.resizeObserver = new ResizeObserver(entries => {
            for (let entry of entries) {
              // console.log('尺寸变化', entry.contentRect);
              //   setTimeout(() => {
              this.isFullScreen = this.isElementFullscreen(elVideo)
              // }, 500);
            }
          })
          this.resizeObserver.observe(elVideo)
        }
      }
    }
  }
}
</script>

<style lang="scss" scoped>
@import "./box.scss";
</style>