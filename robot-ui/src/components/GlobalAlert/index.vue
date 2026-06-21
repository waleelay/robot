<template>
  <div v-if="visible" :class="['global-alert', `alert-${type}`]">
    <div class="alert-content">
      <div class="message">{{ message }}</div>
      <button class="confirm-btn" @click="confirm">确认</button>
    </div>
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex';

export default {
  computed: {
    ...mapState('alert', ['visible', 'message', 'type'])
  },
  methods: {
    ...mapActions('alert', ['hideAlert']),
    confirm() {
      this.hideAlert(); // 用户点击确认后关闭
    }
  }
};
</script>

<style scoped>
.global-alert {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  min-width: 300px;
  padding: 16px 24px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 9999;
  display: flex;
  align-items: center;
  border-left: 6px solid; /* 左侧色条 */
}

/* 根据类型变化的样式 */
.alert-error {
  background: #fff5f5;
  border-color: #ff4444;
  color: #cc0000;
}

.alert-warning {
  background: #fff9e6;
  border-color: #ffaa00;
  color: #996600;
}

.alert-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.message {
  flex: 1;
  padding-right: 20px;
}

.confirm-btn {
  background: #1890ff;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.3s;
}

.confirm-btn:hover {
  background: #40a9ff;
}
</style>
