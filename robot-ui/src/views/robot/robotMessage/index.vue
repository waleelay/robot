<template>
  <div class="app-container">
    <!--  查询-->
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="机器人" prop="dogName">
        <el-input v-model="queryParams.dogName" placeholder="请输入机器人名称" clearable :style="{width: '240px'}">
        </el-input>
      </el-form-item>
      <el-form-item label="状态" prop="dogState">
        <el-select v-model="queryParams.dogState" placeholder="请选择机器人状态"  :style="{width: '100%'}">
          <el-option v-for="(item, index) in dogStateOptions" :key="index" :label="item.label"
                     :value="item.value" ></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
<!--  按钮-->
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
<!--  表格-->
  <el-table v-loading="loading" :data="dogList" @selection-change="handleSelectionChange">
    <el-table-column type="selection" width="50" align="center" />
    <el-table-column label="机器名称" align="center" key="dogName" prop="dogName" v-if="columns[0].visible" />
    <el-table-column label="机器IP" align="center" key="dogIP" prop="dogIP" v-if="columns[1].visible" :show-overflow-tooltip="true" />
    <el-table-column label="机器类型" align="center" key="dogType" prop="dogType" v-if="columns[2].visible" :show-overflow-tooltip="true" :formatter="formatType" />
    <el-table-column label="机器状态" align="center" key="dogState" prop="dogState" v-if="columns[3].visible" :show-overflow-tooltip="true" :formatter="formatDogState"/>
<!--    <el-table-column label="当前地图" align="center" key="dogState" prop="dogState" v-if="columns[4].visible" :show-overflow-tooltip="true" :formatter="formatDogState"/>-->

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
          icon="el-icon-edit"
          @click="handleUpdate(scope.row)"
        >修改</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-delete"
          @click="handleDelete(scope.row)"
        >删除</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-picture-outline-round"
          @click="handleSetMap(scope.row)"
        >配置地图</el-button>
      </template>
    </el-table-column>
  </el-table>
<!--  分页-->
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />
<!--  新增弹窗-->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-row :gutter="15">
        <el-form ref="elForm" :model="dogInfo"  size="medium" label-width="100px" :rules="rules">
          <el-col :span="12">
            <el-form-item label="机器名称" prop="dogName" >
              <el-input v-model="dogInfo.dogName" placeholder="请输入机器名称" clearable :style="{width: '100%'}">
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机器人IP" prop="dogIP" >
              <el-input v-model="dogInfo.dogIP" placeholder="请输入机器人IP" clearable :style="{width: '100%'}">
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机器类型" prop="dogType">
              <el-select v-model="dogInfo.dogType" placeholder="请选择机器类型" clearable :style="{width: '100%'}">
                <el-option v-for="(item, index) in dogTypeOptions" :key="index" :label="item.label"
                           :value="item.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机器状态" prop="dogState">
              <el-select v-model="dogInfo.dogState" placeholder="请选择机器状态" clearable :style="{width: '100%'}">
                <el-option v-for="(item, index) in dogStateOptions" :key="index" :label="item.label"
                           :value="item.value"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <div slot="footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
<!--配置地图弹窗-->
    <el-dialog :title="mapTitle" :visible.sync="mapOpen" width="600px" append-to-body>
      <el-row :gutter="15">
        <el-form ref="mapElForm" :model="mapInfo" size="medium" label-width="100px" :rules="mapRules">
          <el-col :span="24">
            <el-form-item label="地图名称" prop="MapName" required clearable>
              <el-input v-model="mapInfo.MapName" placeholder="请输入地图名称" :maxlength="20" :style="{width: '100%'}">
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
        <el-button type="primary" @click="mapSubmitForm">确 定</el-button>
        <el-button @click="mapCancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getRobotList,addRobot,delRobot,updataRobot,searchMapInfoByDogId } from '@/api/robot/robotMessage.js'
import { getMapList,addMap,delMap,updateMap,getDogList  } from '@/api/robot/map.js'
import { issueTaskbyId,getDogState } from '@/api/login.js'

