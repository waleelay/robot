<template>
  <div>
    <div class="page-action-header">
      <el-button @click="$emit('close')">返回列表</el-button>
      <div class="page-action-header__title">
        {{ editorTitle }}
        <span>执行方式与设备绑定属于计划，任务流程结构仍由任务编排维护。</span>
      </div>
      <div class="page-action-header__actions">
        <el-button v-if="isViewMode" @click="switchEdit">编辑</el-button>
        <el-button v-else type="primary" :loading="saving" @click="savePlan">保存</el-button>
      </div>
    </div>

    <section v-loading="editorLoading" class="panel">
      <el-form :model="form" label-position="top" :disabled="isViewMode">
        <h3>基础信息</h3>
        <div class="form-grid">
          <el-form-item label="计划名称" required>
            <el-input v-model="form.planName" placeholder="例如：园区日常巡检计划" />
          </el-form-item>
          <el-form-item label="计划编码" required>
            <el-input v-model="form.planCode" :disabled="Boolean(form.id)" placeholder="例如：daily_patrol" />
          </el-form-item>
          <el-form-item label="任务编排版本" required>
            <el-select v-model="form.workflowVersionId" filterable placeholder="选择已发布编排版本" @change="handleVersionChange">
              <el-option
                v-for="item in definitionOptions"
                :key="item.latestPublishedVersionId"
                :label="definitionLabel(item)"
                :value="item.latestPublishedVersionId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="执行方式" required>
            <el-select v-model="form.executionMode">
              <el-option label="手动执行" value="MANUAL" />
              <el-option label="计划执行" value="SCHEDULE" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.executionMode === 'SCHEDULE'" label="计划周期" required>
            <el-select v-model="form.scheduleConfig.preset">
              <el-option v-for="item in schedulePresetOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="showTimeOfDay" label="执行时间" required>
            <el-time-picker v-model="form.scheduleConfig.timeOfDay" format="HH:mm" value-format="HH:mm" placeholder="选择时间" />
          </el-form-item>
          <el-form-item v-if="form.executionMode === 'SCHEDULE' && form.scheduleConfig.preset === 'WEEKLY'" label="星期" required>
            <el-select v-model="form.scheduleConfig.weekday">
              <el-option label="周一" value="MON" />
              <el-option label="周二" value="TUE" />
              <el-option label="周三" value="WED" />
              <el-option label="周四" value="THU" />
              <el-option label="周五" value="FRI" />
              <el-option label="周六" value="SAT" />
              <el-option label="周日" value="SUN" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.executionMode === 'SCHEDULE' && form.scheduleConfig.preset === 'CUSTOM'" label="Cron 表达式" required>
            <el-input v-model="form.scheduleConfig.cron" placeholder="例如：0 0 8 * * ?" />
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item label="备注" class="full">
            <el-input v-model="form.remark" type="textarea" :rows="3" />
          </el-form-item>
        </div>

        <template v-if="selectedVersion">
          <div class="section-heading">
            <div>
              <h3>发布模板</h3>
              <p>计划锁定该不可变版本，编排后续重新发布不会影响本计划。</p>
            </div>
            <el-tag effect="plain">v{{ selectedVersion.versionNo || selectedVersion.latestPublishedVersion || '-' }}</el-tag>
          </div>
          <div class="template-summary">
            <div><span>根流程</span><strong>{{ selectedVersion.workflowName || '-' }}</strong></div>
            <div><span>流程节点</span><strong>{{ workflowNodeCount }}</strong></div>
            <div><span>子流程依赖</span><strong>{{ dependencyCount }}</strong></div>
            <div><span>设备角色</span><strong>{{ roleBindings.length }}</strong></div>
            <div><span>组件绑定</span><strong>{{ form.componentBindings.length }}</strong></div>
          </div>
          <el-table v-if="dependencyCount" :data="selectedVersion.dependencies" size="small" class="mt10">
            <el-table-column prop="workflowName" label="子流程" min-width="180" show-overflow-tooltip />
            <el-table-column prop="workflowCode" label="编码" min-width="160" show-overflow-tooltip />
            <el-table-column label="冻结版本" width="100">
              <template slot-scope="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
          </el-table>
        </template>

        <div class="section-heading">
          <div>
            <h3>设备角色绑定</h3>
            <p>角色来自任务编排；每个角色选择一台实际设备执行。</p>
          </div>
        </div>
        <el-empty v-if="!roleBindings.length" description="选择任务编排后配置设备角色" />
        <div v-else class="role-grid">
          <section v-for="role in roleBindings" :key="role.roleKey" class="role-card">
            <div class="role-card__header">
              <div>
                <strong>{{ role.roleName }}</strong>
                <div class="muted">{{ role.roleKey }} · {{ roleTypeLabel(role.roleType) }}</div>
              </div>
              <span class="status info">{{ requirementSummary(role) }}</span>
            </div>
            <el-form-item label="执行设备" required>
              <el-select v-model="role.deviceId" filterable clearable placeholder="选择一台实际设备">
                <el-option v-for="item in deviceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </section>
        </div>

        <div class="section-heading">
          <div>
            <h3>执行组件解析</h3>
            <p>系统自动选择唯一匹配组件；只有同一动作存在多个可用组件时才需要指定。</p>
          </div>
          <el-button :disabled="!form.workflowVersionId" @click="resolveComponents">检查组件来源</el-button>
        </div>
        <el-alert
          v-if="componentResolutionChecked && !componentRequirements.length"
          type="success"
          title="动作执行组件均可自动确定"
          :closable="false"
          show-icon
        />
        <el-table v-if="componentRequirements.length" :data="componentRequirements" size="small">
          <el-table-column label="执行设备" min-width="160" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ row.deviceName || row.serialNumber || row.deviceId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="workflowNodeKey" label="流程节点" min-width="140" />
          <el-table-column label="动作" min-width="180">
            <template slot-scope="{ row }">{{ actionLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="执行组件" min-width="190">
            <template slot-scope="{ row }">
              <el-select :value="componentBindingValue(row)" placeholder="选择组件" @change="applyComponentBinding(row, $event)">
                <el-option
                  v-for="component in componentOptions(row)"
                  :key="component.componentCode"
                  :label="component.componentName || component.componentCode"
                  :value="component.componentCode"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </section>
  </div>
</template>

<script>
import {
  createTask,
  getManagementDevices,
  getTaskDetail,
  getTaskWorkflowDefinitions,
  getTaskWorkflowVersionDetail,
  previewTaskConfiguration,
  updateTask
} from '@/api/new-bi'

export default {
  name: 'BiPatrolBusiness2PlanEdit',
  props: {
    id: {
      type: [String, Number],
      default: ''
    },
    mode: {
      type: String,
      default: 'view'
    }
  },
  data() {
    return {
      editorLoading: false,
      saving: false,
      form: this.defaultForm(),
      definitionOptions: [],
      deviceOptions: [],
      selectedVersion: null,
      roleBindings: [],
      componentRequirements: [],
      componentResolutionChecked: false,
      schedulePresetOptions: [
        { label: '每小时', value: 'HOURLY' },
        { label: '每天', value: 'DAILY' },
        { label: '工作日', value: 'WORKDAY' },
        { label: '每周', value: 'WEEKLY' },
        { label: '自定义', value: 'CUSTOM' }
      ]
    }
  },
  computed: {
    isViewMode() {
      return this.mode === 'view'
    },
    editorTitle() {
      if (!this.id) return '新建任务计划'
      return this.isViewMode ? '任务计划详情' : '编辑任务计划'
    },
    showTimeOfDay() {
      return this.form.executionMode === 'SCHEDULE' && ['DAILY', 'WORKDAY', 'WEEKLY'].indexOf(this.form.scheduleConfig.preset) !== -1
    },
    workflowDocument() {
      return this.parseDefinition(this.selectedVersion && this.selectedVersion.definitionJson)
    },
    workflowNodeCount() {
      return Array.isArray(this.workflowDocument.nodes) ? this.workflowDocument.nodes.length : 0
    },
    dependencyCount() {
      return this.selectedVersion && Array.isArray(this.selectedVersion.dependencies) ? this.selectedVersion.dependencies.length : 0
    }
  },
  watch: {
    id: 'loadPage'
  },
  mounted() {
    this.loadPage()
  },
  methods: {
    async loadPage() {
      await this.loadEditorOptions()
      await this.loadEditor()
    },
    async loadEditorOptions() {
      try {
        const defs = this.unwrap(await getTaskWorkflowDefinitions({ pageNum: 1, pageSize: 500, status: 'PUBLISHED', enabled: true }))
        const devices = this.unwrap(await getManagementDevices({ pageNum: 1, pageSize: 500, enabled: true }))
        this.definitionOptions = (defs.records || []).filter(item => {
          return item.workflowKind === 'MAIN' && item.definitionStatus === 'PUBLISHED' && item.latestPublishedVersionId
        })
        this.deviceOptions = (devices.records || []).map(item => ({
          value: item.id,
          label: `${item.deviceName || item.deviceCode || item.id} / ${item.serialNumber || '-'}`
        }))
      } catch (error) {
        this.showError(error)
      }
    },
    async loadEditor() {
      this.form = this.defaultForm()
      this.selectedVersion = null
      this.roleBindings = []
      this.componentRequirements = []
      this.componentResolutionChecked = false
      if (!this.id) return
      this.editorLoading = true
      try {
        const detail = this.unwrap(await getTaskDetail(this.id))
        this.form = Object.assign(this.defaultForm(), {
          id: detail.id,
          planCode: detail.planCode,
          planName: detail.planName,
          workflowVersionId: detail.workflowVersionId,
          componentBindings: detail.componentBindings || [],
          executionMode: detail.executionMode || 'MANUAL',
          scheduleConfig: this.normalizeScheduleConfig(detail.scheduleConfig),
          eventTriggerConfig: Object.assign({ eventType: 'ALARM', eventSubtype: '' }, detail.eventTriggerConfig || {}),
          offlinePolicy: detail.offlinePolicy || 'CONTINUE',
          enabled: detail.enabled !== false,
          remark: detail.remark || ''
        })
        this.selectedVersion = {
          id: detail.workflowVersionId,
          workflowDefinitionId: detail.workflowDefinitionId,
          workflowCode: detail.workflowCode,
          workflowName: detail.workflowName,
          versionNo: detail.workflowVersion,
          definitionJson: detail.workflowDefinitionJson,
          dependencies: detail.dependencies || []
        }
        if (!this.selectedVersion.definitionJson && detail.workflowDefinitionId && detail.workflowVersionId) {
          const versionDetail = this.unwrap(await getTaskWorkflowVersionDetail(detail.workflowDefinitionId, detail.workflowVersionId))
          this.selectedVersion = Object.assign({}, versionDetail, this.selectedVersion, {
            definitionJson: versionDetail.definitionJson,
            dependencies: versionDetail.dependencies || detail.dependencies || []
          })
        }
        this.roleBindings = await this.buildRoleBindings(this.selectedVersion, detail.roleBindings || [])
      } catch (error) {
        this.showError(error)
      } finally {
        this.editorLoading = false
      }
    },
    async handleVersionChange(versionId, savedBindings) {
      if (savedBindings === undefined) {
        this.form.componentBindings = []
        this.componentRequirements = []
        this.componentResolutionChecked = false
      }
      const definition = this.definitionOptions.find(item => item.latestPublishedVersionId === versionId)
      this.selectedVersion = definition ? this.unwrap(await getTaskWorkflowVersionDetail(definition.id, versionId)) : null
      this.roleBindings = await this.buildRoleBindings(this.selectedVersion, savedBindings)
    },
    async buildRoleBindings(version, savedBindings) {
      if (!version) return []
      const documents = [this.parseDefinition(version.definitionJson)]
      const dependencies = version.dependencies || []
      for (const dependency of dependencies) {
        try {
          const detail = this.unwrap(await getTaskWorkflowVersionDetail(dependency.workflowDefinitionId, dependency.workflowVersionId))
          documents.push(this.parseDefinition(detail.definitionJson))
        } catch (error) {
          this.showError(error)
        }
      }
      const rolesByKey = {}
      documents.forEach(document => {
        const roles = Array.isArray(document.deviceRoles) ? document.deviceRoles : []
        roles.forEach(role => {
          if (role.roleKey && !rolesByKey[role.roleKey]) rolesByKey[role.roleKey] = role
        })
      })
      const savedByRole = {}
      ;(savedBindings || []).forEach(item => {
        savedByRole[item.roleKey] = item
      })
      return Object.keys(rolesByKey).map(key => this.normalizeBinding(rolesByKey[key], savedByRole[key]))
    },
    async savePlan() {
      const message = this.validateForm()
      if (message) {
        this.$message.warning(message)
        return
      }
      this.saving = true
      try {
        const payload = this.buildPayload()
        const res = this.form.id ? await updateTask(this.form.id, payload) : await createTask(payload)
        const data = this.unwrap(res)
        const id = data.id || data || this.form.id
        this.$message.success('已保存')
        this.$emit('saved', id)
      } catch (error) {
        this.showError(error)
      } finally {
        this.saving = false
      }
    },
    async resolveComponents() {
      const message = this.validateForm()
      if (message) {
        this.$message.warning(message)
        return
      }
      try {
        const result = this.unwrap(await previewTaskConfiguration(this.buildPayload()))
        this.componentRequirements = result.componentSelectionRequirements || []
        this.componentResolutionChecked = true
        if (!this.componentRequirements.length) this.$message.success('动作执行组件均可自动确定')
      } catch (error) {
        this.showError(error)
      }
    },
    switchEdit() {
      this.$emit('saved', this.id)
    },
    validateForm() {
      if (!this.form.planName) return '请输入计划名称'
      if (!this.form.planCode) return '请输入计划编码'
      if (!this.form.workflowVersionId) return '请选择任务编排版本'
      if (!this.roleBindings.length) return '任务编排缺少设备角色'
      const missing = this.roleBindings.find(role => !role.deviceId)
      if (missing) return `请为 ${missing.roleName || missing.roleKey} 选择执行设备`
      if (this.form.executionMode === 'SCHEDULE' && this.form.scheduleConfig.preset === 'CUSTOM' && !this.form.scheduleConfig.cron) {
        return '请输入 Cron 表达式'
      }
      return ''
    },
    buildPayload() {
      return {
        id: this.form.id,
        planCode: this.form.planCode,
        planName: this.form.planName,
        workflowVersionId: this.form.workflowVersionId,
        executionMode: this.form.executionMode,
        scheduleConfig: this.form.executionMode === 'SCHEDULE' ? this.normalizedScheduleConfig() : {},
        eventTriggerConfig: {},
        roleBindings: this.roleBindings.map(role => ({
          roleKey: role.roleKey,
          dispatchMode: 'MANUAL_SELECTION',
          allocationMode: 'SINGLE',
          deviceIds: role.deviceId ? [role.deviceId] : [],
          groupIds: [],
          regionIds: [],
          busyPolicy: 'SKIP_BUSY',
          recoveryPolicy: 'FAIL_NODE'
        })),
        componentBindings: this.form.componentBindings || [],
        offlinePolicy: this.form.offlinePolicy,
        enabled: this.form.enabled,
        remark: this.form.remark
      }
    },
    normalizedScheduleConfig() {
      const config = this.normalizeScheduleConfig(this.form.scheduleConfig)
      return {
        preset: config.preset,
        cron: this.cronFromPreset(config),
        timezone: config.timezone || 'Asia/Shanghai',
        timeOfDay: config.timeOfDay,
        weekday: config.weekday
      }
    },
    cronFromPreset(config) {
      if (config.preset === 'CUSTOM') return config.cron || ''
      if (config.preset === 'HOURLY') return '0 0 * * * ?'
      const parts = String(config.timeOfDay || '08:00').split(':')
      const hour = Number(parts[0] || 8)
      const minute = Number(parts[1] || 0)
      if (config.preset === 'WORKDAY') return `0 ${minute} ${hour} ? * MON-FRI`
      if (config.preset === 'WEEKLY') return `0 ${minute} ${hour} ? * ${config.weekday || 'MON'}`
      return `0 ${minute} ${hour} * * ?`
    },
    componentBindingValue(row) {
      const binding = (this.form.componentBindings || []).find(item => this.sameComponentRequirement(item, row))
      return binding ? binding.componentCode : ''
    },
    applyComponentBinding(row, componentCode) {
      const next = (this.form.componentBindings || []).filter(item => !this.sameComponentRequirement(item, row))
      if (componentCode) {
        next.push({
          roleKey: row.roleKey,
          deviceId: row.deviceId,
          capabilityCode: row.capabilityCode,
          actionCode: row.actionCode,
          componentCode
        })
      }
      this.form.componentBindings = next
    },
    sameComponentRequirement(left, right) {
      return String(left.roleKey || '') === String(right.roleKey || '') &&
        String(left.deviceId || '') === String(right.deviceId || '') &&
        String(left.capabilityCode || '') === String(right.capabilityCode || '') &&
        String(left.actionCode || '') === String(right.actionCode || '')
    },
    componentOptions(row) {
      return row.candidates || row.components || []
    },
    normalizeBinding(role, saved) {
      const savedDeviceIds = Array.isArray(saved && saved.deviceIds) ? saved.deviceIds : []
      return {
        roleKey: role.roleKey,
        roleName: role.roleName || role.roleKey,
        roleType: role.roleType || 'EXECUTOR',
        requiredCapabilityCodes: role.requiredCapabilityCodes || [],
        requiredActionCodes: role.requiredActionCodes || [],
        deviceId: savedDeviceIds[0] || (saved && saved.deviceId) || ''
      }
    },
    normalizeScheduleConfig(config) {
      const next = Object.assign(this.defaultForm().scheduleConfig, config || {})
      if (!next.cron) next.cron = this.cronFromPreset(next)
      return next
    },
    defaultForm() {
      return {
        id: null,
        planCode: '',
        planName: '',
        workflowVersionId: null,
        componentBindings: [],
        offlinePolicy: 'CONTINUE',
        executionMode: 'MANUAL',
        scheduleConfig: { preset: 'HOURLY', cron: '0 0 * * * ?', timezone: 'Asia/Shanghai', timeOfDay: '08:00', weekday: 'MON' },
        eventTriggerConfig: { eventType: 'ALARM', eventSubtype: '' },
        enabled: true,
        remark: ''
      }
    },
    parseDefinition(value) {
      if (!value) return {}
      if (typeof value === 'object') return value
      try {
        return JSON.parse(value)
      } catch (error) {
        return {}
      }
    },
    definitionLabel(item) {
      return `${item.workflowName} / ${item.workflowCode} · v${item.latestPublishedVersion}`
    },
    roleTypeLabel(value) {
      return { ROBOT: '机器人', CAMERA: '摄像头', SENSOR: '传感器', OTHER: '其他' }[value] || value || '设备'
    },
    requirementSummary(role) {
      const caps = Array.isArray(role.requiredCapabilityCodes) ? role.requiredCapabilityCodes : []
      return caps.length ? `${caps.length} 项能力` : '无能力约束'
    },
    actionLabel(row) {
      return `${row.capabilityName || row.capabilityCode || '-'} / ${row.actionName || row.actionCode || '-'}`
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
</style>
