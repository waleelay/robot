<template>
  <div class="w100 h100 pt94 pl100">
    <Header activeHead="demo" />
    <Side />

    <div>
      <h4 style="color: #fff"> 单选条件</h4>
      <Radio />
      <h4 style="color: #fff"> 机器狗图标</h4>
      <Robot />
      <h4 style="color: #fff" class="mt20"> 模态框</h4>
      <!-- <Modal /> -->
      
      <MapTool />

      <div class="video-operation">
        <h4 style="color: #fff"> 视频操作图标</h4>
        <VideoTool />
      </div>
      <div class="robot-control">
        <Control />
      </div>
      <div style="position: absolute; top: 94px; right: 98px; display: block;">
        <TaskListTree />
        <TaskList1 />
      </div>
      <div style="position: absolute; top: 94px; right: 468px; display: block;" class="wp284">
        <EquipmentList />
      </div>
      <!-- 表格式的列表 -->
      <div style="position: absolute; top: 110px; left: 810px;">
        <List />
      </div>
      <!-- 告警列表 -->
      <div style="position: absolute; top: 450px; left: 810px;">
        <WarningListCard />
      </div>
      <div>
        <!-- <Snapshot /> -->
        <WarningBatch />
      </div>
    </div>
  </div>
</template>

<script>
// 大屏地图上机器狗信息
import Control from './components/Control.vue';
import Header from './components/Header.vue';
import List from './components/List.vue';
import MapTool from './components/MapTool.vue';
import Modal from './components/Modal.vue';
import Radio from './components/Radio.vue';
import Robot from './components/Robot.vue';
import Side from './components/Side.vue'
import Snapshot from './components/Snapshot.vue';
import TaskListTree from './components/TaskListTree.vue';
import TaskList1 from './components/TaskList1.vue'
import EquipmentList from './components/EquipmentList.vue'
import WarningListCard from './components/WarningListCard.vue';
import VideoTool from './components/VideoTool.vue';
import WarningBatch from './components/modal/warning/WarningBatch.vue'
  export default {
    name: 'DemoPage',
    components: { Header, Side, WarningListCard, VideoTool, Robot, Radio, MapTool, Control, Modal, TaskListTree, List, TaskList1, EquipmentList, Snapshot, WarningBatch, },
    data() {
      return {
      }
    },
    computed: {
    },
    methods: {
    },
    mounted() {
    },
    beforeDestroy() {
    }
  }
</script>