<template>
  <PlanEdit
    v-if="mode !== 'list'"
    :id="currentId"
    :mode="mode"
    class="business2-content ml10 flex1 h100 no-w-scroll"
    @close="backToList"
    @saved="handleSaved"
  />
  <div v-else class="business2-content ml10 flex1 h100 no-w-scroll">
    <div class="business2-toolbar">
      <div class="business2-filter">
        <div class="custom-tab-button flex">
          <div
            v-for="item in statusTabs"
            :key="item.value"
            class="tab-button-item pt5 pb5"
            style="font-size: 14px; line-height: 19px;"
            :class="{ 'is-active': filters.executionStatus === item.value }"
            @click="changeStatus(item.value)"
          >
            {{ item.label }}
          </div>
        </div>
        <el-select v-model="filters.executionMode" clearable placeholder="执行方式" class="business2-search" @change="loadRows(1)">
          <el-option label="手动执行" value="MANUAL" />
          <el-option label="计划执行" value="SCHEDULE" />
        </el-select>
        <el-select v-model="filters.enabled" clearable placeholder="启用状态" class="business2-search" @change="loadRows(1)">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </div>
      <div class="business2-actions flx-align-center">
        <div class="custom-search-div">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="计划名称 / 编码"
            class="business2-search"
            @keyup.enter.native="loadRows(1)"
          >
            <svg-icon slot="prefix" icon-class="search" />
          </el-input>
        </div>
        <div class="table-btns ml10">
          <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="loadRows(1)">
            查询
          </el-button>
          <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="resetFilters">重置</el-button>
          <el-button type="primary" plain style="color: #17D1FF" @click="openEditor('', 'create')">
            <svg-icon icon-class="plus" class="mr10" />
            新建计划
          </el-button>
        </div>
      </div>
    </div>

    <div class="business2-table">
      <el-table v-loading="loading" :data="rows" style="width: 100%" :class="{'no-data': !rows.length}">
        <el-table-column type="index" width="60" label="序号" align="center">
          <template slot-scope="scope">
            <span class="td-index1">{{ (page.pageNum - 1) * page.pageSize + scope.$index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任务计划" min-width="220" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <div class="flex-column" style="justify-content: center; line-height: 22px;">
              <strong>{{ row.planName || '-' }}</strong>
              <div class="muted">{{ row.planCode || '-' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="workflowName" label="任务编排" min-width="190" show-overflow-tooltip />
        <el-table-column label="执行方式" width="110">
          <template slot-scope="{ row }">{{ executionModeLabel(row.executionMode) }}</template>
        </el-table-column>
        <el-table-column label="计划周期" width="110">
          <template slot-scope="{ row }">{{ schedulePresetLabel(row.scheduleConfig && row.scheduleConfig.preset) }}</template>
        </el-table-column>
        <el-table-column key="status" width="100" label="执行状态">
          <template slot-scope="scope">
            <span class="status" :class="{ green: scope.row.executionStatus === 'RUNNING', orange: scope.row.executionStatus === 'IDLE', red: scope.row.executionStatus === 'LAST_FAILED' }">
              {{ executionStatusLabel(scope.row.executionStatus) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="295" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openEditor(row.id, 'view')">详情</el-button>
            <el-button type="text" @click="openEditor(row.id, 'edit')">编辑</el-button>
            <el-button type="text" :disabled="!row.enabled" @click="previewPlan(row)">预览</el-button>
            <el-button
              type="text"
              :disabled="!row.enabled || Boolean(row.activeWorkflowInstanceId)"
              :loading="isStarting(row.id)"
              @click="startPlan(row)"
            >
              立即执行
            </el-button>
            <el-dropdown @command="handleMore($event, row)">
              <el-button type="text">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="toggle">{{ row.enabled ? '停用' : '启用' }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pagination-row"
        background
        :current-page.sync="page.pageNum"
        :page-size="page.pageSize"
        :total="page.total"
        layout="total, prev, pager, next"
        @current-change="loadRows"
      />
    </div>

    <el-dialog :visible.sync="previewVisible" title="执行预览" width="620px">
      <el-alert
        v-if="previewResult"
        :type="previewResult.valid ? 'success' : 'warning'"
        :title="previewResult.valid ? '计划校验通过' : '计划需要处理'"
        :closable="false"
        show-icon
      />
      <ul v-if="previewMessages.length" class="mt10">
        <li v-for="message in previewMessages" :key="message">{{ message }}</li>
      </ul>
      <el-alert
        v-if="previewRequirements.length"
        class="mt10"
        type="info"
        title="部分动作存在多个可执行组件；未指定时会由所有匹配组件执行。"
        :closable="false"
      />
      <span slot="footer">
        <el-button @click="previewVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import {
  deleteTask,
  getTaskList,
  startTask,
  startTaskPreview,
  updateTaskEnabled
} from '@/api/new-bi'
import PlanEdit from './PlanEdit.vue'

export default {
  name: 'BiPatrolBusiness2Plan',
  components: { PlanEdit },
  data() {
    return {
      mode: 'list',
      currentId: '',
      loading: false,
      rows: [],
      page: { pageNum: 1, pageSize: 10, total: 0 },
      filters: {
        keyword: '',
        executionMode: '',
        enabled: '',
        executionStatus: 'all'
      },
      statusTabs: [
        { value: 'all', label: '全部' },
        { value: 'IDLE', label: '空闲' },
        { value: 'RUNNING', label: '运行中' },
        { value: 'LAST_COMPLETED', label: '最近完成' },
        { value: 'LAST_FAILED', label: '最近失败' }
      ],
      startingPlanIds: [],
      previewVisible: false,
      previewResult: null
    }
  },
  computed: {
    previewMessages() {
      return this.previewResult && Array.isArray(this.previewResult.messages) ? this.previewResult.messages : []
    },
    previewRequirements() {
      return this.previewResult && Array.isArray(this.previewResult.componentSelectionRequirements)
        ? this.previewResult.componentSelectionRequirements
        : []
    }
  },
  mounted() {
    this.loadRows()
  },
  methods: {
    async loadRows(pageNum) {
      if (pageNum) this.page.pageNum = pageNum
      this.loading = true
      try {
        const params = {
          pageNum: this.page.pageNum,
          pageSize: this.page.pageSize,
          keyword: this.filters.keyword || undefined,
          executionMode: this.filters.executionMode || undefined,
          enabled: this.filters.enabled === '' ? undefined : this.filters.enabled,
          executeStatus: this.filters.executionStatus === 'all' ? undefined : this.filters.executionStatus
        }
        const data = this.unwrap(await getTaskList(params))
        this.rows = (data.records || []).map(item => Object.assign({ loading: false }, item))
        this.page.pageNum = data.pageNum || this.page.pageNum
        this.page.pageSize = data.pageSize || this.page.pageSize
        this.page.total = data.total || 0
      } catch (error) {
        this.showError(error)
      } finally {
        this.loading = false
      }
    },
    resetFilters() {
      this.filters = { keyword: '', executionMode: '', enabled: '', executionStatus: 'all' }
      this.loadRows(1)
    },
    changeStatus(value) {
      this.filters.executionStatus = value
      this.loadRows(1)
    },
    openEditor(id, mode) {
      this.currentId = id ? String(id) : ''
      this.mode = mode
    },
    backToList(refresh) {
      this.mode = 'list'
      this.currentId = ''
      if (refresh) this.loadRows()
    },
    handleSaved(id) {
      this.currentId = id ? String(id) : this.currentId
      this.mode = id ? 'edit' : 'list'
      this.loadRows()
    },
    async startPlan(row) {
      if (row.activeWorkflowInstanceId) {
        this.$emit('show-record', row.activeWorkflowInstanceId)
        return
      }
      if (this.isStarting(row.id)) return
      this.startingPlanIds = this.startingPlanIds.concat(row.id)
      try {
        const preview = this.unwrap(await startTaskPreview(row.id, {}))
        if (preview && preview.valid === false) {
          this.previewResult = preview
          this.previewVisible = true
          return
        }
        await this.$confirm('确认立即执行该任务计划？', '提示', { type: 'warning' })
        const data = this.unwrap(await startTask(row.id, {}))
        if (data && data.accepted === false) {
          this.previewResult = data.preview || data
          this.previewVisible = true
          this.$message.warning(data.message || '任务未能启动')
          return
        }
        this.$message.success((data && data.message) || '任务已启动')
        this.loadRows()
        if (data && data.workflowInstanceId) this.$emit('show-record', data.workflowInstanceId)
      } catch (error) {
        if (error !== 'cancel') this.showError(error)
      } finally {
        this.startingPlanIds = this.startingPlanIds.filter(id => id !== row.id)
      }
    },
    async previewPlan(row) {
      try {
        this.previewResult = this.unwrap(await startTaskPreview(row.id, {}))
        this.previewVisible = true
      } catch (error) {
        this.showError(error)
      }
    },
    handleMore(command, row) {
      if (command === 'toggle') {
        this.toggleEnabled(row)
      } else if (command === 'delete') {
        this.deletePlan(row)
      }
    },
    async toggleEnabled(row) {
      try {
        await updateTaskEnabled(row.id, !row.enabled)
        this.$message.success(row.enabled ? '已停用' : '已启用')
        this.loadRows()
      } catch (error) {
        this.showError(error)
      }
    },
    async deletePlan(row) {
      try {
        await this.$confirm(`确定删除计划“${row.planName || row.planCode || row.id}”？`, '提示', { type: 'warning' })
        await deleteTask(row.id)
        this.$message.success('已删除')
        this.loadRows(1)
      } catch (error) {
        if (error !== 'cancel') this.showError(error)
      }
    },
    isStarting(planId) {
      return this.startingPlanIds.indexOf(planId) !== -1
    },
    executionModeLabel(value) {
      return { MANUAL: '手动执行', SCHEDULE: '计划执行' }[value] || value || '-'
    },
    schedulePresetLabel(value) {
      return { HOURLY: '每小时', DAILY: '每天', WORKDAY: '工作日', WEEKLY: '每周', CUSTOM: '自定义' }[value] || value || '-'
    },
    executionStatusLabel(value) {
      return { IDLE: '空闲', RUNNING: '运行中', LAST_COMPLETED: '最近完成', LAST_FAILED: '最近失败' }[value] || value || '空闲'
    },
    executionStatusType(value) {
      return { IDLE: 'info', RUNNING: 'orange', LAST_COMPLETED: 'green', LAST_FAILED: 'red' }[value] || 'info'
    },
    formatDateTime(value) {
      if (!value) return '-'
      return String(value).replace('T', ' ').slice(0, 19)
    },
    unwrap(res) {
      if (res && res.code !== undefined) {
        if (res.code === '0' || res.code === 0 || res.code === 200) return res.data || {}
        throw new Error(res.message || '请求失败')
      }
      return res || {}
    },
    showError(error) {
      this.$message.error((error && error.message) || '请求失败')
    }
  }
}
</script>

<style scoped lang="scss">
@import '../common.scss';
@import '../table.scss';
</style>
