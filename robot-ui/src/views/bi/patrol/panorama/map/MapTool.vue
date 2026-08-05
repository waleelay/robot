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
        @click="handleClickTool(item)"
        class="operation-item flx-center flex-column"
        :class="{
          'is-active': item.key === 'path'
            ? pathActive
            : item.key === 'point'
              ? pointActive
              : selectedOper2 === item.key,
          'is-disabled': (item.key === 'point' && !pathOperable) || (item.key === 'path' && isSlam && !taskPathOperable)
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
      <div ref="viewChangeRef" class="view-change flx-center wp50 hp50" @click.stop="toggleViewContainer">
        <img src="../../../../../assets/images/new-bi/view.png" width="44px" height="44px" style="border-radius: 50%;" />
      </div>
      <div ref="viewContainerRef" v-show="showViewContainer" class="view-container bi-corner-box p20 wp274 flex-column" @click.stop :class="{ 'hp332': slamList.length && selectType === 'slam', 'hp145': !slamList.length }">
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
import { mapActions, mapState } from 'vuex';

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
          action: 'showPath'
        },
        {
          icon: 'map-point',
          name: '点位',
          key: 'point',
          action: 'showPoint'
        },
        // {
        //   icon: 'map-angle',
        //   name: '视角',
        //   key: 'angle',
        //   action: 'changeAngle'
        // },
        // {
        //   icon: 'map-scale',
        //   name: '测距',
        //   key: 'scale',
        //   action: 'ranging'
        // },
        {
          icon: 'map-location',
          name: '定位',
          key: 'location',
          action: 'backCenter'
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
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['slamMapList', 'slamOfRobot', 'deviceStats', 'taskPathPoints']),
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
    // SLAM 下「点位」是否可操作：仅看当前地图是否存在点位信息
    pathOperable() {
      if (!this.isSlam) return false
      const slamId = this.currentSlam
      if (slamId === undefined || slamId === null || slamId === '') return false
      const group = this.slamOfRobot?.[String(slamId)]
      const points = group?.points || this.currentSlamMapInfo?.points
      return Array.isArray(points) && points.length > 0
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
    // 有gps定位功能的装备
    gisCountText() {
      const exist = 2;
      return `${exist}/${this.deviceStats?.total || 0}`
    }
  },
  mounted() {
    document.addEventListener('click', this.handleDocumentClick, true)
    this.syncGlobalMapId()
  },
  beforeDestroy() {
    document.removeEventListener('click', this.handleDocumentClick, true)
  },
  methods: {
    ...mapActions('websocketExtraData', ['setMapSearchValue', 'setGlobalMapId']),
    // 统计指定 SLAM 地图关联的装备数量
    getSlamRobotCount(mapItem) {
      const id = mapItem?.id
      if (id === undefined || id === null || id === '') return 0
      const robots = this.slamOfRobot?.[String(id)]?.robots
      return Array.isArray(robots) ? robots.length : 0
    },
    syncGlobalMapId() {
      const slamId = this.currentSlam
      const nextId = this.isSlam && slamId != null && slamId !== '' ? slamId : 'gis'
      if (this.$store.state.websocketExtraData.globalMapId === nextId) return
      this.setGlobalMapId(nextId)
    },
    selectMapType(type) {
      if (this.selectType === type) return
      this.selectType = type
      if (type === 'gis') {
        this.currentType = type
        this.setGlobalMapId('gis')
        this.$emit('changeCurrentSlamId', '')
      }
    },
    selectSlamMap(mapInfo) {
      const nextId = mapInfo?.id
      if (nextId == null || nextId === '') return
      // 已选中同一张图时不重复切换，避免触发地图重载闪烁
      if (this.currentType === 'slam' && String(this.currentSlam) === String(nextId)) return
      this.selectType = 'slam'
      this.currentType = 'slam'
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
    handleClickTool(item) {
      if (item.key === 'point' && !this.pathOperable) return
      if (item.key === 'path' && this.isSlam && !this.taskPathOperable) return
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
    // GIS：内置路径；SLAM：当前地图全部任务路径
    showPath() {
      this.pathActive = !this.pathActive
      if (this.isSlam) {
        this.$emit('toggleTaskPaths', this.pathActive)
        return
      }
      this.$emit('togglePath', this.pathActive)
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
    },
    changeAngle() {
      this.$emit('changeMapAngle')
    },
    ranging() {},
    changeSkin() {},
    backCenter() {
      this.$emit('setCenter')
    },
    changeZoom(key) {
      this.$emit('changeMapZoom', { method: key, value: 1 })
    },
  },
  watch: {
    currentType: {
      handler(newVal) {
        if ((newVal === 'slam') !== this.isSlam) {
          this.$emit('changeMapType', newVal)
        }
      },
    },
    isSlam(newVal) {
      this.currentType = newVal ? 'slam' : 'gis'
      this.selectType = newVal ? 'slam' : 'gis'
      this.resetPathActive()
      this.syncGlobalMapId()
    },
    currentSlam() {
      this.resetPathActive()
      this.syncGlobalMapId()
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
  .view-container {
    position: absolute;
    top: -80px;
    right: 60px;
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
      &::before, &::after {
        position: absolute;
        left: 0;
        width: 100%;
        height: 1px;
        background: url("../../../../../assets/images/new-bi/border.svg") center no-repeat;
        content: '';
      }
      &::before {
        top: 0;
      }
      &::after {
        bottom: 0;
      }
      .text {
        position: relative;
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
</style>
