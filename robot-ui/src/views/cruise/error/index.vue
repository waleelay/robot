<template>
  <div class="app-container">
    <!--  查询条件-->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="异常类型" prop="ErrorType">
        <el-select v-model="queryParams.ErrorType" placeholder="请选择">
        <el-option
          v-for="item in errorTypeOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value">
        </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="是否处理" prop="IsDeal">
        <el-select v-model="queryParams.IsDeal" placeholder="请选择">
          <el-option
            v-for="item in dealOptions"
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
      <el-table-column label="异常类型" align="center" key="errorType" prop="errorType" v-if="columns[0].visible" />
      <el-table-column label="异常名称" align="center" key="ErrorName" prop="ErrorName" v-if="columns[0].visible" />
      <el-table-column label="机器人" align="center" key="dogName" prop="dogName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="地图名称" align="center" key="MapName" prop="MapName" v-if="columns[2].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="点位名称" align="center" key="PointName" prop="PointName" v-if="columns[3].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="发现时间" align="center" key="CreateTime" prop="CreateTime" v-if="columns[4].visible" :show-overflow-tooltip="true" />
     <el-table-column label="已处理" align="center" key="deal" prop="deal" v-if="columns[5].visible">
        <template slot-scope="scope">
          <el-switch v-model="scope.row.deal" @change="handleSwitch(scope.row)"></el-switch>
        </template>
      </el-table-column>
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
            icon="el-icon-picture-outline"
            @click="lookerror(scope.row)"
          >查看异常</el-button>
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
    <el-dialog title="查看异常情况" :visible.sync="pictureOpen" width="800px" append-to-body>
      <img :src="errorImg" class="errorImg"/>

    </el-dialog>
  </div>
</template>

<script>
import { getCruiseErrorList,setErrorisDeal } from '@/api/cruise/resultandError'
export default {
  name:"cruise",
  data() {
    return {
      errorTypeOptions:[{value:0,label:'普通异常'},{value:1,label:'紧急异常'}],
      dealOptions:[{value:1,label:'已处理'},{value:0,label:'未处理'}],
      //查询
      queryParams: {
        StartTime: undefined,
        EndTime: undefined,
        ErrorType: undefined,
        IsDeal: undefined,
        pageNum: 1,
        pageSize: 10,
      },
      date: [],
      // 列信息
      columns: [
        { key: 0, label: `异常类型`, visible: true },
        { key: 1, label: `机器人`, visible: true },
        { key: 2, label: `地图名称`, visible: true },
        { key: 3, label: `点位名称`, visible: true },
        { key: 4, label: `发现时间`, visible: true },
        { key: 5, label: `是否处理`, visible: true },
      ],
      // 总条数
      total: 2,
      // 表格数据
      resultList: null,
      loading: true,
      showSearch: true,
      pictureOpen: false,
      errorImg:``,
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
  },
  methods: {
    getList() {
      if (this.date.length > 0) {
        this.queryParams.StartTime = this.changeDate(this.date[0])
        this.queryParams.EndTime = this.changeDate(this.date[1])
      } else {
        this.queryParams.StartTime = null
        this.queryParams.EndTime = null
      }
      // console.log(this.queryParams)
      //改！
      getCruiseErrorList(this.queryParams).then(res => {
        // console.log(res)
        this.resultList = res.rows.map(item =>{
          return {
            ...item,
            deal:item.IsDeal === 1? true:false,
            errorType: item.ErrorType===1? '紧急异常':'普通异常'
          }
        })
        console.log(this.resultList)
        this.total = res.total
        this.loading = false
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
    lookerror(record) {
      this.errorImg ="https://"+record.ImageUrl;
      this.$nextTick(() => {
        this.pictureOpen = true;
      });
    },
    handleSwitch(row) {
      let query = {
        ErrorId:row.ErrorId,
        IsDeal: row.deal?1:0}
      // console.log(query)
      setErrorisDeal(query).then(res => {
        this.$message.success('成功切换异常状态')
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.queryParams.pageSize = 10
      this.getList();
    },
    resetQuery() {
      this.date=[];
      this.queryParams.ErrorType = undefined
      this.queryParams.IsDeal = undefined
      this.handleQuery();
    },
  }
}
</script>
<style>
imgdiv {
  height: 800px;
}
.errorImg {
  height: 100%;
  width: 100%;
}
</style>
