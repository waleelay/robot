<template>
  <div class="w100">
    <div class="card-title">
      <div class="text">
        任务列表
      </div>
    </div>
    <div class="task-div mt10 hp500 p10 common-scroll">
      <div class="custom-search-div">
        <el-input
          placeholder="请输入内容"
          v-model="searchValue">
          <svg-icon slot="prefix" icon-class="search"></svg-icon>
        </el-input>
      </div>
      <div class="custom-collapse-div mt10" :class="{ collapse: collapse1 }">
        <div class="collapse-header p10 flx-justify-between" @click="collapse1 = !collapse1">
          <div class="flx-center">
            <svg-icon icon-class="app" />
            <span class="ml10">全部设备</span>
          </div>
          <div class="flx-center">
            <span>({{ deviceList.length }}台)</span>
            <svg-icon class="ml10" :icon-class="collapse1 ? 'down' : 'up'" style="color: #6A788B; font-family: Bahnschrift" />
          </div>
        </div>
        <div class="collapse-content p10 common-scroll">
          <div v-for="(item, index) in deviceList" :key="index" class="item flx-justify-between">
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
      <div class="custom-collapse-div mt10" :class="{ collapse: collapse2 }">
        <div class="collapse-header p10 flx-justify-between" @click="collapse2 = !collapse2">
          <div class="flx-center">
            <svg-icon icon-class="unlink" />
            <span class="ml10">未关联设备</span>
          </div>
          <svg-icon :icon-class="collapse2 ? 'down' : 'up'" />
        </div>
        <div class="collapse-content p10 common-scroll">
          <div v-for="(item, index) in deviceList.filter((item) => !item.bind)" :key="index" class="item flx-justify-between">
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
      <div class="task-list mt10">
        <div v-for="(item, index) in taskList" :key="index" class="item p10">
          <div class="flx-justify-between title">
            <div class="flx-align-center">
              <span class="address" style="font-size: 16px;">{{ item.address }}</span>-
              <span>{{ item.taskName }}</span>
            </div>
            <span class="status p4 wp48">进行中</span>
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
            <div v-for="(equipment, index) in item.equipments" :key="index" class="item flx-justify-between">
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
    <!-- <div class="task-item" v-for="(item, index) in taskList" :key="index">
      {{ item }}
    </div> -->
  </div>
</template>

<script>
export default {
  name: 'TaskList',
  data() {
    return {
      tabList: ['装备列表', '任务列表'],
      tabIndex: 0,
      searchValue: '',
      deviceList: [
        {
          name: '机器人-01',
          battery: 80,
          bind: true,
          type: 0
        },
        {
          name: '机器人-02',
          battery: 30,
          bind: false,
          type: 0
        },
        {
          name: '无人机-03',
          battery: 60,
          bind: true,
          type: 1
        },
        {
          name: '无人机-04',
          battery: 40,
          bind: false,
          type: 1
        },
        {
          name: '机器狗-05',
          battery: 70,
          bind: true,
          type: 2
        },
        {
          name: '无人机-06',
          battery: 50,
          bind: false,
          type: 1
        },
        {
          name: '无人机-07',
          battery: 90,
          bind: true,
          type: 1
        },
        {
          name: '无人机-08',
          battery: 20,
          bind: false,
          type: 1
        },
      ],
      collapse1: false,
      collapse2: false,
      collapse3: false,
      taskList: [
        {
          address: '123456',
          taskName: '任务-01',
          time: '2023-08-10 10:00:00',
          addressDetail: '北京市海淀区',
          equipments: [
            {
              name: '机器人-01',
              battery: 80,
              status: 0,
              type: 0
            },
            {
              name: '无人机-03',
              battery: 60,
              status: 0,
              type: 1
            },
          ]
        },
      ]
    }
  },
  mounted() {}
}
</script>
