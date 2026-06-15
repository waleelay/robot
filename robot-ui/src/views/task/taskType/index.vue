<template>
  <div class="app-container">
    <!--  查询条件-->
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="运动名称" prop="ActionId">
        <el-input v-model="queryParams.PointName" placeholder="请输入点名称" clearable :style="{width: '240px'}">
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <!--  按钮(新增，删除)-->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>
    <!--表格-->
    <el-table v-loading="loading" :data="actionInfoDate" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="运动名称" align="center" key="ActionName" prop="ActionName" v-if="columns[0].visible" />
      <el-table-column label="运动URL" align="center" key="ActionURL" prop="ActionURL" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="运动参数" align="center" key="ActionParams" prop="ActionParams" v-if="columns[2].visible" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <span>{{ scope.row.ActionParams.map(item => item.paramName).join(', ') }}</span>
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        align="center"
        width="160"
        class-name="small-padding fixed-width"
      >
        <template slot-scope="scope" v-if="scope.row.userId !== 1">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!--分页-->
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
    <!--    添加或修改犯人信息的对话框-->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-row :gutter="15">
        <el-form ref="actionInfoRef" :model="actionInfoDate" :rules="rules" size="medium" label-width="100px">
          <el-col :span="12">
            <el-form-item label="运动名称" prop="ActionName">
              <el-input v-model="actionInfoDate.ActionName" placeholder="请输入运动名称" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="运动URL" prop="ActionUrl">
              <el-input v-model="actionInfoDate.ActionUrl" placeholder="请输入运动URL" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="参数配置" prop="ActionParams">
              <el-button type="success" circle>+</el-button>
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getActionList,addAction,delAction,updateAction } from '@/api/task/tasktype'
export default {
  name: "taskType",
  data() {
    return {
//  查询部分*************************
      showSearch: true,
      queryParams: {
        pageNum: 1,
        pageSize: 5,
        ActionName: "",
      },
//表格部分************************************
      multiple: true,
      loading: true,
      actionList: null,
      //总数
      total: 2,
      //选择数据项
      ids: [],
      // 列信息
      columns: [
        { key: 0, label: `运动名称`, visible: true },
        { key: 1, label: `运动url`, visible: true },
        { key: 2, label: `参数`, visible: true },
      ],
      //弹窗部分****************************************
      open: false,
      title: '',
      //新增对象
      actionInfoDate: {
        ActionId: undefined,
        ActionName: undefined,
        ActionUrl:undefined,
        ActionParams: [],
      }
    };
  },
  created() {

  },
  methods: {
    //查询--------------------------------------------
    //搜索区点击搜索后
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    //点击重置后
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    //按钮（新增、删除、导入、导出）------------------------
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "增加运动类型"
    },
    //点击删除按钮
    handleDelete(row) {
      const ActionIds = row.PointId || this.ids;
      // console.log(ActionIds)
      this.$modal.confirm('是否确认删除点位编号为"' + ActionIds + '"的数据项？').then(function() {
        return delAction(ActionIds)
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    //表格-----------------------------------------------
    // /** 查询用户列表 */
    getList() {
      getActionList(this.queryParams).then(res => {
          this.actionList = res.rows;
          this.total = res.total;
          this.loading = false;
        }
      );
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.PointId);
      this.multiple = !selection.length;
    },
    //点击修改按钮
    handleUpdate(row) {
      this.reset()
      this.open = true
      this.title = '修改运动配置'
      this.actionInfoDate.ActionName = row.ActionName
      this.actionInfoDate.ActionId = row.ActionId
      this.actionInfoDate.ActionUrl = row.ActionUrl
      this.actionInfoDate.ActionParams = row.ActionParams
      // console.log(this.actionInfoDate)
    },
    //弹窗-------------------------------------------------

    // 取消按钮

    // 表单重置
    reset() {
      this.actionInfoDate = {
        ActionId: undefined,
        ActionName: undefined,
        ActionUrl: undefined,
        ActionParams: [],
      }
      this.resetForm("actionInfoRef");
    },
  }
};
</script>
