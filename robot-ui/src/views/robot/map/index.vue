<template>
  <div class="app-container">
    <!--    查询-->
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="地图名称" prop="MapName">
        <el-input v-model="queryParams.MapName" placeholder="请输入地图名称" clearable :maxlength="20" :style="{width: '240px'}">
        </el-input>
      </el-form-item>
      <el-form-item label="机器人" prop="dogName">
        <el-input v-model="queryParams.dogName" placeholder="请输入机器人名称" clearable :maxlength="20" :style="{width: '240px'}">
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <!--    按钮-->
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
    <!--    表格-->
    <el-table v-loading="loading" :data="mapList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="地图名称" align="center" key="MapName" prop="MapName" v-if="columns[0].visible" />
      <el-table-column label="机器人名称" align="center" key="dogName" prop="dogName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="X轴始坐标" align="center" key="X0" prop="X0" v-if="columns[2].visible" :show-overflow-tooltip="true" />
      <el-table-column label="X轴始坐标" align="center" key="Y0" prop="Y0" v-if="columns[3].visible" :show-overflow-tooltip="true" />
      <!--      <el-table-column label="地图图片" align="center" key="MapName" prop="MapName" v-if="columns[4].visible" :show-overflow-tooltip="true">-->
      <!--        <template slot-scope="scope">-->
      <!--          <img :src="getImageSrc(scope)" alt="地图图片"/>-->
      <!--        </template>-->
      <!--      </el-table-column>-->
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
        <template slot-scope="scope" v-if="scope.row.userId !== 1">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-search"
            @click="lookMapPicture(scope.row)"
          >查看</el-button>
          <!--          <el-button-->
          <!--            size="mini"-->
          <!--            type="text"-->
          <!--            icon="el-icon-edit"-->
          <!--            @click="handleUpdate(scope.row)"-->
          <!--          >修改</el-button>-->
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!--    分页-->
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
    <!--    弹窗(新增)-->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-row :gutter="15">
        <el-form ref="elForm" :model="mapInfo" size="medium" label-width="100px">
          <el-col :span="24">
            <el-form-item label="地图名称" prop="MapName" required clearable>
              <el-input v-model="mapInfo.MapName" placeholder="请输入地图名称" :maxlength="20" :style="{width: '100%'}">
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="!editable">
            <el-form-item label="机器人" prop="dogId" required clearable>
              <el-select v-model="mapInfo.dogId" placeholder="请选择机器人"  :style="{width: '100%'}"
                         :disabled="editable" @change="changeDog">
                <el-option v-for="(item, index) in dogIdOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="!editable">
            <el-form-item label="地图编号" prop="MapID" required clearable>
              <el-input v-model="mapInfo.MapID" placeholder="请输入地图编号" :maxlength="20" :disabled="editable">
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="X轴始坐标" prop="X0" required clearable>
              <el-input v-model="mapInfo.X0" placeholder="请输入X轴始坐标" :maxlength="20" :style="{width: '100%'}">
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Y轴始坐标" prop="Y0" required clearable>
              <el-input v-model="mapInfo.Y0" placeholder="请输入Y轴始坐标" :maxlength="20" :style="{width: '100%'}">
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="栅格图" prop="MapPicture" >
              <el-upload
                class="upload-demo"
                :action="uploadUrl"
                :limit="3"
                :before-upload="beforeUpload"
                :on-change="handleChange"
                :file-list="MapPicturefileList"
                :auto-upload="false"
                :disabled="MapPicturefileList.length > 0"
              >
                <el-button size="small" type="primary">点击上传</el-button>
                <div slot="tip" class="el-upload__tip">只能上传pgm文件</div>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <div slot="footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
    <!--    查看地图图片的弹窗-->
    <el-dialog :title="lookTitle" :visible.sync="lookOpen" width="600px" append-to-body>
      <img :src="mapPictureRoute" alt="地图图片" />
      <div slot="footer">
        <el-button type="primary" @click="closeLookDialog">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMapList,addMap,delMap,updateMap,getDogList  } from '@/api/robot/map.js'
