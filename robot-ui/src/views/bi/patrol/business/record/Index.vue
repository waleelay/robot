<template>
  <div class="ml10 flex1 h100">
    <div :style="{ display: showDetailVisible ? 'none' : 'block' }">
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
            <el-button type="primary" plain style="color: #17D1FF" @click="exportBatch">
              <svg-icon icon-class="export" class="mr10" />
              批量导出
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
              <el-table-column key="workflowName" label="任务名称" min-width="240" show-overflow-tooltip>
                <template slot-scope="scope">
                  <strong>{{ scope.row.workflowName }}</strong>
                  <div class="mb20" style="line-height: 19px; margin-top: -20px;">{{ scope.row.workflowCode }} · {{ scope.row.businessKey || scope.row.id }}</div>
                </template>
              </el-table-column>
              <el-table-column key="equipmentName" label="执行设备" min-width="240" align="center" show-overflow-tooltip>
                <template slot-scope="scope">
                  <span class="status">{{ deviceSummary(scope.row) }}</span>
                </template>
              </el-table-column>
              <el-table-column key="triggerType" label="触发方式" min-width="120" align="center" show-overflow-tooltip>
                <template slot-scope="scope">
                  <span class="status">{{ triggerTypeLabel(scope.row.triggerType) }}</span>
                </template>
              </el-table-column>
              <el-table-column key="status" label="状态" width="100" align="center">
                <template slot-scope="scope">
                  <span class="status" :class="statusTagType(scope.row.status)">
                    {{ statusLabel(scope.row.status) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="视频" width="80">
                <template slot-scope="{ row }">
                  <el-button type="text" :disabled="!videoCount(row)" @click="showVideos(row)">
                    {{ videoCount(row) }} 个视频
                  </el-button>
                </template>
              </el-table-column>
              <el-table-column label="告警" width="80">
                <template slot-scope="{ row }">
                  <span class="status" :class="{ 'orange': row.alarmCount }">
                    {{ row.alarmCount || 0 }} 条
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="轨迹" width="80">
                <template slot-scope="{ row }">
                  <span class="status" :class="trackStatusType(row.trackStatus)">
                    {{ trackStatusLabel(row.trackStatus) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="媒体绑定" width="120">
                <template slot-scope="{ row }">
                  <el-tooltip v-if="row.mediaBindingMessage" :content="row.mediaBindingMessage" placement="top">
                    <span class="status" :class="mediaBindingTagType(row.mediaBindingStatus)">
                      {{ mediaBindingLabel(row.mediaBindingStatus) }}
                    </span>
                  </el-tooltip>
                  <span v-else class="status" :class="mediaBindingTagType(row.mediaBindingStatus)">
                    {{ mediaBindingLabel(row.mediaBindingStatus) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="开始时间" min-width="170">
                <template slot-scope="{ row }">{{ formatDateTime(row.startedAt) }}</template>
              </el-table-column>
              <el-table-column label="完成时间" min-width="170">
                <template slot-scope="{ row }">{{ formatDateTime(row.completedAt) }}</template>
              </el-table-column>
              <el-table-column label="耗时" width="80">
                <template slot-scope="{ row }">{{ durationLabel(row.durationSeconds) }}</template>
              </el-table-column>
              <el-table-column label="备注" min-width="80" show-overflow-tooltip prop="remark" />
              <el-table-column label="操作" width="180" fixed="right">
                <template slot-scope="scope">
                  <el-button type="text" @click="openDetail(scope.row)">详情</el-button>
                  <el-button type="text" class="ml24">导出</el-button>
                  <el-button type="text" class="ml24">删除</el-button>
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
    <RecordDetail :style="{ display: showDetailVisible ? 'block' : 'none' }" ref="recordDetailRef" @close="showDetailVisible = false" />

    <!-- 视频弹窗 -->
    <el-dialog :visible.sync="videosVisible" title="执行视频结果" width="860px">
      <el-table :data="currentVideos" border>
        <el-table-column label="视频 ID" min-width="190" show-overflow-tooltip prop="videoId" />
        <el-table-column label="类型" width="110">
          <template slot-scope="{ row }">{{ mediaTypeLabel(row.mediaType) }}</template>
        </el-table-column>
        <el-table-column label="来源组件" min-width="150" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.sourceComponentName || row.sourceComponentCode || "-" }}</template>
        </el-table-column>
        <el-table-column label="动作" min-width="170" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.actionCode || row.actionRef || "-" }}</template>
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
import { getTaskRecordList } from '../../../../../api/new-bi';
import RecordDetail from './Detail.vue'

export default {
  name: 'BiPatrolBusinessExecuteLogList',
  data() {
    return {
      tabList: [
        {
          value: 'all',
          label: '全部',
        },
        {
          value: 'PREPARING',
          label: '准备中',
        },
        {
          value: 'RUNNING',
          label: '运行中',
        },
        {
          value: 'COMPLETED',
          label: '已完成',
        },
        {
          value: 'FAILED',
          label: '失败',
        },
        {
          value: 'CANCELED',
          label: '已取消',
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
      currentVideos: [],
      videosVisible: false,
      showDetailVisible: false
    }
  },
  components: { RecordDetail },
  methods: {
    // 巡逻巡查调度任务
    async getList(page) {
      if (page) {
        this.taskData.tableData.page = page
      }
      const status = this.tabValue === 'all' ? undefined : this.tabValue
      let query = {pageNum: this.taskData.tableData.page, pageSize: this.taskData.tableData.size, status }
      try {
        const res = await getTaskRecordList(query)        
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
    exportBatch() {
      this.$message.success('批量导出');
    },
    openDetail(row) {
      this.showDetailVisible = true
      this.$refs.recordDetailRef.loadReplay(row.id)
    },
    // 删除
    async deletePlan(row) {
      try {
        // await deleteTask(row.id);
        // this.$message.success('已删除');
        // await getList(1);
      } catch (error) {
        if (error !== "cancel") this.$message.error(error?.message || '请求失败');
      }
    },
    resetFilters() {
      Object.assign(filters, { keyword: "", status: "", triggerType: "", includeRunning: false });
      this.getList(1);
    },
    showVideos(row) {
      this.currentVideos = Array.isArray(row.videoResults) ? row.videoResults : [];
      this.videosVisible = true;
    },
    triggerTypeLabel(value) {
      return { MANUAL: "手动执行", SCHEDULE: "计划执行" }[value] || value || "-";
    },
    statusLabel(value) {
      return {
        PREPARING: "准备中",
        RUNNING: "运行中",
        COMPLETED: "已完成",
        FAILED: "失败",
        CANCELED: "已取消"
      }[value] || value || "-";
    },
    statusTagType(value) {
      return {
        COMPLETED: "green",
        FAILED: "red",
        CANCELED: "info",
        RUNNING: "orange",
        PREPARING: "orange"
      }[value] || "info";
    },
    mediaBindingLabel(value) {
      return {
        NONE: "无视频",
        PENDING: "待绑定",
        BOUND: "已绑定",
        BIND_FAILED: "绑定失败"
      }[value] || value || "无视频";
    },
    mediaBindingTagType(value) {
      return {
        BOUND: "green",
        PENDING: "orange",
        BIND_FAILED: "red",
        NONE: "info"
      }[value] || "info";
    },
    mediaTypeLabel(value) {
      return {
        VISIBLE: "可见光",
        THERMAL: "红外",
        OTHER: "其他"
      }[value] || value || "其他";
    },
    videoCount(row) {
      return Number(row.videoCount ?? (Array.isArray(row.videoResults) ? row.videoResults.length : 0));
    },
    deviceSummary(row) {
      const devices = Array.isArray(row.deviceSummaries) ? row.deviceSummaries : [];
      if (!devices.length) return "-";
      if (devices.length === 1) {
        const device = devices[0];
        return device.deviceName || device.serialNumber || "-";
      }
      return devices
        .slice(0, 2)
        .map((item) => item.deviceName || item.serialNumber || "-")
        .join("、") + (devices.length > 2 ? ` 等 ${devices.length} 台` : "");
    },
    trackStatusLabel(value) {
      return {
        AVAILABLE: "正常",
        PROCESSING: "处理中",
        MISSING: "缺失"
      }[value] || value || "处理中";
    },
    trackStatusType(value) {
      return {
        AVAILABLE: "green",
        PROCESSING: "orange",
        MISSING: "red"
      }[value] || "info";
    },
    durationLabel(seconds) {
      const value = Number(seconds);
      if (!Number.isFinite(value) || value < 0) return "-";
      const minutes = Math.floor(value / 60);
      const rest = value % 60;
      return minutes ? `${minutes}分${rest}秒` : `${rest}秒`;
    },
    formatDateTime(value) {
      if (!value) return "-";
      return String(value).replace("T", " ").slice(0, 19);
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