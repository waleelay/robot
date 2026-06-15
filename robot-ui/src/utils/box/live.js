
const ZQL_multivideo = {
  // 渲染html
  setVideoEl: () => {
    let videoContainer = document.querySelector("#video-container");
    if (ZQL_playingSource.videoNum == 1) {
      videoContainer.className = "one-video";
      videoContainer.innerHTML = `
          <div class="video-box">
            <div class="tips" id="tip0">
              <div class="icon-dot"></div>
              <div class="deviceoffline">
                <i class="z-icon-jiankonglixian" style="font-size: 40rem"></i>
                <span>离线</span>
              </div>
            </div>
            <div class="title-container" id="video-title0"></div>
            <video ref="video" muted id="video0" class="video-js" autoplay="autoplay" preload="auto"></video>
            <canvas class="canvas-shuju" id="canvas0"></canvas>
          </div>
        `
    } else {
      videoContainer.className = "four-video";
      videoContainer.innerHTML = `
          <div  class="video-box">
            <div class="tips" id="tip0">
              
            </div>
            <div class="title-container" id="video-title0"></div>
            <video ref="video" muted id="video0" class="video-js" autoplay="autoplay" preload="auto"></video>
            <canvas class="canvas-shuju" id="canvas0"></canvas>
          </div>
          <div  class="video-box">
            <div class="tips" id="tip1">
              
            </div>
            <div class="title-container" id="video-title1"></div>
            <video ref="video" muted id="video1" class="video-js" autoplay="autoplay" preload="auto"></video>
            <canvas class="canvas-shuju" id="canvas1"></canvas>
          </div>
          <div  class="video-box">
            <div class="tips" id="tip2">
              
            </div>
            <div class="title-container" id="video-title2"></div>
            <video ref="video" muted id="video2" class="video-js" autoplay="autoplay" preload="auto"></video>
            <canvas class="canvas-shuju" id="canvas2"></canvas>
          </div>
          <div class="video-box">
            <div class="tips" id="tip3">
              
            </div>
            <div class="title-container" id="video-title3"></div>
            <video ref="video" muted id="video3" class="video-js" autoplay="autoplay" preload="auto"></video>
            <canvas class="canvas-shuju" id="canvas3"></canvas>
          </div>
        `
    }
  },
  // 加载
  liveLoading: (index) => {
    let tipel = document.querySelector("#tip" + index);
    tipel.innerHTML = `<div class="icon-dot"></div>`
  },
  // 离线
  liveOffline: (index) => {
    let tipel = document.querySelector("#tip" + index);
    tipel.innerHTML = `
      <div class="deviceoffline">
        <i class="z-icon-jiankonglixian" style="font-size: 40rem"></i>
        <span>离线</span>
      </div>
    `
  },
  // 停止加载
  liveStopLoading: (index) => {
    let tipel = document.querySelector("#tip" + index);
    if (tipel) {
      tipel.innerHTML = ``
    }
  },
  // 头部摄像头名称、算法获取并渲染
  setAlgList(index) {
    console.log('setAlgList', index);
    let el = document.querySelector(`#video-title${index}`);
    let algList = ZQL_sources[ZQL_playingSource[index]].alg;
    console.log('algList', algList);
    
    let algEl = '<ul>';
    for (let alg in algList) {
      console.log(111, algList, alg);
      let name = algList[alg].reserved_args.ch_name;
      algEl = algEl + `<li alg="${alg}" index="${index}">${name}</li>`
    }
    algEl = algEl + '</ul>'
    el.innerHTML = `
      <div class="camera">${ZQL_sources[ZQL_playingSource[index]].desc}</div>
      <div class="alg">      
        <div class="algname">算法: ${ZQL_playingSource[index].alg ? ZQL_sources[ZQL_playingSource[index]].alg[alg].reserved_args.ch_name : ''}</div>
        ${algEl}
      </div>
      <div id="close${index}">关闭</div>
    `;
    el.querySelectorAll('li').forEach(item => {
      item.addEventListener('click', (e) => {
        let index = e.currentTarget.getAttribute("index");
        let alg = e.currentTarget.getAttribute("alg")
        console.log(222, ZQL_videosInfos[index], alg);
        
        ZQL_videosInfos[index].alg = alg;
        let videlel = document.querySelector(`#video-title${index}`);
        videlel.querySelector(".algname").innerHTML = '算法：' + ZQL_sources[ZQL_playingSource[index]].alg[alg].reserved_args.ch_name
      })
    })
    document.querySelector(`#close${index}`).addEventListener('click', () => {
      ZQL_multivideo.clearAlgList(index);
      ZQL_multivideo.liveStopLoading(index);
      ZQL_multivideo.destoryVideoByIndex(index);
      ZQL_playingSource[index] = null;
      ZQL_videosInfos[index] = null;
    })
  },
  clearAlgList(index) {
    let el = document.querySelector(`#video-title${index}`);
    if (el) {
      el.innerHTML = ""
    }

  },
  handleRefresh(index) {
    if (!ZQL_videosInfos[index]) {
      return;
    }
    if (ZQL_videosInfos[index].status == "离线") {
      ZQL_multivideo.destoryVideoByIndex(index);
      ZQL_multivideo.subscribeLive(ZQL_playingSource[index], index);
    } else {
      if (!ZQL_videosInfos[index].stream) {
        return;
      }
      let video = document.getElementById("video" + index);
      video && (video.srcObject = null);
      if (ZQL_videosInfos[index] && ZQL_videosInfos[index].replayTimer) {
        clearTimeout(ZQL_videosInfos[index].replayTimer);
        ZQL_videosInfos[index].replayTimer = null;
      }
      ZQL_videosInfos[index] &&
        ZQL_videosInfos[index].srsrtc &&
        ZQL_videosInfos[index].srsrtc.destroy();
      ZQL_videosInfos[index].srsrtc = null;
      ZQL_videosInfos[index].status = "";
      ZQL_multivideo.playVideo(ZQL_playingSource[index], index);
    }
  },
  subscribeLive(cameraId, index) {
    ZQL_multivideo.getCameraSize(cameraId, index);
    ZQL_multivideo.liveLoading(index);
    ZQL_apis
      .subscribeLive(
        // ZQL_sources[cameraId].deviceId,
        // ZQL_sources[cameraId].sourceId
        cameraId
      )
      .then((data) => {
        let stream = data.data;
        if (data && stream) {
          ZQL_videosInfos[index].stream = stream;
          ZQL_multivideo.playVideo(cameraId, index);
        } else {
          if (ZQL_playingSource[index] == cameraId) {
            ZQL_multivideo.liveOffline(index);
            // ZQL_videosInfos[index].status = "离线";
            // ZQL_videosInfos[index].loading = false;
            // this.reSubcribe(cameraId, index);
          }
        }
      })
      .catch((err) => {
        if (
          ZQL_playingSource[index] == cameraId &&
          ZQL_videosInfos[index]
        ) {
          ZQL_multivideo.liveOffline(index);
          // ZQL_videosInfos[index].status = "离线";
          // ZQL_videosInfos[index].loading = false;
          // this.reSubcribe(cameraId, index);
        }
      });
  },
  playVideo(cameraId, index) {
    if (ZQL_videosInfos[index].srsrtc) {
      return;
    }
    ZQL_videosInfos[index].loading = true;
    let video = document.getElementById("video" + index);
    
    let stream = ZQL_videosInfos[index].stream;
    var srsrtc;
    if (stream.indexOf("webrtc") >= 0) {
      let src =
      "webrtc://" + ZQLGLOBAL.serverIp + "/live" + stream.split("/live")[1];
      console.log('++++++++', video, src);
      srsrtc = new JSWebrtc.Player(src, {
        video: video,
        autoplay: true,
        onPlay: (obj) => {        
          ZQL_multivideo.liveStopLoading(index);
          ZQL_videosInfos[index].loading = false;
          ZQL_videosInfos[index].playerState = "success";
        },
      });
    } else if (stream.indexOf(".flv") >= 0) {
      let src = `http://${ZQLGLOBAL.serverIp}:${ZQLGLOBAL.srs_http_server}/live${stream.split("/live")[1]
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
      ZQL_videosInfos[index].playerState = "";
      srsrtc
        .play()
        .then((res) => {
          ZQL_multivideo.liveStopLoading(index);
          ZQL_videosInfos[index].playerState = "success";
          ZQL_videosInfos[index].loading = false;

          if (ZQL_videosInfos[index].refreshTimeInterval) {
            clearInterval(ZQL_videosInfos[index].refreshTimeInterval);
          }
          ZQL_videosInfos[index].refreshTime =
            parseInt((Math.random() * 5 + 5) * 1000) * 60;
          ZQL_videosInfos[index].refreshTimeInterval = setInterval(() => {
            handleRefresh(index);
          }, ZQL_videosInfos[index].refreshTime);
        })
        .catch((err) => { });
      if (ZQL_videosInfos[index].replayTimer) {
        clearTimeout(ZQL_videosInfos[index].replayTimer);
      }
      ZQL_videosInfos[index].replayTimer = setTimeout(() => {
        ZQL_multivideo.replayflv(srsrtc, cameraId, index);
      }, 3000);
    }
    console.log('+++++++++srsrtc', JSWebrtc, srsrtc);
    
    ZQL_videosInfos[index].srsrtc = srsrtc;
  },
  replayflv(srsrtc, cameraId, index) {
    if (!ZQL_videosInfos[index]) {
      return;
    }
    if (ZQL_videosInfos[index].playerState == "success") {
      return;
    } else {
      srsrtc.unload();
      srsrtc.load();
      srsrtc
        .play()
        .then((res) => {
          ZQL_multivideo.liveStopLoading(index);
          ZQL_videosInfos[index].playerState = "success";
          ZQL_videosInfos[index].loading = false;
          if (ZQL_videosInfos[index].refreshTimeInterval) {
            clearInterval(ZQL_videosInfos[index].refreshTimeInterval);
          }
          ZQL_videosInfos[index].refreshTime =
            parseInt((Math.random() * 5 + 5) * 1000) * 60;
          ZQL_videosInfos[index].refreshTimeInterval = setInterval(() => {
            ZQL_multivideo.handleRefresh(index);
          }, ZQL_videosInfos[index].refreshTime);
        })
        .catch((err) => {
          // this.destoryVideoByIndex(index);
          // this.subscribeLive(cameraId, index);
        });
      if (ZQL_videosInfos[index].replayTimer) {
        clearTimeout(ZQL_videosInfos[index].replayTimer);
      }
      ZQL_videosInfos[index].replayTimer = setTimeout(() => {
        ZQL_multivideo.replayflv(srsrtc, cameraId, index);
      }, 3000);
    }
  },
  reSubcribe(cameraId, index) {
    if (ZQL_videosInfos[index].subscribeTimeout) {
      clearTimeout(ZQL_videosInfos[index].subscribeTimeout);
      ZQL_videosInfos[index].subscribeTimeout = null;
    }
    // 错误
    ZQL_multivideo.videosInfos[index].subscribeTimeout = setTimeout(() => {
      ZQL_multivideo.subscribeLive(cameraId, index);
    }, 1000);
  },
  getCameraSize(id, index) {
    ZQL_multivideo.setOrisize(
      ZQL_sources[id].draw_size[0],
      ZQL_sources[id].draw_size[1],
      index, id
    );
  },
  // 处理视频渲染尺寸
  setOrisize(width, height, index, id) {
    console.log(123, 'setOrisize');
    let container = document.querySelector(".video-box");
    if (!container) {
      return;
    }
    if (!ZQL_videosInfos[index]) {
      let alg = null;
      if (sessionStorage.getItem("curalgs")) {
        let cameraId = ZQL_playingSource[index];
        let curalgs = JSON.parse(sessionStorage.getItem("curalgs"));
        alg = curalgs[cameraId]
          ? JSON.parse(JSON.stringify(curalgs[cameraId]))
          : null;
      }
      ZQL_videosInfos[index] = {
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
    if (ZQL_videosInfos[index]) {
      let oriWidth = width;
      let oriHeight = height;
      ZQL_videosInfos[index].oriWidth = oriWidth;
      ZQL_videosInfos[index].oriHeight = oriHeight;

      if (
        oriWidth / container.offsetWidth >
        oriHeight / container.offsetHeight
      ) {
        ZQL_videosInfos[index].actualHeight = container.offsetWidth / (oriWidth / oriHeight)
        ZQL_videosInfos[index].actualWidth = container.offsetWidth;
      } else {
        ZQL_videosInfos[index].actualHeight = container.offsetHeight
        ZQL_videosInfos[index].actualWidth = container.offsetHeight * (oriWidth / oriHeight)
      }
      // videoWidth = ZQL_videosInfos[index].actualWidth;
      ZQL_multivideo.setPosition(index);
    }
  },
  setPosition(index) {
    let container = document.querySelector(".video-box");
    let video = document.querySelector("#video" + index);
    let canvas = document.getElementById("canvas" + index);
    let width = ZQL_videosInfos[index].actualWidth, height = ZQL_videosInfos[index].actualHeight;
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
  setAlarms: (data, index) => {
    ZQL_multivideo.clearCanvas(index);
    if (ZQL_videosInfos[index] && !ZQL_videosInfos[index].canvas) {
      ZQL_videosInfos[index].canvas = document.getElementById("canvas" + index)
    }
    if (
      !ZQL_videosInfos[index] ||
      !ZQL_videosInfos[index].actualWidth ||
      !ZQL_videosInfos[index].actualHeight ||
      !ZQL_videosInfos[index].oriWidth ||
      !ZQL_videosInfos[index].oriHeight
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
              (point[0] * ZQL_videosInfos[index].actualWidth) /
              ZQL_videosInfos[index].oriWidth
            ),
            Math.round(
              (point[1] * ZQL_videosInfos[index].actualHeight) /
              ZQL_videosInfos[index].oriHeight
            ),
          ];
        });
        let context = ZQL_videosInfos[index].canvas.getContext("2d");
        context.font = "20px Arial bolder";
        context.fillStyle = "transparent";
        context.strokeStyle = "rgb(" + color.join(",") + ")";
        context.lineWidth = 2;
        ZQL_multivideo.drawPolygons(points, context);
        ZQL_multivideo.drawPolygonInfo(context, Object.values(bbox.polygons), index);
      });
    }

    if (bbox.rectangles.length > 0) {
      bbox.rectangles.forEach((item, i) => {
        let color = JSON.parse(JSON.stringify(item.color)).reverse();
        let coordinates = {
          x: Math.round(
            (item.xyxy[0] * ZQL_videosInfos[index].actualWidth) /
            ZQL_videosInfos[index].oriWidth
          ),
          y: Math.round(
            (item.xyxy[1] * ZQL_videosInfos[index].actualHeight) /
            ZQL_videosInfos[index].oriHeight
          ),
          x1: Math.round(
            (item.xyxy[2] * ZQL_videosInfos[index].actualWidth) /
            ZQL_videosInfos[index].oriWidth
          ),
          y1: Math.round(
            (item.xyxy[3] * ZQL_videosInfos[index].actualHeight) /
            ZQL_videosInfos[index].oriHeight
          ),
        };
        let context = ZQL_videosInfos[index].canvas.getContext("2d");
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
          ZQL_multivideo.drawLine(context, item);
        });
      });
    }

    if (Object.values(bbox.lines).length > 0) {
      Object.values(bbox.lines).forEach((item, i) => {
        let color = JSON.parse(JSON.stringify(item.color)).reverse();
        let coordinates = {
          x: Math.round(
            (item.line[0][0] * ZQL_videosInfos[index].actualWidth) /
            ZQL_videosInfos[index].oriWidth
          ),
          y: Math.round(
            (item.line[0][1] * ZQL_videosInfos[index].actualHeight) /
            ZQL_videosInfos[index].oriHeight
          ),
          x1: Math.round(
            (item.line[1][0] * ZQL_videosInfos[index].actualWidth) /
            ZQL_videosInfos[index].oriWidth
          ),
          y1: Math.round(
            (item.line[1][1] * ZQL_videosInfos[index].actualHeight) /
            ZQL_videosInfos[index].oriHeight
          ),
        };
        let context = ZQL_videosInfos[index].canvas.getContext("2d");
        context.font = "20px Arial bolder";
        context.fillStyle = "rgb(" + color.join(",") + ")";
        if (item.ext.direction) {
          context.fillText(item.name, (coordinates.x + coordinates.x1) / 2, (coordinates.y + coordinates.y1) / 2 + 20);
        }

        context.strokeStyle = "rgb(" + color.join(",") + ")";
        context.lineWidth = 2;
        ZQL_multivideo.drawLine(context, coordinates);
        ZQL_multivideo.drawCountingInfo(context, Object.values(bbox.lines));
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
        (leftPoint[0] * ZQL_videosInfos[videoindex].actualWidth) /
        ZQL_videosInfos[videoindex].oriWidth,
        (leftPoint[1] * ZQL_videosInfos[videoindex].actualHeight) /
        ZQL_videosInfos[videoindex].oriHeight + 20
      );
      if (item.ext.result) {
        context.fillStyle = "rgb(255,0,0)";
        context.fillText(`${item.name}: ${item.ext.result}`, 0, 20 * index + 20);
      }
    });
  },
  destroyVideo(videonum) {
    for (let i = 0; i < videonum; i++) {
      ZQL_multivideo.destoryVideoByIndex(i);
    }
  },
  destoryVideoByIndex(index) {
    ZQL_multivideo.clearCanvas(index);
    console.log(index, ZQL_videosInfos[index]);
    
    if (ZQL_videosInfos[index]) {
      if (
        ZQL_videosInfos[index] &&
        ZQL_videosInfos[index].subscribeTimeout
      ) {
        clearTimeout(ZQL_videosInfos[index].subscribeTimeout);
        ZQL_videosInfos[index].subscribeTimeout = null;
      }
      if (ZQL_videosInfos[index] && ZQL_videosInfos[index].replayTimer) {
        clearTimeout(ZQL_videosInfos[index].replayTimer);
        ZQL_videosInfos[index].replayTimer = null;
      }
      if (ZQL_videosInfos[index].refreshTimeInterval) {
        clearInterval(ZQL_videosInfos[index].refreshTimeInterval);
        ZQL_videosInfos[index].refreshTimeInterval = null;
      }
      let video = document.getElementById("video" + index);
      video && (video.srcObject = null);
      ZQL_videosInfos[index].srsrtc &&
        ZQL_videosInfos[index].srsrtc.destroy();
      ZQL_multivideo.clearCanvas(index);
      ZQL_videosInfos[index] = null;
    }
  },
  clearCanvas(index) {
    let canvas = document.getElementById("canvas" + index);
    if (canvas && canvas.getContext("2d")) {
      canvas
        .getContext("2d")
        .clearRect(0, 0, canvas.offsetWidth, canvas.offsetHeight);
    }
  },
  connectMqtt() {
    let mqttclient = mqtt.connect(`ws://${ZQLGLOBAL.serverIp}:${ZQLGLOBAL.websocket}/mqtt`);
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
          if (ZQL_videosInfos[i]) {
            let alg =
              ZQL_videosInfos[i].alg && ZQL_videosInfos[i].alg.algname;
              if (
                id == ZQL_playingSource[i] &&
                msg.data.alg.name == ZQL_videosInfos[i].alg
              ) {                
              ZQL_multivideo.setAlarms(msg.data.reserved_data, i);
              if (
                ZQL_videosInfos[i] &&
                ZQL_videosInfos[i].canvasTimeout
              ) {
                clearTimeout(ZQL_videosInfos[i].canvasTimeout);
              }
              ZQL_videosInfos[i].canvasTimeout = setTimeout(() => {
                ZQL_multivideo.clearCanvas(i);
              }, 1000);
              break;
            }
          }
        }
      }
      if (msg.msg_type == "stream_code") {
          // console.log("stream_code", msg.data);
        let cameraId = msg.data.source_id;
        for (let i = 0; i < ZQL_playingSource.videoNum; i++) {
          if (cameraId == ZQL_playingSource[i] && ZQL_videosInfos[i]) {
            if (!ZQL_videosInfos[i].stream_code) {
              ZQL_videosInfos[i].stream_code = msg.data.stream_code;
            } else if (
              msg.data.stream_code != ZQL_videosInfos[i].stream_code
            ) {
              ZQL_videosInfos[i].stream_code = msg.data.stream_code;
              ZQL_multivideo.handleRefresh(i);
            }
            break;
          }
        }
      }

    });
  }
}
// http接口
const ZQL_apis = {
  getSources: () => {
    return new Promise((resolve, reject) => {
      // $.ajax({
      //   type: "GET",
      //   dataType: "json",
      //   url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.getSources}`,
      //   success: function (res) {
      //     resolve(res)
      //   },
      //   error: function (err) {
      //     reject(err)
      //   }
      // });
      resolve({
        "error_code": 0,
        "message": {
            "zh": "查询数据源成功！",
            "en": "Query source successful!"
        },
        "data": [
            {
                "id": "1001",
                "index": 15,
                "ipv4": "192.168.101.231",
                "desc": "云台可见光",
                "type": "camera",
                "encoding": "h264",
                "info": {
                    "protocol": "rtsp",
                    "rtsptype": "gen",
                    "brand": "2",
                    "stream": ":554/h264/ch1/main/av_stream",
                    "username": "admin",
                    "password": "yoseen2018",
                    "rtsp_transport": "tcp"
                },
                "stream": "rtsp://admin:yoseen2018@192.168.101.231:554/h264/ch1/main/av_stream",
                "infer_size": 640,
                "draw_size": [
                    1280,
                    723
                ],
                "alg": {},
                "video_record": 0,
                "status": 1
            },
            {
                "id": "1002",
                "index": 16,
                "ipv4": "192.168.101.231",
                "desc": "云台红外光",
                "type": "camera",
                "encoding": "h264",
                "info": {
                    "protocol": "rtsp",
                    "rtsptype": "input",
                    "brand": "2",
                    "stream": ":554/h264/ch1/main/av_stream",
                    "username": "admin",
                    "password": "yoseen2018",
                    "rtsp_transport": "tcp"
                },
                "stream": "rtsp://admin:yoseen2018@192.168.101.231:554/h264/ch2/main/av_stream",
                "infer_size": 640,
                "draw_size": [
                    1280,
                    960
                ],
                "alg": {},
                "video_record": 0,
                "status": 1
            },
            {
                "id": "23_xxx",
                "index": 17,
                "ipv4": "192.168.1.105",
                "desc": "机器狗前置摄像头",
                "type": "camera",
                "encoding": "h264",
                "info": {
                    "protocol": "rtsp",
                    "rtsptype": "input",
                    "brand": "",
                    "stream": "",
                    "username": "",
                    "password": "",
                    "rtsp_transport": "tcp"
                },
                "stream": "rtsp://192.168.101.105:8554/test",
                "infer_size": 640,
                "draw_size": [
                    1280,
                    960
                ],
                "alg": {},
                "video_record": 0,
                "status": 1
            },
            {
                "id": "23",
                "index": 13,
                "ipv4": "192.168.101.231",
                "desc": "desc_1761723515238",
                "type": "stream",
                "encoding": "h264",
                "info": {
                    "rtsp_transport": "tcp",
                    "username": "",
                    "password": ""
                },
                "stream": "rtsp://admin:CDqhxxjs.@192.168.124.93:554/Streaming/Channels/101",
                "infer_size": 640,
                "draw_size": [
                    1280,
                    720
                ],
                "alg": {
                    "person_gathering": {
                        "alert_window": {
                            "length": 5,
                            "interval": 5,
                            "threshold": 3,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_person": {
                                "conf_thres": 0.6
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "counting",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "reserved_args": {
                            "ch_name": "人员聚集",
                            "threshold": 2,
                            "sound_text": "人员聚集告警",
                            "strategy": "bottom"
                        }
                    },
                    "work_clothes": {
                        "alert_window": {
                            "length": 5,
                            "interval": 5,
                            "threshold": 4,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_person": {
                                "conf_thres": 0.65
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "match_work_clothes",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "reserved_args": {
                            "group_id": "68da2dc31271cb0bd850989c",
                            "similarity": 0.5,
                            "extra_model": {
                                "zql_work_clothes": 5
                            },
                            "ch_name": "未穿工服检测",
                            "sound_text": "未穿工服检测告警",
                            "group_type": "blacklist"
                        }
                    },
                    "fire": {
                        "alert_window": {
                            "length": 3,
                            "interval": 5,
                            "threshold": 2,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_fire_classify": {
                                "conf_thres": 0.6
                            },
                            "zql_fire": {
                                "conf_thres": 0.6
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "general",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86400
                                ]
                            ]
                        },
                        "reserved_args": {
                            "extra_model": {
                                "zql_fire_classify": 1
                            },
                            "ch_name": "明火检测",
                            "sound_text": "明火检测告警",
                            "iou": 0.9
                        }
                    },
                    "fight": {
                        "alert_window": {
                            "length": 3,
                            "interval": 5,
                            "threshold": 2,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_fight_classify": {
                                "conf_thres": 0.5
                            },
                            "zql_fight": {
                                "conf_thres": 0.6
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "general",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86400
                                ]
                            ]
                        },
                        "reserved_args": {
                            "extra_model": {
                                "zql_fight_classify": 1
                            },
                            "ch_name": "打架检测",
                            "sound_text": "打架检测告警"
                        }
                    },
                    "face": {
                        "alert_window": {
                            "type": "interval_threshold_window",
                            "interval": 10,
                            "length": 1,
                            "threshold": 1
                        },
                        "bbox": {
                            "polygons": [],
                            "lines": []
                        },
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "hazard_level": "",
                        "alg_type": "match_face",
                        "model_args": {
                            "zql_face": {
                                "conf_thres": 0.5
                            }
                        },
                        "reserved_args": {
                            "ch_name": "人脸识别",
                            "ch_name_tooltip": "一张图片最多支持识别5个人脸",
                            "sound_text": "人脸识别告警",
                            "threshold": 0,
                            "similarity": 0.65,
                            "group_id": "68edf5001271cb0b67af20fa",
                            "group_type": "blacklist"
                        }
                    },
                    "lpr": {
                        "alert_window": {
                            "type": "interval_threshold_window",
                            "interval": 1,
                            "length": 5,
                            "threshold": 3
                        },
                        "bbox": {
                            "polygons": [],
                            "lines": []
                        },
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "hazard_level": "",
                        "alg_type": "general",
                        "model_args": {
                            "zql_lpr": {
                                "conf_thres": 0.5
                            }
                        },
                        "reserved_args": {
                            "ch_name": "车牌识别",
                            "sound_text": "车牌识别告警"
                        }
                    }
                },
                "video_record": 1,
                "status": -1
            },
            {
                "id": "27",
                "index": 18,
                "ipv4": "192.168.101.231",
                "desc": "desc_1763993844429",
                "type": "stream",
                "encoding": "h264",
                "info": {
                    "rtsp_transport": "tcp",
                    "username": "",
                    "password": ""
                },
                "stream": "rtsp://admin:yoseen2018@192.168.101.231:554/Streaming/Channels/101",
                "infer_size": 640,
                "draw_size": [
                    1280,
                    723
                ],
                "alg": {
                    "face": {
                        "alert_window": {
                            "length": 1,
                            "interval": 1,
                            "threshold": 1,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_face": {
                                "conf_thres": 0.5
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "match_face",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "reserved_args": {
                            "group_id": "6920326f1271cb0eb1215c3b",
                            "similarity": 0.65,
                            "ch_name_tooltip": "一张图片最多支持识别5个人脸",
                            "ch_name": "人脸识别",
                            "threshold": 0,
                            "sound_text": "人脸识别告警",
                            "group_type": "blacklist"
                        }
                    },
                    "person_gathering": {
                        "alert_window": {
                            "length": 5,
                            "interval": 5,
                            "threshold": 3,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_person": {
                                "conf_thres": 0.6
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "counting",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "reserved_args": {
                            "ch_name": "人员聚集",
                            "threshold": 2,
                            "sound_text": "人员聚集告警",
                            "strategy": "bottom"
                        }
                    },
                    "work_clothes": {
                        "alert_window": {
                            "length": 5,
                            "interval": 5,
                            "threshold": 4,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_person": {
                                "conf_thres": 0.65
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "match_work_clothes",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "reserved_args": {
                            "group_id": "68da2dc31271cb0bd850989c",
                            "similarity": 0.5,
                            "extra_model": {
                                "zql_work_clothes": 5
                            },
                            "ch_name": "未穿工服检测",
                            "sound_text": "未穿工服检测告警",
                            "group_type": "blacklist"
                        }
                    },
                    "fire": {
                        "alert_window": {
                            "length": 3,
                            "interval": 5,
                            "threshold": 2,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_fire_classify": {
                                "conf_thres": 0.6
                            },
                            "zql_fire": {
                                "conf_thres": 0.6
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "general",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86400
                                ]
                            ]
                        },
                        "reserved_args": {
                            "extra_model": {
                                "zql_fire_classify": 1
                            },
                            "ch_name": "明火检测",
                            "sound_text": "明火检测告警",
                            "iou": 0.9
                        }
                    },
                    "fight": {
                        "alert_window": {
                            "length": 3,
                            "interval": 5,
                            "threshold": 2,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_fight_classify": {
                                "conf_thres": 0.5
                            },
                            "zql_fight": {
                                "conf_thres": 0.6
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "general",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86400
                                ]
                            ]
                        },
                        "reserved_args": {
                            "extra_model": {
                                "zql_fight_classify": 1
                            },
                            "ch_name": "打架检测",
                            "sound_text": "打架检测告警"
                        }
                    },
                    "lpr": {
                        "alert_window": {
                            "length": 5,
                            "interval": 1,
                            "threshold": 3,
                            "type": "interval_threshold_window"
                        },
                        "hazard_level": "",
                        "model_args": {
                            "zql_lpr": {
                                "conf_thres": 0.5
                            }
                        },
                        "bbox": {
                            "polygons": [
                                {
                                    "polygon": [
                                        [
                                            0,
                                            0
                                        ],
                                        [
                                            1280,
                                            0
                                        ],
                                        [
                                            1280,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ],
                                        [
                                            0,
                                            723
                                        ]
                                    ],
                                    "name": "",
                                    "id": ""
                                }
                            ],
                            "lines": []
                        },
                        "alg_type": "general",
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86399
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86399
                                ]
                            ]
                        },
                        "reserved_args": {
                            "ch_name": "车牌识别",
                            "sound_text": "车牌识别告警"
                        }
                    }
                },
                "video_record": 1,
                "status": 1
            },
            {
                "id": "691eec411271cb0eb1213b92",
                "index": 14,
                "ipv4": "",
                "desc": "拳击视频.mp4",
                "type": "video",
                "encoding": "h264",
                "info": {
                    "rtsp_transport": "tcp",
                    "performance_mode": true
                },
                "stream": "rtmp://127.0.0.1:1935/live/2e0b0eff-2e17-4da3-86e4-36c36375ef3c/691eec411271cb0eb1213b92/video",
                "infer_size": 640,
                "draw_size": [
                    1280,
                    720
                ],
                "alg": {
                    "fight": {
                        "alert_window": {
                            "type": "interval_threshold_window",
                            "interval": 5,
                            "length": 3,
                            "threshold": 2
                        },
                        "bbox": {
                            "polygons": [],
                            "lines": []
                        },
                        "plan": {
                            "1": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "2": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "3": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "4": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "5": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "6": [
                                [
                                    0,
                                    86400
                                ]
                            ],
                            "7": [
                                [
                                    0,
                                    86400
                                ]
                            ]
                        },
                        "hazard_level": "",
                        "alg_type": "general",
                        "model_args": {
                            "zql_fight": {
                                "conf_thres": 0.6
                            },
                            "zql_fight_classify": {
                                "conf_thres": 0.5
                            }
                        },
                        "reserved_args": {
                            "ch_name": "打架检测",
                            "sound_text": "打架检测告警",
                            "extra_model": {
                                "zql_fight_classify": 1
                            }
                        }
                    }
                },
                "video_record": 0,
                "status": 1
            }
        ]
      })
    })
  },
  subscribeLive: (source_id) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.subscribe}?source_id=${source_id}`,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  // 获取一些服务连接端口 srs_server srs_http_api srs_http_server websocket
  sysArgs: () => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.tunnel}`,
        success: function (tunnel) {
          if (tunnel.data.enable == false) {
            $.ajax({
              type: "GET",
              dataType: "json",
              url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.sysArgs}`,
              success: function (res) {
                resolve(res.data.map.local)
              },
              error: function (err) {
                reject(err)
              }
            });
          } else {
            resolve({
              "srs_server": tunnel.data.srs_server,
              "srs_http_api": tunnel.data.srs_http_api,
              "srs_http_server": tunnel.data.srs_http_server,
              "websocket": tunnel.data.websocket
            })
          }

        },
        error: function (err) {
          reject(err)
        }
      });

    })
  },
  detectStream: () => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}:${ZQLGLOBAL.srs_http_api}/api/v1/streams?start=0&count=10000`,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  detectVideo: (device_id, stream) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.detect}?device_id=${device_id}&stream=${stream}&draw_size=1280`,
        success: function (res) {
          if (res.error == 0) {
            resolve({ status: 1 })
          } else {
            resolve({ status: 0 })
          }
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  gettoken: () => {
    var ak = ZQLGLOBAL.accessKey;
    var sk = ZQLGLOBAL.accessSecret;
    var timestamp = parseInt(new Date().getTime() / 1000);
    var nonce = ZQL_apis.generateRandomString(10);
    let signature = ZQL_apis.generateSignature(ak, sk, timestamp, nonce)
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.getToken}?signature=${signature}&ak=${ak}&timestamp=${timestamp}&nonce=${nonce}`,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  generateSignature: (ak, sk, timestamp, nonce) => {
    var message = `${ak}:${timestamp}:${nonce}`;
    var hash = CryptoJS.HmacSHA256(message, sk);
    var signature = CryptoJS.enc.Hex.stringify(hash);
    return signature
  },
  generateRandomString(length) {
    let result = '';
    const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

    for (let i = 0; i < length; i++) {
      result += characters.charAt(Math.floor(Math.random() * characters.length));
    }

    return result;
  }
}

function init() {

  // 设置文档结构
  ZQL_multivideo.setVideoEl();

  // 设置事件监听
  document.querySelector("#icon-oneviveo").addEventListener('click', () => {
    console.log(1);
    
    ZQL_playingSource.videoNum = 1;
    ZQL_multivideo.setVideoEl();
  })
  document.querySelector("#icon-fourviveo").addEventListener('click', () => {
    console.log(4);
    
    ZQL_playingSource.videoNum = 4;
    ZQL_multivideo.setVideoEl();
  })

  // 1. 获取摄像头列表并渲染
  Promise.all([ZQL_apis.getSources()]).then(res => {
    console.log('获取摄像头列表', res);
    let cameras = res[0].data.map(item => {
      item.sourceId = item.id;
      item.title = item.desc;
      item.type = 'source';
      item.checked = false;
      ZQL_sources[item.id] = item
      return item
    });
    // 渲染摄像头列表
    layui.use(function () {
      var tree = layui.tree;
      var layer = layui.layer;
      tree.render({
        elem: '#ZQL_source_tree',
        data: cameras,
        // showCheckbox:true,
        onlyIconControl: true,  // 是否仅允许节点左侧图标控制展开收缩
        click: function (obj) {
          console.log('obj========', obj);
          
          if (obj.data.sourceId) {
            let key = obj.data.sourceId;
            if (ZQL_sources[key].checked == false) {
              ZQL_sources[key].checked = true
              if (ZQL_playingSource.videoNum == 1) {
                ZQL_playingSource[0] = key;
                ZQL_multivideo.subscribeLive(key, 0);
                ZQL_multivideo.setAlgList(0);
              } else {
                for (let i = 0; i < 4; i++) {
                  if (!ZQL_playingSource[i]) {
                    ZQL_playingSource[i] = key;
                    ZQL_multivideo.subscribeLive(key, i);
                    ZQL_multivideo.setAlgList(i)
                    break;
                  }
                }
              }
            } else {
              ZQL_sources[key].checked = false
              if (ZQL_playingSource.videoNum == 1) {
                ZQL_playingSource[0] = null;
                ZQL_multivideo.destoryVideoByIndex(0);
                ZQL_multivideo.clearAlgList(0);
                ZQL_multivideo.liveStopLoading(0);
              } else {
                for (let i = 0; i < 4; i++) {
                  if (ZQL_playingSource[i] == key) {
                    ZQL_playingSource[i] = null;
                    ZQL_multivideo.destoryVideoByIndex(i);
                    ZQL_multivideo.clearAlgList(i)
                    ZQL_multivideo.liveStopLoading(i);
                  }
                }
              }
            }
          }
        }
      });
    });
  }).catch(err => {
    console.log(err)
  })
  // 获取系统参数，连接mqtt,通过mqtt获取实时检测结果和视频流状态码
  ZQL_apis.sysArgs().then(res => {
    ZQLGLOBAL = Object.assign(ZQLGLOBAL, res)
    ZQL_multivideo.connectMqtt()
  }).catch(err => { })
}