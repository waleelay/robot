<template>
  <div class="app-container" v-loading="loading">
    <el-divider class="mb0"></el-divider>
    <el-form ref="form" :model="form" :rules="rules" label-width="130px" :disabled="formDisabled">
      <el-row :gutter="20" class="p20">
        <el-col :span="12" class="pb100" style="border-right: 1px solid #DCDFE6;">
          <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">无人设备信息</div>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="装备名称" prop="name">
                <el-input v-model="form.name" placeholder="请输入装备名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="装备类型" prop="type">
                <el-select v-model="form.type" placeholder="请选择装备类型" class="w100">
                  <el-option
                    v-for="dict in dict.type.qh_motion_equipment_type"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  ></el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="厂商名称" prop="manufacturer">
                <el-select v-model="form.manufacturer" placeholder="请选择厂商名称" class="w100" @change="handleChangeManufacturer">
                  <el-option
                    v-for="dict in dict.type.qh_motion_equipment_manufacturer"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  ></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="型号" prop="modelNumber">
              <el-select v-model="form.modelNumber" placeholder="请选择型号" class="w100">
                <!-- <el-option
                  v-for="dict in dict.type.qh_motion_equipment_model_number"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                ></el-option> -->
                <el-option
                  v-for="dict in equipmentModelNumberList"
                  :key="dict.dictValue"
                  :label="dict.dictLabel"
                  :value="dict.dictValue"
                ></el-option>
              </el-select>
            </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="安装位置" prop="installationPosition">
            <el-input v-model="form.installationPosition" placeholder="请输入安装位置" />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="服务地址" prop="endpoint">
                <el-input v-model="form.endpoint" placeholder="请输入服务地址" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="SIM卡序列号" prop="simCardSerial">
                <el-input v-model="form.simCardSerial" placeholder="请输入SIM卡序列号" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="备注信息" prop="remarks">
            <el-input v-model="form.remarks" type="textarea" placeholder="请输入内容" />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="创建时间" prop="createTime">
                <!-- <el-date-picker clearable
                  v-model="form.createTime"
                  type="datetime"
                  placeholder="请选择创建时间"
                /> -->
                <el-input
                  disabled
                  v-model="form.createTime"
                  placeholder="请输入创建人"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="创建人" prop="createBy">
                <el-input
                  disabled
                  v-model="form.createBy"
                  placeholder="请输入创建人"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>
          <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">上装设备信息</div>
          <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
              <el-button type="primary" icon="el-icon-plus" size="mini" @click="handleAddQhMountedEquipment">添加</el-button>
            </el-col>
            <el-col :span="1.5">
              <el-button type="danger" icon="el-icon-delete" size="mini" @click="handleDeleteQhMountedEquipment">删除</el-button>
            </el-col>
            <el-col :span="1.5">
              <span style="color: red;" v-if="hasTableEmpty()">请填写完整的上装设备信息</span>
            </el-col>
          </el-row>
          <el-table :data="qhMountedEquipmentList" :row-class-name="rowQhMountedEquipmentIndex" @selection-change="handleQhMountedEquipmentSelectionChange" ref="qhMountedEquipment">
            <el-table-column type="selection" width="50" align="center" />
            <el-table-column label="序号" align="center" prop="index" width="50"/>
            <el-table-column label="设备类型" prop="type" min-width="150">
              <template slot-scope="scope">
                <el-select v-model="scope.row.type" placeholder="请选择设备类型" @change="e => handleChangeMountedType(e, scope.$index)">
                  <el-option
                    v-for="dict in dict.type.qh_mounted_equipment_type"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  ></el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="设备型号" prop="modelNumber" min-width="150">
              <template slot-scope="scope">
                <el-select v-model="scope.row.modelNumber" placeholder="请选择设备型号">
                  <el-option
                    v-for="dict in mountedModelNumberList[scope.$index]"
                    :key="dict.dictValue"
                    :label="dict.dictLabel"
                    :value="dict.dictValue"
                  ></el-option>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="IP地址" prop="ipAddress" min-width="150">
              <template slot-scope="scope">
                <el-input v-model="scope.row.ipAddress" placeholder="请输入IP地址" />
              </template>
            </el-table-column>
            <!-- rtsp://用户名:密码@IP地址:端口/Streaming/Channels/201 -->
            <!-- <el-table-column label="视频地址" prop="videoUrl" min-width="150">
              <template slot-scope="scope">
                <el-tooltip effect="dark" content="rtsp://用户名:密码@IP地址:端口/Streaming/Channels/101">
                  <el-input v-model="scope.row.videoUrl" placeholder="请输入视频地址" :disabled="scope.row.type !== 'video'" />
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="红外地址" prop="nirUrl" min-width="150">
              <template slot-scope="scope">
                <el-tooltip effect="dark" content="rtsp://用户名:密码@IP地址:端口/Streaming/Channels/201">
                  <el-input v-model="scope.row.nirUrl" placeholder="请输入红外地址" :disabled="scope.row.type !== 'video'" />
                </el-tooltip>
              </template>
            </el-table-column> -->
          </el-table>
        </el-col>
        <el-col :span="12" class="h100">
          <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">装备上图信息</div>
          <el-form-item label="选择地图" prop="mapInfoId">
            <el-select v-model="form.mapInfoId" placeholder="请选择地图" clearable @change="handleChangeMap">
              <el-option
                v-for="dict in mapList"
                :key="dict.id"
                :label="dict.name"
                :value="dict.id"
              />
            </el-select>
          </el-form-item>
          <img v-if="form.mapInfoId" :src="previewUrl" alt="请选择地图" width="85%" height="auto" style="max-height: 85%; max-width: 85%;" />
        </el-col>
      </el-row>
    </el-form>
    <el-divider class="mt0" />
    <div class="mt20" style="text-align: center;">
      <!-- <el-button type="primary" @click="submitForm">测试</el-button> -->
      <el-button type="primary" @click="submitForm">保存装备</el-button>
      <el-button @click="cancel">取 消</el-button>
      <!-- <el-button type="primary" @click="publish">发布装备</el-button> -->
    </div>
  </div>
