<template>
  <RecordDetail
    v-if="showDetail"
    :id="currentId"
    class="business2-content ml10 flex1 h100 no-w-scroll"
    @close="closeDetail"
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
            :class="{ 'is-active': filters.status === item.value }"
            @click="changeStatus(item.value)"
          >
            {{ item.label }}
          </div>
        </div>
        <el-select v-model="filters.triggerType" clearable placeholder="触发方式" class="business2-search" @change="loadRows(1)">
          <el-option label="手动执行" value="MANUAL" />
          <el-option label="计划执行" value="SCHEDULE" />
        </el-select>
        <el-checkbox v-model="filters.includeRunning" @change="loadRows(1)">包含运行中</el-checkbox>
      </div>
      <div class="business2-actions flx-align-center">
        <div class="custom-search-div">
          <el-input
            v-model="filters.keyword"
            clearable
            placeholder="编排名称 / 业务键"
            class="business2-search"
            @keyup.enter.native="loadRows(1)"
          >
            <svg-icon slot="prefix" icon-class="search" />
          </el-input>
        </div>
        <div class="table-btns ml10">
          <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="loadRows(1)">查询</el-button>
          <el-button type="primary" class="pr20 pl20" plain style="color: #17D1FF" @click="resetFilters">重置</el-button>
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
        <el-table-column label="执行任务" min-width="240" show-overflow-tooltip>
          <template slot-scope="{ row }">
            <div class="flex-column" style="justify-content: center; line-height: 22px;">
              <strong>{{ row.workflowName || '-' }}</strong>
              <div class="muted">{{ row.workflowCode || '-' }} · {{ row.businessKey || row.id }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="执行设备" min-width="170" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ deviceSummary(row) }}</template>
        </el-table-column>
        <el-table-column label="触发方式" width="110">
          <template slot-scope="{ row }">{{ triggerTypeLabel(row.triggerType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template slot-scope="{ row }">
            <span class="status" :class="statusTagType(row.status)">{{ statusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ failureReason(row) }}</template>
        </el-table-column>
        <el-table-column label="视频" width="110" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" :disabled="!videoCount(row)" @click="showVideos(row)">
              {{ videoCount(row) }} 个视频
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="告警" width="90" align="center">
          <template slot-scope="{ row }">
            <span class="status" :class="row.alarmCount ? 'red' : 'info'">{{ row.alarmCount || 0 }} 条</span>
          </template>
        </el-table-column>
        <el-table-column label="轨迹" width="110" align="center">
          <template slot-scope="{ row }">
            <span class="status" :class="trackStatusType(row.trackStatus)">{{ trackStatusLabel(row.trackStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="媒体绑定" width="120" align="center">
          <template slot-scope="{ row }">
            <el-tooltip v-if="row.mediaBindingMessage" :content="row.mediaBindingMessage" placement="top">
              <span class="status" :class="mediaBindingTagType(row.mediaBindingStatus)">{{ mediaBindingLabel(row.mediaBindingStatus) }}</span>
            </el-tooltip>
            <span v-else class="status" :class="mediaBindingTagType(row.mediaBindingStatus)">{{ mediaBindingLabel(row.mediaBindingStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" min-width="160">
          <template slot-scope="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="完成时间" min-width="160">
          <template slot-scope="{ row }">{{ formatDateTime(row.completedAt) }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template slot-scope="{ row }">{{ durationLabel(row.durationSeconds) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="60" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" @click="openDetail(row)">详情</el-button>
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

    <el-dialog :visible.sync="videosVisible" title="执行视频结果" width="860px">
      <el-table :data="currentVideos">
        <el-table-column label="文件 ID" min-width="190" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.fileId || row.videoId || '-' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template slot-scope="{ row }">{{ mediaTypeLabel(row.mediaType) }}</template>
        </el-table-column>
        <el-table-column label="来源组件" min-width="150" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.sourceComponentName || row.sourceComponentCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="动作" min-width="170" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.actionCode || row.actionRef || '-' }}</template>
        </el-table-column>
        <el-table-column label="开始" min-width="160">
          <template slot-scope="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束" min-width="160">
          <template slot-scope="{ row }">{{ formatDateTime(row.endedAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script>
import { getTaskRecordList } from '@/api/new-bi'
import RecordDetail from './RecordDetail.vue'

export default {
  name: 'BiPatrolBusiness2Record',
  components: { RecordDetail },
  props: {
    initialId: {
      type: [String, Number],
      default: ''
    }
  },
  data() {
    return {
      loading: false,
      rows: [],
      page: { pageNum: 1, pageSize: 10, total: 0 },
      filters: {
        keyword: '',
        status: 'all',
        triggerType: '',
        includeRunning: true
      },
      statusTabs: [
        { value: 'all', label: '全部' },
        { value: 'PREPARING', label: '准备中' },
        { value: 'RUNNING', label: '运行中' },
        { value: 'COMPLETED', label: '已完成' },
        { value: 'FAILED', label: '失败' },
        { value: 'CANCELED', label: '已取消' }
      ],
      videosVisible: false,
      currentVideos: [],
      showDetail: false,
      currentId: ''
    }
  },
  watch: {
    initialId: {
      immediate: true,
      handler(value) {
        if (value) this.openDetailById(value)
      }
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
        const data = this.unwrap(await getTaskRecordList({
          pageNum: this.page.pageNum,
          pageSize: this.page.pageSize,
          keyword: this.filters.keyword || undefined,
          status: this.filters.status === 'all' ? undefined : this.filters.status,
          triggerType: this.filters.triggerType || undefined,
          includeRunning: this.filters.includeRunning || undefined
        }))
        this.rows = data.records || []
        this.page.pageNum = data.pageNum || this.page.pageNum
        this.page.pageSize = data.pageSize || this.page.pageSize
        this.page.total = data.total || 0
      } catch (error) {
        this.showError(error)
      } finally {
        this.loading = false
      }
    },
    changeStatus(value) {
      this.filters.status = value
      this.loadRows(1)
    },
    resetFilters() {
      this.filters = { keyword: '', status: 'all', triggerType: '', includeRunning: true }
      this.loadRows(1)
    },
    openDetail(row) {
      this.currentId = row.id
      this.showDetail = true
    },
    openDetailById(id) {
      this.currentId = String(id)
      this.showDetail = true
    },
    closeDetail() {
      this.showDetail = false
      this.currentId = ''
      this.$emit('clear-record')
    },
    showVideos(row) {
      this.currentVideos = Array.isArray(row.videoResults) ? row.videoResults : []
      this.videosVisible = true
    },
    triggerTypeLabel(value) {
      return { MANUAL: '手动执行', SCHEDULE: '计划执行' }[value] || value || '-'
    },
    statusLabel(value) {
      return { PREPARING: '准备中', RUNNING: '运行中', COMPLETED: '已完成', FAILED: '失败', CANCELED: '已取消' }[value] || value || '-'
    },
    statusTagType(value) {
      return { COMPLETED: 'green', FAILED: 'red', CANCELED: 'info', RUNNING: 'orange', PREPARING: 'orange' }[value] || 'info'
    },
    failureReason(row) {
      if (!row || row.status !== 'FAILED') return '-'
      return row.failureReason || '执行失败'
    },
    mediaBindingLabel(value) {
      return { NONE: '无视频', PENDING: '待绑定', BOUND: '已绑定', BIND_FAILED: '绑定失败' }[value] || value || '无视频'
    },
    mediaBindingTagType(value) {
      return { BOUND: 'green', PENDING: 'orange', BIND_FAILED: 'red', NONE: 'info' }[value] || 'info'
    },
    mediaTypeLabel(value) {
      return { VISIBLE: '可见光', THERMAL: '红外', OTHER: '其他' }[value] || value || '其他'
    },
    videoCount(row) {
      return Number(row.videoCount !== undefined ? row.videoCount : (Array.isArray(row.videoResults) ? row.videoResults.length : 0))
    },
    deviceSummary(row) {
      const devices = Array.isArray(row.deviceSummaries) ? row.deviceSummaries : []
      if (!devices.length) return '-'
      if (devices.length === 1) return devices[0].deviceName || devices[0].serialNumber || '-'
      return devices.slice(0, 2).map(item => item.deviceName || item.serialNumber || '-').join('、') + (devices.length > 2 ? ` 等 ${devices.length} 台` : '')
    },
    trackStatusLabel(value) {
      return { AVAILABLE: '正常', PROCESSING: '处理中', MISSING: '缺失' }[value] || value || '处理中'
    },
    trackStatusType(value) {
      return { AVAILABLE: 'green', PROCESSING: 'orange', MISSING: 'red' }[value] || 'info'
    },
    durationLabel(seconds) {
      const value = Number(seconds)
      if (!Number.isFinite(value) || value < 0) return '-'
      const minutes = Math.floor(value / 60)
      const rest = value % 60
      return minutes ? `${minutes}分${rest}秒` : `${rest}秒`
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
