<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="装备名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入装备名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="装备状态" prop="state">
        <el-select v-model="queryParams.state" placeholder="请选择装备状态" clearable style="width: 160px;">
          <el-option
            v-for="dict in dict.type.qh_motion_equipment_state"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="装备类型" prop="type">
        <el-select v-model="queryParams.type" placeholder="请选择装备类型" clearable style="width: 160px;">
          <el-option
            v-for="dict in dict.type.qh_motion_equipment_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="厂商名称" prop="manufacturer">
        <el-select v-model="queryParams.manufacturer" placeholder="请选择厂商名称" clearable style="width: 160px;" @change="handleChangeManufacturer">
          <el-option
            v-for="dict in dict.type.qh_motion_equipment_manufacturer"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="型号" prop="modelNumber">
        <el-select v-model="queryParams.modelNumber" placeholder="请选择型号" clearable style="width: 160px;">
          <!-- <el-option
            v-for="dict in dict.type.qh_motion_equipment_model_number"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          /> -->
          <el-option
            v-for="dict in equipmentModelNumberList"
            :key="dict.dictValue"
            :label="dict.dictLabel"
            :value="dict.dictValue"
          ></el-option>
        </el-select>
      </el-form-item>
      <!-- <el-form-item label="上装设备" prop="modelNumber" label-width="80px">
        <el-select v-model="queryParams.modelNumber" placeholder="请选择型号" clearable style="width: 160px;">
          <el-option
            v-for="dict in dict.type.qh_mounted_equipment_type"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
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
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['rsp:motion:add']"
        >手动接入</el-button>
      </el-col>
