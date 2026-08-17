<template>
  <div class="user-menu">
    <el-dropdown
      class="user-dropdown"
      trigger="click"
      placement="bottom-end"
      @command="handleCommand"
    >
      <div class="user flx-align-center h100" :class="sizeClass">
        <div class="avatar flx-center">{{ userInitials }}</div>
        <div class="ml10 flex-column">
          <span class="text">{{ userName }}</span>
          <span class="role" :class="rolePaddingClass">{{ userRole }}</span>
        </div>
      </div>
      <el-dropdown-menu slot="dropdown" class="custom-dropdown-menu user-menu-dropdown">
        <el-dropdown-item command="switchAccount" class="user-menu-item p10">
          <div class="flx-align-center">
            <svg-icon icon-class="change" class="item-icon" />
            <span class="ml10">切换账号</span>
          </div>
        </el-dropdown-item>
        <el-dropdown-item command="logout" class="user-menu-item p10">
          <div class="flx-align-center">
            <svg-icon icon-class="logout" class="item-icon" />
            <span class="ml10">退出登录</span>
          </div>
        </el-dropdown-item>
      </el-dropdown-menu>
    </el-dropdown>
  </div>
</template>

<script>
import { logout, switchAccount, tokenClaims } from '@/auth'

export default {
  name: 'UserMenu',
  props: {
    size: {
      type: String,
      default: 'md',
      validator: (value) => ['sm', 'md'].includes(value)
    }
  },
  data() {
    return {
      claims: {}
    }
  },
  computed: {
    sizeClass() {
      return this.size === 'sm' ? 'user--sm' : 'user--md'
    },
    rolePaddingClass() {
      return this.size === 'sm' ? 'pt0 pr10 pb0 pl10' : 'pt2 pr10 pb2 pl10'
    },
    userName() {
      return (
        this.claims.preferred_username
        || this.claims.name
        || this.claims.given_name
        || this.claims.email
        || '平台管理员'
      )
    },
    userRole() {
      const realmRoles = (this.claims.realm_access && this.claims.realm_access.roles) || []
      const priority = ['platform_admin', 'admin', 'super_admin', 'operator', 'auditor', 'user']
      for (const role of priority) {
        if (realmRoles.includes(role)) return this.mapRoleLabel(role)
      }
      if (realmRoles.length) {
        return this.mapRoleLabel(realmRoles.find(r => !r.startsWith('default-')) || realmRoles[0])
      }
      return '管理员'
    },
    userInitials() {
      const source = (this.userName || '').trim()
      if (!source) return 'U'
      const ascii = source.match(/[A-Za-z]/g)
      if (ascii && ascii.length) return ascii.slice(0, 1).join('').toUpperCase()
      return source.slice(0, 1).toUpperCase()
    }
  },
  async mounted() {
    try {
      this.claims = await tokenClaims()
    } catch (error) {
      this.claims = {}
    }
  },
  methods: {
    mapRoleLabel(role) {
      const map = {
        platform_admin: '平台管理员',
        super_admin: '超级管理员',
        admin: '管理员',
        operator: '运维',
        auditor: '审计员',
        user: '用户'
      }
      return map[role] || role
    },
    handleCommand(command) {
      if (command === 'logout') {
        this.handleLogout()
        return
      }
      if (command === 'switchAccount') {
        this.handleSwitchAccount()
      }
    },
    async handleLogout() {
      try {
        await this.$primaryConfirm({
          title: '退出登录',
          message: '是否退出当前账号',
          confirmText: '确定',
          cancelText: '取消',
          onConfirm: async () => {
            try {
              await logout()
            } catch (error) {
              this.$message.error('退出登录失败')
              throw error
            }
          }
        })
      } catch (error) {
      }
    },
    async handleSwitchAccount() {
      try {
        await switchAccount()
      } catch (error) {
        this.$message.error('切换账号失败')
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.user-menu {
  height: 100%;
}

.user-dropdown {
  height: 100%;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.user {
  position: relative;
  &::before {
    position: absolute;
    left: -20px;
    width: 1px;
    height: 32px;
    background: rgba(156, 184, 212, 0.50);
    content: "";
  }
  .avatar {
    text-align: center;
    background: linear-gradient(180deg, #0080D5 0%, #0054CC 100%);
    border: 1px solid #5AA0FF;
    border-radius: 50%;
    color: #AED1FF;
    font-family: "Microsoft YaHei";
    font-weight: 700;
  }
  .text {
    color: #BFDFFF;
    text-shadow: 0 1px 3px rgba(5, 12, 25, 0.54);
    font-family: Bahnschrift;
  }
  .role {
    display: block;
    width: fit-content;
    color: #BFDFFF;
    font-family: "Alibaba PuHuiTi";
    font-size: 10px;
    line-height: 14px;
    opacity: 0.8;
    background: #00589A;
  }

  &--sm {
    &::before {
      top: 4px;
    }
    .avatar {
      width: 28px;
      height: 28px;
      font-size: 16px;
    }
    .text {
      font-size: 14px;
      line-height: 17px;
    }
  }

  &--md {
    &::before {
      top: 24px;
    }
    .avatar {
      width: 36px;
      height: 36px;
      font-size: 20px;
    }
    .text {
      font-size: 18px;
      line-height: 22px;
    }
  }
}
</style>
