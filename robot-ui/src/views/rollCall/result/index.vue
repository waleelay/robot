<template>
  <div class="app-container">
    <!--  查询条件-->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="机器人" prop="dogId">
        <el-select v-model="queryParams.dogId" placeholder="请选择">
          <el-option
            v-for="item in dogList"
            :key="item.value"
            :label="item.label"
            :value="item.value">
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围" prop="date">
        <el-date-picker
          v-model="date"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :picker-options="pickerOptions2">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <!--    按钮-->
    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList" :columns="columns"></right-toolbar>
    </el-row>
    <!--    表格-->
    <el-table v-loading="loading" :data="resultList">
      <el-table-column label="任务名称" align="center" key="TaskName" prop="TaskName" v-if="columns[0].visible" />
      <el-table-column label="任务路径" align="center" key="LineName" prop="LineName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="机器人" align="center" key="dogName" prop="dogName" v-if="columns[2].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="地图名称" align="center" key="MapName" prop="MapName" v-if="columns[3].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="到达率" align="center" key="ArrivedRate" prop="ArrivedRate" v-if="columns[4].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="开始时间" align="center" key="StartTime" prop="StartTime" v-if="columns[5].visible" :show-overflow-tooltip="true" />
      <el-table-column label="结束时间" align="center" key="EndTime" prop="EndTime" v-if="columns[6].visible" :show-overflow-tooltip="true"/>
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
            icon="el-icon-search"
            @click="lookRollCall(scope.row)"
          >查看点名情况</el-button>
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
    <!--    查看图片-->
    <el-dialog title="查看点名情况" :visible.sync="rollCallPersonOpen" width="1000px" append-to-body>
      <div class="dialog-content" :style="{ display: 'flex', width: '100%' }">
        <div class="table">
          <el-table v-loading="loading" :data="UserList">
            <el-table-column label="人员编号" align="center" key="UserId" prop="UserId" />
            <el-table-column label="人员姓名" align="center" key="UserName" prop="UserName"/>
            <el-table-column label="负责机器" align="center" key="dogName" prop="dogName"/>
            <el-table-column label="所处点位" align="center" key="PointName" prop="PointName"/>
            <el-table-column label="点到时间" align="center" key="ResultDate" prop="ResultDate" width="160"/>
            <el-table-column label="点名状态" align="center" key="isResult" prop="isResult"/>
            <el-table-column
              label="操作"
              align="center"
              class-name="small-padding fixed-width"
            >
              <template slot-scope="scope">
                <el-button
                  size="mini"
                  type="text"
                  icon="el-icon-search"
                  @click="lookPeople(scope.row)"
                >详细信息</el-button>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="peopleTotal>0"
            :total="peopleTotal"
            :page.sync="peopleQueryParams.pageNum"
            limit.sync= 10
            :pageSizes="pagesize"
            @pagination="getList"
          />
        </div>
        <div class="detail-card">
          <div class="card-content">
            <div v-if="showDetail" class="info-card">
              <img :src="getImageSrc(peopleCard.PictureData)" alt="个人信息图片" class="info-image" />
              <div class="info-details">
                <p><strong>姓名：</strong> {{ peopleCard.UserName }}</p>
                <p><strong>负责机器：</strong> {{ peopleCard.dogName }}</p>
                <p><strong>点到点位：</strong> {{ peopleCard.PointName }}</p>
                <p><strong>点到情况：</strong> {{ peopleCard.isResult }}</p>
                <!-- 可根据 rollCall 数据添加更多字段 -->
              </div>
            </div>
            <el-empty v-else description="点击查看详细信息查看"></el-empty>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getRollcallList,searchResultInfo } from '@/api/rollCall/result'
