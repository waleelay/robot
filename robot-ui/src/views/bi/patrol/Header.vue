<template>
  <div class="w100 header-container">
    <div class="w100 h100 flx-justify-between">
      <!-- Logo 区域 -->
      <div class="flx-center ml30 ">
        <img src="@/assets/images/new-bi/badge.png" alt="" />
        <span class="ml10 header-title">巡逻巡查管控</span>
      </div>
  
      <!-- 导航菜单 -->
      <div class="nav-menu ml80 flx-center">
        <el-menu
          :default-active="activeIndex"
          mode="horizontal"
          background-color="transparent"
          text-color="#838BA0"
          active-text-color="#fff"
          @select="handleSelect"
        >
          <el-menu-item index="panorama">
            <span>全景地图</span>
          </el-menu-item>
          <el-menu-item index="monitor">
            <span>实时监控</span>
          </el-menu-item>
          <el-menu-item index="business">
            <span>业务管理</span>
          </el-menu-item>
          <el-menu-item index="statistics">
            <span>数据统计</span>
          </el-menu-item>
        </el-menu>
      </div>
  
      <!-- 右侧工具栏 -->
      <div class="toolbar flx-align-center h100 old" style="display: none; justify-content: end;">
        <!-- 通知 -->
        <!-- <el-badge :value="notificationCount" class="notification-badge">
          <el-button
            type="text"
            icon="el-icon-bell"
            class="toolbar-btn"
            @click="handleNotification"
          />
        </el-badge> -->
  
        <div class="flx-center flex-column zhzx h100">
          <div class="icon">
            <svg-icon icon-class="change" style="color: #8BAEDC;"></svg-icon>
          </div>
          <div class="text mt4">指挥中心</div>
        </div>
        <div class="flx-center flex-column full ml20 h100" @click="toggleFullscreen">
          <div class="icon">
            <svg-icon :icon-class="isFullscreen ? 'fullscreen1' : 'fullscreen'" style="color: #AED1FF;"></svg-icon>
          </div>
          <div class="text mt4">全屏</div>
        </div>
  
        <!-- 时间显示 -->
        <div class="time-display ml20 pl20 flx-center flex-column h100" style="align-items: unset">
          <span class="time">{{ currentTime }}</span>
          <span class="date">{{ currentDate }}</span>
        </div>
        <div class="org ml20 pl20 flx-align-center h100">
          <svg-icon icon-class="gov" style="font-size: 26px;"></svg-icon>
          <span class="text ml4">成都启航信息</span>
        </div>
      </div>
      <div class="toolbar flx-align-center h100 new pr15" style="justify-content: end;">
        <div class="time-display flx-align-center">
          <span>{{ currentDate }}</span>
          <span class="ml10">{{ currentTime }}</span>
        </div>
        <div class="flx-center icons h100 ml20">
          <div class="icon" @click="toggleFullscreen">
            <svg-icon :icon-class="isFullscreen ? 'close-fullscreen' : 'fullscreen'" style="color: #AED1FF;"></svg-icon>
          </div>
          <div class="icon ml20">
            <!-- <svg-icon icon-class="home" style="color: #8BAEDC;"></svg-icon> -->
             <PageChangeDropdown />
          </div>
          <div class="icon ml20" v-if="showBack" @click="back">
            <svg-icon icon-class="back1" style="color: #AED1FF;"></svg-icon>
          </div>
          <!-- <div class="icon ml20">
            <svg-icon icon-class="clock" style="color: #AED1FF;"></svg-icon>
          </div> -->
        </div>
        <UserMenu class="ml40" size="md" />
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex/dist/vuex.common.js';
import PageChangeDropdown from './../home/PageChangeDropdown.vue'
import UserMenu from '../components/UserMenu.vue'
import { events, isPageFullscreen, togglePageFullscreen } from '@/utils/fullscreen'

