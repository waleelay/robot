<template>
  <div class="flex" style="position: absolute; top: 100px; left: 100px;">
    <div class="toolbar">
      <div class="split-radios">
        <span class="label">分屏模式：</span>
        <el-radio-group v-model="splitType" size="small" @change="onSplitChange">
          <el-radio-button :label="1">一分屏</el-radio-button>
          <el-radio-button :label="4">四分屏</el-radio-button>
          <el-radio-button :label="9">九分屏</el-radio-button>
        </el-radio-group>
      </div>

      <div class="device-panel">
        <!-- 一分屏: 单选设备 -->
        <div v-if="splitType === 1">
          <el-radio-group v-model="singleDeviceId" @change="onSingleDeviceChange">
            <el-radio v-for="dev in deviceList" :key="dev.id" :label="dev.id">
              {{ dev.name }}
            </el-radio>
          </el-radio-group>
        </div>
        <!-- 多分屏: 多选设备（复选框） -->
        <div v-else>
          <el-checkbox-group v-model="checkedDeviceIds" @change="onMultiDeviceChange">
            <el-checkbox v-for="dev in deviceList" :key="dev.id" :label="dev.id">
              {{ dev.name }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
    </div>

    <!-- 视频网格 - 固定数量格子 -->
    <div class="video-grid" :style="gridStyle">
      <div
        v-for="index in splitType"
        :key="index"
        class="video-wrapper"
        :class="{ 'fullscreen': fullscreenIndex === index }"
        :ref="'videoWrapper' + index">
        <!-- 通过键值对获取当前索引的设备，键名为 'slot_' + index -->
        <template v-if="slotDevices['slot_' + index]">
          <div class="video-placeholder">
            <span>📹 {{ slotDevices['slot_' + index].deviceName }}</span>
          </div>
          <div class="video-footer">
            <div class="footer-left">
              <span class="device-name" :title="slotDevices['slot_' + index].deviceName">
                {{ slotDevices['slot_' + index].deviceName }}
              </span>
              <el-select 
                v-model="slotDevices['slot_' + index].currentAlgo" 
                :placeholder="slotDevices['slot_' + index].algorithms.length ? '选择算法' : '无算法'" 
                size="mini" 
                class="algo-select"
                popper-class="algo-popper"
                clearable
                @change="val => onAlgoChange(slotDevices['slot_' + index], val)"
                >
                <!-- :popper-append-to-body="false" -->
                <el-option
                  v-for="algo in slotDevices['slot_' + index].algorithms"
                  :key="algo"
                  :label="algo"
                  :value="algo">
                </el-option>
              </el-select>
            </div>
            <div class="footer-right">
              <span class="fullscreen-btn" @click="toggleFullscreen(index)">
                {{fullscreenIndex === index  ? '退出' : '全屏'}}
                <!-- <i :class="fullscreenIndex === index ? 'fas fa-compress' : 'fas fa-expand'"></i> -->
              </span>
            </div>
          </div>
        </template>
        <!-- 空设备占位 -->
        <template v-else>
          <div class="video-placeholder empty-device">
            ⚡ 空闲
          </div>
          <div class="video-footer">
            <div class="footer-left">
              <span class="device-name empty-device">未分配</span>
              <!-- <el-select size="mini" class="algo-select" placeholder="无设备" disabled>
                <el-option label="无算法" value=""></el-option>
              </el-select> -->
            </div>
            <div class="footer-right">
              <span class="fullscreen-btn" @click="toggleFullscreen(index)">
                {{fullscreenIndex === index  ? '退出' : '全屏'}}
                <!-- <i :class="fullscreenIndex === index ? 'fas fa-compress' : 'fas fa-expand'"></i> -->
              </span>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SplitScreenTest',
  data() {
    return {
      // 设备列表增加到15个
      deviceList: [
        { id: 1, name: '东门摄像头', algorithms: ['打架斗殴', '工服检测', '人员聚集'] },
        { id: 2, name: '西门摄像头', algorithms: ['工服检测', '安全帽识别'] },
        { id: 3, name: '南门云台', algorithms: ['打架斗殴', '烟雾火焰'] },
        { id: 4, name: '北门枪机', algorithms: ['车辆违停', '工服检测'] },
        { id: 5, name: '大厅半球', algorithms: ['人脸抓拍', '打架斗殴'] },
        { id: 6, name: '走廊A', algorithms: ['工服检测', '区域闯入'] },
        { id: 7, name: '机房', algorithms: ['仪表识别', '人员值守'] },
        { id: 8, name: '停车场', algorithms: ['车牌识别', '工服检测'] },
        { id: 9, name: '食堂', algorithms: ['烟火识别', '打架斗殴'] },
        { id: 10, name: '会议室', algorithms: ['人员统计', '工服检测'] },
        { id: 11, name: '仓库A', algorithms: ['烟雾火焰', '入侵检测'] },
        { id: 12, name: '仓库B', algorithms: ['温度异常', '工服检测'] },
        { id: 13, name: '大门岗亭', algorithms: ['车牌识别', '人脸抓拍'] },
        { id: 14, name: '篮球场', algorithms: ['打架斗殴', '区域闯入'] },
        { id: 15, name: '地下车库', algorithms: ['车辆违停', '烟雾火焰'] }
      ],
      splitType: 1,                // 1,4,9
      singleDeviceId: null,         // 一分屏选中的设备id，初始无默认填充
      checkedDeviceIds: [],          // 多分屏选中的设备id数组，初始为空
      // slotDevices改为键值对形式，键名为'slot_1','slot_2'...，值为设备对象或null
      slotDevices: {},
      lastCheckedIds: [],              // 记录上一次的多选值，用于对比变化
      fullscreenIndex: null            // 当前全屏的格子索引，null表示无全屏
    }
  },
  computed: {
    gridStyle() {
      let columns = 1;
      if (this.splitType === 4) columns = 2;
      if (this.splitType === 9) columns = 3;
      return { 'grid-template-columns': `repeat(${columns}, 1fr)` };
    }
  },
  watch: {
    // 分屏变化：完全清空所有选择，重置slotDevices为空数组（全空）
    splitType: {
      handler(newVal, oldVal) {
        // 退出全屏
        this.fullscreenIndex = null;
        // 清空所有选中项
        this.singleDeviceId = null;
        this.checkedDeviceIds = [];
        this.lastCheckedIds = [];
        // 重新初始化 slotDevices，所有键对应的值都为null
        const newSlotDevices = {};
        for (let i = 1; i <= newVal; i++) {
            newSlotDevices['slot_' + i] = null;
        }
        this.slotDevices = newSlotDevices;
      },
      immediate: true
    }
  },
  methods: {
    // 一分屏：单选变化
    onSingleDeviceChange(deviceId) {
      if (this.splitType !== 1) return;
      const dev = this.deviceList.find(d => d.id === deviceId);
      // 创建新的slotDevices对象
      const newSlotDevices = {};
      if (!dev) {
        // 如果设备无效，清空格子
        newSlotDevices['slot_1'] = null;
        return;
      }
      // 创建设备对象，currentAlgo 默认设为 null（不选中任何算法）
      newSlotDevices['slot_1'] = {
        deviceId: dev.id,
        deviceName: dev.name,
        algorithms: dev.algorithms.slice(),
        currentAlgo: null,  // 默认不选中任何算法
        videoSrc: ''
      };
      this.slotDevices = newSlotDevices;
    },

    // 多分屏：复选框变化 - 实现取消勾选清空对应格子，勾选新设备填入第一个空位
    onMultiDeviceChange(newCheckedIds) {
      if (this.splitType === 1) return;

      // 找出新增的id (在newCheckedIds中但不在lastCheckedIds中)
      const added = newCheckedIds.filter(id => !this.lastCheckedIds.includes(id));
      // 找出移除的id (在lastCheckedIds中但不在newCheckedIds中)
      const removed = this.lastCheckedIds.filter(id => !newCheckedIds.includes(id));

      // 更新lastCheckedIds供下次使用
      this.lastCheckedIds = newCheckedIds.slice();

      // 创建新的slotDevices对象（基于当前的slotDevices）
      const newSlotDevices = { ...this.slotDevices };

      // 1. 处理移除：找到该设备所在的插槽索引，将其置为null
      removed.forEach(remId => {
        // 遍历所有插槽找到匹配的设备
        for (let i = 1; i <= this.splitType; i++) {
          const key = 'slot_' + i;
          if (newSlotDevices[key] && newSlotDevices[key].deviceId === remId) {
            // 如果正在全屏的格子被移除，退出全屏
            if (this.fullscreenIndex === i) {
              this.fullscreenIndex = null;
            }
            // 将该插槽置空
            newSlotDevices[key] = null;
            break;
          }
        }
      });

      // 2. 处理新增：找到第一个为null的插槽，放入设备
      added.forEach(addId => {
        const dev = this.deviceList.find(d => d.id === addId);
        if (!dev) return;
        
        // 查找第一个空插槽
        let emptyKey = null;
        for (let i = 1; i <= this.splitType; i++) {
          const key = 'slot_' + i;
          if (newSlotDevices[key] === null) {
              emptyKey = key;
              break;
          }
        }
        
        if (emptyKey) {
          // 放入设备，currentAlgo 默认设为 null（不选中任何算法）
          newSlotDevices[emptyKey] = {
            deviceId: dev.id,
            deviceName: dev.name,
            algorithms: dev.algorithms.slice(),
            currentAlgo: null,  // 默认不选中任何算法
            videoSrc: ''
          };
        } else {
          // 没有空位了，无法继续添加，将该id从checkedDeviceIds中移除
          console.warn('没有空余插槽，无法添加设备', addId);
          const indexToRemove = this.checkedDeviceIds.indexOf(addId);
          if (indexToRemove !== -1) {
            this.checkedDeviceIds.splice(indexToRemove, 1);
          }
          // 同步lastCheckedIds
          this.lastCheckedIds = this.checkedDeviceIds.slice();
        }
      });
      // 更新slotDevices
      this.slotDevices = newSlotDevices;
    },

    // 算法切换
    onAlgoChange(deviceItem, algo) {
      deviceItem.currentAlgo = algo;
      console.log(`设备 ${deviceItem.deviceName} 算法 -> ${algo || '未选择'}`);
    },

    // 分屏切换时（已经在watch中清空，但需要额外处理一些边界）
    onSplitChange(val) {
      // watch已经处理，但需要额外处理全屏状态
      this.fullscreenIndex = null;
    },
    // 切换全屏模式
    toggleFullscreen(index) {
      // 如果当前已经是全屏状态，则退出全屏
      if (this.fullscreenIndex === index) {
        this.fullscreenIndex = null;
        // 移除body的overflow隐藏
        document.body.style.overflow = '';
      } else {
        // 否则进入全屏
        this.fullscreenIndex = index;
        // 防止body滚动
        document.body.style.overflow = 'hidden';
        
        // 延迟一点时间，确保全屏DOM更新后重新定位下拉框
        setTimeout(() => {
          // 触发一次重绘，确保下拉框位置正确
          this.$forceUpdate();
        }, 100);
      }
    }
  },
  mounted() {
    // 初始化：不默认填充任何设备
    this.singleDeviceId = null;
    this.checkedDeviceIds = [];
    this.lastCheckedIds = [];
    // 初始化slotDevices为键值对形式，所有值为null
    const initialSlotDevices = {};
    for (let i = 1; i <= this.splitType; i++) {
        initialSlotDevices['slot_' + i] = null;
    }
    this.slotDevices = initialSlotDevices;
    this.fullscreenIndex = null;
  }
}
</script>

