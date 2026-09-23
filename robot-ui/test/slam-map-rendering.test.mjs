import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const source = readFileSync(new URL(
  '../src/views/bi/gis/globalMap/slam/add-point-task.js', import.meta.url
), 'utf8')
const colorWorkerSource = readFileSync(new URL(
  '../public/js/slam-map-color-worker.js', import.meta.url
), 'utf8')

let latestImage
class FakeImage {
  constructor() {
    latestImage = this
    this.width = 1631
    this.height = 2040
  }

  set src(value) {
    this._src = value
  }
}

const exports = {}
const fakeWindow = { devicePixelRatio: 2 }
vm.runInNewContext(require('@babel/core').transformSync(source, {
  babelrc: false,
  configFile: false,
  plugins: ['@babel/plugin-transform-modules-commonjs']
}).code, {
  exports,
  Image: FakeImage,
  window: fakeWindow,
  require: name => name === 'vuex' ? require('vuex') : {}
})
const mixin = exports.default

test('离屏 Worker 保持原调色规则并保留透明度', async () => {
  let workerResult
  const sourcePixels = new Uint8ClampedArray([
    255, 255, 255, 255,
    20, 20, 20, 255,
    0, 0, 0, 128
  ])
  const workerSelf = {
    postMessage(result) { workerResult = result }
  }
  class FakeOffscreenCanvas {
    getContext() {
      return {
        drawImage() {},
        getImageData() { return { data: sourcePixels } }
      }
    }
  }
  vm.runInNewContext(colorWorkerSource, {
    self: workerSelf,
    Blob,
    Uint32Array,
    OffscreenCanvas: FakeOffscreenCanvas,
    createImageBitmap: async () => ({ width: 3, height: 1, close() {} })
  })

  await workerSelf.onmessage({ data: { requestId: 7, blob: new Blob(['map']) } })

  assert.equal(workerResult.requestId, 7)
  assert.deepEqual(Array.from(new Uint8ClampedArray(workerResult.buffer)), [
    86, 121, 163, 255,
    17, 43, 77, 255,
    7, 10, 13, 128
  ])
})

test('缩小展示时 Canvas 位图按 zoom 和 DPR 分配', () => {
  const canvas = { width: 1, height: 1 }
  const ctx = {
    canvas,
    ctx: {
      setTransform(scaleX, _skewX, _skewY, scaleY) {
        ctx.scaleX = scaleX
        ctx.scaleY = scaleY
      },
      imageSmoothingEnabled: false
    },
    W: 1631,
    H: 2040,
    zoom: 0.25,
    getCanvasRenderScale: mixin.methods.getCanvasRenderScale,
    ensureColoredMap() {},
    draw() {}
  }

  fakeWindow.devicePixelRatio = 2
  mixin.methods.syncCanvasResolution.call(ctx)

  // 1631 × 2040 在 zoom=0.25、DPR=2 时只需 0.5 倍位图，而不是旧逻辑的 2 倍位图。
  assert.equal(canvas.width, 816)
  assert.equal(canvas.height, 1020)
  assert.equal(ctx.scaleX, 0.5)
  assert.equal(ctx.scaleY, 0.5)
})

test('缩小时各重绘分支与 Canvas 位图使用相同缩放系数', () => {
  const transforms = []
  const canvas = { width: 816, height: 1020 }
  const drawingContext = {
    save() {},
    restore() {},
    clearRect() {},
    drawImage() {},
    setTransform(scaleX, _skewX, _skewY, scaleY) {
      transforms.push([scaleX, scaleY])
    },
    setLineDash() {}
  }
  const component = {
    canvas,
    ctx: drawingContext,
    img: {},
    W: 1631,
    H: 2040,
    zoom: 0.25,
    loadedPath: [],
    unloadedPath: [],
    getCanvasRenderScale: mixin.methods.getCanvasRenderScale,
    ensureColoredMap() {},
    getMapBaseImage() { return this.img },
    getScreenLineWidth: mixin.methods.getScreenLineWidth
  }

  fakeWindow.devicePixelRatio = 2
  for (const methodName of ['draw', 'drawLine', 'drawStraightLine']) {
    transforms.length = 0
    mixin.methods[methodName].call(component, [])
    assert.deepEqual(transforms.at(-1), [0.5, 0.5], `${methodName} 使用了不同的绘制倍率`)
  }
})

test('地图调色完成前保持加载态，完成后一次性绘制蓝色成品', async () => {
  let gridBuilds = 0
  let canvasDraws = 0
  let finishColoring
  const component = {
    imageLoadSeq: 0,
    imageUrl: 'blob:map-preview',
    mapLoading: false,
    previewImageStatus: '',
    enableSafetyAreaCheck: false,
    grid: [[1]],
    $refs: { canvas: { getContext: () => ({}) } },
    buildGrid() { gridBuilds++ },
    terminateMapColorWorker() {},
    prepareColoredMap() {
      return new Promise(resolve => { finishColoring = resolve })
    },
    syncCanvasResolution() { canvasDraws++ },
    mapLoadFailed: false
  }

  mixin.methods.loadMap.call(component)
  latestImage.onload()

  assert.equal(gridBuilds, 0)
  assert.equal(component.grid, null)
  assert.equal(component.W, 1631)
  assert.equal(component.H, 2040)
  assert.equal(component.mapLoading, true)
  assert.equal(canvasDraws, 0)

  finishColoring(true)
  await Promise.resolve()

  assert.equal(component.mapLoading, false)
  assert.equal(canvasDraws, 1)
})

test('开启安全区域校验时仍构建障碍网格', () => {
  let gridBuilds = 0
  const component = {
    imageLoadSeq: 0,
    imageUrl: 'blob:map-preview',
    mapLoading: false,
    previewImageStatus: '',
    enableSafetyAreaCheck: true,
    grid: null,
    $refs: { canvas: { getContext: () => ({}) } },
    buildGrid() { gridBuilds++ },
    terminateMapColorWorker() {},
    prepareColoredMap() { return Promise.resolve(true) },
    syncCanvasResolution() {},
    mapLoadFailed: false
  }

  mixin.methods.loadMap.call(component)
  latestImage.onload()

  assert.equal(gridBuilds, 1)
})
