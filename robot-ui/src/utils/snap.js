// WebRTC 流限制	WebRTC 视频流可能无法直接通过 drawImage 捕获
// FLV 流问题	FLV 播放器可能使用了 WebGL 渲染，canvas 无法直接捕获
// 跨域限制	视频源可能存在 CORS 限制
// 元素获取失败	动态生成的 video 元素可能获取不到

export const captureMethods = ['canvas', 'webgl', 'dom'];
// let captureMethod = 'auto';
// 尝试多种快照方式
export const tryMultipleCaptureMethods =  async (container) => {  
  for (const method of captureMethods) {
    try {
      const canvas = await captureByMethod(container, method);
      if (canvas) {
        // captureMethod = method;  // 记录成功的方式
        return canvas;
      }
    } catch (e) {
      console.warn(`方式 ${method} 失败:`, e);
    }
  }
  
  return null;
}
// 根据方式进行快照
export const captureByMethod = async (container, method) => {
  switch (method) {
    case 'canvas':
      return captureByCanvas(container);
    case 'webgl':
      return captureByWebGL(container);
    case 'dom':
      return captureByDOM(container);
    default:
      return captureByCanvas(container);
  }
}
// 方式1：Canvas 直接捕获
function captureByCanvas(container) {
  return new Promise((resolve, reject) => {
    const video = container.querySelector('video');
    if (!video) {
      reject(new Error('未找到 video 元素'));
      return;
    }
    
    // 检查视频状态
    if (video.readyState < 2) {
      reject(new Error('视频尚未加载'));
      return;
    }
    
    try {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth || video.clientWidth || 1280;
      canvas.height = video.videoHeight || video.clientHeight || 720;
      
      const ctx = canvas.getContext('2d');
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
      
      resolve(canvas);
    } catch (e) {
      reject(new Error(`Canvas 捕获失败: ${e.message}`));
    }
  });
}
// 方式2：WebGL 纹理读取（适用于 WebRTC 流）
function captureByWebGL(container) {
  return new Promise((resolve, reject) => {
    const video = container.querySelector('video');
    if (!video) {
      reject(new Error('未找到 video 元素'));
      return;
    }
    
    try {
      // 创建离屏 canvas
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth || video.clientWidth || 1280;
      canvas.height = video.videoHeight || video.clientHeight || 720;
      
      // 使用 WebGL 上下文
      const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
      if (!gl) {
        reject(new Error('WebGL 不可用'));
        return;
      }
      
      // 创建着色器程序
      const program = createWebGLProgram(gl);
      if (!program) {
        reject(new Error('创建 WebGL 程序失败'));
        return;
      }
      
      // 创建纹理
      const texture = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      
      // 绑定视频到纹理
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
      
      // 设置帧缓冲区
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      
      resolve(canvas);
    } catch (e) {
      reject(new Error(`WebGL 捕获失败: ${e.message}`));
    }
  });
}
// 创建 WebGL 程序
function createWebGLProgram(gl) {
  try {
    const vertexShader = createShader(gl, gl.VERTEX_SHADER, `
      attribute vec2 position;
      varying vec2 texCoord;
      void main() {
        texCoord = position * 0.5 + 0.5;
        gl_Position = vec4(position, 0.0, 1.0);
      }
    `);
    
    const fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, `
      precision mediump float;
      uniform sampler2D texture;
      varying vec2 texCoord;
      void main() {
        gl_FragColor = texture2D(texture, texCoord);
      }
    `);
    
    const program = gl.createProgram();
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      console.error('WebGL 程序链接失败:', gl.getProgramInfoLog(program));
      return null;
    }
    
    gl.useProgram(program);
    
    // 设置顶点数据
    const positionBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    
    const positionLocation = gl.getAttribLocation(program, 'position');
    gl.enableVertexAttribArray(positionLocation);
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);
    
    return program;
  } catch (e) {
    console.error('创建 WebGL 程序失败:', e);
    return null;
  }
}
// 创建着色器
function createShader(gl, type, source) {
  const shader = gl.createShader(type);
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.error('着色器编译失败:', gl.getShaderInfoLog(shader));
    return null;
  }
  
  return shader;
}
// 方式3：DOM 快照（使用 html2canvas）
const captureByDOM = async(container) => {
  return new Promise((resolve, reject) => {
    // 检查是否已加载 html2canvas
    if (typeof html2canvas === 'undefined') {
      // 动态加载 html2canvas
      const script = document.createElement('script');
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js';
      script.onload = () => {
        executeDOMCapture(container, resolve, reject);
      };
      script.onerror = () => {
        reject(new Error('无法加载 html2canvas'));
      };
      document.head.appendChild(script);
    } else {
      executeDOMCapture(container, resolve, reject);
    }
  });
}  
// 执行 DOM 快照
function executeDOMCapture(container, resolve, reject) {
  html2canvas(container, {
    useCORS: true,
    allowTaint: true,
    logging: false
  }).then(canvas => {
    resolve(canvas);
  }).catch(err => {
    reject(new Error(`DOM 快照失败: ${err.message}`));
  });
}
// 添加水印
export function drawWatermark(canvas) {
  const ctx = canvas.getContext('2d');
  
  // 绘制半透明背景
  ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
  ctx.fillRect(10, canvas.height - 60, canvas.width - 20, 50);
  
  // 绘制文字
  ctx.fillStyle = '#fff';
  ctx.font = '14px Microsoft YaHei';
  // ctx.fillText(`告警类型: ${this.types[this.activeIndex].label}`, 20, canvas.height - 35);
  // ctx.fillText(`时间: ${this.getCurrentTime()}`, 20, canvas.height - 15);
  
  // if (this.alarmDesc) {
  //   ctx.fillStyle = 'rgba(0, 0, 0, 0.5)';
  //   ctx.fillRect(10, canvas.height - 100, canvas.width - 20, 30);
  //   ctx.fillStyle = '#fff';
  //   ctx.fillText(`描述: `, 20, canvas.height - 80);
  // }
}
function getCurrentTime() {
  const now = new Date();
  return now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
}