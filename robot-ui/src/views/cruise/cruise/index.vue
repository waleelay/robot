<template>
  <div class="app-container">
    <!--  查询条件-->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
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
      <el-table-column label="任务id" align="center" key="TaskId" prop="TaskId" v-if="columns[0].visible" />
      <el-table-column label="任务名" align="center" key="TaskName" prop="TaskName" v-if="columns[1].visible" :show-overflow-tooltip="true" />
      <el-table-column label="点位id" align="center" key="PointId" prop="PointId" v-if="columns[2].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="点位名" align="center" key="PointName" prop="PointName" v-if="columns[3].visible" :show-overflow-tooltip="true"/>
      <el-table-column label="创造时间" align="center" key="createTime" prop="createTime" v-if="columns[4].visible" :show-overflow-tooltip="true" />
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
            @click="lookImg(scope.row)"
          >查看图片</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-camera"
            @click="lookVideo(scope.row)"
          >查看视频</el-button>
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
    <el-dialog title="查看巡航图片" :visible.sync="pictureOpen" width="800px" append-to-body>
      <el-carousel class="imgdiv">
        <el-carousel-item v-for="(imgUrl,index) in cruiseImgList" :key="index">
          <img :src="imgUrl.PhotoUrl" alt="巡航图片" class="img"/>
        </el-carousel-item>
      </el-carousel>
    </el-dialog>
<!--    查看视频-->
    <el-dialog title="查看巡航视频" :visible.sync="videoOpen" width="800px" append-to-body>
      <video ref="videoPlayer" width="750" controls :key="cruiseVideoUrl">
        <source :src="cruiseVideoUrl" type="video/mp4">
        您的浏览器不支持视频播放。
      </video>
    </el-dialog>
  </div>
</template>

<script>
import { getCruiseList } from '@/api/cruise/resultandError'
export default {
  name:"cruise",
  data() {
    return {
      //查询
      queryParams: {
        StartTime: undefined,
        EndTime: undefined,
        pageNum: 1,
        pageSize: 10,
      },
      date: [],
      // 列信息
      columns: [
        { key: 0, label: `任务id`, visible: true },
        { key: 1, label: `任务名`, visible: true },
        { key: 2, label: `点位id`, visible: true },
        { key: 3, label: `点位名`, visible: true },
        { key: 4, label: `创造时间`, visible: true },
      ],
      // 总条数
      total: 2,
      // 表格数据
      resultList: null,
      loading: true,
      showSearch: true,
      pictureOpen: false,
      videoOpen: false,
      cruiseImgList: [],
      cruiseVideoUrl: null,
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
    //时间初始化
    // getTime() {
    //   // 设置时间范围：昨天晚上12点至当前时间
    //   this.date = this.setDefaultDateRange();
    // },
    //设置初始时间
    // setDefaultDateRange() {
    //   const now = new Date();
    //   // const oneHourAgo = new Date(now.getTime() - 60 * 60 * 1000); // 减去一个小时的毫秒数
    //   // return [oneHourAgo, now];
    //   const yesterday = new Date(now);
    //   // 设置昨天凌晨和前天凌晨
    //   yesterday.setDate(now.getDate() - 1);
    //   yesterday.setHours(0, 0, 0, 0); // 昨天的00:00
    //   // 设置 value1 和 value2 的默认值
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
      getCruiseList(this.queryParams).then(res => {
        // console.log(res)
        this.resultList = res.rows
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
    lookImg(record) {
      // console.log(record)
      this.pictureOpen = true
      this.cruiseImgList = record.images.map(img => {
        let PhotoUrl = "https://"+img.PhotoUrl
        return {
          PhotoUrl: PhotoUrl
        }
      })
      // console.log(this.cruiseImgList)
    },
    lookVideo(record) {
      console.log(record);
      this.videoOpen = true;
      this.cruiseVideoUrl = "https://" + record.videos[0].VideoUrl + "?t=" + new Date().getTime();
      // console.log(this.cruiseVideoUrl);

      // 强制重新加载视频
      this.$nextTick(() => {
        const video = this.$refs.videoPlayer;
        if (video) {
          video.load(); // 手动调用 load() 方法重新加载视频
        }
      });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.date = []
      this.handleQuery();
    },
  }
}
</script>
<style>
imgdiv {
  height: 800px;
}
.img {
  height: 100%;
  width: 100%;
}
</style>
