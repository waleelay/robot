<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-15 14:01:46
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\patrol\business\Index.vue
 * @Version: 
-->
<template>
  <div class="flex-column h100 pr20 pl20 pb10 pt20">
    <div class="card-title wp226">
      <div class="text">
        业务管理中心
      </div>
    </div>
    <div class="d-flex flex1 mt10">
      <div class="side-menu p10 wp226 h100">
        <div v-for="(item, index) in sideList" :key="index" class="item flx-align-center p10" :class="{ 'is-active': activeSideIndex === index, mt10: index !== 0 }" @click="activeSideIndex = index">
          <svg-icon :icon-class="item.svg"></svg-icon>
          <span class="ml10">{{ item.name }}</span>
        </div>
      </div>
      <VideoReply />
      <!-- <TaskTableList v-if="activeSideIndex === 0" />
      <ExecuteLogTableList v-else /> -->
    </div>
  </div>
</template>

<script>
import TaskTableList from './TaskTableList.vue'
import ExecuteLogTableList from './ExecuteLogTableList.vue';
import VideoReply from './reply/Video-Reply.vue'
export default {
  name: 'BiPatrolBusiness',
  components: { TaskTableList, ExecuteLogTableList, VideoReply },
  data() {
    return {
      sideList: [
        {
          name: '调度任务列表',
          svg: 'log1',
        },
        {
          name: '巡检执行记录',
          svg: 'detail',
        },
      ],
      activeSideIndex: 0
    }
  },
  methods: {}
}
</script>

<style scoped lang="scss">
@import './index.scss';
</style>