export default {
  name: 'Header',
  props: {
    activeHead: {
      type: String,
      default: 'home'
    },
  },
  components: {
    PageChangeDropdown,
    UserMenu
  },
  computed: {
    showBack() {
      return this.$store.getters['websocketRobot/getSelectedRobotId'] && this.$route.path === '/bi/patrol/monitor'
    }
  },
  data() {
    return {
      currentDate: '',
      currentTime: '',
      timer: null,
      isFullscreen: false,
      notificationCount: 5,
      username: '管理员',
      userAvatar: '',
      // activeIndex: this.activeHead
      activeIndex: this.$route.path.split('/patrol/')[1]
    }
  },
  mounted() {
    this.updateTime()
    this.timer = setInterval(this.updateTime, 1000)

    // 监听 Fullscreen API 与窗口尺寸变化，保持图标同步
    events.forEach(event => document.addEventListener(event, this.onFullscreenChange))
    window.addEventListener('resize', this.onFullscreenChange)
    // 拦截 F11，改走 Fullscreen API，以便点击图标可退出
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
    ...mapActions('websocketRobot', ['setSelectedRobotId']),
    back() {
      this.setSelectedRobotId('')
    },
    goHome() {
      this.$router.push({ name: 'biIndex' })
      this.setSelectedRobotId('')
    },
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
    // 菜单选择
    handleSelect(key) {
      if (this.activeIndex !== key) {
        this.setSelectedRobotId('')
        if (key !== 'monitor') {
        }
        this.activeIndex = key
        this.$router.push(`/bi/patrol/${key}`)
      }
    },
    // 通知点击
    handleNotification() {
      this.$message.info('您有 ' + this.notificationCount + ' 条新通知')
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
  height: 80px;
  background-image: url("../../../assets/images/new-bi/header1.png");
  background-size: 100% 100%;
  // padding: 0 20px;
  z-index: 100;

  // Logo 区域
  .header-title {
    font-family: YouSheBiaoTiHei;
    font-size: 40px;
    letter-spacing: 12px;
    line-height: 52px;
    background: linear-gradient(0deg, #29A5FF 0%, #4E9EFF 19%, #B3D1FF 37%, #DAEBFF 55%, #EBF5FD 72%, #F9FCFE 87%, #FFF 100%);
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

  // 工具栏
  .toolbar.old {
    // gap: $spacing-lg;

    .notification-badge {
      ::v-deep .el-badge__content {
        // background-color: $danger-color;
        border: none;
      }
    }

    .zhzx, .full {
      .icon {
        display: block;
        width: 26px;
        height: 26px;
        text-align: center;
        line-height: 26px;
        background: linear-gradient(180deg, rgba(2, 27, 61, 0.80) 0%, rgba(0, 116, 195, 0.80) 100%);
        border: 0.65px solid #5AA0FF;
        border-radius: 50%;
        font-size: 16px;
      }
      .text {
        color: #8BAEDC;
        font-family: "Microsoft YaHei";
        font-size: 14px;
        line-height: 18px;
      }
    }

    .toolbar-btn {
      // color: $text-primary;
      font-size: 20px;
      padding: 0;
      margin-right: 8px;
      // transition: all $transition-base;

      &:hover {
        // color: $primary-color;
        background-color: rgba(0, 148, 255, 0.1);
        transform: scale(1.1);
      }
    }

    .time-display {
      color: #BFDFFF;
      text-shadow: 0 1px 3px rgba(5, 12, 25, 0.54);
      font-family: "Alibaba PuHuiTi";
      font-size: 16px;
      opacity: 0.8;
      .time {
        font-family: Bahnschrift;
        font-size: 22px;
        line-height: 26px;
      }
    }
    .org {
      color: #8BAEDC;
      text-align: center;
      font-family: "Microsoft YaHei";
      font-size: 18px;
      font-weight: 600;
      line-height: 24px;
    }
    .time-display, .org {
      position: relative;
      &::before {
        position: absolute;
        top: 24px;
        left: 0;
        width: 1px;
        height: 32px;
        background: rgba(156, 184, 212, 0.50);
        content: "";
      }
    }

    .user-info {
      display: flex;
      align-items: center;
      // gap: $spacing-sm;
      // padding: $spacing-sm $spacing-md;
      // border-radius: $border-radius-lg;
      cursor: pointer;
      // transition: all $transition-base;

      &:hover {
        background-color: rgba(0, 148, 255, 0.1);
      }

      .username {
        font-size: 14px;
        // color: $text-primary;
        font-weight: 500;
      }

      i {
        // color: $text-secondary;
        // transition: transform $transition-base;
      }

      &:hover i {
        transform: rotate(180deg);
      }
    }
  }

  .toolbar.new {
    .icons {
      .icon {
        display: block;
        width: 36px;
        height: 36px;
        text-align: center;
        line-height: 36px;
        background: linear-gradient(180deg, #011229 0%, #0080D5 100%);
        border: 1px solid #5AA0FF;
        border-radius: 50%;
        font-size: 20px;
      }
    }
    .time-display {
      color: #BFDFFF;
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