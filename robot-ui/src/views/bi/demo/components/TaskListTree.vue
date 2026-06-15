<template>
  <div class="w100 hp500">
    <div class="card-title">
      <div class="text">
        任务列表
      </div>
    </div>
    <!-- common-scroll -->
    <div class="tab-content mt10 pr10 pb10 pl10" style="height: calc(100% - 47px);">
      <div class="task-div-tab mt20 mb20 flx-center">
        <div
          class="tab-item flex1"
          v-for="(item, index) in tabList"
          :key="item"
          :class="{ 'is-active': tabIndex === index }"
          @click="tabChange(index)"
        >
          {{ item }}
        </div>
      </div>
      <div class="custom-search-div">
        <el-input
          placeholder="请输入内容"
          v-model="searchValue">
          <svg-icon slot="prefix" icon-class="search"></svg-icon>
        </el-input>
      </div>
      <div v-if="tabIndex === 0" class="equipment-div mt10 common-scroll" style="height: calc(100% - 119px); margin-right: -10px;">
        <div class="collapse-box pl26 h100">
          <div
            v-for="(equipment, typeIndex) in Object.values(equipmentInfo)"
            :key="equipment.type"
            class="custom-collapse-div"
            :class="{ collapse: collapseArr[typeIndex] }"
          >
            <div class="collapse-header p10 flx-justify-between" @click="toggleCollapse(typeIndex)">
              <div class="flx-center">
                <svg-icon icon-class="unlink" />
                <span class="ml10">{{ equipment.type }}({{ equipment.list.length }})</span>
              </div>
              <svg-icon :icon-class="collapseArr[typeIndex] ? 'down' : 'up'" />
            </div>
            <div class="collapse-content pl12 common-scroll mr10 pr7">
              <div
                v-for="(item, index) in equipment.list"
                :key="item.name"
                class="item flx-justify-between"
                :class="{ 'is-active': selectedEquipmentList1.includes(item.name) }"
                :draggable="!selectedEquipmentList1.includes(item.name)"
                @dragstart="onDragStart($event, item)"
                @dragend="onDragEnd"
                :style="{ cursor: !selectedEquipmentList1.includes(item.name) ? 'grab' : 'default' }"
              >
                <!-- @click="handleSelectEquipment(item)" -->
                <div class="flx-center">
                  <svg-icon :icon-class="item.type === 0 ? 'robot' : item.type === 1 ? 'robot-uav' : 'robot-dog'" />
                  <span class="ml10">{{ item.name }}</span>
                </div>
                <div class="flx-center">
                  <svg-icon
                    :icon-class="item.battery >= 90 ? 'battery-4' : item.battery >= 80 ? 'battery-3' : item.battery >= 50 ? 'battery-2' : item.battery >= 40 ? 'battery-1' : 'battery-0'"
                    :style="{ color: item.battery < 50 ? '#D33333' : '#3DB56A' }"
                  >
                  </svg-icon>
                  <span class="ml10">{{ item.battery }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="task-div1 mt10 common-scroll pr10" style="height: calc(100% - 119px); margin-right: -10px;">
        <div class="task-list">
          <div
            v-for="(item, index) in taskList"
            :key="index"
            class="task-item p10"
            :class="{ 'is-active': item.id === selectedTaskId }"
            @click="handleSelectTask(item)"
            >
            <div class="flx-justify-between title">
              <div class="flx-align-center">
                <span class="address" style="font-size: 16px;">{{ item.address }}</span>-
                <span>{{ item.taskName }}</span>
              </div>
              <span class="status p4 wp50">进行中</span>
            </div>
            <div class="info mt10">
              <div class="flx-align-center">
                <svg-icon icon-class="time" />
                <span class="ml10">{{ item.time }}（预计45分钟）</span>
              </div>
              <div class="flx-align-center mt6">
                <svg-icon icon-class="address" />
                <span class="ml10">{{ item.addressDetail }}</span>
              </div>
              <div class="flx-align-center mt6">
                <span>执行装备（{{ item.equipments.length }}）</span>
              </div>
            </div>
            <div class="device mt10" v-if="item.equipments.length">
              <div
                v-for="equipment in item.equipments"
                :key="equipment.name"
                class="item flx-justify-between"
                :class="{ 'is-active': selectedEquipmentList2.includes(equipment.name) }"
                :draggable="!selectedEquipmentList2.includes(equipment.name)"
                @dragstart="onDragStart($event, equipment)"
                @dragend="onDragEnd"
                :style="{ cursor: !selectedEquipmentList2.includes(equipment.name) ? 'grab' : 'default' }"
              >
                <!-- @click="handleSelectEquipment(equipment)" -->
                <div class="flx-center">
                  <svg-icon :icon-class="equipment.type === 0 ? 'robot' : equipment.type === 1 ? 'robot-uav' : 'robot-dog'" />
                  <span class="ml10">{{ equipment.name }}</span>
                </div>
                <div class="flx-center">
                  <svg-icon
                    :icon-class="equipment.battery >= 90 ? 'battery-4' : equipment.battery >= 80 ? 'battery-3' : equipment.battery >= 50 ? 'battery-2' : equipment.battery >= 40 ? 'battery-1' : 'battery-0'"
                    :style="{ color: equipment.battery < 50 ? '#D33333' : '#3DB56A' }"
                  >
                  </svg-icon>
                  <span class="ml4 battery">{{ equipment.battery }}%</span>  
                  <span class="status ml10 p4" :class="{ error: equipment.status === 1 }">{{ equipment.status === 1 ? '异常' : '执行中' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!-- <div class="task-item" v-for="(item, index) in taskList" :key="index">
      {{ item }}
    </div> -->
  </div>
</template>

<script>
import { mapState, mapActions, mapGetters } from 'vuex'
export default {
  name: 'TaskListTree',
  computed: {
    ...mapState('dragVideo', ['dropResult', 'splitType'])
    // ...mapGetters('dragVideo', ['dropResult'])
    // dropResult() {
    //   // return this.$store.getters['dragVideo/dropResult'];
    //   return this.$store.state.dragVideo.dropResult;
    // }
  },
  data() {
    return {
      tabList: ['装备列表', '任务列表'],
      tabIndex: 0,
      searchValue: '',
      taskList: [
        {
          id: 1,
          address: '123456',
          taskName: '任务-01',
          time: '2023-08-10 10:00:00',
          addressDetail: '北京市海淀区',
          equipments: [
            {
              name: '机器人-11',
              battery: 80,
              status: 0,
              type: 0
            },
            {
              name: '无人机-13',
              battery: 60,
              status: 0,
              type: 1
            },
          ]
        },
        {
          id: 2,
          address: '123456',
          taskName: '任务-02',
          time: '2023-08-10 10:00:00',
          addressDetail: '北京市海淀区',
          equipments: [
            {
              name: '机器人-15',
              battery: 80,
              status: 0,
              type: 0
            },
            {
              name: '无人机-16',
              battery: 60,
              status: 0,
              type: 1
            },
          ]
        },
      ],
      selectedTaskId: 1,
      equipmentList: [
        {
          name: '机器人-11',
          battery: 80,
          status: 0,
          type: 0,
          online: true,
          association: true,
          pending: true
        },
        {
          name: '无人机-13',
          battery: 60,
          status: 0,
          type: 1,
          online: true,
          association: true,
          pending: true
        },
        {
          name: '机器狗-15',
          battery: 70,
          status: 0,
          type: 2,
          online: false,
          association: false,
          pending: false
        },
        {
          name: '无人机-16',
          battery: 50,
          status: 0,
          type: 1,
          online: true,
          association: false,
          pending: false
        }
      ],
      equipmentInfo: {
        online: {
          type: '在线装备',
          list: []
        },
        unAssociation: {
          type: '未关联装备',
          list: []
        },
        pending: {
          type: '任务中装备',
          list: []
        },
        offline: {
          type: '离线装备',
          list: []
        }
      },
      collapseArr: [],
      selectedEquipmentList1: [],
      selectedEquipmentList2: []
    }
  },
  mounted() {
    this.equipmentList.map(item => {
      if (item.online) {
        this.equipmentInfo.online.list.push(item)
      } else {
        this.equipmentInfo.offline.list.push(item)  
      }
      if (!item.association) {
        this.equipmentInfo.unAssociation.list.push(item)
      }
      if (item.pending) {
        this.equipmentInfo.pending.list.push(item)
      }
    })
  },
  methods: {
    ...mapActions('dragVideo', ['dragStart']),
    tabChange(index) {  
      this.tabIndex = index
      if (index && this.taskList.length) {
        let task = this.taskList.filter(item => item.id === this.selectedTaskId)[0]
        const taskItem = task || this.taskList[0]
        this.selectedTaskId = taskItem.id
        this.selectedEquipmentList2 = taskItem.equipments.map(item => item.name).slice(0, this.splitType)
      }
      this.$emit('select-task', index ? this.selectedEquipmentList2 : this.selectedEquipmentList1)
    },
    handleSelectEquipment(equipment, selectedRows) {
      this[this.tabIndex ? 'selectedEquipmentList2' : 'selectedEquipmentList1'] = selectedRows.filter(item => item !== null)
      // if (this.selectedEquipmentList.includes(equipment.name)) {
      //   this.selectedEquipmentList = this.selectedEquipmentList.filter(item => item !== equipment.name)
      // } else {
      //   this.selectedEquipmentList.push(equipment.name)
      // }
    },
    toggleCollapse(typeIndex) {
      this.$set(this.collapseArr, typeIndex, !this.collapseArr[typeIndex])
    },
    handleSelectTask(task) {
      this.selectedTaskId = task.id
      this.selectedEquipmentList = task.equipments.map(item => item.name).slice(0, this.splitType)
      this.$emit('select-task', this.selectedEquipmentList)
    },
    // 拖拽开始: 将任务数据存入 dataTransfer
    onDragStart(event, info) {
      // 设置拖拽数据 - 使用纯文本格式
      event.dataTransfer.setData('text/plain', JSON.stringify({ data: info, componentId: 'taskListComponent' }));
      // 设置拖拽效果
      event.dataTransfer.effectAllowed = 'move';
      // 添加拖拽中的样式
      event.target.classList.add('dragging');
      this.dragStart({ data: info, componentId: 'taskListComponent' })
    },
    onDragEnd(event) {
      // 移除拖拽样式
      event.target.classList.remove('dragging');
    }
  },
  watch: {
    dropResult: {
      handler(newResult) {
        console.log('测试', newResult);
        if (newResult) {
          if (newResult.componentId === 'taskListComponent') {
            this.handleSelectEquipment(newResult.data, newResult.selectedRows)
          }
        } else {
          this[this.tabIndex ? 'selectedEquipmentList2' : 'selectedEquipmentList1'] = []
        }
      },
      deep: true
    },
    splitType: {
      handler() {
        if (this.tabIndex) {
          this.tabChange(1)
        }
      },
      deep: true
    }
  }
}
</script>
