import { computed } from 'vue';

export default {
  name: 'AddPointTask',
  data() {
    return {
      // ---------- 配置 ----------
      imagePath: require('./3.png'), // 请替换为您的PNG实际路径
      safetyMargin: 1.5,           // 安全距离（像素）

      // ---------- 状态 ----------
      img: null,
      canvas: null,
      ctx: null,
      grid: [],      // 障碍物网格 (0=可通行, 1=障碍/安全区)
      W: 0,
      H: 0,
      startPoint: null, // [x, y]
      endPoint: null,   // [x, y]
      unloadedPath: [],         // [[x, y], ...]
      loadedPath: [],         // [[x, y], ...]
      currentPoint: null,
      isLoaded: false,

      locationPoint: null, 
    };
  },
  computed: {
    locationStyle() {
      const rect = this.$refs?.pointLocationRef?.getBoundingClientRect() || { width: 0, height: 0 };
      // console.log('缩放', rect.width, rect.height, this.getScaleContext().scaleX, this.getScaleContext().scaleY);
      return {
        top: `${((this.locationPoint?.y || 0) - rect.height / 2) / this.getScaleContext().scaleY}px`,
        left: `${((this.locationPoint?.x || 0) - rect.width / 2) / this.getScaleContext().scaleX}px`,
      };
    },
  },
  mounted() {
    this.canvas = this.$refs.canvas;
    this.ctx = this.canvas.getContext('2d');
    this.loadMap();

  },
  methods: {
    // ---------- 加载地图 ----------
    loadMap() {
      const img = new Image();
      img.crossOrigin = 'anonymous';
      img.onload = () => {
        this.img = img;
        this.W = this.canvas.width = img.width;
        this.H = this.canvas.height = img.height;
        this.buildGrid(img);
        this.isLoaded = true;
        this.draw();
        console.log('地图加载完成，安全距离已应用');
      };
      img.onerror = () => {
        alert('图片加载失败，请确认路径正确: ' + this.imagePath);
      };
      img.src = this.imageUrl;
    },

    // ---------- 构建障碍物网格（含安全距离） ----------
    buildGrid(img) {
      const ctx = this.ctx;
      ctx.drawImage(img, 0, 0);
      const imageData = ctx.getImageData(0, 0, this.W, this.H);
      const data = imageData.data;

      // 1. 识别黑色像素
      const obstacle = [];
      for (let y = 0; y < this.H; y++) {
        obstacle[y] = [];
        for (let x = 0; x < this.W; x++) {
          const i = (y * this.W + x) * 4;
          const brightness = (data[i] + data[i + 1] + data[i + 2]) / 3;
          obstacle[y][x] = brightness < 60 ? 1 : 0;
        }
      }

      // 2. 扩展安全距离
      const margin = this.safetyMargin;
      this.grid = [];
      for (let y = 0; y < this.H; y++) {
        this.grid[y] = [];
        for (let x = 0; x < this.W; x++) {
          let blocked = false;
          // 检查周围 margin 范围内是否有黑色像素
          for (let dy = -margin; dy <= margin; dy++) {
            for (let dx = -margin; dx <= margin; dx++) {
              const ny = y + dy;
              const nx = x + dx;
              if (ny < 0 || ny >= this.H || nx < 0 || nx >= this.W) continue;
              if (obstacle[ny] && obstacle[ny][nx] === 1) {
                blocked = true;
                break;
              }
            }
            if (blocked) break;
          }
          this.grid[y][x] = blocked ? 1 : 0;
        }
      }
    },
    drawLine(paths) {
      if (!this.img) return;
      const ctx = this.ctx;
      ctx.drawImage(this.img, 0, 0, this.W, this.H);
      // 添加文字（核心）
      // ctx.fillStyle = '#0D9F31';
      // ctx.fillRect(this.startPoint[0], this.startPoint[1], 50, 22);
      ctx.fillStyle = '#0D9F31';
      ctx.font = '8px Arial';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      // ctx.lineHeight = '18px';
      // ctx.padding = '2px 4px';
      ctx.fillText('起始地', this.startPoint[0], this.startPoint[1]);
      // 绘制路径（亮蓝色）
      if (paths && paths.length > 0) {
        // 遍历每条路径
        paths.forEach((path, index) => {
          // 设置样式（可根据索引或条件设置不同颜色）
          ctx.strokeStyle = index > 0 ? '#0D9F31' : '#0BF9FE';
          ctx.lineWidth = 1;
          
          if (index === 0) {
            ctx.setLineDash([5, 2]);
          } else {
            ctx.setLineDash([]); // 实线
          }

          ctx.beginPath();
          ctx.moveTo(path[0][0], path[0][1]);
          for (let i = 1; i < path.length; i++) {
            ctx.lineTo(path[i][0], path[i][1]);
          }
          ctx.stroke();
        });

        ctx.shadowBlur = 0;
      }
    },

    // ---------- 绘制 ----------
    draw() {
      if (!this.img) return;
      const ctx = this.ctx;
      ctx.drawImage(this.img, 0, 0, this.W, this.H);

      // // 绘制起点（绿色）
      // if (this.startPoint) {
      //   this.mark(this.startPoint, '#2ecc71');
      // }
      // // 绘制终点（红色）
      // if (this.endPoint) {
      //   this.mark(this.endPoint, '#e74c3c');
      // }

      // 绘制路径（亮蓝色）
      if (this.unloadedPath && this.unloadedPath.length > 0) {
        ctx.strokeStyle = '#0BF9FE';
        ctx.lineWidth = 3;
        // ctx.shadowColor = 'rgba(0, 200, 255, 0.5)';
        // ctx.shadowBlur = 8;
        ctx.setLineDash([5, 5]);
        ctx.beginPath();
        ctx.moveTo(this.unloadedPath[0][0], this.unloadedPath[0][1]);
        for (let i = 1; i < this.unloadedPath.length; i++) {
          ctx.lineTo(this.unloadedPath[i][0], this.unloadedPath[i][1]);
        }
        ctx.stroke();
        ctx.shadowBlur = 0;
      }
    },

    mark(point, color) {
      return
      const ctx = this.ctx;
      ctx.fillStyle = color;
      ctx.shadowColor = 'rgba(255, 255, 255, 0.3)';
      ctx.shadowBlur = 10;
      ctx.beginPath();
      
      ctx.arc(point[0], point[1], 6, 0, Math.PI * 2);
      ctx.fill();
      ctx.shadowBlur = 0;
    },

    // ---------- 重置 ----------
    reset() {
      this.startPoint = null;
      this.endPoint = null;
      this.currentPoint = null;
      this.unloadedPath = [];
      this.loadedPath = [];
      this.draw();
    },

    // ---------- 点击处理 ----------
    onCanvasClick(event) {
      if (!this.isLoaded) {
        alert('地图尚未加载完成，请稍后');
        return;
      }
      const rect = this.canvas.getBoundingClientRect();      
      const scaleX = this.W / rect.width;
      const scaleY = this.H / rect.height;
      const x = Math.floor((event.clientX - rect.left) * scaleX);
      const y = Math.floor((event.clientY - rect.top) * scaleY);

      if (x < 0 || x >= this.W || y < 0 || y >= this.H) return;

      // 检查是否可通行
      if (this.grid[y] && this.grid[y][x] === 1) {
        alert('该位置不可通行（障碍物或安全距离内）');
        return;
      }
      const pixel = this.eventToPixel(event);
      const point = this.pixelToMapPoint(pixel, this.map);
      this.locationPoint = { x: event.clientX - rect.left, y: event.clientY - rect.top, pixelX: point.x, pixelY: point.y, pixelX: point.pixelX, pixelY: point.pixelY };
      
      // 如果没有起点，设置起点
      // if (!this.startPoint) {
      //   this.startPoint = [x, y];
      //   this.unloadedPath = [];
      //   this.draw();
      //   return;
      // }

      // 如果没有终点，设置终点并计算路径
      if (!this.endPoint) {
        this.endPoint = [x, y];
        // this.unloadedPath = this.aStar(this.startPoint, this.endPoint);
        // if (!this.unloadedPath || this.unloadedPath.length === 0) {
        //   alert('无法找到安全路径，请尝试其他终点');
        // }
        
        const pixelStart = this.drawablePoints?.[0]?.pixel || { x: 0, y: 0 }
        this.startPoint = [parseInt(pixelStart.x), parseInt(pixelStart.y)]
        this.currentPoint = [parseInt(pixelStart.x), parseInt(pixelStart.y)]
        this.renderUnloaded();

        
        setTimeout(() => {
          console.log('渲染loaded');
          
          this.renderLoaded();
        }, 3000);
        return;
      }

      // 如果已有起点和终点，重置并重新开始
      // this.startPoint = [x, y];
      this.startPoint = null;
      this.endPoint = [x, y];
      // console.log(x, y);
      
      this.unloadedPath = [];
      this.loadedPath = [];
      this.draw();
    },

    renderUnloaded() {
      this.unloadedPath = this.aStar(this.currentPoint, this.endPoint);
      if (!this.unloadedPath || this.unloadedPath.length === 0) {
        alert('无法找到安全路径，请尝试其他终点');
        return
      }
      this.drawLine([this.unloadedPath]);
    },
    renderLoaded() {
      // 选择机器狗后计算距离
      const pixelStart = this.drawablePoints?.[1]?.pixel || { x: 0, y: 0 }
      this.currentPoint = [29, 39]
      // console.log(333, this.startPoint, this.currentPoint);
      
      this.loadedPath = this.aStar(this.startPoint, this.currentPoint);
      if (!this.loadedPath || this.loadedPath.length === 0) {
        alert('无法找到安全路径，请尝试其他终点');
        return
      }
      this.unloadedPath = this.aStar(this.currentPoint, this.endPoint);
      this.drawLine([this.unloadedPath, this.loadedPath]);
    },

    // ---------- A* 寻路（8方向，使用安全距离网格） ----------
    aStar(start, end) {
      const W = this.W;
      const H = this.H;
      const grid = this.grid;

      const key = (p) => p[0] + ',' + p[1];
      const heuristic = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);

      const open = [{ p: start, g: 0, f: heuristic(start, end) }];
      const came = {};
      const cost = {};
      cost[key(start)] = 0;

      const dirs = [
        [1, 0],
        [-1, 0],
        [0, 1],
        [0, -1],
        [1, 1],
        [-1, -1],
        [1, -1],
        [-1, 1],
      ];

      while (open.length > 0) {
        open.sort((a, b) => a.f - b.f);
        const cur = open.shift();
        const p = cur.p;

        if (p[0] === end[0] && p[1] === end[1]) {
          const result = [p];
          let current = p;
          while (key(current) !== key(start)) {
            current = came[key(current)];
            result.push(current);
          }
          return result.reverse();
        }

        for (const d of dirs) {
          const nx = p[0] + d[0];
          const ny = p[1] + d[1];
          if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
          if (grid[ny] && grid[ny][nx] === 1) continue;

          const nk = nx + ',' + ny;
          const ng = cur.g + Math.hypot(d[0], d[1]);
          if (cost[nk] === undefined || ng < cost[nk]) {
            cost[nk] = ng;
            came[nk] = p;
            open.push({
              p: [nx, ny],
              g: ng,
              f: ng + heuristic([nx, ny], end),
            });
          }
        }
      }
      return [];
    },
    updateLine() {
      this.unloadedPath = this.aStar(this.startPoint, this.endPoint);
      if (!this.unloadedPath || this.unloadedPath.length === 0) {
        this.$message.warning('无法找到安全路径，请尝试其他终点');
      }
      this.draw();
    },
    getScaleContext() {
      const wrapper = this.$el && this.$el.closest && this.$el.closest('.screen-wrapper')
      
      if (!wrapper) {
        return {
          left: 0,
          top: 0,
          scaleX: 1,
          scaleY: 1,
          width: window.innerWidth,
          height: window.innerHeight
        }
      }
      const rect = wrapper.getBoundingClientRect()
      // 实际宽高，未缩放比例 1920*1080
      // console.log('rect', rect.top, rect.left, rect.width, rect.height, wrapper.offsetWidth, wrapper.offsetHeight);
      
      const scaleX = rect.width && wrapper.offsetWidth ? rect.width / wrapper.offsetWidth : 1
      const scaleY = rect.height && wrapper.offsetHeight ? rect.height / wrapper.offsetHeight : 1
      return {
        left: rect.left,
        top: rect.top,
        scaleX: scaleX || 1,
        scaleY: scaleY || 1,
        width: wrapper.offsetWidth || window.innerWidth,
        height: wrapper.offsetHeight || window.innerHeight
      }
    }
  },
}