<template>
  <div class="flx-center mt30">
    <div class="outer wp120 hp120">
      <div class="inner flx-center wp120 hp120 m0">
        <div class="circle flx-center">移动</div>
      </div>
      <div
        v-for="key in ['advance', 'back', 'turn-left' , 'turn-right']"
        :key="key"
        :class="['arrow', robotControlObj[key].class, deviceData.onDockState === '1' ? 'is-disabled': '']"
        :title="robotControlObj[key].label"
        @mousedown="handleControl(robotControlObj[key])"
      >
        <svg-icon icon-class="control-arrow" />
      </div>
    </div>
    <div class="control-btns mt28">
      <div class="btn-box flx-justify-between flex-column wp150">
        <div class="flx-justify-between w100">
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp70" @click="handleControl(robotControlObj['zuoyi'])">左平移</el-button>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp70 ml20" @click="handleControl(robotControlObj['youyi'])">右平移</el-button>
        </div>
        <div class="flx-justify-between mt10 w100">
          <el-button type="primary" plain class="wp70" @click="controlRobot('shutdown')">一键返航</el-button>
          <el-button type="primary" plain class="wp70" @click="controlRobot('startup')">退出充电桩</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getRecentTaskInfo } from '@/api/bigScreen.js'
import robotControl from '../js/mixins/robotControl';
import robotControlByKeyboard from '../js/mixins/robotControlByKeyboard';
import { controlRobot } from '../js/utils/robotUtil';

export default {
  name: "Control",
  props: {
    dogId: {
      type: [Number, String],
      // required: true,
    },
    dogName: {
      type: [String],
      // required: true,
    },
    endpoint: {
      type: String,
      // required: true,
    },
    motionId: {
      type: [Number],
      required: false
    }
  },
  mixins: [robotControlByKeyboard, robotControl],
  data() {
    return {
    };
  },
  watch: {
    dogId(newVal) {
      // console.log("当前机器人 ID 变化:", newVal);
      //routeName
      this.getRobotBasicInfo(newVal)
    },
    dogName(newVal) {
      this.deviceData.deviceName = newVal
    },
  },
  mounted() {},
  methods: {
    getRobotBasicInfo(dogId) {
      // console.log(dogId)
      getRecentTaskInfo(dogId).then(res => {
        // console.log(res)
        if (res.data.LineName) {
          this.deviceData.LineName = res.data.LineName
        } else {
          this.deviceData.LineName = '当前未执行任务'
        }
      }).catch(error => {
        this.deviceData.LineName = '当前未执行任务'
      })
    },
    // 开机
    open() { },
    // 关机
    close() { },
    // 退出控制
    exit() {
      this.$emit('handleClose')
    }
  },
};
</script>
<style lang="scss" scoped>
@keyframes fade-scale {
  0% {
    opacity: 0.8;
    transform: scale(0.95);
  }
  50% {
    opacity: 1;
    transform: scale(1.005);
  }
  100% {
    opacity: 0.8;
    transform: scale(0.95);
  }
}
.arrow {
  &.up, &.down {
    left: 51px !important;
  }
  &.right, &.left {
    top: 51px !important;
  }
}
</style>
