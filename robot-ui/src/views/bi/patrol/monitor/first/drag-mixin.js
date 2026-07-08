import { mapActions } from "vuex";

export default {
  data() {
    return {
      dragInfo: {},
      dragOver: false
    }
  },
  methods: {
    ...mapActions('dragVideo', ['dropComplete', 'resetDrag']),
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
    async onDrop(event, index) {
      // 获取拖拽数据
      const rawData = event.dataTransfer.getData('text/plain');
      if (!rawData) {
        // 没有获取到拖拽数据
        this.dragOver = false;
        return;
      }
      
      try {
        const data = JSON.parse(rawData);
        // console.log('拖拽成功，任务数据:', data);
        // 触发自定义事件，获取数据
        this.dragInfo = data;
        this.$emit('test', { ...data, index: `slot_${this.index}` })
        await this.dropComplete({...data, splitType: this.splitType, selectedRows: []})
      } catch (e) {
        console.error('数据解析失败', e);
        this.$message.error('数据解析失败');
      }
      
      // 重置拖拽状态
      this.dragOver = false;
    }
  }
}