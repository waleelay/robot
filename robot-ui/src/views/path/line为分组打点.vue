<!--
 * @Author: dengxumei
 * @Date: 2025-09-09 17:00:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2025-09-16 16:20:33
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\path\line为分组打点.vue
 * @Version: 
-->
<template>
  <canvas :id="id" class="drawingCanvas"></canvas>
</template>

<script>
export default {
  props: {
    className: {
      type: String,
      default: 'scaled-image'
    },
    id: {
      type: String,
      default: 'drawingCanvas'
    },
    // 是否需要圆点
    needArc: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      // 点的半径
      pointRadius: 5,
      // 存储所有点的数组
      points: [],
      canvas: null,
      ctx: null,
      colors: ['#0079fe', '#eab516', '#e74c3c', '#55ce9f', '#586b8c', '#5689ee']
    }
  },
  mounted() {
    // this.init()
  },
  methods: {
    init() {
      if (this.canvas) return
      this.canvas = document.getElementById(this.id);
      const img = document.getElementsByClassName(this.className)[0]
      this.ctx = this.canvas.getContext('2d');
      // 设置画布大小为图片在页面中显示的像素大小
      // this.canvas.width = width;
      // this.canvas.height = height;
      this.canvas.width = img.clientWidth;
      this.canvas.height = img.clientHeight;
      // console.log(img.clientWidth);
      // console.log(img.clientHeight);
      
      // console.log('111canvas', img, this.id, this.className, img.clientWidth, img.clientHeight);

      // 窗口大小改变时调整画布大小
      window.addEventListener('resize', function() {
          this.canvas.width = window.innerWidth;
          this.canvas.height = window.innerHeight;
          this.redrawCanvas();
      });
      this.redrawCanvas()
    },
    createLine(event) {
      this.init()
      // 相对于视口位置
      const rect = this.canvas.getBoundingClientRect()
      // 获取鼠标位置
      // console.log('event', event);
      
      const x = event.clientX - rect.left;
      const y = event.clientY - rect.top;
      
      // 将点添加到数组
      // console.log('点========', x, y);
      this.points.push({ x, y });
      
      // 重绘画布
      this.redrawCanvas();
    },
    deleteLine(index) {
      this.points.splice(index, 1)
      this.redrawCanvas()
    },
    redrawCanvas(colorIndex = 0) {
      this.init()
      // 清空画布
      this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      
      // 绘制所有线段
      this.ctx.beginPath();
      this.ctx.strokeStyle = this.id === 'luxiang-drawingCanvas' ? '#4d995f' : this.colors[colorIndex];
      this.ctx.lineWidth = this.id === 'luxiang-drawingCanvas' ? 5 : 2;
      if (this.points.length > 1) {
        this.ctx.moveTo(this.points[0].x, this.points[0].y);
        for (let i = 1; i < this.points.length; i++) {
          this.ctx.lineTo(this.points[i].x, this.points[i].y);
        }
        this.ctx.stroke();
      }
      
      // 绘制所有点
      // if (this.id !== 'luxiang-drawingCanvas') {
        this.ctx.fillStyle = '#e74c3c';
      // }
      // this.ctx.font = '12px Arial';
      // this.ctx.textAlign = 'center';
      // this.ctx.textBaseLine = 'bottom';
      // for (const point of this.points) {
      //   this.ctx.beginPath();
      //   this.ctx.arc(point.x, point.y, this.pointRadius, 0, Math.PI * 2);
      //   this.ctx.fill();

      //   // 绘制标签
      //   this.ctx.fillStyle = '#f00'
      //   this.ctx.fillText = '#f00'
      // }
      this.points.map((point, index) => {
        if (this.id === 'luxiang-drawingCanvas') {
          this.ctx.fillStyle = '#e74c3c'
        } else {
          if (index === 0) {
            // this.ctx.fillStyle = '#e74c3c';
          } else if (index === this.points.length - 1) {
            this.ctx.fillStyle = '#00ac3a';
          } else {
            this.ctx.fillStyle = '#0079fe';
          }
        }
        this.ctx.beginPath();
        if (this.needArc || this.points[index].needArc) {
          this.ctx.arc(point.x + 0.5, point.y + 0.75, this.pointRadius, 0, Math.PI * 2);
        }
        this.ctx.fill();

        // 绘制标签
        // this.ctx.fillStyle = '#f00'
        // this.ctx.fillText = ('点' + (index + 1), point.y - this.pointRadius - 2, this.pointRadius, 0)
        // this.ctx.fillStyle = '#f0f'
      })
    }
  }
}
</script>

<style lang="scss">
.drawingCanvas {
  position: absolute;
  // width: 100%;
  // height: 100%;
  top: 0;
  left: 0;
}
</style>