<template>
  <div class="app-container" v-loading="loading">
    <el-row :gutter="20" class="h100">
      <el-col :span="12" class="h100 ovya" style="border-right: 1px solid #DCDFE6">
        <el-form ref="form" :model="form" :rules="rules" label-width="130px">
          <el-form-item label="地图名称" prop="name">
            <el-input v-model="form.name" :disabled="formDisabled" placeholder="请输入地图名称" />
          </el-form-item>
          <el-form-item label="X轴起始坐标" prop="startXCoordinate">
            <el-input v-model="form.startXCoordinate" :disabled="formDisabled" placeholder="请输入X轴起始坐标" />
          </el-form-item>
          <el-form-item label="Y轴起始坐标" prop="startYCoordinate">
            <el-input v-model="form.startYCoordinate" :disabled="formDisabled" placeholder="请输入Y轴起始坐标" />
          </el-form-item>
          <el-form-item label="Z轴起始坐标" prop="startZCoordinate">
            <el-input v-model="form.startZCoordinate" :disabled="formDisabled" placeholder="请输入Z轴起始坐标" />
          </el-form-item>
          <el-form-item label="创建时间" prop="createTime">
            <el-input v-model="form.createTime" disabled />
          </el-form-item>
          <el-form-item label="创建人" prop="createBy">
            <el-input
              v-model="form.createBy"
              disabled
              placeholder="请输入创建人"
              clearable
            />
          </el-form-item>
          <el-form-item label="地图文件" prop="file">
            <file-upload ref="uploadRef" :disabled="formDisabled" :inputValue.sync="form.file" :manual="true" @upload="getFileUrl" :fileType="['pgm']" :showFileList="true" />
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
          </el-form-item>
          <el-form-item label="起始坐标" prop="startCoordinate">
            <el-input v-model="form.startCoordinate" placeholder="请输入起始坐标" />
          </el-form-item> -->
          <el-form-item label="">
            <el-button type="primary" @click="submitForm" v-if="!formDisabled">保存地图</el-button>
            <el-button @click="cancel">取 消</el-button>
            <!-- <el-button type="primary" @click="publish" :disabled="!(form.status === '0' && form.id)">发布地图</el-button> -->
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="12" class="flx-center h100" style="background: #cdcdcd;text-align: center; color: #f3f3f3; font-size: 30px; letter-spacing: 5px;">
        <span v-if="!previewUrl" style="color: #e9e9e9">请上传地图文件</span>
        <img v-else :src="previewUrl" alt="请上传地图文件" width="85%" height="auto" style="max-height: 85%; max-width: 85%;" />
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { addMapInfo, updateMapInfo } from "@/api/rsp/map";
import { parsePGM } from "../../utils/pgm";
import { getMapInfo } from "../../api/rsp/map";
import { changeDate } from "../../utils";

export default {
  name: "Map",
  dicts: ['qh_map_geo_coordinate', 'qh_map_projected_coordinate'],
  data() {
    return {
      // 遮罩层
      loading: false,
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        name: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        startXCoordinate: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        startYCoordinate: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        startZCoordinate: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        createTime: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        createBy: [
          { required: true, message: "请输入", trigger: "blur" }
        ],
        file: [
          { required: true, message: "请选择文件", trigger: "change" }
        ]
      },
      formDisabled: false,
      previewUrl: '',
      publishDisabled: true,
      id: this.$route.query.id
    };
  },
  mounted() {
    if (this.id) {
      this.formDisabled = this.$route.query.r === 't'
      this.getData()
    } else {
      this.$set(this.form, 'createTime', changeDate(new Date()))
      this.$set(this.form, 'createBy', this.$store.getters.nickName)
    }
  },
  methods: {
    getData() {
      getMapInfo(this.id).then(response => {
        this.form = response.data
        // this.previewUrl = response.data.mapFileUrl
        this.previewUrl = `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${response.data.mapFileUrl}`
        this.form.file = true
      });
    },
    // 取消按钮
    cancel() {
      this.reset();
      history.back(-1)
    },
    // 表单重置
    reset() {
      this.form = {};
      this.resetForm("form");
    },
    // 发布
    publish() {},
    /** 提交按钮 */
    submitForm() {
      const keys = ['id', 'name', 'startXCoordinate', 'startYCoordinate', 'startZCoordinate', 'createTime', 'createBy', 'file']
      this.$refs["form"].validate(valid => {
        if (valid) {
          const formData = new FormData()
          Object.keys(this.form).map(key => {
            if ((key === 'file' && this.form[key] === true) || !keys.includes(key)) return
            formData.append(key, this.form[key])
          })
          if (this.form.id) {
            updateMapInfo(formData).then(response => {
              this.$modal.msgSuccess("修改成功");
              // this.open = false;
              // this.getList();
              this.$router.push({ name: 'Map' })
            });
          } else {
            formData.append('status', '0')
            addMapInfo(formData).then(response => {
              this.$modal.msgSuccess("新增成功");
              // this.open = false;
              // this.getList();
              this.$router.push({ name: 'Map' })
            });
          }
        }
      });
    },
    getFileUrl(file) {
      if (!file) {
        this.loading = false
        this.previewUrl = ''
      } else {
        parsePGM(file).then(res => {
          this.loading = false
          this.previewUrl = res
        }).catch(() => {
          this.loading = false
        })
      }
    }
  }
};
</script>
