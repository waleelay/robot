<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title=""
  >
    <template slot="footer"></template>
    <div class="custom-modal-container snapshot-container1">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="flx-align-center">
            <div class="title ml10">快速抓拍</div>
          </div>
          <div class="close mr10" @click="handleClose">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content pt20 pr20 pb30 pl28 flex">
          <div class="type-div">
            <div class="second-title">告警类型</div>
            <div class="mt10 flex">
              <div
                v-for="(item, index) in types"
                :key="index"
                class="flx-center type"
                :class="{'is-active': activeIndex === item.value}"
                @click="activeIndex = item.value"
              >
                <span class="symbol flx-center">
                  <svg-icon icon-class="shopping"></svg-icon>
                </span>
                <span class="ml10">{{ item.label }}</span>
              </div>
            </div>
            <!-- 预览区域 -->
            <div class="form-item mt10">
              <label class="form-label">预览</label>
              <div class="preview-box">
                <img v-if="previewImage" :src="previewImage" alt="快照预览" class="preview-img">
                <div v-else class="no-preview">点击下方按钮获取快照</div>
              </div>
            </div>
            <div class="btns flx-center">
              <!-- <el-button type="primary" @click="takeSnapshot" :disabled="!activeIndex || isTakingSnapshot">
                {{isTakingSnapshot ? '获取中' : '获取快照'}}
              </el-button> -->
              <el-button type="success" @click="saveSnapshot" :disabled="!previewImage">
                保存快照
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { captureByMethod, drawWatermark } from '../../../../utils/snap';

export default {
  name: 'Snap',
  props: {
    idName: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      dialogVisible: false,
      activeIndex: 0,
      types: [
        { label: '任务告警', value: 1 },
        { label: '业务告警', value: 2 },
        { label: '设备告警', value: 3 },
        { label: '其他告警', value: 4 },
      ],
      previewImage: '',
      isTakingSnapshot: false,  // 添加加载状态
      captureMethod: 'auto',  // 快照方式
    }
  },
  methods: {
    async showModal() {
      await this.takeSnapshot();
      this.dialogVisible = true;
    },
    // 获取视频快照
    async takeSnapshot() {
      this.isTakingSnapshot = true;
      try {
        // 获取 video 元素
        // 方式2：通过 idName 获取
        const video = document.getElementById(this.idName);
        if (!video) {
          throw new Error('未找到视频元素');
        }
        // 自动检测，尝试多种方式
        // let canvas = await this.tryMultipleCaptureMethods(video);
        let canvas = await captureByMethod(video, 'canvas');
        if (!canvas) {
          throw new Error('所有快照方式均失败');
        }
        
        // 添加水印
        drawWatermark(canvas);
        
        // 转换为 base64
        this.previewImage = canvas.toDataURL('image/png');
        this.snapshotData = {
          image: this.previewImage,
        //   alarmType: this.selectedAlarmType,
        //   alarmDesc: this.alarmDesc,
        //   timestamp: new Date().getTime(),
        //   slotKey: this.slotKey,
        //   captureMethod: this.captureMethod
        };
        
        this.$message.success('快照获取成功');
        
      } catch (error) {
        console.error('获取快照失败:', error);
        this.$message.error(`获取快照失败: ${error.message}`);
      } finally {
        this.isTakingSnapshot = false;
      }
    },
    
    // 保存快照
    saveSnapshot() {
      if (!this.snapshotData) {
        this.$message.error('请先获取快照');
        return;
      }
      const link = document.createElement('a');
      const timestamp = new Date().toISOString().slice(0,19).replace(/:/g, '-');
      link.download = `screenshot_${timestamp}.png`;
      link.href = this.previewImage;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      // 可以在这里调用后端接口保存快照
      // console.log('保存快照:', this.snapshotData);
      // 模拟保存
      this.$message.success('截图已保存为PNG图片');
      this.handleClose();
    },
    // 关闭弹窗
    handleClose() {
      this.previewImage = '';
      this.snapshotData = null;
      this.activeIndex = 0;
      this.dialogVisible = false;
    }
  }
}
</script>

<style lang="scss" scoped>
.form-item {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  margin-bottom: 8px;
  font-weight: bold;
  color: #333;
}

.preview-box {
  width: 100%;
  height: 200px;
  border: 1px dashed #ddd;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.no-preview {
  color: #999;
  font-size: 14px;
}
</style>
