<template>
  <div class="map-operation">
    <div v-if="openSearch" class="custom-search-div wp220">
      <el-input
        placeholder="请输入装备名称、类型"
        v-model="searchValue"
        clearable
        @keyup.enter.native="search()"
        @clear="search()"
      >
        <svg-icon slot="prefix" icon-class="search"></svg-icon>
      </el-input>
    </div>
    <div class="operation">
      <div v-for="(item, index) in operList1" :key="item.key" @click="handleClickTool(item)" class="operation-item flx-center flex-column" :class="{ 'is-active': selectedOper1 === item.key }">
        <div class="flx-center flex-column">
          <svg-icon :icon-class="item.icon" />
          <span class="mt4">{{ item.name }}</span>
        </div>
      </div>
    </div>
    <div class="operation">
      <div
        v-for="(item, index) in displayOperList2"
        :key="item.key"
        :title="item.title || item.name"
        @click="handleClickTool(item)"
        class="operation-item flx-center flex-column"
        :class="{
          'is-active': item.key === 'path'
            ? pathActive
            : item.key === 'point'
              ? pointActive
              : item.key === 'scale'
                ? rangingActive
                : selectedOper2 === item.key,
          'is-disabled': isOperDisabled(item)
        }"
      >
        <template v-if="index === 10">
          <el-popover placement="left" trigger="hover" popper-class="custom-popover map-layer-popover">
            <template slot="reference">
              <svg-icon :icon-class="item.key === 'angle' ? `${item.icon}-${angle === '3D' ? '2D' : '3D'}` : item.icon" />
              <span>{{ item.name }}</span>
            </template>
            <el-radio-group v-model="tabIndex" class="custom-radio-group flex with-border vertical">
              <el-radio v-for="item in tabList" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
            </el-radio-group>
          </el-popover>
        </template>
        <template v-else>
          <svg-icon :icon-class="item.key === 'angle' ? `${item.icon}-${angle === '3D' ? '2D' : '3D'}` : item.icon" />
          <span class="mt4">{{ item.name }}</span>
        </template>
      </div>
    </div>

    <div class="mt22 view">
      <transition name="map-view-tip-fade">
        <div
          v-if="slamEmptyTipVisible"
          class="map-view-tip"
          role="status"
        >
          <span class="map-view-tip__text">{{ slamEmptyTipText }}</span>
          <i class="map-view-tip__arrow" aria-hidden="true"></i>
        </div>
      </transition>
      <div ref="viewChangeRef" class="view-change flx-center wp50 hp50" @click.stop="toggleViewContainer">
        <img src="../../../../../assets/images/new-bi/view.png" width="44px" height="44px" style="border-radius: 50%;" />
      </div>
      <div
        ref="viewContainerRef"
        v-show="showViewContainer"
        class="view-container bi-corner-box p20 wp274 flex-column"
        @click.stop
        :class="{
          'hp332': slamList.length && selectType === 'slam',
          'is-gis-tip': selectType !== 'slam'
        }"
      >
        <div class="title">地图选择</div>
        <div class="d-flex mt8">
          <div class="img-view wp112 hp63" :class="{ 'is-active': currentType === 'gis' && selectType !== 'slam' }" @click="selectMapType('gis')">
            <img src="../../../../../assets/images/new-bi/gis-view.png" alt="" srcset="" class="w100 h100">
            <span class="type-name">GIS地图</span>
            <span class="count p2">({{ gisCountText }})</span>
          </div>
          <div class="img-view wp112 hp63 ml10" :class="{ 'is-active': currentType === 'slam' || selectType === 'slam' }" @click="selectMapType('slam')">
            <img src="../../../../../assets/images/new-bi/slam-view.png" alt="" srcset="" class="w100 h100">
            <span class="type-name">SLAM地图</span>
          </div>
        </div>
        <div v-if="selectType === 'slam'" class="slam-list mt20 pb6 flex1 common-scroll">
          <div v-for="item in slamList" :key="item.id" class="item flx-justify-between" :class="{ 'is-active': String(currentSlam) === String(item.id) }" @click="selectSlamMap(item)">
            <span class="text-ellipsis" :title="item.mapName">{{ item.mapName }}</span>
            <div class="flx-align-center">
              <span class="ml10">{{ getSlamRobotCount(item) }}台</span>
              <!-- <svg-icon v-if="String(currentSlam) === String(item.id)" icon-class="check" class="ml10" style="font-size: 16px;"></svg-icon> -->
            </div>
          </div>
        </div>
        <div v-else class="desc mt16 pt10 pb10">
          <div class="text red pl8 text-ellipsis">
            GIS地图仅展示支持GPS定位的设备
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions, mapState } from 'vuex'
import { getGisZoomRange, isMapToolSpecialPoint, isPointToolRequireCharge } from '../../../js/constants/gisMapPoints.js'

