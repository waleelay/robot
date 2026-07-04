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
  </div>
</template>

<script>
import { mapActions } from 'vuex';

export default {
  name: 'MapTool',
  props: {
    angle: {
      type: String,
      default: '2D'
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
    }
  },
  methods: {
    ...mapActions('websocketExtraData', ['setMapSearchValue']),
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
      console.log(111, this.searchValue);
      
      this.setMapSearchValue(this.searchValue)
    },
    changeLayer() {},
    showPath() {},
    changeAngle() {
      this.$emit('changeMapAngle', this.angle === '3D' ? '2D' : '3D')
    },
    ranging() {},
    changeSkin() {},
    backCenter() {
      this.$emit('setCenter')
    },
    changeZoom(key) {
      this.$emit('changeMapZoom', { method: key, value: 1 })
    },
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
  width: 48px;
  .operation {
    width: 48px;
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
</style>