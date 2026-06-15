import { Message } from "element-ui";

export function parsePGM(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (event) => {
      console.log(3);
      
      try {
        const buffer = event.target.result;
        const data = new Uint8Array(buffer);
        
        // 解析PGM文件头
        const header = parsePGMHeader(data);
        if (!header) throw new Error('Invalid PGM header');
        
        // 创建图像数据
        const imageData = createImageData(header, data);
        
        // 生成预览URL
        const url = generatePreview(imageData, header);
        resolve(url)
        
        // 显示图像信息
        // this.imageInfo = `${header.width}×${header.height} 灰度${header.maxval}`;
        
      } catch (error) {
        Message.error('PGM文件解析失败: ' + error.message);
        reject()
      }
    };
    reader.readAsArrayBuffer(file);
  })
}

// 解析PGM文件头
function parsePGMHeader(data) {
  // 将二进制数据转为文本解析头信息
  const headerText = new TextDecoder().decode(data.slice(0, 100));
  const lines = headerText.split('\n').filter(line => !line.startsWith('#') && line.trim() !== '');
  
  if (lines.length < 3) return null;
  
  const magic = lines[0].trim();
  if (magic !== 'P5') throw new Error('仅支持P5格式PGM');
  
  const [width, height] = lines[1].trim().split(/\s+/).map(Number);
  const maxval = parseInt(lines[2]);
  
  // 计算像素数据起始位置
  const headerLength = lines.slice(0, 3).join('\n').length + 1;
  
  return {
    width,
    height,
    maxval,
    dataOffset: headerLength
  };
}

// 创建ImageData对象
function createImageData(header, data) {
  const { width, height, maxval, dataOffset } = header;
  const imageData = new ImageData(width, height);
  const pixels = imageData.data;
  
  // 获取像素数据
  const pixelData = data.slice(dataOffset, dataOffset + width * height);
  
  // 转换灰度到RGBA
  for (let i = 0, j = 0; i < pixelData.length; i++, j += 4) {
    const value = pixelData[i];
    pixels[j] = value;       // R
    pixels[j + 1] = value;   // G
    pixels[j + 2] = value;   // B
    pixels[j + 3] = 255;     // Alpha
  }
  
  return imageData;
}

// 生成预览URL
function generatePreview(imageData, header) {
  const canvas = document.createElement('canvas');
  canvas.width = header.width;
  canvas.height = header.height;
  
  const ctx = canvas.getContext('2d');
  ctx.putImageData(imageData, 0, 0);
  
  // 转为DataURL用于预览
  const previewUrl = canvas.toDataURL('image/png');
  return previewUrl
}