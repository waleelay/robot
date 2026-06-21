export default {
  data() {
    return {
      videoObj: {
        // 数量
        totalCount: 2,
        // 当前播放进度
        currentTime: '00:00:00',
        // 最大时长
        maxDuration: 0,
        // 进度0.1，1太大
        step: 0.1,
        // 时间线
        max: 100,
        inputValue: 0,
        // 总录制时长
        totalPeriod: 60,
        // 开始时间
        startTime: '13:00:00',
        // 结束时间
        endTime: '13:01:00',
      },
      // 视频监控
      videoList1: [
        {
          id: 1,
          // 单个视频总时长：秒
          period: 4,
          startTime: '13:00:11',
          endTime: '13:00:14',
          url: require('./video1.mp4')
        },
        {
          id: 2,
          period: 13,
          startTime: '13:00:15',
          endTime: '13:00:27',
          url: require('./video2.mp4')
        }
      ],
      // 红外监控
      videoList2: [
        {
          id: 3,
          // 单个视频总时长：秒
          period: 13,
          startTime: '13:00:03',
          endTime: '13:00:15',
          url: require('./video2.mp4')
        },
        {
          id: 4,
          period: 4,
          startTime: '13:00:31',
          endTime: '13:00:34',
          url: require('./video1.mp4')
        }
      ],
      // 点位
      points: [
        { posX: 0, posY: 0, period: 4, startTime: '13:00:10', endTime: '13:00:14' },
        { posX: 0.01, posY: 0.01, period: 10, startTime: '13:00:10', endTime: '13:00:14' },
        { posX: 0.02, posY: 0.02, period: 20, startTime: '13:00:10', endTime: '13:00:14' },
      ],
      warningList: [
        { createTime: '13:00:02', info: 'XXXX' },
        { createTime: '13:00:22', info: 'YYYY' },
        { createTime: '13:00:32', info: 'ZZZZ' },
      ],
      // 视频是否加载完成
      loadedStatusArr: [false, false],
      rtsp: require('./video1.mp4'),
      timer: null,
      isPlay: false,
      currentJkInfo: {},
      currentHwInfo: {},
    }
  },
  mounted() {
    this.videoObj.max = this.getTotalTime(this.videoObj.startTime, this.videoObj.endTime)
    setTimeout(() => {
      this.currentJkInfo = this.getVideo(this.videoList1, this.videoObj.startTime)
      this.currentHwInfo = this.getVideo(this.videoList2, this.videoObj.startTime)
    }, 2000);
  },
  methods: {
    getVideo(videoList, startTime) {
      const obj = videoList.filter(item => {
        return item.startTime <= startTime && item.endTime >= startTime
      })
      if (obj.length) {
        obj[0].startSecs = this.getTotalTime(this.videoObj.startTime, obj[0].startTime)
      }
      return obj.length ? obj[0] : {}
    },
    playPauseVideo1() {
      this.isPlay = !this.isPlay
      if (this.isPlay) {
        // 100ms加0.1,1秒加1
        const timeValue = Number(this.videoObj.inputValue)
        if (timeValue >= this.videoObj.max) {
          this.videoObj.inputValue = this.videoObj.step
        } else {
          this.videoObj.inputValue = timeValue + this.videoObj.step
        }
        this.handleInputTimeLine1(this.videoObj.inputValue)
        this.timer = setInterval(() => {
          this.videoObj.inputValue = (this.videoObj.inputValue * 10 + this.videoObj.step * 10) / 10
          if (((this.videoObj.inputValue * 10 + this.videoObj.step * 10)) / 10 >= this.videoObj.max) {
            this.videoObj.inputValue = this.videoObj.max
            clearInterval(this.timer)
            this.isPlay = !this.isPlay
          }
          this.handleInputTimeLine1(this.videoObj.inputValue)
        }, 100)
      } else {
        clearInterval(this.timer)
      }
    },
    // 时间换算成秒
    timeToSeconds(timeStr) {
      // console.log('+++++++++++++++++++++++++++++', timeStr);
      
      const [h, m, s] = timeStr.split(':').map(Number)
      return h * 3600 + m * 60 + s
    },
    // 计算结束和开始的秒数
    getTotalTime(startTime, endTime) {
      return this.timeToSeconds(endTime) - this.timeToSeconds(startTime)
    },
    // 改变时间轴
    handleInputTimeLine1(e) {
      if (this.loading) return;
      this.loading = true;      
      this.videoObj.currentTime = this.formatTime1(parseFloat(e));
      this.loadedStatusArr.map((item, index) => {
        const video = this.$refs['manualVideoRef' + (index + 1)].$refs.video
        // console.log(1);
          
        if (index === 0 && this.currentJkInfo.url) {
          video.currentTime = parseFloat(e) - this.currentJkInfo.startSecs
          // console.log(1, video.currentTime, parseFloat(e), this.currentJkInfo.startSecs);
          
        } else if (index === 1 && this.currentHwInfo.url) {
          video.currentTime = parseFloat(e) - this.currentHwInfo.startSecs
          // console.log(2, video.currentTime, parseFloat(e), this.currentHwInfo.startSecs);
        } else {
          video.currentTime = parseFloat(e)
        }
      })
      // if (e === 3.2) {
      //   this.rtsp2 = require('./video1.mp4')
      //   setTimeout(() => {
      //     this.$refs['manualVideoRef2'].$refs.video.currentTime = 2
      //   });
      // }
      // console.log('------------------', (e * 10 + 1) / 10);
      if (((e * 10 + 1) / 10) === 3) {
        // console.log(22);
        
      }
      const jkInfo = this.getVideo(this.videoList1, this.formatTime1(parseFloat((e * 10 + 1) / 10)))
      // console.log('------------------', this.videoObj.currentTime, jkInfo.url, this.currentJkInfo.url);
      
      if (jkInfo.url !== this.currentJkInfo.url) {
        this.currentJkInfo = jkInfo
      }
      const hwInfo = this.getVideo(this.videoList2, this.formatTime1(parseFloat((e * 10 + 1) / 10)))
      if (hwInfo.url !== this.currentHwInfo.url) {
        this.currentHwInfo = hwInfo
      }
      this.loading = false;
    },
    // 格式化时间显示 (秒 → hh:mm:ss)
    formatTime1(seconds) {
      const startSecs = this.timeToSeconds(this.videoObj.startTime)
      const totalSecs = Number(startSecs) + seconds
      const hours = Math.floor(totalSecs / 3600);
      let mins = Math.floor(totalSecs / 60);
      mins = mins >= 60 ? (mins % 60) : 0
      const secs = Math.floor(totalSecs % 60);
      return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    },
    changeVideo1(info) {
      const { type, index, currentTime } = info
      if (type === 'status') {
        this.loadedVideo(info)
      } else if (type === 'fullScreen') {
        this.fullScreen(index)
      } else if (type === 'time') {
        this.handleVideoTimeupdate(info)
      }
    },
    // 视频已加载
    loadedVideo(info) {
      // console.log(111, loadedVideo);
      // this.videoObj.max = this.getTotalTime(this.videoObj.endTime - this.videoObj.startTime)
      this.loadedStatusArr[info.index] = true
      // if (!this.loadedStatusArr.includes(false)) {
      //   let maxDuration = 0
      //   // 加载完成
      //   this.loadedStatusArr.map((item, index) => {
      //     const duration = this.$refs['manualVideoRef' + (index + 1)].$refs.video.duration
      //     if (maxDuration < duration) {
      //       maxDuration = duration
      //       this.videoObj.maxDuration = this.formatTime(maxDuration)
      //     }
      //     if (index === this.loadedStatusArr.length - 1) {
      //       this.videoObj.max = maxDuration
      //     }
      //   })
      // }
    },
    fullScreen(index) {
      const video = this.$refs['manualVideoRef' + (index + 1)].$refs.video
      if (video.requestFullScreen) {
        video.requestFullScreen()
      } else if (video.mozRequestFullScreen) {
        video.mozRequestFullScreen()
      } else if (video.webkitRequestFullScreen) {
        video.webkitRequestFullScreen()
      } else if (video.msRequestFullScreen) {
        video.msRequestFullScreen()
      }
    },
    // 视频播放改变时间轴
    handleVideoTimeupdate(info) {
      // console.log('============handleVideoTimeupdate=============');
      const { type, index, currentTime } = info
      // if (type === 'time') {
      //   console.log(currentTime);
      //   this.videoObj.inputValue = currentTime
      //   this.videoObj.currentTime = this.formatTime1(parseFloat(currentTime));
      //   this.formatTime1()
      // }
    }
  },
  beforeDestroy() {
    console.log('销毁video');
    
    if (this.timer) {
      clearInterval(this.timer)
    }
  }
}