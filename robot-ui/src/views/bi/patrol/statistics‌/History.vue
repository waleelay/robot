<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :modal="false"
    append-to-body
    center
    title=""
  >
    <template slot="footer"></template>
    <div class="flex mb20 ml72">
      <div class="custom-modal-container robot-control-container">
        <div class="box">
          <div class="top m10 flx-justify-between">
            <div class="title ml10">历史报告列表</div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content p10">
            <el-table :data="tableData.data" ref="dataTableRef" :height="500">
              <el-table-column type="index" key="index" width="50" label="序号" align="center">
                <template slot-scope="scope">
                  <span class="td-index1">
                    {{ (tableData.page - 1) * tableData.size + scope.$index + 1 }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="reportName" key="reportName" label="报告名称" show-overflow-tooltip></el-table-column>
              <el-table-column prop="downloadTime" key="downloadTime" width="170" label="下载时间" />
              <el-table-column prop="format" key="format" label="格式" width="80" show-overflow-tooltip></el-table-column>
              <el-table-column key="status" width="120" label="状态" align="center">
                <template slot-scope="scope">
                  <span class="status" :class="{ green: scope.row.status.toLowerCase() === 'completed', error: scope.row.status.toLowerCase() === 'interrupted' }">
                    {{ scope.row.statusName }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template slot-scope="scope">
                  <el-button type="text" @click="downloadFile(scope.row)">下载</el-button>
                  <el-button type="text" class="ml24" @click="deleteFile(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <!-- v-if="tableData.total > tableData.size" -->
            <el-pagination
              v-model:current-page="tableData.page"
              background
              :page-size="tableData.size"
              :total="tableData.total"
              layout="prev, pager, next"
              @current-change="getHistoryData"
              class="pagination-row"
            />
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { deleteReport, downloadReport, getHistoryList } from '../../../../api/new-bi';
export default {
  name: 'HistoryReportList',
  data() {
    return {
      dialogVisible: false,
      tableData: {
        data: [
          // { jobName: '周界定时巡逻任务', reportName: '周界定时巡逻任务', jobDescription: '每隔1h执行1次', createTime: '2026-03-31', executeStatus: 0, loading: false, nextValidTime: '2026-04-01 06:00:00'},
          // { jobName: '一监区监舍人员清点', reportName: '一监区监舍人员清点', jobDescription: '每日20:30执行', createTime: '2026-03-31', executeStatus: 0, loading: false, nextValidTime: '2026-04-01 20:30:00'},
          // { jobName: '成衣车间人员清点', reportName: '成衣车间人员清点', jobDescription: '每1h执行1次', createTime: '2026-03-31', executeStatus: 1, loading: false, nextValidTime: '2026-04-01 06:00:00'},
          // { jobName: '公共区域定时巡逻', reportName: '公共区域定时巡逻', jobDescription: '每1h执行1次', createTime: '2026-03-31', executeStatus: 1, loading: false, nextValidTime: '2026-04-01 06:00:00'},
          // { jobName: '多功能厅定时巡逻', reportName: '多功能厅定时巡逻', jobDescription: '每日06:30执行', createTime: '2026-03-31', executeStatus: 2, loading: false, nextValidTime: '2026-04-01 06:30:00'},
          // { jobName: '会见大厅公共法律服', reportName: '会见大厅公共法律服', jobDescription: '每日08:00执行', createTime: '2026-03-31', executeStatus: 2, loading: false, nextValidTime: '2026-04-01 08:00:00'},
        ],
        total: 0,
        size: 10,
        page: 1
      },
    }
  },
  methods: {
    async getHistoryData(page) {
      if (page) this.tableData.page = page
      const res = await getHistoryList({ page: this.tableData.page, size: this.tableData.size })
      this.tableData.data = res.data || []
      this.tableData.total = res.total || 0
    },
    showModal() {
      this.dialogVisible = true;
      this.getHistoryData()
    },
    async downloadFile(row) {
      try {
        const res = await downloadReport(row.id)
        const blob = new Blob([res], {type: this.getMimeType(row.format.toLowerCase())});
        // 创建 Blob URL
        const blobUrl = window.URL.createObjectURL(blob);

        // 创建下载链接
        const link = document.createElement('a');
        link.href = blobUrl;
        // 设置文件名
        link.download = row.reportName;
        document.body.appendChild(link);

        // 触发下载
        link.click();

        // 移除链接并释放 Blob URL
        document.body.removeChild(link);
        window.URL.revokeObjectURL(blobUrl);
      } catch (error) {
        this.$message.error('下载失败')
      }
    },
    async deleteFile(row) {
      try {
        const res = await deleteReport(row.id)
        this.$message.success('删除成功')     
        this.getHistoryData()  
      } catch (error) {
        this.$message.error('删除失败')
      }
    },
    getMimeType(extension) {
      const mimeTypes = {
          pdf: 'application/pdf',
          txt: 'text/plain',
          doc: 'application/msword',
          docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          xls: 'application/vnd.ms-excel',
          xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          png: 'image/png',
          jpg: 'image/jpeg',
          jpeg: 'image/jpeg',
          gif: 'image/gif',
          csv: 'text/csv',
          json: 'application/json',
          zip: 'application/zip',
          rar: 'application/x-rar-compressed'
          // 更多 MIME 类型可以根据需求添加
      };
      // 默认类型
      return mimeTypes[extension] || 'application/octet-stream';
    }
  }
}
</script>

<style lang="scss" scoped>
@import './../business/common.scss';
.custom-modal-container {
  background: linear-gradient(180deg, rgba(4, 60, 149, 0.40) 0.01%, rgba(4, 33, 68, 0.30) 6.03%, rgba(4, 23, 62, 0.32) 56.39%, rgba(7, 45, 94, 0.31) 101.39%, rgba(4, 62, 151, 0.40) 109.49%);
  backdrop-filter: blur(15px);
  border: 1px solid #2A86F3;
}
.custom-dialog__wrapper {
  background: rgba(2, 19, 40, 0.80);
}
::v-deep {
  .el-table tr th {
    background: #223860;
  }
}
</style>