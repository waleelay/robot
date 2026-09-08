<template>
  <div class="alarm-mock-panel" :class="{ collapsed }">
    <div class="alarm-mock-panel__header" @click="collapsed = !collapsed">
      <span>告警弹窗模拟</span>
      <span class="alarm-mock-panel__toggle">{{ collapsed ? '展开' : '收起' }}</span>
    </div>
    <div v-show="!collapsed" class="alarm-mock-panel__body">
      <p class="alarm-mock-panel__hint">点选场景即时注入；会先清空上次模拟数据。</p>
      <div class="alarm-mock-panel__list">
        <button
          v-for="item in scenarios"
          :key="item.key"
          type="button"
          class="alarm-mock-panel__btn"
          :class="{ active: item.key === activeKey }"
          :disabled="running"
          @click.stop="onRun(item.key)"
        >
          <span class="name">{{ item.label }}</span>
          <span class="desc">{{ item.desc }}</span>
        </button>
      </div>
      <div class="alarm-mock-panel__footer">
        <button type="button" class="alarm-mock-panel__link" :disabled="running" @click.stop="$emit('clear')">
          清空模拟 / 关窗
        </button>
        <span v-if="activeKey" class="alarm-mock-panel__status">当前：{{ activeLabel }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import { ALARM_MOCK_SCENARIO_META } from './alarm-dialog-mock'

export default {
  name: 'AlarmMockPanel',
  props: {
    running: {
      type: Boolean,
      default: false
    },
    activeKey: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      collapsed: false,
      scenarios: ALARM_MOCK_SCENARIO_META
    }
  },
  computed: {
    activeLabel() {
      const hit = this.scenarios.find(item => item.key === this.activeKey)
      return hit ? hit.label : this.activeKey
    }
  },
  methods: {
    onRun(key) {
      this.$emit('run', key)
    }
  },
  mounted() {
    if (this.$el && this.$el.parentNode !== document.body) {
      document.body.appendChild(this.$el)
    }
  },
  beforeDestroy() {
    if (this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
  }
}
</script>

<style lang="scss" scoped>
.alarm-mock-panel {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 5000;
  width: 280px;
  color: #e8f4ff;
  font-size: 12px;
  border: 1px solid rgba(77, 179, 255, 0.45);
  background: rgba(6, 28, 48, 0.92);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(6px);
  border-radius: 4px;
  pointer-events: auto;

  &.collapsed {
    width: 160px;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    cursor: pointer;
    user-select: none;
    border-bottom: 1px solid rgba(77, 179, 255, 0.25);
    font-weight: 600;
  }

  &__toggle {
    color: #8ec8ff;
    font-weight: 400;
  }

  &__body {
    padding: 10px 12px 12px;
  }

  &__hint {
    margin: 0 0 8px;
    color: rgba(232, 244, 255, 0.72);
    line-height: 1.4;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 360px;
    overflow: auto;
  }

  &__btn {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    width: 100%;
    padding: 8px 10px;
    text-align: left;
    color: #e8f4ff;
    border: 1px solid rgba(77, 179, 255, 0.28);
    background: rgba(16, 52, 86, 0.75);
    border-radius: 2px;
    cursor: pointer;

    &:hover:not(:disabled) {
      border-color: #4db3ff;
      background: rgba(28, 157, 255, 0.22);
    }

    &.active {
      border-color: #ffb347;
      box-shadow: inset 0 0 0 1px rgba(255, 179, 71, 0.35);
    }

    &:disabled {
      opacity: 0.55;
      cursor: not-allowed;
    }

    .name {
      font-size: 13px;
      font-weight: 600;
    }

    .desc {
      color: rgba(232, 244, 255, 0.65);
      line-height: 1.3;
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-top: 10px;
  }

  &__link {
    padding: 0;
    border: none;
    background: transparent;
    color: #8ec8ff;
    cursor: pointer;

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  &__status {
    color: #ffb347;
    white-space: nowrap;
  }
}
</style>
