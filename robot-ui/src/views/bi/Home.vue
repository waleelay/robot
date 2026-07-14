<template>
  <div class="h100 pt94 pl72 common-scroll">
    <Header activeHead="home" />
    <!-- <DragExample /> -->
  </div>
</template>

<script>
  import Header from './components/Header.vue'
  // import DragExample from './components/drag/Index.vue'
  export default {
    name: 'BiHome',
    components: { Header },
    data() {
      return {
        
      }
    }
  }
</script>