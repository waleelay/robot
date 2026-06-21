<!--
 * @Author: dengxumei
 * @Date: 2025-09-09 17:00:53
 * @LastEditors: dengxumei
 * @LastEditTime: 2025-09-17 10:26:54
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\path\line.vue
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
    },
    // 多路径
    multiple: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      // 点的半径
      pointRadius: 5,
      // 存储所有点的数组
      points: [],
      canvas: null,
      pointGroups: [],
      ctx: null
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

      this.canvas.width = img.clientWidth;
      this.canvas.height = img.clientHeight;
      // luxiang-map
      this.canvas.parentElement.style.maxHeight = img.clientHeight+'px'
      // console.log('111canvas', img, this.id, this.className, img.clientWidth, img.clientHeight);
      // 窗口大小改变时调整画布大小
      window.addEventListener('resize', () => {
        // this.canvas.width = window.innerWidth;
        // this.canvas.height = window.innerHeight;
        this.canvas.width = this.canvas.parentElement.clientWidth;
        this.canvas.height = this.canvas.parentElement.clientHeight;
        
        this.redrawCanvas();
      });
      // 鼠标移动事件
      this.canvas.addEventListener('mousemove', evt =>  {
        // console.log('%mousemove', 'color: #f00')
        const mousePos = this.getMousePos(evt);
        this.checkPointHover(mousePos);
      });
      
      // 鼠标移出画布时重置高亮
      this.canvas.addEventListener('mouseout', () => {
        // console.log('%mouseout', 'color: #0f0')
        this.pointGroups.forEach(group => {
          group.highlighted = false;
        });
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
    redrawCanvas() {
      this.init()
      // 清空画布
      this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      
      if (this.multiple) {
        this.renderPointsGroup()
        return
      }
      // 绘制所有线段
      this.ctx.beginPath();
      this.ctx.strokeStyle = this.id === 'luxiang-drawingCanvas' ? '#4d995f' : '#0079fe';
      this.ctx.lineWidth = this.id === 'luxiang-drawingCanvas' ? 4 : 2;
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
    },
    renderPointsGroup() {
      const noHighlightedArr = this.pointGroups.filter(group => !group.highlighted)
      this.drawPointsGroup(noHighlightedArr)
      const highlightedArr = this.pointGroups.filter(group => group.highlighted)
      this.drawPointsGroup(highlightedArr)
    },
    drawPointsGroup(pointGroups) {
      pointGroups.forEach(group => {
        // console.log(123, group);
        const points = group.points
        const color = group.highlighted ? '#2575fc' : group.color;
        
        // 绘制连线
        this.ctx.beginPath();
        this.ctx.moveTo(points[0].x, points[0].y);
        
        for (let i = 1; i < points.length; i++) {
          this.ctx.lineTo(points[i].x, points[i].y);
        }
        
        this.ctx.strokeStyle = color;
        this.ctx.lineWidth = group.highlighted ? 6 : 2;
        this.ctx.stroke();
        
        // 绘制点
        for (let i = 0; i < points.length; i++) {
          const point = points[i];
          
          // 点外圈
          this.ctx.beginPath();
          this.ctx.arc(point.x, point.y, point.radius, 0, Math.PI * 2);
          // 起始点、终点、普通点
          this.ctx.fillStyle = i === 0 ? '#e74c3c': i === points.length - 1 ? '#00ac3a': '#0079fe';
          this.ctx.fill();
          
          // 点内白点
          this.ctx.beginPath();
          this.ctx.arc(point.x, point.y, point.radius / 2, 0, Math.PI * 2);
          // this.ctx.fillStyle = 'white';
          this.ctx.fill();
        }
      });
    },
    // 检测鼠标位置
    getMousePos(evt) {
      const rect = this.canvas.getBoundingClientRect();
      // console.log('鼠标======', rect.left, rect.top, evt.clientX, evt.clientY);
      const scaleX = this.canvas.width / rect.width
      const scaleY = this.canvas.height / rect.height
      return {
        x: (evt.clientX - rect.left) * scaleX,
        y: (evt.clientY - rect.top) * scaleY
      };
    },
    checkPointHover(mousePos) {
      
      let hoveredGroup = null;
      
      // 重置所有组的高亮状态
      this.pointGroups.forEach(group => {
        group.highlighted = false;
      });
      // console.log(document.getElementsByClassName('screen-wrapper'));
      
      // 检查每个点
      let scale = 1;
      let tS = null
      if (document.getElementsByClassName('screen-wrapper').length) {
        tS = document.getElementsByClassName('screen-wrapper')[0].style.transform
      } else {
        tS = document.getElementsByClassName('app-wrapper')[0].style.transform
      }
      if (tS) {
        scale = tS.split(',')[0].split('scale(')[1]
      }
      // console.log('scale===========缩放比例=======', scale)
      for (const group of this.pointGroups) {
        for (const point of group.points) {
          const dx = mousePos.x - point.x;
          const dy = mousePos.y - point.y;
          const distance = Math.sqrt(dx * dx + dy * dy);
          // const distance = Math.sqrt(dx * dx + dy * dy);
          // console.log('mousePos=====', mousePos, 'point.x', point.x, 'point.y', point.y, 'distance=====================', distance);
          
          if (distance < point.radius + 8) {
              group.highlighted = true;
              hoveredGroup = group;
              break;
          }
        }
        if (hoveredGroup) break;
      }
      this.redrawCanvas();
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