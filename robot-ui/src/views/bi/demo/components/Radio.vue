<template>
  <div>
    <div class="custom-tab-button flex">
      <div v-for="item in tabList" :key="item.value" class="tab-button-item" :class="{ 'is-active': tabIndex === item.value }" @click="tabIndex = item.value">{{ item.label }}</div>
    </div>
    <div>
      <el-radio-group v-model="tabIndex" class="mt20 custom-radio-group flex with-border vertical p20">
        <el-radio v-for="item in tabList" :key="item.value" :label="item.value" class="flx-align-center">{{ item.label }}</el-radio>
      </el-radio-group>
    </div>
    <el-checkbox-group v-model="selectedList" class="custom-check-group flex vertical">
      <el-checkbox label="复选框 A" class="flx-align-center"></el-checkbox>
      <el-checkbox label="复选框 B" class="flx-align-center"></el-checkbox>
      <el-checkbox label="复选框 C" class="flx-align-center"></el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script>
  export default {
    name: 'Radio',
    // props: {
    //   tabList: {
    //     type: Array,
    //     default: () => []
    //   },
    //   tabIndex: {
    //     type: Number,
    //     default: 0
    //   }
    // },
    data() {
      return {
        tabList: [
          {
            label: '今日',
            value: 0
          },
          {
            label: '本月',
            value: 1
          },
          {
            label: '当年',
            value: 2
          }
        ],
        tabIndex: 0,
        selectedList: ['复选框 B']
      }
    }
  }
</script>
