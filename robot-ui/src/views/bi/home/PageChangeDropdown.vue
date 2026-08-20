 <template>
  <el-dropdown class="page-dropdown" trigger="click" :placement="$route.name === 'biIndex' ? 'top-end' : 'bottom-end'" @visible-change="isOpen = !isOpen" @command="goPage">
    <span :style="{ 'font-size': $route.name === 'biIndex' ? '16px' : '20px', color: '#AED1FF' }">
      <svg-icon icon-class="app1" />
    </span>
    <el-dropdown-menu slot="dropdown" class="custom-dropdown-menu page-dropdown-menu p10" :class="{'mt25': $route.name !== 'biIndex'}" :style="{ transform: $route.name !== 'biIndex' ? 'translateX(185px)' : 'translateX(0px)' }">
      <div class="d-flex">
        <el-dropdown-item v-for="item in pageList" :key="item.label" :title="item.value.includes('no-') ? '暂未开放' : ''" :command="item.value" class="flx-center flex-column wp76 hp68" :class="{ 'is-active': $route.name.includes(item.value) }">
          <svg-icon class="default-svg-icon" :icon-class="item.icon" style="font-size: 26px" />
          <svg-icon class="active-svg-icon" :icon-class="item.icon + 1" style="font-size: 26px" />
          <span class="text mt2">{{ item.label }}</span>
        </el-dropdown-item>
      </div>
    </el-dropdown-menu>
  </el-dropdown>
</template>

<script>
import { mapActions } from 'vuex';
import {
  BIGSCREEN_PERMISSIONS,
  PATROL_PAGES,
  firstPatrolRouteName,
  hasAnyBigscreenPermission,
  hasBigscreenPermission
} from '@/utils/bigscreen-access'

const pages = [
  { label: '指挥中心', value: 'biIndex', icon: 'page-home', permission: BIGSCREEN_PERMISSIONS.HOME },
  { label: '巡逻巡查', value: 'biPatrol', icon: 'page-patrol', permissions: PATROL_PAGES.map(page => page.permission) },
  { label: '人员管控', value: 'biStaff', icon: 'page-staff', permission: BIGSCREEN_PERMISSIONS.STAFF },
  { label: '生产安全', value: 'no-biSafety', icon: 'page-safety', permission: BIGSCREEN_PERMISSIONS.SAFETY },
  { label: '应急处置', value: 'no-biEmergency', icon: 'page-emergency', permission: BIGSCREEN_PERMISSIONS.EMERGENCY }
]
export default {
  name: 'PageChangeDropdown',
  computed: {
    pageList() {
      return pages.filter(item => {
        const visible = item.permissions
          ? hasAnyBigscreenPermission(this.permissions, item.permissions)
          : hasBigscreenPermission(this.permissions, item.permission)
        return visible && !this.$route.name.includes(item.value)
      })
    },
    permissions() {
      return this.$store.getters.bigscreenPermissions
    },
    activeCameras() {
      return this.$store.getters['websocketRobot/getActiveCameras']
    }
  },
  data() {
    return {
      isOpen: false
    }
  },
  methods: {
     ...mapActions('websocketRobot', ['stopCamera']),
    async clearCameras() {
      for (const [index, key] of Object.keys(this.activeCameras).entries()) {
        if (this.activeCameras[key]?.camera) {
          await this.stopCamera(this.activeCameras[key].camera);
        }
      }
    },
    async goPage(pathName) {
      if (pathName.includes('no-')) {
        this.$message({
          message: '暂未开放',
          type: 'warning'
        })
        return
      }
      await this.clearCameras()
      const routeName = pathName === 'biPatrol' ? firstPatrolRouteName(this.permissions) : pathName
      if (routeName) this.$router.push({ name: routeName })
    },
  }
}
</script>

<style lang="scss" scoped>
.page-dropdown {
  .page-btn {
    // padding: 0 15px 0 20px;
    padding: 0 20px;
    background: linear-gradient(0deg, rgba(16, 61, 135, 0.80) 14.29%, rgba(41, 113, 216, 0.80) 90.48%);
    border: 1px solid #1E4D91;
    color: #FFF;
    text-shadow: 0 1px 0 rgba(0, 22, 35, 0.20);
    font-family: "Microsoft YaHei";
    font-size: 20px;
    line-height: 42px; /* 100% */
    text-align: center;
  }
}
</style>
