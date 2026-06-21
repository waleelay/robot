<template>
  <div>
    <div class="d-flex">
      <div class="luxiang-map flex1 mr10">
        <Map style="background: #ccc;" ref="mapRef1" />
      </div>
      <div class="videos">
        <div class="video-div">
          <div class="t3">周界巡逻机器狗上装设备-视频监控001</div>
          <LxVideo :rtsp="rtsp1" @changeVideo="changeVideo" :index="0" ref="manualVideoRef1" style="width: 492px; height: 246px; background: #797979; cursor: pointer;"/>
        </div>
        <div class="infrared-div mt10">
          <div class="t3">周界巡逻机器狗上装设备-红外探测</div>
          <LxVideo :rtsp="rtsp2" @changeVideo="changeVideo" :index="1" :isRstp="true" ref="manualVideoRef2" style="width: 492px; height: 246px; background: #797979; cursor: pointer;"/>
        </div>
        <div class="gj mt10 wp492">
          <div class="mm_table_box table">
            <el-table :data="gjData.tableData.data" ref="dataTableRef2" border class="gj-table">
              <el-table-column type="index" key="index" width="50" label="序号" align="center">
                <template slot-scope="scope">
                  {{ (gjData.tableData.page - 1) * gjData.tableData.size + scope.$index + 1 }}
                </template>
              </el-table-column>
              <el-table-column prop="ErrorType" key="ErrorType" label="告警类型" show-overflow-tooltip>
                <template slot-scope="scope">
                  {{ scope.row.ErrorType === 0 ? '业务告警' : scope.row.ErrorType === 1 ? '任务告警': '设备告警' }}
                </template>
              </el-table-column>
              <el-table-column prop="ErrorName" key="ErrorName" label="告警内容" show-overflow-tooltip></el-table-column>
              <el-table-column prop="CreateTime" key="CreateTime" label="告警时间" show-overflow-tooltip></el-table-column>
            </el-table>
            <el-pagination
              v-if="gjData.tableData.total > gjData.tableData.size"
              v-model:current-page="gjData.tableData.page"
              background
              :page-size="gjData.tableData.size"
              :total="gjData.tableData.total"
              layout="prev, pager, next"
              class="mt10"
              />
              <!-- @current-change="e => getWarnList(e, 'page')" -->
          </div>
        </div>
      </div>
    </div>
    <div class="custom-controls p15 mt20">
      <!-- <input class="timeline" type="range" min="0" max="100" value="50" step="1" @input="handleInputTimeLine" /> -->
      <el-input class="timeline" :style="{ '--fill-percent': (videoObj.inputValue / videoObj.max) * 100 + '%' }" type="range" :min="0" :max="videoObj.max" v-model="videoObj.inputValue" :step="0.1" @input="handleInputTimeLine" />
      <div class="time-display ml20">
        <span class="current-time">{{ videoObj.currentTime }}</span> / <span id="duration">{{ videoObj.maxDuration }}</span>
      </div>
      <el-button type="primary" @click="playPauseVideo" class="play-btn ml20">播放/暂停</el-button>
    </div>
  </div>
</template>

<script>
import LxVideo from './video.vue'

