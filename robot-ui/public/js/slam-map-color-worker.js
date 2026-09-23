self.onmessage = async function(event) {
  const payload = event.data || {}
  const requestId = payload.requestId
  const blob = payload.blob

  try {
    if (!(blob instanceof Blob) || typeof createImageBitmap !== 'function' || typeof OffscreenCanvas !== 'function') {
      throw new Error('当前浏览器不支持离屏地图调色')
    }

    const bitmap = await createImageBitmap(blob)
    const width = bitmap.width
    const height = bitmap.height
    const canvas = new OffscreenCanvas(width, height)
    const context = canvas.getContext('2d', { willReadFrequently: true })
    context.drawImage(bitmap, 0, 0, width, height)
    bitmap.close()

    const imageData = context.getImageData(0, 0, width, height)
    const pixels = new Uint32Array(imageData.data.buffer)
    for (let index = 0; index < pixels.length; index += 1) {
      const pixel = pixels[index]
      const red = pixel & 0xff
      const green = (pixel >>> 8) & 0xff
      const blue = (pixel >>> 16) & 0xff
      const alpha = pixel & 0xff000000
      if (red > 230 && green > 230 && blue > 230) {
        pixels[index] = alpha | 0x00a37956
      } else if (red > 10 && green > 10 && blue > 10) {
        pixels[index] = alpha | 0x004d2b11
      } else {
        pixels[index] = alpha | 0x000d0a07
      }
    }

    self.postMessage({
      requestId: requestId,
      width: width,
      height: height,
      buffer: imageData.data.buffer
    }, [imageData.data.buffer])
  } catch (error) {
    self.postMessage({
      requestId: requestId,
      error: error && error.message ? error.message : '离屏地图调色失败'
    })
  }
}
