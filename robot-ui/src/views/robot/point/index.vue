<template>
<div class="app-container">
<!--  查询条件-->
  <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
    <el-form-item label="点位名称" prop="PointName">
      <el-input v-model="queryParams.PointName" placeholder="请输入点名称" clearable :style="{width: '240px'}">
      </el-input>
    </el-form-item>
    <el-form-item label="对应地图" prop="MapID">
      <el-select v-model="queryParams.MapID" placeholder="请选择对应地图" clearable :style="{width: '240px'}">
        <el-option v-for="(item, index) in MapIdOptions" :key="item.label" :label="item.label"
                   :value="item.value" ></el-option>
      </el-select>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
      <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
    </el-form-item>
  </el-form>
<!--按钮(新增，删除)-->
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
      <el-upload
        action=""
        ref="upload"
        accept=".json"
        :show-file-list="false"
        :auto-upload="false"
        :before-upload="beforeUpload"
        :on-change="handleFileChange"
      >
        <el-button
          type="info"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="triggerFileUpload"
        >导入</el-button>
      </el-upload>
    </el-col>
    <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
  </el-row>
<!--  表格-->
  <el-table v-loading="loading" :data="pointList" @selection-change="handleSelectionChange">
    <el-table-column type="selection" width="50" align="center" />
    <el-table-column label="点位编号" align="center" key="PointId" prop="PointId" v-if="columns[0].visible" />
    <el-table-column label="点位名称" align="center" key="PointName" prop="PointName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
    <el-table-column label="对应地图" align="center" key="MapName" prop="MapName" v-if="columns[2].visible" :show-overflow-tooltip="true"/>
    <el-table-column label="机器人" align="center" key="dogId" prop="dogName" v-if="columns[3].visible" :show-overflow-tooltip="true"/>
    <el-table-column label="点位X轴" align="center" key="PosX" prop="PosX" v-if="columns[4].visible" :show-overflow-tooltip="true"/>
    <el-table-column label="点位Y轴" align="center" key="PosY" prop="PosY" v-if="columns[5].visible" :show-overflow-tooltip="true"/>
    <el-table-column label="点位Z轴" align="center" key="PosZ" prop="PosZ" v-if="columns[7].visible" :show-overflow-tooltip="true"/>
    <el-table-column label="点位角度" align="center" key="AngleYaw" prop="AngleYaw" v-if="columns[7].visible" :show-overflow-tooltip="true" />
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
<!--  修改-->
  <el-dialog :title="title" :visible.sync="editOpen" width="600px" append-to-body>
    <el-row :gutter="15">
      <el-form ref="editPoint" :model="editPointInfo" size="medium" label-width="90px">
        <el-col :span="12">
          <el-form-item label="点位名称" prop="PointName">
            <el-input v-model="editPointInfo.PointName" placeholder="请输入点位名称" :maxlength="20" clearable
                      :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
      </el-form>
    </el-row>
    <div slot="footer">
      <el-button type="primary" @click="submitEditForm">确 定</el-button>
      <el-button @click="editCancel">取 消</el-button>
    </div>
  </el-dialog>
<!--  弹窗（增加）-->
  <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
    <el-row :gutter="15">
      <el-form ref="pointInfo" :model="pointInfoDate" size="medium" label-width="90px">
        <el-col :span="24">
          <el-row :gutter="15">
            <el-col :span="24">
              <el-form-item label="点位名称" prop="PointName">
                <el-input v-model="pointInfoDate.PointName" placeholder="请输入点位名称" :maxlength="20" clearable
                          :style="{width: '100%'}"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="对应地图" prop="MapID">
                <el-select v-model="pointInfoDate.MapID" placeholder="请选择对应地图" clearable
                           :style="{width: '100%'}" @change="getdogIdOptions">
                  <el-option v-for="(item, index) in MapIdOptions" :key="index" :label="item.label"
                             :value="item.value" :disabled="item.disabled"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="机器狗" prop="dogName">
                <el-input v-model="pointInfoDate.dogName" placeholder="请选择地图" :maxlength="20" disabled
                          :style="{width: '100%'}"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
        </el-col>
        <el-col :span="12">
          <el-form-item label="点位X轴" prop="PosX">
            <el-input v-model="pointInfoDate.PosX" placeholder="请输入点位X轴" :maxlength="20" clearable
                      :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="点位Y轴" prop="PosY">
            <el-input v-model="pointInfoDate.PosY" placeholder="请输入点位Y轴" :maxlength="20" clearable
                      :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="点位Z轴" prop="PosZ">
            <el-input v-model="pointInfoDate.PosZ" placeholder="请输入点位Z轴" :maxlength="20" clearable
                      :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="点位角度" prop="AngleYaw">
            <el-input v-model="pointInfoDate.AngleYaw" placeholder="请输入点位角度" :maxlength="20" clearable
                      :style="{width: '100%'}"></el-input>
          </el-form-item>
        </el-col>
      </el-form>
    </el-row>
    <div slot="footer">
      <el-button type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="cancel">取 消</el-button>
    </div>
  </el-dialog>
