<template>
<div class="custom-video-div" :class="[prefixId, { 'is-page-fullscreen': isPageFullscreen }]">
  <div class="flx-justify-between">
    <div class="card-title hp36 flx-justify-between pr26" :class="cardTitleClass" style="line-height: 36px;">
      <div class="text"> 
        多设备实时画面
      </div>
      <div class="split-screen flx-align-center">
        <span @click="onSplitChange(1)" :class="{ 'is-active': splitType === 1 }">
          <svg-icon icon-class="screen-split-1" />
        </span>
        <span @click="onSplitChange(4)" class="ml10" :class="{ 'is-active': splitType === 4 }">
          <svg-icon icon-class="screen-split-4" />
        </span>
        <span @click="onSplitChange(6)" class="ml10" :class="{ 'is-active': splitType === 6 }">
          <svg-icon icon-class="screen-split-6" />
        </span>
        <span @click="onSplitChange(9)" class="ml10" :class="{ 'is-active': splitType === 9 }">
          <svg-icon icon-class="screen-split-9" />
        </span>
        <span class="ml10" @click="toggleFullscreen1">
          <svg-icon :icon-class="isPageFullscreen ? 'close-fullscreen' : 'fullscreen1'" />
        </span>
      </div>
    </div>
  </div>
  <div
    class="list hp759 flx-justify-between flex-wrap mt9 w100"
    style="width: 1364px;"
    :class="{
      'pr13 pl13 pt13 pb13': splitType === 4 && prefixId === 'test-video-div-second',
      'pr18 pl18': splitType === 6 && prefixId === 'test-video-div-second',
      'pt13 pb13': splitType === 9 && prefixId === 'test-video-div-second',
      'pr26 pl26': !(splitType === 4 && prefixId === 'test-video-div-second'),
      'pr55 pl47': prefixId === 'test-video-div-first'
    }"
  >
    <div class="horn top-left"></div>
    <div class="horn top-right"></div>
    <div class="horn bottom-left"></div>
    <div class="horn bottom-right"></div>
    <!-- style="width: calc(100% - 42px); height: calc(100% - 30px);" 1312 738 -->
    <div v-if="splitType === 6" class="pr5 pl5">
      <div class="d-flex">
        <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_1')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="1" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="six-1" />
        <!-- <VideoBox :videoIndex="0" :prefixId="prefixId" :splitType="splitType" :slotDevices="slotDevices" @updateSlot="updateSlot" className="six-1" /> -->
        <div class="ml26">
          <div
            :draggable="ZQL_videosInfos['slot_2']"
            @dragstart="onDragStart($event, ZQL_videosInfos['slot_2'].robot, 'smallVideo', 'slot_2')"
            @dragend="onDragEnd"
            :style="{ cursor: 'grab' }"
          >
            <VideoBox
              @toggleFullscreen="toggleFullscreen"
              @onAlgoChange="onAlgoChange"
              @playPauseVideo="playPauseVideo('slot_2')"
              @test="test"
              @removeVideo="handleRemoveVideo"
              @refreshVideo="handleRefreshVideo"
              :videoIndex="2"
              :prefixId="prefixId"
              :splitType="splitType"
              :ZQL_videosInfos="ZQL_videosInfos"
              className="six-2"
            />
          </div>
          <div
            :draggable="ZQL_videosInfos['slot_3']"
            @dragstart="onDragStart($event, ZQL_videosInfos['slot_3'].robot, 'smallVideo', 'slot_3')"
            @dragend="onDragEnd"
            :style="{ cursor: 'grab' }"
          >
            <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_3')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="3" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="mt16 six-3" />
          </div>
        </div>
      </div>
      <div class="d-flex mt20">
        <div
          :draggable="ZQL_videosInfos['slot_4']"
          @dragstart="onDragStart($event, ZQL_videosInfos['slot_4'].robot, 'smallVideo', 'slot_4')"
          @dragend="onDragEnd"
          :style="{ cursor: 'grab' }"
        >
          <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_4')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="4" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="six-4" />
        </div>
        <div
          :draggable="ZQL_videosInfos['slot_5']"
          @dragstart="onDragStart($event, ZQL_videosInfos['slot_5'].robot, 'smallVideo', 'slot_5')"
          @dragend="onDragEnd"
          :style="{ cursor: 'grab' }"
        >
          <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_5')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="5" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="ml28 six-5" />
        </div>
        <div
          :draggable="ZQL_videosInfos['slot_6']"
          @dragstart="onDragStart($event, ZQL_videosInfos['slot_6'].robot, 'smallVideo', 'slot_6')"
          @dragend="onDragEnd"
          :style="{ cursor: 'grab' }"
          >
          <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_6')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="6" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="ml26 six-6" />
        </div>
      </div>
    </div>
    <template v-else>
      <!-- <VideoBox
        v-for="index in splitType"
        :key="index"
        :videoIndex="index"
        :prefixId="prefixId"
        :splitType="splitType"
        :ZQL_videosInfos="ZQL_videosInfos"
        :slotDevices="slotDevices"
        @updateSlot="updateSlot"
      /> -->
      <VideoBox
        @toggleFullscreen="toggleFullscreen"
        @onAlgoChange="onAlgoChange"
        @playPauseVideo="playPauseVideo(`slot_${index}`)"
        @test="test"
        @removeVideo="handleRemoveVideo"
        @refreshVideo="handleRefreshVideo"
        v-for="index in splitType"
        :key="index"
        :videoIndex="index"
        :prefixId="prefixId"
        :splitType="splitType"
        :ZQL_videosInfos="ZQL_videosInfos"
      />
    </template>
  </div>
