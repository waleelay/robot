export default {
  data() {
    return {
      videoObj: {
        // 数量
        totalCount: 2,
        // 当前播放进度
        currentTime: "",
        // currentTime: "2025-07-29 13:00:00",
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
        startTime: "2025-07-29 13:00:00",
        // 结束时间
        endTime: "2025-07-29 13:01:00",
      },
      // 视频监控
      videoList1: [
        // {
        //   id: 1,
        //   // 单个视频总时长：秒
        //   period: 4,
        //   startTime: '2025-07-29 13:00:11',
        //   endTime: '2025-07-29 13:00:14',
        //   url: require('./video1.mp4')
        // },
        // {
        //   id: 2,
        //   period: 13,
        //   startTime: '2025-07-29 13:00:15',
        //   endTime: '2025-07-29 13:00:27',
        //   url: require('./video2.mp4')
        // }
      ],
      // 红外监控
      videoList2: [
        // {
        //   id: 3,
        //   // 单个视频总时长：秒
        //   period: 13,
        //   startTime: "2025-07-29 13:00:03",
        //   endTime: "2025-07-29 13:00:15",
        //   url: require("./video2.mp4"),
        // },
        // {
        //   id: 4,
        //   period: 4,
        //   startTime: "2025-07-29 13:00:31",
        //   endTime: "2025-07-29 13:00:34",
        //   url: require("./video1.mp4"),
        // },
      ],
      // 点位
      points: [
        // ['0.294077, 0.22317'], ['1.48185, 1.89352'], ['1.62115, 8.08522'], ['1.4151, 7.86176'], ['1.52195, 1.62047'], ['0.0322144, 0.271276']
        // {
        //   posX: -0.294077,
        //   posY: -0.22317,
        //   waitTime: 0,
        //   startTime: "2025-07-29 13:00:01",
        //   endTime: "2025-07-29 13:00:10",
        // },
        // {
        //   posX: -1.48185,
        //   posY: -1.89352,
        //   waitTime: 5,
        //   startTime: "2025-07-29 13:00:11",
        //   endTime: "2025-07-29 13:00:20",
        // },
        // {
        //   posX: -1.62115,
        //   posY: -8.08522,
        //   waitTime: 10,
        //   startTime: "2025-07-29 13:00:20",
        //   endTime: "2025-07-29 13:00:30",
        // },
        // {
        //   posX: 1.4151,
        //   posY: -7.86176,
        //   waitTime: 5,
        //   startTime: "2025-07-29 13:00:30",
        //   endTime: "2025-07-29 13:00:35",
        // },
        // {
        //   posX: 1.52195,
        //   posY: -1.62047,
        //   waitTime: 0,
        //   startTime: "2025-07-29 13:00:35",
        //   endTime: "2025-07-29 13:00:40",
        // },
        // {
        //   posX: 0.0322144,
        //   posY: -0.271276,
        //   waitTime: 2,
        //   startTime: "2025-07-29 13:00:40",
        //   endTime: "2025-07-29 13:00:59",
        // },
      ],
      pointObj: {},
      warningList: [
        // {
        //   alarmTime: "2025-07-29 13:00:02",
        //   alarmContent: "XXXX",
        //   alarmType: "任务告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:12",
        //   alarmContent: "YYYY",
        //   alarmType: "业务告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:12",
        //   alarmContent: "1111",
        //   alarmType: "业务告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:13",
        //   alarmContent: "2222",
        //   alarmType: "业务告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:14",
        //   alarmContent: "33333",
        //   alarmType: "业务告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:15",
        //   alarmContent: "4444",
        //   alarmType: "业务告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:32",
        //   alarmContent: "ZZZZ",
        //   alarmType: "设备告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:32",
        //   alarmContent: "AAAA",
        //   alarmType: "设备告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:32",
        //   alarmContent: "BBBB",
        //   alarmType: "设备告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:32",
        //   alarmContent: "CCCC",
        //   alarmType: "设备告警",
        // },
        // {
        //   alarmTime: "2025-07-29 13:00:32",
        //   alarmContent: "EEE",
        //   alarmType: "设备告警",
        // },
      ],
      tableData: [],
      // 视频是否加载完成
      loadedStatusArr: [false, false],
      timer: null,
      isPlay: false,
      currentJkInfo: {},
      currentHwInfo: {},
      mapInfo: {
        // X0: -3.1,
        // Y0: -15,
        // currentImage: require("./../../../path/李磊的地图.jpg"),
        X0: 0,
        Y0: 0,
        currentImage: "",
      },
      pointInfo: { posX: 0, posY: 0 },
      // 路径记录
      linePoints: [],
    };
  },
  mounted() {
    // this.getMapSize();
    // this.getAllPoints();
    // this.videoObj.max = this.getTotalTime(
    //   this.videoObj.startTime,
    //   this.videoObj.endTime
    // );
    // setTimeout(() => {
    //   this.currentJkInfo = this.getVideo(
    //     this.videoList1,
    //     this.videoObj.startTime
    //   );
    //   this.currentHwInfo = this.getVideo(
    //     this.videoList2,
    //     this.videoObj.startTime
    //   );
    // }, 2000);
  },
  methods: {
    // 根据时间获取视频
    getVideo(videoList, startTime) {
      // recordedEndedAt
      const obj = videoList.filter((item) => {
        return item.startTime <= startTime && item.endTime >= startTime;
      });
      if (obj.length) {
        obj[0].startSecs = this.getTotalTime(
          this.videoObj.startTime,
          obj[0].startTime
        );
      }
      return obj.length ? obj[0] : {};
    },
    // 播放/暂停
    playPauseVideo1() {
      this.isPlay = !this.isPlay;
      if (this.isPlay) {
        // 100ms加0.1,1秒加1
        const timeValue = Number(this.videoObj.inputValue);
        if (timeValue >= this.videoObj.max) {
          this.videoObj.inputValue = this.videoObj.step;
        } else {
          this.videoObj.inputValue = timeValue + this.videoObj.step;
        }
        this.handleInputTimeLine1(this.videoObj.inputValue);
        this.timer = setInterval(() => {
          this.videoObj.inputValue = (this.videoObj.inputValue * 10 + this.videoObj.step * 10) / 10;
          if ((this.videoObj.inputValue * 10 + this.videoObj.step * 10) / 10 >= this.videoObj.max) {
            this.videoObj.inputValue = this.videoObj.max;
            clearInterval(this.timer);
            this.isPlay = !this.isPlay;
          }
          this.handleInputTimeLine1(this.videoObj.inputValue);
        }, 100);
      } else {
        clearInterval(this.timer);
      }
    },
    // 时分秒hh:mm:ss换算成秒
    timeToSeconds(timeStr) {
      const [h, m, s] = timeStr.split(":").map(Number);
      return h * 3600 + m * 60 + s;
    },
    // 计算结束和开始的秒数
    getTotalTime(startTime, endTime) {
      // return this.timeToSeconds(endTime) - this.timeToSeconds(startTime)
      return (
        (new Date(endTime).getTime() - new Date(startTime).getTime()) / 1000
      );
    },
    // 改变时间轴
    handleInputTimeLine1(e) {
      if (this.loading) return;
      this.loading = true;
      this.videoObj.currentTime = this.formatTime1(parseFloat(e));
      this.loadedStatusArr.map((item, index) => {
        const video = this.$refs["manualVideoRef" + (index + 1)].$refs.video;
        if (index === 0 && this.currentJkInfo.url) {
          video.currentTime = parseFloat(e) - this.currentJkInfo.startSecs;
        } else if (index === 1 && this.currentHwInfo.url) {
          video.currentTime = parseFloat(e) - this.currentHwInfo.startSecs;
        } else {
          video.currentTime = parseFloat(e);
        }
      });
      const jkInfo = this.getVideo(
        this.videoList1,
        this.formatTime1(parseFloat((e * 10 + 1) / 10))
      );
      if (jkInfo.url !== this.currentJkInfo.url) {
        this.currentJkInfo = jkInfo;
      }
      const hwInfo = this.getVideo(
        this.videoList2,
        this.formatTime1(parseFloat((e * 10 + 1) / 10))
      );
      if (hwInfo.url !== this.currentHwInfo.url) {
        this.currentHwInfo = hwInfo;
      }
      this.loading = false;
    },
    // 格式化时间显示 (秒 → hh:mm:ss)
    formatTime1(seconds, startTime) {
      // 秒
      const startSecs =
        new Date(startTime || this.videoObj.startTime).getTime() / 1000;
      const totalSecs = Number(startSecs) + seconds;
      const date = new Date(Math.floor(totalSecs) * 1000);
      const year = date.getFullYear();
      const month = date.getMonth() + 1;
      const day = date.getDate();
      const hours = date.getHours();
      const mins = date.getMinutes();
      const secs = date.getSeconds();
      return `${year.toString().padStart(2, "0")}-${month
        .toString()
        .padStart(2, "0")}-${day.toString().padStart(2, "0")} ${hours
        .toString()
        .padStart(2, "0")}:${mins.toString().padStart(2, "0")}:${secs
        .toString()
        .padStart(2, "0")}`;
    },
    changeVideo1(info) {
      const { type, index, currentTime } = info;
      if (type === "status") {
        this.loadedVideo(info);
      } else if (type === "fullScreen") {
        this.fullScreen(index);
      } else if (type === "time") {
        this.handleVideoTimeupdate(info);
      }
    },
    // 视频已加载
    loadedVideo(info) {
      this.loadedStatusArr[info.index] = true;
    },
    fullScreen(index) {
      const video = this.$refs["manualVideoRef" + (index + 1)].$refs.video;
      if (video.requestFullScreen) {
        video.requestFullScreen();
      } else if (video.mozRequestFullScreen) {
        video.mozRequestFullScreen();
      } else if (video.webkitRequestFullScreen) {
        video.webkitRequestFullScreen();
      } else if (video.msRequestFullScreen) {
        video.msRequestFullScreen();
      }
    },
    // 视频播放改变时间轴
    handleVideoTimeupdate(info) {
      const { type, index, currentTime } = info;
    },
    // 表格分页
    handleCurrentChange(page) {
      // this.gjData.tableData.data = this.tableData.slice((page - 1) * 5, 5)
      this.gjData.tableData.data = this.warningList.slice(
        (page - 1) * this.gjData.tableData.size,
        page * this.gjData.tableData.size
      );
    },
    // 点击当表格前行
    handleClickCurrentRow(row) {
      const alarmTime = new Date(row.alarmTime).getTime();
      const startTime = new Date(this.videoObj.startTime).getTime()
      if (alarmTime - 5 <= startTime) {
        this.videoObj.inputValue = 0
      } else {
        const newTime = (alarmTime - 5 <= startTime) ? startTime : this.formatTime1(-5, alarmTime)
        const loadedS = (new Date(newTime).getTime() - startTime) / 1000
        this.videoObj.inputValue = loadedS
      }
      this.handleInputTimeLine1(this.videoObj.inputValue)
    },
    getAllPoints() {
      // console.log(111);
      const start = new Date(this.videoObj.startTime).getTime() / 1000;
      this.pointObj = {};
      this.points.map((item, index) => {
        let { posX, posY, startTime, endTime } = item;
        if (index === 0) {
          endTime = startTime;
        } else if (index !== this.points.length - 1) {
          endTime = startTime;
          startTime = this.formatTime1(
            0,
            new Date(startTime)
          );
        }
        const styleObj = this.getRobotStyle(item)
        // console.log('styleObj==============', styleObj);
        // 点位宽高忽略不计
        const scaleX = this.$refs.mapImage.width / 100
        const scaleY = this.$refs.mapImage.height / 100
        this.pointObj[startTime] = { ...item, needArc: true, x: styleObj.left.split('%')[0] * scaleX, y: styleObj.top.split('%')[0] * scaleY };
        if (index !== this.points.length - 1 && new Date(this.points[index + 1].endTime).getTime() / 1000 - new Date(endTime).getTime() / 1000 > 2) {
          this.renderPeriod(index, endTime, posX, posY);
        }
        if (index === this.points.length - 1 && item.startTime !== this.videoObj.endTime) {
          this.renderWaitTime(this.getTotalTime(item.startTime, this.videoObj.endTime), item.startTime, this.pointObj[startTime])
        }
      });
      
      console.log(
        "总时长",
        this.getTotalTime(this.videoObj.startTime, this.videoObj.endTime),
        this.getTotalTime(this.videoObj.startTime, this.points[0].startTime),
        this.getTotalTime(this.points[this.points.length - 1].startTime, this.videoObj.endTime),
        "点位",
        Object.keys(this.pointObj).length, this.pointObj
      );
    },
    // 间隔点位或者等待间隔渲染
    renderPeriod(index, endTime, posX, posY) {
      const x = this.points[index + 1].posX;
      const y = this.points[index + 1].posY;
      const secs =
        new Date(this.points[index + 1].startTime).getTime() / 1000 -
        new Date(endTime).getTime() / 1000;
      const intervalX = (x - posX) / secs;
      const intervalY = (y - posY) / secs;
      // console.log('间隔==========', intervalX, intervalY);

      for (let j = 1; j < secs; j++) {
        const styleObj = this.getRobotStyle({ posX: posX + intervalX * j, posY: posY + intervalY * j, })
        // console.log('============', this.formatTime1(j, endTime), posX + intervalX * j, posY + intervalY * j);
        // 点位宽高忽略不计
        const scaleX = this.$refs.mapImage.width / 100
        const scaleY = this.$refs.mapImage.height / 100
        this.pointObj[this.formatTime1(j, endTime)] = {
          posX: posX + intervalX * j,
          posY: posY + intervalY * j,
          x: styleObj.left.split('%')[0] * scaleX,
          y: styleObj.top.split('%')[0] * scaleY
        };
      }
    },
    renderWaitTime(waitTime, startTime, item) {
      for (let i = 1; i < waitTime; i++) {
        this.pointObj[this.formatTime1(i, startTime)] = item;
      }
    },

    //更新尺寸
    async getMapSize() {
      const img = this.$refs.mapImage;
      await new Promise((resolve) => {
        // 如果图片已经加载完成，直接解析
        if (img.complete) {
          resolve();
        } else {
          img.onload = () => resolve();
          img.onerror = () => resolve(); // 如果图片加载失败，也需要解析
        }
      });

      this.mapWidth = img.naturalWidth;
      this.mapHeight = img.naturalHeight;

      // 获取图片在页面上的实际显示大小
      this.displayWidth = img.clientWidth;
      this.displayHeight = img.clientHeight;
      if (this.pointObj[this.videoObj.startTime]) {
        this.pointInfo = {
          posX: this.pointObj[this.videoObj.startTime].posX,
          posY: this.pointObj[this.videoObj.startTime].posY,
          needArc: this.pointObj[this.videoObj.startTime].needArc,
        };
        const obj = this.getRobotStyle()
        // console.log(111, obj.left, obj.top, this.pointInfo);
        
        document.getElementsByClassName("luxiang-robot")[0].style.top = obj.top;
        document.getElementsByClassName("luxiang-robot")[0].style.left = obj.left;
        
      }
      this.videoObj.currentTime = this.videoObj.startTime
    },
    // 获取点位
    getRobotStyle(info) {
      const pointInfo = info || this.pointInfo
      const posX = pointInfo.posX
      let PixelX = (pointInfo.posX - this.mapInfo.X0) / 0.1;
      let x = (PixelX / this.mapWidth) * 100;
      let PixelY =
        this.mapHeight - (pointInfo.posY - this.mapInfo.Y0) / 0.1;
      let y = (PixelY / this.mapHeight) * 100;
      return { left: x + "%", top: y + "%" };
    },
  },
  watch: {
    "videoObj.currentTime": {
      handler(newVal, oldVal) {
        // console.log('this.videoObj.currentTime', this.videoObj.currentTime);
        
        if (!newVal) return
        // const robot = document.getElementsByClassName("luxiang-robot")[0];
        // this.linePoints = [];
        // this.$refs.lineRef.points = [];
        if (this.videoObj.currentTime === this.videoObj.startTime && this.$refs.lineRef) {
          this.linePoints = [];
          this.$refs.lineRef.points = [];
          this.$refs.lineRef.redrawCanvas();
        }
        // const arr = this.warningList.filter((item) => {
        //   return (
        //     new Date(newVal).getTime() > new Date(item.alarmTime).getTime()
        //   );
        // });
        // this.tableData = this.warningList;
        // this.gjData.tableData.data = arr.slice(0, this.gjData.tableData.size);
        // this.gjData.tableData.total = this.tableData.length;
        if (this.pointObj[newVal]) {
          // console.log('---------------------------------------', this.linePoints.length);
          
          this.pointInfo = {
            ...this.pointObj[newVal]
            // posX: this.pointObj[newVal].posX,
            // posY: this.pointObj[newVal].posY,
            // needArc: this.pointObj[newVal].needArc,
            // timestamp: new Date(this.videoObj.currentTime).getTime()
          };
          const keys = Object.keys(this.pointObj)
          const index = keys.indexOf(newVal)
          const len = this.linePoints.length
          if (len > index + 1) {
            // this.linePoints.splice(0, index + 1)
            // this.$refs.lineRef.points.splice(0, index + 1)
            this.linePoints.splice(index + 1, len - index - 1)
            this.$refs.lineRef.points.splice(index + 1, len - index - 1)
            this.$refs.lineRef.redrawCanvas();
          } else if (len < index + 1) {
            const newKeys = keys.splice(len, index -len + 1)
            newKeys.map((key, index) => {
              this.linePoints.push(this.pointObj[key])
              this.$refs.lineRef.points.push(this.pointObj[key])
              if (index === newKeys.length - 1) {
                this.$refs.lineRef.redrawCanvas();
              }
            })
          }
          // if (!newKeys.length){
          //   this.$refs.lineRef.redrawCanvas();
          // }
          // if (robot) {
          //   const x = robot.offsetLeft;
          //   const y = robot.offsetTop;
          //   const isExist = []
          //   this.linePoints.map((item, index) => {
          //     if (item.timestamp > this.pointInfo.timestamp) {
          //       isExist.push(index)
          //     }
          //   })
          //   this.linePoints.push({ x, y, ...this.pointInfo });
          //   this.$refs.lineRef.points.push({
          //     x,
          //     y,
          //     needArc: this.pointInfo.needArc,
          //   });
          //   this.$refs.lineRef.redrawCanvas();
          // }
        }
        
      },
      immediate: true,
    },
  },
  beforeDestroy() {
    console.log("销毁video");

    if (this.timer) {
      clearInterval(this.timer);
    }
  },
};