import { getRobotList} from '@/api/robot/robotMessage.js'
export default {
  name:"cruise",
  data() {
    return {
      //查询
      queryParams: {
        StartTime: undefined,
        EndTime: undefined,
        dogId: undefined,
        pageNum: 1,
        pageSize: 10,
      },
      dogParams: {
        dogState: 1,
        dogName: "",
        pageNum: 1,
        pageSize: 50,
      },
      pagesize:[10],
      peopleQueryParams: {
        TaskId:undefined,
        LineId:undefined,
        pageNum: 1,
        pageSize: 10,
      },
      peopleTotal: 0,
      date: [],
      // 列信息
      columns: [
        { key: 0, label: `任务名称`, visible: true },
        { key: 1, label: `任务路径`, visible: true },
        { key: 2, label: `机器人`, visible: true },
        { key: 3, label: `地图名称`, visible: true },
        { key: 4, label: `到达率`, visible: true },
        { key: 5, label: `开始时间`, visible: true },
        { key: 6, label: `结束时间`, visible: true },
      ],
      // 总条数
      total: 0,
      // 表格数据
      resultList: [],
      loading: true,
      showSearch: true,
      rollCallPersonOpen: false,
      peopleCard:{
        peopleUrl:``,
        UserId: undefined,
        UserName: undefined,
        Sex:undefined,
      },
      UserList:[],
      showDetail: false,
      dogList:[],
      pickerOptions2: {
        disabledDate: (time) => {
          // 禁用今天以后的日期
          const today = new Date();
          return time.getTime() > today.getTime(); // 禁用未来的日期
        },
      }
    }
  },
  async created() {
    // await this.getTime()
    await this.getList()
    this.getDogList()
  },
  methods: {
    getDogList() {
      getRobotList(this.dogParams).then(res => {
        this.dogList = res.rows.map(row => {
          return {
            value: row.dogId,
            label: row.dogName,
          }
        })
      })
    },
    getImageSrc(imgName) {
      return `${process.env.VUE_APP_BASE_API}/users/${imgName}.jpg`;
    },
    //时间初始化
    // getTime() {
    //   this.date = this.setDefaultDateRange();
    // },
    //设置初始时间
    // setDefaultDateRange() {
    //   const now = new Date();
    //   const yesterday = new Date(now);
    //   yesterday.setDate(now.getDate() - 1);
    //   yesterday.setHours(0, 0, 0, 0);
    //   return [yesterday, now];
    // },
    getList() {
      if (this.date.length > 0) {
        this.queryParams.StartTime = this.changeDate(this.date[0])
        this.queryParams.EndTime = this.changeDate(this.date[1])
      } else {
        this.queryParams.StartTime = null
        this.queryParams.EndTime = null
      }
      // console.log(this.queryParams)
      getRollcallList(this.queryParams).then(res => {
        // console.log(res)
        this.resultList = res.rows
        this.total = res.total
        this.loading = false
      })
    },
    lookRollCall(record) {
      //改
      // console.log(record)
      this.rollCallPersonOpen = true
      this.peopleQueryParams.TaskId = record.TaskId
      this.peopleQueryParams.LineId = record.LineId
      // console.log(this.peopleQueryParams)
      searchResultInfo(this.peopleQueryParams).then(res => {
        // console.log(res)
        this.UserList = res.rows.map(row => {
          return {
            ...row,
            isResult:row.Result===0 ? '未到':'已到'
          }
        })
        // console.log(this.UserList)
        this.peopleTotal = res.total
      })
    },

    changeDate(date) {
      var year = date.getFullYear(); //年
      var month = date.getMonth() + 1;//月
      var strDate = date.getDate();//日
      var hour = date.getHours();//时
      var minute = date.getMinutes();//分
      var second = date.getSeconds()
      month = month > 9 ? month : '0' + month
      strDate = strDate > 9 ? strDate : '0' + strDate
      hour = hour > 9 ? hour : '0' + hour
      minute = minute > 9 ? minute : '0' + minute
      second = second >9 ? second : '0' +second
      var newdate = year+'-'+month+'-'+strDate+' '+hour+':'+minute+ ':'+second
      return newdate
    },
    lookPeople(record) {
      this.showDetail = true
      // console.log(record)
      this.peopleCard = record
    },
    handleSwitch(row) {
      // console.log(row)
      //改!
      //传给后端id与isdeal
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.date = []
      this.queryParams.dogId = undefined,
      this.handleQuery();
    },
  }
}
</script>
<style>
.dialog-content {
  display: flex;
  justify-content: space-evenly;
  width: 100%;
}
.table {
  width: 75%;
}
.detail-card {
  width: 20%;
  border-radius: 4px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.12);
}
.info-card {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-evenly;
  align-items: center;
  background: rgba(255, 255, 255, 0.2);
  /* border: 1px solid #dcdcdc; */
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.info-image {
  width: 70%;
  height: 50%;
  border-radius: 20%;
  margin-bottom: 10px;
}
.info-details {
  height: 30%;
  text-align: left;
}
.info-details p {
  margin: 10px 0;
  font-size: 10px;
}

.card-content {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.info {
  margin-top: 10px;
  text-align: left;
}

.info p {
  margin: 5px 0;
}
</style>
