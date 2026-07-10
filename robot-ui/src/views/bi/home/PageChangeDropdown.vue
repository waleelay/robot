 <template>
  <el-dropdown class="page-dropdown" trigger="click" :placement="$route.name === 'biIndex' ? 'top-end' : 'bottom-end'" @visible-change="isOpen = !isOpen" @command="goPage">
    <span :style="{ 'font-size': $route.name === 'biIndex' ? '16px' : '20px', color: '#AED1FF' }">
      <svg-icon icon-class="change" />
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

const pages = [
  { label: '指挥中心', value: 'biIndex', icon: 'page-home' },
  { label: '巡逻巡查', value: 'biPatrol', icon: 'page-patrol' },
  { label: '人员管控', value: 'no-biStaff', icon: 'page-staff' },
  { label: '生产安全', value: 'no-biSafety', icon: 'page-safety'},
  { label: '应急处置', value: 'no-biEmergency', icon: 'page-emergency' }
]
export default {
  name: 'PageChangeDropdown',
  computed: {
    pageList() {
      return pages.filter((item) => !this.$route.name.includes(item.value))
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
      this.$router.push({ name: pathName })
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