</template>

<script>
import { addMap, updateMap } from "@/api/rsp/map";
import { parsePGM } from "../../utils/pgm";
import { listMapInfo } from "../../api/rsp/map";
import { getMountedModelByType, getEquipmentModelByType } from "../../api/rsp/equipment";
import { addMotion, getMotion, updateMotion } from "../../api/rsp/motion";
import { changeDate } from "../../utils";
export default {
  name: "Map",
  dicts: ['qh_motion_equipment_type', 'qh_motion_equipment_state', 'qh_motion_equipment_manufacturer', 'qh_motion_equipment_model_number', 'qh_mounted_equipment_type'],
  data() {
    return {
      // 遮罩层
      loading: false,
      // 子表选中数据
      checkedQhMountedEquipment: [],
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        name: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        type: [
          { required: true, message: "请选择", trigger: "change" }
        ],
        manufacturer: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        modelNumber: [
          { required: true, message: "请选择", trigger: "change" }
        ],
        createTime: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        createBy: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        endpoint: [
          { required: true, message: "请输入", trigger: "blur" },
          { pattern: '^(?=.{0,100}$)(((([01]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))[.]){3}(([0-1]?[0-9]{1,2})|(2[0-4][0-9])|(25[0-5]))):(([1-9][0-9]{0,3})|([1-5][0-9]{4})|(6[0-4][0-9]{3})|(65[0-4][0-9]{2})|(655[0-2][0-9])|(6553[0-5]))$', message: "请输入IP:PORT的格式", trigger: "blur" }
        ],
        mapInfoId: [
          { required: true, message: "请选择", trigger: "change" }
        ],
      },
      formDisabled: false,
      previewUrl: '',
      // 上装设备信息表格数据
      qhMountedEquipmentList: [],
      mapList: [],
      mountedModelNumberList: [],
      // 装备型号
      equipmentModelNumberList: []
    };
  },
  created() {
    listMapInfo({ status: '1' }).then(response => {
      this.mapList = response.rows;
    });
  },
  mounted() {
    listMapInfo({ status: '1' }).then(response => {
      this.mapList = response.rows;
      if (this.$route.query.id) {
        this.formDisabled = this.$route.query.r === 't'
        getMotion(this.$route.query.id).then(response => {
          this.form = response.data
          if (response.data.manufacturer) {
            getEquipmentModelByType(response.data.manufacturer).then(res => {
              this.equipmentModelNumberList = [...res.data]
            })
          }
          this.qhMountedEquipmentList = response.data.qhMountedEquipmentList
          this.qhMountedEquipmentList.map((item, index) => {
            this.handleChangeMountedType(item.type, index, item.modelNumber)
          })
          this.handleChangeMap(this.form.mapInfoId)
        });
      } else {
        this.$set(this.form, 'createTime', changeDate(new Date()))
        this.$set(this.form, 'createBy', this.$store.getters.nickName)
      }
    });
  },
  methods: {
    handleChangeManufacturer(e) {
      this.$set(this.form, 'modelNumber', undefined)
      getEquipmentModelByType(e).then(res => {
        this.equipmentModelNumberList = [...res.data]
      })
    },
    handleChangeMap(e) {
      if (e === '') {
        this.mapFileUrl = ''
      } else {
        const mapFileUrl = this.mapList.filter(item => item.id === e)[0].mapFileUrl
        this.previewUrl = `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${mapFileUrl}`
      }
    },
    handleChangeMountedType(e, index, modelNumber) {
      this.qhMountedEquipmentList[index].modelNumber = modelNumber || ''
      getMountedModelByType(e).then(res => {
        this.mountedModelNumberList[index] = []
        this.$set(this.mountedModelNumberList, index, [...res.data])
      })
    },
    // 取消按钮
    cancel() {
      this.reset();
      history.back(-1)
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
        endpoint: null,
        simCardSerial: null,
        remarks: null,
        createTime: null,
        createBy: null,
        state: null,
        mapInfoId: null,
        sceneId: null,
        totalMileage: null
      };
      this.qhMountedEquipmentList = [];
      this.mountedModelNumberList = []
      this.previewUrl = ''
      this.resetForm("form");
    },
    // 发布
    publish() {},
    // 表格是否有空数据
    hasTableEmpty() {
      const types = [undefined, null, '']
      const arr = this.qhMountedEquipmentList.filter(item => {
        const { type, modelNumber, ipAddress } = item
        return (types.includes(type) || types.includes(modelNumber) || types.includes(ipAddress))
      })
      return arr.length > 0
    },
    /** 提交按钮 */
    submitForm() {
      if (this.hasTableEmpty()) {
        this.$message.warning('请填写完整的上装设备信息')
        return
      }
      this.$refs["form"].validate(valid => {
        if (valid) {
          // TODO
          if (this.form.id) {
            updateMotion({...this.form, qhMountedEquipmentList: this.qhMountedEquipmentList }).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.$router.push({ name: 'Equipment' })
            });
          } else {
            addMotion({...this.form, qhMountedEquipmentList: this.qhMountedEquipmentList }).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.$router.push({ name: 'Equipment' })
            });
          }
        }
      });
    },
    getFileUrl(file) {
      parsePGM(file).then(res => {
        this.loading = false
        this.previewUrl = res
      }).catch(() => {
        this.loading = false
      })
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
      this.mountedModelNumberList.push([]);
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
        this.mountedModelNumberList = this.mountedModelNumberList.filter(function(item, index) {
          return checkedQhMountedEquipment.indexOf(index + 1) == -1
        });
      }
    },
    /** 复选框选中数据 */
    handleQhMountedEquipmentSelectionChange(selection) {
      this.checkedQhMountedEquipment = selection.map(item => item.index)
    },
  }
}
</script>