import Map from '../../map2.vue';
export default {
  name: 'luxiang',
  components: { LxVideo, Map },
  data() {
    return {
      jobId: '',
      info: {},
      // rtsp1: require('./video1.mp4'),
      // rtsp2: require('./video2.mp4'),
      rtsp1: '',
      rtsp2: '',
      // rtsp1: '',
      // rtsp2: '',
      loadedStatusArr: [false, false],
      gjData: {
        tableData: {
          data: [
            { name: '巡逻机器狗', ErrorType: 0, ErrorTime: '15:30', ErrorName: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
            { name: '点名机器人', ErrorType: 1, ErrorTime: '15:30', ErrorName: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
            { name: '公服机器人', ErrorType: 2, ErrorTime: '15:30', ErrorName: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '系统误报'},
            { name: '跟踪机器狗', ErrorType: 1, ErrorTime: '15:30', ErrorName: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处置'},
            { name: '公服机器人', ErrorType: 0, ErrorTime: '15:30', ErrorName: 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', status: '已处置'},
          ],
          total: 10,
          size: 5,
          page: 1
        }
      },
      videoObj: {
        totalCount: 2,
        currentTime: 0,//当前播放进度
        maxDuration: 0,//最大时长
        // 时间线
        max: 100,
        inputValue: 0
      },
      loading: false
    }
  },
  mounted() {},
  methods: {
    // 播放暂停
    playPauseVideo() {
      const paused = this.$refs.manualVideoRef1.$refs.video.paused
      this.loadedStatusArr.map((item, index) => {
        const video = this.$refs['manualVideoRef' + (index + 1)].$refs.video
        if (paused) {
          video.play()
        } else {
          video.pause()
        }
      })
    },
    changeVideo(info) {
      const { type, index, currentTime } = info
      if (type === 'status') {
        this.loadedStatusArr[index] = true
        if (!this.loadedStatusArr.includes(false)) {
          let maxDuration = 0
          // 加载完成
          this.loadedStatusArr.map((item, index) => {
            
            const duration = this.$refs['manualVideoRef' + (index + 1)].$refs.video.duration
            if (maxDuration < duration) {
              maxDuration = duration
              this.videoObj.maxDuration = this.formatTime(maxDuration)
            }
            if (index === this.loadedStatusArr.length - 1) {
              this.videoObj.max = maxDuration
            }
          })
        }
      }
      if (type === 'time') {
      // console.log('时间', currentTime);
      //   if (this.loading) return;
      //   this.loading = true;
      //   this.videoObj.inputValue = currentTime;
      //   this.videoObj.currentTime = this.formatTime(currentTime);
      //   this.loading = false;
      }

      if (type === 'fullScreen') {
        console.log(111);
        
        const video = this.$refs['manualVideoRef' + (index + 1)].$refs.video
        console.log(222);
        if (video.requestFullScreen) {
          video.requestFullScreen()
        } else if (video.mozRequestFullScreen) {
          video.mozRequestFullScreen()
        } else if (video.webkitRequestFullScreen) {
          video.webkitRequestFullScreen()
        } else if (video.msRequestFullScreen) {
          video.msRequestFullScreen()
        }
      }
    },
    // 控制
    handleInputTimeLine(e) {
      console.log('handleInputTimeLine', e);
      if (this.loading) return;
      this.loading = true;
      this.loadedStatusArr.map((item, index) => {
        const video = this.$refs['manualVideoRef' + (index + 1)].$refs.video
        video.currentTime = parseFloat(e)
      })
      
      this.videoObj.currentTime = this.formatTime(parseFloat(e));
      this.loading = false;
    },
    // 格式化时间显示 (秒 → mm:ss)
    formatTime(seconds) {
      const mins = Math.floor(seconds / 60);
      const secs = Math.floor(seconds % 60);
      return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    },
    // 在时间轴上添加关键帧标记
    // renderKeyframes() {
    //   const keyframes = [5, 15, 30]; // 示例关键帧时间点
    //   const timeline = document.getElementById('timeline');
    //   const style = document.createElement('style');
      
    //   let css = '';
    //   keyframes.forEach(time => {
    //     const percentage = (time / maxDuration) * 100;
    //     css += `
    //       #timeline::-webkit-slider-runnable-track {
    //         background-image: linear-gradient(
    //           to right,
    //           #4CAF50 0%,
    //           #4CAF50 ${percentage}%,
    //           #ddd ${percentage}%,
    //           #ddd 100%
    //         );
    //       }
    //     `;
    //   });
      
    //   style.textContent = css;
    //   document.head.appendChild(style);
    // }
  },
  // watch: {
  //   loadedStatus: function (newVal, oldVal) {
  //     console.log('变化了');
      
  //   }
  // }
  watch: {
    // 加载视频
    rtsp1: {
      handler: function(rtsp) {
        // console.log(11, rtsp);
        // this.$refs.manualVideoRef1.$refs.video.load()
      },
      // immediate: true
    },
    rtsp2: {
      handler: function(rtsp) {
        // console.log(22, rtsp);
        // this.$refs.manualVideoRef2.$refs.video.load()
      },
      // immediate: true
    }
  }
}
</script>

<style lang="scss" scoped>
::v-deep {
  .luxiang-map {
    background: #ccc;
    .middle_center {
      padding: 0 !important;
      .image {
        max-width: 1088px;
        height: 800px;
      }
    }
  }

}
.t3 {
  color: #fff;
  font-size: 16px;
  line-height: 24px;
  background: #333;
}
::v-deep {
  .el-table {
    &.gj-table {
      height: 200px;
      border-left: none;
      .el-table__header-wrapper, .el-table__body-wrapper, .el-table__footer-wrapper {
        border-left: 1px solid #797979;
      }
      .el-table__body-wrapper {
        height: calc(100% - 32px);
        overflow-y: auto;
      }
    }
  }
}

::v-deep {
  .el-input {
    padding: 0 !important;
    &__inner {
      height: 8px;
      padding: 0 !important;
      line-height: 8px;
      // border: 1px solid #2f2f2f;
      background: transparent;
    }
    input[type=range] {
      width: 100%;
      height: 8px;
      // border: 1px solid #2f2f2f;
      // appearance: none;
      -webkit-appearance: none;
      background: #ddd;
      border-radius: 4px;
      outline: none;
      &::-webkit-slider-runnable-track {
        // background: #0079fe;
      }
      &::-webkit-slider-thumb {
        // appearance: none;
        -webkit-appearance: none;
        width: 16px;
        height: 16px;
        background: #0079fe;
        border-radius: 50%;
        cursor: pointer;
        transition: all 0.2s;
        // border: 1px solid #0079fe;
        margin-top: -4px;
      }
      &::-moz-range-thumb {
        width: 16px;
        height: 16px;
        background: #0079fe;
        border-radius: 50%;
        cursor: pointer;
        transition: all 0.2s;
        margin-top: -3px;
      }
    }
    /* 填充进度部分的样式 */
    input[type=range]::-webkit-slider-runnable-track {
      height: 10px;
      background: linear-gradient(to right, #0079fe var(--fill-percent), #ddd var(--fill-percent));
      // background: linear-gradient(to right, #0079fe 50%, #ddd 50%);
      border-radius: 4px;
    }
  }
}
input[type=range] {
  height: 16px;
  border: 1px solid #2f2f2f;
  appearance: none;
  background: transparent;
  &::-webkit-slider-runnable-track {
    background: #0079fe;
  }
  &::-moz-range-track {
    // accent-color: #0079fe;
  }
  &::-webkit-slider-thumb {
    appearance: none;
    background: #0079fe;
  }
  &::-webkit-slider-runnable-track {
    // background-image: linear-gradient(
    //   to right,
    //   #4CAF50 0%,
    //   #4CAF50 ${percentage}%,
    //   #ddd ${percentage}%,
    //   #ddd 100%
    // );
  }
}

.custom-controls {
  display: flex;
  align-items: center;
  padding: 10px;
  background: #f0f0f0;
  border-radius: 5px;

  .timeline {
    flex-grow: 1;
  }
  .time-display {
    width: 170px;
    font-family: monospace;
    font-size: 14px;
    text-align: center;
  }
  .play-btn {
    padding: 10px 15px;
    cursor: pointer;
  }
}
</style>