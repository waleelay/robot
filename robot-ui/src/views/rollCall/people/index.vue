<template>
  <div class="app-container">
    <!--  查询条件-->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="犯人编号" prop="UserId">
        <el-input
          v-model="queryParams.UserId"
          placeholder="请输入查询编号"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="犯人姓名" prop="UserName">
        <el-input
          v-model="queryParams.UserName"
          placeholder="请输入查询姓名"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <!--  按钮(新增，删除，导入，导出)-->
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
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="triggerFileInput"
        >导入</el-button>
      </el-col>
      <input type="file" ref="fileInput" accept=".xlsx, .xls" @change="submitUpload" style="display:none;" />
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
        >全部导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>
    <!--表格-->
    <el-table v-loading="loading" :data="UserList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="编号" align="center" key="UserId" prop="UserId" v-if="columns[0].visible" />
      <el-table-column label="姓名" align="center" key="UserName" prop="UserName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="性别" align="center" width="50" key="Sex" prop="Sex" v-if="columns[2].visible">
        <template slot-scope="scope">
          <span>{{ scope.row.Sex === 0 ? '男' : '女' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="年龄" align="center" width="50" key="Age" prop="Age" v-if="columns[3].visible" />
      <el-table-column label="照片名" align="center" key="PictureData" prop="PictureData" v-if="columns[4].visible" :show-overflow-tooltip="true" />
      <el-table-column label="重点关注" align="center" width="50" key="IsImportant" prop="IsImportant" v-if="columns[5].visible">
        <template slot-scope="scope">
          <span>{{ scope.row.IsImportant === 0 ? '否' : '是' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="籍贯" align="center" key="NativePlace" prop="NativePlace" v-if="columns[6].visible" :show-overflow-tooltip="true" />
      <el-table-column label="犯罪性质" align="center" key="CrimeType" prop="CrimeType" v-if="columns[7].visible" />
      <el-table-column label="刑罚种类" align="center" key="CrimeQuality" prop="CrimeQuality" v-if="columns[8].visible" />
      <el-table-column label="刑期（年）" align="center" key="Sentence" prop="Sentence" v-if="columns[9].visible" />
      <el-table-column label="释放日期" align="center" width="120" key="ReleaseDate" prop="ReleaseDate" v-if="columns[10].visible" />
      <el-table-column label="认罪态度" align="center" key="ReformAttitude" prop="ReformAttitude" v-if="columns[11].visible" />


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
    <!--    添加或修改犯人信息的对话框-->
    <el-dialog :title="title" :visible.sync="open" width="1000px" append-to-body>
      <el-row :gutter="15">
        <el-divider content-position="center">基本信息</el-divider>
        <el-form ref="elForm" :model="formData" :rules="rules" size="medium" label-width="100px">
          <el-col :span="8">
            <el-form-item label="犯人编号" prop="UserId">
              <el-input v-model="formData.UserId" placeholder="请输入犯人编号" :maxlength="20" clearable
                        :style="{width: '100%'}" :disabled="title === '修改用户'"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="犯人姓名" prop="UserName">
              <el-input v-model="formData.UserName" placeholder="请输入犯人姓名" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="犯人性别" prop="Sex">
              <el-radio v-model.number="formData.Sex" :label="0">男</el-radio>
              <el-radio v-model.number="formData.Sex" :label="1">女</el-radio>
<!--              <el-select v-model="formData.Sex" placeholder="请选择犯人性别" clearable :style="{width: '100%'}">-->
<!--                <el-option v-for="(item, index) in SexOptions" :key="index" :label="item.label"-->
<!--                           :value="item.value" :disabled="item.disabled"></el-option>-->
<!--              </el-select>-->
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="犯人照片名" prop="PictureData">
              <el-input v-model="formData.PictureData" placeholder="请输入照片名" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="犯人年龄" prop="Age">
              <el-input-number v-model="formData.Age" :min="1" :max="100"></el-input-number>
<!--              <el-input v-model="formData.age" placeholder="请输入年龄" :maxlength="20" clearable-->
<!--                        :style="{width: '100%'}"></el-input>-->
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="文化程度" prop="EducationLevel">
              <el-input v-model="formData.EducationLevel" placeholder="请输入文化程度" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="特征" prop="Trait">
              <el-input v-model="formData.Trait" placeholder="请输入特征" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="捕前地址" prop="ArrestAddress">
              <el-input v-model="formData.ArrestAddress" placeholder="请输入捕前地址" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="捕前职业" prop="Occupation">
              <el-input v-model="formData.Occupation" placeholder="请输入捕前职业" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <el-row :gutter="15">
        <el-divider content-position="center">家庭社会关系</el-divider>
        <el-form ref="familyelForm" :model="formData" :rules="familyRules" size="medium" label-width="100px">
          <el-col :span="8">
            <el-form-item label="籍贯" prop="NativePlace">
              <el-input v-model="formData.NativePlace" placeholder="请输入籍贯" :maxlength="30" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="亲属" prop="FamilyRelation">
              <el-input v-model="formData.FamilyRelation" placeholder="请输入亲属" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="社会关系" prop="SocialRelation">
              <el-input v-model="formData.SocialRelation" placeholder="请输入主要社会关系" :maxlength="30" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <el-row :gutter="15">
        <el-divider content-position="center">犯罪情况</el-divider>
        <el-form ref="crimeelForm" :model="formData" :rules="crimeRules" size="medium" label-width="100px">
          <el-col :span="8">
            <el-form-item label="犯罪性质" prop="CrimeType">
              <el-input v-model="formData.CrimeType" placeholder="请输入犯罪性质" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="刑罚种类" prop="CrimeQuality">
              <el-input v-model="formData.CrimeQuality" placeholder="请输入刑罚种类" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="刑期(年)" prop="Sentence">
              <el-input-number v-model="formData.Sentence" :min="1" :max="100"></el-input-number>
<!--              <el-input v-model="formData.period" placeholder="请输入刑期" :maxlength="20" clearable-->
<!--                        :style="{width: '100%'}"></el-input>-->
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="释放日期" prop="ReleaseDate">
              <el-date-picker
                v-model="formData.ReleaseDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="选择释放日期">
              </el-date-picker>
<!--              <el-input v-model="formData.releaseDate" placeholder="请输入释放日期" :maxlength="20" clearable-->
<!--                        :style="{width: '100%'}"></el-input>-->
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="重点关注" prop="IsImportant">
              <el-radio v-model.number="formData.IsImportant" :label = "1">是</el-radio>
              <el-radio v-model.number="formData.IsImportant" :label= "0">否</el-radio>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="犯罪案情" prop="CaseDetails">
              <el-input
                type="textarea"
                maxlength="30"
                show-word-limit
                placeholder="请输入犯罪案情"
                v-model="formData.CaseDetails">
              </el-input>
<!--              <el-input v-model="formData.case" placeholder="请输入犯罪案情" :maxlength="20" clearable-->
<!--                        :style="{width: '100%'}"></el-input>-->
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <el-row :gutter="15">
        <el-divider content-position="center">改造表现情况</el-divider>
        <el-form ref="reformelForm" :model="formData" :rules="reformRules" size="medium" label-width="100px">
          <el-col :span="8">
            <el-form-item label="认罪态度" prop="ReformAttitude">
              <el-input v-model="formData.ReformAttitude" placeholder="请输入认罪态度" :maxlength="10" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="行为表现" prop="Behavior">
              <el-input v-model="formData.Behavior" placeholder="请输入行为表现" :maxlength="20" clearable
                        :style="{width: '100%'}"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="奖惩情况" prop="Rewards">
              <el-input
                type="textarea"
                maxlength="30"
                show-word-limit
                placeholder="请输入奖惩情况"
                v-model="formData.Rewards">
              </el-input>
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
import {getUserList,delUser,addUser,updateUser,getExportUserList} from '@/api/rollCall/people'
import { getToken } from '@/utils/auth'
import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
export default {
  name: "people",
  data() {
    return {
      //查询
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        UserId: undefined,
        UserName: undefined,
      },
      //文件上传部分
      uploadUrl: `${process.env.VUE_APP_BASE_API}/User/uploadUserInfo`,
      exportUserList: [],
      // 显示搜索条件
      showSearch: true,
      // 遮罩层
      loading: true,
      // 非多个禁用
      multiple: true,
      // 选中数组
      ids: [],
      // 列信息
      columns: [
        { key: 0, label: `编号`, visible: true },
        { key: 1, label: `姓名`, visible: true },
        { key: 2, label: `性别`, visible: true },
        { key: 3, label: `年龄`, visible: true },
        { key: 4, label: `照片名`, visible: true },
        { key: 5, label: `重点关注`, visible: true },
        { key: 6, label: `籍贯`, visible: true },
        { key: 7, label: `犯罪性质`, visible: true },
        { key: 8, label: `刑罚种类`, visible: true },
        { key: 9, label: `刑期（年）`, visible: true },
        { key: 10, label: `释放日期`, visible: true },
        { key: 11, label: `认罪态度`, visible: true },
      ],
      // 总条数
      total: 2,
      // 用户表格数据
      UserList: null,
      //弹窗
      // 弹出层标题
      title: "",
      open: false,
      formData: {
        UserId: undefined,
        UserName: undefined,
        Sex: undefined,
        UserPart: undefined,
        PictureData: undefined,
      },
      rules: {
        UserId: [{required: true, message: '请输入犯人编号', trigger: 'blur'}],
        UserName: [{required: true, message: '请输入犯人姓名', trigger: 'blur'}],
        Sex: [{required: true, message: '请选择犯人性别', trigger: 'change'}],
        PictureData: [{required: true, message: '请输入犯人照片名', trigger: 'blur'}],
        Age: [{required: true, message: '请输入犯人年龄', trigger: 'blur'}],
        EducationLevel: [{required: true, message: '请输入文化程度', trigger: 'blur'}],
        Trait: [{required: true, message: '请输入特征', trigger: 'blur'}],
        ArrestAddress: [{required: true, message: '请输入捕前地址', trigger: 'blur'}],
        Occupation: [{required: true, message: '请输入捕前职业', trigger: 'blur'}]
      },
      familyRules: {
        NativePlace:[{required: true, message: '请输入籍贯', trigger: 'blur'}],
        FamilyRelation:[{required: true, message: '请输入亲属', trigger: 'blur'}],
        SocialRelation:[{required: true, message: '请输入主要社会关系', trigger: 'blur'}]
      },
      crimeRules:{
        CrimeType:[{required: true, message: '请输入犯罪性质', trigger: 'blur'}],
        CrimeQuality:[{required: true, message: '请输入刑罚种类', trigger: 'blur'}],
        Sentence:[{required: true, message: '请输入刑期', trigger: 'blur'}],
        ReleaseDate:[{required: true, message: '请输入释放日期', trigger: 'blur'}],
        IsImportant:[{required: true, message: '请选择是否为重要人员', trigger: 'blur'}],
        CaseDetails:[{required: true, message: '请输入犯罪案情', trigger: 'blur'}]
      },
      reformRules:{
        ReformAttitude:[{required: true, message: '请输入认罪态度', trigger: 'blur'}],
        Behavior:[{required: true, message: '请输入行为表现', trigger: 'blur'}],
        Rewards: [{required: true, message: '请输入奖惩情况', trigger: 'blur'}]
      },
      SexOptions: [{
        "label": "男",
        "value": 0
      }, {
        "label": "女",
        "value": 1
      }],
      UserPartOptions: [],
      UserPartProps: {},
      //判断是新增还是修改(新增0，修改1)
      state:0,
    };
  },
  created() {
    this.getList()
  },
  mounted() {
    const XLSX = require('xlsx');  // 动态引入
  },
  methods: {
    //文件上传
    // 触发文件选择框
    triggerFileInput() {
      this.$refs.fileInput.click();
    },
    // 处理文件上传
    submitUpload(event) {
      // console.log('处理文件上传')
      const selectedFile = event.target.files[0];
      if (!selectedFile) {
        // console.log('没有文件被选择，什么也不做')
        return; // 如果没有文件被选择，什么也不做
      }
      const formData = new FormData();
      formData.append('file', selectedFile);

      // 发送文件上传请求
      fetch(this.uploadUrl, {
        method: 'POST',
        headers: {
          // 如果需要 token，这里可以添加 Authorization
          'Authorization': 'Bearer ' + getToken(),
        },
        body: formData,
      })
        .then(response => {
          // console.log('response')
          // console.log(response.json())
          this.$refs.fileInput.value = null; // 清空文件输入
          this.getList()
        })
        .then(data => {
          this.$message.success('文件上传成功');
        })
        .catch(error => {
          this.$message.error('文件上传失败');
          // console.error(error);
          this.$refs.fileInput.value = null;
        });
    },
    //点击导出操作 1.获取要导出的数据
    handleExport() {
      getUserList(this.queryParams).then(res => {
        // console.log(res)
        this.exportUserList = res.data;
        this.submitExport()
      })
    },
    // 2. 将获取到的数据转成excel
    submitExport() {
      const sheetData = [
        ['犯人编号', '犯人姓名', '犯人性别', '犯人照片名','犯人年龄','文化程度','特征','捕前地址','捕前职业','籍贯','亲属','主要社会关系','犯罪性质','刑罚种类','刑期','释放日期','是否为重要人员','犯罪案情','认罪态度','行为表现','奖惩情况'], // 表头
        ...this.exportUserList.map(item => [
          item.UserId,
          item.UserName,
          item.Sex === 0 ? '男' : '女', // 性别转换为可读格式
          item.PictureData,
          item.Age,
          item.EducationLevel,
          item.Trait,
          item.ArrestAddress,
          item.Occupation,
          item.NativePlace,
          item.FamilyRelation,
          item.SocialRelation,
          item.CrimeType,
          item.CrimeQuality,
          item.Sentence,
          item.ReleaseDate,
          item.IsImportant === 0 ? '否':'是',
          item.CaseDetails,
          item.ReformAttitude,
          item.Behavior,
          item.Rewards,
        ])
      ];
      // 创建工作簿
      const wb = XLSX.utils.book_new();
      // 使用 aoa_to_sheet 将二维数组数据转换为工作表
      const ws = XLSX.utils.aoa_to_sheet(sheetData);
      // 添加工作表到工作簿
      XLSX.utils.book_append_sheet(wb, ws, 'Sheet1');
      // 导出为二进制数据
      const wbout = XLSX.write(wb, { bookType: 'xlsx', type: 'array' });
      // 保存文件为 .xlsx 格式
      saveAs(new Blob([wbout], { type: 'application/octet-stream' }), '人员信息.xlsx');
    },
    //查询--------------------------------------------
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
    //按钮（新增、删除、导入、导出）------------------------
    /** 新增按钮操作 */
    handleAdd() {
      this.state = 0
      this.reset();
      this.open = true;
      this.title = "添加用户";
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const UserIds = row.UserId || this.ids;
      // console.log(UserIds)
      this.$modal.confirm('是否确认删除犯人编号为"' + UserIds + '"的数据项？').then(function() {
        //******************************
        return delUser(UserIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    //表格-----------------------------------------------
    // /** 查询用户列表 */
    getList() {
      // console.log(this.queryParams)
      getExportUserList(this.queryParams).then(res => {
          // console.log(res)
          this.UserList = res.rows;
          this.total = res.total;
          this.loading = false;
        }
      );
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.UserId);
      this.multiple = !selection.length;
      // console.log(this.ids)
    },

    //弹窗-------------------------------------------------
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.state=1
      this.reset();
      // console.log(row)
      this.formData = row;
      // this.formData.UserId = row.UserId
      // this.formData.UserName = row.UserName
      // this.formData.Sex = row.Sex
      // this.formData.UserPart = row.UserPart
      // this.formData.PictureData = row.PictureData
      this.open = true;
      this.title = "修改用户";
    },
    /** 提交按钮 */
    submitForm: function() {
      // console.log(this.formData)
      this.$refs["elForm","familyelForm","crimeelForm","reformelForm"].validate(valid => {
        if (valid) {
          if (this.state == 1) {
            // console.log(this.formData)
            updateUser(this.formData).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            // console.log(this.formData)
            //我在这里传的！
            addUser(this.formData).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },
// 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.formData = {};
      this.resetForm("elForm");
      this.resetForm("familyelForm");
      this.resetForm("crimeelForm");
      this.resetForm("reformelForm");
    },
  }
};
</script>
<style>
.el-divider--horizontal {
  display: block;
  height: 1px;
  width: 100%;
  margin: 30px 0 24px 0;
}
.el-divider__text {
  position: absolute;
  background-color: #FFFFFF;
  padding: 0 20px;
  font-weight: 600;
  color: #5e5e63;
  font-size: 14px;
}
</style>
