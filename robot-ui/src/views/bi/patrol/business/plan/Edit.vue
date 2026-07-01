<template>
  <div>
    <div class="page-action-header">
      <el-button @click="$emit('close')">返回列表</el-button>
      <div>
        <div class="page-action-header__title">{{ editorTitle }}</div>
        <div class="muted">执行方式与设备绑定属于计划，任务流程结构仍由任务编排维护。</div>
      </div>
      <div class="page-action-header__actions">
        <el-button v-if="isViewMode" @click="$router.push(`/eiop/tasks/plans/${route.params.id}/edit`)">编辑</el-button>
        <el-button v-else type="primary" @click="savePlan">保存</el-button>
      </div>
    </div>

    <section class="panel" v-loading="editorLoading">
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
                :label="`${item.workflowName} / ${item.workflowCode} · v${item.latestPublishedVersion}`"
                :value="item.latestPublishedVersionId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="执行方式" required>
            <el-select v-model="form.executionMode">
              <el-option v-for="item in executionModeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.executionMode === 'SCHEDULE'" label="计划周期" required>
            <el-select v-model="form.scheduleConfig.preset">
              <el-option v-for="item in schedulePresetOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="form.executionMode === 'SCHEDULE' && ['DAILY', 'WORKDAY', 'WEEKLY'].includes(form.scheduleConfig.preset)"
            label="执行时间"
            required
          >
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
            <el-tag effect="plain">v{{ selectedVersion.versionNo }}</el-tag>
          </div>
          <div class="template-summary">
            <div><span>根流程</span><strong>{{ selectedVersion.workflowName }}</strong></div>
            <div><span>流程节点</span><strong>{{ workflowDocument.nodes?.length || 0 }}</strong></div>
            <div><span>子流程依赖</span><strong>{{ selectedVersion.dependencies?.length || 0 }}</strong></div>
          </div>
          <el-table v-if="selectedVersion.dependencies?.length" :data="selectedVersion.dependencies" border size="small">
            <el-table-column prop="workflowName" label="子流程" min-width="180" />
            <el-table-column prop="workflowCode" label="编码" min-width="160" />
            <el-table-column prop="versionNo" label="冻结版本" width="100">
              <template #default="{ row }">v{{ row.versionNo }}</template>
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
              <el-tag effect="plain">{{ requirementSummary(role) }}</el-tag>
            </div>
            <div class="form-grid">
              <el-form-item label="执行设备" class="full" required>
                <el-select v-model="role.deviceId" filterable clearable placeholder="选择一台实际设备">
                  <el-option v-for="item in deviceOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </div>
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
        <el-table v-if="componentRequirements.length" :data="componentRequirements" border size="small">
          <el-table-column label="执行设备" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.deviceName || row.serialNumber || row.deviceId || "-" }}
            </template>
          </el-table-column>
          <el-table-column prop="workflowNodeKey" label="流程节点" min-width="140" />
          <el-table-column label="动作" min-width="180">
            <template #default="{ row }">
              <el-tooltip :content="`${row.capabilityCode || '-'} / ${row.actionCode || '-'}`" placement="top">
                <span>{{ row.capabilityName || row.capabilityCode }} / {{ row.actionName || row.actionCode }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="执行组件" min-width="190">
            <template #default="{ row }">
              <el-select
                :model-value="componentBindingValue(row)"
                placeholder="选择组件"
                @change="(value) => applyComponentBinding(row, value)"
              >
                <el-option
                  v-for="component in componentOptions(row)"
                  :key="component.componentCode"
                  :label="component.componentName || component.componentCode"
                  :value="component.componentCode"
                >
                  <span>{{ component.componentName || component.componentCode }}</span>
                  <span class="option-code">{{ component.componentCode }}</span>
                </el-option>
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
    </section>
  </div>
</template>

<script>
export default {
  name: 'BiPatrolBusinessPlanEdit',
  data() {
    return {
      id: '',
      editorLoading: false,
      form: this.defaultForm()
    }
  },
  computed: {
    isViewMode() {
      return Boolean(this.id) && !this.$route.path.endsWith("/edit")
    },
    editorTitle(){
      if (!this.id) return "新建任务计划";
      return this.isViewMode ? "任务计划详情" : "编辑任务计划";
    }
  },
  methods: {
    savePlan() {
    },
    defaultForm() {
      return {
      id: null,
      planCode: "",
      planName: "",
      workflowVersionId: null,
      componentBindings: [],
      offlinePolicy: "CONTINUE",
      executionMode: "MANUAL",
      scheduleConfig: { preset: "HOURLY", cron: "0 0 * * * ?", timezone: "Asia/Shanghai", timeOfDay: "08:00", weekday: "MON" },
      eventTriggerConfig: { eventType: "ALARM", eventSubtype: "" },
      enabled: true,
      remark: ""
    }
    }
  }
}
</script>
