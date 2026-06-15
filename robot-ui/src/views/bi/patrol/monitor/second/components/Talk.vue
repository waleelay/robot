<!--
 * @Author: dengxumei
 * @Date: 2026-04-08 09:36:42
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-09 14:45:52
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\monitor\second\components\Talk.vue
 * @Version: 
-->
<template>
  <div class="box p20 mt10 flx-center flex-column">
    <div class="circle wp126 hp126 flx-center flex-column" :class="{ 'talking': selectCamera.intercomActive }" @click="handleTalk">
      <span>
        <!-- <svg-icon :icon-class="isTalk ? 'mic-fill' : 'mic-off-fill'" /> -->
        <svg-icon :icon-class="selectCamera.intercomActive ? 'mic-fill' : 'mic-off-fill'" />
      </span>
      <span class="mt8">{{ selectCamera.intercomActive ? '挂断' : '点击通话' }}</span>
    </div>
    <div class="wp269 hp16 mt22 progress flx-align-center">
      <!-- 底层未划过轨道 背景色 #093974 -->
      <div class="track-bg"></div>
      <!-- 动态划过层：展示颜色 #0C132A 以及 box-shadow: inset 0 0 6px 2px #09F -->
      <div class="filled-glow" :style="{'--value-percent': Math.ceil(currentVolume / 255 * 100)}"></div>
      <!-- <span class="value">{{ currentVolume }}</span> -->
      <input 
        type="range" 
        min="0" 
        max="255" 
        v-model="currentVolume"
        class="custom-slider" 
        @input="updateVolume"
        id="volumeSlider"
        :style="{'--value-percent': Math.ceil(currentVolume / 255 * 100)}"
      />
    </div>
    <div class="btns mt20">
      <el-button type="primary" class="wp124 hp30" @click="handleControlVoice('MUTE')">{{ isMuted ? '取消' : '静音' }}</el-button>
      <el-button type="primary" class="wp124 hp30 ml20" style="cursor: default;">
        <span @click="handleControlVoice('VOLUME_DOWN')">
          <svg-icon icon-class="minus" />
        </span>
        <span class="ml10 mr10">音量</span>
        <span @click="handleControlVoice('VOLUME_UP')">
          <svg-icon icon-class="plus" />
        </span>
      </el-button>
    </div>
  </div>
</template>

<script>
import voiceUtil from '../../../../js/mixins/voiceUtil';
import { mapActions } from 'vuex';

export default {
  name: 'Talk',
  mixins: [voiceUtil],
  computed: {
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot']
    },
    selectCamera() {
      return this.selectedRobot?.cameras?.[0] || {}
    }
  },
  data() {
    return { }
  },
  mounted() {
    setTimeout(() => {
      console.log('this.getSelectedRobot', this.getSelectedRobot)
    }, 2000);
  },
  methods: {
    ...mapActions('websocketRobot', ['toggleIntercom']),
    async handleTalk() {
      await this.toggleIntercom({ robotId: this.selectedRobotId, camera: this.selectCamera })
    },

  }
}
</script>
<style scoped lang="scss">
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
  .circle {
    color: #fff;
    background: #021328;
    box-shadow: 0 0 17px 0 #159Aff inset;
    border-radius: 50%;
    font-family: "Alibaba PuHuiTi";
    font-size: 10.2px;
    letter-spacing: 0.2px;
    .svg-icon {
      font-size: 59px;
      color: #159Aff;
    }
    &.talking {
      box-shadow: 0 0 60px 0 #42B3FF inset;
      &, .svg-icon {
        color: #0BF9FE
      }
    }
  }
}

.btns {
  ::v-deep .el-button {
    padding: 0;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    background: #021328;
    border-radius: 4px;
    border: none;
    box-shadow: 0 0 14px 2px #09F inset;
    text-align: center;
    .svg-icon {
      font-size: 16px;
      cursor: pointer;
    }
  }
}

// 音量
.progress {
  position: relative;
  /* 基础轨道背景容器（作为底层）显示未划过底色 #093974 */
  .track-bg {
    position: absolute;
    top: 50%;
    left: 0;
    transform: translateY(-50%);
    width: 100%;
    height: 8px;
    background: #093974;   /* 未划过背景色 */
    border-radius: 2px;
    box-shadow: inset 0 1px 3px rgba(0,0,0,0.4), 0 1px 0 rgba(255,255,255,0.05);
    pointer-events: none;
    z-index: 1;
  }
  .filled-glow {
    position: absolute;
    top: 50%;
    left: 0;
    transform: translateY(-50%);
    width: var(--value-percent);
    height: 8px;
    background: #0C132A;   /* 划过区域背景色 */
    border-radius: 2px 0 0 2px;  /* 左侧圆角，右侧平直按宽度变化，当宽度100%时变为全圆角，但为了美观，动态处理 */
    box-shadow: 0 0 6px 2px #09F inset;   /* 内阴影效果，仅在划过区域呈现 */
    pointer-events: none;
    z-index: 2;
    // transition: width 0.05s linear; /* 顺滑跟随滑块，但滑块是实时，同步很快 */
  }
  input[type=range].custom-slider {
    position: relative;
    z-index: 3;
    width: 100%;
    -webkit-appearance: none;
    appearance: none;
    background: transparent;  /* 完全透明，让下面两层显示 */
    outline: none;
    cursor: pointer;
    margin: 0;
    /* 滑块轨道完全透明，因为我们使用下层自定义轨道 */
    &::-webkit-slider-runnable-track {
      background: transparent;
      height: 8px;
      border-radius: 2px;
    }
    &::-moz-range-track {
      background: transparent;
      height: 8px;
      border-radius: 2px;
    }

    /* 滑块手柄样式 - 科技感圆形 */
    &::-webkit-slider-thumb {
      -webkit-appearance: none;
      appearance: none;
      width: 16px;
      height: 16px;
      background: #021328;
      border: none;
      border-radius: 50%;
      box-shadow: 0 0 10px 2px #09f inset;
      cursor: pointer;
      margin-top: -4px;  /* 因为轨道高10px，thumb高22px，垂直居中偏移 */
      transition: 0.1s ease;
      z-index: 10;
    }

    &::-webkit-slider-thumb:hover {
      transform: scale(1.2);
      background: #021328;
      box-shadow: 0 0 10px 2px #09f inset;
    }

    /* Firefox */
    &::-moz-range-thumb {
      width: 16px;
      height: 16px;
      background: #021328;
      border: none;
      border-radius: 50%;
      cursor: pointer;
      box-shadow: 0 0 10px 2px #09f inset;
      margin-top: -4px;  /* 因为轨道高10px，thumb高22px，垂直居中偏移 */
    }
    
    &::-moz-range-thumb:hover {
      transform: scale(1.15);
      background: #021328;
    }

    /* 兼容 Edge 以及确保滑块高度正常 */
    &:focus {
      outline: none;
    }
  }
}
</style>