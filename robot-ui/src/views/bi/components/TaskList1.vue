<template>
  <div class="task-overview wp360 h100 flex-column">
    <div class="overview-box">
      <div class="title mb10 flx-justify-between">
        <div class="text">
          任务概览
        </div>
        <div class="flx-center type-icon">
          <span @click="listType = 'waterfall'">
            <svg-icon icon-class="waterfall" style="font-size: 16px;" :class="{'is-active': listType === 'waterfall'}" />
          </span>
          <span @click="listType = 'list'">
            <svg-icon icon-class="list" style="font-size: 16px;" :class="{'is-active': listType === 'list'}" class="ml10" />
          </span>
        </div>
      </div>
      <div class="mt20">
        <div class="flx-center total">
          <img src="@/assets/images/new-bi/icon1.png" width="62px" height="62px" />
          <span class="ml6 text">任务总数</span>
          <span class="count ml6">
            <span class="num mr6">800</span>个
          </span>
        </div>
        <div class="flx-justify-between mt10 pending mr12 ml12">
          <div class="flx-align-center">
            <img src="@/assets/images/new-bi/icon2.svg" width="40px" height="40px" />
            <span class="ml10 text">正在执行任务</span>
          </div>
          <span class="count wp137">
            <span class="num">598</span>个
          </span>
        </div>
      </div>
      <div class="flx-justify-between mt20 pr24 pl24 statistics">
        <div class="desc-item">
          <div class="flx-justify-between flex-column">
            <div class="count">
              <span class="num">120</span> 个
            </div>
            <div class="desc mt10">待执行任务</div>
          </div>
        </div>
        <div class="vertical-line"></div>
        <div class="desc-item">
          <div class="flx-justify-between flex-column">
            <div class="count">
              <span class="num">8</span> 个
            </div>
            <div class="desc mt10">已完成任务</div>
          </div>
        </div>
        <div class="vertical-line"></div>
        <div class="desc-item">
          <div class="flx-justify-between flex-column h100">
            <div class="count">
              <span class="num">356</span> 个
            </div>
            <div class="desc mt10">异常任务</div>
          </div>
        </div>
      </div>
    </div>
    <div class="list-box mt30 flex1 flex-column" style="overflow-y: hidden; margin-right: -8px;">
      <div class="title mb10">
        <div class="text">
          任务列表
        </div>
      </div>
      <div style="line-height: 0;">
        <img src="../../../assets/images/new-bi/title-line-340-2.png" alt="" width="340" height="2">
      </div>
      <div v-if="listType === 'waterfall'" class="waterfall-list mt10 mb10 pr4 flex1 common-scroll" style="overflow-y: auto;">
        <div class="item wp340 hp266" v-for="item in listData" :key="item.id">
          <div class="flx-justify-between">
            <div class="name">{{ item.name }}</div>
            <div class="status success">{{ item.status }}</div>
          </div>
          <div class="flx-justify-between flex-wrap mt20">
            <div class="w50 pl10">
              <div class="term">
                <div class="flx-align-center">
                  <svg-icon icon-class="address" style="color: #0BF9FE; font-size: 14px;" />
                  <div class="text ml10">位置</div>
                </div>
                <div class="mt10 value">A区-3号通道</div>
              </div>
            </div>
            <div class="w50 pl20">
              <div class="term">
                <div class="flx-align-center">
                  <svg-icon icon-class="time" style="color: #AF811F; font-size: 14px;" />
                  <div class="text ml10">完成度</div>
                </div>
                <div class="mt10 value">80%</div>
              </div>
            </div>
            <div class="w50 mt20 pl10">
              <div class="term">
                <div class="flx-align-center">
                  <svg-icon icon-class="robot" style="color: #A453F1; font-size: 14px;" />
                  <div class="text ml10">设备</div>
                </div>
                <div class="mt10 value">3台</div>
              </div>
            </div>
            <div class="w50 mt20 pl20">
              <div class="term">
                <div class="flx-align-center">
                  <svg-icon icon-class="time" style="color: #3B82F6; font-size: 14px;" />
                  <div class="text ml10">时间</div>
                </div>
                <div class="mt10 value">18:00-20:00</div>
              </div>
            </div>
          </div>
          <div class="operation flx-align-center mt20">
            <el-button class="detail flex1" type="primary">
              <span class="flx-center">
                详情
                <svg-icon icon-class="down" class="ml10" style="transform: rotate(-90deg);" />
              </span>
            </el-button>
            <el-button type="primary" class="play-pause ml10">
              <svg-icon icon-class="pause" />
            </el-button>
            <el-button type="primary" class="delete ml10">
              <svg-icon icon-class="close1" />
            </el-button>
          </div>
        </div>
      </div>
      <List v-else class="mt10 flex1 flex-column" style="overflow-y: hidden;"/>
    </div>
    <div class="flx-justify-between mt10 w100 task-btns">
      <el-button type="primary" plain class="wp234" style="color: #17D1FF" @click="addTask">
        <svg-icon icon-class="plus" class="mr10" />
        添加新任务
      </el-button>
      <el-button type="primary" plain class="wp100" @click="$emit('changeSide')">任务管理</el-button>
    </div>

    <!-- 模态框 -->
    <TaskAdd ref="taskAddRef" />
  </div>
</template>

<script>
import List from './List.vue';
import TaskAdd from './modal/TaskAdd.vue';
export default {
  name: 'TaskList1',
  components: {
    List,
    TaskAdd
  },
  data() {
    return {
      listType: 'waterfall',
      listData: [
        {
          id: 1,
          name: '任务1',
          status: '进行中',
          time: '2023-08-01 10:00:00'
        },
        {
          id: 2,
          name: '任务2',
          status: '已完成',
          time: '2023-08-02 12:00:00'
        },
        {
          id: 3,
          name: '任务3',
          status: '已完成',
          time: '2023-08-03 14:00:00'
        },
        {
          id: 4,
          name: '任务4',
          status: '进行中',
          time: '2023-08-04 16:00:00'
        },
      ]
    }
  },
  methods: {
    addTask() {
      this.$refs.taskAddRef.showModal();
    }
  }
}
</script>