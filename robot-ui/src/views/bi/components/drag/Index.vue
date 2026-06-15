<!--
 * @Author: dengxumei
 * @Date: 2026-03-31 10:02:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-05-11 10:42:10
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\components\drag\Index.vue
 * @Version: 
-->
<template>
  <div class="d-flex mt20 ml100">
    <EquipmentList class="wp284" />
    <div
      class="ml20 w50 hp300"
      @dragover.prevent="onDragOver"
      @dragenter.prevent="onDragEnter"
      @dragleave.prevent="onDragLeave"
      @drop.prevent="onDrop"
      style="color: #fff; font-size: 14px; border: 1px solid #374E69; padding-left: 50px; border-radius: 4px;"
      :class="{ 'drop-active': dragOver }">
      <div class="mt50">类型：{{ dragInfo.type }}</div>
      <div class="mt20">名称：{{ dragInfo.name }}</div>
      <div class="mt20">状态：<span class="status p4 wp41" :class="{ error: dragInfo.status !== 0 }">{{ dragInfo.status === 0 ? '正常' : '异常' }}</span></div>
    </div>
  </div>
</template>

<script>
import EquipmentList from '../EquipmentListTree.vue';

export default {
  name: 'DragExample',
  components: { EquipmentList },
  data() {
    return {
      dragInfo: {},
      dragOver: false   // 控制拖拽悬停样式
    }
  },
  methods: {
    onDragOver(event) {
      // 阻止默认行为以允许放置
      this.dragOver = true;
    },
    onDragEnter(event) {
      this.dragOver = true;
    },
    onDragLeave(event) {
      // 只有当离开目标元素本身时才取消高亮
      if (!event.currentTarget.contains(event.relatedTarget)) {
        this.dragOver = false;
      }
    },
    onDrop(event) {
      // 获取拖拽数据
      const rawData = event.dataTransfer.getData('text/plain');
      if (!rawData) {
        // 没有获取到拖拽数据
        this.dragOver = false;
        return;
      }
      
      try {
        const data = JSON.parse(rawData);
        console.log('拖拽成功，任务数据:', rawData);
        // 触发自定义事件，获取数据
        this.dragInfo = data;
      } catch (e) {
        this.$message.error('数据解析失败');
      }
      
      // 重置拖拽状态
      this.dragOver = false;
    }
  }
}
</script>

<style lang="scss" scoped>
.status {
  color: #0BF9FE;
  text-align: center;
  font-family: "Alibaba PuHuiTi";
  font-size: 12px;
  font-weight: 400;
  line-height: 12px; /* 100% */
  letter-spacing: 0.857px;
  border-radius: 2px;
  background: rgba(40, 118, 210, 0.20);
  box-shadow: 0 0 4px 0 #69C4FF inset;
  &.error {
    color: #FF3434;
    background: rgba(210, 40, 40, 0.20);
    box-shadow: 0 0 4px 0 #FF6969 inset;
  }
}
</style>