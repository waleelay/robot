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
        <div class="title">表单模板</div>
      </div>
    </template>
    <!-- <el-form ref="formRef" :model="form" :rules="[]" class="custom-form pt5"> -->
    <el-form ref="formRef" :model="form" :rules="{}" class="custom-form pt5" :label-position="right" label-width="80px">
      <el-form-item prop="username" label="账号" required>
        <el-input
          v-model="form.username"
          type="text"
          auto-complete="off"
          placeholder="账号"
        >
          <!-- <svg-icon slot="prefix" icon-class="user" class="el-input__icon input-icon" /> -->
        </el-input>
      </el-form-item>
      <el-form-item prop="pwd" label="密码">
        <el-input
          v-model="form.pwd"
          type="text"
          auto-complete="off"
          placeholder="账号"
        >
          <!-- <svg-icon slot="prefix" icon-class="user" class="el-input__icon input-icon" /> -->
        </el-input>
      </el-form-item>
      <div class="title1 mb10">二级标题</div>
      <el-form-item prop="checkList" label="密码">
        <el-checkbox-group v-model="form.checkList">
          <el-checkbox label="复选框 A"></el-checkbox>
          <el-checkbox label="复选框 B"></el-checkbox>
          <el-checkbox label="复选框 C"></el-checkbox>
          <!-- <el-checkbox label="禁用" disabled></el-checkbox>
          <el-checkbox label="选中且禁用" disabled></el-checkbox> -->
        </el-checkbox-group>
      </el-form-item>
      <el-form-item prop="selectValue" label="密码">
        <el-select v-model="form.selectValue" placeholder="请选择" class="w100" popper-class="custom-select-popper1">
          <el-option
            v-for="item in options"
            :key="item.value"
            :label="item.label"
            :value="item.value">
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <template slot="footer">
      <el-button tt="modal"  @click="dialogVisible = false">取消</el-button>
      <el-button tt="modal" class="ml10" @click="save">确认</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: 'form',
  data() {
    return {
      form: {
        checkList: ["复选框 A"]
      },
      rules: [],
      dialogVisible: true,
      options: [
        { value: '选项1', label: '黄金糕', url: '' },
        { value: '选项2', label: '双皮奶', url: '' },
        { value: '选项3', label: '蚵仔煎', url: '' },
        { value: '选项4', label: '龙须面', url: '' },
        { value: '选项5',label: '北京烤鸭', url: '' },
        { value: '选项15',label: '北京烤鸭', url: '' },
        { value: '选项52',label: '北京烤鸭', url: '' }
      ],
    }
  },
  methods: {
    save() {},
  }
}
</script>

<style lang="scss" scoped>
@import "./form.scss";

</style>
