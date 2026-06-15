import { getBoxHostList } from '../../../../api/bi';
import { getDeviceList, getSysArgs, getTunnels, subscribeLiveBySourceId } from '../../../../api/box';
import mqttClient from '../../../../plugins/mqtt-client';
let ZQLGLOBAL = {
  serverIp: '192.168.124.94' ,// 修改成您的盒子aiboxd的IP地址
  // getDevices: `http://192.168.124.94:9091/ks/device`,
  // getSources: `http://192.168.124.94:9091/ks/source`,
  // subscribe: `http://192.168.124.94:9089/ks/stream/live/subscribe`,
  // detect: `http://192.168.124.94:9089/ks/stream/attr`,
  // tunnel:`http://192.168.124.94:9091/ks/system/tunnel`,
  // sysArgs: `http://192.168.124.94:9091/ks/system/args`,
  getDevices: `/box/box9091/ks/device`,
//   数据源
  getSources: `/box/box9091/ks/source`,
//   订阅实时视频流，返回data: webrtc://ip/stream/12345678901234567890123456789012
  subscribe: `/box/box9089/ks/stream/live/subscribe`,
  detect: `/box/box9089/ks/stream/attr`,
  tunnel:`/box/box9091/ks/system/tunnel`,
//   系统参数
  sysArgs: `/box/box9091/ks/system/args`,
  // 播放时产生mqtt消息，实时检测结果，用于前端播放时实时画框
  resultTopic: 'ks/sink_local_result',
  streamCodeTopic: 'ks/stream', //视频流状态码，状态码改变后应重新播放
  srs_server: 1935,
  srs_http_api: 1985,
  srs_http_server: 8080,
  websocket: 8083,
}
export default {
  data() {
    return {
      // 修改为动态键值对形式，不再限制为4个
      ZQL_videosInfos: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的视频信息
      ZQL_playingSource: {}, // 键名为'slot_1', 'slot_2'...，值为对应格子的摄像头ID  { 'slot_1': 1, 'slot_2': 2 }
      ZQL_sources: {},
      statusArr: {}, // 改为对象形式，键名为'slot_1'...
      sourceceList: [],
      deviceList: [],
      splitType: 1, // 分屏类型：1,4,9
      
      // 新增：分屏模式相关数据
      singleId: null, // 一分屏选中的设备id
      checkedIds: [], // 多分屏选中的设备id数组
      lastCheckedIds: [], // 记录上一次的多选值
      fullscreenKey: null, // 当前全屏的格子键名，null表示无全屏
      
      // 新增：算法选择相关
      currentAlgo: null, // 当前选中的算法
      
      // 新增：UI状态
      isFullscreen: false // 是否处于全屏状态
    }
  },
  methods: {
    // flag: 1 暂停 0 播放
    playPauseVideo(key, flag) {
      const videoInfo = this.ZQL_videosInfos[key];
      if (!videoInfo) return;
      // 优先尝试直接操作 video 元素
      const video = document.getElementById(`${this.prefixId}video${key}`);
      if (flag) {
        // 暂停
        if (video) {
          video.pause();  // 直接暂停 video 元素，保持画面
        } else if (videoInfo.srsrtc && videoInfo.srsrtc.pause) {
          videoInfo.srsrtc.pause();
        }
        videoInfo.playerState = 'paused';
        // 暂停时清空 canvas 绘制（如果选择了算法）
        this.pauseCanvas(key);
      } else {
        // 播放
        if (video) {
          video.play().catch(err => {
            console.warn('video.play() failed:', err);
            // 如果直接播放失败，尝试播放器的 play 方法
            if (videoInfo.srsrtc && videoInfo.srsrtc.play) {
              videoInfo.srsrtc.play();
            }
          });
        } else if (videoInfo.srsrtc && videoInfo.srsrtc.play) {
          videoInfo.srsrtc.play();
        }
        videoInfo.playerState = 'success';
        // 恢复播放时恢复 canvas 绘制
        this.resumeCanvas(key);
      }
    },
    // 暂停 canvas 绘制
    pauseCanvas(key) {
      const videoInfo = this.ZQL_videosInfos[key];
      if (!videoInfo) return;
      
      // 1. 清空当前 canvas
      this.clearCanvas(`${this.prefixId}canvas${key}`);
      
      // 2. 如果有正在等待的 canvasTimeout，清除它
      if (videoInfo.canvasTimeout) {
        clearTimeout(videoInfo.canvasTimeout);
        videoInfo.canvasTimeout = null;
      }
      
      // 3. 标记 canvas 暂停状态
      videoInfo.canvasPaused = true;
    },

    // 恢复 canvas 绘制
    resumeCanvas(key) {
      const videoInfo = this.ZQL_videosInfos[key];
      if (!videoInfo) return;
      
      // 取消暂停标记
      videoInfo.canvasPaused = false;
      
      // 如果有缓存的告警数据，立即渲染
      if (videoInfo.pendingAlarms && videoInfo.videoReady) {
        this.setAlarms(videoInfo.pendingAlarms, key);
        videoInfo.pendingAlarms = null;
      }
    },
    // 刷新视频
    refreshVideo(key) {
      console.log(`刷新视频: ${key}`);
      
      const videoInfo = this.ZQL_videosInfos[key];
      if (!videoInfo) return;
      
      // 获取当前播放的摄像头ID
      const cameraId = this.ZQL_playingSource[key];
      if (!cameraId) return;
      
      // 保存当前选择的算法
      const currentAlg = videoInfo.alg;
      const currentAlgName = videoInfo.currentAlgName;
      
      // 显示加载状态
      this.liveLoading(key);
      
      // 销毁旧的播放器
      if (videoInfo.srsrtc) {
        try {
          videoInfo.srsrtc.destroy();
        } catch (e) {
          console.warn('销毁播放器失败:', e);
        }
        videoInfo.srsrtc = null;
      }
      
      // 清空视频元素
      const video = document.getElementById(`${this.prefixId}video${key}`);
      if (video) {
        video.srcObject = null;
        video.src = '';
        video.load();
      }
      
      // 清空 canvas
      this.clearCanvas(`${this.prefixId}canvas${key}`);
      
      // 清除定时器
      if (this.ZQL_videosInfos[key].refreshTimeInterval) {
        clearInterval(this.ZQL_videosInfos[key].refreshTimeInterval);
        this.ZQL_videosInfos[key].refreshTimeInterval = null;
      }
      
      // 重置状态
      videoInfo.stream = null;
      videoInfo.stream_code = null;
      videoInfo.playerState = 'loading';
      videoInfo.videoReady = false;
      videoInfo.canvasPaused = false;
      videoInfo.pendingAlarms = null;
      
      // 重新订阅视频流
      this.subscribeLive(cameraId, key);
      
      // 恢复算法选择
      if (currentAlg) {
        videoInfo.alg = currentAlg;
        videoInfo.currentAlgName = currentAlgName;
      }
    },
    // 根据分屏数量初始化格子
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
    // 一分屏：单选变化
    onSingleDeviceChange(deviceId) {
      if (this.splitType !== 1) return;
      
      this.singleId = deviceId;
      const dev = this.deviceList.find(d => d.id === deviceId);
      
      if (!dev) {
        // 如果设备无效，清空格子
        this.clearSlot('slot_1');
        return;
      }
      
      // 销毁当前格子的视频
      this.destoryVideoByKey('slot_1');
      
      // 设置新的播放源
      this.$set(this.ZQL_playingSource, 'slot_1', deviceId);
      
      // 初始化视频信息
      this.$set(this.ZQL_videosInfos, 'slot_1', {
        ...dev,
        deviceId: dev.id,
        deviceName: dev.desc,
        algorithms: dev.algorithms || [],
        currentAlgo: null,
        currentAlgName: null,
        loading: false,
        status: ''
      });
      
      // 订阅视频流
      this.subscribeLive(deviceId, 'slot_1');
      this.setAlgList('slot_1');
      
      // 触发设备变化事件
      this.$emit('device-change', { key: 'slot_1', deviceId });
    },
    
    // 多分屏：复选框变化
    onMultiDeviceChange(newCheckedIds) {
      console.log('多选');
      if (this.splitType === 1) return;      
      // 找出新增的id
      const added = newCheckedIds.filter(id => !this.lastCheckedIds.includes(id));
      // 找出移除的id
      const removed = this.lastCheckedIds.filter(id => !newCheckedIds.includes(id));
      
      // 更新lastCheckedIds
      this.lastCheckedIds = [...newCheckedIds];
      this.checkedIds = [...newCheckedIds];
      
      // 1. 处理移除：找到设备所在的格子并清空
      removed.forEach(remId => {
        // 遍历所有格子找到匹配的设备
        for (let i = 1; i <= this.splitType; i++) {
          const key = 'slot_' + i;
          if (this.ZQL_playingSource[key] === remId) {
            // 如果正在全屏的格子被移除，退出全屏
            if (this.fullscreenKey === key) {
              this.fullscreenKey = null;
              this.isFullscreen = false;
              document.body.style.overflow = '';
            }
            // 清空该格子
            this.clearSlot(key);
            break;
          }
        }
      });
      
      // 2. 处理新增：找到第一个空闲格子并放入设备
      added.forEach(addId => {
        const dev = this.deviceList.find(d => d.id === addId);
        if (!dev) return;
        
        // 查找第一个空闲格子
        const emptyKey = this.getFirstEmptySlot();
        
        if (emptyKey) {
          // 放入设备
          this.$set(this.ZQL_playingSource, emptyKey, addId);
          this.$set(this.ZQL_videosInfos, emptyKey, {
            ...dev,
            deviceId: dev.id,
            deviceName: dev.desc,
            algorithms: dev.algorithms || [],
            currentAlgo: null,
            currentAlgName: null,
            loading: false,
            status: ''
          });
          
          // 订阅视频流
          this.subscribeLive(addId, emptyKey);
          
          // 触发设备变化事件
          this.$emit('device-change', { key: emptyKey, deviceId: addId });
        } else {
          // 没有空闲格子，无法添加
          console.warn('没有空闲格子，无法添加设备', addId);
          
          // 从选中列表中移除
          const indexToRemove = this.checkedIds.indexOf(addId);
          if (indexToRemove !== -1) {
            this.checkedIds.splice(indexToRemove, 1);
          }
          this.lastCheckedIds = [...this.checkedIds];
        }
      });

      console.log(this.ZQL_videosInfos);
      
    },
    
    // 切换全屏模式
    toggleFullscreen(key) {
      // 切换全屏时更新视频尺寸大小
      this.getCameraSize(this.ZQL_playingSource[key], key)
      // if (this.fullscreenKey === key) {
      //   // 退出全屏
      //   this.fullscreenKey = null;
      //   this.isFullscreen = false;
      //   document.body.style.overflow = '';
      // } else {
      //   // 进入全屏
      //   this.fullscreenKey = key;
      //   this.isFullscreen = true;
      //   document.body.style.overflow = 'hidden';
        
      //   // 延迟触发重绘，确保全屏样式生效
        // setTimeout(() => {
        //   this.$forceUpdate();
        // }, 100);
      // }
      // // 触发全屏变化事件
      // this.$emit('fullscreen-change', { key, isFullscreen: this.fullscreenKey === key });
      this.$nextTick(() => {
        // 处理全屏切换 append-to-body的影响
        const container1 = this.getRef(`dropdownRef${key}`);
        const menu = this.getRef(`dropdownMenuRef${key}`);
        if (container1 && menu && menu.popperElm) {
          if (this.fullscreenKey === key) {
            menu.popperElm.classList.add('top_unset', 'left_unset')
            menu.popperElm.style.bottom = '35px'
            container1.appendChild(menu.popperElm)
          } else {
            menu.popperElm.classList.remove('top_unset', 'left_unset')
            menu.popperElm.style.bottom = 'unset'
            // container1.removeChild(menu.popperElm)
            this.fullscreenKey = key
          }
        }
      })
    },
    
    // 算法切换
    onAlgoChange(data) {
      const { deviceItem, alg, key } = data
      deviceItem.currentAlg = alg.custom_key;
      deviceItem.currentAlgName = alg.reserved_args.ch_name;
      if (this.ZQL_videosInfos[key]) {
        // TODO:
        this.ZQL_videosInfos[key].currentAlgo = alg.custom_key;
        this.ZQL_videosInfos[key].alg = alg.custom_key;
        
        // console.log(`设备 ${this.ZQL_videosInfos[key].deviceName} 算法 -> ${alg.custom_key || '未选择'}`);
        
        // 触发算法变化事件
        this.$emit('alg-change', { key, alg });
      }
    },
    // 获取第一个空闲格子
    getFirstEmptySlot() {
      for (let i = 1; i <= this.splitType; i++) {
        const key = 'slot_' + i;
        if (!this.ZQL_playingSource[key]) {
          return key;
        }
      }
      return null;
    },
    
    // 清空指定格子
    clearSlot(key) {
      this.destoryVideoByKey(key);
      this.$set(this.ZQL_playingSource, key, null);
      // this.$set(this.ZQL_videosInfos, key, null);
      this.clearAlgList(key);
      this.liveStopLoading(key);
      // console.log('清空', key, this.ZQL_playingSource, this.ZQL_videosInfos);
      
    },
    clearTimer(timer, method = clearInterval) {
      if (timer) {
        console.log('method====', method);
        
        method(timer);
        timer = null;
      }
    },
    // 加载
    liveLoading(key) {
      this.$set(this.statusArr, key, 'loading');
      if (this.ZQL_videosInfos[key]) {
        this.ZQL_videosInfos[key].loading = true;
        this.ZQL_videosInfos[key].status = 'loading';
      }
    },
    
    // 离线
    liveOffline(key) {
      this.$set(this.statusArr, key, 'off');
      if (this.ZQL_videosInfos[key]) {
        // 1. 清空 canvas（重要！）
        this.clearCanvas(`${this.prefixId}canvas${key}`);
        // 2. 清理 video 元素，避免显示静止画面
        const video = document.getElementById(`${this.prefixId}video${key}`);
        if (video) {
          video.srcObject = null;  // 清除视频源
          video.src = '';          // 清空 src
          video.load();            // 触发重新加载，显示空白
        }
        
        // 3. 销毁播放器实例
        if (this.ZQL_videosInfos[key].srsrtc) {
          try {
            this.ZQL_videosInfos[key].srsrtc.destroy();
          } catch (e) {
            console.warn('销毁播放器失败:', e);
          }
          this.ZQL_videosInfos[key].srsrtc = null;
        }
        
        // 4. 更新状态
        this.ZQL_videosInfos[key].loading = false;
        this.ZQL_videosInfos[key].status = '离线';
        this.ZQL_videosInfos[key].playerState = 'error';
        
        // 5. 清除定时器
        // if (this.ZQL_videosInfos[key] && this.ZQL_videosInfos[key][timerKey]) {
        //   method(this.ZQL_videosInfos[key][timerKey]);
        //   this.ZQL_videosInfos[key][timerKey] = null;
        // }
        this.clearTimer(this.ZQL_videosInfos[key]['refreshTimeInterval'], 'refreshTimeInterval');
        ['replayTimer', 'loadingTimeout', 'canvasTimeout', 'subscribeTimeout'].map(timerKey => {
          this.clearTimer(this.ZQL_videosInfos[key][timerKey], clearTimeout);
        })
        // 重置视频就绪状态
        this.ZQL_videosInfos[key].videoReady = false;
        this.ZQL_videosInfos[key].pendingAlarms = null;
      }
    },
    
    // 停止加载
    liveStopLoading(key) {
      this.$set(this.statusArr, key, 'stop');
      if (this.ZQL_videosInfos[key]) {
        this.ZQL_videosInfos[key].loading = false;
        this.ZQL_videosInfos[key].status = '在线';
      }
    },
    
    // 头部摄像头名称、算法获取并渲染
    setAlgList(key) {
      this.selselectAlg = ''
    },
    
    clearAlgList(key) {
      // 根据key获取对应的DOM元素
      let el = document.querySelector(`#${this.prefixId}video-title${key}`);
      if (el) {
        el.innerHTML = ""
      }
    },
    
    handleRefresh(key) {
      if (!this.ZQL_videosInfos[key]) {
        return;
      }
      if (this.ZQL_videosInfos[key].status == "离线") {
        this.destoryVideoByKey(key);
        this.subscribeLive(this.ZQL_playingSource[key], key);
      } else {
        
        if (!this.ZQL_videosInfos[key].stream) {
          return;
        }
        let video = document.getElementById(`${this.prefixId}video${key}`);
        video && (video.srcObject = null);
        if (this.ZQL_videosInfos[key] && this.ZQL_videosInfos[key].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[key].replayTimer);
          this.ZQL_videosInfos[key].replayTimer = null;
        }
        this.ZQL_videosInfos[key] &&
          this.ZQL_videosInfos[key].srsrtc &&
          this.ZQL_videosInfos[key].srsrtc.destroy();
        this.ZQL_videosInfos[key].srsrtc = null;
        this.ZQL_videosInfos[key].status = "";
        this.playVideo(this.ZQL_playingSource[key], key);
      }
    },
    
    subscribeLive(cameraId, key) {
      this.getCameraSize(cameraId, key);
      this.liveLoading(key);
      // subscribeLiveBySourceId(`${ZQLGLOBAL.subscribe}?source_id=${cameraId}`).then(res => {
      subscribeLiveBySourceId(`http://192.168.124.94:9089/ks/stream/live/subscribe?source_id=${cameraId}`).then(res => {
        if (res && res.data) {
          // 确保ZQL_videosInfos[key]存在
          if (!this.ZQL_videosInfos[key]) {
            this.$set(this.ZQL_videosInfos, key, {});
          }
          this.ZQL_videosInfos[key].stream = res.data;
          this.playVideo(cameraId, key);
        } else {
          if (this.ZQL_playingSource[key] == cameraId) {
            this.liveOffline(key);
          }
        }
      }).catch((err) => {
        if (
          this.ZQL_playingSource[key] == cameraId &&
          this.ZQL_videosInfos[key]
        ) {
          this.liveOffline(key);
        }
      });
    },
    
    playVideo(cameraId, key) {
      if (this.ZQL_videosInfos[key] && this.ZQL_videosInfos[key].srsrtc) {
        return;
      }
      
      // 确保ZQL_videosInfos[key]存在
      if (!this.ZQL_videosInfos[key]) {
        this.$set(this.ZQL_videosInfos, key, {});
      }
      
      this.ZQL_videosInfos[key].loading = true;
      let video = document.getElementById(`${this.prefixId}video${key}`);
      
      let stream = this.ZQL_videosInfos[key].stream;
      // stream = webrtc://127.0.0.1/live/2e0b0eff-2e17-4da3-86e4-36c36375ef3c/68f8763c1271cb0b8d4caeb5
      var srsrtc;
      
      if (stream.indexOf("webrtc") >= 0) {
        // jswebrtc.min.js中将ZQLGLOBAL作为全局变量
        window.ZQLGLOBAL = ZQLGLOBAL
        // console.log(12);
        // const src = 'webrtc://192.168.124.94/live/2e0b0eff-2e17-4da3-86e4-36c36375ef3c/698c22b61271cb0e361eb5d2'
        const src =
        'webrtc://' + ZQLGLOBAL.serverIp + '/live' + stream.split('/live')[1];
        srsrtc = new JSWebrtc.Player(src, {
          video,
          autoplay: true,
          onPlay: (obj) => {
            console.log('播放111', obj);
            this.liveStopLoading(key);
            this.ZQL_videosInfos[key].loading = false;
            this.ZQL_videosInfos[key].playerState = "success";
            this.ZQL_videosInfos[key].videoReady = true;  // 标记视频已就绪
            // 如果有缓存的告警数据，立即渲染
            if (this.ZQL_videosInfos[key].pendingAlarms) {
              this.setAlarms(this.ZQL_videosInfos[key].pendingAlarms, key);
              this.ZQL_videosInfos[key].pendingAlarms = null;
            }
          },
          onPause: (obj) => {
            // console.log('暂停111', obj);
            this.ZQL_videosInfos[key].playerState = 'paused';  // 标记为暂停状态
            // 不需要清空画面，保持当前帧
            // this.liveOffline(key)
          }
        });
      } else if (stream.indexOf(".flv") >= 0) {
        let src = `http://${ZQLGLOBAL.serverIp}:${ZQLGLOBAL.srs_http_server}/live${stream.split("/live")[1]}`
        srsrtc = mpegts.createPlayer(
          {
            type: "flv",
            url: src,
            isLive: true,
          },
          { enableWorker: true }
        );
        srsrtc.attachMediaElement(video);
        srsrtc.load();
        this.ZQL_videosInfos[key].playerState = "";
        srsrtc
          .play()
          .then((res) => {            
            this.liveStopLoading(key);
            this.ZQL_videosInfos[key].playerState = "success";
            this.ZQL_videosInfos[key].loading = false;
            this.ZQL_videosInfos[key].videoReady = true;  // 标记视频已就绪
            // 如果有缓存的告警数据，立即渲染
            if (this.ZQL_videosInfos[key].pendingAlarms) {
              this.setAlarms(this.ZQL_videosInfos[key].pendingAlarms, key);
              this.ZQL_videosInfos[key].pendingAlarms = null;
            }

            if (this.ZQL_videosInfos[key].refreshTimeInterval) {
              clearInterval(this.ZQL_videosInfos[key].refreshTimeInterval);
            }
            this.ZQL_videosInfos[key].refreshTime =
              parseInt((Math.random() * 5 + 5) * 1000) * 60;
            this.ZQL_videosInfos[key].refreshTimeInterval = setInterval(() => {
              this.handleRefresh(key);
            }, this.ZQL_videosInfos[key].refreshTime);
          })
          .catch((err) => { });
        if (this.ZQL_videosInfos[key].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[key].replayTimer);
        }
        this.ZQL_videosInfos[key].replayTimer = setTimeout(() => {
          this.replayflv(srsrtc, cameraId, key);
        }, 3000);
      }
      // console.log('+++++++++srsrtc', srsrtc);
      this.ZQL_videosInfos[key].srsrtc = srsrtc;
      // console.log('------------ZQL_playingSource', ZQL_playingSource);
    },
    
    replayflv(srsrtc, cameraId, key) {
      if (!this.ZQL_videosInfos[key]) {
        return;
      }
      if (this.ZQL_videosInfos[key].playerState == "success") {
        return;
      } else {
        srsrtc.unload();
        srsrtc.load();
        srsrtc
          .play()
          .then((res) => {
            this.liveStopLoading(key);
            this.ZQL_videosInfos[key].playerState = "success";
            this.ZQL_videosInfos[key].loading = false;
            if (this.ZQL_videosInfos[key].refreshTimeInterval) {
              clearInterval(this.ZQL_videosInfos[key].refreshTimeInterval);
            }
            this.ZQL_videosInfos[key].refreshTime =
              parseInt((Math.random() * 5 + 5) * 1000) * 60;
            this.ZQL_videosInfos[key].refreshTimeInterval = setInterval(() => {
              this.handleRefresh(key);
            }, this.ZQL_videosInfos[key].refreshTime);
          })
          .catch((err) => {
            // this.destoryVideoByKey(key);
            // this.subscribeLive(cameraId, key);
          });
        if (this.ZQL_videosInfos[key].replayTimer) {
          clearTimeout(this.ZQL_videosInfos[key].replayTimer);
        }
        this.ZQL_videosInfos[key].replayTimer = setTimeout(() => {
          this.replayflv(srsrtc, cameraId, key);
        }, 3000);
      }
    },
    
    reSubcribe(cameraId, key) {
      if (this.ZQL_videosInfos[key] && this.ZQL_videosInfos[key].subscribeTimeout) {
        clearTimeout(this.ZQL_videosInfos[key].subscribeTimeout);
        this.ZQL_videosInfos[key].subscribeTimeout = null;
      }
      
      // 确保ZQL_videosInfos[key]存在
      if (!this.ZQL_videosInfos[key]) {
        this.$set(this.ZQL_videosInfos, key, {});
      }
      
      this.ZQL_videosInfos[key].subscribeTimeout = setTimeout(() => {
        this.subscribeLive(cameraId, key);
      }, 1000);
    },
    // 根据告警画布
    setAlarms(data, key) {
      // 如果 canvas 已暂停，不进行绘制
      if (this.ZQL_videosInfos[key] && this.ZQL_videosInfos[key].canvasPaused) {
        // 缓存告警数据，等待恢复播放时渲染
        this.ZQL_videosInfos[key].pendingAlarms = data;
        return;
      }
      this.clearCanvas(`${this.prefixId}canvas${key}`);
      const {actualWidth, actualHeight, oriWidth, oriHeight, canvas} = this.ZQL_videosInfos[key] || {}
      if (this.ZQL_videosInfos[key] && !canvas) {
        this.ZQL_videosInfos[key].canvas = document.getElementById(`${this.prefixId}canvas${key}`)
      }
      if (!this.ZQL_videosInfos[key] || !actualWidth || !actualHeight || !oriWidth || !oriHeight) {
        return;
      }
      const widthScale = this.ZQL_videosInfos[key].actualWidth / this.ZQL_videosInfos[key].oriWidth
      const widthHeight = this.ZQL_videosInfos[key].actualHeight / this.ZQL_videosInfos[key].oriHeight
      this.updateOneCanvasByBbox(data.bbox, this.ZQL_videosInfos[key].canvas, widthScale, widthHeight)
      
    },
    
    destroyVideo() {
      // 销毁所有视频
      Object.keys(this.ZQL_videosInfos).forEach(key => {
        this.destoryVideoByKey(key);
      });
    },
    
    destoryVideoByKey(key) {
      this.clearCanvas(`${this.prefixId}canvas${key}`);
      const videoInfo = this.ZQL_videosInfos[key];
      if (videoInfo) {
        const {subscribeTimeout, replayTimer, refreshTimeInterval} = videoInfo;
        
        if (subscribeTimeout) {
          clearTimeout(subscribeTimeout);
        }
        if (replayTimer) {
          clearTimeout(replayTimer);
        }
        if (refreshTimeInterval) {
          clearInterval(refreshTimeInterval);
        }
        
        let video = document.getElementById(`${this.prefixId}video${key}`);
        if (video) {
          video.srcObject = null;
          video.src = '';
          video.load();
        }
        
        if (videoInfo.srsrtc) {
          videoInfo.srsrtc.destroy();
          videoInfo.srsrtc = null;
        }
        
        this.clearCanvas(`${this.prefixId}canvas${key}`);
        
        // 将对应键的值设为null
        this.$set(this.ZQL_videosInfos, key, null);
        this.$set(this.ZQL_playingSource, key, null);
      }
    },
    connectMqtt: function() {
      // 检查当前连接状态
      const connectionStatus = mqttClient.getConnectionStatus();
      if (!connectionStatus.isConnected) {
        // 如果未连接，所有视频框显示离线
        Object.keys(this.ZQL_playingSource).forEach(key => {
          if (this.ZQL_videosInfos[key]) {
            this.liveOffline(key);
          }
        });
      }
      
      // 连接 MQTT
      const callback = (topic, payload) => {
        let msg = JSON.parse(payload.toString());
        if (msg.msg_type == 'result') {
          // console.log("result", msg.data);
          let id = msg.data.source.id;
          // 遍历所有视频格子，不再限制为4个
          Object.keys(this.ZQL_videosInfos).forEach(key => {
            if (this.ZQL_videosInfos[key]) {
              let alg = this.ZQL_videosInfos[key].alg && this.ZQL_videosInfos[key].alg.algname;
              if (id == this.ZQL_playingSource[key] && msg.data.alg.name == this.ZQL_videosInfos[key].alg) {                
                // 检查视频是否已就绪且未暂停，直接渲染
                if (this.ZQL_videosInfos[key].videoReady && !this.ZQL_videosInfos[key].canvasPaused) {
                  // 视频已就绪，直接渲染
                  this.setAlarms(msg.data.reserved_data, key);
                } else {
                  // 视频未就绪，缓存告警数据
                  this.ZQL_videosInfos[key].pendingAlarms = msg.data.reserved_data;
                }
                if (this.ZQL_videosInfos[key] && this.ZQL_videosInfos[key].canvasTimeout) {
                  clearTimeout(this.ZQL_videosInfos[key].canvasTimeout);
                }
                // 只有在未暂停状态下才设置自动清空定时器
                if (!this.ZQL_videosInfos[key].canvasPaused) {
                  this.ZQL_videosInfos[key].canvasTimeout = setTimeout(() => {
                    this.clearCanvas(`${this.prefixId}canvas${key}`);
                  }, 1000);
                }
              }
            }
          });
        }
        if (msg.msg_type == 'stream_code') {
          let cameraId = msg.data.source_id;
          // console.log("stream_code", msg.data);
          // 遍历所有视频格子，不再限制为4个
          Object.keys(this.ZQL_videosInfos).forEach(key => {
            if (cameraId == this.ZQL_playingSource[key] && this.ZQL_videosInfos[key]) {
              if (!this.ZQL_videosInfos[key].stream_code) {
                this.ZQL_videosInfos[key].stream_code = msg.data.stream_code;
              } else if (msg.data.stream_code != this.ZQL_videosInfos[key].stream_code) {
                this.ZQL_videosInfos[key].stream_code = msg.data.stream_code;
                this.handleRefresh(key);
              }
            }
          });
        }
      }
      mqttClient.subscribe(ZQLGLOBAL.resultTopic, callback);
      mqttClient.subscribe(ZQLGLOBAL.streamCodeTopic, callback);

      // 监听连接状态
      const connectionListener = (isConnected, isReconnecting, attempt) => {
        if (isReconnecting) {
          console.log(`正在重连... (第${attempt}次尝试)`)
          // 重连过程中显示加载状态
          Object.keys(this.ZQL_playingSource).forEach(key => {
            if (this.ZQL_videosInfos[key]) {
              this.liveLoading(key);
            }
          });
        } else if (isConnected) {
          console.log('连接已建立，正在恢复视频流...')
          // 延迟一段时间再恢复，确保MQTT订阅完全恢复
          setTimeout(() => {
            this.recoverAllStreams();
          }, 1000);
        } else {
          console.log('连接已断开，显示离线状态');
          // 连接断开时，对所有视频框显示离线
          Object.keys(this.ZQL_playingSource).forEach(key => {
            if (this.ZQL_videosInfos[key]) {
              this.liveOffline(key);
            }
          });
        }
      }
      mqttClient.onConnectionChange(connectionListener);
      // 取消监听
      // mqttClient.offConnectionChange(connectionListener);
    },

    recoverAllStreams() {
      // 恢复所有视频流
      Object.keys(this.ZQL_playingSource).forEach(key => {
        const cameraId = this.ZQL_playingSource[key];
        if (cameraId && this.ZQL_videosInfos[key]) {
          // 获取video元素并清理
          const video = document.getElementById(`${this.prefixId}video${key}`);
          if (video) {
            video.srcObject = null;  // 关键：清除视频源
            video.src = '';          // 双重保险
          }
          // 销毁旧的播放器
          if (this.ZQL_videosInfos[key].srsrtc) {
            this.ZQL_videosInfos[key].srsrtc.destroy();
            this.ZQL_videosInfos[key].srsrtc = null;
          }
          // 重置状态
          this.ZQL_videosInfos[key].stream = null;
          this.ZQL_videosInfos[key].stream_code = null;
          this.ZQL_videosInfos[key].playerState = "loading";
          this.ZQL_videosInfos[key].loading = true;
          this.ZQL_videosInfos[key].canvasTimeout = null;
          // 清除定时器
          if (this.ZQL_videosInfos[key].refreshTimeInterval) {
            clearInterval(this.ZQL_videosInfos[key].refreshTimeInterval);
            this.ZQL_videosInfos[key].refreshTimeInterval = null;
          }
          // 重置视频就绪状态
          this.ZQL_videosInfos[key].videoReady = false;
          this.ZQL_videosInfos[key].pendingAlarms = null;
          // 重新订阅
          this.subscribeLive(cameraId, key);
        }
      });
    },
    
    async getBoxHost() {
      const res = await getBoxHostList()
      if (res.code === 200) {
        if (res.data && res.data.length) {
          ZQLGLOBAL.serverIp = res.data[0].dictValue
          console.log('盒子赋值========', res.data);
          window.ZQLGLOBAL = {...ZQLGLOBAL, serverIp: res.data[0].dictValue}
        }
      }
    },
    
    // 请求后端接口
    async getSources() {
      await getBoxHostList();
      ZQLGLOBAL.serverIp = '192.168.124.94'
      // draw_size: [1280,720]
      // return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${imgUrl}`;
      const url = process.env.NODE_ENV === 'development' ? 'http://192.168.124.94:9091/ks/source' : `${ZQLGLOBAL.getSources}`
      getDeviceList(url).then(res => {
        console.log('获取摄像头列表', res);
        this.deviceList = res.data.map(item => {
          item.sourceId = item.id;
          item.title = item.desc;
          item.type = 'source';
          item.checked = false;
          // item.algorithms = Object.values(item.alg).slice();
          item.algorithms = Object.entries(item.alg).map(([key, obj]) => ({ custom_key: key, ...obj }));
          this.ZQL_sources[item.id] = item
          return item
        });
        
        // // 初始化视频格子
        // this.initSlots(this.splitType);
        
        // if (this.dogId) {
        //   this.handleClickDevice(this.dogId)
        // } else {
        //   this.$emit('getDogList', cameras)
        // }
        // this.deviceList = cameras.filter(item => item.id == this.dogId)
        // 默认选中
        // this.onSingleDeviceChange(this.deviceList[this.deviceList.length - 1].id)
      }).catch(err => {
        console.error('获取摄像头列表出错', err);
      })
    },
    
    sysArgs() {
      const tunnelUrl = process.env.NODE_ENV === 'development' ? 'http://192.168.124.94:9091/ks/system/tunnel' : `${ZQLGLOBAL.tunnel}`
      const argsUrl = process.env.NODE_ENV === 'development' ? 'http://192.168.124.94:9091/ks/system/args' : `${ZQLGLOBAL.sysArgs}`
      getTunnels(tunnelUrl).then(tunnelRes => {
        if (tunnelRes.data.enable == false) {
          getSysArgs(argsUrl).then(res => {
            ZQLGLOBAL = Object.assign(ZQLGLOBAL, res.data.map.local)
            this.connectMqtt()
          }).catch(err => {
            console.error('getSysArgs出错', err);
          })
        } else {
          const { srs_server, srs_http_api, srs_http_server, websocket } = tunnelRes.data
          ZQLGLOBAL = Object.assign(ZQLGLOBAL, { srs_server, srs_http_api, srs_http_server, websocket })
          this.connectMqtt()
        }
      }).catch(err => {
        console.error('getTunnels出错', err);
      })
    },    
    // 新增方法：将设备分配到指定格子
    assignDeviceToSlot(deviceId, key) {
      const dev = this.deviceList.find(d => d.id === deviceId);
      if (!dev) return false;
      
      // 销毁该格子原有的视频
      this.destoryVideoByKey(key);
      
      // 设置新的播放源
      this.$set(this.ZQL_playingSource, key, deviceId);
      this.$set(this.ZQL_videosInfos, key, {
        ...dev,
        deviceId: dev.id,
        deviceName: dev.desc,
        algorithms: dev.algorithms || [],
        currentAlgo: null,
        loading: false,
        status: ''
      });
      
      // 订阅视频流
      this.subscribeLive(deviceId, key);
      
      return true;
    },
    
    // 新增方法：获取当前分屏类型
    getSplitType() {
      return this.splitType;
    },
    
    // 新增方法：获取全屏状态
    getFullscreenStatus() {
      return {
        isFullscreen: this.isFullscreen,
        fullscreenKey: this.fullscreenKey
      };
    },
    
    // 新增方法：退出全屏
    exitFullscreen() {
      if (this.fullscreenKey) {
        this.fullscreenKey = null;
        this.isFullscreen = false;
        document.body.style.overflow = '';
        this.$emit('fullscreen-change', { key: null, isFullscreen: false });
      }
    },
    // 删除视频框
    removeVideo(key) {
      // 1. 清除视频相关资源
      this.destoryVideoByKey(key);
      
      // 2. 清除算法选择
      if (this.ZQL_videosInfos[key]) {
        this.ZQL_videosInfos[key].alg = null;
        this.ZQL_videosInfos[key].currentAlg = null;
        this.ZQL_videosInfos[key].currentAlgName = null;
      }
      
      // 3. 清除播放源
      this.$set(this.ZQL_playingSource, key, null);
      
      // 4. 清空视频信息
      this.$set(this.ZQL_videosInfos, key, null);
      
      // 5. 更新状态
      this.$set(this.statusArr, key, null);
      
      // 6. 通知父组件更新右侧设备列表的选中状态
      this.$emit('videoRemoved', key);
    }
  },
  destroy() {
    // 取消特定主题的所有订阅
    mqttClient.unsubscribe(ZQLGLOBAL.resultTopic);
    mqttClient.unsubscribe(ZQLGLOBAL.streamCodeTopic);
    // 取消特定主题的特定回调
    // mqttClient.unsubscribe(ZQLGLOBAL.resultTopic, callback);
  }
}