<!--      <el-col :span="1.5">-->
<!--        <el-button-->
<!--          type="primary"-->
<!--          plain-->
<!--          icon="el-icon-plus"-->
<!--          size="mini"-->
<!--          @click="handleAdd"-->
<!--          v-hasPermi="['rsp:motion:add']"-->
<!--        >自动接入</el-button>-->
<!--      </el-col>-->
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['rsp:motion:remove']"
        >批量删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          :disabled="multiple"
          @click="handleChangeStatus('-1')"
          v-hasPermi="['rsp:motion:changeState']"
        >批量撤销</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-check"
          size="mini"
          :disabled="multiple"
          @click="handleChangeStatus('1')"
          v-hasPermi="['rsp:motion:changeState']"
        >批量发布</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="motionList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="装备名称" align="center" prop="name" />
      <el-table-column label="装备类型" align="center" prop="type" width="100">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_motion_equipment_type" :value="scope.row.type"/>
        </template>
      </el-table-column>
      <el-table-column label="厂商名称" align="center" prop="manufacturer" width="120">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_motion_equipment_manufacturer" :value="scope.row.manufacturer"/>
        </template>
      </el-table-column>
      <el-table-column label="型号" align="center" prop="modelNumber" width="120">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_motion_equipment_model_number" :value="scope.row.modelNumber"/>
        </template>
      </el-table-column>
      <el-table-column label="服务地址" align="center" prop="endpoint" width="160" />
      <el-table-column label="安装位置" align="center" prop="installationPosition" width="120" />
      <el-table-column label="上装设备" align="center" min-width="150">
        <template slot-scope="scope">
          <dict-tag v-for="(item, index) in scope.row.qhMountedEquipmentList" :options="dict.type.qh_mounted_equipment_type" :value="item.type" style="display: inline-block;" :style="{ marginLeft: index > 0 ? '10px' : 0 }" />
        </template>
      </el-table-column>
      <el-table-column label="接入时间" align="center" prop="createTime" width="160" />
      <el-table-column label="装备状态" align="center" prop="state" width="80">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.qh_motion_equipment_state" :value="scope.row.state"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" min-width="200" class-name="small-padding fixed-width">
        <!--
          未发布：查看 编辑 删除 发布
          运行中：查看 编辑 (不可点：删除 撤销)
          空闲中： 查看 编辑 (不可点：删除) 撤销
          离线： 查看 编辑 删除 撤销
        -->
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleAdd(scope.row, true)"
          >查看</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleAdd(scope.row)"
            v-hasPermi="['rsp:motion:edit']"
          >编辑</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['rsp:motion:remove']"
            :disabled="scope.row.state === '-1' || scope.row.state !== '0'"
            >删除</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleChangeStatus('1', scope.row)"
            v-hasPermi="['rsp:path:remove']"
            v-if="scope.row.state === '0'"
          >发布</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleChangeStatus('1', scope.row)"
            v-hasPermi="['rsp:path:remove']"
            v-if="scope.row.state === '-1'"
          >生效</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleChangeStatus('-1', scope.row)"
            v-hasPermi="['rsp:path:remove']"
            v-if="scope.row.state === '1'"
          >撤销</el-button>
            <!-- :disabled="scope.row.state !== '3' || scope.row.state !== '4'" -->
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

    <!-- 添加或修改无人装备信息对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="装备名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入装备名称" />
        </el-form-item>
        <el-form-item label="装备类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择装备类型">
            <el-option
              v-for="dict in dict.type.qh_motion_equipment_state"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="厂商名称" prop="manufacturer">
          <el-select v-model="form.manufacturer" placeholder="请选择厂商名称">
            <el-option
              v-for="dict in dict.type.qh_motion_equipment_manufacturer"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="型号" prop="modelNumber">
          <el-select v-model="form.modelNumber" placeholder="请选择型号">
            <el-option
              v-for="dict in dict.type.qh_motion_equipment_model_number"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="安装位置" prop="installationPosition">
          <el-input v-model="form.installationPosition" placeholder="请输入安装位置" />
        </el-form-item>
        <el-form-item label="IP地址" prop="ipAddress">
          <el-input v-model="form.ipAddress" placeholder="请输入IP地址" />
        </el-form-item>
        <el-form-item label="SIM卡序列号" prop="simCardSerial">
          <el-input v-model="form.simCardSerial" placeholder="请输入SIM卡序列号" />
        </el-form-item>
        <el-form-item label="备注信息" prop="remarks">
          <el-input v-model="form.remarks" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="装备状态" prop="state">
          <el-input v-model="form.state" placeholder="请输入装备状态" />
        </el-form-item>
        <el-form-item label="关联的地图ID" prop="mapId">
          <el-input v-model="form.mapId" placeholder="请输入关联的地图ID" />
        </el-form-item>
        <el-form-item label="关联的场景ID" prop="sceneId">
          <el-input v-model="form.sceneId" placeholder="请输入关联的场景ID" />
        </el-form-item>
        <el-form-item label="累积里程" prop="totalMileage">
          <el-input v-model="form.totalMileage" placeholder="请输入累积里程" />
        </el-form-item>
        <el-divider content-position="center">上装设备信息信息</el-divider>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button type="primary" icon="el-icon-plus" size="mini" @click="handleAddQhMountedEquipment">添加</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="danger" icon="el-icon-delete" size="mini" @click="handleDeleteQhMountedEquipment">删除</el-button>
          </el-col>
        </el-row>
        <el-table :data="qhMountedEquipmentList" :row-class-name="rowQhMountedEquipmentIndex" @selection-change="handleQhMountedEquipmentSelectionChange" ref="qhMountedEquipment">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="序号" align="center" prop="index" width="50"/>
          <el-table-column label="设备类型" prop="type" width="150">
            <template slot-scope="scope">
              <el-select v-model="scope.row.type" placeholder="请选择设备类型">
                <el-option
                  v-for="dict in dict.type.qh_mounted_equipment_type"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                ></el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="设备型号" prop="modelNumber" width="150">
            <template slot-scope="scope">
              <el-select v-model="scope.row.modelNumber" placeholder="请选择设备型号">
                <el-option
                  v-for="dict in dict.type.qh_motion_equipment_model_number"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                ></el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="IP地址" prop="ipAddress" width="150">
            <template slot-scope="scope">
              <el-input v-model="scope.row.ipAddress" placeholder="请输入IP地址" />
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMotion, delMotion, addMotion, updateMotion } from "@/api/rsp/motion";
import { changeMotionStatus, listMotion, getEquipmentModelByType } from "../../api/rsp/equipment";

