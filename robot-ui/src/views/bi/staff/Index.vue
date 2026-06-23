<template>
  <div style="height: calc(100% - 79px)">
    <Header activeHead="staff" />
    <Side />

    <div class="flex" style="position: absolute; top: 100px; left: 100px;">
      <div class="custom-video-div">
        <div class="flx-justify-between">
          <div class="card-title mb10 wp424">
            <div class="text">
              多设备实时画面
            </div>
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
            <!-- <span @click="$refs.videoToolRef.toggleFullscreen()" class="ml10"> -->
            <span class="ml10">
              <svg-icon icon-class="fullscreen1" />
            </span>
          </div>
        </div>
        <div class="list hp768 flx-justify-between flex-wrap mt10 pr26 pl26" style="width: 1364px;">
          <div class="horn top-left"></div>
          <div class="horn top-right"></div>
          <div class="horn bottom-left"></div>
          <div class="horn bottom-right"></div>
          <!-- style="width: calc(100% - 42px); height: calc(100% - 30px);" 1312 738 -->
          <div v-if="splitType === 6" class="pr5 pl5">
            <div class="d-flex">
              <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_1')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="1" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="six-1" />
              <div class="ml26">
                <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_2')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="2" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="six-2" />
                <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_3')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="3" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="mt16 six-3" />
              </div>
            </div>
            <div class="d-flex mt20">
              <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_4')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="4" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="six-4" />
              <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_5')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="5" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="ml28 six-5" />
              <VideoBox @toggleFullscreen="toggleFullscreen" @onAlgoChange="onAlgoChange" @playPauseVideo="playPauseVideo('slot_6')" @test="test" @removeVideo="handleRemoveVideo" @refreshVideo="handleRefreshVideo" :videoIndex="6" :prefixId="prefixId" :splitType="splitType" :ZQL_videosInfos="ZQL_videosInfos" className="ml26 six-6" />
            </div>
          </div>
          <template v-else>
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
      <el-radio-group v-if="splitType === 1" @change="onSingleDeviceChange" v-model="singleId" class="ml10 custom-radio-group flex with-border vertical ml20">
        <el-radio v-for="robot in robots" :key="robot.robotId" :label="robot.robotId" class="flx-align-center">{{ robot.name }}</el-radio>
      </el-radio-group>
      <div class="ml10">
        <div
          v-for="(robot, index) in robots"
          :key="robot.robotId"
          :draggable="!isCheckRobot(robot.robotId)"
          @dragstart="onDragStart($event, robot)"
          @dragend="onDragEnd"
          @click="handleClickRobot(robot)"
          class="flex"
          :style="{ color: isCheckRobot(robot.robotId) ? '#00f' : '#ccc', cursor: !isCheckRobot(robot.robotId) ? 'grab' : 'default', marginTop: index === 0 ? 0 : '10px' }">
          {{ robot.name }}
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import Header from '../components/Header.vue';
import Side from '../components/Side.vue'
// import video from '../js/mixins/video.js'
import canvasUtil from '../js/mixins/box-canvas.js'
import VideoBox from './VideoBox.vue';
import { mapActions, mapState } from 'vuex';
  export default {
    name: 'BiStaff',
    mixins: [canvasUtil],
    components: { Header, Side, VideoBox },
    props: {
      prefixId: {
        type: String,
        default: 'test-video-div'
      }
    },
    computed: {
      ...mapState('dragVideo', ['dropResult']),
      // 获取基础信息
      robots() {
        return this.$store.getters['websocketRobot/getRobots'];
      },
    },
    data() {
      return {
        splitType: 4,
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

        // ===================================================================================
        // 修改为动态键值对形式，不再限制为4个
        ZQL_videosInfos: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的视频信息
        ZQL_playingSource: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的摄像头ID  { 'slot_1': 1, 'slot_2': 2 }
        ZQL_sources: {},
        statusArr: {}, // 改为对象形式，键名为'slot_1'...
        sourceceList: []
      }
    },
    async mounted() {
      // this.getSources();
      // this.sysArgs();

      // 初始化：不默认填充任何设备
      this.singleId = null;
      this.checkedIds = [];
      this.lastCheckedIds = [];
      this.slotDevices = new Array(this.splitType).fill(null);
    },
    methods: {
      ...mapActions('dragVideo', ['dragStart']),
      ...mapActions('websocketRobot', ['loadRobots', 'startCamera', 'stopCamera']),
      getRef(refName) {
        return this.$refs?.[refName]?.[0] || {}
      },
      async test(data) {
        const emptyKey = data.index
        if (this.ZQL_playingSource[emptyKey]) {
          console.log('已存在，关闭视频并覆盖', this.checkedIds);
          this.checkedIds.splice(this.checkedIds.indexOf(this.ZQL_playingSource[emptyKey]), 1)
          // 清空视频数据
          await this.playPauseVideo(emptyKey)
          this.clearSlot(emptyKey)
        }
        // 填充 放入设备
        const robot = this.robots.find(d => d.robotId === data.data.robotId);
        if (!robot) return;
        // 拖拽默认第一个摄像头
        const camera = data.data.key ? data.data : robot.cameras[0]
        this.$set(this.ZQL_playingSource, emptyKey, camera.key);
        this.$set(this.ZQL_videosInfos, emptyKey, { robot, ...camera });
        if (!this.checkedIds.includes(camera.key)) {
          this.checkedIds.push(camera.key)
        }
        await this.startCamera({ robot, camera })
        // 订阅视频流
        // this.subscribeLive(data.data.robotId, emptyKey);
        
        // 触发设备变化事件
        // this.$emit('device-change', { key: emptyKey, deviceId: data.data.robotId });
      },
      playPauseVideo(key) {     
        const camera = this.ZQL_videosInfos[key];
        if (!camera) return;
        // 获取视频元素
        const videoElement = document.getElementById(camera.key);
        videoElement.paused ? this.resumeVideo(videoElement, this.ZQL_videosInfos[key]) : this.pauseVideo(videoElement)
        // this.ZQL_videosInfos[key].status ? this.stopVideo(key) : this.startCamera(this.ZQL_videosInfos[key].robot, this.ZQL_videosInfos[key])
      },
      pauseVideo(videoElement) {
        if (videoElement) {
          // 暂停视频播放，但保留当前画面，短暂暂停
          videoElement.pause();
        }
        // // 通过 LiveKit API 取消视频轨道订阅，画面会变黑，无法保留画面
        // if (camera.room) {
        //   camera.room.localParticipant?.unpublishTrack(videoTrack);
        //   // 或者取消订阅远端轨道
        //   camera.room.remoteParticipants.forEach(participant => {
        //     participant.videoTracks.forEach(track => {
        //       track.unsubscribe();
        //     });
        //   });
        // }
        // 更新状态标记为已暂停
        // this.$set(this.ZQL_videosInfos, key, { ...camera, status: '' });
      },
      resumeVideo(videoElement, camera) {
        if (videoElement) {
          // 继续播放视频
          videoElement.play().catch(err => {
            console.error('播放视频失败:', err);
            // 如果直接播放失败，尝试重新建立连接
            if (camera.robot) {
              this.startCamera({ robot: camera.robot, camera });
            }
          });
        } else {
          // 如果视频元素不存在，重新启动摄像头
          if (camera.robot) {
            this.startCamera({ robot: camera.robot, camera });
          }
        }
      },
      // 刷新视频
      refreshVideo(key) {
        const videoInfo = this.ZQL_videosInfos[key];
        if (!videoInfo || !videoInfo.robot) return;
        
        // 获取摄像头信息
        const camera = this.ZQL_videosInfos[key];
        
        // 标记为重新加载中
        this.$set(this.ZQL_videosInfos, key, { ...videoInfo, loading: true });
        
        // 如果有正在运行的会话，先停止它
        if (camera.session) {
          this.stopCamera(camera).then(() => {
            // 停止成功后重新启动
            this.startCamera({ robot: videoInfo.robot, cameras: videoInfo });
          }).catch(() => {
            // 即使停止失败也尝试重新启动
            this.startCamera({ robot: videoInfo.robot, cameras: videoInfo });
          });
        } else {
          // 如果没有会话，直接启动
          this.startCamera({ robot: videoInfo.robot, cameras: videoInfo });
        }
      },
      // 处理视频删除
      async handleRemoveVideo(key) {
        const camera = this.ZQL_videosInfos[key];
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
      // 分屏切换时（已经在watch中清空，但需要额外处理一些边界）
      onSplitChange(val) {
        this.splitType = val;
        // watch已经处理，但需要额外处理全屏状态
        this.fullscreenIndex = null;
      },
      // 拖拽开始: 将任务数据存入 dataTransfer
      onDragStart(event, info) {        
        // 设置拖拽数据 - 使用纯文本格式
        event.dataTransfer.setData('text/plain', JSON.stringify({ data: info, componentId: 'videoDemoComponent' }));
        // 设置拖拽效果
        event.dataTransfer.effectAllowed = 'move';
        // 添加拖拽中的样式
        event.target.classList.add('dragging');
        this.dragStart({ data: info, componentId: 'videoDemoComponent' })
      },
      onDragEnd(event) {
        // console.log('222222', event);
        // 移除拖拽样式
        event.target.classList.remove('dragging');
      },
      isCheckRobot(robotId) {
        return this.robots.find((robot) => robot.robotId === robotId)?.cameras?.filter((camera) => this.checkedIds.includes(camera.key)).length || 0
      },
      async handleClickRobot(robot) {
        if (this.isCheckRobot(robot.robotId)) {
          // 取消选中
          for (const [index, key] of Object.keys(this.ZQL_videosInfos).entries()) {
            const camera = this.ZQL_videosInfos[key];
            if (!camera) return
            await this.stopCamera(camera)
            this.$set(this.ZQL_videosInfos, key, null)
            this.$set(this.ZQL_playingSource, key, null)
            this.checkedIds.splice(this.checkedIds.indexOf(camera.key), 1);
          }
          console.log('this.checkedIds', this.checkedIds);
        } else {
          // TODO: 处理点击机器人时的逻辑（选中/取消选中）
          for (const [index, key] of Object.keys(this.ZQL_videosInfos).entries()) {
            await this.test({ index: key,  data: robot.cameras?.[index] || {} });
          }
        }
      },
      onAlgoChange(data) {},
      toggleFullscreen(data) {},
      initSlots(splitType) {
        const newVideosInfos = {};
        const newPlayingSource = {};
        const newStatusArr = {};
        
        for (let i = 1; i <= splitType; i++) {
          const key = 'slot_' + i;
          newVideosInfos[key] = null;
          newPlayingSource[key] = null;
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
        handler(newVal, oldVal) {
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
          this.initSlots(newVal);
        },
        immediate: true
      },
      // 监听robots变化，同步更新ZQL_videosInfos中的数据
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
                  // console.log('camera--------------------------------', camera.status, camera);
                  // 更新视频信息，保持与原数据同步
                  this.$set(this.ZQL_videosInfos, key, { robot, ...videoInfo, ...camera });
                }
              }
            }
          });
        },
        deep: true,
        immediate: true
      }
    },
  }
</script>
