<template>
  <div class="auth-fail-page">
    <div class="auth-fail-card">
      <div class="auth-fail-icon" aria-hidden="true">
        <span class="auth-fail-icon__mark">!</span>
      </div>
      <p class="auth-fail-kicker">{{ kicker }}</p>
      <h1 class="auth-fail-title">{{ title }}</h1>
      <p class="auth-fail-desc">{{ description }}</p>
      <div class="auth-fail-actions">
        <el-button
          class="auth-fail-btn auth-fail-btn--primary"
          type="primary"
          icon="el-icon-refresh"
          :loading="reloading"
          @click="reloadPermissions"
        >
          重新加载权限
        </el-button>
        <el-button
          class="auth-fail-btn auth-fail-btn--ghost"
          icon="el-icon-switch-button"
          :loading="loggingOut"
          @click="handleLogout"
        >
          退出当前账号
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { logout } from '@/auth'
import { firstAccessibleRouteName } from '@/utils/bigscreen-access'

const LOAD_FAILED_MESSAGE = '无法加载当前用户的业务权限，请稍后重试。'

export default {
  name: 'Page401',
  data() {
    return {
      reloading: false,
      loggingOut: false
    }
  },
  computed: {
    isLoadFailed() {
      return this.$route.query.reason === 'loadFailed'
    },
    kicker() {
      return this.isLoadFailed ? '身份权限初始化失败' : '访问权限不足'
    },
    title() {
      return this.isLoadFailed ? '暂时无法进入管理台' : '暂时无法进入该页面'
    },
    description() {
      return this.isLoadFailed
        ? '当前用户的业务权限暂时无法加载。你可以重新加载权限，或退出当前账号后切换其他账号登录。'
        : '当前账号没有该页面的访问权限。你可以重新加载权限，或退出当前账号后切换其他账号登录。'
    }
  },
  mounted() {
    if (this.isLoadFailed) {
      this.showLoadFailedMessage()
    }
  },
  methods: {
    showLoadFailedMessage() {
      this.$message({
        type: 'error',
        message: LOAD_FAILED_MESSAGE,
        duration: 4000,
        showClose: true
      })
    },
    async reloadPermissions() {
      if (this.reloading || this.loggingOut) return
      this.reloading = true
      try {
        await this.$store.dispatch('bigscreenAccess/reset')
        await this.$store.dispatch('bigscreenAccess/load')
        const routeName = firstAccessibleRouteName(this.$store.getters.bigscreenPermissions)
        if (routeName) {
          this.$router.replace({ name: routeName })
          return
        }
        this.$message.warning('当前账号没有可访问的页面，请退出后切换账号')
      } catch (error) {
        this.showLoadFailedMessage()
      } finally {
        this.reloading = false
      }
    },
    async handleLogout() {
      if (this.reloading || this.loggingOut) return
      this.loggingOut = true
      try {
        await this.$store.dispatch('bigscreenAccess/reset')
        await logout()
      } catch (error) {
        this.loggingOut = false
        this.$message.error('退出登录失败')
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.auth-fail-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: linear-gradient(180deg, #eef2f7 0%, #f5f7fb 48%, #e9eef5 100%);
}

.auth-fail-card {
  width: 640px;
  max-width: 100%;
  padding: 48px 56px 40px;
  text-align: center;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
}

.auth-fail-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: #f8ebd0;

  &__mark {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: #f5b400;
    color: #fff;
    font-size: 26px;
    font-weight: 700;
    line-height: 1;
  }
}

.auth-fail-kicker {
  margin: 0 0 8px;
  color: #9aa3af;
  font-size: 14px;
  line-height: 22px;
}

.auth-fail-title {
  margin: 0 0 12px;
  color: #111827;
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
}

.auth-fail-desc {
  margin: 0 auto 28px;
  max-width: 460px;
  color: #6b7280;
  font-size: 14px;
  line-height: 24px;
}

.auth-fail-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 12px;
}

.auth-fail-btn {
  min-width: 148px;
  height: 40px;
  padding: 0 18px;
  font-size: 14px;
  border-radius: 8px;

  &--primary {
    background: #2563eb;
    border-color: #2563eb;

    &:hover,
    &:focus {
      background: #1d4ed8;
      border-color: #1d4ed8;
    }
  }

  &--ghost {
    color: #4b5563;
    background: #fff;
    border-color: #e5e7eb;

    &:hover,
    &:focus {
      color: #111827;
      background: #f9fafb;
      border-color: #d1d5db;
    }
  }
}
</style>
