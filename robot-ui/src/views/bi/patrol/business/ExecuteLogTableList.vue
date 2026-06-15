<template>
  <div class="ml10 flex1 h100">
    <div class="flx-justify-between">
      <div class="custom-tab-button flex">
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
            <svg-icon icon-class="export" class="mr10" />
            批量导出
          </el-button>
        </div>
      </div>
    </div>
    <div class="mt20">
      <div class="flex1">
        <div class="mm_table_box table h100">
          <el-table :data="ddrwzxqkData.tableData.data" ref="dataTableRef1" style="height: calc(100% - 34px)">
            <el-table-column type="index" key="index" width="50" label="序号" align="center">
              <template slot-scope="scope">
                <span class="td-index1">
                  {{ (ddrwzxqkData.tableData.page - 1) * ddrwzxqkData.tableData.size + scope.$index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="mapName" key="pathName" label="地图名称" show-overflow-tooltip></el-table-column>
            <el-table-column prop="equipmentName" key="equipmentName" label="装备名称" width="185">
              <template slot-scope="scope">
                <span class="type">{{ scope.row.equipmentName }}</span>
                <!-- <span class="type ml10">机器人02</span> -->
              </template>
            </el-table-column>
            <el-table-column prop="jobName" key="jobName" label="任务名称" show-overflow-tooltip></el-table-column>
            <el-table-column prop="times" key="times" label="当日执行次数" align="center" show-overflow-tooltip>
              <template slot-scope="scope">
                <span class="status">{{ scope.row.times || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" key="startTime" label="执行时间" show-overflow-tooltip></el-table-column>
            <el-table-column prop="jobDescription" key="jobDescription" label="执行周期" show-overflow-tooltip></el-table-column>
            <el-table-column prop="nextValidTime" key="nextValidTime" label="下次执行时间" show-overflow-tooltip></el-table-column>
            <el-table-column key="alertTimes" width="100" label="有效告警次数" align="center">
              <template slot-scope="scope">
                <span class="status" :class="{ green: scope.row.alertTimes === 0, orange: scope.row.alertTimes !== 0 }">
                  {{ scope.row.alertTimes || 0 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250">
              <template slot-scope="scope">
                <el-button type="text">详情</el-button>
                <el-button type="text" class="ml24">导出</el-button>
                <el-button type="text" class="ml24">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <!-- v-if="ddrwzxqkData.tableData.total > ddrwzxqkData.tableData.size" -->
          <el-pagination
            v-model:current-page="ddrwzxqkData.tableData.page"
            background
            :page-size="ddrwzxqkData.tableData.size"
            :total="ddrwzxqkData.tableData.total"
            layout="prev, pager, next"
            @current-change="getDdrwzxqkDataList"
            class="mt10"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { executeDdrw, getXlxcDdrwzxqkInfo } from '../../../../api/bi'
export default {
name: 'BiPatrolBusinessExecuteLogList',
  data() {
    return {
      ddrwzxqkData: {
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
        pendingTasks: 0,
        executingTasks: 0,
        executedTasks: 0,
      },
      searchValue: ''
    }
  },
  methods: {
    getDdrwzxqkDataList(page) {
      if (page) {
        this.ddrwzxqkData.tableData.page = page
      }
      let query = {pageNum: this.ddrwzxqkData.tableData.page, pageSize: this.ddrwzxqkData.tableData.size, jobTaskType: 0 }
      getXlxcDdrwzxqkInfo(query).then(res => {
        this.ddrwzxqkData.tableData.data = res.rows
        this.ddrwzxqkData.tableData.total = res.total
      })
    },
    addTask() {}
  },
  mounted() {
    // this.handleClick(this.activeIndex);
    this.getDdrwzxqkDataList()
  }
}
</script>

<style scoped lang="scss">
@import './common.scss';
</style>