<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-08 15:32:24
 * @Description: TaskListTree
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\monitor\first\Index.vue
 * @Version: 
-->
<template>
  <div class="flex h100 pr20 pl20 pb10 pt20">
    <div class="left h100 no-w-scroll" style="overflow-y: auto;">
      <!-- 1364px -->
      <LeftVideo :prefixId="prefixId" ref="leftVideoRef" style="width: 1432px;" />
      <!-- <el-radio-group @change="onSingleDeviceChange" v-if="splitType === 1" v-model="singleId" class="custom-radio-group flex with-border vertical ml20">
        <el-radio v-for="dev in deviceList" :key="dev.id" :label="dev.id" class="flx-align-center">{{ dev.desc }}</el-radio>
      </el-radio-group>
      <el-checkbox-group @change="onMultiDeviceChange" v-else v-model="checkedIds" class="custom-check-group flex vertical ml20">
        <el-checkbox v-for="dev in deviceList" :key="dev.id" :label="dev.id" class="flx-align-center">{{ dev.desc }}</el-checkbox>
      </el-checkbox-group> -->
      <div class="mt9">
        <div class="card-title title-1364-30 mb9 w100 hp30 pl30" style="line-height: 30px;">
          <div class="text">
            抓拍记录
          </div>
        </div>
        <Snapshot />
      </div>
    </div>
    <div class="right flex1 flex-column ml30 h100">
      <TaskListTree ref="taskListRef" @select-task="selectTask" @updateVideo="updateVideo" />
      <div class="mt20 flex1 flex-column">
        <div class="card-title">
          <div class="text">
            实时地图
          </div>
        </div>
        <div class="flex1 mt10 h100" style="min-height: 403px; background: #1c121c;">
          <SmallMap />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import LeftVideo from './LeftVideo.vue'
import Snapshot from '../../../components/Snapshot.vue'
import TaskListTree from './TaskListTree.vue';
import { mapActions } from 'vuex'
import SmallMap from '../../../gis/globalMap/SmallMap..vue';
export default {
  name: 'BiPatrolMonitor',
  components: { TaskListTree, LeftVideo, Snapshot, SmallMap },
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    }
  },
  data() {
    return {}
  },
  computed: {
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    }
  },
  async mounted() {
    this.setSelectedRobotId('')
    // this.setSplitType(1)
    // for (const [index, key] of Object.keys(this.activeCameras).entries()) {
    //   // console.log('++++++++++++++++++++++++++++++');
    //   const res = await this.stopCamera(this.activeCameras[key].camera);
    //   // console.log('++++++++++++++++++++++++');
    // }
  },
  methods: {
    ...mapActions('dragVideo', ['setSplitType']),
    ...mapActions('websocketRobot', ['stopCamera', 'setSelectedRobotId']),
    selectTask(selectRows) {
      this.$refs.leftVideoRef.slotDevices = this.$refs.leftVideoRef.slotDevices.map((item, index) => selectRows[index] || null)
      
    },
    updateVideo(data) {
      this.$refs.leftVideoRef.test({ data })
    },
    // async updateVideo(data) {
    //   if (data.key) {
    //     await this.$refs.leftVideoRef.test({ data })
    //   } else {
    //     console.log('更新视频', data);
    //     // for (const item of data) {
    //       await this.$refs.leftVideoRef.test({ data })
    //     // }
    //   }
    // },
  }
}
</script>