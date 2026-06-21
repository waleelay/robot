<template>
  <div class="robot-box flex">
    <div v-for="item in robotList" :key="item.index" class="robot flx-center flex-column" :class="{'is-active': item.status === 1, 'is-error': item.status === 2, 'is-off': item.status === 3}">
      <img
        :src="require(`@/assets/images/new-bi/${item.type}-${item.status === 0 ? 'normal' : item.status === 1 ? 'active' : item.status === 2 ? 'error' : 'off'}.png`)"
        alt=""
        :width="item.status === 1 ? 66 : 50"
        :height="item.status === 1 ? 93 : 70"
      />
      <div class="status mt10">{{ item.name }}({{ item.status === 1 ? '任务中' : item.status === 2 ? '异常' : item.status === 3 ? '离线' : '空闲' }})</div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      robotList: [
        {
          index: 'robot',
          name: '机器人01',
          type: 'robot',
          status: 0//空闲
        },
        {
          index: 'robot-dog',
          name: '机器狗01',
          type: 'robot-dog',
          status: 1// 任务中
        },
        {
          index: 'uav',
          name: '无人机01',
          type: 'robot-uav',
          status: 2// 异常
        },
        {
          index: 'charge',
          name: '充电桩1',
          type: 'robot-charge',
          status: 3
        },
      ],
    }
  }
}
</script>