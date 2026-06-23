<template>
  <div class="w100">
    <div class="card-title title-284-37">
      <div class="text">
        装备列表
      </div>
    </div>
    <div class="mt10 hp606 common-scroll" style="overflow-y: auto;">
      <div class="collapse-box pl26 h100">
        <div
          v-for="(type, key, index) of equipmentCameraObj"
          class="custom-collapse-div"
          :class="{ collapse: collapse[index] }"
          :key="key"
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
              v-for="(item, key, index) of type.cameras"
              :key="key"
              class="item flx-justify-between"
              :class="{ 'is-active': checkedIds.includes(item.key) }"
              :draggable="!checkedIds.includes(item.key)"
              @dragstart="onDragStart($event, item, 'equipmentListComponent')"
              @dragend="onDragEnd"
              @click="handleClickCamera(item)"
              :style="{ cursor: !checkedIds.includes(item.key) ? 'grab' : 'default' }"
            >
              <!-- @click="handleSelectEquipment(item)" -->
              <div class="flx-center">
                <span class="dot" :class="{ error: item.status !== 0 }"></span>
                <span class="ml10">{{ item.name }}</span>
              </div>
              <span class="status p4 wp41" :class="{ error: item.status !== 'online' }">{{ item.status }}</span>
            </div>
          </div>
        </div>
      </div>
    </div> 
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex';
import { onDragStart, onDragEnd } from '@/store/modules/dragVideo.js';
export default {
  name: 'EquipmentListTree',
  computed: {
    ...mapState('dragVideo', ['splitType']),
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
    // robots() {
      // return this.$store.getters['websocketRobot/getRobots']?.filter(item => item.robotId === this.$store.getters['websocketRobot/getSelectedRobotId']);
    // }
    selectedRobot() {
      return this.$store.getters['websocketRobot/getSelectedRobot'] || {}
    },
  },
  data() {
    return {
      equipmentCameraObj: {},
      equipmentList1: [
        // {
        //   name: '机器狗本体',
        //   svg: 'robot-dog',
        //   list: [
        //     {
        //       name: '前置摄像头',
        //       status: 0,
        //     },
        //     {
        //       name: '后置摄像头',
        //       status: 1,
        //     },
        //     {
        //       name: '左置摄像头',
        //       status: 0,
        //     },
        //     {
        //       name: '右置摄像头',
        //       status: 0,
        //     }
        //   ]
        // },
        // {
        //   name: '红外装备',
        //   svg: 'infrared',
        //   list: [
        //     {
        //       name: '可见光',
        //       status: 0,
        //     },
        //     {
        //       name: '红外热成像',
        //       status: 0,
        //     },
        //   ]
        // },
        // {
        //   name: '激光雷达',
        //   svg: 'lidar',
        //   list: [
        //     {
        //       name: '点云视图',
        //       status: 0,
        //     },
        //   ]
        // },
      ],
      collapse: [],
      checkedIds: [],
    }
  },
  created() {
    this.setSplitType(4)
  },
  async mounted() {
    this.updateRobot()
    // for (const [index, key] of Object.keys(this.activeCameras).entries()) {
    //   console.log(1);
    //   await this.stopCamera(this.activeCameras[key].camera);
    // }
    if (this.splitType === 1 || this.splitType !== this.checkedIds.length) {
      const cameras = (this.robots?.[0]?.cameras || [])
      await this.$emit('updateVideo', cameras)
    }
  },
  methods: {
    ...mapActions('dragVideo', ['dragStart', 'setSplitType']),
    ...mapActions('websocketRobot', ['stopCamera', 'startCamera', 'toggleCamera']),
    onDragStart,
    onDragEnd,
    async updateRobot() {
      const robot = Object.assign({}, this.selectedRobot)
      const cameras = [...(robot?.cameras || [])]
      if (robot.robotId) {
        const groupKey = 'camera'
        this.$set(this.equipmentCameraObj, groupKey, {
          type: groupKey,
          name: '摄像头',
          svg: 'infrared',
          cameras: {},
        })
        for (const item of cameras) {
          this.$set(this.equipmentCameraObj[groupKey].cameras, item.name, { robot, ...item })
        }
      }
    },
    toggleCollapse(index) {
      this.$set(this.collapse, index, !this.collapse[index])      
    },
    async handleClickCamera(item) {
      // console.log('点击摄像头', item);
      if (this.splitType === 1 || this.splitType !== this.checkedIds.length) {
        await this.$emit('updateVideo', item)
      }
    },
  },
  watch: {
    splitType: {
      handler() {
        this.checkedIds = []
      },
      deep: true
    },
    robots: {
      handler(newVal) {
        this.updateRobot()
      },
      deep: true,
      // immediate: true,
    },
    activeCameras: {
      handler(newVal) {
        this.checkedIds = Object.keys(newVal).map(key => key)
      },
      deep: true
    },
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
  overflow: hidden;
  &.error {
    color: #FF3434;
    background: rgba(210, 40, 40, 0.20);
    box-shadow: 0 0 4px 0 #FF6969 inset;
  }
}
</style>
