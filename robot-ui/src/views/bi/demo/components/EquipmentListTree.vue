<template>
  <div class="w100">
    <div class="card-title">
      <div class="text">
        装备列表
      </div>
    </div>
    <div class="mt10 hp606 common-scroll" style="overflow-y: auto;">
      <div class="collapse-box pl26 h100">
        <div
          v-for="(type, index) in equipmentList"
          class="custom-collapse-div"
          :class="{ collapse: collapse[index] }"
        >
          <div class="collapse-header p10 flx-justify-between" @click="toggleCollapse(index)">
            <div class="flx-center">
              <svg-icon :icon-class="type.svg" />
              <span class="ml10">{{ type.name }}</span>
            </div>
            <svg-icon class="ml10" :icon-class="collapse[index] ? 'down' : 'up'" style="color: #6A788B; font-family: Bahnschrift" />
          </div>
          <div class="collapse-content pl12 common-scroll mr10 pr7">
            <div
              v-for="(item, index) in type.list"
              :key="index"
              class="item flx-justify-between"
              :class="{ 'is-active': selectedEquipmentList1.includes(item.name) }"
              :draggable="!selectedEquipmentList1.includes(item.name)"
              @dragstart="onDragStart($event, item)"
              @dragend="onDragEnd"
              :style="{ cursor: !selectedEquipmentList1.includes(item.name) ? 'grab' : 'default' }"
            >
              <!-- @click="handleSelectEquipment(item)" -->
              <div class="flx-center">
                <span class="dot" :class="{ error: item.status !== 0 }"></span>
                <span class="ml10">{{ item.name }}</span>
              </div>
              <span class="status p4 wp41" :class="{ error: item.status !== 0 }">{{ item.status === 0 ? '正常' : '异常' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div> 
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
export default {
  name: 'EquipmentList',
  computed: {
    ...mapState('dragVideo', ['dropResult', 'splitType'])
  },
  data() {
    return {
      equipmentList: [
        {
          name: '机器狗本体',
          svg: 'robot-dog',
          list: [
            {
              name: '前置摄像头',
              status: 0,
            },
            {
              name: '后置摄像头',
              status: 1,
            },
            {
              name: '左置摄像头',
              status: 0,
            },
            {
              name: '右置摄像头',
              status: 0,
            }
          ]
        },
        {
          name: '红外装备',
          svg: 'infrared',
          list: [
            {
              name: '可见光',
              status: 0,
            },
            {
              name: '红外热成像',
              status: 0,
            },
          ]
        },
        {
          name: '激光雷达',
          svg: 'lidar',
          list: [
            {
              name: '点云视图',
              status: 0,
            },
          ]
        },
      ],
      collapse: [],
      selectedEquipmentList1: [],
    }
  },
  methods: {
    ...mapActions('dragVideo', ['dragStart']),
    toggleCollapse(index) {
      this.$set(this.collapse, index, !this.collapse[index])      
    },
    // 拖拽开始: 将任务数据存入 dataTransfer
    onDragStart(event, info) {
      // 设置拖拽数据 - 使用纯文本格式
      event.dataTransfer.setData('text/plain', JSON.stringify(info));
      // 设置拖拽效果
      event.dataTransfer.effectAllowed = 'move';
      // 添加拖拽中的样式
      event.target.classList.add('dragging');
    },
    onDragEnd(event) {
      // 移除拖拽样式
      event.target.classList.remove('dragging');
    },
    // 拖拽开始: 将任务数据存入 dataTransfer
    onDragStart(event, info) {
      // 设置拖拽数据 - 使用纯文本格式
      event.dataTransfer.setData('text/plain', JSON.stringify({ data: info, componentId: 'equipmentListComponent' }));
      // 设置拖拽效果
      event.dataTransfer.effectAllowed = 'move';
      // 添加拖拽中的样式
      event.target.classList.add('dragging');
      this.dragStart({ data: info, componentId: 'equipmentListComponent' })
    },
    onDragEnd(event) {
      // 移除拖拽样式
      event.target.classList.remove('dragging');
    },
    handleSelectEquipment(equipment, selectedRows) {
      this.selectedEquipmentList1 = selectedRows.filter(item => item !== null)
      // if (this.selectedEquipmentList.includes(equipment.name)) {
      //   this.selectedEquipmentList = this.selectedEquipmentList.filter(item => item !== equipment.name)
      // } else {
      //   this.selectedEquipmentList.push(equipment.name)
      // }
    }
  },
  watch: {
    dropResult: {
      handler(newResult) {
        console.log('测试2', newResult);
        if (newResult) {
          if (newResult.componentId === 'equipmentListComponent') {
            this.handleSelectEquipment(newResult.data, newResult.selectedRows)
          }
        } else {
          this.selectedEquipmentList1 = []
        }
      },
      deep: true
    },
    splitType: {
      handler() {
        this.selectedEquipmentList1 = []
      },
      deep: true
    }
  }
}
</script>
<style lang="scss" scoped>
/* 拖拽时隐藏鼠标默认样式 */
.task-item.dragging {
  opacity: 0.5;
}

.collapse-box {
  .custom-collapse-div {
    position: relative;
    .collapse-header {
      color: #8897AB;
      font-size: 14px;
      font-weight: 600;
      line-height: 18px;
      .svg-icon {
        font-size: 16px;
      }
    }
    .collapse-content {
      max-height: 200px;
      overflow-y: auto;
      &::-webkit-scrollbar {
        width: 2px;               /* 垂直滚动条宽度 */
        height: 2px;              /* 水平滚动条高度 */
      }
      &::-webkit-scrollbar-thumb {
        border: 1px solid #42536F; /* 创建内边距效果,会覆盖背景值 */
      }
      .item {
        position: relative;
        padding: 5px 10px;
        color: #92A0B6;
        font-size: 14px;
        line-height: 19px;
        background: #08101B;
        border: 1px solid #08101B;
        &.is-active {
          border-color: #0BF9FE;
        }
        & + .item {
          margin-top: 10px;
        }
        .svg-icon {
          font-size: 14px;
        }
        &::before {
          position: absolute;
          top: 15.5px;
          left: -10px;
          width: 8px;
          height: calc(100% + 10px);
          content: '';
          transition: all 0.3s ease;
        }
        // 只有一条数据时
        &:only-of-type::before {
          border: none !important;
        }
        &:first-child::before {
          border-top: 1px solid rgba(136, 151, 171, 0.3);
        }
        &:last-child::before {
          display: none;
        }
        &:not(:last-child)::before {
          border-bottom: 1px solid rgba(136, 151, 171, 0.3);
          border-left: 1px solid rgba(136, 151, 171, 0.3);
        }
      }
    }
    &.collapse {
      .collapse-content {
        display: none;
      }
    }
    &::before {
      position: absolute;
      top: 19px;
      left: -16px;
      width: 16px;
      height: 100%;
      content: '';
      transition: all 0.3s ease;
    }
    // 只有一条数据时
    &:only-of-type::before {
      border: none !important;
    }
    &:first-child::before {
      border-top: 1px solid rgba(136, 151, 171, 0.5);
    }
    &:last-child::before {
      display: none;
    }
    &:not(:last-child)::before {
      border-bottom: 1px solid rgba(136, 151, 171, 0.5);
      border-left: 1px solid rgba(136, 151, 171, 0.5);
    }
  }
}

.dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #0BF9FE;
  &.error {
    background: #FF3434;
  }
}
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
