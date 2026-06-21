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
        <div class="title">下发执行</div>
      </div>
    </template>
    <!-- <el-form ref="formRef" :model="form" :rules="[]" class="custom-form pt5"> -->
    <el-form ref="formRef" :model="form" class="custom-form pt5 vertical-form">
      <div class="title1 mb10">执行设备</div>
      <el-form-item label="执行设备" prop="equipmentIds" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
        <el-select v-model="form.equipmentIds" placeholder="请选择" multiple class="w100" popper-class="custom-select-popper1" @change="handleChangeEquipment" @remove-tag="handleRemoveTag">
          <el-option
            v-for="dict in equipmentList"
            :key="dict.id"
            :label="dict.name"
            :value="dict.id"
          >
          </el-option>
        </el-select>
      </el-form-item>
      <div v-if="form.pathIds.length">=
        <div class="title1 mb10">路径选择</div>
        <!-- <el-form-item
          label="路径选择"
          prop="pathId"
          :rules="{ required: true, message: '不允许为空', trigger: 'blur' }"
        >
          <el-select v-model="form.pathId" :disabled="disabled" class="w100" placeholder="请选择" clearable popper-class="custom-select-popper1">
            <el-option
              v-for="dict in pathList"
              :key="dict.pathId"
              :label="dict.pathName"
              :value="dict.pathId"
            />
          </el-select>
        </el-form-item> -->
        <el-form-item
          v-for="(path, index) in form.pathIds"
          :label="'路径'"
          :key="path.key"
          :prop="'pathIds.' + index + '.value'"
          :rules="{
            required: true, message: '路径不能为空', trigger: 'blur'
          }"
        >
          <el-select v-model="path.value" class="w100" placeholder="请选择" clearable popper-class="custom-select-popper1">
            <el-option
              v-for="dict in pathList[index]"
              :key="dict.pathId"
              :label="dict.pathName"
              :value="dict.pathId"
            />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <template slot="footer">
      <el-button tt="modal" @click="submitForm(true)">开始执行</el-button>
      <el-button tt="modal" @click="dialogVisible = false">取 消</el-button>
      <el-button tt="modal" @click="submitForm(false)">无需执行</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { listAllPath } from "@/api/rsp/path";
import { listAllMotion } from "@/api/rsp/equipment";
import { addCar, executeTask, executeTaskByStep, updateCar } from "../../../api/bi";
export default {
  name: 'executeCar',
  data() {
    return {
      equipmentList: [],
      pathList: [],
      // 表单参数
      form: {
        pathIds: [
          // { value: '' }
        ],
        equipmentIds: []
      },
      disabled: false,
      dialogVisible: false,
      info: {}
    }
  },
  methods: {
    open(data) {
      this.info = data
      this.form = {
        pathIds: [
          // { value: '' }
        ],
        equipmentIds: []
      };
      this.equipmentList = [];
      this.pathList = [];
      this.resetForm('formRef');
      this.getMotionList()
      this.dialogVisible = true
    },
    /** 查询装备列表 */
    getMotionList() {
      this.loading = true;
      listAllMotion({ stateIn: '1,3' }).then(response => {
        this.equipmentList = response.data;
      });
    },
    handleChangeEquipment(e) {
      e.map((item, index) => {
        const eqItem = this.equipmentList.filter(eq => eq.id === item)
        this.pathList.push(eqItem[0].paths)
        this.form.pathIds.push({ value: '', key: item })
      })
    },
    handleRemoveTag(tag) {
      this.form.pathIds = this.form.pathIds.filter(item => {
        return item.key !== tag
      })
    },
    /** 查询路径规划列表 */
    getPathList(e) {
      this.pathList = [];
      this.form.pathId = '';
      if (e.length) {
        listAllPath('?equipmentId=' + e).then(response => {
          this.pathList = response.data;
        });
      }
    },
    submitForm(executeFlag) {
      if (!executeFlag) {
        this.dialogVisible = false
        const formData = new FormData();
        Object.keys(this.info).map(key => {
          if(this.info[key] === null) return
          formData.append(key, key === 'status' ? '3' : this.info[key])
        })
        updateCar(formData).then(res => {
          if (res.code === 200) {
            this.$modal.msgSuccess(res.msg);
            this.$emit('refresh')
            this.dialogVisible = false
            // 打开详情页面
          } else {
            this.$modal.msgSuccess(res.msg);
          }
        });
      } else {
        // this.dialogVisible = false
        // // // 默认第0个路径        
        const pathIds = this.form.pathIds.map(item => item.value)
        // this.$emit('detail', { ...this.info, bindPaths: this.pathList[0].filter(item => pathIds.includes(item.pathId))[0], equipmentList: this.equipmentList.filter(item => this.form.equipmentIds.includes(item.id)) })
        // return
        this.$refs.formRef.validate(valid => {
          if (valid) {
            executeTask({ ...this.info, pathIds }).then(res => {
              if (res.code === 200) {
                this.$modal.msgSuccess(res.msg);
                this.dialogVisible = false
                // 第1个装备的路径
                const bindPaths = this.pathList[0].filter(item => pathIds.includes(item.pathId))[0]
                this.$emit('detail', { ...this.info, pathId: this.info.pathId || bindPaths.pathId, bindPaths, equipmentList: this.equipmentList.filter(item => this.form.equipmentIds.includes(item.id)) })
                // executeTaskByStep({ ...this.info, pathId: this.form.pathIds[0] }).then(res1 => {
                //   if (res1.code === 200) {
                //     this.$modal.msgSuccess(res1.msg);
                //     this.dialogVisible = false
                //     executeTaskByStep({ ...this.info, pathId: this.form.pathIds[0].value })
                //     // 打开详情页面
                //   } else {
                //     this.$modal.msgSuccess(res1.msg);
                //   }
                // });
                // 打开详情页面
              } else {
                this.$modal.msgSuccess(res.msg);
              }
            });
          }
        })
      }
    },
  }
}
</script>

<style lang="scss" scoped>
@import "./form.scss";

</style>
