<template>
  <div class="box p30 mt10 flx-center yuntai">
    <div class="flx-center flex-column">
      <div class="item wp120 flx-center">
        <span class="left-top" title="左上" @mousedown="startFrameControl(ptzObj['tl'].key)" @mouseup="stopFrameControl(ptzObj['tl'].key)" @mouseleave="stopFrameControl(ptzObj['tl'].key)" @touchstart.prevent="startFrameControl(ptzObj['tl'].key)" @touchend.prevent="stopFrameControl(ptzObj['tl'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
        <span class="top ml10" title="上" @mousedown="startFrameControl(ptzObj['t'].key)" @mouseup="stopFrameControl(ptzObj['t'].key)" @mouseleave="stopFrameControl(ptzObj['t'].key)" @touchstart.prevent="startFrameControl(ptzObj['t'].key)" @touchend.prevent="stopFrameControl(ptzObj['t'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
        <span class="ml10 right-top" title="右上" @mousedown="startFrameControl(ptzObj['tr'].key)" @mouseup="stopFrameControl(ptzObj['tr'].key)" @mouseleave="stopFrameControl(ptzObj['tr'].key)" @touchstart.prevent="startFrameControl(ptzObj['tr'].key)" @touchend.prevent="stopFrameControl(ptzObj['tr'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
      </div>
      <div class="item wp120 flx-center mt10">
        <span class="left" title="左" @mousedown="startFrameControl(ptzObj['l'].key)" @mouseup="stopFrameControl(ptzObj['l'].key)" @mouseleave="stopFrameControl(ptzObj['l'].key)" @touchstart.prevent="startFrameControl(ptzObj['l'].key)" @touchend.prevent="stopFrameControl(ptzObj['l'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
        <span class="ml10" :title="isPtzAutoRotateOn(ptzDevice) ? '停止旋转' : '开启自动慢旋转'"  @click="togglePtzAutoRotate">
          <svg-icon icon-class="move-slow" style="font-size: 14px;" />
        </span>
        <span class="ml10 right" title="右" @mousedown="startFrameControl(ptzObj['r'].key)" @mouseup="stopFrameControl(ptzObj['r'].key)" @mouseleave="stopFrameControl(ptzObj['r'].key)" @touchstart.prevent="startFrameControl(ptzObj['r'].key)" @touchend.prevent="stopFrameControl(ptzObj['r'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
      </div>
      <div class="item wp120 flx-center mt10">
        <span class="left-bottom" title="左下" @mousedown="startFrameControl(ptzObj['bl'].key)" @mouseup="stopFrameControl(ptzObj['bl'].key)" @mouseleave="stopFrameControl(ptzObj['bl'].key)" @touchstart.prevent="startFrameControl(ptzObj['bl'].key)" @touchend.prevent="stopFrameControl(ptzObj['bl'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
        <span class="bottom ml10" title="下" @mousedown="startFrameControl(ptzObj['b'].key)" @mouseup="stopFrameControl(ptzObj['b'].key)" @mouseleave="stopFrameControl(ptzObj['b'].key)" @touchstart.prevent="startFrameControl(ptzObj['b'].key)" @touchend.prevent="stopFrameControl(ptzObj['b'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
        <span class="ml10 right-bottom" title="右下" @mousedown="startFrameControl(ptzObj['br'].key)" @mouseup="stopFrameControl(ptzObj['br'].key)" @mouseleave="stopFrameControl(ptzObj['br'].key)" @touchstart.prevent="startFrameControl(ptzObj['br'].key)" @touchend.prevent="stopFrameControl(ptzObj['br'].key)">
          <svg-icon icon-class="control-arrow" />
        </span>
      </div>
    </div>
    <div class="flx-center ml48">
      <div class="item">
        <span title="焦距-"  @mousedown="startFrameControl(ptzObj['fm'].key)" @mouseup="stopFrameControl(ptzObj['fm'].key)" @mouseleave="stopFrameControl(ptzObj['fm'].key)" @touchstart.prevent="startFrameControl(ptzObj['fm'].key)" @touchend.prevent="stopFrameControl(ptzObj['fm'].key)">
          <svg-icon icon-class="focal-minus" />
        </span>
        <span class="ml10" title="焦距+"  @mousedown="startFrameControl(ptzObj['fp'].key)" @mouseup="stopFrameControl(ptzObj['fp'].key)" @mouseleave="stopFrameControl(ptzObj['fp'].key)" @touchstart.prevent="startFrameControl(ptzObj['fp'].key)" @touchend.prevent="stopFrameControl(ptzObj['fp'].key)">
          <svg-icon icon-class="focal-plus" />
        </span>
      </div>
    </div>
  </div>
</template>

<script>
import ptzMixin from './yuntai.js'
export default {
  name: 'Yuntai',
  mixins: [ptzMixin],
  data() {
    return {
      ptzObj: {
        'tl': { key: 'ptz-up-left' },
        't': { key: 'ptz-up' },
        'tr': { key: 'ptz-up-right' },
        'l': { key: 'ptz-left' },
        'center': { key: 'ptz-center' },
        'r': { key: 'ptz-right' },
        'bl': { key: 'ptz-down-left' },
        'b': { key: 'ptz-down' },
        'br': { key: 'ptz-down-right' },
        'fm': { key: 'zoom-out' },
        'fp': { key: 'zoom-in' },
      }
    }
  }
}
</script>
<style scoped lang="scss">
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
  .item {
    span {
      display: inline-block;
      width: 30px;
      color: #159AFF;
      font-size: 16px;
      line-height: 30px;
      text-align: center;
      background: #021328;
      box-shadow: 0 0 14px 2px #09F inset;
      border-radius: 4px;
      cursor: pointer;
      &.left-top .svg-icon {
        transform: rotate(-45deg);
      }
      &.right-top .svg-icon {
        transform: rotate(45deg);
      }
      &.left-bottom .svg-icon {
        transform: rotate(-135deg);
      }
      &.right-bottom .svg-icon {
        transform: rotate(135deg);
      }
      &.right .svg-icon {
        transform: rotate(90deg);
      }
      &.left .svg-icon {
        transform: rotate(-90deg);
      }
      &.bottom {
        transform: rotate(180deg);
      }
    }
  }
}
</style>