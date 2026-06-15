<template>
  <div class="app-container">
    <!--    查询条件-->
    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="点位名称" prop="PointName">
        <el-input v-model="queryParams.PointName" placeholder="请输入点名称" clearable :style="{width: '240px'}">
        </el-input>
      </el-form-item>
      <el-form-item label="对应地图" prop="MapID">
        <el-select v-model="queryParams.MapID" placeholder="请选择对应地图" clearable :style="{width: '240px'}">
          <el-option v-for="(item, index) in MapIDOptions" :key="index" :label="item.label"
                     :value="item.value" :disabled="item.disabled"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <!--    按钮（导入）-->
    <el-row :gutter="10" class="mb8">
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
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>
    <!--    表格-->
    <el-table v-loading="loading" :data="pointPeopleList" v-if="pointPeopleList" >
      <el-table-column label="点位编号" align="center" key="PointId" prop="PointId" v-if="columns[0].visible" />
      <el-table-column label="点位名称" align="center" key="PointName" prop="PointName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="对应地图" align="center" key="MapName" prop="MapName" v-if="columns[2].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="配置人员" align="center" key="peoples" prop="peoples" v-if="columns[3].visible" :show-overflow-tooltip="true">
        <template slot-scope="scope">
          <span>{{ scope.row.peoples.map(person => person.UserName).join(', ') }}</span>
        </template>
      </el-table-column>
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
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
          >配置人员</el-button>
        </template>
      </el-table-column>
    </el-table>
    <!--    分页-->
    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="handlePage"
    />
    <!--    弹窗-->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-row :gutter="15">
        <el-form ref="elForm" :model="pointInfoDate" size="medium" label-width="68px">
          <el-col :span="24">
            <el-form-item label="配置人员" prop="peoples">
              <div>
                <el-tag
                  v-for="(person, index) in pointInfoDate.peoples"
                  :key="person.UserId"
                  closable
                  @close="removePerson(index)"
                  :disable-transitions="false"
                >
                  {{ person.UserName }}
                </el-tag>
              </div>
            </el-form-item>
          </el-col>
        </el-form>
        <!--        弹窗里面对表格的查询-->
        <el-form :model="dialogQueryParams" ref="dialogQueryForm" size="small" :inline="true" v-show="showSearch" label-width="78px">
          <el-form-item label="犯人编号" prop="userId">
            <el-input
              v-model="dialogQueryParams.userId"
              placeholder="请输入查询编号"
              clearable
              style="width: 200px"
              @keyup.enter.native="dialogHandleQuery"
            />
          </el-form-item>
          <el-form-item label="犯人姓名" prop="userName">
            <el-input
              v-model="dialogQueryParams.userName"
              placeholder="请输入查询姓名"
              clearable
              style="width: 200px"
              @keyup.enter.native="dialogHandleQuery"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="dialogHandleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="dialogResetQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <!--弹窗里面的表格-->
        <el-table ref="elTable" v-loading="dialogLoading" :data="userList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" align="center" />
          <el-table-column label="编号" align="center" key="UserId" prop="UserId" v-if="columns[0].visible" />
          <el-table-column label="姓名" align="center" key="UserName" prop="UserName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
          <el-table-column label="是否为重要人员" align="center" key="IsImportant" prop="IsImportant" v-if="columns[2].visible" :show-overflow-tooltip="true">
            <template slot-scope="scope">
              <span>{{ scope.row.IsImportant === 0 ? '否' : '是' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="犯罪性质" align="center" key="CrimeType" prop="CrimeType" v-if="columns[3].visible" :show-overflow-tooltip="true" />
        </el-table>
        <!--弹窗里面的分页-->
        <pagination
          v-show="dialogTotal>0"
          :total="dialogTotal"
          :page.sync="dialogQueryParams.pageNum"
          :limit.sync= "dialogQueryParams.pageSize"
          @pagination="getPeopleList"
        />
      </el-row>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getMapIdOptions } from '@/api/robot/point'
import { getToken } from '@/utils/auth'
import { getppList,setPeopleforPoint,getTotal,getUsefulUserList,getExportPointUserList } from '@/api/rollCall/pointPeople'
import * as XLSX from 'xlsx';
import { saveAs } from 'file-saver';
export default {
  name: "pointPeople",
  data() {
    return {
      //  查询
      showSearch: true,
      queryParams: {
        pageNum: 1,
        pageSize: 5,
        PointName: undefined,
        MapID: 0,
      },
      MapIDOptions: [],
      //表格部分************************************
      loading: true,
      pointPeopleList: [],
      list:[],
      //总数
      total: 2,
      // 列信息
      columns: [
        { key: 0, label: `点位编号`, visible: true },
        { key: 1, label: `点位名称`, visible: true },
        { key: 2, label: `对应地图`, visible: true },
        { key: 3, label: `配置人员`, visible: true },
      ],
      //弹窗部分****************************************
      open: false,
      title: '',
      pointInfoDate: {
        PointId: undefined,
        PointName: undefined,
        MapID: undefined,
        MapName: undefined,
        dogId: undefined,
        peoples: []      //存
      },
      selectedRows: [], // 存储当前选中的行
      userList: [],
      //查询用户参数
      dialogQueryParams: {
        pageNum: 1,
        pageSize: 5,
        userId: undefined,
        userName: undefined,
      },
      dialogTotal: 2,
      dialogLoading: true,
      uploadUrl: `${process.env.VUE_APP_BASE_API}/PointAndUser/uploadPointAndUser`,
      exportUserList: [],
    };
  },
  created() {
    this.getList()
    this.getMapIdOptions()
  },
  mounted() {
    const XLSX = require('xlsx')
  },
  methods: {
    //导入和导出
    // 触发文件选择框
    triggerFileInput() {
      this.$refs.fileInput.click();
    },
    // 处理文件上传
    submitUpload(event) {
      const selectedFile = event.target.files[0];
      if (!selectedFile) {
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
          this.$refs.fileInput.value = null; // 清空文件输入
          this.getList()
        })
        .then(data => {
          this.$message.success('文件上传成功');
        })
        .catch(error => {
          this.$message.error('文件上传失败');
          this.$refs.fileInput.value = null;
        });
    },
    //点击导出操作 1.获取要导出的数据
    handleExport() {
      getExportPointUserList(this.queryParams).then(res => {
        this.exportUserList = res.data;
        this.submitExport()
      })
    },
    // 2. 将获取到的数据转成excel
    submitExport() {
      const sheetData = [
        ['地图编号', '地图名称', '用户编号', '用户姓名', '点位编号','点位名称'], // 表头
        ...this.exportUserList.map(item => [
          item.MapID,
          item.MapName,
          item.UserId,
          item.UserName,
          item.PointId,
          item.PointName,
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
      saveAs(new Blob([wbout], { type: 'application/octet-stream' }), '点位人员配置.xlsx');
    },
    //获取地图选择列表
    getMapIdOptions() {
      getMapIdOptions().then(res=>{
        this.MapIDOptions = res.data.map(item=>({
          value: item.MapID,
          label: item.MapName,
        }))
      })
    },
    //新增or修改的表单重置
    reset() {
      this.pointInfoDate = {
        PointId: undefined,
        MapID: undefined,
        PointName: undefined,
        MapName: undefined,
        peoples: [],
      }
      this.resetForm("elForm");
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
    //导入
    //表格区********************************
    //初始化表格
    getList() {
      getppList(this.queryParams).then(res => {
        if (res && res.rows && Array.isArray(res.rows)) {
          this.pointPeopleList = res.rows.map(item => ({
            PointId: item.Point.PointId,
            PointName: item.Point.PointName,
            MapID: item.Point.MapID,
            MapName: item.Point.MapName,
            dogId: item.Point.dogId,
            peoples: item.Users
          }));
        } else {
          this.pointPeopleList = []; // 或者处理异常情况
        }
        getTotal(this.queryParams).then(res => {
          this.total = res
        })
        this.loading = false;
      }).catch(error => {
        this.pointPeopleList = [];
        this.loading = false;
      });
    },
    //弹窗-------------------------------------------------
    //  点击配置人员按钮
    handleUpdate(row) {
      this.reset();
      this.open = true;
      this.title = "配置 "+row.PointName+" 点位人员信息"
      this.pointInfoDate.PointId = row.PointId
      this.pointInfoDate.PointName = row.PointName
      this.pointInfoDate.MapID = row.MapID
      this.pointInfoDate.MapName = row.MapName
      this.pointInfoDate.dogId = row.dogId
      this.pointInfoDate.peoples = row.peoples
      this.getPeopleList()
    },
    //获取人员列表以供选择
    getPeopleList() {
      getUsefulUserList(this.dialogQueryParams).then(res=> {
        this.userList = res.rows;
        // console.log(this.userList)
        this.dialogTotal = res.total
        this.dialogLoading = false
        this.$nextTick(this.updateTableSelection)
      })
    },
    //页数变了or一页显示的多了
    handlePage() {
      this.getList()
    },
    //表格选择改变了
    handleSelectionChange(newSelectedRows) {
      const previousSelectedRows = this.selectedRows;
      const selected = newSelectedRows.filter(
        (row) => !previousSelectedRows.includes(row)
      ); // 选中的数据
      const deselected = previousSelectedRows.filter(
        (row) => !newSelectedRows.includes(row)
      ); // 取消选中的数据

      //将新选中的数据加进去
      if (selected.length > 0) {
        let existingIds = new Set(this.pointInfoDate.peoples.map(item => item.UserId))
        selected.forEach(item => {
          if(!existingIds.has(item.UserId)) {
            this.pointInfoDate.peoples.push(item);
          }
        })
      }
      //将需要删除的数据删去
      if (deselected.length > 0 ) {
        let idsToDelete = new Set(this.userList.map(item => item.UserId));
        let filtered = []
        this.pointInfoDate.peoples.forEach(item => {
          let foundIn = deselected.some(deleteId =>deleteId.UserId ===item.UserId )
          if (!foundIn || !idsToDelete.has(item.UserId)) {
            filtered.push(item)
          }
        })
        this.pointInfoDate.peoples = filtered
      }
      this.selectedRows = newSelectedRows;
    },

    // 根据 pointInfoDate.peoples 更新 table 选中项
    updateTableSelection() {
      this.$nextTick(() => {
        const selectedUserIds = this.pointInfoDate.peoples.map(person => person.UserId);
        const table = this.$refs.elTable;
        if (table) {
          table.clearSelection();
          this.userList.forEach(user => {
            if (selectedUserIds.includes(user.UserId)) {
              table.toggleRowSelection(user, true);
            }
          });
        }
      });
    },
    //移除el-tag里面的值
    removePerson(index) {
      this.pointInfoDate.peoples.splice(index, 1);
      this.updateTableSelection();
    },
    // 添加人员到 pointInfoDate.peoples
    addPeople(users) {
      this.pointInfoDate.peoples = [...this.pointInfoDate.peoples, ...users];
      this.updateTableSelection(); // 添加后更新 table 选中项
    },
    //弹窗中查询人员
    dialogHandleQuery() {
      this.dialogQueryParams.pageNum = 1;
      this.getPeopleList()
    },
    // /** 重置按钮操作 */
    dialogResetQuery() {
      this.resetForm("dialogQueryForm");
      this.dialogHandleQuery();
    },
    submitForm() {
      setPeopleforPoint(this.pointInfoDate).then(() => {
        this.$modal.msgSuccess("配置人员成功");
        this.open = false;
        this.getList();
        this.dialogLoading=true
      });
    },
    cancel() {
      this.open = false;
      this.dialogLoading=true
      this.reset()
    }
  }
};
</script>