export default {
  name: "Motion",
  dicts: ['qh_motion_equipment_state', 'qh_motion_equipment_manufacturer', 'qh_motion_equipment_model_number', 'qh_mounted_equipment_type', 'qh_motion_equipment_type'],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      selectRows: [],
      // 子表选中数据
      checkedQhMountedEquipment: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 无人装备信息表格数据
      motionList: [],
      // 上装设备信息表格数据
      qhMountedEquipmentList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        name: null,
        type: null,
        manufacturer: null,
        modelNumber: null,
        installationPosition: null,
        ipAddress: null,
        simCardSerial: null,
        remarks: null,
        state: null,
        mapId: null,
        sceneId: null,
        totalMileage: null
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
      },
      equipmentModelNumberList: []
    };
  },
  created() {
    this.getList();
  },
  methods: {
    handleChangeManufacturer(e) {
      this.$set(this.queryParams, 'modelNumber', undefined)
      getEquipmentModelByType(e).then(res => {
        this.equipmentModelNumberList = [...res.data]
      })
    },
    /** 查询无人装备信息列表 */
    getList() {
      this.loading = true;
      listMotion(this.queryParams).then(response => {
        this.motionList = response.rows;
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
        type: null,
        manufacturer: null,
        modelNumber: null,
        installationPosition: null,
        ipAddress: null,
        simCardSerial: null,
        remarks: null,
        createTime: null,
        createBy: null,
        state: null,
        mapId: null,
        sceneId: null,
        totalMileage: null
      };
      this.qhMountedEquipmentList = [];
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd(row, readOnly) {
      const query = {}
      if (row) {
        query.id = row.id
        query.r = readOnly ? 't' : 'f'
      }
      this.$router.push({ name: 'equipemtAdd', query })
      // this.reset();
      // this.open = true;
      // this.title = "添加无人装备信息";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const id = row.id || this.ids
      getMotion(id).then(response => {
        this.form = response.data;
        this.qhMountedEquipmentList = response.data.qhMountedEquipmentList;
        this.open = true;
        this.title = "修改无人装备信息";
      });
    },
    /* 发布、撤销 */
    handleChangeStatus(state, row) {
      if (!row && !this.ids.length) {
        this.$message.warning('请选择至少一条数据')
        return
      }
      const ids = row ? row.id : this.ids
      changeMotionStatus({ ids, state }).then(res => {
        this.$message.success(status ? '发布成功' : '撤销成功');
        this.getList();
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.form.qhMountedEquipmentList = this.qhMountedEquipmentList;
          if (this.form.id) {
            updateMotion(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addMotion(this.form).then(response => {
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
      this.$modal.confirm('是否确认删除无人装备信息编号为"' + ids + '"的数据项？').then(function() {
        return delMotion(ids);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
	  /** 上装设备信息序号 */
    rowQhMountedEquipmentIndex({ row, rowIndex }) {
      row.index = rowIndex + 1;
    },
    /** 上装设备信息添加按钮操作 */
    handleAddQhMountedEquipment() {
      let obj = {};
      obj.type = "";
      obj.modelNumber = "";
      obj.ipAddress = "";
      this.qhMountedEquipmentList.push(obj);
    },
    /** 上装设备信息删除按钮操作 */
    handleDeleteQhMountedEquipment() {
      if (this.checkedQhMountedEquipment.length == 0) {
        this.$modal.msgError("请先选择要删除的上装设备信息数据");
      } else {
        const qhMountedEquipmentList = this.qhMountedEquipmentList;
        const checkedQhMountedEquipment = this.checkedQhMountedEquipment;
        this.qhMountedEquipmentList = qhMountedEquipmentList.filter(function(item) {
          return checkedQhMountedEquipment.indexOf(item.index) == -1
        });
      }
    },
    /** 复选框选中数据 */
    handleQhMountedEquipmentSelectionChange(selection) {
      this.checkedQhMountedEquipment = selection.map(item => item.index)
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('rsp/motion/export', {
        ...this.queryParams
      }, `motion_${new Date().getTime()}.xlsx`)
    }
  }
};
</script>
