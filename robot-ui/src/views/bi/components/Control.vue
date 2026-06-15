<template>
  <div class="flx-center">
    <div class="outer">
      <div class="inner flx-center">
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
    <div class="control-btns ml20">
      <div class="btn-box flx-justify-between flex-column wp310">
        <el-button type="primary" @click="controlDevice('startup')">退出充电桩</el-button>
        <div class="flx-justify-between mt10 w100">
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp150" @click="handleControl(robotControlObj['zuoyi'])">左移</el-button>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp150 ml20" @click="handleControl(robotControlObj['youyi'])">右移</el-button>
        </div>
        <div class="flx-justify-between mt10 w100">
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp100" @click="handleControl(robotControlObj['zhanli'])">站立</el-button>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp100" @click="handleControl(robotControlObj['paxia'])">趴下</el-button>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp100" @click="handleControl(robotControlObj['paxia'])">趴下</el-button>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp100" @click="handleControl(robotControlObj['tztb'])"> 停止踏步</el-button>
        </div>
        <div class="flx-justify-between mt10 w100">
          <!-- 步态：行走0 普通楼梯1 斜坡/防滑步态2 感知楼梯步态 4 -->
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp100" @click="handleControl(robotControlObj['jiting'])">急停</el-button>
          <el-select
            :disabled="deviceData.onDockState === '1'"
            v-model="butaiValue"
            placeholder="切换步态"
            @change="e => handleControl(robotControlObj['qhbt'], e)"
            class="wp100 ml10"
            title="切换步态"
            popper-class="custom-select control-select-popper p10"
          >
            <el-option v-for="item in butaiList" :key="item.label" :label="item.label" :value="item.value" />
          </el-select>
          <el-button :disabled="deviceData.onDockState === '1'" type="primary" class="wp100 ml10" @click="setSpeed">设定速度</el-button>
        </div>
        <div class="flx-justify-between mt10 w100">
          <el-button type="primary" plain class="wp167" @click="controlRobot('shutdown')">一键返航</el-button>
          <el-button type="primary" plain class="wp167" @click="exit()">退出控制</el-button>
        </div>
        <div class="flx-justify-between mt10 w100">
          <el-button type="primary" plain class="wp234" style="color: #17D1FF">
            <svg-icon icon-class="plus" class="mr10" />
            添加新任务
          </el-button>
          <el-button type="primary" plain class="wp100">任务管理</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getRecentTaskInfo } from '@/api/bigScreen.js'
import robotControlByKeyboard from '../js/mixins/robotControlByKeyboard';
import robotControl from '../js/mixins/robotControl';
import { controlRobot } from '../js/utils/robotUtil';
import { robotControlObj } from '../js/constants/robot-control';

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
  mixins: [robotControl, robotControlByKeyboard],
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
</style>