</div>
</template>

<script>
// import video from '../../../js/mixins/video.js'
import canvasUtil from '../../../js/mixins/box-canvas.js'
import VideoBox from './../../../staff/VideoBox.vue';
import { mapActions, mapState } from 'vuex';
import { onDragStart, onDragEnd } from '@/store/modules/dragVideo.js';
export default {
  name: 'LeftVideo',
  mixins: [canvasUtil],
  components: { VideoBox },
  props: {
    prefixId: {
      type: String,
      default: 'test-video-div'
    },
    cardTitleClass: {
      type: String,
      default: 'title-1364-37'
    }
  },
  data() {
    return {
      algInfoList: [
        // {
        //   // currentAlg: 0,
        //   // currentAlgLabel: '人脸识别',
        //   currentAlg: '',
        //   currentAlgLabel: '',
        //   algTypes: [
        //     // {
        //     //   label: '人脸识别',
        //     //   value: 0
        //     // },
        //     // {
        //     //   label: '车辆识别',
        //     //   value: 1
        //     // }
        //   ]
        // }
      ],
      singleId: null,         // 一分屏选中的设备id，初始无默认填充
      checkedIds: [],          // 多分屏选中的设备id数组，初始为空
      slotDevices: [],                // 长度等于splitType，存储每个格子的设备信息或null
      lastCheckedIds: [],            // 记录上一次的多选值，用于对比变化
      fullscreenIndex: null,            // 当前全屏的格子索引，null表示无全屏
      isPageFullscreen: false,

      // ===================================================================================
      // 修改为动态键值对形式，不再限制为4个
      ZQL_videosInfos: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的视频信息
      ZQL_playingSource: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的摄像头ID  { 'slot_1': 1, 'slot_2': 2 }
      ZQL_sources: {},
      statusArr: {}, // 改为对象形式，键名为'slot_1'...
      sourceceList: [],
      manualChange: false
    }
  },
  computed: {
    ...mapState('dragVideo', ['dropResult', 'splitType']),
    ...mapState('websocketRobot', ['robots', 'cameras']),
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    },
  },
  async mounted() {
    // 初始化：不默认填充任何设备
    this.singleId = null;
    this.checkedIds = [];
    this.lastCheckedIds = [];
    this.slotDevices = new Array(this.splitType).fill(null);
    // 从 store 加载机器人列表

    this.setPrefixId(this.prefixId)
    document.addEventListener('fullscreenchange', this.updatePageFullscreenState)
    document.addEventListener('webkitfullscreenchange', this.updatePageFullscreenState)
    document.addEventListener('mozfullscreenchange', this.updatePageFullscreenState)
    document.addEventListener('MSFullscreenChange', this.updatePageFullscreenState)
  },
  beforeDestroy() {
    document.removeEventListener('fullscreenchange', this.updatePageFullscreenState)
    document.removeEventListener('webkitfullscreenchange', this.updatePageFullscreenState)
    document.removeEventListener('mozfullscreenchange', this.updatePageFullscreenState)
    document.removeEventListener('MSFullscreenChange', this.updatePageFullscreenState)
  },
  methods: {
    ...mapActions('dragVideo', ['resetDrag', 'setSplitType']),
    ...mapActions('websocketRobot', ['startCamera', 'stopCamera', 'restartCamera', 'setPrefixId']),
    onDragStart,
    onDragEnd,
    updateSlot(data) {
      this.slotDevices[data.index] = data.data
    },
    getRef(refName) {
      return this.$refs?.[refName]?.[0] || {}
    },
    async start(robot, data) {
      // console.log('start-----------------------------------');
      
      const emptyIndex = data.index
      if (!robot) return;
      const camera = data.data
      this.$set(this.ZQL_playingSource, emptyIndex, camera.key);
      this.$set(this.ZQL_videosInfos, emptyIndex, { robot, ...camera });
      // console.log('ZQL_playingSource', this.ZQL_playingSource);
      await this.startCamera({ robot, camera })
    },
    rebindCameraTracks(cameras) {
      this.$nextTick(() => {
        const cameraList = cameras || []
        cameraList.forEach(camera => {
          if (!camera) return
          const video = document.getElementById(this.prefixId + camera.key)
          const audio = document.getElementById(this.prefixId + camera.key + '-audio')
          if (video) {
            if (!camera.remoteVideoTrack) {
              video.srcObject = null
              video.load()
            } else {
              camera.remoteVideoTrack.attach(video)
            }
          }
          if (audio) {
            if (!camera.remoteAudioTrack) {
              audio.srcObject = null
            } else {
              camera.remoteAudioTrack.attach(audio)
            }
          }
        })
      })
    },
    async test(data) {
      console.log('-----------------------', data, this.ZQL_videosInfos);
      let emptyKey = data.index
      // 填充 放入设备
      const robot = this.robots.find(d => d.robotId === data.data.robotId);
      // 拖拽默认第一个摄像头或者主体摄像头
      const cameraObj = data?.data?.key ? data.data : robot.cameras.filter(c => c.groupType === 'body')[0] || robot.cameras[0]
      const camera = this.cameras?.[cameraObj.key] || cameraObj
      if (this.splitType === 6 && data.componentId === 'smallVideo') {
        const existObj = Object.assign({}, this.ZQL_videosInfos[emptyKey])
        const sourceObj = Object.assign({}, this.ZQL_videosInfos[data.slotKey])
        // console.log('11111111', this.ZQL_playingSource[emptyKey], this.ZQL_playingSource[data.slotKey]);
        if (this.ZQL_playingSource[emptyKey]) {
          // console.log(1, emptyKey, data.slotKey);
          
          this.$set(this.ZQL_videosInfos, data.slotKey, existObj)
          this.$set(this.ZQL_playingSource, data.slotKey, existObj.key)
          const existCamera = existObj.robot ? existObj.robot : robot.robot.cameras.filter(c => c.groupType === 'body')[0] || robot.cameras[0]
          // this.restartCamera(this.cameras?.[this.ZQL_playingSource[emptyKey]] || this.ZQL_videosInfos[emptyKey])
        } else {
          this.$set(this.ZQL_videosInfos, data.slotKey, null)
          this.$set(this.ZQL_playingSource, data.slotKey, null)
          // console.log(2, emptyKey, data.slotKey);
        }
          // console.log(3, emptyKey, data.slotKey);
        this.$set(this.ZQL_videosInfos, emptyKey, sourceObj)
        this.$set(this.ZQL_playingSource, emptyKey, sourceObj.key)
        // this.restartCamera(camera)
        const key1 = this.ZQL_playingSource[emptyKey]
        const key2 = this.ZQL_playingSource[data.slotKey]        
        this.rebindCameraTracks([this.cameras?.[key1], this.cameras?.[key2]])
        return
      }
      if (this.splitType === 1) {
        if (this.ZQL_playingSource['slot_1']) {
          // console.log('--------------------------------------------------------已存在', camera);
          this.checkedIds.splice(this.checkedIds.indexOf(this.ZQL_playingSource['slot_1']), 1)
          // 清空视频数据
          await this.stopCamera(this.ZQL_videosInfos['slot_1'])
          if (this.ZQL_playingSource['slot_1'] !== camera.key) {
            console.log(7);
            
            await this.start(robot, { index: 'slot_1', data: camera })
          } else {
            this.clearSlot('slot_1')
            console.log(8);
          }
        } else {
          await this.start(robot, { index: 'slot_1', data: camera })
        }
      } else {
        console.log(5);
        
        let hasPlayed = false;
        for (const key of Object.keys(this.ZQL_playingSource)) {
          // 优先在正在播放此摄像头的槽位重新播放
          if (this.ZQL_playingSource[key] === camera.key) {
            // console.log(1);
            
            await this.stopCamera(camera)
            this.$set(this.ZQL_videosInfos, key, null)
            this.$set(this.ZQL_playingSource, key, null)
            hasPlayed = true;
          }
        };
        
        // 如果没有找到正在播放的槽位，在第一个空位播放
        if (!hasPlayed) {
          // console.log(2);
          emptyKey = emptyKey || Object.keys(this.ZQL_playingSource).find(
            key => this.ZQL_playingSource[key] === null
          ) || 'slot_1';
          
          if (emptyKey) {
            await this.start(robot, { index: emptyKey, data: camera });
          }
        }
      }
    },
    playPauseVideo(key) {     
      const camera = this.ZQL_videosInfos[key];
      if (!camera) return;
      // 获取视频元素
      const videoElement = document.getElementById(`${this.prefixId}${camera.key}`);
      
      // 更新响应式播放状态
      const isPaused = videoElement.paused;
      this.$set(this.ZQL_videosInfos[key], 'isPaused', !isPaused);
      isPaused ? this.resumeVideo(videoElement, this.ZQL_videosInfos[key]) : this.pauseVideo(videoElement)
      // this.ZQL_videosInfos[key].status ? this.stopVideo(key) : this.startCamera(this.ZQL_videosInfos[key].robot, this.ZQL_videosInfos[key])
    },
    pauseVideo(videoElement) {
      if (videoElement) {
        // 暂停视频播放，但保留当前画面，短暂暂停
        videoElement.pause();
      }
    },
    resumeVideo(videoElement, camera) {
      if (videoElement) {
        // 继续播放视频
        videoElement.play().catch(err => {
          console.error('播放视频失败:', err);
          // 如果直接播放失败，尝试重新建立连接
          if (camera.robot) {
            this.startCamera({ robot: camera.robot, camera: this.cameras?.[camera.key] || camera });
          }
        });
      } else {
        // 如果视频元素不存在，重新启动摄像头
        if (camera.robot) {
          this.startCamera({ robot: camera.robot, camera: this.cameras?.[camera.key] || camera });
        }
      }
    },
    // 刷新视频
    refreshVideo(key) {
      console.log('刷新视频===============', key, this.ZQL_videosInfos[key]);
      
      const videoInfo = this.ZQL_videosInfos[key];
      if (!videoInfo || !videoInfo.robot) return;
      
      // 获取摄像头信息
      const camera = this.cameras?.[videoInfo.key] || this.ZQL_videosInfos[key];
      
      // 标记为重新加载中
      this.$set(this.ZQL_videosInfos, key, { ...camera, loading: true });
      
      // 如果有正在运行的会话，先停止它
      if (camera.session) {
        this.stopCamera(camera).then(() => {
          // 停止成功后重新启动
          this.startCamera({ robot: videoInfo.robot, camera });
        }).catch(() => {
          // 即使停止失败也尝试重新启动
          this.startCamera({ robot: videoInfo.robot, camera });
        });
      } else {
        // 如果没有会话，直接启动
        this.startCamera({ robot: videoInfo.robot, camera });
      }
    },
    // 处理视频删除
    async handleRemoveVideo(key) {
      console.log(key, this.ZQL_playingSource, this.ZQL_videosInfos);
      
      const camera = this.cameras?.[this.ZQL_playingSource[key]] || {};
      if (camera) {
        // 从选中设备中移除
        this.checkedIds.splice(this.checkedIds.indexOf(camera.key), 1);
      }
      await this.stopCamera(camera)
      // 调用mixin中的删除方法
      // this.removeVideo(key);
      this.$set(this.ZQL_videosInfos, key, null)
      this.$set(this.ZQL_playingSource, key, null)
    },
    // 处理刷新视频
    handleRefreshVideo(key) {
      this.refreshVideo(key);
    },
    updateData(robots) {
      Object.keys(this.ZQL_videosInfos).forEach(key => {
        const videoInfo = this.ZQL_videosInfos[key];
        if (videoInfo && videoInfo.robotId) {
          // 找到对应的机器人
          const robot = robots.find(r => r.robotId === videoInfo.robotId);
          if (robot) {
            // 找到对应的摄像头
            const camera = robot.cameras.find(c => c.key === videoInfo.key);
            if (camera) {
              // console.log('camera--------------------------------', camera.status, camera);
              // 更新视频信息，保持与原数据同步
              this.$set(this.ZQL_videosInfos, key, { robot, ...videoInfo, ...camera });
            }
          }
        }
      });
    },
    // 分屏切换时（已经在watch中清空，但需要额外处理一些边界）
    onSplitChange(val) {
      if (this.splitType === val) {
        this.rebindCameraTracks(this.currentVisibleCameras())
        return
      }
      const playingBeforeChange = this.orderedPlayingVideoInfos()
      this.manualChange = true
      this.setSplitType(val);
      this.fullscreenIndex = null;
      this.$nextTick(() => {
        this.applySplitVideoChannels(playingBeforeChange, val)
      })
    },
    onSingleDeviceChange1(e) {
      if (this.splitType !== 1) return;
      const dev = this.deviceList.find(d => d.id === e);
      // 创建新的slotDevices对象
      const newSlotDevices = {};
      if (!dev) {
        // 如果设备无效，清空格子
        newSlotDevices['slot_1'] = null;
        return;
      }
      // 创建设备对象，currentAlgo 默认设为 null（不选中任何算法）
      newSlotDevices['slot_1'] = {
        ...dev,
        currentAlg: null,  // 默认不选中任何算法
        currentAlgName: null,
        videoSrc: ''
      };
      this.slotDevices = newSlotDevices;
    },
    // 多分屏：复选框变化 - 实现取消勾选清空对应格子，勾选新设备填入第一个空位
    onMultiDeviceChange1(newCheckedIds) {
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
          if (newSlotDevices[key] && newSlotDevices[key].id === remId) {
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
            ...dev,
            currentAlg: null,  // 默认不选中任何算法
            currentAlgName: null,
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
    revertObjToArr(obj) {
      return Object.values(obj)
    },
    // 算法切换
    onAlgoChange(data) {
      const { deviceItem, alg } = data
      deviceItem.currentAlg = alg.alg_type;
      deviceItem.currentAlgName = alg.reserved_args.ch_name;
      // console.log(`设备 ${deviceItem.desc} 算法 -> ${alg?.type || '未选择'}`);
    },
    toggleFullscreen(data) {},
    fullscreenElement() {
      return document.fullscreenElement || document.webkitFullscreenElement || document.mozFullScreenElement || document.msFullscreenElement
    },
    requestFullscreen(element) {
      const request = element.requestFullscreen || element.webkitRequestFullscreen || element.mozRequestFullScreen || element.msRequestFullscreen
      if (request) return request.call(element)
      return Promise.resolve()
    },
    exitFullscreen() {
      const exit = document.exitFullscreen || document.webkitExitFullscreen || document.mozCancelFullScreen || document.msExitFullscreen
      if (exit) return exit.call(document)
      return Promise.resolve()
    },
    async toggleFullscreen1() {
      const target = this.$el && this.$el.classList && this.$el.classList.contains('custom-video-div')
        ? this.$el
        : this.$el && this.$el.querySelector('.custom-video-div')
      if (!target) return
      const current = this.fullscreenElement()
      if (current === target) {
        await this.exitFullscreen()
      } else {
        if (current) await this.exitFullscreen()
        await this.requestFullscreen(target)
      }
      this.updatePageFullscreenState()
    },
    updatePageFullscreenState() {
      const target = this.$el && this.$el.classList && this.$el.classList.contains('custom-video-div')
        ? this.$el
        : this.$el && this.$el.querySelector('.custom-video-div')
      this.isPageFullscreen = !!target && this.fullscreenElement() === target
      if (!this.isPageFullscreen) this.fullscreenIndex = null
    },
    orderedPlayingVideoInfos() {
      return Object.keys(this.ZQL_videosInfos)
        .sort((a, b) => Number(a.replace('slot_', '')) - Number(b.replace('slot_', '')))
        .map(key => this.ZQL_videosInfos[key])
        .filter(Boolean)
    },
    currentVisibleCameras() {
      return Object.values(this.ZQL_videosInfos)
        .filter(Boolean)
        .map(camera => this.cameras?.[camera.key] || camera)
    },
    async applySplitVideoChannels(playingItems, splitType) {
      const nextVideosInfos = {}
      const nextPlayingSource = {}
      const retainedKeys = new Set()
      const items = playingItems || []

      for (let i = 1; i <= splitType; i++) {
        const key = `slot_${i}`
        const item = items[i - 1]
        if (!item) {
          nextVideosInfos[key] = null
          nextPlayingSource[key] = null
          continue
        }
        const camera = this.cameras?.[item.key] || item
        const robot = item.robot || this.robots.find(robotItem => robotItem.robotId === item.robotId)
        nextVideosInfos[key] = { robot, ...item, ...camera }
        nextPlayingSource[key] = camera.key
        retainedKeys.add(camera.key)
      }

      const removedItems = items.slice(splitType)
      for (const item of removedItems) {
        const camera = this.cameras?.[item.key] || item
        if (camera && camera.key && !retainedKeys.has(camera.key)) {
          await this.stopCamera(camera)
        }
      }

      this.ZQL_videosInfos = nextVideosInfos
      this.ZQL_playingSource = nextPlayingSource
      this.checkedIds = Object.values(nextPlayingSource).filter(Boolean)
      this.lastCheckedIds = this.checkedIds.slice()
      this.slotDevices = new Array(splitType).fill(null)
      this.rebindCameraTracks(this.currentVisibleCameras())
    },
    initSlots(splitType) {
      const newVideosInfos = {};
      const newPlayingSource = {};
      const newStatusArr = {};
      
      for (let i = 1; i <= splitType; i++) {
        const key = 'slot_' + i;
        newVideosInfos[key] = this.ZQL_videosInfos?.[key] || null;
        newPlayingSource[key] = this.ZQL_playingSource?.[key] || null;
        newStatusArr[key] = 'off';
        this.clearSlot(key);
      }
      
      this.ZQL_videosInfos = newVideosInfos;
      this.ZQL_playingSource = newPlayingSource;
      this.statusArr = newStatusArr;
      
      // 清空全屏状态
      this.fullscreenKey = null;
      this.isFullscreen = false;
      document.body.style.overflow = '';
    },
    clearSlot(key) {
      // 将对应键的值设为null
      this.$set(this.ZQL_videosInfos, key, null);
      this.$set(this.ZQL_playingSource, key, null);
    },
  },
  watch: {
    // 分屏变化：完全清空所有选择，重置slotDevices为空（全空）
    splitType: {
      async handler(newVal, oldVal) {
        // console.log('=====================splitType=============================', oldVal, newVal);
        
        // 退出全屏
        this.fullscreenIndex = null;
        // 清空所有选中项
        this.singleId = null;
        this.checkedIds = [];
        this.lastCheckedIds = [];
        // // 重新初始化 slotDevices，所有键对应的值都为null
        // const newSlotDevices = {};
        // for (let i = 1; i <= newVal; i++) {
        //     newSlotDevices['slot_' + i] = null;
        // }
        // this.slotDevices = newSlotDevices;
        // // 退出全屏
        // // this.fullscreenKey = null;
        // // this.isFullscreen = false;
        // document.body.style.overflow = '';
        if (oldVal === undefined || !this.manualChange) this.initSlots(newVal);
        this.slotDevices = new Array(this.splitType).fill(null);
        // this.resetDrag({ splitType: this.splitType });
        const playingKeys = Object.values(this.ZQL_playingSource)
        // console.log('playingKeys', playingKeys, this.activeCameras)
        for (const [index, key] of Object.keys(this.activeCameras).entries()) {

          if (!playingKeys.includes(key)) {
            if (this.activeCameras[key]?.camera) {
              console.log('============================================playingKeys========================================================', key);
              await this.stopCamera(this.activeCameras[key].camera);
            }
          }
        }

      },
      // immediate: true
    },
    robots: {
      handler() {
        // 同步ZQL_videosInfos与robots数据
        Object.keys(this.ZQL_videosInfos).forEach(key => {
          const videoInfo = this.ZQL_videosInfos[key];
          if (videoInfo && videoInfo.robotId) {
            // 找到对应的机器人
            const robot = this.robots.find(r => r.robotId === videoInfo.robotId);
            if (robot) {
              // 找到对应的摄像头
              const camera = robot.cameras.find(c => c.key === videoInfo.key);
              if (camera) {
                // console.log('camera--------------------------------', videoInfo.key, camera.status, camera);
                // 更新视频信息，保持与原数据同步
                this.$set(this.ZQL_videosInfos, key, { robot, ...videoInfo, ...camera });
              }
            }
          }
        });
      },
      deep: true,
      immediate: true
    },
    // activeCameras: {
    //   handler(newCameras) {
    //     console.log('==========================newCameras=========================', newCameras);
    //     // 更新视频显示
    //     // this.updateVideoDisplay(newCameras);
    //   },
    //   deep: true
    // }
  },
  async beforeDestroy() {
    // console.info('leafVideo-destroy')
    // for (const [index, key] of Object.keys(this.activeCameras).entries()) {
    //   if (this.activeCameras[key]?.camera) {
    //     await this.stopCamera(this.activeCameras[key].camera);
    //   }
    // }
  }
}
</script>

<style lang="scss" scoped>
.card-title {
  width: 1364px;
}

.custom-video-div.is-page-fullscreen {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #061a33;
  padding: 12px;

  .card-title,
  .list {
    width: 100% !important;
  }

  .list {
    height: calc(100vh - 57px) !important;
  }
}
</style>
