<template>
  <div class="map-operation">
    <div class="operation1 p10">
      <div
        v-for="(item, index) in operList1"
        :key="item.key"
        @click="handleClickTool(item)"
        class="operation-item flx-center flex-column"
        :class="{ 'is-active': selectedOper1 === item.key }"
      >
        <template v-if="index === 0">
          <el-popover placement="left" trigger="hover" popper-class="custom-popover map-layer-popover">
            <template slot="reference">
              <svg-icon :icon-class="item.icon" />
              <span>{{ item.name }}</span>
            </template>
            <el-radio-group v-model="tabIndex" class="custom-radio-group flex with-border vertical">
              <el-radio v-for="item in tabList" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
            </el-radio-group>
          </el-popover>
        </template>
        <template v-else>
          <svg-icon :icon-class="item.icon" />
          <span>{{ item.name }}</span>
        </template>
      </div>
    </div>
    <div class="operation2 mt20 p10">
      <div v-for="(item, index) in operList2" :key="item.key" @click="handleChangeType(item)" class="operation-item flx-center flex-column" :class="{ 'is-active': selectedOper2 === item.key }">
        <svg-icon v-if="index !== 0" class="mt10 mb10" style="font-size: 16px; color: #9FA29F" icon-class="switch1" />
        <svg-icon :icon-class="item.icon" />
        <span>{{ item.name }}</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MapTool',
  data() {
    return {
      selectedOper1: 'layer',
      selectedOper2: 'outer',
      operList1: [
        {
          icon: 'map-layer',
          name: '图层',
          key: 'layer',
          action: 'changeLayer'
        },
        {
          icon: 'map-angle',
          name: '视角',
          key: 'angle',
          action: 'changeAngle'
        },
        {
          icon: 'map-scale',
          name: '测距',
          key: 'scale',
          action: 'ranging'
        },
        {
          icon: 'map-scale',
          name: '皮肤',
          key: 'skin',
          action: 'changeSkin'
        },
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
      operList2: [
        {
          icon: 'map-in-out-side',
          name: '外部',
          key: 'outer'
        },
        {
          icon: 'map-in-out-side',
          name: '内部',
          key: 'inner'
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
      ]
    }
  },
  methods: {
    handleChangeType(item) {
      if (this.selectedOper2 === item.key) return
      this.selectedOper2 = item.key
      this.$emit('changeMapType')
    },
    handleClickTool(item) {
      this[item.action](item.key);
    },
    changeLayer() {},
    changeAngle() {},
    ranging() {},
    changeSkin() {},
    changeZoom(key) {
      this.$emit('changeMapZoom', { method: key, value: 1 })
    },
  }
}
</script>