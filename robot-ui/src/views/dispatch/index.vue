<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="任务名称" prop="jobName">
        <el-input
          v-model="queryParams.jobName"
          placeholder="请输入任务名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="任务状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择任务状态" clearable>
          <el-option
            v-for="dict in dict.type.sys_job_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker clearable
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :picker-options="pickerOptions">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="创建人" prop="createBy">
        <el-input
          v-model="queryParams.createBy"
          placeholder="请输入创建人"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
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
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['monitor:job:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['monitor:job:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['monitor:job:remove']"
        >删除</el-button>
      </el-col>
      <!-- <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['monitor:job:export']"
        >导出</el-button>
      </el-col> -->
      <!-- <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-s-operation"
          size="mini"
          @click="handleJobLog"
          v-hasPermi="['monitor:job:query']"
        >日志</el-button>
      </el-col> -->
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="jobList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="任务编号" width="100" align="center" prop="jobId" />
      <el-table-column label="任务名称" align="center" prop="jobName" :show-overflow-tooltip="true" />
      <el-table-column label="任务组名" align="center" prop="jobGroup">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.sys_job_group" :value="scope.row.jobGroup"/>
        </template>
      </el-table-column>
      <!-- <el-table-column label="调用目标字符串" align="center" prop="invokeTarget" :show-overflow-tooltip="true" /> -->
      <el-table-column label="任务路径" align="center" prop="pathName" :show-overflow-tooltip="true" />
      <el-table-column label="装备名称" align="center" prop="equipmentName" :show-overflow-tooltip="true" />
      <!-- <el-table-column label="cron执行表达式" align="center" prop="cronExpression" :show-overflow-tooltip="true" /> -->
      <el-table-column label="执行周期" align="center" prop="jobDescription" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" align="center" prop="createTime" :show-overflow-tooltip="true" />
      <el-table-column label="创建人" align="center" prop="createBy" :show-overflow-tooltip="true" />
      <el-table-column label="任务状态" align="center" width="120px">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.status"
            active-value="0"
            inactive-value="1"
            @change="handleStatusChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" min-width="220" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleView(scope.row)"
            v-hasPermi="['monitor:job:edit']"
          >查看</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['monitor:job:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['monitor:job:remove']"
          >删除</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-caret-right"
            @click="handleRun(scope.row)"
            v-hasPermi="['monitor:job:changeStatus']"
          >执行一次</el-button>
          <!-- <el-dropdown size="mini" @command="(command) => handleCommand(command, scope.row)" v-hasPermi="['monitor:job:changeStatus', 'monitor:job:query']">
            <el-button size="mini" type="text" icon="el-icon-d-arrow-right">更多</el-button>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="handleView" icon="el-icon-view"
                v-hasPermi="['monitor:job:query']">任务详细</el-dropdown-item>
              <el-dropdown-item command="handleJobLog" icon="el-icon-s-operation"
                v-hasPermi="['monitor:job:query']">调度日志</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown> -->
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改定时任务对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="jobName">
              <el-input v-model="form.jobName" placeholder="请输入任务名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务分组" prop="jobGroup">
              <el-select v-model="form.jobGroup" placeholder="请选择任务分组" style="width: 100%">
                <el-option
                  v-for="dict in dict.type.sys_job_group"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务路径" prop="pathId">
              <el-select v-model="form.pathId" placeholder="请选择任务路径" @change="handleChangePath" style="width: 100%">
                <el-option
                  v-for="dict in pathList"
                  :key="dict.pathId"
                  :label="dict.pathName"
                  :value="dict.pathId"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务装备" prop="pathId">
              <el-input v-model="form.equipmentName" disabled />
            </el-form-item>
          </el-col>
          <!-- <el-col :span="12">
            <el-form-item label="任务装备" prop="jobGroup">
              <el-input v-model="form.jobGroup" disabled />
            </el-form-item>
          </el-col> -->
          <!-- <el-col :span="24">
            <el-form-item label="cron表达式" prop="cronExpression">
              <el-input v-model="form.cronExpression" placeholder="请输入cron执行表达式">
                <template slot="append">
                  <el-button type="primary" @click="handleShowCron">
                    生成表达式
                    <i class="el-icon-time el-icon--right"></i>
                  </el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col> -->
          <el-col :span="24">
            <el-col :span="15.5">
              <el-form-item label="执行周期" prop="startTime">
                在
                <el-date-picker clearable
                  v-model="form.startTime"
                  type="datetime"
                  placeholder="请选择开始执行时间"
                  format="yyyy-MM-dd HH:mm:ss"
                  value-format="yyyy-MM-dd HH:mm:ss"
                />
                <span class="mr10 ml10">开始执行，间隔</span>
              </el-form-item>
            </el-col>
            <el-col :span="8.5">
              <el-form-item prop="intervalNum" label-width="0">
                <el-input-number v-model="form.intervalNum" :precision="0" :step="1" :min="1" :max="max" style="width: 120px;"></el-input-number>
                <el-select v-model="form.unit" placeholder="请选择" clearable style="width: 100px; margin-left: 20px;" @change="handleChangeUnit">
                  <el-option
                    v-for="dict in dict.type.execution_interval_unit"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  ></el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-col>
          <el-col :span="24">
            <el-form-item label="执行次数" prop="executeCount">
              <el-input
                v-model="form.executeCount"
                placeholder="请输入正整数"
              >
                <template #append>次</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="预计时长" prop="estimatedDuration">
              <el-input
                v-model="form.estimatedDuration"
                placeholder="请输入正整数"
              >
                <template #append>分钟</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="form.jobId !== undefined">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio
                  v-for="dict in dict.type.sys_job_status"
                  :key="dict.value"
                  :label="dict.value"
                >{{dict.label}}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行策略" prop="misfirePolicy">
              <el-radio-group v-model="form.misfirePolicy" size="small">
                <el-radio-button label="1">立即执行</el-radio-button>
                <el-radio-button label="2">执行一次</el-radio-button>
                <el-radio-button label="3">放弃执行</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否并发" prop="concurrent">
              <el-radio-group v-model="form.concurrent" size="small">
                <el-radio-button label="0">允许</el-radio-button>
                <el-radio-button label="1">禁止</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm()">保存任务</el-button>
        <el-button @click="cancel">取 消</el-button>
        <!-- <el-button type="primary" @click="submitForm('0')">发布任务</el-button> -->
      </div>
    </el-dialog>

    <el-dialog title="Cron表达式生成器" :visible.sync="openCron" append-to-body destroy-on-close class="scrollbar">
      <crontab @hide="openCron=false" @fill="crontabFill" :expression="expression"></crontab>
    </el-dialog>

    <!-- 任务日志详细 -->
    <el-dialog title="任务详细" :visible.sync="openView" width="700px" append-to-body>
      <el-form ref="form" :model="form" label-width="120px" size="mini">
        <el-row>
          <el-col :span="12">
            <el-form-item label="任务编号：">{{ form.jobId }}</el-form-item>
            <el-form-item label="任务名称：">{{ form.jobName }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务分组：">{{ jobGroupFormat(form) }}</el-form-item>
            <el-form-item label="创建时间：">{{ form.createTime }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="cron表达式：">{{ form.cronExpression }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行周期：">{{ form.jobDescription }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次执行时间：">{{ parseTime(form.nextValidTime) }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调用目标方法：">{{ form.invokeTarget }}</el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务状态：">
              <div v-if="form.status == 0">正常</div>
              <div v-else-if="form.status == 1">暂停</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否并发：">
              <div v-if="form.concurrent == 0">允许</div>
              <div v-else-if="form.concurrent == 1">禁止</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行策略：">
              <div v-if="form.misfirePolicy == 0">默认策略</div>
              <div v-else-if="form.misfirePolicy == 1">立即执行</div>
              <div v-else-if="form.misfirePolicy == 2">执行一次</div>
              <div v-else-if="form.misfirePolicy == 3">放弃执行</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="openView = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import Crontab from '@/components/Crontab'
import { listPath } from "../../api/rsp/path";
import { listJob, addJob, getJob, editJob, delJob, runJob, changeJobStatus } from "../../api/rsp/dispatch";
import { changeDate } from "../../utils";

export default {
  components: { Crontab },
  name: "Job",
  dicts: ['sys_job_group', 'sys_job_status', 'execution_interval_unit'],
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
      // 定时任务表格数据
      jobList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示详细弹出层
      openView: false,
      // 是否显示Cron表达式弹出层
      openCron: false,
      // 传入的表达式
      expression: "",
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        jobName: undefined,
        jobGroup: undefined,
        status: undefined,
        createTimeStart: null,
        createTimeEnd: null,
        createBy: null
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
        jobName: [
          { required: true, message: "任务名称不能为空", trigger: "blur" }
        ],
        pathId: [
          { required: true, message: "任务路径不能为空", trigger: "blur" }
        ],
        // invokeTarget: [
        //   { required: true, message: "调用目标字符串不能为空", trigger: "blur" }
        // ],
        // cronExpression: [
        //   { required: true, message: "cron执行表达式不能为空", trigger: "blur" }
        // ],
        startTime: [
          { required: true, message: "开始执行时间不能为空", trigger: "blur" }
        ],
        intervalNum: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        unit: [
          { required: true, message: "请选择", trigger: "blur" }
        ],
        estimatedDuration: [
          {pattern: '^([1-9][0-9]*)$', message: "请输入正整数"}
        ],
        executeCount: [
          {pattern: '^([1-9][0-9]*)$', message: "请输入正整数"}
        ]
      },
      max: 74,
      pathList: []
    };
  },
  created() {
    this.getList();
    listPath({ pathState: '4' }).then(res => {
      this.pathList = res.rows
    })
  },
  methods: {
    handleChangePath(e) {
      this.form.equipmentName = this.pathList.filter(item => item.pathId === e)[0].equipmentName
    },
    /** 查询定时任务列表 */
    getList() {
      this.loading = true;
      listJob(this.queryParams).then(response => {
        this.jobList = response.rows;
        this.total = response.total;
        this.loading = false;
      }).catch(() => this.loading = false);
    },
    // 任务组名字典翻译
    jobGroupFormat(row, column) {
      return this.selectDictLabel(this.dict.type.sys_job_group, row.jobGroup);
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        jobId: undefined,
        jobName: undefined,
        jobGroup: undefined,
        invokeTarget: undefined,
        // cronExpression: undefined,
        misfirePolicy: 1,
        concurrent: 1,
        status: "0",
        pathId: undefined,
        startTime: undefined,
        intervalNum: 1,
        unit: '年'
      };
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      if (this.timeRange && this.timeRange.length) {
        this.queryParams.createTimeStart = changeDate(this.timeRange[0])
        this.queryParams.createTimeEnd = changeDate(this.timeRange[1])
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
      this.ids = selection.map(item => item.jobId);
      this.single = selection.length != 1;
      this.multiple = !selection.length;
    },
    // 更多操作触发
    handleCommand(command, row) {
      switch (command) {
        case "handleRun":
          this.handleRun(row);
          break;
        case "handleView":
          this.handleView(row);
          break;
        case "handleJobLog":
          this.handleJobLog(row);
          break;
        default:
          break;
      }
    },
    // 任务状态修改
    handleStatusChange(row) {
      let text = row.status === "0" ? "启用" : "停用";
      this.$modal.confirm('确认要"' + text + '""' + row.jobName + '"任务吗？').then(function() {
        return changeJobStatus(row.jobId, row.status);
      }).then(() => {
        this.$modal.msgSuccess(text + "成功");
      }).catch(function() {
        row.status = row.status === "0" ? "1" : "0";
      });
    },
    /* 立即执行一次 */
    handleRun(row) {
      this.$modal.confirm('确认要立即执行一次"' + row.jobName + '"任务吗？').then(function() {
        return runJob(row.jobId, row.jobGroup);
      }).then(() => {
        this.$modal.msgSuccess("执行成功");
      }).catch(() => {});
    },
    /** 任务详细信息 */
    handleView(row) {
      getJob(row.jobId).then(response => {
        if (![undefined, null, ''].includes(response.data.estimatedDuration)) {
          response.data.estimatedDuration = Number(response.data.estimatedDuration)
        }
        if (![undefined, null, ''].includes(response.data.executeCount)) {
          response.data.executeCount = Number(response.data.executeCount)
        }
        this.form = response.data;
        this.openView = true;
      });
    },
    /** cron表达式按钮操作 */
    handleShowCron() {
      this.expression = this.form.cronExpression;
      this.openCron = true;
    },
    /** 确定后回传值 */
    crontabFill(value) {
      this.form.cronExpression = value;
    },
    /** 任务日志列表查询 */
    handleJobLog(row) {
      const jobId = row.jobId || 0;
      this.$router.push('/monitor/job-log/index/' + jobId)
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加任务";
      this.form.unit = '年'
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const jobId = row.jobId || this.ids;
      getJob(jobId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改任务";
      });
    },
    /** 提交按钮 */
    submitForm: function(status) {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.jobId != undefined) {
            editJob({ ...this.form, status: status || this.form.status }).then(response => {
              this.$modal.msgSuccess(response.msg);
              this.open = false;
              this.getList();
            });
          } else {
            addJob({ ...this.form, status: status || this.form.status }).then(response => {
              this.$modal.msgSuccess(response.msg);
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const jobIds = row.jobId || this.ids;
      this.$modal.confirm('是否确认删除定时任务编号为"' + jobIds + '"的数据项？').then(function() {
        return delJob(jobIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('monitor/job/export', {
        ...this.queryParams
      }, `job_${new Date().getTime()}.xlsx`)
    },
    // 选择间隔类型
    handleChangeUnit(type) {
      switch (type) {
        case '0':
          this.max = 74
          if (this.form.intervalNum > this.max) this.form.intervalNum = 1
          break;
        case '1':
          this.max = 3
          if (this.form.intervalNum > this.max) this.form.intervalNum = 1
          break;
        case '2':
          this.max = 11
          if (this.form.intervalNum > this.max) this.form.intervalNum = 1
          break;
        case '3':
          this.max = 3
          if (this.form.intervalNum > this.max) this.form.intervalNum = 1
          break;
        case '4':
          this.max = 31
          if (this.form.intervalNum > this.max) this.form.intervalNum = 1
          break;
        default:
          this.max = 23
          if (this.form.intervalNum > this.max) this.form.intervalNum = 1
          break;
      }
    }
  }
};
</script>
