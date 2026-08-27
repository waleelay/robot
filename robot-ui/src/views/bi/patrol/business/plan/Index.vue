<template>
  <PlanEdit
    v-if="mode !== 'list'"
    :id="currentId"
    :mode="mode"
    class="business2-content ml10 flex1 h100 no-w-scroll"
    @close="backToList"
    @saved="handleSaved"
  />
  <div v-else class="business2-content ml10 flex1 flex-column h100 no-w-scroll">
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
        <!-- <el-select v-model="filters.enabled" clearable placeholder="启用状态" class="business2-search" @change="loadRows(1)">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select> -->
      </div>
      <div class="business2-actions flx-align-center">
        <div class="custom-search-div">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="计划名称 / 编码"
            class="business2-search"
            @keyup.enter.native="loadRows(1)"
            @clear="resetFilters"
          >
            <svg-icon slot="prefix" icon-class="search" />
          </el-input>
        </div>
        <div class="table-btns ml10">
          <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="loadRows(1)">
            查询
          </el-button>
          <!-- <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="resetFilters">重置</el-button> -->
          <el-button
            v-if="canCreatePlan"
            type="primary"
            plain
            style="color: #17D1FF"
            @click="openEditor('', 'create')"
          >
            <svg-icon icon-class="plus" class="mr10" />
            新建计划
          </el-button>
        </div>
      </div>
    </div>

    <div class="business2-table flex1 flex-column">
      <div class="flex1">
        <el-table
          v-loading="loading"
          :key="permissionRenderKey"
          :data="rows"
          style="width: 100%"
          :empty-text="canViewPlan ? '暂无数据' : '无权限'"
          :class="{'no-data': !rows.length}"
        >
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
          <el-table-column label="预计时长" width="110" align="center">
            <template slot-scope="{ row }">{{ durationLabel(row.expectedDurationSeconds) }}</template>
          </el-table-column>
          <el-table-column key="status" width="150" label="执行状态" align="center">
            <template slot-scope="scope">
              <div class="execution-status-cell">
                <span class="status" :class="executionStatusType(scope.row.executionStatus)">
                  {{ executionStatusLabel(scope.row.executionStatus) }}
                </span>
                <span v-if="scope.row.controlStatus === 'OPERATING'" class="execution-control-note">控制中</span>
                <span v-else-if="scope.row.controlStatus === 'EXCEPTION'" class="execution-control-note is-error">控制异常</span>
              </div>
            </template>
          </el-table-column>
          <!-- <el-table-column key="lastResultStatus" min-width="140" label="最近一次执行状态" align="center">
            <template slot-scope="scope">
              <span
                v-if="scope.row.lastResultStatus"
                class="status"
                :class="executionStatusType(scope.row.lastResultStatus)"
              >
                {{ executionStatusLabel(scope.row.lastResultStatus) }}
              </span>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column> -->
          <el-table-column label="操作" width="430" fixed="right">
            <template slot-scope="{ row }">
              <el-button
                type="text"
                :disabled="!canViewPlan"
                :title="canViewPlan ? undefined : '无权限'"
                @click="openEditor(row.id, 'view')"
              >
                详情
              </el-button>
              <el-button
                v-if="canEditPlan"
                type="text"
                @click="openEditor(row.id, 'edit')"
              >
                编辑
              </el-button>
              <el-button type="text" :disabled="!row.enabled" @click="previewPlan(row)">预览</el-button>
              <el-button
                v-if="row.activeWorkflowInstanceId"
                type="text"
                @click="$emit('show-record', row.activeWorkflowInstanceId)"
              >
                查看监控
              </el-button>
              <el-button
                v-if="hasLifecycleAction(row, 'PAUSE') && canPauseExecution"
                type="text"
                @click="controlPlanInstance(row, 'PAUSE')"
              >
                暂停
              </el-button>
              <el-button
                v-if="hasLifecycleAction(row, 'RESUME') && canResumeExecution"
                type="text"
                @click="controlPlanInstance(row, 'RESUME')"
              >
                恢复
              </el-button>
              <el-button
                v-if="(hasLifecycleAction(row, 'TERMINATE') || hasLifecycleAction(row, 'RETRY_TERMINATE')) && canTerminateExecution"
                type="text"
                @click="controlPlanInstance(row, 'TERMINATE')"
              >
                {{ hasLifecycleAction(row, 'RETRY_TERMINATE') ? '重试终止' : '终止' }}
              </el-button>
              <el-button
                v-if="hasLifecycleAction(row, 'FORCE_TERMINATE') && canForceTerminateExecution"
                type="text"
                @click="forceTerminatePlanInstance(row)"
              >
                强制结束
              </el-button>
              <el-button
                v-if="!row.activeWorkflowInstanceId && canExecutePlan"
                type="text"
                :disabled="!row.enabled"
                :loading="isStarting(row.id)"
                @click="startPlan(row)"
              >
                {{ isStarting(row.id) ? '执行中' : '立即执行' }}
              </el-button>
              <el-button
                v-if="canDeletePlan"
                type="text"
                @click="deletePlan(row)"
              >
                删除
              </el-button>
              <!-- <el-dropdown @command="handleMore($event, row)">
                <el-button type="text">更多<i class="el-icon-arrow-down el-icon--right" /></el-button>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item command="toggle">{{ row.enabled ? '停用' : '启用' }}</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown> -->
            </template>
          </el-table-column>
        </el-table>
      </div>
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

    <el-dialog :visible.sync="executionParameterDialog.visible" title="补充执行参数" width="620px" append-to-body>
      <p class="dialog-description">请填写本次任务需要的执行参数，保存后将用于本次立即执行。</p>
      <el-form label-position="top">
        <div v-if="dialogMissingRequirements.length" class="form-grid">
          <el-form-item
            v-for="parameter in dialogMissingRequirements"
            :key="parameterBindingKey(parameter)"
            :label="parameterDisplayLabel(parameter)"
            required
          >
            <el-select
              v-if="parameterHasEnum(parameter)"
              :value="dialogParameterValue(parameter)"
              clearable
              :placeholder="parameter.description || '选择' + parameter.label"
              @change="setDialogParameterValue(parameter, $event)"
            >
              <el-option
                v-for="option in parameter.schema.enum"
                :key="String(option)"
                :label="parameterEnumLabel(parameter, option)"
                :value="option"
              />
            </el-select>
            <el-switch
              v-else-if="parameterSchemaType(parameter) === 'boolean'"
              :value="dialogParameterValue(parameter) === true"
              @change="setDialogParameterValue(parameter, $event)"
            />
            <el-input-number
              v-else-if="isNumberParameter(parameter)"
              :value="dialogNumberParameterValue(parameter)"
              :min="parameter.schema && parameter.schema.minimum"
              :max="parameter.schema && parameter.schema.maximum"
              :step="(parameter.schema && parameter.schema.multipleOf) || 1"
              controls-position="right"
              @change="setDialogParameterValue(parameter, $event)"
            />
            <el-input
              v-else
              :value="dialogParameterValue(parameter)"
              :type="parameter.schema && parameter.schema.format === 'textarea' ? 'textarea' : 'text'"
              :rows="parameter.schema && parameter.schema.format === 'textarea' ? 3 : undefined"
              :placeholder="parameter.description || '填写' + parameter.label"
              @input="setDialogParameterValue(parameter, $event)"
            />
            <div v-if="parameter.description" class="parameter-description">{{ parameter.description }}</div>
          </el-form-item>
        </div>
        <section
          v-for="target in dialogMissingTargetRequirements"
          :key="dialogTargetBindingKey(target)"
          class="execution-parameter-card"
        >
          <strong>{{ targetDisplayLabel(target) }}</strong>
          <div class="form-grid">
            <el-form-item label="X 坐标" required>
              <el-input-number
                :value="dialogTargetNumberValue(target, 'x')"
                controls-position="right"
                @change="setDialogTargetValue(target, 'x', $event)"
              />
            </el-form-item>
            <el-form-item label="Y 坐标" required>
              <el-input-number
                :value="dialogTargetNumberValue(target, 'y')"
                controls-position="right"
                @change="setDialogTargetValue(target, 'y', $event)"
              />
            </el-form-item>
            <el-form-item label="朝向">
              <el-input-number
                :value="dialogTargetNumberValue(target, 'yaw')"
                controls-position="right"
                @change="setDialogTargetValue(target, 'yaw', $event)"
              />
            </el-form-item>
          </div>
        </section>
      </el-form>
      <span slot="footer">
        <el-button @click="executionParameterDialog.visible = false">取消</el-button>
        <el-button
          v-if="canExecutePlan"
          type="primary"
          :loading="executionParameterDialog.submitting"
          @click="submitExecutionParameters"
        >
          保存并执行
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import {
  deleteTask,
  forceTerminateTaskRecord,
  getTaskList,
  pauseTaskRecord,
  resumeTaskRecord,
  startTask,
  startTaskPreview,
  terminateTaskRecord,
  updateTaskEnabled
} from '@/api/new-bi'
import { isRequestErrorNotified } from '@/utils/request'
import {
  executionStatusLabel as resolveExecutionStatusLabel,
  executionStatusType as resolveExecutionStatusType
} from '../execution-status'
import { hasManagementPermission as matchManagementPermission, TASK_PERMISSIONS } from '@/utils/bigscreen-access'
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
        { value: 'IDLE', label: '待执行' },
        { value: 'RUNNING', label: '执行中' }
      ],
      previewVisible: false,
      previewResult: null,
      startingPlanIds: [],
      executionParameterDialog: {
        visible: false,
        submitting: false,
        planId: '',
        planName: '',
        requirements: [],
        values: {},
        targetRequirements: [],
        targetValues: {}
      }
    }
  },
  computed: {
    ...mapGetters(['bigscreenPermissions', 'bigscreenAuthorizationBypassed']),
    permissionRenderKey() {
      return [
        (this.bigscreenPermissions || []).join('|'),
        this.bigscreenAuthorizationBypassed ? '1' : '0'
      ].join('#')
    },
    canViewPlan() {
      return this.hasManagementPermission(TASK_PERMISSIONS.PLAN_VIEW)
    },
    canCreatePlan() {
      return this.hasManagementPermission(TASK_PERMISSIONS.PLAN_CREATE)
    },
    canEditPlan() {
      return this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EDIT)
    },
    canExecutePlan() {
      return this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EXECUTE)
    },
    canDeletePlan() {
      return this.hasManagementPermission(TASK_PERMISSIONS.PLAN_DELETE)
    },
    canPauseExecution() {
      return this.hasManagementPermission(TASK_PERMISSIONS.EXECUTION_PAUSE)
    },
    canResumeExecution() {
      return this.hasManagementPermission(TASK_PERMISSIONS.EXECUTION_RESUME)
    },
    canTerminateExecution() {
      return this.hasManagementPermission(TASK_PERMISSIONS.EXECUTION_TERMINATE)
    },
    canForceTerminateExecution() {
      return this.hasManagementPermission(TASK_PERMISSIONS.EXECUTION_FORCE_TERMINATE)
    },
    previewMessages() {
      return this.previewResult && Array.isArray(this.previewResult.messages) ? this.previewResult.messages : []
    },
    previewRequirements() {
      return this.previewResult && Array.isArray(this.previewResult.componentSelectionRequirements)
        ? this.previewResult.componentSelectionRequirements
        : []
    },
    dialogMissingRequirements() {
      return (this.executionParameterDialog.requirements || []).filter(item => !item.configured)
    },
    dialogMissingTargetRequirements() {
      return (this.executionParameterDialog.targetRequirements || []).filter(item => !item.configured)
    }
  },
  mounted() {
    this.loadRows()
  },
  methods: {
    async loadRows(pageNum) {
      if (!this.canViewPlan) {
        this.rows = []
        this.page.total = 0
        this.loading = false
        this.$message.warning('无权限')
        return
      }
      if (pageNum) this.page.pageNum = pageNum
      this.loading = true
      try {
        const params = {
          pageNum: this.page.pageNum,
          pageSize: this.page.pageSize,
          keyword: this.filters.keyword || undefined,
          executionMode: this.filters.executionMode || undefined,
          // enabled: this.filters.enabled === '' ? undefined : this.filters.enabled,
          enabled: true,
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
    hasManagementPermission(permission) {
      return matchManagementPermission(
        permission,
        this.bigscreenPermissions,
        this.bigscreenAuthorizationBypassed
      )
    },
    openEditor(id, mode) {
      if (mode === 'view' && !this.hasManagementPermission(TASK_PERMISSIONS.PLAN_VIEW)) return
      if (mode === 'create' && !this.hasManagementPermission(TASK_PERMISSIONS.PLAN_CREATE)) return
      if (mode === 'edit' && !this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EDIT)) return
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
      if (!this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EXECUTE)) return
      if (this.isStarting(row.id)) return
      this.startingPlanIds = this.startingPlanIds.concat(row.id)
      try {
        const preview = this.unwrap(await startTaskPreview(row.id, {}))
        if (preview && preview.valid === false) {
          const missingParameters = (preview.actionParameterRequirements || []).filter(item => !item.configured)
          const missingTargets = (preview.targetRequirements || []).filter(item => !item.configured)
          if (missingParameters.length || missingTargets.length) {
            this.openExecutionParameterDialog(row, preview.actionParameterRequirements || [], preview.targetRequirements || [])
            return
          }
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
    openExecutionParameterDialog(plan, requirements, targets) {
      const values = {}
      ;(requirements || []).forEach(item => {
        values[this.parameterBindingKey(item)] = item.value
      })
      const targetValues = {}
      ;(targets || []).forEach(item => {
        targetValues[this.dialogTargetBindingKey(item)] = {
          x: item.x == null ? null : item.x,
          y: item.y == null ? null : item.y,
          yaw: item.yaw == null ? null : item.yaw
        }
      })
      this.executionParameterDialog = {
        visible: true,
        submitting: false,
        planId: plan.id,
        planName: plan.planName,
        requirements: requirements || [],
        values,
        targetRequirements: targets || [],
        targetValues
      }
    },
    async submitExecutionParameters() {
      if (!this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EXECUTE)) return
      const missing = this.dialogMissingRequirements.find(item => !this.hasParameterValue(this.dialogParameterValue(item)))
      if (missing) {
        this.$message.warning(`请填写${this.parameterDisplayLabel(missing)}`)
        return
      }
      const missingTarget = this.dialogMissingTargetRequirements.find(item => {
        const value = this.dialogTargetValue(item)
        return !this.hasParameterValue(value.x) || !this.hasParameterValue(value.y)
      })
      if (missingTarget) {
        this.$message.warning(`请填写${this.targetDisplayLabel(missingTarget)}的 X 坐标和 Y 坐标`)
        return
      }
      this.executionParameterDialog.submitting = true
      try {
        const data = this.unwrap(await startTask(this.executionParameterDialog.planId, {
          actionParameterValues: (this.executionParameterDialog.requirements || []).map(item => ({
            workflowVersionId: item.workflowVersionId,
            workflowNodeId: item.workflowNodeId,
            phase: item.phase,
            actionIndex: item.actionIndex,
            parameterName: item.parameterName,
            value: this.dialogParameterValue(item)
          })),
          targetBindings: (this.executionParameterDialog.targetRequirements || []).map(item => Object.assign({
            workflowVersionId: item.workflowVersionId,
            workflowNodeId: item.workflowNodeId
          }, this.dialogTargetValue(item)))
        }))
        if (data && data.accepted === false) {
          this.previewResult = data.preview || data
          this.previewVisible = true
          this.$message.warning(data.message || '任务未能启动')
          return
        }
        this.executionParameterDialog.visible = false
        this.$message.success((data && data.message) || '任务已启动')
        this.loadRows()
        if (data && data.workflowInstanceId) this.$emit('show-record', data.workflowInstanceId)
      } catch (error) {
        this.showError(error)
      } finally {
        this.executionParameterDialog.submitting = false
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
      if (!this.hasManagementPermission(TASK_PERMISSIONS.PLAN_DELETE)) return
      try {
        await this.$confirm(`确定删除计划“${row.planName || row.planCode || row.id}”？`, '提示', { type: 'warning' })
        await deleteTask(row.id)
        this.$message.success('已删除')
        this.loadRows(1)
      } catch (error) {
        if (error !== 'cancel') this.showError(error)
      }
    },
    async controlPlanInstance(row, action) {
      const permission = {
        PAUSE: TASK_PERMISSIONS.EXECUTION_PAUSE,
        RESUME: TASK_PERMISSIONS.EXECUTION_RESUME,
        TERMINATE: TASK_PERMISSIONS.EXECUTION_TERMINATE
      }[action]
      if (permission && !this.hasManagementPermission(permission)) return
      const label = { PAUSE: '暂停', RESUME: '恢复', TERMINATE: '终止' }[action] || '控制'
      const api = { PAUSE: pauseTaskRecord, RESUME: resumeTaskRecord, TERMINATE: terminateTaskRecord }[action]
      if (!api || !row.activeWorkflowInstanceId) return
      try {
        await this.$confirm(`确认${label}“${row.planName || row.planCode || row.id}”当前正在执行的任务吗？`, `${label}任务`, {
          type: action === 'TERMINATE' ? 'warning' : 'info'
        })
        const data = this.unwrap(await api(row.activeWorkflowInstanceId, {}))
        this.$message.success((data && data.message) || `${label}命令已发送`)
        this.loadRows()
      } catch (error) {
        if (error !== 'cancel') this.showError(error)
      }
    },
    async forceTerminatePlanInstance(row) {
      if (!this.hasManagementPermission(TASK_PERMISSIONS.EXECUTION_FORCE_TERMINATE)) return
      if (!row.activeWorkflowInstanceId) return
      try {
        const { value } = await this.$prompt(
          '平台将结束任务，但不能证明边缘设备已经停止；未确认设备会进入安全隔离。请输入强制结束原因。',
          '强制结束任务',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            inputPlaceholder: '请输入现场确认或处置说明',
            inputValidator: text => {
              const length = String(text || '').trim().length
              if (length > 200) return '原因不能超过200字'
              return length >= 1 || '请输入强制结束原因'
            }
          }
        )
        const data = this.unwrap(await forceTerminateTaskRecord(row.activeWorkflowInstanceId, String(value).trim()))
        this.$message.success((data && data.message) || '任务已强制结束，未确认设备已进入安全隔离')
        this.loadRows()
      } catch (error) {
        if (error !== 'cancel') this.showError(error)
      }
    },
    hasLifecycleAction(row, action) {
      const actions = row && Array.isArray(row.availableLifecycleActions) ? row.availableLifecycleActions : []
      return Boolean(row && row.activeWorkflowInstanceId && actions.indexOf(action) !== -1)
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
      return resolveExecutionStatusLabel(value, '待执行')
    },
    executionStatusType(value) {
      return resolveExecutionStatusType(value)
    },
    durationLabel(seconds) {
      if (!seconds) return '-'
      const minutes = Math.round(Number(seconds) / 60)
      if (!Number.isFinite(minutes) || minutes <= 0) return '-'
      if (minutes < 60) return `${minutes} 分钟`
      if (minutes % 60 === 0) return `${minutes / 60} 小时`
      return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
    },
    parameterBindingKey(parameter) {
      return [
        parameter.workflowVersionId,
        parameter.workflowNodeId,
        parameter.phase,
        parameter.actionIndex,
        parameter.parameterName
      ].join('|')
    },
    parameterSchemaType(parameter) {
      return parameter && parameter.schema && parameter.schema.type
    },
    parameterHasEnum(parameter) {
      return Boolean(parameter && parameter.schema && Array.isArray(parameter.schema.enum))
    },
    isNumberParameter(parameter) {
      return ['number', 'integer'].indexOf(this.parameterSchemaType(parameter)) !== -1
    },
    dialogParameterValue(parameter) {
      const key = this.parameterBindingKey(parameter)
      return this.executionParameterDialog.values[key] === undefined ? '' : this.executionParameterDialog.values[key]
    },
    dialogNumberParameterValue(parameter) {
      const value = this.dialogParameterValue(parameter)
      return value === '' || value == null ? undefined : Number(value)
    },
    setDialogParameterValue(parameter, value) {
      const values = Object.assign({}, this.executionParameterDialog.values)
      values[this.parameterBindingKey(parameter)] = value
      this.$set(this.executionParameterDialog, 'values', values)
    },
    parameterEnumLabel(parameter, option) {
      const enumValues = parameter && parameter.schema && Array.isArray(parameter.schema.enum) ? parameter.schema.enum : []
      const index = enumValues.indexOf(option)
      const names = parameter && parameter.schema && Array.isArray(parameter.schema.enumNames) ? parameter.schema.enumNames : []
      return names[index] || String(option)
    },
    parameterDisplayLabel(parameter) {
      return [parameter.workflowNodeName, parameter.actionName, parameter.label].filter(Boolean).join(' / ')
    },
    targetBindingKey(target) {
      return [target.workflowVersionId, target.workflowNodeId].join('|')
    },
    dialogTargetBindingKey(target) {
      return this.targetBindingKey(target)
    },
    targetDisplayLabel(target) {
      return [target.workflowName, target.workflowNodeName, '目标坐标'].filter(Boolean).join(' / ')
    },
    dialogTargetValue(target) {
      return this.executionParameterDialog.targetValues[this.dialogTargetBindingKey(target)] || {}
    },
    dialogTargetNumberValue(target, field) {
      const value = this.dialogTargetValue(target)[field]
      return value === '' || value == null ? undefined : Number(value)
    },
    setDialogTargetValue(target, field, value) {
      const targetValues = Object.assign({}, this.executionParameterDialog.targetValues)
      targetValues[this.dialogTargetBindingKey(target)] = Object.assign({}, this.dialogTargetValue(target), { [field]: value })
      this.$set(this.executionParameterDialog, 'targetValues', targetValues)
    },
    hasParameterValue(value) {
      return value != null && (!(typeof value === 'string') || value.trim() !== '')
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
      if (isRequestErrorNotified(error)) return
      this.$message.error((error && error.message) || '请求失败')
    }
  }
}
</script>

<style scoped lang="scss">
@import '../common.scss';
@import '../table.scss';
</style>