export default {
  name: "robotMessage",
  data() {
    return {
      // 查询
      dogStateOptions: [
        {
          value: 1,
          label: "正常"
        },
        {
          value: 0,
          label: "禁用"
        }
      ],
      showSearch: true,
      queryParams: {
        dogState: 1,
        dogName: "",
        pageNum: 1,
        pageSize: 5,
      },
      // 表格
      multiple: true,
      loading: true,
      dogList: [],
      //总数
      total: 0,
      //选择数据项
      ids: [],
      // 列信息
      columns: [
        { key: 0, label: `机器名称`, visible: true },
        { key: 1, label: `机器IP`, visible: true },
        { key: 2, label: `机器类型`, visible: true },
        { key: 3, label: `机器状态`, visible: true },
        { key: 4, label: `地图`, visible: true }
      ],
      // 弹窗
      rules: {
        dogName: {required: true},
        dogIP: {required: true},
        dogType: {required: true},
        dogState: {required: true}
      },
      mapRules: {
        MapName: {required: true},
        X0: {required: true},
        Y0:{required: true}
      },
      open: false,
      title: '',
      mapTitle:'',
      mapOpen:false,
      mapInfo: {
        MapID: undefined,
        MapName: undefined,
        dogId: undefined,
        dogName: undefined,
        MapPicture: undefined,
        X0: undefined,
        Y0: undefined
      },
      //图片上传
      uploadUrl: `${process.env.VUE_APP_BASE_API}/User/uploadUserInfo`,
      MapPicturefileList: [], // 存储上传的图片文件
      //新增的对象
      dogInfo: {
        dogId: undefined,
        dogName: undefined,
        dogIP: undefined,
        dogType: undefined,
      },
      //机器狗选择列表
      dogTypeOptions: [
        {value: 0, label: '人形'},
        {value: 1, label: '犬形'},
        {value: 2, label: '轮式'},
      ],
    };
  },
  created() {
    this.getList()
  },
  methods: {
    // 查询
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    //点击重置
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 按钮
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "新增机器人"
    },
    // 点击配置地图
    handleSetMap(row) {
      // console.log(row)
      this.mapInfo = {}
      this.mapInfo.MapID = 0;
      this.mapInfo.dogId = row.dogId;
      this.mapInfo.dogName = row.dogName;
      this.mapOpen = true;
      this.mapTitle = "配置新地图"
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
    mapSubmitForm: function() {
      //首先确定这条狗是否已经配置了地图，若配置了，则删掉重新增加，若没有配置，则直接新增。
      //this.mapInfo.dogId
      let mapIds = null
      // console.log(this.mapInfo.dogId)
      searchMapInfoByDogId(this.mapInfo.dogId).then(res => {
        if (res.data.length === 0) {
          //暂时还没有配置地图，直接新增
          this.setMapInfo()
        } else {
          // console.log("1")
          mapIds = res.data[0].MapID
          delMap(mapIds)
          this.setMapInfo()
          //已经配置了地图了，先删再增加
          // this.$modal.confirm('配置新的地图会删除掉旧的地图，确认配置新的吗？').then(function() {
          //   console.log("1")
          //   mapIds = res.data[0].MapID
          //   delMap(mapIds)
          //   this.setMapInfo()
          // }).then(() => {
          // }).catch(() => {
          // });
        }
        // console.log(res)
      })
    },
    setMapInfo() {
      this.$refs["mapElForm"].validate(valid => {
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
          addMap(formData).then(response => {
            // console.log(response)
            this.$modal.msgSuccess("配置成功");
            this.mapOpen = false;
            this.MapPicturefileList=[]
            this.getList();
            this.mapReset();
          }).catch(error=> {
            // console.error(error)
            this.$modal.msgError("请上传pgm地图文件")
            this.mapOpen = false;
            this.MapPicturefileList=[]
            this.getList();
            this.mapReset();
          });
        }
      });
    },
    // 取消按钮
    mapCancel() {
      this.mapOpen = false;
      this.mapReset();
    },
    mapReset() {
      this.mapInfo = {
        MapID: 0,
        MapName: undefined,
        dogId: undefined,
        dogName: undefined,
        MapPicture: undefined,
        X0: undefined,
        Y0: undefined
      }
      this.resetForm("mapElForm");
    },
    handleDelete(row) {
      const dogIds = row.dogId || this.ids;
      this.$modal.confirm('是否确认删除编号为"' + dogIds + '"的机器人数据项？').then(function() {
        return delRobot(dogIds)
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    // 表格
    getList() {
      getRobotList(this.queryParams).then(res => {
          // console.log(res)
        this.dogList = res.rows
        // this.dogList = res.rows.map(row => ({
        //   ...row,  // 保留其他字段不变
        //   dogType: row.dogType === 1 ? '犬形' :
        //     row.dogType === 2 ? '人形' :
        //       row.dogType === 3 ? '轮式' : row.dogType
        // }));
          this.total = res.total;
          this.loading = false;
        }
      );
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.dogId);
      this.multiple = !selection.length;
      // console.log(this.ids)
    },
    //点击修改按钮
    handleUpdate(row) {
      this.dogInfo.dogId = row.dogId
      this.dogInfo.dogIP = row.dogIP
      this.dogInfo.dogType = row.dogType
      this.dogInfo.dogName = row.dogName
      this.open = true
      this.title = "修改机器人信息"
    },
    formatDogState(row, column, cellValue, index) {
      if (cellValue === 1) {
        return '正常';
      } else if (cellValue === 0) {
        return '禁用';
      }
      return '未知';
    },
    formatType(row, column, cellValue, index) {
      if (cellValue === 1) {
        return '犬形';
      } else if (cellValue === 0) {
        return '人形';
      }else if (cellValue === 2) {
        return '轮式';
      }
      return '未知';
    },
    // 弹窗
    reset() {
      this.dogInfo = {
        dogId: undefined,
        dogName: undefined,
        dogIP: undefined,
        dogType: undefined,
      }
      this.resetForm("elForm");
    },
    //点击确认（新增or修改）
    submitForm: function() {
      this.$refs["elForm"].validate(valid => {
        if (valid) {
          if (this.dogInfo.dogId == undefined) {
            //新增
            // console.log(this.dogInfo)
            addRobot(this.dogInfo).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            }).catch(error=> {
              this.$modal.msgError("添加失败")
            });
          } else {
            //更新
            // console.log(this.dogInfo)
            updataRobot(this.dogInfo).then(response => {
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
