<template>
  <div class="w100 header-container">
    <div class="w100 h100 d-flex" style="justify-content: center;">
      <span class="header-title mt11" >具身智能平台指挥中心</span>
      <div class="toolbar flx-align-center hp40 mt43" style="justify-content: end;">
        <div class="time-display flx-align-center">
          <span>{{ currentDate }}</span>
          <span class="ml10">{{ currentTime }}</span>
        </div>
        <div class="flx-center icons h100">
          <div class="icon ml20" @click="toggleFullscreen">
            <svg-icon :icon-class="isFullscreen ? 'close-fullscreen' : 'fullscreen'" :style="{ color: '#AED1FF', fontSize: isFullscreen ? undefined : '14px' }"></svg-icon>
          </div>
          <div class="icon ml20">
            <!-- <svg-icon icon-class="home" style="color: #8BAEDC;"></svg-icon> -->
             <PageChangeDropdown />
          </div>
          <!-- <div class="icon ml20">
            <svg-icon icon-class="clock" style="color: #AED1FF;"></svg-icon>
          </div> -->
        </div>
        <UserMenu class="ml40" size="sm" />
      </div>
    </div>
  </div>
</template>

<script>
import PageChangeDropdown from './PageChangeDropdown.vue'
import UserMenu from '../components/UserMenu.vue'
import { events, isPageFullscreen, togglePageFullscreen } from '@/utils/fullscreen'
export default {
  name: 'Header',
  components: {
    PageChangeDropdown,
    UserMenu
  },
  data() {
    return {
      currentDate: '',
      currentTime: '',
      timer: null,
      isFullscreen: false,
      notificationCount: 5,
      username: '管理员',
      userAvatar: ''
    }
  },
  mounted() {
    this.updateTime()
    this.timer = setInterval(this.updateTime, 1000)

    // 仅同步网页全屏状态（不含视频框全屏）
    events.forEach(event => document.addEventListener(event, this.onFullscreenChange))
    window.addEventListener('resize', this.onFullscreenChange)
    // 拦截 F11，改走网页 Fullscreen API，以便点击图标可退出
    window.addEventListener('keydown', this.onFullscreenKeydown)
    this.onFullscreenChange()
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
    }
    events.forEach(event => document.removeEventListener(event, this.onFullscreenChange))
    window.removeEventListener('resize', this.onFullscreenChange)
    window.removeEventListener('keydown', this.onFullscreenKeydown)
  },
  methods: {
    // 更新时间
    updateTime() {
      const now = new Date()
      const year = now.getFullYear()
      const month = String(now.getMonth() + 1).padStart(2, '0')
      const day = String(now.getDate()).padStart(2, '0')
      const hours = String(now.getHours()).padStart(2, '0')
      const minutes = String(now.getMinutes()).padStart(2, '0')
      const seconds = String(now.getSeconds()).padStart(2, '0')
      this.currentDate = `${year}-${month}-${day}`
      this.currentTime = `${hours}:${minutes}:${seconds}`
    },
    // 拦截 F11，切换网页全屏
    onFullscreenKeydown(e) {
      if (e.key === 'F11') {
        e.preventDefault()
        this.toggleFullscreen()
      }
    },
    // 网页全屏切换（不影响视频框全屏状态判断）
    toggleFullscreen() {
      togglePageFullscreen()
    },
    // 网页全屏状态改变
    onFullscreenChange() {
      this.isFullscreen = isPageFullscreen()
    }
  }
}
</script>

<style lang="scss" scoped>

.header-container {
  position: fixed;
  top: 0;
  left: 0;
  height: 93px;
  // background-image: url("../../../assets/images/new-bi/header2.png");
  // background-size: 100% 100%;
  // padding: 0 20px;
  z-index: 100;

  &::before, &::after {
    content: '';
    position: absolute;
    left: 0;
    width: 100%;
    height: 100px;
    z-index: 0;
  }
  &::before {
    top: 0;
    background: linear-gradient(90deg, rgba(20, 31, 51, 0.20) 50.08%, rgba(19, 30, 50, 0.00) 100%);
  }
  & > div {
    position: absolute;
    background-image: url("../../../assets/images/new-bi/header2.png");
    background-size: 100% 100%;
    z-index: 1;
  }

  // Logo 区域
  .header-title {
    text-align: center;
    // text-shadow: 0 2px 10px #205DC5, 0 4px 6px #1973D6, 0 1px 2px #165EAE;
    font-family: YouSheBiaoTiHei;
    font-size: 38px;
    font-style: normal;
    font-weight: 400;
    line-height: 51.247px; /* 134.86% */
    letter-spacing: 15px;
    background: linear-gradient(199deg, #FFF 24.5%, #91CDF7 73.27%);
    background-clip: text;
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  // 导航菜单
  .nav-menu {
    flex: 1;
    display: flex;
    // justify-content: center;
    // max-width: 600px;
    margin-left: 248px;
    padding: 12.5px 0 15px;

    ::v-deep .el-menu {
      border-bottom: none;

      .el-menu-item {
        width: 116px;
        height: 54px;
        color: #DFDFDF !important;
        text-align: center;
        font-size: 16px;
        font-weight: 600;
        line-height: 54px;
        text-align: center;
        font-family: "Microsoft YaHei";
        border-bottom: none !important;
        &:hover, &.is-active {
          color: #FFF !important;
          font-weight: 700;
          background: transparent !important;
        }
        &:hover {
          background: transparent !important;
        }
        &:active, &.is-active {
          background-image: url("../../../assets/images/new-bi/nav-active.png") !important;
        }

        & + .el-menu-item {
          margin-left: 22px;
        }
      }
    }
  }

  .toolbar {
    position: absolute;
    top: 0;
    right: 30px;
    .icons {
      .icon {
        display: block;
        width: 28px;
        height: 28px;
        text-align: center;
        line-height: 28px;
        background: linear-gradient(180deg, #011229 0%, #0080D5 100%);
        border: 1px solid #5AA0FF;
        border-radius: 50%;
        font-size: 16px;
      }
    }
    .time-display {
      color: #82C1FF;
      text-shadow: 0 1px 3px rgba(5, 12, 25, 0.54);
      font-family: "Alibaba PuHuiTi";
      font-size: 16px;
      line-height: 22px;
    }
  }
}

@keyframes pulse {
  0%, 100% {
    filter: drop-shadow(0 0 5px rgba(0, 148, 255, 0.5));
  }
  50% {
    filter: drop-shadow(0 0 15px rgba(0, 148, 255, 0.8));
  }
}
</style>