<style>
.toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 30px;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid #eaeef2;
}
.split-radios {
    display: flex;
    align-items: center;
    gap: 20px;
}
.split-radios .el-radio-group {
    margin-left: 8px;
}
.device-panel {
    flex: 1;
    min-width: 260px;
    max-height: 120px;
    overflow-y: auto;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    padding: 8px;
    background: #f9fafc;
}
.device-panel .el-radio-group, 
.device-panel .el-checkbox-group {
    display: flex;
    flex-wrap: wrap;
    gap: 12px 20px;
}
/* 视频网格容器 */
.video-grid {
    display: grid;
    gap: 16px;
    transition: all 0.2s;
    margin-top: 16px;
}
.video-wrapper {
    box-shadow: 0 0 1px 1px rgba(0, 0, 0, 0.5);
    background: #1e1e2f;
    border-radius: 4px;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    position: relative;
    aspect-ratio: 16 / 9;
}
.video-placeholder {
    flex: 1;
    background: #2a2a3a;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #a9a9b3;
    font-size: 14px;
    min-height: 0;
    position: relative;
}
.video-placeholder video {
    width: 100%;
    height: 100%;
    object-fit: cover;
    background: #000;
}
.video-footer {
    background: rgba(0,0,0,0.7);
    color: white;
    padding: 6px 12px;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 8px;
    backdrop-filter: blur(4px);
    border-top: 1px solid rgba(255,255,255,0.2);
    z-index: 10;
}
.device-name {
    font-weight: 500;
    margin-right: 8px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 120px;
}
.algo-select {
    width: 130px;
}
.algo-select .el-input__inner {
    height: 28px;
    line-height: 28px;
    background: rgba(255,255,255,0.15);
    border-color: rgba(255,255,255,0.3);
    color: white;
}
.algo-select .el-input__icon {
    line-height: 28px;
    color: rgba(255,255,255,0.7);
}
.empty-device {
    color: #c0c4cc;
    font-style: italic;
}
.algo-popper {
    background: #2c3e50;
    border: none;
    z-index: 10000 !important;
}
.algo-popper .el-select-dropdown__item {
    color: #eee;
}
.algo-popper .el-select-dropdown__item.hover,
.algo-popper .el-select-dropdown__item:hover {
    background-color: #1e2b38;
    color: #fff;
}
/* 全屏按钮样式 */
.fullscreen-btn {
    color: white;
    font-size: 16px;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 4px;
    transition: background-color 0.2s;
    display: flex;
    align-items: center;
    justify-content: center;
}
.fullscreen-btn:hover {
    background-color: rgba(255,255,255,0.2);
}
.footer-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
    min-width: 0;
}
.footer-right {
    display: flex;
    align-items: center;
    gap: 4px;
}
/* 全屏模式样式 - 优化下拉框可见性 */
.video-wrapper.fullscreen {
    position: fixed;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    z-index: 9999;
    aspect-ratio: auto;
    background: #000;
}
.video-wrapper.fullscreen .video-placeholder {
    height: calc(100vh - 60px);
}
.video-wrapper.fullscreen .video-footer {
    position: fixed;
    bottom: 0;
    left: 0;
    width: 100%;
    background: rgba(0,0,0,0.8);
    padding: 10px 20px;
    z-index: 10001;
    border-top: 1px solid rgba(255,255,255,0.2);
}
.video-wrapper.fullscreen .algo-select {
    width: 160px;
}
.video-wrapper.fullscreen .algo-select .el-input__inner {
    height: 32px;
    line-height: 32px;
    font-size: 14px;
}
/* 确保下拉面板在全屏时显示在最上层 */
.video-wrapper.fullscreen .el-select-dropdown {
    z-index: 10002 !important;
}
/* 调整下拉面板位置，确保不会超出视口 */
.el-select-dropdown {
    z-index: 10002 !important;
}
</style>