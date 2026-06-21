<template>
  <el-dropdown trigger="click">
    <div @click="gotoScreen">
      <svg-icon class-name="size-icon" icon-class="table" />
    </div>
  </el-dropdown>
</template>

<script>
export default {
  data() {
    return {
    }
  },
  methods: {
    gotoScreen() {
      // this.$router.push({ path:"/bigScreen" }).catch(()=>{});
      const targetRoute = this.$router.resolve({
        path: '/bigScreen'
      })
      window.open(targetRoute.href, '_blank')
    }
  }

}
</script>
