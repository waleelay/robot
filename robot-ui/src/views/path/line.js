export function line(width, height) {
  const canvas = document.getElementById('drawingCanvas');
  const img = document.getElementsByClassName('scaled-image')[0]
  const ctx = canvas.getContext('2d');
  
  // 设置画布大小为图片在页面中显示的像素大小
  // canvas.width = width;
  // canvas.height = height;
  canvas.width = img.clientWidth;
  canvas.height = img.clientHeight;

  console.log('canvas', img.clientWidth, img.clientHeight);
  

  // 存储所有点的数组
  const points = [];
  
  // 点的半径
  const pointRadius = 2;
  
  // 监听鼠标点击事件
  canvas.addEventListener('click', function(event) {
    // 相对于视口位置
    const rect = canvas.getBoundingClientRect()
    // 获取鼠标位置
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    
    // 将点添加到数组
    // console.log('点========', x, y);
    
    points.push({ x, y });
    
    // 重绘画布
    redrawCanvas();
  });

  // 重绘画布函数
  function redrawCanvas() {
      // 清空画布
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      // 绘制所有线段
      ctx.beginPath();
      ctx.strokeStyle = '#3498db';
      ctx.lineWidth = 2;
      
      if (points.length > 1) {
          ctx.moveTo(points[0].x, points[0].y);
          for (let i = 1; i < points.length; i++) {
              ctx.lineTo(points[i].x, points[i].y);
          }
          ctx.stroke();
      }
      
      // 绘制所有点
      ctx.fillStyle = '#e74c3c';
      for (const point of points) {
          ctx.beginPath();
          ctx.arc(point.x, point.y, pointRadius, 0, Math.PI * 2);
          ctx.fill();
      }
  }
  
  // 窗口大小改变时调整画布大小
  window.addEventListener('resize', function() {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      redrawCanvas();
  });
}
