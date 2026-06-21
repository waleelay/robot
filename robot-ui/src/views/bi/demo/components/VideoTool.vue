<template>
  <div>
    <div class="flx-center p10 custom-video-tool" :class="className">
      <!-- <div class="p6 pixel">
        <div class="w100 list">
          <div v-for="item in pixel.pixelTypes" :key="item.value" class="item" @click="handleChangePixel(item)" :class="{'is-active': pixel.currentPixel === item.value}">{{ item.label }}</div>
        </div>
        <span>{{ pixel.currentPixelLabel }}</span>
      </div> -->
      <div :ref="`dropdownRef${slotKey}_pixel`">
        <el-dropdown class="custom-dropdown pixel-dropdown" trigger="hover" placement="top">
          <span class="el-dropdown-link">
            {{ pixel.currentPixelLabel }}
          </span>
          <el-dropdown-menu :ref="`dropdownMenuRef${slotKey}_pixel`" slot="dropdown" :append-to-body="false" class="custom-dropdown-menu pixel-dropdown-menu">
            <el-dropdown-item v-for="item in pixel.pixelTypes" :key="item.value" class="item" @click="pixel.currentPixel = item.value" :class="{'is-active': pixel.currentPixel === item.value}">{{ item.label }}</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
      <div :ref="`dropdownRef${slotKey}_volume`">
        <el-dropdown class="custom-dropdown volume-dropdown" trigger="hover" placement="top" @visible-change="handleVolumeVisibleChange">
          <!-- <span class="el-dropdown-link">
            {{ pixel.currentPixelLabel }}
          </span> -->
          <span @click="toggleMute" :title="volumeValue">
            <svg-icon :icon-class="volumeIconClass" />
          </span>
          <el-dropdown-menu :ref="`dropdownMenuRef${slotKey}_volume`" slot="dropdown" :append-to-body="false" class="custom-dropdown-menu volume-dropdown-menu">
            <el-dropdown-item>
              <div class="info flx-center">
                <span class="value">{{ volumeInfo.currentVolume }}</span>
                <input 
                  type="range" 
                  min="0" 
                  max="100" 
                  v-model="volumeInfo.currentVolume"
                  class="mt10 mb10 vertical-slider" 
                  @input="updateVolume"
                  id="volumeSlider"
                  :style="{'--value-percent': `${volumeInfo.currentVolume}%`}"
                />
              </div>
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </div>
      <!-- <div class="volume ml20">
        <div class="info flx-center">
          <span class="value">{{ volumeInfo.currentVolume }}</span>
          <input 
            type="range" 
            min="0" 
            max="100" 
            v-model="volumeInfo.currentVolume"
            class="mt10 mb10 vertical-slider" 
            @input="updateVolume"
            id="volumeSlider"
            :style="{'--value-percent': `${volumeInfo.currentVolume}%`}"
          />
        </div>
        <span @click="toggleMute" :title="volumeValue">
          <svg-icon :icon-class="volumeIconClass" />
        </span>
      </div> -->
      <div @click="getSnapshot()"><svg-icon title="快照" icon-class="camera" /></div>
      <div :title="openMic ? '关闭麦克风' : '打开麦克风'" @click="toggleMic()">
        <svg-icon :icon-class="openMic ? 'mic' : 'mic-off'" />
      </div>
      <div><svg-icon title="控制器" icon-class="control" /></div>
      <div><svg-icon title="刷新" icon-class="refresh" /></div>
      <div :title="isPlay ? '暂停' : '播放'" @click="playPauseVideo()">
        <svg-icon :icon-class="isPlay ? 'pause' : 'play'" />
      </div>
      <div :title="isFullscreen ? '退出全屏' : '全屏'" @click="toggleFullscreen()">
        <svg-icon :icon-class="isFullscreen ? 'close-fullscreen' : 'fullscreen1'" />
      </div>
      <div><svg-icon title="移除" icon-class="delete" /></div>
    </div>
  </div>
</template>

<script>
  import videoUtils from '@/utils/videoUtils.js'
  export default {
    name: 'VideoTool',
    props: {
      slotKey: {
        type: String,
        default: ''
      },
      idName: {
        type: String,
        default: ''
      },
      className: {
        type: Object,
        default: () => ({ one: true }),
      }
    },
    mixins: [videoUtils],
  }
</script>

<style lang="scss" scoped>

</style>