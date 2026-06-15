<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="告警时间">
        <el-date-picker clearable
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :picker-options="pickerOptions">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="告警类型" prop="alarmType">
        <el-select v-model="queryParams.alarmType" placeholder="请选择告警类型" clearable style="width: 160px;">
          <el-option
            v-for="dict in dict.type.qh_alarm_record_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="告警状态" prop="state">
        <el-select v-model="queryParams.state" placeholder="请选择告警状态" clearable style="width: 160px;">
          <el-option
            v-for="dict in dict.type.qh_alarm_record_state"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <!-- <el-form-item label="业务场景" prop="alarmStatus">
        <el-select v-model="queryParams.state" placeholder="请选择告警状态" clearable style="width: 160px;"> -->
          <!-- <el-option
            v-for="dict in dict.type.qh_alarm_record_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          /> -->
        <!-- </el-select>
      </el-form-item> -->
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-download"
          size="mini"
          :disabled="multiple"
          @click="handleExport()"
          v-hasPermi="['rsp:alarm-record:export']"
        >导出记录</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="alarmRecordList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="装备名称" prop="equipmentName" />
      <el-table-column label="告警类型" align="center">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_alarm_record_type" :value="scope.row.alarmType"/>
        </template>
      </el-table-column>
      <!-- <el-table-column label="业务场景" prop="sceneName" /> -->
      <el-table-column label="告警时间" prop="alarmTime" width="180">
        <!-- <template slot-scope="scope">
          <span>{{ parseTime(scope.row.alarmTime, '{y}-{m}-{d}') }}</span>
        </template> -->
      </el-table-column>
      <el-table-column label="告警内容" align="center" prop="alarmContent" />
      <el-table-column label="告警状态" align="center">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_alarm_record_state" :value="scope.row.state"/>
        </template>
      </el-table-column>
      <el-table-column label="处理结果" align="center" prop="alarmContent" />
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script>
import { listAlarmRecord } from "@/api/rsp/alarm";
import { changeDate } from "../../utils";

export default {
  name: "AlarmRecord",
  dicts: ['qh_alarm_record_type', 'qh_alarm_record_state'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 告警信息表格数据
      alarmRecordList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        alarmType: null,
        startTime: null,
        endTime: null,
        state: null
        // TODO:业务场景 是 否
      },
      timeRange: [],
      pickerOptions: {
        disabledDate: (time) => {
          // 禁用今天以后的日期
          const today = new Date();
          return time.getTime() > today.getTime(); // 禁用未来的日期
        },
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
      },
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询告警信息列表 */
    getList() {
      this.loading = true;
      listAlarmRecord(this.queryParams).then(response => {
        this.alarmRecordList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    /** 搜索按钮操作 */
    handleQuery() {
      if (this.timeRange && this.timeRange.length) {
        this.queryParams.startTime = changeDate(this.timeRange[0])
        this.queryParams.endTime = changeDate(this.timeRange[1])
      }
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.timeRange = []
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('rsp/alarm-record/export/' + this.ids.join(','), {
        // ...this.queryParams
        // const ids = row.id || this.ids;
      }, `alarm-record_${new Date().getTime()}.xlsx`)
    }
  }
};
</script>
