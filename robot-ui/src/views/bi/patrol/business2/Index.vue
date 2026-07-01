<template>
  <div class="flex-column h100 pr20 pl20 pb10 pt20 business2-page">
    <div class="card-title wp226">
      <div class="text">业务管理2</div>
    </div>
    <div class="d-flex flex1 mt10 business2-body">
      <div class="side-menu p10 wp226 h100">
        <div
          v-for="(item, index) in sideList"
          :key="item.name"
          class="item flx-align-center p10"
          :class="{ 'is-active': activeSideIndex === index, mt10: index !== 0 }"
          @click="activeSideIndex = index"
        >
          <svg-icon :icon-class="item.svg" />
          <span class="ml10">{{ item.name }}</span>
        </div>
      </div>
      <PlanIndex v-if="activeSideIndex === 0" />
      <RecordIndex v-else />
    </div>
  </div>
</template>

<script>
import PlanIndex from './plan/Index.vue'
import RecordIndex from './record/Index.vue'

export default {
  name: 'BiPatrolBusiness2',
  components: { PlanIndex, RecordIndex },
  data() {
    return {
      activeSideIndex: 0,
      sideList: [
        { name: '任务计划', svg: 'log1' },
        { name: '执行记录', svg: 'detail' }
      ]
    }
  }
}
</script>

<style scoped lang="scss">
@import './index.scss';
</style>
