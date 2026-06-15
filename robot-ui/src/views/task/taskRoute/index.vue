<template>
  <div class="app-container">
    <!--  查询条件-->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="路径名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入查询路径名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <!--      这里后面写map与dog的下拉框-->
      <el-form-item label="地图名称" prop="MapID">
        <el-select v-model="queryParams.MapID" placeholder="请选择对应地图" clearable :style="{width: '240px'}">
          <el-option v-for="(item, index) in MapIdOptions" :key="item.label" :label="item.label"
                     :value="item.value" ></el-option>
        </el-select>
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
    <el-table v-loading="loading" :data="RouteList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="路径编号" align="center" key="LineInfo.LineId" prop="LineInfo.LineId" v-if="columns[0].visible" />
      <el-table-column label="路径名称" align="center" key="LineInfo.LineName" prop="LineInfo.LineName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="地图名称" align="center" key="LineInfo.MapName" prop="LineInfo.MapName" v-if="columns[2].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="详细信息" align="center" key="LineInfo.LineContent" prop="LineInfo.LineContent" v-if="columns[3].visible" :show-overflow-tooltip="true" />
      <!--      <el-table-column label="创建时间" align="center" prop="createTime" v-if="columns[4].visible" width="160">-->
      <!--        <template slot-scope="scope">-->
      <!--          <span>{{ parseTime(scope.row.createTime) }}</span>-->
      <!--        </template>-->
      <!--      </el-table-column>-->
      <el-table-column
        label="操作"
        align="center"
        width="160"
        class-name="small-padding fixed-width"
      >
        <template slot-scope="scope">
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
  </div>
</template>

<script>
import { getMapIdOptions } from '@/api/robot/point'
import { getRouteList,delRoute,getLinebyId,getMapPointbyId } from '@/api/task/taskRoute'
export default {
  name: "taskRoute",
  data() {
    return {
      //查询**************************************
      MapIdOptions:[],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        LineName: undefined,
        MapID: 0
      },
      // 显示搜索条件
      showSearch: true,
      // 遮罩层
      loading: true,
      //表格****************************************
      // 非多个禁用
      multiple: true,
      // 选中数组
      ids: [],
      // 列信息
      columns: [
        { key: 0, label: `路径编号`, visible: true },
        { key: 1, label: `路径名称`, visible: true },
        { key: 2, label: `地图名称`, visible: true },
        { key: 3, label: `详细信息`, visible: true },
      ],
      // 总条数
      total: 2,
      // 用户表格数据
      RouteList: null,
    };
  },
  created() {
    this.getMapIdOptions()
    this.getList()
  },
  methods: {
    //查询***********************************************
    getMapIdOptions() {
      getMapIdOptions().then(res=>{
        this.MapIdOptions = res.data.map(item=>({
          value: item.MapID,
          label: item.MapName,
        }))
      })
    },
    //搜索按钮操作
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    //重置按钮操作
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    //按钮***********************************************
    //新增按钮操作
    handleAdd() {
      this.$router.push({ path:"/taskRoute/addRoute" })
    },
    //更新
    handleUpdate(row) {
      // console.log(row)
      this.$router.push({name: 'updateRoute',params: {id: row.LineInfo.LineId, MapID: row.LineInfo.MapID}})
    },
    //删除按钮操作
    handleDelete(row) {
      const routeIds = (this.ids && this.ids.length > 0) ? this.ids : row.LineInfo.LineId;
      this.$modal.confirm('是否确认删除路径编号为"' + routeIds + '"的数据项？').then(function() {
        //******************************
        return delRoute(routeIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    //表格***********************************************
    //查询路径列表
    getList() {
      this.loading = true;
      // console.log(this.queryParams)
      getRouteList(this.queryParams).then(res => {
          // console.log(res)
          this.RouteList = res.rows;
          this.total = res.total;
          this.loading = false;
        }
      ).catch(() => {
        this.loading = false
      });
    },
    //多选框选中数据
    handleSelectionChange(selection) {
      // console.log(selection)
      this.ids = selection.map(item => item.LineInfo.LineId);
      this.multiple = !selection.length;
      // console.log(this.ids)
    },

  }
};
</script>
