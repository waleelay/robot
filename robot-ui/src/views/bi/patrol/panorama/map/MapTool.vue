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
        :class="{ 'is-active': selectedOper2 === item.key }"
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

    <div class="mt22">
      <div class="view-change flx-center">
        <!-- <img src="../../../../../assets/images/new-bi/view.png" width="44px" height="44px" style="border-radius: 50%;" /> -->
        <img src="../../../../../assets/images/new-bi/video-empty.png" width="44px" height="44px" style="border-radius: 50%;" />
      </div>
      <div class="view-container p20">
        <div class="title">地图选择</div>
        <div class="d-flex mt8">
          <div class="img-view wp112 hp63" :class="{ 'is-active': currentType === 'gis' }" @click="selectMapType('gis')">
            <img src="../../../../../assets/images/new-bi/gis-view.png" alt="" srcset="" class="w100 h100">
            <span>GIS地图</span>
          </div>
          <div class="img-view wp112 hp63 ml10" :class="{ 'is-active': currentType === 'slam' }" @click="selectMapType('slam')">
            <img src="../../../../../assets/images/new-bi/slam-view.png" alt="" srcset="" class="w100 h100">
            <span>SLAM地图</span>
          </div>
        </div>
        <div v-if="currentType === 'slam'" class="slam-list pb6">
          <div v-for="item in slamList" :key="item.id || item.name" class="item flx-justify-between" :class="{ 'is-active': currentSlam === item.name }">
            <span>{{ item.name }}</span>
            <svg-icon v-if="currentSlam === item.name" icon-class="check" style="font-size: 16px;"></svg-icon>
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
      type: String,
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
        // {
        //   icon: 'map-in-out-side',
        //   name: '外部',
        //   key: 'outer'
        // },
        // {
        //   icon: 'map-in-out-side',
        //   name: '内部',
        //   key: 'inner'
        // },
        // {
        //   icon: 'map-layer',
        //   name: '图层',
        //   key: 'layer',
        //   action: 'changeLayer'
        // },
        // {
        //   icon: 'map-path',
        //   name: '路径',
        //   key: 'path',
        //   action: 'showPath'
        // },
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
    }
  },
  computed: {
    ...mapState('websocketExtraData', {
      slamList: state => Array.isArray(state.slamMapList) ? state.slamMapList : []
    })
  },
  mounted() {
    this.operList2 = this.operList2.filter(item => this.showAngle || (!this.showAngle && item.key !== 'angle'))
  },
  methods: {
    ...mapActions('websocketExtraData', ['setMapSearchValue']),
    selectMapType(type) {
      this.currentType = type
    },
    handleChangeType(item) {
      if (this.selectedOper2 === item.key) return
      this.selectedOper2 = item.key
      this.$emit('changeMapType')
    },
    handleClickTool(item) {
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
    showPath() {},
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
  border-radius: 24px; 
  background: #FFF;
  box-shadow: 0 1.297px 3.243px 0 rgba(0, 0, 0, 0.30);
  .view-container {
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