import { issueTaskbyId,getDogState } from '@/api/login.js'
export default {
  name: "map",
  data() {
    return {
      imageSrc: `${process.env.VUE_APP_BASE_API}/images/1.jpg`,
      // 查询---------------------------------------------
      showSearch: true,
      queryParams: {
        MapName: "",
        dogName: "",
        pageNum: 1,
        pageSize: 5,
      },
      // 表格---------------------------------------------
      multiple: true,
      loading: true,
      mapList: [],
      //总数
      total: 0,
      //选择数据项
      ids: [],
      // 列信息
      columns: [
        { key: 0, label: `地图名称`, visible: true },
        { key: 1, label: `机器人名称`, visible: true },
        { key: 2, label: `X轴始坐标`, visible: true },
        { key: 3, label: `Y轴始坐标`, visible: true },
        { key: 4, label: `地图栅格图`, visible: true },
      ],
      // 弹窗---------------------------------------------
      editable: true,
      open: false,
      title: '',
      //新增的对象
      mapInfo: {
        MapID: undefined,
        MapName: undefined,
        dogId: undefined,
        dogName: undefined,
        MapPicture: undefined,
        X0: undefined,
        Y0: undefined
      },
      //机器狗选择列表
      dogIdOptions: [],
      //图片上传
      uploadUrl: `${process.env.VUE_APP_BASE_API}/User/uploadUserInfo`,
      previewImage: '',
      previewVisible: false,
      MapPicturefileList: [], // 存储上传的图片文件
      //查看地图图片的弹窗
      lookTitle: '',
      lookOpen: false,
      mapPictureRoute:``,
    };
  },
  created() {
    this.getList()
    this.getDogList()
  },
  methods: {
    //查询dog列表
    getDogList() {
      getDogList().then(res=> {
        console.log(res)
        this.dogIdOptions = res.data.map(item=>({
          value: item.dogId,
          label: item.dogName
        }))
      })
    },
    // getImageSrc(item) {
    //   return `${process.env.VUE_APP_BASE_API}/images/maps/${item.row.MapName}.jpg`;
    // },
    lookMapPicture(row) {
      //查看地图图片
      console.log(row)
      this.lookOpen = true
      this.lookTitle = "查看地图图片:"+ row.MapName
      this.mapPictureRoute = `${process.env.VUE_APP_BASE_API}/images/maps/${row.MapName}.jpg`
    },
    closeLookDialog() {
      this.lookOpen = false
    },
    // 查询---------------------------------------------
    //点击搜索
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    //点击重置
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 按钮---------------------------------------------
    handleAdd() {
      this.editable = false
      this.reset();
      this.open = true;
      this.title = "新增地图"
    },
    handleDelete(row) {
      let mapIds = undefined
      if( row.MapID !== undefined ) {
        mapIds = row.MapID
      } else if (this.ids.length !== 0) {
        mapIds = this.ids;
      }
      this.$modal.confirm('是否确认删除地图编号为"' + mapIds + '"的数据项？').then(function() {
        return delMap(mapIds)
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    // 表格---------------------------------------------
    getList() {
      getMapList(this.queryParams).then(res => {
          this.mapList = res.rows;
          this.total = res.total;
          this.loading = false;
          console.log(this.mapList)
        }
      );
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.MapID);
      this.multiple = !selection.length;
      console.log(this.ids)
    },
    //更新
    handleUpdate(row) {
      console.log(row)
      this.mapInfo.MapID = row.MapID
      this.mapInfo.MapName = row.MapName
      this.mapInfo.dogId = row.dogId
      this.mapInfo.dogName = row.dogName
      this.mapInfo.MapPicture = row.MapPicture
      this.mapInfo.X0 = row.X0
      this.mapInfo.Y0 = row.Y0
      this.editable = true
      this.open = true
      this.title = "修改地图信息"
    },
    // 弹窗---------------------------------------------
    reset() {
      this.mapInfo = {
        MapID: undefined,
        MapName: undefined,
        dogId: undefined,
        dogName: undefined,
        MapPicture: undefined,
        X0: undefined,
        Y0: undefined
      }
      this.resetForm("elForm");
    },
    changeDog(value) {
      console.log(value)
      const selectedOption = this.dogIdOptions.find(option => option.value === value);
      this.mapInfo.dogId = selectedOption.value
      this.mapInfo.dogName = selectedOption.label
    },
    handleChange(file, fileList) {
      this.MapPicturefileList = fileList;  // 更新上传列表，确保响应式
    },
    //图片上传部分
    async beforeUpload(file) {
      const isPGM = file.name.endsWith('.pgm');  // 检查文件扩展名而不是 MIME 类型
      if (!isPGM) {
        this.$message.error('请上传PGM格式图片!');
        return false;
      }

      // 将 PGM 文件推送到上传队列，确保响应式更新
      this.MapPicturefileList = [...this.MapPicturefileList, file];
      return false; // 阻止默认的上传行为
    },
    //点击确认（新增or修改）
    submitForm: function() {
      this.$refs["elForm"].validate(valid => {
        if (valid) {
          const formData = new FormData();
          if (this.MapPicturefileList.length > 0) {
            formData.append('MapPicture', this.MapPicturefileList[0].raw);
          }
          formData.append('MapID', this.mapInfo.MapID);
          formData.append('MapName', this.mapInfo.MapName);
          formData.append('dogId', this.mapInfo.dogId);
          formData.append('dogName',this.mapInfo.dogName)
          formData.append('X0',this.mapInfo.X0)
          formData.append('Y0',this.mapInfo.Y0)
          if (!this.editable) {
            addMap(formData).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;

              this.getList();
            }).catch(error=> {
              console.log(error)
              this.$modal.msgError("添加失败，请确认地图编号与机器人没有重复")
            });
          } else {
            //更新
            updateMap(formData).then(response => {
              this.$modal.msgSuccess("修改成功");
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
  }
};
</script>
