export default {
  methods: {
    // 多边形
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
    // 画线
    drawLine(ctx, line) {
      ctx.beginPath();
      ctx.moveTo(line.x, line.y);
      ctx.lineTo(line.x1, line.y1);
      ctx.stroke();
    },
    // 数量
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
    drawPolygonInfo(context, polygons, widthScale, heightScale) {
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
          leftPoint[0] * widthScale,
          leftPoint[1] * heightScale + 20
        );
        if (item.ext.result) {
          context.fillStyle = "rgb(255,0,0)";
          context.fillText(`${item.name}: ${item.ext.result}`, 0, 20 * index + 20);
        }
      });
    },
    // 清空画布
    clearCanvas(idName) {
      let canvas = document.getElementById(idName);
      if (canvas && canvas.getContext("2d")) {
        canvas
          .getContext("2d")
          .clearRect(0, 0, canvas.offsetWidth, canvas.offsetHeight);
      }
    },
    // 处理告警画布
    updateOneCanvasByBbox(bbox, canvas, widthScale, heightScale) {
      if (Object.values(bbox.polygons).length > 0) {
        Object.values(bbox.polygons).forEach((item) => {
          let color = JSON.parse(JSON.stringify(item.color)).reverse();
          // let color = item.color;
          let points = item.polygon.map((point) => {
            // 四舍五入，返回整数
            return [Math.round(point[0] * widthScale), Math.round(point[1] * heightScale)];
          });
          let context = canvas.getContext("2d");
          context.font = "20px Arial bolder";
          context.fillStyle = "transparent";
          context.strokeStyle = "rgb(" + color.join(",") + ")";
          context.lineWidth = 2;
          this.drawPolygons(points, context);
          this.drawPolygonInfo(context, Object.values(bbox.polygons), widthScale, heightScale);
        });
      }

      if (bbox.rectangles.length > 0) {
        bbox.rectangles.forEach((item, i) => {
          let color = JSON.parse(JSON.stringify(item.color)).reverse();
          let coordinates = {
            x: Math.round(item.xyxy[0] * widthScale),
            y: Math.round(item.xyxy[1] * heightScale),
            x1: Math.round(item.xyxy[2] * widthScale),
            y1: Math.round(item.xyxy[3] * heightScale),
          };
          let context = canvas.getContext("2d");
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
            x: Math.round(item.line[0][0] * widthScale),
            y: Math.round(item.line[0][1] * heightScale),
            x1: Math.round(item.line[1][0] * widthScale),
            y1: Math.round(item.line[1][1] * heightScale),
          };
          let context = canvas.getContext("2d");
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
    // 设置视频和画布位置
    setPosition(index, width, height) {
      let container = document.querySelector(`#${this.prefixId}${index}`);
      let video = document.querySelector(`#${this.prefixId}video${index}`);
      let canvas = document.getElementById(`${this.prefixId}canvas${index}`);
      video.style.position = "absolute";
      video.style.width = width + "px";
      video.style.height = height + "px";
      canvas.width = width;
      canvas.height = height;
      // 居中布局      
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
    // 处理视频渲染尺寸
    setOrisize(width, height, index, id, containerWidth, containerHeight) {
      let container = document.querySelector(`#${this.prefixId}${index}`);
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
          id,
          loading: true,
          openWs: true,
          alg,
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
      } else {
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
        this.setPosition(index, this.ZQL_videosInfos[index].actualWidth, this.ZQL_videosInfos[index].actualHeight);
      }

      this.$nextTick(() => {
        // 处理全屏切换 append-to-body的影响
        const container1 = this.getRef(`dropdownRef${index}`);
        const menu = this.getRef(`dropdownMenuRef${index}`);
        if (container1 && menu && menu.popperElm) {
          menu.popperElm.classList.add('top_unset', 'left_unset')
          menu.popperElm.style.bottom = '35px'
          container1.appendChild(menu.popperElm)
        }
      })
    },
    getCameraSize(id, index) {
      this.setOrisize(
        this.ZQL_sources[id].draw_size[0],
        this.ZQL_sources[id].draw_size[1],
        index, id
      );
    },
    // 全屏-处理视频渲染尺寸
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
        if (height > parentEl.clientHeight) {
          height = parentEl.clientHeight
          width = parentEl.clientHeight * scale
        }
        document.getElementsByClassName(className)[0].style.width = `${width}px`;
        document.getElementsByClassName(className)[0].style.height = `${height}px`;
        this.setOrisize(draw_size[0], draw_size[1], index, this.ZQL_playingSource[index], width, height)
      })
    },
  }
}