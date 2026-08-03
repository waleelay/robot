<template>
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center record-detail-dialog"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="多媒体详情"
  >
    <template slot="footer"></template>
    <div class="custom-modal-container warning-batch-container multimedia-detail-container">
      <div class="decoration wp167 hp5">
        <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
      </div>
      <div class="box">
        <div class="top m4 flx-justify-between">
          <div class="title ml10">多媒体详情</div>
          <div class="close mr10" @click="close">
            <svg-icon icon-class="close"></svg-icon>
          </div>
        </div>
        <div class="info-content p10 flex">
          <div class="task preview-panel">
            <div class="capture-view">
              <div class="second-title">{{ previewTitle }}</div>
              <div class="list-box mt10">
                <div class="preview-box wp576 hp324">
                  <template v-if="details.fileId">
                    <div v-if="isImage" class="img-b w100 h100">
                      <img :src="details.customUrl" alt="">
                    </div>
                    <div v-else class="video-b w100 h100 flx-center">
                      <video
                        ref="detailPlayer"
                        class="w100 h100"
                        controls
                        playsinline
                        preload="metadata"
                      />
                    </div>
                    <div class="download" @click="download">
                      {{ isImage ? '下载原图' : '下载视频' }}
                    </div>
                  </template>
                  <div v-else class="w100 h100 flx-center empty-text">{{ emptyPreviewText }}</div>
                </div>
              </div>
            </div>
            <div class="mt20 details">
              <div class="second-title">{{ detailSectionTitle }}</div>
              <div class="mt10">
                <div class="flex">
                  <div class="item flex1">
                    <span class="name">{{ timeLabel }}</span>
                    <span class="value">{{ formatDateTime(details.uploadedAt || details.createdAt) }}</span>
                  </div>
                  <div class="item flex1 pl30">
                    <span class="name">{{ cameraLabel }}</span>
                    <span class="value">{{ getCameraName(details.robotId, details.deviceId || details.cameraId) || '-' }}</span>
                  </div>
                </div>
                <div class="flex mt10">
                  <div class="item flex1">
                    <span class="name">{{ locationLabel }}</span>
                    <span class="value">{{ isImage ? getLocationText(details) : durationText(details.durationSeconds) }}</span>
                  </div>
                  <div class="item flex1 pl30">
                    <span class="name">装备名称：</span>
                    <span class="value">{{ robotBaseInfo?.[details.robotId]?.name || '-' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div v-if="!simpleMode" class="task list-panel wp294 pr10">
            <div class="second-title">{{ listTitle }}</div>
            <div class="mt10">
              <div class="filter-panel">
                <div class="combined-filter flx-align-center">
                  <div class="type-part">
                    <el-select
                      v-model="searchType"
                      placeholder="条件"
                      @change="handleSearchTypeChange"
                      popper-class="custom-select modal-search-select-popper p9"
                    >
                      <el-option label="关键字" value="keyword" />
                      <el-option label="日期" value="date" />
                    </el-select>
                  </div>
                  <div class="value-part flex1">
                    <el-input
                      v-if="searchType === 'keyword'"
                      :placeholder="selectedRobotId ? '相机/位置' : '装备/相机/位置'"
                      v-model="searchValue"
                      clearable
                      @keyup.enter.native="handleFilter"
                      @clear="handleFilter"
                    >
                      <svg-icon slot="prefix" icon-class="search"></svg-icon>
                    </el-input>
                    <el-date-picker
                      v-else
                      v-model="dateValue"
                      type="daterange"
                      range-separator="至"
                      start-placeholder="开始日期"
                      end-placeholder="结束日期"
                      format="yyyy-M-d"
                      value-format="yyyy-M-d"
                      clearable
                      :picker-options="pickerOptions"
                      @change="handleDateChange"
                    />
                  </div>
                </div>
              </div>
              <div class="list-box mt10 hp374" style="margin-right: -6px; padding-right: 6px;">
                <div
                  v-for="item in filteredList"
                  :key="item.fileId"
                  class="item pt9 pr6 pb9 pl10 flx-justify-between"
                  :class="{ selected: selectedId === item.fileId }"
                  @click="handleClickRow(item)"
                >
                  <div class="flx-align-center w100">
                    <div class="img">
                      <img
                        v-if="item.fileType === 'IMAGE'"
                        :src="item.customUrl"
                        alt=""
                        class="w100 h100"
                      >
                      <video
                        v-else
                        :ref="`listThumb_${item.fileId}`"
                        class="w100 h100"
                        muted
                        playsinline
                        preload="metadata"
                      />
                    </div>
                    <div class="ml10 flex1 info-right">
                      <div class="flx-justify-between flx-align-center">
                        <div>
                          <div class="info text-ellipsis" :title="getListTitle(item)">{{ getListTitle(item) }}</div>
                          <div class="date mt5">{{ formatDateTime(item.uploadedAt || item.createdAt) }}</div>
                          <div class="flx-align-center mt4 address">
                            <svg-icon icon-class="address"></svg-icon>
                            <span class="ml10 text-ellipsis">{{ getLocationText(item) }}</span>
                          </div>
                        </div>
                        <div
                          class="delete-btn ml5"
                          title="删除"
                          @click.stop="handleDelete(item)"
                        >
                          <svg-icon icon-class="delete"></svg-icon>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-if="!filteredList.length" class="empty-text flx-center hp100">暂无记录</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import { mapState } from 'vuex'
import Hls from 'hls.js'
import {
  createFileObjectUrl,
  getFiles,
  getFilePlayUrl,
  fileDownloadUrl,
  revokeFileObjectUrl,
  deleteFile
} from '../../../../../../api/media.js'
import { durationText, recordingTimeRangeText } from '../../../../../../utils/index.js'

export default {
  name: 'MultimediaDetail',
  data() {
    return {
      dialogVisible: false,
      details: {},
      searchType: 'keyword',
      searchValue: '',
      dateValue: [],
      outerTabIndex: 0,
      simpleMode: false,
      listData: [],
      selectedId: '',
      detailHls: null,
      thumbPlayers: {},
      imageLoadSeq: 0,
      ownedImageObjectUrls: [],
      pickerOptions: {
        disabledDate: (date) => {
          const before = `${new Date().getFullYear() - 9}-1-1 00:00:00`
          return (
            new Date(date).getTime() < new Date(before).getTime() ||
            new Date(date).getTime() > new Date().getTime()
          )
        }
      }
    }
  },
  computed: {
    ...mapState('websocketExtraData', ['robotBaseInfo']),
    selectedRobotId() {
      return this.$store.getters['websocketRobot/getSelectedRobotId']
    },
    isImage() {
      return this.outerTabIndex === 0
    },
    previewTitle() {
      return this.isImage ? '抓拍画面' : '视频记录'
    },
    detailSectionTitle() {
      return this.isImage ? '抓拍详情' : '录制详情'
    },
    listTitle() {
      return this.isImage ? '抓拍列表' : '录制列表'
    },
    timeLabel() {
      return this.isImage ? '抓拍时间：' : '录制时间：'
    },
    cameraLabel() {
      return this.isImage ? '抓拍相机：' : '录制相机：'
    },
    locationLabel() {
      return this.isImage ? '抓拍位置：' : '录制时长：'
    },
    emptyPreviewText() {
      return this.isImage ? '暂无画面' : '暂无视频'
    },
    hasDateRange() {
      return Array.isArray(this.dateValue) && this.dateValue.length === 2
    },
    filteredList() {
      const keyword = (this.searchValue || '').trim()
      let startTs = null
      let endTs = null
      if (this.searchType === 'date' && this.hasDateRange) {
        startTs = new Date(`${this.dateValue[0]} 00:00:00`).getTime()
        endTs = new Date(`${this.dateValue[1]} 23:59:59`).getTime()
      }
      return this.listData.filter(item => {
        if (this.searchType === 'date') {
          if (startTs == null || endTs == null) return true
          const t = new Date(item.uploadedAt || item.createdAt).getTime()
          return !Number.isNaN(t) && t >= startTs && t <= endTs
        }
        if (!keyword) return true
        const title = this.getListTitle(item)
        const camera = this.getCameraName(item.robotId, item.deviceId || item.cameraId)
        const robot = this.robotBaseInfo?.[item.robotId]?.name || ''
        const location = this.getLocationText(item)
        return [title, camera, robot, location, item.fileName].some(text =>
          String(text || '').includes(keyword)
        )
      })
    }
  },
  beforeDestroy() {
    this.imageLoadSeq += 1
    this.destroyOwnedImageObjectUrls()
    this.destroyDetailPlayer()
    this.destroyThumbPlayers()
  },
  methods: {
    durationText,
    async open({ item, tabIndex = 0, list = [], simple = false } = {}) {
      this.dialogVisible = true
      this.searchType = 'keyword'
      this.searchValue = ''
      this.dateValue = []
      this.simpleMode = !!simple
      this.outerTabIndex = tabIndex === 1 ? 1 : 0
      if (this.simpleMode) {
        const hydrated = await this.hydrateImageItems(item ? [item] : [])
        if (hydrated == null) return
        const current = hydrated[0] || null
        this.listData = current ? [current] : []
        if (current) {
          await this.selectItem(current)
        } else {
          this.details = {}
          this.selectedId = ''
        }
        return
      }
      await this.loadList()
      const current = item
        ? (this.filteredList.find(row => row.fileId === item.fileId) || this.normalizeItem(item))
        : this.filteredList[0]
      if (current) {
        await this.selectItem(current)
      } else {
        this.details = {}
        this.selectedId = ''
      }
      if (!this.isImage) {
        this.$nextTick(() => this.bindListThumbs())
      }
    },
    handleSearchTypeChange() {
      this.handleFilter()
      if (!this.isImage) {
        this.$nextTick(() => this.bindListThumbs())
      }
    },
    handleDateChange(val) {
      if (!val || (Array.isArray(val) && (val.length === 2 || val.length === 0))) {
        this.handleFilter()
        if (!this.isImage) {
          this.$nextTick(() => this.bindListThumbs())
        }
      }
    },
    normalizeItem(item = {}) {
      return {
        ...item,
        fileType: item.fileType || (item.contentType?.startsWith('video') ? 'VIDEO' : 'IMAGE'),
        customUrl: item.customUrl || ''
      }
    },
    async hydrateImageItems(items = []) {
      const loadSeq = ++this.imageLoadSeq
      const generatedUrls = []
      const hydrated = await Promise.all(items.map(async source => {
        const item = this.normalizeItem(source)
        if (item.fileType !== 'IMAGE' || !item.fileId) {
          return item
        }
        try {
          const customUrl = await createFileObjectUrl(item.fileId)
          generatedUrls.push(customUrl)
          return { ...item, customUrl }
        } catch (e) {
          return { ...item, customUrl: '' }
        }
      }))
      if (loadSeq !== this.imageLoadSeq) {
        generatedUrls.forEach(revokeFileObjectUrl)
        return null
      }
      this.destroyOwnedImageObjectUrls()
      this.ownedImageObjectUrls = generatedUrls
      return hydrated
    },
    destroyOwnedImageObjectUrls() {
      this.ownedImageObjectUrls.forEach(revokeFileObjectUrl)
      this.ownedImageObjectUrls = []
    },
    async loadList() {
      this.destroyThumbPlayers()
      try {
        const params = {
          page: 0,
          size: 50,
          status: 'READY',
          fileType: this.outerTabIndex === 1 ? 'VIDEO' : 'IMAGE'
        }
        if (this.selectedRobotId) params.robotId = this.selectedRobotId
        const res = await getFiles(params) || {}
        const hydrated = await this.hydrateImageItems(res.items || [])
        if (hydrated != null) {
          this.listData = hydrated
        }
      } catch (e) {
        this.destroyOwnedImageObjectUrls()
        this.listData = []
      }
    },
    handleFilter() {
      const current = this.filteredList.find(item => item.fileId === this.selectedId) || this.filteredList[0]
      if (current) {
        this.selectItem(current)
      } else {
        this.destroyDetailPlayer()
        this.details = {}
        this.selectedId = ''
      }
    },
    async handleClickRow(item) {
      await this.selectItem(item)
    },
    async selectItem(item) {
      this.details = { ...item }
      this.selectedId = item.fileId
      this.destroyDetailPlayer()
      if ((item.fileType || 'IMAGE') === 'VIDEO') {
        this.$nextTick(() => this.playDetailVideo(item))
      }
    },
    async playDetailVideo(recording) {
      if (!recording?.fileId) return
      try {
        const playback = await getFilePlayUrl(recording.fileId)
        const player = this.$refs.detailPlayer
        if (!player) return
        const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
        player.controls = true
        player.loop = false
        if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = `${preUrl}${playback.playUrl}`
        } else if (Hls.isSupported()) {
          this.detailHls = new Hls()
          this.detailHls.loadSource(playback.playUrl)
          this.detailHls.attachMedia(player)
        }
      } catch (e) {
        this.$message.error('视频加载失败')
      }
    },
    async bindListThumbs() {
      for (const item of this.filteredList) {
        if ((item.fileType || 'IMAGE') !== 'VIDEO') continue
        await this.bindListThumb(item)
      }
    },
    async bindListThumb(recording) {
      if (!recording?.fileId || recording.status !== 'READY') return
      try {
        const playback = await getFilePlayUrl(recording.fileId)
        const ref = this.$refs[`listThumb_${recording.fileId}`]
        const player = Array.isArray(ref) ? ref[0] : ref
        if (!player) return
        this.destroyThumbPlayer(recording.fileId)
        let recordedHls = null
        const preUrl = process.env.VUE_APP_BASE_ORIGIN || window.location.origin
        player.muted = true
        player.controls = false
        if (player.canPlayType('application/vnd.apple.mpegurl')) {
          player.src = `${preUrl}${playback.playUrl}`
        } else if (Hls.isSupported()) {
          recordedHls = new Hls()
          recordedHls.loadSource(playback.playUrl)
          recordedHls.attachMedia(player)
        }
        this.thumbPlayers[recording.fileId] = { player, recordedHls }
      } catch (e) {}
    },
    destroyThumbPlayer(fileId) {
      const data = this.thumbPlayers[fileId]
      if (!data) return
      if (data.recordedHls) data.recordedHls.destroy()
      if (data.player) {
        data.player.pause()
        data.player.removeAttribute('src')
        data.player.load()
      }
      delete this.thumbPlayers[fileId]
    },
    destroyThumbPlayers() {
      Object.keys(this.thumbPlayers).forEach(id => this.destroyThumbPlayer(id))
    },
    destroyDetailPlayer() {
      if (this.detailHls) {
        this.detailHls.destroy()
        this.detailHls = null
      }
      const player = this.$refs.detailPlayer
      if (player) {
        player.pause()
        player.removeAttribute('src')
        player.load()
      }
    },
    getCameraName(robotId, deviceId) {
      return this.robotBaseInfo?.[robotId]?.cameras?.find(item => item.deviceId === deviceId)?.name || ''
    },
    getListTitle(item) {
      return this.getCameraName(item.robotId, item.deviceId || item.cameraId)
        || item.fileName
        || '未命名文件'
    },
    getLocationText(item) {
      const robot = this.robotBaseInfo?.[item.robotId]
      return robot?.location?.address
        || robot?.locationName
        || item.location?.address
        || '-'
    },
    formatDateTime(val) {
      if (!val) return '-'
      const d = new Date(val)
      if (Number.isNaN(d.getTime())) {
        return String(val).replace('T', ' ').replace(/-/g, '.').slice(0, 19)
      }
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}  ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    async download() {
      if (!this.details.fileId) return
      try {
        const res = await fileDownloadUrl(this.details.fileId)
        const url = res?.downloadUrl || res?.url || this.details.customUrl
        if (!url) {
          this.$message.error('下载地址获取失败')
          return
        }
        const link = document.createElement('a')
        link.href = url
        link.download = this.details.fileName || `${Date.now()}.${this.isImage ? 'jpg' : 'mp4'}`
        link.target = '_blank'
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
      } catch (e) {
        try {
          const link = document.createElement('a')
          link.href = this.details.customUrl
          link.download = this.details.fileName || `${Date.now()}.jpg`
          link.target = '_blank'
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
        } catch (err) {
          this.$message.error('下载失败')
        }
      }
    },
    async handleDelete(item) {
      if (!item?.fileId) return
      try {
        await this.$secondaryConfirm({
          title: '删除',
          message: '记录删除后不可恢复，确认执行删除操作?',
          confirmText: '确认',
          cancelText: '取消',
          onConfirm: async () => {
            await deleteFile(item.fileId)
          }
        })
        await this.afterDelete(item)
      } catch (error) {
        // 用户取消或删除失败
      }
    },
    async afterDelete(item) {
      const deletedId = item?.fileId
      const keepId = this.selectedId === deletedId ? '' : this.selectedId
      await this.loadList()
      this.$emit('deleted', item)
      const next = (keepId && this.filteredList.find(row => row.fileId === keepId)) || this.filteredList[0]
      if (next) {
        await this.selectItem(next)
      } else {
        this.destroyDetailPlayer()
        this.details = {}
        this.selectedId = ''
      }
      if (!this.isImage) {
        this.$nextTick(() => this.bindListThumbs())
      }
    },
    close() {
      this.imageLoadSeq += 1
      this.destroyOwnedImageObjectUrls()
      this.destroyDetailPlayer()
      this.destroyThumbPlayers()
      this.dialogVisible = false
      this.details = {}
      this.listData = []
      this.selectedId = ''
      this.searchType = 'keyword'
      this.searchValue = ''
      this.dateValue = []
      this.outerTabIndex = 0
      this.simpleMode = false
    }
  }
}
</script>

<style lang="scss" scoped>
.record-detail-dialog {
  ::v-deep .el-dialog {
    position: unset !important;
    margin-top: 0 !important;
  }
}
.multimedia-detail-container {
  .box {
    width: auto !important;
  }
  .list-panel.wp294 {
    flex-shrink: 0;
  }
  .preview-box {
    position: relative;
    border: 0.5px solid #1665A2;
    background: #001D46;
    overflow: hidden;
    img,
    video {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }
    .img-b img {
      pointer-events: none;
    }
    .download {
      position: absolute;
      top: 10px;
      right: 10px;
      display: none;
      padding: 6px 20px;
      color: #fff;
      font-size: 12px;
      line-height: normal;
      border-radius: 4px;
      background: rgba(0, 0, 0, 0.6);
      cursor: pointer;
      z-index: 2;
    }
    &:hover .download {
      display: block;
    }
  }
  .filter-panel {
    .combined-filter {
      height: 30px;
      border-radius: 4px;
      border: 1px solid #374E69;
      background: #111B2A;
      overflow: hidden;
    .type-part {
      flex-shrink: 0;
      width: 90px;
      height: 100%;
      border-right: 1px solid #374E69;
      ::v-deep .el-select {
        width: 100%;
        .el-input__inner {
          height: 30px;
          line-height: 30px;
          padding: 0 36px 0 10px;
          border: none;
          border-radius: 0;
          background: transparent;
          font-size: 14px;
          color: #8897AB;
        }
        .el-input__suffix {
          right: 10px;
          .el-select__caret {
            color: #8897AB;
            font-size: 16px;
          }
        }
        .el-input__icon {
          line-height: 30px;
        }
      }
    }
    .value-part {
      min-width: 0;
      height: 100%;
      ::v-deep .el-input {
        .el-input__prefix {
          left: 8px;
          color: #8897AB;
          line-height: 30px;
        }
        .el-input__inner {
          height: 30px;
          padding: 0 24px 0 30px;
          border: none;
          border-radius: 0;
          background: transparent;
          color: #fff;
          &::placeholder {
            color: #8897AB;
            font-size: 14px;
          }
        }
        .el-input__suffix {
          right: 2px;
          color: #8897AB;
        }
        .el-input__icon {
          line-height: 30px;
        }
      }
      ::v-deep .el-date-editor {
        width: 100%;
        height: 30px;
        padding: 0 8px;
        border: none;
        border-radius: 0;
        background: transparent;
        box-shadow: none;
        .el-range-input {
          background: transparent;
          color: #fff;
          font-size: 14px;
          &::placeholder {
            color: #8897AB;
          }
        }
        .el-range-separator {
          color: #8897AB;
          line-height: 30px;
          width: 16px;
          padding: 0;
        }
        .el-range__icon,
        .el-range__close-icon {
          line-height: 30px;
          color: #8897AB;
        }
      }
    }
    }
  }
  .list-box {
    position: relative;
    overflow: hidden auto;
    &::-webkit-scrollbar { width: 4px; height: 4px; }
    &::-webkit-scrollbar-track { background: #13243A; border-radius: 0; }
    &::-webkit-scrollbar-thumb { background: #223C5F; border-radius: 100px; border: 2px solid #223C5F; }
    .item {
      border-radius: 4px;
      background: #102036;
      cursor: pointer;
      border: 1px solid #102036;
      & + .item { margin-top: 10px; }
      .img {
        width: 112px;
        height: 63px;
        flex-shrink: 0;
        border-radius: 4px;
        overflow: hidden;
        background: #3C4656;
        img, video { width: 100%; height: 100%; object-fit: cover; pointer-events: none; }
      }
      .info-right { min-width: 0; }
      .date, .info, .address { font-size: 12px; line-height: 16px; }
      .date { color: #92A0B6; }
      .info { color: #fff; max-width: 110px; }
      .address {
        color: #92A0B6;
        .svg-icon { font-size: 12px; color: #92A0B6; }
        span { max-width: 110px; }
      }
      .delete-btn {
        flex-shrink: 0; color: #3C4656; font-size: 16px; line-height: 1; cursor: pointer;
        &:hover { color: #2A86F3; }
      }
      &:hover { border: 1px solid #159AFF; background: #0B2951; }
      &.selected {
        border: 1px solid #159AFF; background: #0B2951;
        .delete-btn { color: #2A86F3; }
      }
    }
  }
  .empty-text { color: rgba(255, 255, 255, 0.5); font-size: 14px; }
  .details {
    .item {
      display: flex; align-items: center; color: #D0DEEE; font-size: 14px; line-height: 18px;
      .name { color: #D0DEEE; flex-shrink: 0; }
      .value { color: #FFF; }
    }
  }
}
.text-ellipsis { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
