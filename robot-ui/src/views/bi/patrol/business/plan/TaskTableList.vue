<template>
  <div class="ml10 flex1 h100">
    <div class="flx-justify-between">
      <div class="custom-tab-button flex">
        <div
          v-for="item in tabList"
          :key="item.value"
          class="tab-button-item pt5 pb5"
          style="font-size: 14px; line-height: 19px;"
          :class="{ 'is-active': tabValue === item.value }"
          @click="tabValue = item.value">
          {{ item.label }}
        </div>
      </div>
      <div class="flx-align-center">
        <div class="custom-search-div">
          <el-input
            placeholder="请输入内容"
            v-model="searchValue">
            <svg-icon slot="prefix" icon-class="search"></svg-icon>
          </el-input>
        </div>
        <div class="table-btns ml10">
          <el-button type="primary" plain style="color: #17D1FF" @click="addTask">
            <svg-icon icon-class="plus" class="mr10" />
            创建任务
          </el-button>
        </div>
      </div>
    </div>
    <div class="mt20">
      <div class="flex1">
        <div class="mm_table_box table h100">
          <el-table :data="taskData.tableData.data" ref="dataTableRef1" style="height: calc(100% - 34px)">
            <el-table-column type="index" key="index" width="50" label="序号" align="center">
              <template slot-scope="scope">
                <span class="td-index1">
                  {{ (taskData.tableData.page - 1) * taskData.tableData.size + scope.$index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="planName" key="planName" label="任务名称" show-overflow-tooltip></el-table-column>
            <el-table-column prop="equipmentName" key="equipmentName" label="装备名称" width="185">
              <template slot-scope="scope">
                <span class="type">{{ scope.row.equipmentName }}</span>
                <!-- <span class="type ml10">机器人02</span> -->
              </template>
            </el-table-column>
            <el-table-column prop="createTime" key="createTime" label="创建时间" show-overflow-tooltip></el-table-column>
            <el-table-column key="scheduleConfig.preset" label="执行周期" show-overflow-tooltip>
              <template slot-scope="scope">
                {{ schedulePresetLabel(scope.row?.scheduleConfig?.preset) }}
              </template>
            </el-table-column>
            <el-table-column key="executionMode" label="执行方式" show-overflow-tooltip>
              <template slot-scope="scope">
                {{ executionModeLabel(scope.row.executionMode) }}
              </template>
            </el-table-column>
            <el-table-column key="nextTriggerAt" label="下次执行时间" show-overflow-tooltip>
              <template slot-scope="scope">
                {{ formatDateTime(scope.row.nextTriggerAt) }}
              </template>
            </el-table-column>
            <el-table-column key="status" width="100" label="执行状态">
              <template slot-scope="scope">
                <span class="status" :class="{ green: scope.row.executionStatus === 'RUNNING', orange: scope.row.executionStatus === 'IDLE', red: scope.row.executionStatus === 'LAST_FAILED' }">
                  {{ executionStatusLabel(scope.row.executionStatus) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="278">
              <template slot-scope="scope">
                <el-button type="text" @click="$emit('close', scope.row.id)">详情</el-button>
                <el-button type="text" class="ml24">编辑</el-button>
                <el-button
                  type="text"
                  class="ml24"
                  :disabled="scope.row.enabled"
                  :loading="isStarting(scope.row.id)"
                  @click="startPlan(scope.row)"
                >
                  {{ scope.row.executionStatus === 1 ? '暂停' : '立即执行' }}
                </el-button>
                <el-button type="text" class="ml24" @click="deletePlan(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <!-- v-if="taskData.tableData.total > taskData.tableData.size" -->
          <el-pagination
            v-model:current-page="taskData.tableData.page"
            background
            :page-size="taskData.tableData.size"
            :total="taskData.tableData.total"
            layout="prev, pager, next"
            @current-change="getList"
            class="mt10"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getTaskList, startTask, deleteTask } from '../../../../../api/new-bi';
export default {
name: 'BiPatrolBusinessTask',
  data() {
    return {
      tabList: [
        {
          value: 'all',
          label: '全部',
        },
        {
          value: 'IDLE',
          label: '待执行',
        },
        {
          value: 'RUNNING',
          label: '执行中',
        },
        {
          value: 'LAST_COMPLETED, LAST_FAILED',
          label: '已完成',
        },
      ],
      tabValue: 'all',
      taskData: {
        tableData: {
          data: [],
          total: 0,
          size: 10,
          page: 1
        },
      },
      searchValue: '',
      startingPlanIds: new Set()
    }
  },
  methods: {
    // 巡逻巡查调度任务
    async getList(page) {
      if (page) {
        this.taskData.tableData.page = page
      }
      // 已完成参数需对接
      const executeStatus = this.tabValue === 'all' ? undefined : this.tabValue
      let query = {pageNum: this.taskData.tableData.page, pageSize: this.taskData.tableData.size, executeStatus }
      try {
        const res = await getTaskList(query)        
        if (res.code === '0') {
          let data = res.data
          this.taskData.tableData.data = data.records.map(item => {
            item.loading = false
            return item
          })
          this.taskData.tableData.total = data.total
        } else {
          this.$message.error(res.message)
        }
      } catch (error) {
        // this.$message.error(error.message)
      }
    },
    // 执行
    async startPlan(row) {
      if (row.activeWorkflowInstanceId) {
        // 跳转执行记录页面
        // this.$router.push(`/eiop/tasks/execution-records/${row.activeWorkflowInstanceId}`);
        return;
      }
      if (this.isStarting(row.id)) return;
      this.startingPlanIds = new Set(this.startingPlanIds).add(row.id);
      const res = await startTask(row.id, {});
      if (res.code === '0') {
        if (!res.data.accepted) {
          this.$message.warning(res.data.message || "任务未能启动");
          return;
        }
        this.$message.success(res.data.message || "任务已启动");
        const next = new Set(this.startingPlanIds);
        next.delete(row.id);
        this.startingPlanIds = next;
        await this.getList()
      } else {
        this.$message.error(res.message)
      }
    },
    // 删除
    async deletePlan(row) {
      try {
        await deleteTask(row.id);
        this.$message.success('已删除');
        await getList(1);
      } catch (error) {
        if (error !== "cancel") this.$message.error(error?.message || '请求失败');
      }
    },
    addTask() {},
    isStarting(planId) {
      return this.startingPlanIds.has(planId);
    },
    executionStatusLabel(value) {
      return {
        IDLE: '空闲',
        RUNNING: '运行中',
        LAST_COMPLETED: '最近完成',
        LAST_FAILED: '最近失败'
      }[value] || value || '空闲';
    },
    executionStatusType(value) {
      return {
        IDLE: 'info',
        RUNNING: 'warning',
        LAST_COMPLETED: 'success',
        LAST_FAILED: 'danger'
      }[value] || 'info';
    },
    formatDateTime(value) {
      if (!value) return '-';
      return String(value).replace('T', ' ').slice(0, 19);
    },
    executionModeLabel(value) {
      const executionModeOptions = [
        { label: '手动执行', value: 'MANUAL' },
        { label: '计划执行', value: 'SCHEDULE' }
      ];
      return executionModeOptions.find((item) => item.value === value)?.label || value || '-';
    },
    schedulePresetLabel(value) {
      const schedulePresetOptions = [
        { label: "每小时", value: "HOURLY" },
        { label: "每天", value: "DAILY" },
        { label: "工作日", value: "WORKDAY" },
        { label: "每周", value: "WEEKLY" },
        { label: "自定义", value: "CUSTOM" }
      ];
      return schedulePresetOptions.find((item) => item.value === value)?.label || value || '-';
    }

  },
  watch: {
    tabValue: {
      handler(newVal, oldVal) {
        this.getList(1)
      }
    }
  },
  mounted() {
    this.getList();
  }
}
</script>

<style scoped lang="scss">
@import '../common.scss';
</style>