<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center flx-align-end"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :modal="false"
    append-to-body
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
            <el-table :data="tableData.data" ref="dataTableRef1" style="height: calc(100% - 34px)">
              <el-table-column type="index" key="index" width="50" label="序号" align="center">
                <template slot-scope="scope">
                  <span class="td-index1">
                    {{ (tableData.page - 1) * tableData.size + scope.$index + 1 }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="mapName" key="pathName" label="报告名称" show-overflow-tooltip></el-table-column>
              <el-table-column prop="equipmentName" key="equipmentName" label="下载时间" />
              <el-table-column prop="jobName" key="jobName" label="格式" show-overflow-tooltip></el-table-column>
              <el-table-column key="alertTimes" width="100" label="状态" align="center">
                <template slot-scope="scope">
                  <span class="status" :class="{ green: scope.row.alertTimes === 0, orange: scope.row.alertTimes !== 0 }">
                    {{ scope.row.alertTimes || 0 }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="250">
                <template slot-scope="scope">
                  <el-button type="text">瞎子啊</el-button>
                  <el-button type="text" class="ml24">删除</el-button>
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
              @current-change="getDdrwzxqkDataList"
              class="mt10"
            />
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'HistoryReportList',
  data() {
    return {
      dialogVisible: false,
      tableData: {
        data: [
          { jobName: '周界定时巡逻任务', pathName: '周界定时巡逻任务', jobDescription: '每隔1h执行1次', createTime: '2026-03-31', executeStatus: 0, loading: false, nextValidTime: '2026-04-01 06:00:00'},
          { jobName: '一监区监舍人员清点', pathName: '一监区监舍人员清点', jobDescription: '每日20:30执行', createTime: '2026-03-31', executeStatus: 0, loading: false, nextValidTime: '2026-04-01 20:30:00'},
          { jobName: '成衣车间人员清点', pathName: '成衣车间人员清点', jobDescription: '每1h执行1次', createTime: '2026-03-31', executeStatus: 1, loading: false, nextValidTime: '2026-04-01 06:00:00'},
          { jobName: '公共区域定时巡逻', pathName: '公共区域定时巡逻', jobDescription: '每1h执行1次', createTime: '2026-03-31', executeStatus: 1, loading: false, nextValidTime: '2026-04-01 06:00:00'},
          { jobName: '多功能厅定时巡逻', pathName: '多功能厅定时巡逻', jobDescription: '每日06:30执行', createTime: '2026-03-31', executeStatus: 2, loading: false, nextValidTime: '2026-04-01 06:30:00'},
          { jobName: '会见大厅公共法律服', pathName: '会见大厅公共法律服', jobDescription: '每日08:00执行', createTime: '2026-03-31', executeStatus: 2, loading: false, nextValidTime: '2026-04-01 08:00:00'},
        ],
        total: 10,
        size: 6,
        page: 1
      },
    }
  },
  methods: {
    getDdrwzxqkDataList(page) {},
    showModal() {
      this.dialogVisible = true;
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
  .el-pagination {
    text-align: right;
    .el-pager li,
    button,
    &.is-background .btn-prev,
    &.is-background .btn-next,
    &.is-background .el-pager li {
      min-width: 26px !important;
      height: 26px;
      // color: #18ADFE;
      line-height: 26px;
      // background: rgba(43, 86, 121, 0.30);
      // border: 1px solid #18ADFE;
      border-radius: 2px;
    }
    &.is-background:not(.old) {
      .el-pager li,
      button,
      .btn-prev,
      .btn-next,
      .el-pager li {
        color: #7BA9EA;
        background: #1A2747;
        border: none;
        border-radius: 4px;
      }
      .el-pager li:not(.disabled).active {
        color: #FFF;
        border: none;
        background: #2A86F3;
        box-shadow: none !important;
      }
    }
  }
  .el-pagination:not(.old) {
    text-align: right;
    .el-pager li, button {
      min-width: 26px !important;
      height: 26px;
      color: #7BA9EA;
      line-height: 26px;
      border: none;
    }
    .el-pager li {
      &.active {
        color: #fff;
        background: #2A86F3;
        border: none;
      }
    }
  }
}
</style>