import { getBoxHostList } from '../../../api/bi';
import { getDeviceList, getSysArgs, getTunnels, subscribeLiveBySourceId } from '../../../api/box';
let ZQLGLOBAL = {
  serverIp: '192.168.124.94' ,// 修改成您的盒子aiboxd的IP地址
  // getDevices: `http://192.168.124.94:9091/ks/device`,
  // getSources: `http://192.168.124.94:9091/ks/source`,
  // subscribe: `http://192.168.124.94:9089/ks/stream/live/subscribe`,
  // detect: `http://192.168.124.94:9089/ks/stream/attr`,
  // tunnel:`http://192.168.124.94:9091/ks/system/tunnel`,
  // sysArgs: `http://192.168.124.94:9091/ks/system/args`,
  getDevices: `/box/box9091/ks/device`,
  getSources: `/box/box9091/ks/source`,
  subscribe: `/box/box9089/ks/stream/live/subscribe`,
  detect: `/box/box9089/ks/stream/attr`,
  tunnel:`/box/box9091/ks/system/tunnel`,
  sysArgs: `/box/box9091/ks/system/args`,
  resultTopic: 'ks/sink_local_result', //实时检测结果
  streamCodeTopic: 'ks/stream', //视频流状态码，状态码改变后应重新播放
  srs_server: 1935,
  srs_http_api: 1985,
  srs_http_server: 8080,
  websocket: 8083,
}
export default {
  data() {
    return {
      ZQL_videosInfos: {
        0: null,
        1: null,
        2: null,
        3: null
      },
      ZQL_playingSource: {
        0: null,
        1: null,
        2: null,
        3: null,
        videoNum: 4,
        curposition: -1
      },
      ZQL_sources: {},
      statusArr: [
        'loading'
      ],
      deviceList: []
    }
  },
  methods: {
    // 加载
    liveLoading(index) {
      this.statusArr[index] = 'loading';
    },
    // 离线
    liveOffline(index) {
      this.statusArr[index] = 'off';
    },
    // 停止加载
    liveStopLoading(index) {
      this.statusArr[index] = 'stop';
    },
    // 头部摄像头名称、算法获取并渲染
    setAlgList(index) {
      this.selselectAlg = ''
    },
    clearAlgList(index) {
      let el = document.querySelector(`#${this.prefixId}video-title${index}`);
      if (el) {
        el.innerHTML = ""
      }

    },
    handleRefresh(index) {
      if (!this.ZQL_videosInfos[index]) {
        return;
      }
      if (this.ZQL_videosInfos[index].status == "离线") {
        this.destoryVideoByIndex(index);
        this.subscribeLive(this.ZQL_playingSource[index], index);
      } else {
        if (!this.ZQL_videosInfos[index].stream) {
          return;
        }
        let video = document.getElementById(`${this.prefixId}video${index}`);
        video && (video.srcObject = null);
        if (this.ZQL_videosInfos[index] && this.ZQL_videosInfos[index].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[index].replayTimer);
          this.ZQL_videosInfos[index].replayTimer = null;
        }
        this.ZQL_videosInfos[index] &&
          this.ZQL_videosInfos[index].srsrtc &&
          this.ZQL_videosInfos[index].srsrtc.destroy();
        this.ZQL_videosInfos[index].srsrtc = null;
        this.ZQL_videosInfos[index].status = "";
        this.playVideo(this.ZQL_playingSource[index], index);
      }
    },
    subscribeLive(cameraId, index) {
      this.getCameraSize(cameraId, index);
      this.liveLoading(index);
      subscribeLiveBySourceId(`${ZQLGLOBAL.subscribe}?source_id=${cameraId}`).then(res => {
        if (res && res.data) {
          this.ZQL_videosInfos[index].stream = res.data;
          this.playVideo(cameraId, index);
        } else {
          if (this.ZQL_playingSource[index] == cameraId) {
            this.liveOffline(index);
            // ZQL_videosInfos[index].status = "离线";
            // ZQL_videosInfos[index].loading = false;
            // this.reSubcribe(cameraId, index);
          }
        }
      }).catch((err) => {
        if (
          this.ZQL_playingSource[index] == cameraId &&
          this.ZQL_videosInfos[index]
        ) {
          this.liveOffline(index);
          // ZQL_videosInfos[index].status = "离线";
          // ZQL_videosInfos[index].loading = false;
          // this.reSubcribe(cameraId, index);
        }
      });
    },
    playVideo(cameraId, index) {
      if (this.ZQL_videosInfos[index].srsrtc) {
        return;
      }
      this.ZQL_videosInfos[index].loading = true;
      let video = document.getElementById(`${this.prefixId}video${index}`);
      
      let stream = this.ZQL_videosInfos[index].stream;
      // stream = webrtc://127.0.0.1/live/2e0b0eff-2e17-4da3-86e4-36c36375ef3c/68f8763c1271cb0b8d4caeb5
      var srsrtc;
      if (stream.indexOf("webrtc") >= 0) {
        // jswebrtc.min.js中将ZQLGLOBAL作为全局变量
        window.ZQLGLOBAL = ZQLGLOBAL
        const src =
          "webrtc://" + ZQLGLOBAL.serverIp + "/live" + stream.split("/live")[1];
        // console.log('++++++++', video, src);
        // src = "webrtc://10.2.75.231/live/2e0b0eff-2e17-4da3-86e4-36c36375ef3c/68f8763c1271cb0b8d4caeb5
        srsrtc = new JSWebrtc.Player(src, {
          video,
          autoplay: true,
          onPlay: (obj) => {
            this.liveStopLoading(index);
            this.ZQL_videosInfos[index].loading = false;
            this.ZQL_videosInfos[index].playerState = "success";
          }
        });
      } else if (stream.indexOf(".flv") >= 0) {
        let src = `:${ZQLGLOBAL.srs_http_server}/live${stream.split("/live")[1]
          }`;
        srsrtc = mpegts.createPlayer(
          {
            type: "flv",
            url: src,
            isLive: true,
          },
          { enableWorker: true }
        );
        srsrtc.attachMediaElement(video);
        srsrtc.load();
        this.ZQL_videosInfos[index].playerState = "";
        srsrtc
          .play()
          .then((res) => {
            this.liveStopLoading(index);
            this.ZQL_videosInfos[index].playerState = "success";
            this.ZQL_videosInfos[index].loading = false;

            if (this.ZQL_videosInfos[index].refreshTimeInterval) {
              clearInterval(this.ZQL_videosInfos[index].refreshTimeInterval);
            }
            this.ZQL_videosInfos[index].refreshTime =
              parseInt((Math.random() * 5 + 5) * 1000) * 60;
            this.ZQL_videosInfos[index].refreshTimeInterval = setInterval(() => {
              handleRefresh(index);
            }, this.ZQL_videosInfos[index].refreshTime);
          })
          .catch((err) => { });
        if (this.ZQL_videosInfos[index].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[index].replayTimer);
        }
        this.ZQL_videosInfos[index].replayTimer = setTimeout(() => {
          this.replayflv(srsrtc, cameraId, index);
        }, 3000);
      }
      // console.log('+++++++++srsrtc', srsrtc);
      this.ZQL_videosInfos[index].srsrtc = srsrtc;
    },
    replayflv(srsrtc, cameraId, index) {
      if (!this.ZQL_videosInfos[index]) {
        return;
      }
      if (this.ZQL_videosInfos[index].playerState == "success") {
        return;
      } else {
        srsrtc.unload();
        srsrtc.load();
        srsrtc
          .play()
          .then((res) => {
            this.liveStopLoading(index);
            this.ZQL_videosInfos[index].playerState = "success";
            this.ZQL_videosInfos[index].loading = false;
            if (this.ZQL_videosInfos[index].refreshTimeInterval) {
              clearInterval(this.ZQL_videosInfos[index].refreshTimeInterval);
            }
            this.ZQL_videosInfos[index].refreshTime =
              parseInt((Math.random() * 5 + 5) * 1000) * 60;
            this.ZQL_videosInfos[index].refreshTimeInterval = setInterval(() => {
              this.handleRefresh(index);
            }, this.ZQL_videosInfos[index].refreshTime);
          })
          .catch((err) => {
            // this.destoryVideoByIndex(index);
            // this.subscribeLive(cameraId, index);
          });
        if (this.ZQL_videosInfos[index].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[index].replayTimer);
        }
        this.ZQL_videosInfos[index].replayTimer = setTimeout(() => {
          this.replayflv(srsrtc, cameraId, index);
        }, 3000);
      }
    },
    reSubcribe(cameraId, index) {
      if (this.ZQL_videosInfos[index].subscribeTimeout) {
        clearTimeout(this.ZQL_videosInfos[index].subscribeTimeout);
        this.ZQL_videosInfos[index].subscribeTimeout = null;
      }
      this.ZQL_videosInfos[index].subscribeTimeout = setTimeout(() => {
        this.subscribeLive(cameraId, index);
      }, 1000);
    },
    getCameraSize(id, index) {
      this.setOrisize(
        this.ZQL_sources[id].draw_size[0],
        this.ZQL_sources[id].draw_size[1],
        index, id
      );
    },
    // 处理视频渲染尺寸
    setOrisize(width, height, index, id, containerWidth, containerHeight) {
      let container = document.querySelector(`.${this.prefixId}video-box`);
      if (!container) {
        return;
      }
      if (!this.ZQL_videosInfos[index]) {
        let alg = null;
        if (sessionStorage.getItem("curalgs")) {
          let cameraId = this.ZQL_playingSource[index];
          let curalgs = JSON.parse(sessionStorage.getItem("curalgs"));
          alg = curalgs[cameraId]
            ? JSON.parse(JSON.stringify(curalgs[cameraId]))
            : null;
        }
        this.ZQL_videosInfos[index] = {
          id: id,
          loading: true,
          openWs: true,
          alg: alg,
          algListShow: false,
          subscribeTimeout: null,
          refreshTimeInterval: null, // 定时刷新定时器
          refreshTime: null, // 定时刷新时间
          replayTimer: null,
          playerState: "pending",
          detectInterval: null,
          quanping: false,
          srsrtc: null,
          stream: "",
          status: "",
          stream_code: "",
        };
      }
      if (this.ZQL_videosInfos[index]) {
        let oriWidth = width;
        let oriHeight = height;
        this.ZQL_videosInfos[index].oriWidth = oriWidth;
        this.ZQL_videosInfos[index].oriHeight = oriHeight;

        if (
          oriWidth / container.offsetWidth >
          oriHeight / container.offsetHeight
        ) {
          this.ZQL_videosInfos[index].actualHeight = container.offsetWidth / (oriWidth / oriHeight)
          this.ZQL_videosInfos[index].actualWidth = container.offsetWidth;
        } else {
          this.ZQL_videosInfos[index].actualHeight = container.offsetHeight
          this.ZQL_videosInfos[index].actualWidth = container.offsetHeight * (oriWidth / oriHeight)
        }
        this.ZQL_videosInfos[index].actualWidth = containerWidth || this.ZQL_videosInfos[index].actualWidth
        this.ZQL_videosInfos[index].actualHeight = containerHeight || this.ZQL_videosInfos[index].actualHeight
        // videoWidth = ZQL_videosInfos[index].actualWidth;
        this.setPosition(index);
      }
    },
    setPosition(index) {
      let container = document.querySelector(`.${this.prefixId}video-box`);
      let video = document.querySelector(`#${this.prefixId}video${index}`);
      let canvas = document.getElementById(`${this.prefixId}canvas${index}`);
      let width = this.ZQL_videosInfos[index].actualWidth, height = this.ZQL_videosInfos[index].actualHeight;
      video.style.position = "absolute";
      video.style.width = width + "px";
      video.style.height = height + "px";
      canvas.width = width;
      canvas.height = height;
      if (width / container.offsetWidth < height / container.offsetHeight) {
        let left = (container.offsetWidth - width) / 2;
        video.style.left = Math.floor(left) + "px";
        video.style.top = 0 + "px";
        canvas.style.left = Math.floor(left) + "px";
        canvas.style.top = "0px";
      } else {
        let top = (container.offsetHeight - height) / 2;
        video.style.top = Math.floor(top) + "px";
        video.style.left = 0 + "px";
        canvas.style.top = Math.floor(top) + "px";
        canvas.style.left = "0px";
      }
    },
    setAlarms(data, index) {
      this.clearCanvas(index);
      if (this.ZQL_videosInfos[index] && !this.ZQL_videosInfos[index].canvas) {
        this.ZQL_videosInfos[index].canvas = document.getElementById(`${this.prefixId}canvas${index}`)
      }
      if (
        !this.ZQL_videosInfos[index] ||
        !this.ZQL_videosInfos[index].actualWidth ||
        !this.ZQL_videosInfos[index].actualHeight ||
        !this.ZQL_videosInfos[index].oriWidth ||
        !this.ZQL_videosInfos[index].oriHeight
      ) {
        return;
      }
      // let bbox = data.result.data.bbox;
      
      let bbox = data.bbox;
      if (Object.values(bbox.polygons).length > 0) {
        Object.values(bbox.polygons).forEach((item) => {
          let color = JSON.parse(JSON.stringify(item.color)).reverse();
          // let color = item.color;
          let points = item.polygon.map((point) => {
            return [
              Math.round(
                (point[0] * this.ZQL_videosInfos[index].actualWidth) /
                this.ZQL_videosInfos[index].oriWidth
              ),
              Math.round(
                (point[1] * this.ZQL_videosInfos[index].actualHeight) /
                this.ZQL_videosInfos[index].oriHeight
              ),
            ];
          });
          let context = this.ZQL_videosInfos[index].canvas.getContext("2d");
          context.font = "20px Arial bolder";
          context.fillStyle = "transparent";
          context.strokeStyle = "rgb(" + color.join(",") + ")";
          context.lineWidth = 2;
          this.drawPolygons(points, context);
          this.drawPolygonInfo(context, Object.values(bbox.polygons), index);
        });
      }

      if (bbox.rectangles.length > 0) {
        bbox.rectangles.forEach((item, i) => {
          let color = JSON.parse(JSON.stringify(item.color)).reverse();
          let coordinates = {
            x: Math.round(
              (item.xyxy[0] * this.ZQL_videosInfos[index].actualWidth) /
              this.ZQL_videosInfos[index].oriWidth
            ),
            y: Math.round(
              (item.xyxy[1] * this.ZQL_videosInfos[index].actualHeight) /
              this.ZQL_videosInfos[index].oriHeight
            ),
            x1: Math.round(
              (item.xyxy[2] * this.ZQL_videosInfos[index].actualWidth) /
              this.ZQL_videosInfos[index].oriWidth
            ),
            y1: Math.round(
              (item.xyxy[3] * this.ZQL_videosInfos[index].actualHeight) /
              this.ZQL_videosInfos[index].oriHeight
            ),
          };
          let context = this.ZQL_videosInfos[index].canvas.getContext("2d");
          context.font = "20px Arial bolder";
          context.fillStyle = "rgb(" + color.join(",") + ")";
          context.fillText(item.label || "", coordinates.x, coordinates.y - 10);
          context.strokeStyle = "rgb(" + color.join(",") + ")";
          context.lineWidth = 2;
          // context.strokeRect(
          //   coordinates.x,
          //   coordinates.y,
          //   coordinates.x1 - coordinates.x,
          //   coordinates.y1 - coordinates.y
          // );
          let lines = [];
          let lineWidth = (coordinates.x1 - coordinates.x) / 4;
          let lineHeight = (coordinates.y1 - coordinates.y) / 4;
          lines[0] = {
            x: coordinates.x,
            y: coordinates.y,
            x1: coordinates.x + lineWidth,
            y1: coordinates.y,
          };
          lines[1] = {
            x: coordinates.x,
            y: coordinates.y,
            x1: coordinates.x,
            y1: coordinates.y + lineHeight,
          };
          lines[2] = {
            x: coordinates.x1,
            y: coordinates.y,
            x1: coordinates.x1 - lineWidth,
            y1: coordinates.y,
          };
          lines[3] = {
            x: coordinates.x1,
            y: coordinates.y,
            x1: coordinates.x1,
            y1: coordinates.y + lineHeight,
          };
          lines[4] = {
            x: coordinates.x,
            y: coordinates.y1,
            x1: coordinates.x + lineWidth,
            y1: coordinates.y1,
          };
          lines[5] = {
            x: coordinates.x,
            y: coordinates.y1,
            x1: coordinates.x,
            y1: coordinates.y1 - lineHeight,
          };
          lines[6] = {
            x: coordinates.x1,
            y: coordinates.y1,
            x1: coordinates.x1 - lineWidth,
            y1: coordinates.y1,
          };
          lines[7] = {
            x: coordinates.x1,
            y: coordinates.y1,
            x1: coordinates.x1,
            y1: coordinates.y1 - lineHeight,
          };
          lines.forEach((item) => {
            this.drawLine(context, item);
          });
        });
      }

      if (Object.values(bbox.lines).length > 0) {
        Object.values(bbox.lines).forEach((item, i) => {
          let color = JSON.parse(JSON.stringify(item.color)).reverse();
          let coordinates = {
            x: Math.round(
              (item.line[0][0] * this.ZQL_videosInfos[index].actualWidth) /
              this.ZQL_videosInfos[index].oriWidth
            ),
            y: Math.round(
              (item.line[0][1] * this.ZQL_videosInfos[index].actualHeight) /
              this.ZQL_videosInfos[index].oriHeight
            ),
            x1: Math.round(
              (item.line[1][0] * this.ZQL_videosInfos[index].actualWidth) /
              this.ZQL_videosInfos[index].oriWidth
            ),
            y1: Math.round(
              (item.line[1][1] * this.ZQL_videosInfos[index].actualHeight) /
              this.ZQL_videosInfos[index].oriHeight
            ),
          };
          let context = this.ZQL_videosInfos[index].canvas.getContext("2d");
          context.font = "20px Arial bolder";
          context.fillStyle = "rgb(" + color.join(",") + ")";
          if (item.ext.direction) {
            context.fillText(item.name, (coordinates.x + coordinates.x1) / 2, (coordinates.y + coordinates.y1) / 2 + 20);
          }

          context.strokeStyle = "rgb(" + color.join(",") + ")";
          context.lineWidth = 2;
          this.drawLine(context, coordinates);
          this.drawCountingInfo(context, Object.values(bbox.lines));
        });
      }
    },
    drawPolygons(points, context) {
      context.beginPath();
      context.moveTo(points[0][0], points[0][1]);

      for (var i = 1; i < points.length; i++) {
        context.lineTo(points[i][0], points[i][1]);
      }
      context.closePath();
      context.fill();
      context.stroke();
    },
    drawLine(ctx, line) {
      ctx.beginPath();
      ctx.moveTo(line.x, line.y);
      ctx.lineTo(line.x1, line.y1);
      ctx.stroke();
    },
    drawCountingInfo(context, lines) {
      lines.forEach((item, index) => {
        context.fillStyle = "rgb(255,0,0)";
        if (item.ext.direction.length == 2) {
          context.fillText(`[${item.name}] ${item.ext.action.count}: ${item.ext.result.count}`, 0, 20 * index + 20);
        } else {
          context.fillText(`[${item.name}] ${item.ext.action.increase}: ${item.ext.result.increase},${item.ext.action.decrease}: ${item.ext.result.decrease},${item.ext.action.delta}: ${item.ext.result.delta}`, 0, 20 * index + 20);
        }
      });
    },
    drawPolygonInfo(context, polygons, videoindex) {
      polygons.forEach((item, index) => {
        context.fillStyle =
          "rgb(" +
          JSON.parse(JSON.stringify(item.color)).reverse().join(",") +
          ")";
        let leftPoint = item.polygon[0];
        for (let i = 1; i < item.polygon.length; i++) {
          if (item.polygon[i][0] < leftPoint[0]) {
            leftPoint = item.polygon[i];
          }
        }
        context.fillText(
          `${item.name}`,
          (leftPoint[0] * this.ZQL_videosInfos[videoindex].actualWidth) /
          this.ZQL_videosInfos[videoindex].oriWidth,
          (leftPoint[1] * this.ZQL_videosInfos[videoindex].actualHeight) /
          this.ZQL_videosInfos[videoindex].oriHeight + 20
        );
        if (item.ext.result) {
          context.fillStyle = "rgb(255,0,0)";
          context.fillText(`${item.name}: ${item.ext.result}`, 0, 20 * index + 20);
        }
      });
    },
    destroyVideo(videonum) {
      for (let i = 0; i < videonum; i++) {
        this.destoryVideoByIndex(i);
      }
    },
    destoryVideoByIndex(index) {
      this.clearCanvas(index);
      // console.log(index, this.ZQL_videosInfos[index]);
      
      if (this.ZQL_videosInfos[index]) {
        if (
          this.ZQL_videosInfos[index] &&
          this.ZQL_videosInfos[index].subscribeTimeout
        ) {
          clearTimeout(this.ZQL_videosInfos[index].subscribeTimeout);
          this.ZQL_videosInfos[index].subscribeTimeout = null;
        }
        if (this.ZQL_videosInfos[index] && this.ZQL_videosInfos[index].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[index].replayTimer);
          this.ZQL_videosInfos[index].replayTimer = null;
        }
        if (this.ZQL_videosInfos[index].refreshTimeInterval) {
          clearInterval(this.ZQL_videosInfos[index].refreshTimeInterval);
          this.ZQL_videosInfos[index].refreshTimeInterval = null;
        }
        let video = document.getElementById(`${this.prefixId}video${index}`);
        video && (video.srcObject = null);
        this.ZQL_videosInfos[index].srsrtc &&
          this.ZQL_videosInfos[index].srsrtc.destroy();
        this.clearCanvas(index);
        this.ZQL_videosInfos[index] = null;
      }
    },
    clearCanvas: function(index) {
      let canvas = document.getElementById(`${this.prefixId}canvas${index}`);
      if (canvas && canvas.getContext("2d")) {
        canvas
          .getContext("2d")
          .clearRect(0, 0, canvas.offsetWidth, canvas.offsetHeight);
      }
    },
    connectMqtt: function() {
      // let mqttclient = mqtt.connect(`wss://${ZQLGLOBAL.serverIp}:${ZQLGLOBAL.websocket}/mqtt`);
      // let mqttclient = mqtt.connect(`wss://192.168.124.204/ws/mqtt`);
      let mqttclient = mqtt.connect(`wss://${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_HOST.replaceAll("'", '') : location.host}/ws/mqtt`);
      mqttclient.subscribe(
        ZQLGLOBAL.resultTopic,
        { qos: 0 },
        (error) => {
          if (error) {
            console.log("subscribe error:", error);
            return;
          }
        }
      );
      mqttclient.subscribe(
        ZQLGLOBAL.streamCodeTopic,
        { qos: 0 },
        (error) => {
          if (error) {
            console.log("subscribe error:", error);
            return;
          }
        }
      );
      mqttclient.on("message", (topic, payload) => {
        let msg = JSON.parse(payload.toString());
        if (msg.msg_type == "result") {
          // console.log("result", msg.data);
          
          let id = msg.data.source.id;
          for (let i = 0; i < 4; i++) {          
            if (this.ZQL_videosInfos[i]) {
              let alg =
                this.ZQL_videosInfos[i].alg && this.ZQL_videosInfos[i].alg.algname;
                if (
                  id == this.ZQL_playingSource[i] &&
                  msg.data.alg.name == this.ZQL_videosInfos[i].alg
                ) {                
                this.setAlarms(msg.data.reserved_data, i);
                if (
                  this.ZQL_videosInfos[i] &&
                  this.ZQL_videosInfos[i].canvasTimeout
                ) {
                  clearTimeout(this.ZQL_videosInfos[i].canvasTimeout);
                }
                this.ZQL_videosInfos[i].canvasTimeout = setTimeout(() => {
                  this.clearCanvas(i);
                }, 1000);
                break;
              }
            }
          }
        }
        if (msg.msg_type == "stream_code") {
          let cameraId = msg.data.source_id;
          // console.log("stream_code", msg.data);
          for (let i = 0; i < this.ZQL_playingSource.videoNum; i++) {
            if (cameraId == this.ZQL_playingSource[i] && this.ZQL_videosInfos[i]) {
              if (!this.ZQL_videosInfos[i].stream_code) {
                this.ZQL_videosInfos[i].stream_code = msg.data.stream_code;
              } else if (
                msg.data.stream_code != this.ZQL_videosInfos[i].stream_code
              ) {
                this.ZQL_videosInfos[i].stream_code = msg.data.stream_code;
                this.handleRefresh(i);
              }
              break;
            }
          }
        }

      });
    },
    getHeight(className, draw_size, index) {
      // 1280*723
      this.$nextTick(() => {
        const el = document.getElementsByClassName(className)[0]
        const parentEl = el.parentElement
        const scale = draw_size[0] / draw_size[1];
        parentEl.style.height = `${parentEl.clientWidth / scale}px`;
        // let width = el.clientWidth;
        let width = parentEl.clientWidth;
        let height = width / scale
        // console.log('xxxxxxxxxxxxx', parentEl.clientWidth, parentEl.clientHeight, el.clientWidth, el.clientHeight);
        if (height > parentEl.clientHeight) {
          height = parentEl.clientHeight
          width = parentEl.clientHeight * scale
        }
        // console.log('wwwwwwwww', className, document.getElementsByClassName(className)[0], width, height);
        
        document.getElementsByClassName(className)[0].style.width = `${width}px`;
        document.getElementsByClassName(className)[0].style.height = `${height}px`;
        // document.getElementById(`${this.prefixId}video${index}`).style.width = `${width}px`;
        // document.getElementById(`${this.prefixId}video${index}`).style.height = `${height}px`;
        // document.getElementById(`${this.prefixId}canvas${index}`).style.width = `${width}px`;
        // document.getElementById(`${this.prefixId}canvas${index}`).style.height = `${height}px`;
        // this.handleRefresh(index);
        this.setOrisize(draw_size[0], draw_size[1], index, this.ZQL_playingSource[index], width, height)
      })
    },
    getHeight1(className, draw_size) {
      this.$nextTick(() => {
        const el = document.getElementsByClassName(className)[0]
        const parentEl = el.parentElement
        const scale = draw_size[0] / draw_size[1];
        let width = el.clientWidth;
        let height = width / scale
        if (height > parentEl.clientHeight) {
          height = parentEl.clientHeight
          width = parentEl.clientHeight * scale
        }
        document.getElementsByClassName(className)[0].style.width = `${width}px`;
        document.getElementsByClassName(className)[0].style.height = `${height}px`;
        // console.log('xxxxxxxxxxxxx', width, height);
        this.handleRefresh(getHeight);
        // this.setOrisize(draw_size[0], draw_size[1], index, this.ZQL_playingSource[index])
      })
    },
    getBoxHost() {
      getBoxHostList().then(res => {
        if (res.code === 200) {
          if (res.data && res.data.length) {
            ZQLGLOBAL.serverIp = res.data[0].dictValue
            console.log('盒子赋值========', res.data);
            
            window.ZQLGLOBAL = {...ZQLGLOBAL, serverIp: res.data[0].dictValue}
          }
        }
      })
    },
    // 请求后端接口
    getSources() {
      // this.deviceList = [
      //   {
      //     draw_size: [1280,720],
      //     id: 1
      //   }
      // ]
      // return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${imgUrl}`;
      const url = process.env.NODE_ENV === 'development' ? 'http://10.2.75.231:9091/ks/source' : `${ZQLGLOBAL.getSources}`
      getDeviceList(url).then(res => {
        console.log('获取摄像头列表', res);
        const cameras = res.data.map(item => {
          item.sourceId = item.id;
          item.title = item.desc;
          item.type = 'source';
          item.checked = false;
          this.ZQL_sources[item.id] = item
          return item
        });
        // this.deviceList = cameras
        this.deviceList = cameras.filter(item => item.id == this.dogId) 
        if (this.deviceList.length) {
          this.selselectDevice = this.deviceList[0].sourceId
          setTimeout(() => {
            this.handleClickDevice(this.selselectDevice)
          }, 1000);
        }
      }).catch(err => {
        console.error('获取摄像头列表出错', err);
      })
    },
    sysArgs() {
      const tunnelUrl = process.env.NODE_ENV === 'development' ? 'http://10.2.75.231:9091/ks/system/tunnel' : `${ZQLGLOBAL.tunnel}`
      const argsUrl = process.env.NODE_ENV === 'development' ? 'http://10.2.75.231:9091/ks/system/args' : `${ZQLGLOBAL.sysArgs}`
      getTunnels(tunnelUrl).then(tunnelRes => {
        if (tunnelRes.data.enable == false) {
          getSysArgs(argsUrl).then(res => {
            ZQLGLOBAL = Object.assign(ZQLGLOBAL, res.data.map.local)
            this.connectMqtt()
          }).catch(err => {
            console.error('getSysArgs出错', err);
          })
        } else {
          const { srs_server, srs_http_api, srs_http_server, websocket } = tunnelRes.data
          ZQLGLOBAL = Object.assign(ZQLGLOBAL, { srs_server, srs_http_api, srs_http_server, websocket })
          this.connectMqtt()
        }
      }).catch(err => {
        console.error('getTunnels出错', err);
      })
    }
  }
}