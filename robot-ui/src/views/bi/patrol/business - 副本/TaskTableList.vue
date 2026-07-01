<template>
  <div class="ml10 flex1 h100">
    <div class="flx-justify-between">
      <div class="custom-tab-button flex">
        <div
          v-for="item in tabList"
          :key="item.value"
          class="tab-button-item pt5 pb5"
          style="font-size: 14px; line-height: 19px;"
          :class="{ 'is-active': tabIndex === item.value }"
          @click="tabIndex = item.value">
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
          <el-button type="primary" plain style="color: #17D1FF" @click="addTask">
            <svg-icon icon-class="plus" class="mr10" />
            创建任务
          </el-button>
        </div>
      </div>
    </div>
    <div class="mt20">
      <div class="flex1">
        <div class="mm_table_box table h100">
          <el-table :data="ddrwData.tableData.data" ref="dataTableRef1" style="height: calc(100% - 34px)">
            <el-table-column type="index" key="index" width="50" label="序号" align="center">
              <template slot-scope="scope">
                <span class="td-index1">
                  {{ (ddrwData.tableData.page - 1) * ddrwData.tableData.size + scope.$index + 1 }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="jobName" key="jobName" label="任务名称" show-overflow-tooltip></el-table-column>
            <el-table-column prop="pathName" key="pathName" label="路线名称" show-overflow-tooltip></el-table-column>
            <el-table-column prop="equipmentName" key="equipmentName" label="装备名称" width="185">
              <template slot-scope="scope">
                <span class="type">{{ scope.row.equipmentName }}</span>
                <!-- <span class="type ml10">机器人02</span> -->
              </template>
            </el-table-column>
            <el-table-column prop="createTime" key="createTime" label="创建时间" show-overflow-tooltip></el-table-column>
            <el-table-column prop="jobDescription" key="jobDescription" label="执行周期" show-overflow-tooltip></el-table-column>
            <el-table-column prop="nextValidTime" key="nextValidTime" label="下次执行时间" show-overflow-tooltip></el-table-column>
            <el-table-column key="status" width="100" label="执行状态">
              <template slot-scope="scope">
                <span class="status" :class="{ green: scope.row.executeStatus === 1, orange: scope.row.executeStatus === 0 }">
                  {{ scope.row.executeStatus === 0 ? '待执行' : scope.row.executeStatus === 1 ? '执行中': '已完成' }}
                </span>
                <!-- <span class="status orange ml20">待执行</span>
                <span class="status ml20">正常</span> -->
              </template>
            </el-table-column>
            <el-table-column label="操作" width="278">
              <template slot-scope="scope">
                <el-button type="text">详情</el-button>
                <el-button type="text" class="ml24">编辑</el-button>
                <el-button type="text" class="ml24">{{ scope.row.executeStatus === 1 ? '暂停' : '立即执行' }}</el-button>
                <el-button type="text" class="ml24">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <!-- v-if="ddrwData.tableData.total > ddrwData.tableData.size" -->
          <el-pagination
            v-model:current-page="ddrwData.tableData.page"
            background
            :page-size="ddrwData.tableData.size"
            :total="ddrwData.tableData.total"
            layout="prev, pager, next"
            @current-change="getXlxcDdrwList"
            class="mt10"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { executeDdrw, getDdrwzxqkInfo } from '../../../../api/bi'
export default {
name: 'BiPatrolBusinessTask',
  data() {
    return {
      tabList: [
        {
          value: 'all',
          label: '全部',
        },
        {
          value: 0,
          label: '待执行',
        },
        {
          value: 1,
          label: '执行中',
        },
        {
          value: 2,
          label: '已完成',
        },
      ],
      tabIndex: 'all',
      ddrwData: {
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
    // handleClick(index) {
    //   this.activeIndex = index;
    // }
    manualExecuteDdrw(item, index) {
      if (item.loading === true) {
        return false
      }
      this.$set(this.ddrwData.tableData.data, index, { ...item, executeStatus: 1 })
      item.loading = true
      executeDdrw({ jobId: item.jobId, jobGroup: item.jobGroup, executeStatus: 1 }).then(res => {
        item.loading = false
        if (res.code === 200) {
          this.$message.success(res.msg)
          this.getDdrwzxqkDataList()
        } else {
          this.$message.error(res.msg)
          this.$set(this.ddrwData.tableData.data, index, { ...item, executeStatus: 0 })
        }
      }).catch(() => {
        item.loading = false
        this.$set(this.ddrwData.tableData.data, index, { ...item, executeStatus: 0 })
      })
    },
    // 巡逻巡查调度任务
    getXlxcDdrwList(page) {
      if (page) {
        this.ddrwData.tableData.page = page
      }
      let query = {pageNum: this.ddrwData.tableData.page, pageSize: this.ddrwData.tableData.size, taskType: 0 }
      getDdrwzxqkInfo(query).then(res => {
        let data = res.data
        this.ddrwData.pendingTasks = data.pendingTasks
        this.ddrwData.executingTasks = data.executingTasks
        this.ddrwData.executedTasks = data.executedTasks
        this.ddrwData.tableData.data = data.sysJobList.map(item => {
          item.loading = false
          return item
        })
        this.ddrwData.tableData.total = data.total
      })
    },
    addTask() {}
  },
  mounted() {
    // this.handleClick(this.activeIndex);
    this.getXlxcDdrwList();
  }
}
</script>

<style scoped lang="scss">
@import './common.scss';
</style>