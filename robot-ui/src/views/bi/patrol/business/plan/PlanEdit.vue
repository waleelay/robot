<template>
  <div>
    <div class="page-action-header table-btns">
      <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="$emit('close')">返回列表</el-button>
      <div class="page-action-header__title">
        {{ editorTitle }}
        <span>执行方式与设备绑定属于计划，任务流程结构仍由任务编排维护。</span>
      </div>
      <div class="page-action-header__actions">
        <el-button
          v-if="isViewMode && canEditPlan"
          class="pr20 pl20"
          plain
          style="color: #17D1FF"
          @click="switchEdit"
        >
          编辑
        </el-button>
        <el-button
          v-else-if="!isViewMode && canSavePlan"
          type="primary"
          class="pr20 pl20"
          plain
          style="color: #17D1FF"
          :loading="saving"
          @click="savePlan"
        >
          保存
        </el-button>
      </div>
    </div>

    <section v-loading="editorLoading" class="panel">
      <el-form :model="form" label-position="top" :disabled="isViewMode">
        <h3>基础信息</h3>
        <div class="form-grid">
          <el-form-item label="计划名称" required>
            <el-input v-model="form.planName" placeholder="例如：园区日常巡检计划" />
          </el-form-item>
          <el-form-item label="任务编排版本" required>
            <el-select v-if="!form.id" v-model="form.workflowVersionId" filterable placeholder="选择已发布编排版本" @change="handleVersionChange">
              <el-option
                v-for="item in definitionOptions"
                :key="item.latestPublishedVersionId"
                :label="definitionLabel(item)"
                :value="item.latestPublishedVersionId"
              />
            </el-select>
            <div v-else class="locked-version">
              <span>{{ lockedVersionLabel }}</span>
              <span class="status info">已锁定</span>
              <el-button
                v-if="!isViewMode && latestVersionOption"
                type="text"
                @click="upgradeToLatestVersion"
              >
                改用最新版本 v{{ latestVersionOption.latestPublishedVersion }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="执行方式" required>
            <el-select v-model="form.executionMode">
              <el-option label="手动执行" value="MANUAL" />
              <el-option label="计划执行" value="SCHEDULE" />
            </el-select>
          </el-form-item>
          <el-form-item label="预计执行时长（分钟）" required>
            <el-input-number v-model="form.expectedDurationMinutes" :min="1" :max="525600" :step="5" style="width: 100%" />
            <div class="field-tip">到达预计时长仍未收到边缘端执行结果时，任务将按超时失败处理</div>
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

        <section v-if="executionParameterGroups.length || targetRequirementGroups.length" class="execution-parameter-section">
          <div class="section-heading">
            <div>
              <h3>执行参数</h3>
              <p>这些参数由任务编排交给计划填写，已配置的专业参数不会在这里重复展示。</p>
            </div>
          </div>
          <div class="execution-parameter-groups">
            <section v-for="group in executionParameterGroups" :key="group.key" class="execution-parameter-card">
              <strong>{{ group.title }}</strong>
              <div class="form-grid">
                <el-form-item v-for="parameter in group.parameters" :key="parameterBindingKey(parameter)" :label="parameter.label" required>
                  <el-select
                    v-if="parameterHasEnum(parameter)"
                    :value="parameterValue(parameter)"
                    clearable
                    :placeholder="parameter.description || '选择' + parameter.label"
                    @change="setParameterValue(parameter, $event)"
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
                    :value="parameterValue(parameter) === true"
                    @change="setParameterValue(parameter, $event)"
                  />
                  <el-input-number
                    v-else-if="isNumberParameter(parameter)"
                    :value="numberParameterValue(parameter)"
                    :min="parameter.schema && parameter.schema.minimum"
                    :max="parameter.schema && parameter.schema.maximum"
                    :step="(parameter.schema && parameter.schema.multipleOf) || 1"
                    controls-position="right"
                    @change="setParameterValue(parameter, $event)"
                  />
                  <el-input
                    v-else
                    :value="parameterValue(parameter)"
                    :type="parameter.schema && parameter.schema.format === 'textarea' ? 'textarea' : 'text'"
                    :rows="parameter.schema && parameter.schema.format === 'textarea' ? 3 : undefined"
                    :placeholder="parameter.description || '填写' + parameter.label"
                    @input="setParameterValue(parameter, $event)"
                  />
                  <div v-if="parameter.description" class="parameter-description">{{ parameter.description }}</div>
                </el-form-item>
              </div>
            </section>
            <section v-for="target in targetRequirementGroups" :key="targetBindingKey(target)" class="execution-parameter-card">
              <strong>{{ targetDisplayLabel(target) }}</strong>
              <div class="form-grid">
                <el-form-item label="X 坐标" required>
                  <el-input-number
                    :value="targetNumberValue(target, 'x')"
                    controls-position="right"
                    @change="setTargetValue(target, 'x', $event)"
                  />
                </el-form-item>
                <el-form-item label="Y 坐标" required>
                  <el-input-number
                    :value="targetNumberValue(target, 'y')"
                    controls-position="right"
                    @change="setTargetValue(target, 'y', $event)"
                  />
                </el-form-item>
                <el-form-item label="朝向">
                  <el-input-number
                    :value="targetNumberValue(target, 'yaw')"
                    controls-position="right"
                    @change="setTargetValue(target, 'yaw', $event)"
                  />
                </el-form-item>
              </div>
            </section>
          </div>
        </section>

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
                <el-option
                  v-for="item in deviceOptionsForRole(role)"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </section>
        </div>

        <div class="section-heading table-btns">
          <div>
            <h3>执行组件解析</h3>
            <p>系统自动选择唯一匹配组件；只有同一动作存在多个可用组件时才需要指定。</p>
          </div>
          <el-button type="primary" plain style="color: #17D1FF" :disabled="!form.workflowVersionId" @click="resolveComponents">检查组件来源</el-button>
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
          <el-table-column label="流程节点" min-width="160" show-overflow-tooltip>
            <template slot-scope="{ row }">
              <el-tooltip :content="row.workflowNodeKey || '-'" placement="top">
                <span>{{ workflowNodeLabel(row) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="动作" min-width="180">
            <template slot-scope="{ row }">{{ actionLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="执行组件" min-width="190">
            <template slot-scope="{ row }">
              <el-select
                :value="componentBindingValue(row)"
                multiple
                collapse-tags
                clearable
                filterable
                placeholder="不选择则全部执行"
                @change="applyComponentBinding(row, $event)"
              >
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
import { mapGetters } from 'vuex'
import {
  createTask,
  getSelectionOptionDevices,
  getSceneResourceGrants,
  getSelectionWorkflowDefinition,
  getTaskDetail,
  getTaskWorkflowDefinitions,
  getTaskWorkflowVersionDetail,
  previewTaskConfiguration,
  updateTask
} from '@/api/new-bi'
import { hasManagementPermission as matchManagementPermission, TASK_PERMISSIONS } from '@/utils/bigscreen-access'
import { isRequestErrorNotified } from '@/utils/request'

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
      sceneDeviceScopes: {},
      selectedVersion: null,
      roleBindings: [],
      actionParameterRequirements: [],
      targetRequirements: [],
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
    ...mapGetters(['bigscreenPermissions', 'bigscreenAuthorizationBypassed']),
    isViewMode() {
      return this.mode === 'view'
    },
    editorTitle() {
      if (!this.id) return '新建任务计划'
      return this.isViewMode ? '任务计划详情' : '编辑任务计划'
    },
    savePermission() {
      return this.form.id ? TASK_PERMISSIONS.PLAN_EDIT : TASK_PERMISSIONS.PLAN_CREATE
    },
    canEditPlan() {
      return this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EDIT)
    },
    canSavePlan() {
      return this.hasManagementPermission(this.savePermission)
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
    },
    executionParameterGroups() {
      return this.groupActionParameterRequirements(this.actionParameterRequirements)
    },
    targetRequirementGroups() {
      return this.targetRequirements
    },
    latestVersionOption() {
      const version = this.selectedVersion
      if (!this.form.id || !version || !version.workflowDefinitionId) return null
      return this.definitionOptions.find(item => {
        return String(item.id) === String(version.workflowDefinitionId) &&
          Number(item.latestPublishedVersion) > Number(version.versionNo)
      }) || null
    },
    lockedVersionLabel() {
      const version = this.selectedVersion
      if (!version) return '未选择发布版本'
      return `${version.workflowName || '任务编排'} · v${version.versionNo || '-'}`
    }
  },
  watch: {
    id: 'loadPage'
  },
  mounted() {
    this.loadPage()
  },
  methods: {
    hasManagementPermission(permission) {
      return matchManagementPermission(
        permission,
        this.bigscreenPermissions,
        this.bigscreenAuthorizationBypassed
      )
    },
    async loadPage() {
      await this.loadEditorOptions()
      await this.loadEditor()
    },
    async loadEditorOptions() {
      try {
        const defs = this.unwrap(await getTaskWorkflowDefinitions({ pageNum: 1, pageSize: 500, status: 'PUBLISHED', enabled: true }))
        const devices = this.unwrapList(await getSelectionOptionDevices())
        this.definitionOptions = (defs.records || []).filter(item => {
          return item.workflowKind === 'MAIN' && item.definitionStatus === 'PUBLISHED' && item.latestPublishedVersionId
        })
        this.deviceOptions = devices.map(item => {
          const attributes = item.attributes || {}
          const value = item.value || item.id
          return {
            value,
            label: item.label || `${item.deviceName || item.deviceCode || value} / ${item.serialNumber || attributes.serialNumber || '-'}`,
            raw: Object.assign({ id: value }, item, attributes)
          }
        })
      } catch (error) {
        this.showError(error)
      }
    },
    async loadEditor() {
      this.form = this.defaultForm()
      this.selectedVersion = null
      this.roleBindings = []
      this.actionParameterRequirements = []
      this.targetRequirements = []
      this.componentRequirements = []
      this.componentResolutionChecked = false
      this.sceneDeviceScopes = {}
      if (!this.id) return
      this.editorLoading = true
      try {
        const detail = this.unwrap(await getTaskDetail(this.id))
        this.form = Object.assign(this.defaultForm(), {
          id: detail.id,
          planName: detail.planName,
          workflowVersionId: detail.workflowVersionId,
          componentBindings: this.normalizeComponentBindings(detail.componentBindings || []),
          actionParameterBindings: detail.actionParameterBindings || [],
          targetBindings: detail.targetBindings || [],
          executionMode: detail.executionMode || 'MANUAL',
          expectedDurationMinutes: Math.round((detail.expectedDurationSeconds || 3600) / 60),
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
        await this.loadActionParameterRequirements(this.selectedVersion)
      } catch (error) {
        this.showError(error)
      } finally {
        this.editorLoading = false
      }
    },
    async handleVersionChange(versionId, savedBindings) {
      this.form.workflowVersionId = versionId
      if (savedBindings === undefined) {
        this.form.componentBindings = []
        this.form.actionParameterBindings = []
        this.form.targetBindings = []
        this.componentRequirements = []
        this.componentResolutionChecked = false
      }
      const definition = this.definitionOptions.find(item => item.latestPublishedVersionId === versionId)
      this.selectedVersion = definition ? this.unwrap(await getTaskWorkflowVersionDetail(definition.id, versionId)) : null
      this.roleBindings = await this.buildRoleBindings(this.selectedVersion, savedBindings)
      await this.loadActionParameterRequirements(this.selectedVersion)
    },
    async upgradeToLatestVersion() {
      const option = this.latestVersionOption
      if (!option) return
      try {
        await this.$confirm(
          `改用“${option.workflowName} · v${option.latestPublishedVersion}”后，设备角色、组件选择和执行参数会清空，需要重新配置。确定继续吗？`,
          '改用最新版本',
          { confirmButtonText: '改用', cancelButtonText: '取消', type: 'warning' }
        )
        this.roleBindings = []
        await this.handleVersionChange(option.latestPublishedVersionId)
        this.$message.success('已切换到最新版本，请重新配置计划')
      } catch (error) {
        if (error !== 'cancel') this.showError(error)
      }
    },
    async loadActionParameterRequirements(version) {
      if (!version) {
        this.actionParameterRequirements = []
        this.targetRequirements = []
        this.form.actionParameterBindings = []
        this.form.targetBindings = []
        return
      }
      const documents = [version]
      const dependencies = version.dependencies || []
      for (const dependency of dependencies) {
        try {
          documents.push(this.unwrap(await getTaskWorkflowVersionDetail(dependency.workflowDefinitionId, dependency.workflowVersionId)))
        } catch (error) {
          this.showError(error)
        }
      }
      const requirements = documents.reduce((result, workflowVersion) => {
        return result.concat(this.collectActionParameterRequirements(workflowVersion))
      }, [])
      const targets = documents.reduce((result, workflowVersion) => {
        return result.concat(this.collectTargetRequirements(workflowVersion))
      }, [])
      const requirementKeys = requirements.map(item => this.parameterBindingKey(item))
      const targetKeys = targets.map(item => this.targetBindingKey(item))
      this.actionParameterRequirements = requirements
      this.targetRequirements = targets
      this.form.actionParameterBindings = (this.form.actionParameterBindings || [])
        .filter(binding => requirementKeys.indexOf(this.parameterBindingKey(binding)) !== -1)
      this.form.targetBindings = (this.form.targetBindings || [])
        .filter(binding => targetKeys.indexOf(this.targetBindingKey(binding)) !== -1)
    },
    async buildRoleBindings(version, savedBindings) {
      if (!version) {
        this.sceneDeviceScopes = {}
        return []
      }
      const pairs = []
      try {
        pairs.push([
          version,
          this.unwrap(await getSelectionWorkflowDefinition(version.workflowDefinitionId))
        ])
      } catch (error) {
        this.showError(error)
        pairs.push([version, {}])
      }
      const dependencies = version.dependencies || []
      for (const dependency of dependencies) {
        try {
          const workflowVersion = this.unwrap(await getTaskWorkflowVersionDetail(
            dependency.workflowDefinitionId,
            dependency.workflowVersionId
          ))
          const definition = this.unwrap(await getSelectionWorkflowDefinition(dependency.workflowDefinitionId))
          pairs.push([workflowVersion, definition])
        } catch (error) {
          this.showError(error)
        }
      }
      const rolesByKey = {}
      pairs.forEach(pair => {
        const workflowVersion = pair[0]
        const definition = pair[1] || {}
        const document = this.parseDefinition(workflowVersion && workflowVersion.definitionJson)
        const nodes = Array.isArray(document.nodes) ? document.nodes : []
        const usedRoleKeys = {}
        nodes.forEach(node => {
          if (['DEVICE_TASK', 'ACTION', 'CONTROL'].indexOf(node.type) === -1) return
          const roleKey = node.config && node.config.roleKey
          if (roleKey) usedRoleKeys[roleKey] = true
        })
        const roles = Array.isArray(document.deviceRoles) ? document.deviceRoles : []
        roles.forEach(role => {
          if (!role.roleKey || !usedRoleKeys[role.roleKey]) return
          const existing = rolesByKey[role.roleKey] || Object.assign({}, role, {
            requiredCapabilityCodes: [],
            requiredActionCodes: [],
            sceneIds: []
          })
          existing.requiredCapabilityCodes = this.uniqueStrings(
            (existing.requiredCapabilityCodes || []).concat(role.requiredCapabilityCodes || [])
          )
          existing.requiredActionCodes = this.uniqueStrings(
            (existing.requiredActionCodes || []).concat(role.requiredActionCodes || [])
          )
          const sceneId = definition.sceneId != null
            ? definition.sceneId
            : (definition.attributes && definition.attributes.sceneId)
          if (sceneId != null && existing.sceneIds.indexOf(sceneId) === -1) {
            existing.sceneIds.push(sceneId)
          }
          rolesByKey[role.roleKey] = existing
        })
      })
      const sceneIds = []
      Object.keys(rolesByKey).forEach(key => {
        (rolesByKey[key].sceneIds || []).forEach(sceneId => {
          if (sceneIds.indexOf(sceneId) === -1) sceneIds.push(sceneId)
        })
      })
      await this.loadSceneDeviceScopes(sceneIds)
      const savedByRole = {}
      ;(savedBindings || []).forEach(item => {
        savedByRole[item.roleKey] = item
      })
      return Object.keys(rolesByKey).map(key => this.normalizeBinding(rolesByKey[key], savedByRole[key]))
    },
    async loadSceneDeviceScopes(sceneIds) {
      const next = {}
      await Promise.all((sceneIds || []).map(async sceneId => {
        try {
          const grants = this.unwrapList(await getSceneResourceGrants(sceneId))
          next[String(sceneId)] = {
            deviceIds: grants
              .filter(item => item.resourceType === 'DEVICE')
              .map(item => String(item.resourceId)),
            deviceGroupIds: grants
              .filter(item => item.resourceType === 'DEVICE_GROUP')
              .map(item => String(item.resourceId))
          }
        } catch (error) {
          this.showError(error)
          next[String(sceneId)] = { deviceIds: [], deviceGroupIds: [] }
        }
      }))
      this.sceneDeviceScopes = next
    },
    deviceOptionsForRole(role) {
      const sceneIds = (role && role.sceneIds) || []
      if (!sceneIds.length) return []
      return this.deviceOptions.filter(item => {
        return sceneIds.every(sceneId => {
          const scope = this.sceneDeviceScopes[String(sceneId)]
          if (!scope) return false
          const deviceId = String(item.value)
          const groupId = item.raw && item.raw.groupId
          return scope.deviceIds.indexOf(deviceId) !== -1
            || (groupId != null && scope.deviceGroupIds.indexOf(String(groupId)) !== -1)
        })
      })
    },
    async savePlan() {
      if (!this.hasManagementPermission(this.savePermission)) return
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
      if (!this.hasManagementPermission(TASK_PERMISSIONS.PLAN_EDIT)) return
      this.$emit('saved', this.id)
    },
    validateForm() {
      if (!this.form.planName) return '请输入计划名称'
      if (!this.form.workflowVersionId) return '请选择任务编排版本'
      if (!this.form.expectedDurationMinutes || this.form.expectedDurationMinutes < 1 || this.form.expectedDurationMinutes > 525600) {
        return '请填写预计执行时长（1～525600 分钟）'
      }
      if (!this.roleBindings.length) return '任务编排缺少设备角色'
      if (this.form.executionMode === 'SCHEDULE') {
        if (!this.form.scheduleConfig.preset) return '请选择计划周期'
        if (this.showTimeOfDay && !this.form.scheduleConfig.timeOfDay) return '请选择执行时间'
        if (this.form.scheduleConfig.preset === 'WEEKLY' && !this.form.scheduleConfig.weekday) return '请选择星期'
        if (this.form.scheduleConfig.preset === 'CUSTOM' && !this.form.scheduleConfig.cron) return '请输入 Cron 表达式'
        const missingParameter = this.actionParameterRequirements.find(item => !this.hasParameterValue(this.parameterValue(item)))
        if (missingParameter) return `计划执行需要填写${this.parameterDisplayLabel(missingParameter)}`
        const missingTarget = this.targetRequirements.find(item => {
          const value = this.targetValue(item)
          return !this.hasParameterValue(value.x) || !this.hasParameterValue(value.y)
        })
        if (missingTarget) return `计划执行需要填写${this.targetDisplayLabel(missingTarget)}的 X 坐标和 Y 坐标`
      }
      const missing = this.roleBindings.find(role => !role.deviceId)
      if (missing) return `请为 ${missing.roleName || missing.roleKey} 选择执行设备`
      const outOfScopeBinding = this.roleBindings.find(role => {
        return !this.deviceOptionsForRole(role).some(device => String(device.value) === String(role.deviceId))
      })
      if (outOfScopeBinding) return `${outOfScopeBinding.roleName} 的设备不在当前场景资源范围内`
      return ''
    },
    buildPayload() {
      return {
        id: this.form.id,
        planName: this.form.planName,
        workflowVersionId: this.form.workflowVersionId,
        executionMode: this.form.executionMode,
        expectedDurationSeconds: (this.form.expectedDurationMinutes || 0) * 60,
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
        actionParameterBindings: this.form.actionParameterBindings || [],
        targetBindings: this.form.targetBindings || [],
        offlinePolicy: this.form.offlinePolicy,
        enabled: this.form.enabled,
        remark: this.form.remark
      }
    },
    collectActionParameterRequirements(workflowVersion) {
      const document = this.parseDefinition(workflowVersion && workflowVersion.definitionJson)
      const requirements = []
      ;(document.nodes || []).forEach(node => {
        const config = node.config || {}
        this.collectActionParameterRequirementsForPhase(requirements, workflowVersion, node, 'SINGLE', config.actions)
        this.collectActionParameterRequirementsForPhase(requirements, workflowVersion, node, 'MOVING', config.transitActions)
        this.collectActionParameterRequirementsForPhase(requirements, workflowVersion, node, 'ARRIVAL', config.arrivalActions)
      })
      return requirements
    },
    collectTargetRequirements(workflowVersion) {
      const document = this.parseDefinition(workflowVersion && workflowVersion.definitionJson)
      return (document.nodes || [])
        .filter(node => node.type === 'DEVICE_TASK' && node.config && node.config.targetType === 'PLAN_COORDINATE')
        .map(node => ({
          workflowVersionId: workflowVersion.id,
          workflowName: workflowVersion.workflowName || '任务流程',
          workflowNodeId: node.id,
          workflowNodeName: node.name || '设备任务'
        }))
    },
    collectActionParameterRequirementsForPhase(requirements, workflowVersion, node, phase, actions) {
      if (!Array.isArray(actions)) return
      actions.forEach((action, index) => {
        const properties = action && action.paramsSchema && action.paramsSchema.properties ? action.paramsSchema.properties : {}
        const parameterNames = Array.isArray(action && action.planParameterNames) ? action.planParameterNames : []
        parameterNames.forEach(parameterName => {
          const schema = properties[parameterName]
          if (!schema) return
          requirements.push({
            workflowVersionId: workflowVersion.id,
            workflowName: workflowVersion.workflowName || '任务流程',
            workflowNodeId: node.id,
            workflowNodeName: node.name || '执行阶段',
            phase,
            actionIndex: index + 1,
            actionName: action.actionName || action.actionCode || '业务动作',
            parameterName,
            label: schema.title || schema.label || parameterName,
            description: schema.description || '',
            schema
          })
        })
      })
    },
    groupActionParameterRequirements(requirements) {
      const groups = {}
      ;(requirements || []).forEach(parameter => {
        const title = [parameter.workflowName, parameter.workflowNodeName, parameter.actionName].filter(Boolean).join(' / ')
        const key = [parameter.workflowVersionId, parameter.workflowNodeId, parameter.phase, parameter.actionIndex].join('|')
        if (!groups[key]) groups[key] = { key, title, parameters: [] }
        groups[key].parameters.push(parameter)
      })
      return Object.keys(groups).map(key => groups[key])
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
    targetBindingKey(target) {
      return [target.workflowVersionId, target.workflowNodeId].join('|')
    },
    targetDisplayLabel(target) {
      return [target.workflowName, target.workflowNodeName, '目标坐标'].filter(Boolean).join(' / ')
    },
    targetValue(target) {
      return (this.form.targetBindings || []).find(item => this.targetBindingKey(item) === this.targetBindingKey(target)) || {}
    },
    targetNumberValue(target, field) {
      const value = this.targetValue(target)[field]
      return value === '' || value == null ? undefined : Number(value)
    },
    setTargetValue(target, field, value) {
      const key = this.targetBindingKey(target)
      const list = this.form.targetBindings || []
      const index = list.findIndex(item => this.targetBindingKey(item) === key)
      const binding = Object.assign({}, this.targetValue(target), {
        workflowVersionId: target.workflowVersionId,
        workflowNodeId: target.workflowNodeId,
        [field]: value
      })
      if (index < 0) {
        this.form.targetBindings = list.concat(binding)
      } else {
        this.$set(this.form.targetBindings, index, binding)
      }
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
    parameterValue(parameter) {
      const binding = (this.form.actionParameterBindings || [])
        .find(item => this.parameterBindingKey(item) === this.parameterBindingKey(parameter))
      return binding && binding.value !== undefined ? binding.value : ''
    },
    numberParameterValue(parameter) {
      const value = this.parameterValue(parameter)
      return value === '' || value == null ? undefined : Number(value)
    },
    setParameterValue(parameter, value) {
      const key = this.parameterBindingKey(parameter)
      const list = this.form.actionParameterBindings || []
      const index = list.findIndex(item => this.parameterBindingKey(item) === key)
      const binding = {
        workflowVersionId: parameter.workflowVersionId,
        workflowNodeId: parameter.workflowNodeId,
        phase: parameter.phase,
        actionIndex: parameter.actionIndex,
        parameterName: parameter.parameterName,
        value
      }
      if (index < 0) {
        this.form.actionParameterBindings = list.concat(binding)
      } else {
        this.$set(this.form.actionParameterBindings, index, binding)
      }
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
    hasParameterValue(value) {
      return value != null && (!(typeof value === 'string') || value.trim() !== '')
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
      return (this.form.componentBindings || [])
        .filter(item => this.sameComponentRequirement(item, row))
        .map(item => item.componentCode)
        .filter(Boolean)
    },
    applyComponentBinding(row, value) {
      const componentCodes = Array.isArray(value) ? value : (value ? [value] : [])
      const next = (this.form.componentBindings || []).filter(item => !this.sameComponentRequirement(item, row))
      componentCodes.forEach(componentCode => {
        next.push({
          roleKey: row.roleKey,
          deviceId: row.deviceId,
          capabilityCode: row.capabilityCode,
          actionCode: row.actionCode,
          componentCode
        })
      })
      this.form.componentBindings = next
    },
    normalizeComponentBindings(bindings) {
      const next = []
      ;(bindings || []).forEach(item => {
        const componentCodes = Array.isArray(item.componentCodes) ? item.componentCodes : (item.componentCode ? [item.componentCode] : [])
        componentCodes.forEach(componentCode => {
          next.push(Object.assign({}, item, {
            componentCode,
            componentCodes: undefined
          }))
        })
      })
      return next
    },
    sameComponentRequirement(left, right) {
      return String(left.roleKey || '') === String(right.roleKey || '') &&
        String(left.deviceId || '') === String(right.deviceId || '') &&
        String(left.capabilityCode || '') === String(right.capabilityCode || '') &&
        String(left.actionCode || '') === String(right.actionCode || '')
    },
    componentOptions(row) {
      if (Array.isArray(row.sourceComponents) && row.sourceComponents.length) return row.sourceComponents
      if (Array.isArray(row.candidates) && row.candidates.length) return row.candidates
      if (Array.isArray(row.components) && row.components.length) return row.components
      return (row.sourceComponentCodes || []).map(componentCode => ({
        componentCode,
        componentName: componentCode
      }))
    },
    workflowNodeLabel(row) {
      if (row.workflowNodeName) return row.workflowNodeName
      const nodes = Array.isArray(this.workflowDocument.nodes) ? this.workflowDocument.nodes : []
      const node = nodes.find(item => item.id === row.workflowNodeKey || item.key === row.workflowNodeKey)
      return (node && (node.name || node.label)) || row.workflowNodeKey || '-'
    },
    normalizeBinding(role, saved) {
      const savedDeviceIds = Array.isArray(saved && saved.deviceIds) ? saved.deviceIds : []
      return {
        roleKey: role.roleKey,
        roleName: role.roleName || role.roleKey,
        roleType: role.roleType || 'EXECUTOR',
        requiredCapabilityCodes: role.requiredCapabilityCodes || [],
        requiredActionCodes: role.requiredActionCodes || [],
        sceneIds: role.sceneIds || [],
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
        planName: '',
        workflowVersionId: null,
        componentBindings: [],
        actionParameterBindings: [],
        targetBindings: [],
        offlinePolicy: 'CONTINUE',
        executionMode: 'MANUAL',
        expectedDurationMinutes: 60,
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
    unwrapList(res) {
      const data = this.unwrap(res)
      if (Array.isArray(data)) return data
      if (data && Array.isArray(data.records)) return data.records
      return []
    },
    uniqueStrings(values) {
      const next = []
      ;(values || []).forEach(item => {
        if (item != null && next.indexOf(item) === -1) next.push(item)
      })
      return next
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
</style>
