export default {
  data() {
    return {
      videoObj: {
        // 当前播放进度
        currentTime: "",
        // 进度0.1，1太大
        step: 0.1,
        // 时间线
        max: 100,
        inputValue: 0,
        // 开始时间
        startTime: "2025-07-29 13:00:00",
        // 结束时间
        endTime: "2025-07-29 13:01:00",
      },
      timer: null,
      isPlay: false,
      rateList: [0.5, 1, 1.25, 1.5, 2],
    }
  },
  methods: {
    getInitData() {
      const startTime = "2026-06-10 13:58:37"
      const endTime = "2026-06-10 14:58:37"
      this.videoObj.inputValue = 0
      this.videoObj.startTime = startTime
      this.videoObj.endTime = endTime
      this.videoObj.max = this.getTotalTime(
        this.videoObj.startTime,
        this.videoObj.endTime
      );
    },
    changeSpeed(step) {
      // 限制速度范围在 0.1 倍到 4.0 倍之间，防止失控
      this.videoObj.step = Math.min(4.0, Math.max(0.1, step)) / 10;
      this.recordings.map((recording, index) => {
        const ref = this.$refs[`recordedPlayer_${recording.recordingId}`];
        const video = Array.isArray(ref) ? ref[0] : ref
        video.playbackRate = this.videoObj.step * 10;
      });
    },
    // 根据时间获取视频
    updateVideo(e) {
      this.recordings.map((recording, index) => {
        const ref = this.$refs[`recordedPlayer_${recording.recordingId}`];
        const video = Array.isArray(ref) ? ref[0] : ref
        const startTime = this.formatTime(parseFloat((e * 10 + 1) / 10))        
        if (recording.status !== 'READY') return
        if (recording.recordedStartedAt <= startTime && recording.recordedEndedAt >= startTime) {
          this.recordingData[recording.recordingId].player[this.isPlay ? 'play' : 'pause']();
        } else {
          if (!this.recordingData[recording.recordingId].player.paused) {
            this.recordingData[recording.recordingId].player.pause();
          }
        }
      });
    },
    // 播放/暂停
    playPauseVideo() {
      this.isPlay = !this.isPlay;
      if (this.isPlay) {
        // 100ms加0.1,1秒加1
        const timeValue = Number(this.videoObj.inputValue);
        if (timeValue >= this.videoObj.max) {
          this.videoObj.inputValue = this.videoObj.step;
        } else {
          this.videoObj.inputValue = timeValue + this.videoObj.step;
        }
        this.handleInputTimeLine(this.videoObj.inputValue);
        this.timer = setInterval(() => {
          this.videoObj.inputValue = (this.videoObj.inputValue * 10 + this.videoObj.step * 10) / 10;
          if ((this.videoObj.inputValue * 10 + this.videoObj.step * 10) / 10 >= this.videoObj.max) {
            this.videoObj.inputValue = this.videoObj.max;
            clearInterval(this.timer);
            this.isPlay = !this.isPlay;
          }
          this.handleInputTimeLine(this.videoObj.inputValue);
        }, 100);
      } else {
        clearInterval(this.timer);
        this.updateVideo(this.videoObj.inputValue)
      }
    },
    // 改变时间轴
    handleInputTimeLine(e) {
      this.videoObj.currentTime = this.formatTime(parseFloat(e));
      this.updateVideo(e);
    },
    // 格式化时间显示 (秒 → hh:mm:ss)
    formatTime(seconds, startTime) {
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
    getTotalTime(startTime, endTime) {
      // return this.timeToSeconds(endTime) - this.timeToSeconds(startTime)
      return (
        (new Date(endTime).getTime() - new Date(startTime).getTime()) / 1000
      );
    },
  }
}