<!--  点击导入后进行配置-->
  <el-dialog :title="uploadTitle" :visible.sync="uploadOpen" width="1000px" append-to-body>

    <el-row :gutter="15" v-for="(item, index) in uploadPointList"
            :key="index" class="uploadRow">
      <el-col :span="12" class="uploadCol">
        <div class="labelName">{{item.Value}}点名称:</div>
        <el-input v-model="item.PointName" placeholder="请输入点位名称"
                  :style="{width: '100%'}" ></el-input>
      </el-col>
      <el-col :span="12" class="uploadCol">
        <div class="labelName">{{item.Value}}点位置:</div>
        <div>X:{{item.PosX}} Y:{{item.PosY}}Z:{{item.PosZ}}</div>
      </el-col>
    </el-row>
    <div slot="footer">
      <el-button type="primary" @click="Uploadsubmit">提 交</el-button>
      <el-button @click="uploadCancel">取 消</el-button>
    </div>
  </el-dialog>
</div>
</template>

<script>
import { getMapIdOptions,listPoint,delPoint,updataPoint,addPoint,searDogId,importPoint} from '@/api/robot/point'
import { getDogState } from "../../../api/login";

export default {
  name: "point",
  data() {
    return {
    //  查询部分*************************
      showSearch: true,
      queryParams: {
        pageNum: 1,
        pageSize: 5,
        PointName: "",
        MapID: "",
      },
      //表格部分************************************
      multiple: true,
      loading: true,
      pointList: null,
      //总数
      total: 2,
      //选择数据项
      ids: [],
      // 列信息
      columns: [
        { key: 0, label: `点位编号`, visible: true },
        { key: 1, label: `点位名称`, visible: true },
        { key: 2, label: `对应地图`, visible: true },
        { key: 3, label: `机器狗`, visible: true },
        { key: 4, label: `点位X轴`, visible: true },
        { key: 5, label: `点位Y轴`, visible: true },
        { key: 6, label: `点位Z轴`, visible: true },
        { key: 7, label: `点位角度`, visible: true },
      ],
      //弹窗部分****************************************
      open: false,
      title: '',
      //新增的对象
      pointInfoDate: {
        PointId: undefined,
        MapID: undefined,
        dogId: undefined,
        dogName: undefined,
        PointName: undefined,
        PosX: undefined,
        PosY: undefined,
        PosZ: undefined,
        AngleYaw: undefined,
      },
      //地图列表
      MapIdOptions: [],
      //修改弹窗
      editOpen: false,
      //修改表单
      editPointInfo: {
        PointId: undefined,
        PointName: undefined
      },
      //导入的列表
      uploadPointList: [],
      uploadOpen: false,
      uploadTitle: "配置点位名称",
      dogInfo:{}
    };
  },
  created() {
    this.getMapIdOptions()
    this.getList()
  },
  computed: {
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
  },
  watch: {
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          const message = newMessage[newMessage.length - 1];
          if (message) {
            // console.log(JSON.stringify(message, null, 2));
            //获取基础信息后的行动
            this.handleBasicMessage(message);
          }
        }
      },
      immediate: true
    },
  },
  methods: {
    //获取地图列表
    getMapIdOptions() {
      getMapIdOptions().then(res=>{
        this.MapIdOptions = res.data.map(item=>({
          value: item.MapID,
          label: item.MapName,
        }))
      })
    },
    //获取机器人列表
    async getdogIdOptions(value) {
      //写接口(根据地图的id(value)查询机器人列表)
      try {
        const response = await searDogId(value);
        this.dogInfo = response.data
        this.$set(this.pointInfoDate, 'dogName', this.dogInfo.label);
        this.$set(this.pointInfoDate, 'dogId', this.dogInfo.value);
        // console.log(this.pointInfoDate)
      } catch (error) {
        this.$message.error('无法查询机器人列表');
      }
    },
    //新增or修改的表单重置
    reset() {
      this.pointInfoDate = {
        PointId: undefined,
        MapID: undefined,
        dogId: undefined,
        PointName: undefined,
        PosX: undefined,
        PosY: undefined,
        PosZ: undefined,
        AngleYaw: undefined,
      }
      this.resetForm("pointInfo");
    },
    //搜索区*********************************
    //搜索区点击搜索后
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    //点击重置后
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    //按钮区*****************************
    //点击新增按钮
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "增加点位"
      this.getMapIdOptions()
    },
    //点击删除按钮
    handleDelete(row) {
      const PointIds = row.PointId || this.ids;
      this.$modal.confirm('是否确认删除点位编号为"' + PointIds + '"的数据项？').then(function() {
        return delPoint(PointIds)
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    //点击导入按钮
    beforeUpload(file) {
      // Do something before upload
      return true; // Return false to prevent upload
    },
    triggerFileUpload() {
      this.$refs.upload.$el.querySelector('input[type="file"]').click();
    },
    //将json转为对象数组了
    handleFileChange(file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const jsonArray = JSON.parse(e.target.result);
          if (Array.isArray(jsonArray)) {
            this.uploadPointList = jsonArray
            this.handleJson()
          } else {
            this.uploadPointList = [];
            this.$modal.msgError('文件内容不是一个有效的对象数组')
          }
        } catch (error) {
          this.uploadPointList = [];
          this.$modal.msgError('解析 JSON 文件时出错')
        }
      };
      reader.readAsText(file.raw);
    },
    //获取到了json对象数组后
    async handleJson() {
      const mapId = this.uploadPointList[1].MapID
      await this.getdogIdOptions(mapId)
      // 使用 this.$set 来确保 PointName 是响应式的
      this.uploadPointList.forEach(obj => {
        this.$set(obj, 'PointName', '');  // 使用 this.$set
        if (this.dogInfo) {
          obj.dogId = this.dogInfo.value;
          obj.dogName = this.dogInfo.label;
        }
      })
      this.$nextTick(() => {
        // console.log('DOM 已经更新');
        this.uploadOpen = true
      });
    },
    //表格区********************************
    //初始化表格
    getList() {
      listPoint(this.queryParams).then(res => {
        // console.log(res)
        this.pointList = res.rows;
        this.total = res.total;
        this.loading = false;
        }
      );
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.PointId);
      this.multiple = !selection.length;
    },
    //修改按钮
    handleUpdate(row) {
      this.reset();
      const PointId = row.PointId;
      this.editPointInfo.PointId=row.PointId
      this.editOpen = true;
      this.title = "修改"+row.PointName+"点位名称";
    },
  //  弹窗区**************************************
    //新增按钮
    submitForm: function() {
      this.$refs["pointInfo"].validate(valid => {
        if (valid) {
          addPoint(this.pointInfoDate).then(response => {
            this.$modal.msgSuccess("新增成功");
            this.open = false;
            this.getList();
          });
        }
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    //修改按钮
    submitEditForm() {
      this.$refs["editPoint"].validate(valid => {
        if (valid) {
          updataPoint(this.editPointInfo).then(res=>{
            this.$message.success("修改成功");
            this.editCancel();
            this.getList();
          })
        }
      })
    },
    editCancel() {
      this.editOpen=false
      this.editPointInfo={PointId: undefined,PointName: undefined}
    },
    //提 交
    Uploadsubmit() {
      if (this.uploadPointList.some(item => !item.PointName)) {
        this.$modal.msgError('请为所有点位填写名称');
        return;
      }
      importPoint(this.uploadPointList).then(res=>{
        this.$modal.msgSuccess('成功批量导入')
        this.uploadOpen = false
        this.getList()
      })

    },
    //取消
    uploadCancel() {
      this.uploadOpen = false
      this.uploadPointList = []
    },
    handleBasicMessage(message) {
      //获取到了点的信息以后传给后端去存储打点
      if (message && message.items && message.items.posX != null && message.items.posY != null && message.items.posZ != null) {
        this.pointInfoDate.PosX = message.items.posX;
        this.pointInfoDate.PosY = message.items.posY;
        this.pointInfoDate.PosZ = message.items.posZ;
        this.pointInfoDate.AngleYaw = message.items.angleYaw
      } else {
        this.$message.error('位置信息格式不正确');
      }
    },
  }
};
</script>
<style lang="scss" scoped>
.uploadRow {
  height: 60px;
  .uploadCol {
    display: flex;
    .labelName {
      width: 30%;
      font-size: 18px;
      text-align: center;
      //color: #696969;
    }
  }
}

</style>
