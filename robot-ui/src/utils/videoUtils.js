export default {
  data() {
    return {
      volumeInfo: {
        // 当前音量值 原声音*100，视频音量0-1
        currentVolume: 100,
        // 是否静音
        isMuted: false,
        // 之前的音量值（用于取消静音）
        previousVolume: 100,
        // 是否显示脉冲动画
        isPulsing: false,
        // 是否需要考虑上下键跟机器狗操作键的冲突
        visible: false
      },
      // 播放/暂停
      isPlay: true,
      // 打开/关闭麦克风
      openMic: true,
      // 切换全屏
      isFullscreen: true,
      pixel: {
        currentPixel: 'auto',
        currentPixelLabel: '自动',
        pixelTypes: [
          { value: 'auto', label: '自动' },
          { value: 'main', label: '高清' },
          { value: 'sub', label: '流畅' }
          // {
          //   label: '超清4K',
          //   value: '4K'
          // },
          // {
          //   label: '高清 SDR 1080P',
          //   value: '1080P'
          // },
          // {
          //   label: '准高清720P',
          //   value: '720P'
          // },
          // {
          //   label: '标清480P',
          //   value: '480P'
          // },
          // {
          //   label: '智能',
          //   value: 'smart'
          // },
        ]
      }
    }
  },
  computed: {
    videoEl() {
      // console.log('#' + this.idName + ' > video');
      
      return document.querySelector('#' + this.idName + ' > video')
    },
    // 音量显示值
    volumeValue() {
      const cur  = Number(this.volumeInfo.currentVolume)
      return (cur === 0 || this.volumeInfo.isMuted) ? '扬声器：静音' : `扬声器：${cur}%`;
    },
    // 音量图标类，根据音量值动态计算
    volumeIconClass() {
      const cur  = Number(this.volumeInfo.currentVolume)
      if (cur === 0 || this.volumeInfo.isMuted) {
        return 'volume-mute';
      } else if (cur < 50) {
        return 'volume-l';
      } else {
        return 'volume';
      }
    },
  },
  methods: {
    // 音量操作
    handleVolumeVisibleChange(visible) {
      this.volumeInfo.visible = visible;
    },
    // 更新音量
    updateVolume() {
      const cur  = Number(this.volumeInfo.currentVolume)
      
      // 确保音量在0-100范围内
      // this.volumeInfo.currentVolume = Math.max(0, Math.min(100, this.volumeInfo.currentVolume));
      // this.volumeInfo.currentVolume = this.videoEl.volume * 100;
      
      // 触发脉冲动画
      this.triggerPulse();
      
      // 如果音量为0，自动静音
      if (cur === 0) {
        this.volumeInfo.isMuted = true;
      } else {
        this.volumeInfo.isMuted = false;
      }
      this.videoEl.volume = cur / 100;
    },
    // 切换静音
    toggleMute() {
      const cur  = Number(this.volumeInfo.currentVolume)
      if (this.volumeInfo.isMuted) {
        // 取消静音，恢复之前的音量
        this.volumeInfo.isMuted = false;
        this.volumeInfo.currentVolume = this.volumeInfo.previousVolume;
        this.updateVolume();
      } else {
        // 静音，保存当前音量
        this.volumeInfo.previousVolume = cur;
        this.volumeInfo.isMuted = true;
        this.volumeInfo.currentVolume = 0;
        this.updateVolume();
      }
        
      // 触发脉冲动画
      this.triggerPulse();
    },
    // 触发脉冲动画
    triggerPulse() {
      this.volumeInfo.isPulsing = true;
      setTimeout(() => {
        this.volumeInfo.isPulsing = false;
      }, 500);
    },
    // 像素
    handleChangePixel(info) {
      this.pixel.currentPixel = info.value;
      this.pixel.currentPixelLabel = info.label;
    },
    // 切换麦克风
    toggleMic() {
      this.openMic = !this.openMic;
    },
    // 切换视频播放
    playPauseVideo() {
      // 切换状态（这里的 isPlay 表示当前是否正在播放）
      this.isPlay = !this.isPlay;
      // // 发送事件：0 表示播放，1 表示暂停
      this.$emit('playPauseVideo', this.isPlay ? 0 : 1);
    },
    // 键盘事件处理
    handleKeydown(event) {
      // 上箭头增加音量
      if (event.key === 'ArrowUp') {
        event.preventDefault();
        this.volumeInfo.currentVolume += 5;
        this.updateVolume();
      } else if (event.key === 'ArrowDown') {
        // 下箭头减少音量
        event.preventDefault();
        this.volumeInfo.currentVolume -= 5;
        this.updateVolume();
      } else if (event.key === 'm' || event.key === 'M') {
        // M键切换静音
        event.preventDefault();
        this.toggleMute();
      } else if (event.key === 'Escape' && this.isFullscreen) {
        // 退出全屏
        this.exitFullscreen()
      }
    },
    // 切换全屏
    initFullscreen() {
      // 添加事件监听
      this.addEventListeners()
      this.checkFullscreenStatus()
    },
    addEventListeners() {
      const events = [
        'fullscreenchange',
        'webkitfullscreenchange',
        'mozfullscreenchange',
        'MSFullscreenChange'
      ]
      
      events.forEach(event => {
        document.addEventListener(event, this.handleFullscreenChange)
      })
      
      document.addEventListener('keydown', this.handleKeydown)
    },
    removeEventListeners() {
      const events = [
        'fullscreenchange',
        'webkitfullscreenchange',
        'mozfullscreenchange',
        'MSFullscreenChange'
      ]
      
      events.forEach(event => {
        document.removeEventListener(event, this.handleFullscreenChange)
      })
      
      document.removeEventListener('keydown', this.handleKeydown)
    },
    // 进入全屏
    async enterFullscreen() {
      const el = document.getElementById(this.idName)
      
      if (!el) return
      const methods = [
        'requestFullscreen',
        'mozRequestFullScreen',
        'webkitRequestFullscreen',
        'msRequestFullscreen'
      ]
      for (const method of methods) {
        if (el[method]) {
          await el[method]()
          break
        }
      }
    },
    // 退出全屏
    async exitFullscreen() {
      const methods = [
        'exitFullscreen',
        'mozCancelFullScreen',
        'webkitExitFullscreen',
        'msExitFullscreen'
      ]
      
      for (const method of methods) {
        if (document[method]) {
          await document[method]()
          break
        }
      }
    },
    // 切换全屏
    async toggleFullscreen() {
      if (!this.isFullscreen) {
        await this.enterFullscreen()
      } else {
        await this.exitFullscreen()
      }
      await new Promise(resolve => requestAnimationFrame(resolve))
      this.updateDropdownStyle('pixel');
      this.updateDropdownStyle('volume');
      this.$emit('updateDropdownStyle');
      this.$emit('toggleFullscreen', this.slotKey)
    },
    // 初始化下拉框样式
    initDropdownStyle(key) {
      const menu = this.$refs[`dropdownMenuRef${this.slotKey}_${key}`];
      if (menu && menu.popperElm) {
        // 确保下拉框宽度自适应内容
        menu.popperElm.style.width = 'auto';
        menu.popperElm.style.minWidth = '120px';
        // 移除可能影响定位的样式
        menu.popperElm.style.left = 'unset';
        menu.popperElm.style.right = 'unset';
        menu.popperElm.style.bottom = 'unset';
        menu.popperElm.style.position = 'unset';
        menu.popperElm.style.transform = 'unset';
      }
    },
    // 处理全屏切换 append-to-body的影响
    updateDropdownStyle(key, forceReset) {      
      this.$nextTick(() => {
        const container1 = this.$refs[`dropdownRef${this.slotKey}_${key}`];
        const menu = this.$refs[`dropdownMenuRef${this.slotKey}_${key}`];
        if (container1 && menu && menu.popperElm) {
          if (this.isFullscreen) {
            menu.popperElm.classList.add('top_unset', 'left_unset0')
            menu.popperElm.style.bottom = '35px'
            menu.popperElm.style.position = 'absolute'
            menu.popperElm.style.left = '0'
            if (menu.popperElm.parentElement !== container1) {
              container1.appendChild(menu.popperElm)
            }
          } else {
            menu.popperElm.classList.remove('top_unset', 'left_unset')
            menu.popperElm.style.bottom = 'unset'
            menu.popperElm.style.position = 'unset'
            menu.popperElm.style.left = 'unset'
            // 将元素移回body（如果之前被移动过）
            if (menu.popperElm.parentElement === container1 || forceReset) {
              document.body.appendChild(menu.popperElm)
            }
          }
        }
      })
    },
    // 检查全屏状态
    checkFullscreenStatus() {
      this.isFullscreen = !!(
        document.fullscreenElement ||
        document.mozFullScreenElement ||
        document.webkitFullscreenElement ||
        document.msFullscreenElement
      )
    },
    handleFullscreenChange() {
      this.checkFullscreenStatus()
      this.$emit('change', this.isFullscreen)
      // 全屏状态改变时更新 dropdown 样式
      this.$nextTick(() => {
        this.updateDropdownStyle('pixel');
        this.updateDropdownStyle('volume');
      })
    },
    resizeChange() {
      this.$emit('toggleFullscreen', this.slotKey)
      // 窗口大小改变时检查并更新 dropdown 样式
      this.$nextTick(() => {
        this.updateDropdownStyle('pixel');
        this.updateDropdownStyle('volume');
      })
    },
    // 拍照
    
  },
  mounted() {
    // 全屏切换
    this.initFullscreen();
    // 音量
    // 初始音量设置
    // console.log(this.videoEl);
    
    this.volumeInfo.isMuted = this.videoEl.muted;
    this.volumeInfo.currentVolume = this.videoEl.volume * 100;
    this.updateVolume();
    // 初始化时强制重置样式，确保与非全屏样式保持一致
    this.$nextTick(() => {
      this.updateDropdownStyle('pixel', true);
      this.updateDropdownStyle('volume', true);
    })
    // TODO: 添加键盘事件监听 统一写在addEventListeners方法里

    // 初始化时不强制应用样式，让dropdown使用默认定位
    this.$emit('updateDropdownStyle', true);
    window.addEventListener('resize', this.resizeChange);
  },
  beforeDestroy() {
    // 移除键盘事件监听
    this.removeEventListeners();
    window.removeEventListener('resize', this.resizeChange);
  }
}