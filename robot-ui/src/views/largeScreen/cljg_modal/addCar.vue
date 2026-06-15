<template>
  <el-dialog
    class="bi-form-dialog-wrapper flx-align-center"
    v-dialogDrag
    width="597px"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
  >
    <template slot="title">
      <div class="bi-dialog-header">
        <div class="title">新增预约车辆</div>
      </div>
    </template>
    <el-form ref="formRef" :model="form" class="custom-form pt5" label-position="right" label-width="85px">
      <el-form-item prop="driverFile" label="" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
        <el-upload
          ref="uploadRef"
          class="avatar-uploader"
          action="#"
          :accept="acceptType"
          :show-file-list="false"
          list-type="picture-card"
          :auto-upload="false"
          :on-change="handleFileChange"
          v-model="form.driverFile"
          >
          <!-- :before-upload="beforeUpload" -->
          <!-- :on-success="uploadSuccess" -->
          <!-- <i class="el-icon-plus"></i> -->
          <img v-if="imageUrl" :src="imageUrl" class="avatar">
          <div class="flex-column flx-center upload-text" v-else>
            <i class="el-icon-plus avatar-uploader-icon" style="font-size: 24px;"></i>
            <span class="mt10">上传清晰正面单人照</span>
          </div>
        </el-upload>
      </el-form-item>
      <el-form-item prop="driverName" label="司机姓名" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
        <el-input
          v-model="form.driverName"
          type="text"
          auto-complete="off"
          placeholder="请输入"
        >
        </el-input>
      </el-form-item>
      <el-form-item prop="licensePlate" label="车牌号码" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
        <el-input
          v-model="form.licensePlate"
          type="text"
          auto-complete="off"
          placeholder="请输入"
        >
        </el-input>
      </el-form-item>
      <el-form-item label="货物类型" prop="goodsType" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
        <!-- <el-select v-model="form.goodsType" placeholder="请选择" class="w100" popper-class="custom-select-popper1">
          <el-option
            v-for="dict in types"
            :key="dict.id"
            :label="dict.name"
            :value="dict.id"
          >
          </el-option>
        </el-select> -->
        <el-input
          v-model="form.goodsType"
          type="text"
          auto-complete="off"
          placeholder="请输入"
        ></el-input>
      </el-form-item>
      <el-form-item
        label="预约日期"
        prop="reservationTime"
        :rules="{ required: true, message: '不允许为空', trigger: 'blur' }"
      >
        <el-date-picker
          v-model="form.reservationTime"
          type="datetime"
          placeholder="选择日期时间"
          default-time="12:00:00"
          popper-class="custom-date-picker-popper"
          style="width: 100%"
          format="yyyy-M-d HH:mm:ss"
          value-format="yyyy-M-d HH:mm:ss"
        >
        </el-date-picker>
      </el-form-item>
    </el-form>
    <template slot="footer">
      <el-button tt="modal" @click="dialogVisible = false">取 消</el-button>
      <el-button tt="modal" @click="submitForm()">确定</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { addCar } from "../../../api/bi";
export default {
  name: 'executeCar',
  data() {
    return {
      types: [
        {id: 1, name: '商品'},
        {id: 2, name: '液体'},
        {id: 3, name: '固体'},
        {id: 4, name: '其他'},
      ],
      // 表单参数
      form: {},
      disabled: false,
      dialogVisible: false,
      acceptType: '.png,.jpg,.jpeg',
      imageUrl: ''
    }
  },
  methods: {
    open() {
      this.form = {};
      this.resetForm('formRef');
      this.dialogVisible = true
    },
    beforeUpload(file) {
      return false
    },
    // 处理文件选择变化
    handleFileChange(file, fileList) {
      // 检查文件类型
      if (!file.raw.type.startsWith('image/')) {
        this.$message.error('请选择图片文件！');
        return;
      }
      
      // 检查文件大小（5MB限制）
      if (file.raw.size > 5 * 1024 * 1024) {
        this.$message.error('图片大小不能超过5MB！');
        return;
      }
      
      // 创建文件读取器
      const reader = new FileReader();
      
      // 读取完成后的回调
      reader.onload = (e) => {
        // 将图片数据添加到预览列表
        // this.previewImages.push({
        //   name: file.name,
        //   url: e.target.result,
        //   file: file.raw
        // });
        this.imageUrl = e.target.result;
      };
      
      // 读取文件内容
      reader.readAsDataURL(file.raw);
      this.form.driverFile = file;
      console.log(file);
      
    },
    beforeRemove() {},
    uploadSuccess(res, file){
      // this.form.driverPhotoUrl = URL.createObjectURL(file.raw);
      // this.imageUrl = URL.createObjectURL(file.raw);
    },
    uploadError() {},
    submitForm() {
      this.$refs.formRef.validate(valid => {
        if (valid) {
          const formData = new FormData();
          Object.keys(this.form).map(key => {
            formData.append(key, key === 'driverFile' ? this.form[key].raw : this.form[key])
          })
          addCar(formData).then(res => {
            if (res.code === 200) {
              this.$modal.msgSuccess(res.msg);
              this.dialogVisible = false
            } else {
              this.$modal.msgSuccess(res.msg);
            }
          });
        }
      })
    },
  }
}
</script>

<style lang="scss" scoped>
@import "./form.scss";
::v-deep .el-upload {
  &.el-upload--picture-card {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 165px;
    height: 219px;
    border-radius: 4px;
    border: 1px solid #159AFF;
    background: #001D46;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    .upload-text {
      color: #159AFF;
      text-align: center;
      font-family: "Alibaba PuHuiTi";
      font-size: 14px;
      font-style: normal;
      font-weight: 400;
      line-height: 20px; /* 142.857% */
      letter-spacing: 1.488px;
      i {
        color: #159AFF;
      }
    }
  }
}
  .avatar-uploader .el-upload:hover {
    border-color: #409EFF;
  }
  // .avatar-uploader-icon {
  //   font-size: 28px;
  //   color: #8c939d;
  //   width: 178px;
  //   height: 178px;
  //   line-height: 178px;
  //   text-align: center;
  // }
  .avatar {
    width: 178px;
    height: 178px;
    display: block;
  }
</style>