export default {
  name: 'MapTool',
  props: {
    angle: {
      type: String,
      default: '2D'
    },
    showAngle: {
      type: Boolean,
      default: false
    },
    isSlam: {
      type: Boolean,
      default: false
    },
    currentSlam: {
      type: [String, Number],
      default: ''
    },
    // GIS 当前缩放层级（由 GlobalGisMap zoom-change 同步）
    currentGisZoom: {
      type: Number,
      default: null
    }
  },
  data() {
    return {
      selectedOper1: 'layer',
      selectedOper2: 'outer',
      operList1: [
        {
          icon: 'map-search',
          name: '搜索',
          key: 'search',
          action: 'handleSearch'
        }
      ],
      operList2: [
        {
          icon: 'map-path',
          name: '路径',
          key: 'path',
          action: 'showPath',
          title: '任务路径'
        },
        {
          icon: 'map-point3',
          name: '点位',
          key: 'point',
          action: 'showPoint',
          title: '地图点位'
        },
        // {
        //   icon: 'map-angle',
        //   name: '视角',
        //   key: 'angle',
        //   action: 'changeAngle'
        // },
        {
          icon: 'map-scale',
          name: '测距',
          key: 'scale',
          action: 'ranging',
          title: '测距：单击加点，双击结束，Esc清除/退出'
        },
        {
          icon: 'map-location',
          name: '复位',
          key: 'location',
          action: 'backCenter',
          title: '复位到默认视图'
        },
        // {
        //   icon: 'map-scale',
        //   name: '皮肤',
        //   key: 'skin',
        //   action: 'changeSkin'
        // },
        {
          icon: 'map-zoom-in',
          name: '放大',
          key: 'zoomIn',
          action: 'changeZoom'
        },
        {
          icon: 'map-zoom-out',
          name: '缩小',
          key: 'zoomOut',
          action: 'changeZoom'
        },
      ],
      tabIndex: 0,
      tabList: [
        {
          label: '机器人图层',
          value: 0
        },
        {
          label: '任务路径图层',
          value: 1
        },
      ],
      openSearch: false,
      searchValue: '',
      currentType: this.isSlam ? 'slam' : 'gis',
      selectType: this.isSlam ? 'slam' : 'gis',
      showViewContainer: false,
      pathActive: false,
      pointActive: false,
      rangingActive: false,
      // 默认 SLAM 无预览时，地图选择按钮旁气泡提示（展示态本地控制；是否已提示走 vuex）
      slamEmptyTipVisible: false,
      slamEmptyTipText: '点击此处可切换地图',
      slamEmptyTipTimer: null,
    }
  },
  computed: {
    ...mapState('websocketExtraData', [
      'slamMapList',
      'slamOfRobot',
      'deviceStats',
      'taskPathPoints',
      'robotLocation',
      'defaultGpsDevices',
      'globalMapId',
      'overviewReady',
      'defaultMapIsSlam',
      'slamEmptyTipShown'
    ]),
    slamList() {
      return Array.isArray(this.slamMapList) ? this.slamMapList : []
    },
    currentSlamMapInfo() {
      const id = this.currentSlam
      if (id === undefined || id === null || id === '') return null
      return this.slamOfRobot?.[String(id)]?.mapInfo
        || this.slamList.find(item => String(item.id) === String(id))
        || null
    },
    // GIS / SLAM 均显示「路径」；「点位」仅 SLAM；视角仅 GIS
    displayOperList2() {
      return this.operList2.filter(item => {
        if (item.key === 'angle' && !this.showAngle) return false
        if (item.key === 'point') return this.isSlam
        return true
      })
    },
    // SLAM 下「点位」是否可操作：由 map-config.disablePointWithoutCharge 控制
    pathOperable() {
      if (!this.isSlam) return false
      const slamId = this.currentSlam
      if (slamId === undefined || slamId === null || slamId === '') return false
      const points = this.slamOfRobot?.[String(slamId)]?.mapInfo?.points
        || this.currentSlamMapInfo?.points
      if (!Array.isArray(points) || !points.length) return false
      if (isPointToolRequireCharge()) return points.some(isMapToolSpecialPoint)
      return true
    },
    // SLAM 下「路径」是否可操作：当前地图是否有关联任务路径
    taskPathOperable() {
      if (!this.isSlam) return true
      const slamId = this.currentSlam
      if (slamId === undefined || slamId === null || slamId === '') return false
      const paths = this.taskPathPoints || {}
      return Object.keys(paths).some(taskId => {
        const data = paths[taskId]
        return data && String(data.mapId) === String(slamId) && Array.isArray(data.pathPoints) && data.pathPoints.length > 0
      })
    },
    // 具备 GPS 经纬度的装备数量（overview.gpsDevices 或实时 location）
    gpsEquipmentCount() {
      const fromOverview = Array.isArray(this.defaultGpsDevices) ? this.defaultGpsDevices.length : 0
      if (fromOverview > 0) return fromOverview
      const locations = this.robotLocation || {}
      return Object.values(locations).filter(location => {
        const lat = location?.lat
        const lng = location?.lng
        return lat != null && lat !== '' && lng != null && lng !== ''
      }).length
    },
    // 有gps定位功能的装备
    gisCountText() {
      return `${this.gpsEquipmentCount}/${this.deviceStats?.total || 0}`
    },
    // GIS 缩放上下限来自 map-config zoom: [min, max]
    gisZoomRange() {
      return getGisZoomRange()
    },
    // 已到最大层级：禁用放大
    zoomInDisabled() {
      if (this.isSlam || this.currentGisZoom == null) return false
      return this.currentGisZoom >= this.gisZoomRange.maxZoom - 1e-6
    },
    // 已到最小层级：禁用缩小
    zoomOutDisabled() {
      if (this.isSlam || this.currentGisZoom == null) return false
      return this.currentGisZoom <= this.gisZoomRange.minZoom + 1e-6
    }
  },
  mounted() {
    document.addEventListener('click', this.handleDocumentClick, true)
    // 不在此处兜底 GIS：须等 overview（setAll）完成 GPS / SLAM 判断
  },
  beforeDestroy() {
    document.removeEventListener('click', this.handleDocumentClick, true)
    this.clearSlamEmptyTipTimer()
  },
  methods: {
    ...mapActions('websocketExtraData', ['setMapSearchValue', 'setGlobalMapId', 'setSlamEmptyTipShown']),
    // 统计指定 SLAM 地图关联的装备数量
    getSlamRobotCount(mapItem) {
      const id = mapItem?.id
      if (id === undefined || id === null || id === '') return 0
      const robots = this.slamOfRobot?.[String(id)]?.robots
      return Array.isArray(robots) ? robots.length : 0
    },
    clearSlamEmptyTipTimer() {
      if (!this.slamEmptyTipTimer) return
      clearTimeout(this.slamEmptyTipTimer)
      this.slamEmptyTipTimer = null
    },
    /**
     * 仅当 overview 默认即为 SLAM、且预览不可用时提示；
     * 标记写入 vuex，跨路由全局只显示一次。
     */
    showSlamEmptyTip(text) {
      if (!this.isSlam) return
      if (this.globalMapId === 'gis') return
      // 默认地图不是 SLAM（含用户后来手动切到 SLAM）不提示
      if (!this.defaultMapIsSlam) return
      if (this.slamEmptyTipShown) return
      this.setSlamEmptyTipShown(true)
      this.slamEmptyTipText = text || '点击此处可切换地图'
      this.slamEmptyTipVisible = true
      this.clearSlamEmptyTipTimer()
      this.slamEmptyTipTimer = setTimeout(() => {
        this.slamEmptyTipVisible = false
        this.slamEmptyTipTimer = null
      }, 3000)
    },
    selectMapType(type) {
      if (type === 'gis') {
        // UI 已是 GIS 时仍可能尚未写入 globalMapId，不能直接 return
        if (this.selectType === type && this.globalMapId === 'gis') return
        this.selectType = type
        this.currentType = type
        // 单项联动：切 GIS 后侧栏任务卡片展示全部任务，不根据任务反切地图
        this.setGlobalMapId('gis')
        return
      }
      if (this.selectType === type) return
      this.selectType = type
    },
    selectSlamMap(mapInfo) {
      const nextId = mapInfo?.id
      if (nextId == null || nextId === '') return
      // 已选中同一张图时不重复切换，避免触发地图重载闪烁
      if (this.currentType === 'slam' && String(this.currentSlam) === String(nextId)) return
      this.selectType = 'slam'
      this.currentType = 'slam'
      // 单项联动：写入当前 SLAM 地图，侧栏按 mapId 筛选任务卡片
      this.setGlobalMapId(nextId)
      this.$emit('changeSlamMap', mapInfo)
    },
    toggleViewContainer() {
      this.showViewContainer = !this.showViewContainer
    },
    hideViewContainer() {
      this.showViewContainer = false
    },
    handleDocumentClick(event) {
      if (this.$refs.viewChangeRef?.contains(event.target) || this.$refs.viewContainerRef?.contains(event.target)) return
      this.hideViewContainer()
    },
    selectSlam(item) {
      // if (this.currentSlamId === item.id) return
      // this.$emit('changeCurrentSlamId', item.id)
      // this.currentType = 'slam'
    },
    handleChangeType(item) {
      if (this.selectedOper2 === item.key) return
      this.selectedOper2 = item.key
      this.$emit('changeMapType')
    },
    isOperDisabled(item) {
      if (item.key === 'point' && !this.pathOperable) return true
      if (item.key === 'path' && this.isSlam && !this.taskPathOperable) return true
      if (item.key === 'zoomIn' && this.zoomInDisabled) return true
      if (item.key === 'zoomOut' && this.zoomOutDisabled) return true
      return false
    },
    handleClickTool(item) {
      if (this.isOperDisabled(item)) return
      this[item.action](item.key);
    },
    handleSearch() {
      this.openSearch = !this.openSearch
      if (this.openSearch) {
        this.searchValue = ''
      }
    },
    search() {
      this.setMapSearchValue(this.searchValue)
    },
    changeLayer() {},
    // GIS：内置路径；SLAM：当前地图全部任务路径。
    // 高亮时选中卡片只显示该任务；再点「路径」关闭并清空。
    // 未高亮时选中卡片会高亮「路径」并只显示该任务；取消卡片或再点「路径」均清空。
    showPath() {
      this.pathActive = !this.pathActive
      if (this.isSlam) {
        this.$emit('toggleTaskPaths', this.pathActive)
        return
      }
      this.$emit('togglePath', this.pathActive)
    },
    setPathActive(active) {
      const next = !!active
      if (this.pathActive === next) return
      this.pathActive = next
      if (this.isSlam) this.$emit('toggleTaskPaths', next)
      else this.$emit('togglePath', next)
    },
    // SLAM 地图：切换点位显示
    showPoint() {
      if (!this.pathOperable) {
        this.pointActive = false
        this.$emit('togglePath', false)
        return
      }
      this.pointActive = !this.pointActive
      this.$emit('togglePath', this.pointActive)
    },
    resetPathActive() {
      if (this.pathActive) {
        this.pathActive = false
        if (this.isSlam) this.$emit('toggleTaskPaths', false)
        else this.$emit('togglePath', false)
      }
      if (this.pointActive) {
        this.pointActive = false
        this.$emit('togglePath', false)
      }
      this.resetRangingActive()
    },
    changeAngle() {
      this.$emit('changeMapAngle')
    },
    ranging() {
      this.rangingActive = !this.rangingActive
      this.$emit('toggleRanging', this.rangingActive)
    },
    resetRangingActive() {
      if (!this.rangingActive) return
      this.rangingActive = false
      this.$emit('toggleRanging', false)
    },
    changeSkin() {},
    backCenter() {
      this.$emit('setCenter')
    },
    changeZoom(key) {
      this.$emit('changeMapZoom', { method: key, value: 1 })
    },
  },
  watch: {
    isSlam: {
      immediate: true,
      handler(newVal) {
        this.currentType = newVal ? 'slam' : 'gis'
        this.selectType = newVal ? 'slam' : 'gis'
        this.resetPathActive()
      }
    },
    currentSlam() {
      this.resetPathActive()
    },
    pathOperable(val) {
      // 仅当地图本身无点位时关闭；选中/打开装备不影响点位渲染
      if (!val && this.pointActive) {
        this.pointActive = false
        this.$emit('togglePath', false)
      }
    },
    taskPathOperable(val) {
      if (!val && this.isSlam && this.pathActive) {
        this.pathActive = false
        this.$emit('toggleTaskPaths', false)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.custom-search-div {
  position: absolute;
  top: 0;
  left: -240px;
  ::v-deep .el-input {
    .el-input__prefix {
      left: 10px;
      line-height: 40px;
    }
    .el-input__inner {
      height: 40px;
      padding: 0 10px 0 36px;
      border-radius: 4px;
      border: 1px solid #374E69;
      background: #111B2A;
      font-weight: 600;
      &::placeholder {
        color: #8897AB;
        font-size: 14px;
      }
    }
  }
}
.map-operation {
  position: absolute;
  top: 33px !important;
  right: 20px !important;
  width: 50px;
  .operation {
    width: 50px;
    padding: 20px 10px;
    border-radius: 4px;
    border: 1px solid #4C617B;
    background: #141E28;
    backdrop-filter: blur(2px);
    & + .operation {
      margin-top: 10px;
    }
    .operation-item {
      color: #D7EDFF;
      font-family: "Microsoft YaHei";
      font-size: 14px;
      cursor: pointer;
      .svg-icon {
        font-size: 28px;
      }
      span {
        height: 18px;
        line-height: 18px;
      }
      &:hover, &.is-active {
        color: #00CBFD;
      }
      &.is-disabled {
        opacity: 0.4;
        cursor: not-allowed;
        pointer-events: none;
      }
      & + .operation-item {
        position: relative;
        margin-top: 20px;
        &::before {
          position: absolute;
          top: -9.5px;
          left: 4px;
          display: block;
          width: 20px;
          height: 1px;
          background: rgba(255, 255, 255, 0.30);
          content: '';
        }
      }
    }
  }
}

.view-change {
  position: relative;
  cursor: pointer;
  border-radius: 50%; 
  background: #FFF;
  box-shadow: 0 1.297px 3.243px 0 rgba(0, 0, 0, 0.30);
}
.view {
  position: relative;
  .map-view-tip {
    position: absolute;
    top: 50%;
    right: calc(100% + 12px);
    z-index: 6;
    transform: translateY(-50%);
    max-width: 220px;
    padding: 10px 12px;
    border-radius: 4px;
    border: 1px solid rgba(11, 249, 254, 0.45);
    background: rgba(14, 28, 45, 0.92);
    box-shadow: 0 0 12px rgba(11, 249, 254, 0.18);
    backdrop-filter: blur(4px);
    pointer-events: none;
    white-space: nowrap;
  }
  .map-view-tip__text {
    color: #D7EDFF;
    font-family: "Microsoft YaHei";
    font-size: 13px;
    line-height: 18px;
    letter-spacing: 0.3px;
  }
  .map-view-tip__arrow {
    position: absolute;
    top: 50%;
    right: -6px;
    width: 10px;
    height: 10px;
    border-top: 1px solid rgba(11, 249, 254, 0.45);
    border-right: 1px solid rgba(11, 249, 254, 0.45);
    background: rgba(14, 28, 45, 0.92);
    transform: translateY(-50%) rotate(45deg);
    content: '';
  }
  .view-container {
    position: absolute;
    top: -80px;
    right: 60px;
    // 避免定高 + transform:scale 后底边 1px 装饰被裁切
    overflow: visible;
    &.is-gis-tip {
      height: auto !important;
      min-height: 181px;
    }
    .title {
      color: #FFF;
      font-family: "Microsoft YaHei";
      font-size: 14px;
      font-weight: 600;
      line-height: 19px;
    }
    .img-view {
      position: relative;
      border-radius: 4px;
      background: linear-gradient(0deg, rgba(0, 0, 0, 0.50) 0%, rgba(0, 0, 0, 0.50) 100%), url(<path-to-image>) lightgray 50% / cover no-repeat;
      &.is-active {
        border: 2px solid #0BF9FE;
      }
      .type-name, .count {
        position: absolute;
        color: #FFF;
      }
      .type-name {
        bottom: 0;
        left: 6px;
        font-family: "Microsoft YaHei";
        font-size: 14px;
        line-height: 18px;
      }
      .count {
        top: 4px;
        right: 4px;
        border-radius: 2px;
        background: rgba(0, 0, 0, 0.60);
        color: #FFF;
        font-family: Bahnschrift;
        font-size: 12px;
        font-weight: 350;
        letter-spacing: 0.857px;
        line-height: 14px;
      }
    }
    .slam-list {
      min-height: 0;
      overflow-y: auto;
      margin-right: -20px;
      .item {
        width: 234px;
        padding: 8px 10px;
        color: #D0DEEE;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        line-height: 19px;
        letter-spacing: 0.857px;
        align-items: flex-start;
        &.is-active {
          border: 1px solid #0BF9FE;
        }
        .svg-icon {
          height: 19px;
        }
      }
    }
    .desc{
      position: relative;
      z-index: 3;
      // 给上下装饰线留出空间，避免贴边被 scale 裁切
      padding-top: 12px !important;
      padding-bottom: 12px !important;
      &::before, &::after {
        position: absolute;
        left: 0;
        width: 100%;
        // ScaleScreen 缩放后 1px 会变成亚像素消失，用 2px + 渐变更稳
        height: 2px;
        background: linear-gradient(90deg, #091D3C 0%, #038EFF 52%, #021534 100%);
        content: '';
        z-index: 1;
        pointer-events: none;
      }
      &::before {
        top: 0;
      }
      &::after {
        bottom: 0;
      }
      .text {
        position: relative;
        z-index: 1;
        color: #D0DEEE;
        font-family: "Alibaba PuHuiTi";
        font-size: 12px;
        line-height: 16px;
        letter-spacing: 0.857px;
        &::before {
          position: absolute;
          top: 6px;
          left: 0;
          width: 4px;
          height: 4px;
          border-radius: 50%;
          content: '';
        }
        &.red {
          &::before {
            background: #FF0000;
          }
        }
      }
    }
  }
}

.map-view-tip-fade-enter-active,
.map-view-tip-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.map-view-tip-fade-enter,
.map-view-tip-fade-leave-to {
  opacity: 0;
  transform: translate(6px, -50%);
}
</style>
