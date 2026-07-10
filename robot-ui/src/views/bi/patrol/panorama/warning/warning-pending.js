import WarnPending from './WarnPending.vue'
export default {
  components: { WarnPending },
  data() {
    return {
      // 通知数据源数组
      notifications: [],
      // 存储所有通知实例，方便批量操作
      notifyInstances: [],
      // 计数器用于生成唯一ID
      counter: 0,
    };
  },
  watch: {
    notifications: {
      handler(newVal, oldVal) {
        if (newVal.length) {
          this.showNotification(newVal[0]);
        }
      },
      deep: true,
      immediate: true
    },
  },
  mounted() {
    // 初始化时添加一个通知作为示例
    setTimeout(() => {
      this.addNotification();
    }, 2000);
  },
  methods: {
    // 添加单个通知
    addNotification() {      
      this.counter++;
      const newNotify = {
        id: this.counter,
        title: `通知 #${this.counter}`,
        content: `这是第 ${this.counter} 条通知内容`,
        progress: Math.floor(Math.random() * 100),
        timestamp: new Date().toLocaleTimeString(),
      };
      this.notifications.push(newNotify);
    },

    // 批量添加通知
    addMultipleNotifications() {
      const count = 3;
      for (let i = 0; i < count; i++) {
        this.counter++;
        this.notifications.push({
          id: this.counter,
          title: `通知 #${this.counter}`,
          content: `批量添加的第 ${i + 1} 条通知`,
          progress: Math.floor(Math.random() * 100),
          timestamp: new Date().toLocaleTimeString(),
        });
      }
    },
    showNotification(data) {
      const h = this.$createElement;
      
      // 使用自定义组件
      const messageVNode = h('WarnPending', {
        props: {
          title: data.title,
          content: data.content,
          progress: data.progress,
          timestamp: data.timestamp,
        },
        // 可以监听组件事件
        on: {
          action: (payload) => {
            console.log('用户点击了操作按钮', payload);
            // 处理自定义操作
          },
        },
      });

      const instance = this.$notify({
        title: data.title,
        message: messageVNode,
        duration: 0,
        showClose: false,
        customClass: 'custom-notification',
        onClose: () => {
          this.removeNotification(data.id);
        },
      });

      this.notifyInstances.push({
        id: data.id,
        instance: instance,
      });
    },
    // 从数组中移除通知（由 onClose 回调触发）
    removeNotification(id) {
      const index = this.notifications.findIndex(item => item.id === id);
      if (index !== -1) {
        this.notifications.splice(index, 1);
      }
      // 清理实例
      const instIndex = this.notifyInstances.findIndex(item => item.id === id);
      if (instIndex !== -1) {
        this.notifyInstances.splice(instIndex, 1);
      }
    },

    // 全部关闭
    closeAllNotifications() {
      // 方法1：遍历存储的实例，逐个关闭
      this.notifyInstances.forEach(item => {
        if (item.instance && item.instance.close) {
          item.instance.close();
        }
      });

      // 清空实例数组
      this.notifyInstances = [];
      
      // 清空数据数组
      this.notifications = [];
      
      console.log('所有通知已关闭');
    },

    // 关闭特定通知（可选）
    closeNotificationById(id) {
      const item = this.notifyInstances.find(item => item.id === id);
      if (item && item.instance) {
        item.instance.close();
        // 移除实例和数据
        this.notifyInstances = this.notifyInstances.filter(item => item.id !== id);
        this.notifications = this.notifications.filter(item => item.id !== id);
      }
    },

    // 清除所有通知（另一种实现方式，不依赖存储的实例）
    closeAllNotificationsAlt() {
      // 注意：Element UI 的 $notify 没有全局关闭方法
      // 所以我们需要在创建时保存所有实例
      // 这里使用第一种方法
      this.closeAllNotifications();
    },
  },
  // 组件销毁时清理所有通知
  beforeDestroy() {
    this.closeAllNotifications();
  },
}