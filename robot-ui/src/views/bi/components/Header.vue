<template>
  <div class="w100 header-container flx-justify-between" style="align-items: initial;">
    <!-- Logo 区域 -->
    <div class="header-title">警用具身智能平台</div>

    <!-- 导航菜单 -->
    <div class="nav-menu">
      <el-menu
        :default-active="activeIndex"
        mode="horizontal"
        background-color="transparent"
        text-color="#838BA0"
        active-text-color="#fff"
        @select="handleSelect"
      >
        <el-menu-item index="home">
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="patrol">
          <span>巡逻巡查</span>
        </el-menu-item>
        <el-menu-item index="staff">
          <span>人员管理</span>
        </el-menu-item>
        <el-menu-item index="vehicle">
          <span>车辆监管</span>
        </el-menu-item>
        <el-menu-item index="demo">
          <span>DEMO组件</span>
        </el-menu-item>
      </el-menu>
    </div>

    <!-- 右侧工具栏 -->
    <div class="toolbar">
      <!-- 通知 -->
      <!-- <el-badge :value="notificationCount" class="notification-badge">
        <el-button
          type="text"
          icon="el-icon-bell"
          class="toolbar-btn"
          @click="handleNotification"
        />
      </el-badge> -->

      <!-- 全屏切换 -->
      <el-tooltip content="全屏" placement="bottom">
        <el-button
          type="text"
          :icon="isFullscreen ? 'el-icon-crop' : 'el-icon-full-screen'"
          class="toolbar-btn"
          @click="toggleFullscreen"
        />
      </el-tooltip>

      <!-- 时间显示 -->
      <div class="time-display">
        <span class="date">{{ currentDate }}</span>
        <span class="time ml10">{{ currentTime }}</span>
      </div>
      <svg-icon icon-class="notice2" class="ml10" style="font-size: 20px; color: #9BB8D5;"></svg-icon>

      <!-- 用户信息 -->
      <!-- <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="36" :src="userAvatar">
            <img src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
          </el-avatar>
          <span class="username">{{ username }}</span>
          <i class="el-icon-arrow-down"></i>
        </div>
        <el-dropdown-menu slot="dropdown" class="user-dropdown">
          <el-dropdown-item command="profile">
            <i class="el-icon-user"></i>
            个人中心
          </el-dropdown-item>
          <el-dropdown-item command="settings">
            <i class="el-icon-setting"></i>
            账号设置
          </el-dropdown-item>
          <el-dropdown-item divided command="logout">
            <i class="el-icon-switch-button"></i>
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown> -->
    </div>
  </div>
</template>

<script>
export default {
  name: 'Header',
  props: {
    activeHead: {
      type: String,
      default: 'home'
    },
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
      activeIndex: this.activeHead
    }
  },
  mounted() {
    this.updateTime()
    this.timer = setInterval(this.updateTime, 1000)
    
    // 监听全屏变化
    document.addEventListener('fullscreenchange', this.onFullscreenChange)
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
    }
    document.removeEventListener('fullscreenchange', this.onFullscreenChange)
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
    
    // 菜单选择
    handleSelect(key) {
      this.activeIndex = key
      this.$router.push(`/bi/${key}`)
    },
    
    // 通知点击
    handleNotification() {
      this.$message.info('您有 ' + this.notificationCount + ' 条新通知')
    },
    
    // 全屏切换
    toggleFullscreen() {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen()
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen()
        }
      }
    },
    
    // 全屏状态改变
    onFullscreenChange() {
      this.isFullscreen = !!document.fullscreenElement
    },
    
    // 用户菜单命令
    handleCommand(command) {
      switch (command) {
        case 'profile':
          this.$message.info('跳转到个人中心')
          break
        case 'settings':
          this.$message.info('跳转到账号设置')
          break
        case 'logout':
          this.$confirm('确定要退出登录吗?', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }).then(() => {
            this.$message.success('退出成功')
            // 这里添加退出登录逻辑
          })
          break
      }
    }
  }
}
</script>

<style lang="scss" scoped>

.header-container {
  position: fixed;
  top: 0;
  left: 0;
  height: 79px;
  background-image: url("../../../assets/images/new-bi/header.png");
  background-size: 100% 100%;
  padding: 0 20px;
  z-index: 100;

  // Logo 区域
  .header-title {
    padding-top: 4.75px;
    margin-left: 30px;
    text-shadow: 0 4px 4px rgba(0, 30, 64, 0.22);
    font-family: YouSheBiaoTiHei;
    font-size: 44px;
    font-style: normal;
    font-weight: 400;
    line-height: 57px;
    letter-spacing: 13.2px;
    background: linear-gradient(0deg, #51B0F5 0%, #84C7F7 19%, #B0DBFA 37%, #D2EAFC 55%, #EBF5FD 72%, #F9FCFE 87%, #FFF 100%);
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
    margin-left: 205px;
    padding: 12.5px 0 15px;

    ::v-deep .el-menu {
      border-bottom: none;

      .el-menu-item {
        width: 125px;
        height: 39px;
        line-height: 30px;
        color: #838BA0;
        text-align: center;
        font-family: "Microsoft YaHei";
        font-size: 18px;
        font-weight: 600;
        border-bottom: none !important;
        &:hover {
          background-color: transparent !important;
        }

        &.is-active {
          color: #FFF;
          font-weight: 700;
          background-image: url("../../../assets/images/new-bi/nav-active.svg");
          background-size: 100% 100%;
        }
        & + .el-menu-item {
          margin-left: 12px;
        }
      }
    }
  }

  // 工具栏
  .toolbar {
    display: flex;
    align-items: flex-start;
    padding-top: 20px;
    // gap: $spacing-lg;

    .notification-badge {
      ::v-deep .el-badge__content {
        // background-color: $danger-color;
        border: none;
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
      // margin-top: -16px;
      color: #BFDFFF;
      text-shadow: 0 1px 3px rgba(5, 12, 25, 0.54);
      font-family: "Alibaba PuHuiTi";
      font-size: 16px;
      font-style: normal;
      font-weight: 400;
      line-height: 22px;
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