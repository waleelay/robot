<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="地图名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入地图名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="地图状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择地图状态" clearable>
          <el-option
            v-for="dict in dict.type.qh_map_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
        <el-date-picker clearable
          v-model="timeRange"
          type="daterange"
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
          @click="handleAdd()"
          v-hasPermi="['rsp:map-info:add']"
        >新建地图</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleChangeStatus(null, '0')"
          v-hasPermi="['rsp:map-info:changeStatus']"
        >批量撤销</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="single"
          @click="handleChangeStatus(null, '1')"
          v-hasPermi="['rsp:map-info:changeStatus']"
        >批量发布</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="mapList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="地图名称" align="center" prop="name" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建人" align="center" prop="createBy" />
      <el-table-column label="地图状态" align="center" prop="status">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_map_status" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" min-width="200px" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleAdd(scope.row, true)"
            v-hasPermi="['rsp:map-info:query']"
          >查看</el-button>
          <el-upload
            action=""
            ref="upload"
            accept=".json"
            :show-file-list="false"
            :auto-upload="false"
            :before-upload="beforeUpload"
            :on-change="(uploadFile) => handleFileChange(uploadFile, scope.row.id)"
            style="display: inline-block;"
            class="ml20 mr20"
          >
            <el-button
              type="text"
              icon="el-icon-upload2"
              size="mini"
              v-hasPermi="['rsp:map-info:importPoint']"
            >导入路径点</el-button>
          </el-upload>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleAdd(scope.row)"
            v-hasPermi="['rsp:map-info:edit']"
          >编辑</el-button>
          <!-- 已发布 -->
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleChangeStatus(scope.row, '0')"
            v-hasPermi="['rsp:map-info:changeStatus']"
            :disabled="scope.row.status === '0'"
          >撤销</el-button>
          <!-- 未发布 -->
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleChangeStatus(scope.row, '1')"
            v-hasPermi="['rsp:map-info:changeStatus']"
            :disabled="scope.row.status === '1'"
          >发布</el-button>
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

    <!-- 添加或修改地图对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="地图名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入地图名称" />
        </el-form-item>
        <el-form-item label="X轴起始坐标" prop="startXCoordinate">
          <el-input v-model="form.startXCoordinate" placeholder="请输入X轴起始坐标" />
        </el-form-item>
        <el-form-item label="Y轴起始坐标" prop="startYCoordinate">
          <el-input v-model="form.startYCoordinate" placeholder="请输入Y轴起始坐标" />
        </el-form-item>
        <!-- <el-form-item label="起始坐标" prop="startCoordinate">
          <el-input v-model="form.startCoordinate" placeholder="请输入起始坐标" />
        </el-form-item> -->
        <el-form-item label="地图文件二进制数据" prop="data">
          <file-upload v-model="form.data"/>
        </el-form-item>
        <!-- <el-form-item label="地理坐标系统" prop="geographicCoordinateSystem">
          <el-select v-model="form.geographicCoordinateSystem" placeholder="请选择地理坐标系统">
            <el-option
              v-for="dict in dict.type.qh_map_geo_coordinate"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="投影坐标系统" prop="projectionCoordinateSystem">
          <el-select v-model="form.projectionCoordinateSystem" placeholder="请选择投影坐标系统">
            <el-option
              v-for="dict in dict.type.qh_map_projected_coordinate"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item> -->
        <el-form-item label="地图状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择地图状态">
            <el-option
              v-for="dict in dict.type.qh_map_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- <img :src="src" style="width: 500px; height: 500px;" /> -->
  </div>
</template>

<script>
import { listMapInfo, getMapInfo, delMapInfo, addMapInfo, updateMapInfo } from "@/api/rsp/map";
import { changeMapStatus, importPoint } from "../../api/rsp/map";
import { changeDate } from "../../utils";

export default {
  name: "Map",
  dicts: ['qh_map_geo_coordinate', 'qh_map_projected_coordinate', 'qh_map_status'],
  data() {
    return {
      // src: require('D:/resources/robot/maps/李磊的地图.jpg'),
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 选中数组
      selectRows: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 地图表格数据
      mapList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        createBy: null,
        status: null
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
      inputPoints: []
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询地图列表 */
    getList() {
      this.loading = true;
      listMapInfo(this.queryParams).then(response => {
        this.mapList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        id: null,
        name: null,
        startXCoordinate: null,
        startYCoordinate: null,
        startCoordinate: null,
        createTime: null,
        createBy: null,
        data: null,
        geographicCoordinateSystem: null,
        projectionCoordinateSystem: null,
        status: null
      };
      this.resetForm("form");
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
      this.selectRows = selection || []
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd(row, readOnly) {
      // this.reset();
      // this.open = true;
      // this.title = "添加地图";
      this.$router.push({ name: 'mapAdd', query: row ? { id: row.id, r: readOnly ? 't' : 'f' } : {} })
    },
    /* 发布、撤销 */
    handleChangeStatus(row, status) {
      if (!row && !this.ids.length) {
        this.$message.warning('请选择至少一条数据')
        return
      }
      const ids = row ? row.id : this.ids
      changeMapStatus({ ids, status }).then(res => {
        this.$message.success(status ? '发布成功' : '撤销成功');
        this.getList();
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getMapInfo(id).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改地图";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.id) {
            updateMapInfo(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addMapInfo(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const ids = row.id || this.ids;
      this.$modal.confirm('是否确认删除地图编号为"' + ids + '"的数据项？').then(function() {
        return delMapInfo(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('rsp/map/export', {
        ...this.queryParams
      }, `map_${new Date().getTime()}.xlsx`)
    },

    //将json转为对象数组了
    handleFileChange(file, id) {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const jsonArray = JSON.parse(e.target.result);
          if (Array.isArray(jsonArray)) {
            this.inputPoints = jsonArray
            this.handleJson(id)
          } else {
            this.inputPoints = [];
            this.$message.msgError('文件内容不是一个有效的对象数组')
          }
        } catch (error) {
          this.inputPoints = [];
          this.$message.msgError('解析 JSON 文件时出错')
        }
      };
      reader.readAsText(file.raw);
    },
    //获取到了json对象数组后
    async handleJson(id) {
      importPoint({ inputPoints: this.inputPoints, id }).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
          this.getList()
        }
      })
    },
    //点击导入按钮
    beforeUpload(file) {
      // Do something before upload
      return true; // Return false to prevent upload
    },
  }
};
</script>
