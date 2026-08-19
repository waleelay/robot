<template>
  <div
    v-if="visible"
    class="equipment-screen-select bi-corner-box posa wp274 hp321 p20"
    @click.stop
  >
    <div class="select-header flx-justify-between">
      <div class="select-title">{{ title }}</div>
      <div class="select-close curp flx-center" @click="$emit('close')">
        <svg-icon icon-class="close" />
      </div>
    </div>
    <div class="select-search flx-align-center mt10 pl10 pr10 pt6 pb6">
      <svg-icon icon-class="search" class="search-icon" />
      <input
        v-model="keyword"
        class="search-input flex1"
        type="text"
        :placeholder="placeholder"
      >
    </div>
    <div class="select-list common-scroll mt10 ovhx hp216">
      <template v-if="filteredOptions.length">
        <div
          v-for="(item, index) in filteredOptions"
          :key="item.id"
          class="select-item flx-justify-between pl10 pr10 curp hp36"
          :class="{
            selected: item.id === selectedId,
            disabled: item.disabled,
            occupied: item.occupied,
            mt10: index !== 0
          }"
          :title="item.title || item.label"
          @click="handleSelect(item)"
        >
          <span class="item-name text-ellipsis flex1">{{ item.label }}</span>
          <svg-icon
            v-if="item.id === selectedId || item.occupied"
            icon-class="success"
            class="check-icon"
          />
        </div>
      </template>
      <Empty
        v-else
        width="126px"
        :opacity="0.7"
        textColor="#BEE1FF"
        :text="'暂无数据'"
      />
    </div>
  </div>
</template>

<script>
import Empty from '../components/Empty.vue'
export default {
  name: 'EquipmentScreenSelect',
  components: { Empty },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: '装备画面选择'
    },
    placeholder: {
      type: String,
      default: '请输入装备名称'
    },
    options: {
      type: Array,
      default: () => []
    },
    selectedId: {
      type: [String, Number],
      default: ''
    }
  },
  data() {
    return {
      keyword: ''
    }
  },
  computed: {
    filteredOptions() {
      const kw = (this.keyword || '').trim().toLowerCase()
      if (!kw) return this.options
      return this.options.filter(item => String(item.label || '').toLowerCase().includes(kw))
    }
  },
  watch: {
    visible(val) {
      if (val) this.keyword = ''
    }
  },
  methods: {
    handleSelect(item) {
      if (item.disabled) return
      this.$emit('select', item)
    }
  }
}
</script>

<style lang="scss" scoped>
.equipment-screen-select {
  position: absolute;
  left: calc(100% + 10px);
  bottom: 0;
  z-index: 20;
  background: rgba(0, 19, 48, 0.90);
  pointer-events: auto;
  .select-title {
    color: #FFF;
    font-family: "Microsoft YaHei";
    font-size: 14px;
    font-weight: 600;
    line-height: 19px;
  }
  .select-close {
    color: rgba(255, 255, 255, 0.5);
    .svg-icon {
      font-size: 12px;
    }
  }
  .select-search {
    gap: 10px;
    border-radius: 4px;
    border: 1px solid #D0DEEE;
    .search-icon {
      width: 16px;
      height: 16px;
      color: #D0DEEE;
      font-size: 16px;
      flex-shrink: 0;
    }
    .search-input {
      min-width: 0;
      border: none;
      outline: none;
      background: transparent;
      color: #D0DEEE;
      font-family: "Microsoft YaHei";
      font-size: 14px;
      line-height: 18px;
      &::placeholder {
        color: #D0DEEE;
      }
    }
  }
  .select-list {
    position: relative;
  }
  .select-item {
    box-sizing: border-box;
    border: 1px solid transparent;
    .item-name {
      color: #D0DEEE;
      font-family: "Alibaba PuHuiTi", "Microsoft YaHei";
      font-size: 14px;
      letter-spacing: 0.857px;
      line-height: 19px;
    }
    .check-icon {
      width: 16px;
      height: 16px;
      color: #0BF9FE;
      font-size: 16px;
      flex-shrink: 0;
    }
    &.selected {
      border-color: #0BF9FE;
    }
    &.occupied {
      border-color: rgba(11, 249, 254, 0.35);
      opacity: 0.55;
      cursor: not-allowed;
    }
    &.disabled:not(.occupied) {
      opacity: 0.4;
      cursor: not-allowed;
    }
    &:hover:not(.disabled):not(.occupied) {
      background: rgba(11, 249, 254, 0.06);
    }
  }
}
</style>