<template>
  <div style="height: calc(100% - 79px)">
    <Header activeHead="vehicle" />
    <Side />
    <SplitScreenTest class="wp500" />
    
  </div>
</template>

<script>
import Header from './components/Header.vue';
import Side from './components/Side.vue'
import SplitScreenTest from './demo/components/SplitScreenTest.vue'
export default {
  name: 'BiVehicle',
  components: { Header, Side, SplitScreenTest },
  data() {
    return {
    }
  },
  
}
</script>