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
        v-for="(item, index) in operList2"
        :key="item.key"
        @click="handleClickTool(item)"
        class="operation-item flx-center flex-column"
        :class="{
          'is-active': item.key === 'path' ? pathActive : selectedOper2 === item.key,
          'is-disabled': item.key === 'path' && !pathOperable
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
      <div ref="viewContainerRef" v-show="showViewContainer" class="view-container p20 wp274" @click.stop :class="{ 'hp332': slamList.length && selectType === 'slam', 'hp145': !slamList.length }">
        <div class="title">地图选择</div>
        <div class="d-flex mt8">
          <div class="img-view wp112 hp63" :class="{ 'is-active': currentType === 'gis' && selectType !== 'slam' }" @click="selectMapType('gis')">
            <img src="../../../../../assets/images/new-bi/gis-view.png" alt="" srcset="" class="w100 h100">
            <span>GIS地图</span>
          </div>
          <div class="img-view wp112 hp63 ml10" :class="{ 'is-active': currentType === 'slam' || selectType === 'slam' }" @click="selectMapType('slam')">
            <img src="../../../../../assets/images/new-bi/slam-view.png" alt="" srcset="" class="w100 h100">
            <span>SLAM地图</span>
          </div>
        </div>
        <div v-if="selectType === 'slam'" class="slam-list mt20 pb6">
          <div v-for="item in slamList" :key="item.id" class="item flx-justify-between" :class="{ 'is-active': String(currentSlam) === String(item.id) }" @click="selectSlamMap(item)">
            <span>{{ item.mapName }}</span>
            <svg-icon v-if="String(currentSlam) === String(item.id)" icon-class="check" style="font-size: 16px;"></svg-icon>
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
          icon: 'map-angle',
          name: '视角',
          key: 'angle',
          action: 'changeAngle'
        },
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
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['slamMapList', 'slamOfRobot', 'showRobotIds', 'robotBaseInfo', 'taskPathPoints']),
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
    // SLAM 下路径是否可操作：无选中装备看地图点位；选中一个装备看任务路径
    pathOperable() {
      if (!this.isSlam) return false
      const ids = Array.isArray(this.showRobotIds) ? this.showRobotIds : []
      if (!ids.length) {
        const slamId = this.currentSlam
        if (slamId === undefined || slamId === null || slamId === '') return false
        const group = this.slamOfRobot?.[String(slamId)]
        const points = group?.points || this.currentSlamMapInfo?.points
        return Array.isArray(points) && points.length > 0
      }
      if (ids.length !== 1) return false
      const robot = this.robotBaseInfo?.[ids[0]] || {}
      const taskId = robot.runningTaskId
      if (taskId === undefined || taskId === null || taskId === '') return false
      const pathData = this.taskPathPoints?.[taskId]
      if (!pathData || !Array.isArray(pathData.pathPoints) || !pathData.pathPoints.length) return false
      return String(pathData.mapId) === String(this.currentSlam)
    }
  },
  mounted() {
    this.operList2 = this.operList2.filter(item => this.showAngle || (!this.showAngle && item.key !== 'angle'))
    document.addEventListener('click', this.handleDocumentClick, true)
    this.syncGlobalMapId()
  },
  beforeDestroy() {
    document.removeEventListener('click', this.handleDocumentClick, true)
  },
  methods: {
    ...mapActions('websocketExtraData', ['setMapSearchValue', 'setGlobalMapId']),
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
      this.currentType = 'slam'
      if (mapInfo?.id != null && mapInfo?.id !== '') {
        this.setGlobalMapId(mapInfo.id)
      }
      this.$emit('changeSlamMap', mapInfo)
      // this.showViewContainer = false
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
      if (item.key === 'path' && !this.pathOperable) return
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
    showPath() {
      if (!this.pathOperable) {
        this.pathActive = false
        this.$emit('togglePath', false)
        return
      }
      this.pathActive = !this.pathActive
      this.$emit('togglePath', this.pathActive)
    },
    resetPathActive() {
      if (!this.pathActive) return
      this.pathActive = false
      this.$emit('togglePath', false)
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
      if (!newVal) this.resetPathActive()
      this.syncGlobalMapId()
    },
    currentSlam() {
      this.resetPathActive()
      this.syncGlobalMapId()
    },
    pathOperable(val) {
      if (!val) this.resetPathActive()
    },
    showRobotIds: {
      deep: true,
      handler() {
        if (!this.pathOperable) this.resetPathActive()
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
    border-radius: 6px;
    border: 2px solid rgba(0, 0, 0, 0.00);
    background: rgba(0, 19, 48, 0.90);
    box-shadow: 0 0 20px 0 rgba(1, 80, 170, 0.80) inset;
    backdrop-filter: blur(5px);
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
      span {
        position: absolute;
        bottom: 0;
        left: 6px;
        color: #FFF;
        font-family: "Microsoft YaHei";
        font-size: 14px;
        line-height: 18px;
      }
    }
    .slam-list {
      .item {
        width: 234px;
        padding: 8px 10px;
        color: #D0DEEE;
        font-family: "Alibaba PuHuiTi";
        font-size: 14px;
        line-height: 19px;
        letter-spacing: 0.857px;
        &.is-active {
          border: 1px solid #0BF9FE;
        }
      }
    }
  }
}
</style>
