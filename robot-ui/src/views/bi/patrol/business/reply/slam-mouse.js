
export default {
  data() {
    return {
      // 拖拽相关
      offsetX: 0,
      offsetY: 0,
      isDragging: false,
      startX: 0,
      startY: 0,
      startOffsetX: 0,
      startOffsetY: 0,
      className: '.map-preview-point',
      viewportWidth: 247,
      viewportHeight: 169,
      showOperList: true,
      operList: [
        {
          icon: 'map-path',
          name: '路径',
          key: 'path',
          action: 'renderPath'
        },
        {
          icon: 'map-location',
          name: '定位',
          key: 'location',
          action: 'backCenter'
        },
        {
          icon: 'map-zoom-in',
          name: '放大',
          key: 'zoomIn',
          action: 'zoomIn'
        },
        {
          icon: 'map-zoom-out',
          name: '缩小',
          key: 'zoomOut',
          action: 'zoomOut'
        },
        {
          icon: 'address',
          name: '移动',
          key: 'move',
          action: 'startMove'
        },
      ],
      zoom: 1,
      imageUrl: '',
      previewImageStatus: '地图预览加载中',
      imageObjectUrl: '',
      imageLoadSeq: 0,
      hoveredPointId: null,
    }
  },
  computed: {
    // 地图宽度
    previewWidth() {
      return Number(this.map?.previewWidth || 0)
    },
    // 地图高度
    previewHeight() {
      return Number(this.map?.previewHeight || 0)
    },
    // 是否已加载地图
    hasPreview() {
      return !!this.map?.previewFileId &&
        !!this.previewWidth &&
        !!this.previewHeight &&
        this.map?.resolution !== undefined &&
        this.map?.originX !== undefined &&
        this.map?.originY !== undefined &&
        this.map?.originYaw !== undefined
    },
    // 地图stage样式
    stageStyle() {
      return {
        width: `${this.previewWidth * this.zoom}px`,
        height: `${this.previewHeight * this.zoom}px`,
        transform: `translate(${this.offsetX}px, ${this.offsetY}px)`
      }
    },
    pathPointIds() {
      return this.detailPointId()
    },
    drawablePoints() {
      return this.points
        .map((point) => ({ ...point, pixel: this.mapPointToPixel(point, this.map) }))
        .filter((point) => point.pixel)
    },
    // map规划的路径
    polylinePoints() {
        return this.pathPointIds
        .map((id) => this.drawablePoints.find((point) => point.id === id)?.pixel)
        .filter(Boolean)
        .map((pixel) => `${pixel.x},${pixel.y}`)
        .join(" ")
    },
    replyPolylinePoints() {
      return this.movePoints.join(" ")
    },
    // 返回需要监听的对象，依赖 map 和 hasPreview
    previewSource() {
      const id = this.map && this.map.id;
      const cacheKey = (this.map && (this.map.previewGeneratedAt || this.map.previewFileId));
      return {
        id,
        cacheKey,
        hasPreview: this.hasPreview,
      };
    },
  },
  methods: {
    detailPointId() {
      const record = this.pathInfo
      return [...(record?.points || [])].sort((a, b) => a.pointOrder - b.pointOrder).map((item) => item.mapPointId)
    },
    handleClickTool(item) {
      this[item.action]();
    },
    // 点转相对像素
    mapPointToPixel(point, map) {
      if (!this.hasPreview || !map) return null;
      const width = Number(map.previewWidth);
      const height = Number(map.previewHeight);
      const resolution = Number(map.resolution);
      const originX = Number(map.originX);
      const originY = Number(map.originY);
      const originYaw = Number(map.originYaw);
      if (!width || !height || !resolution) return null;
      const dx = Number(point.coordinateX) - originX;
      const dy = Number(point.coordinateY) - originY;
      const cos = Math.cos(originYaw);
      const sin = Math.sin(originYaw);
      const localX = dx * cos + dy * sin;
      const localY = -dx * sin + dy * cos;
      return { x: localX / resolution, y: height - localY / resolution };
    },
    // 相对像素转点
    pixelToMapPoint(pixel, map) {
      if (!this.hasPreview || !map) return null;
      const width = Number(map.previewWidth);
      const height = Number(map.previewHeight);
      const resolution = Number(map.resolution);
      const originX = Number(map.originX);
      const originY = Number(map.originY);
      const originYaw = Number(map.originYaw);
      if (!width || !height || !resolution) return null;
      const cos = Math.cos(originYaw);
      const sin = Math.sin(originYaw);
      const localX = pixel.x * resolution;
      const localY = (height - pixel.y) * resolution;
      const dx = localX * cos - localY * sin;
      const dy = localX * sin + localY * cos;
      return {
        coordinateX: Number((originX + dx).toFixed(3)),
        coordinateY: Number((originY + dy).toFixed(3)),
        coordinateZ: 0,
        pixelX: Number(pixel.x.toFixed(1)),
        pixelY: Number(pixel.y.toFixed(1))
      };
    },
    renderPath() {
      this.showPath = !this.showPath;
    },
    revokeImageUrl() {
      if (this.imageObjectUrl) {
        URL.revokeObjectURL(this.imageObjectUrl);
        this.imageObjectUrl = null;
      }
      this.imageUrl = "";
    },

    // 拖拽地图
    handleMouseDown(e) {
      if (e.target.closest(this.className)) return
      this.isDragging = true
      this.startX = e.clientX
      this.startY = e.clientY
      this.startOffsetX = this.offsetX
      this.startOffsetY = this.offsetY
      document.addEventListener('mousemove', this.handleMouseMove)
      document.addEventListener('mouseup', this.handleMouseUp)
    },
    handleMouseMove(e) {
      if (!this.isDragging) return
      const stageWidth = this.previewWidth;
      const stageHeight = this.previewHeight;
      
      // 计算当前偏移
      let newOffsetX = this.startOffsetX + e.clientX - this.startX
      let newOffsetY = this.startOffsetY + e.clientY - this.startY
      
      // 边界限制：确保stage始终完全覆盖viewport，边界距离不为正数
      const diffX = stageWidth - this.viewportWidth;
      const diffY = stageHeight - this.viewportHeight;
      
      if (diffX > 0) {
        // stage比viewport宽，可拖动范围: [-(diffX), 0]
        // 向左拖动可以看到右侧内容，确保left<=0, right<=0
        const maxOffsetX = diffX;
        newOffsetX = Math.max(-maxOffsetX, Math.min(0, newOffsetX));
      } else {
        // stage小于等于viewport宽，保持居中
        newOffsetX = 0;
      }
      
      if (diffY > 0) {
        // stage比viewport高，可拖动范围: [-(diffY), 0]
        // 向上拖动可以看到下侧内容，确保top<=0, bottom<=0
        const maxOffsetY = diffY;
        newOffsetY = Math.max(-maxOffsetY, Math.min(0, newOffsetY));
      } else {
        // stage小于等于viewport高，保持居中
        newOffsetY = 0;
      }
      this.offsetX = newOffsetX
      this.offsetY = newOffsetY
    },
    handleMouseUp() {
      this.isDragging = false
      document.removeEventListener('mousemove', this.handleMouseMove)
      document.removeEventListener('mouseup', this.handleMouseUp)
    },
    handleWheel(e) {
      e.preventDefault()
      const viewport = this.$el.querySelector(this.className)
      if (!viewport) return
      
      const rect = viewport.getBoundingClientRect()
      const mouseX = e.clientX - rect.left
      const mouseY = e.clientY - rect.top
      
      // 计算鼠标相对于stage中心的位置
      const stageWidth = this.previewWidth;
      const stageHeight = this.previewHeight;
      
      // 计算鼠标在stage上的原始位置（考虑当前偏移）
      const stageMouseX = mouseX - this.offsetX
      const stageMouseY = mouseY - this.offsetY
      
      // 计算缩放因子
      const delta = e.deltaY > 0 ? -0.1 : 0.1
      const newZoom = Math.max(this.minZoom(), Math.min(3, this.zoom + delta))
      
      // 计算新的偏移，使鼠标位置保持在原地
      const scale = newZoom / this.zoom
      this.offsetX = mouseX - stageMouseX * scale
      this.offsetY = mouseY - stageMouseY * scale
      
      this.zoom = Number(newZoom.toFixed(2))
      // 缩放后检查边界，确保stage始终覆盖viewport
      const newStageWidth = this.previewWidth;
      const newStageHeight = this.previewHeight;
      const diffX = newStageWidth - this.viewportWidth;
      const diffY = newStageHeight - this.viewportHeight;
      
      if (diffX > 0) {
        const maxOffsetX = diffX;
        this.offsetX = Math.max(-maxOffsetX, Math.min(0, this.offsetX));
      } else {
        this.offsetX = 0;
      }
      
      if (diffY > 0) {
        const maxOffsetY = diffY;
        this.offsetY = Math.max(-maxOffsetY, Math.min(0, this.offsetY));
      } else {
        this.offsetY = 0;
      }
    },

    // 缩放地图
    
    // 最小缩放比例：确保stage不会小于viewport的宽高
    minZoom() {
      const stageWidth = this.previewWidth;
      const stageHeight = this.previewHeight;
      
      if (!stageWidth || !stageHeight) return 0.5;
      
      // 计算最小缩放比例，确保stage宽高不小于viewport
      const minZoomByWidth = this.viewportWidth / stageWidth;
      const minZoomByHeight = this.viewportHeight / stageHeight;
      
      return Math.max(minZoomByWidth, minZoomByHeight, 0.5);
    },
    zoomIn() {
      this.zoom = Math.min(3, Number((this.zoom + 0.25).toFixed(2)));
    },
    zoomOut() {
      this.zoom = Math.max(this.minZoom(), Number((this.zoom - 0.25).toFixed(2)));
      
      // 缩小后检查边界，确保top, left, right, bottom >= 0
      const stageWidth = this.previewWidth * this.zoom;
      const stageHeight = this.previewHeight * this.zoom;
      const diffX = stageWidth - this.viewportWidth;
      const diffY = stageHeight - this.viewportHeight;
      
      if (diffX > 0) {
        const maxOffsetX = diffX / 2;
        this.offsetX = Math.max(-maxOffsetX, Math.min(0, this.offsetX));
      } else {
        this.offsetX = 0;
      }
      
      if (diffY > 0) {
        const maxOffsetY = diffY / 2;
        this.offsetY = Math.max(-maxOffsetY, Math.min(0, this.offsetY));
      } else {
        this.offsetY = 0;
      }
    },
    resetView() {
      this.zoom = 1
      this.offsetX = 0
      this.offsetY = 0
    },
    
    backCenter() {
      // 获取第4个点（索引为3）
      const point4 = this.drawablePoints[3];
      if (!point4 || !point4.pixel) return;
      const stageWidth = this.previewWidth * this.zoom;
      const stageHeight = this.previewHeight * this.zoom;
      
      // 计算该点在stage上的位置（已缩放）
      const pointX = point4.pixel.x * this.zoom;
      const pointY = point4.pixel.y * this.zoom;
      
      // 计算使该点居中所需的偏移
      // viewport中心位置
      const viewportCenterX = viewportWidth / 2;
      const viewportCenterY = viewportHeight / 2;
      
      // 目标偏移 = viewport中心 - 点在stage上的位置
      let targetOffsetX = viewportCenterX - pointX;
      let targetOffsetY = viewportCenterY - pointY;
      
      // 应用边界限制：确保top, left, right, bottom >= 0
      const diffX = stageWidth - viewportWidth;
      const diffY = stageHeight - viewportHeight;
      
      if (diffX > 0) {
        const maxOffsetX = diffX / 2;
        targetOffsetX = Math.max(-maxOffsetX, Math.min(0, targetOffsetX));
      } else {
        targetOffsetX = 0;
      }
      
      if (diffY > 0) {
        const maxOffsetY = diffY / 2;
        targetOffsetY = Math.max(-maxOffsetY, Math.min(0, targetOffsetY));
      } else {
        targetOffsetY = 0;
      }
      
      this.offsetX = targetOffsetX;
      this.offsetY = targetOffsetY;
    },
  },
  watch: {
    previewSource: {
      immediate: true,
      async handler(newVal) {
        this.imageObjectUrl = require('../../slam/preview-image.png');
        this.imageUrl = require('../../slam/preview-image.png');
        
        // const { id, cacheKey, hasPreview } = newVal;
        // const seq = ++this.imageLoadSeq;
        // this.revokeImageUrl();
        // this.previewImageStatus = '地图预览加载中';
        // if (!hasPreview || !id) return;
        // try {
        //   const blob = await mapApi.previewImageBlob(id, cacheKey);
        //   const nextUrl = URL.createObjectURL(blob);
        //   if (seq !== this.imageLoadSeq) {
        //     URL.revokeObjectURL(nextUrl);
        //     return;
        //   }
        //   this.imageObjectUrl = nextUrl;
        //   this.imageUrl = nextUrl;
        // } catch (error) {
        //   if (seq === this.imageLoadSeq) {
        //     this.previewImageStatus = '地图预览图片加载失败，请检查接口授权或后端服务';
        //   }
        // }
      },
    },
  },
  beforeDestroy() {
    this.imageLoadSeq += 1
    this.revokeImageUrl();
  }
}