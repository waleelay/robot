<template>
  <el-select v-model="internalValue" :popper-append-to-body="false" @change="handleChange">
    <el-option
      v-for="item in dataList"
      :key="item.value"
      :label="item.label"
      :value="item.value">
    </el-option>
  </el-select>
</template>

<script>
export default {
  props: {
    value: {
      type: Number, // 确保 value 类型为 Number
      default: () => 0,
    },
    dataList: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      internalValue: this.value,
    };
  },
  watch: {
    value(newValue) {
      this.internalValue = newValue;
    },
  },
  methods: {
    handleChange(selectedValue) {
      const selectedItem = this.dataList.find(item => item.value === selectedValue);
      const selectedLabel = selectedItem ? selectedItem.label : '';

      // console.log('用户选中的值:', selectedValue);
      this.$emit('input', selectedValue);
      // 发出 change 事件，父组件可以监听
      this.$emit('change', { value: selectedValue, label: selectedLabel });
    }
  }
};
</script>
<style lang='scss' scoped>
// 修改input默认值颜色 兼容其它主流浏览器
::v-deep input::-webkit-input-placeholder {
  color: rgba(255, 255, 255, 0.50);
}
::v-deep input::-moz-input-placeholder {
  color: rgba(255, 255, 255, 0.50);
}
::v-deep input::-ms-input-placeholder {
  color: rgba(255, 255, 255, 0.50);
}
// input框
::v-deep .el-select,
::v-deep .el-input,
::v-deep .el-input__inner {
  background-color: rgba(7,52,89,0.8);
  color: rgba(255, 255, 255, 0.8);
  text-align: left;
  border: 1px solid rgba(37,214,221,0.5);
}

// 鼠标悬浮时 input框颜色
::v-deep .el-input__inner:hover{
  background-color: rgba(255, 255, 255, 0.12);
}

// input框 右侧的箭头
::v-deep .el-select .el-input .el-select__caret {
  color: rgba(255, 255, 255, 0.50);
}

// option选项 上面的箭头
::v-deep .el-popper[x-placement^="bottom"] .popper__arrow::after {
  border-bottom-color: rgba(255, 255, 255,0.04);
  z-index: 9999;
}
::v-deep .popper__arrow {
  border: none;
}
// option选项 最外层
::v-deep .el-select-dropdown {
  min-width: 100% !important;
  box-sizing: border-box;
  border: none !important;
  background: rgba(7,52,89,0.8) !important;
  z-index: 9999;
}
// option选项 文字样式
::v-deep .el-select-dropdown__item {
  color: rgba(255, 255, 255, 0.5) !important;
  z-index: 9999;
}
::v-deep .el-select-dropdown__item.selected span{
  color: rgba(255, 255, 255, 0.50) !important;
  z-index: 9999;
}
// 移入option选项 样式调整
::v-deep .el-select-dropdown__item.hover{
  background-color: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.80) !important;
  z-index: 9999;
}

// 下拉框垂直滚动条宽度
::v-deep .el-scrollbar__bar.is-vertical {
  width: 10px;
  top: 2px;
}

// option选项 下面的箭头
::v-deep .el-popper[x-placement^="top"] .popper__arrow::after {
  border-top-color: rgba(43, 45, 55, 0.80);
  z-index: 9999;
}

.el-select {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
}
</style>
