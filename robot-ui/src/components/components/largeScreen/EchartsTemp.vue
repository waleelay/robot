<template>
  <div
    class="chart-box"
    ref="myChartsRef"
    :style="{ height: height, width: width }"
    :option="option"
  />
</template>
<script>
import { init } from 'echarts';

export default {
  name: 'EchartsTemp',
  props: {
    width: {
      type: String,
      default: '100%'
    },
    height: {
      type: String,
      default: '100%'
    },
    option: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      myChart: null,
      timer: null
    };
  },
  methods: {
    initChart() {
      if (this.myChart !== null) {
        this.myChart.dispose();
      }
      this.myChart = init(this.$refs.myChartsRef);
      this.myChart?.setOption(this.option, true);
    },
    resizeChart() {
      this.timer = setTimeout(() => {
        if (this.myChart) {
          this.myChart.resize();
        }
      }, 500);
    }
  },
  watch: {
    option: {
      handler(val) {
        console.log("Echarts option 变化:", val);
        if (this.myChart) {
          this.myChart.setOption(val, true);
        } else {
          this.initChart();
        }
      },
      deep: true
    }
  },
  mounted() {
    this.initChart();
    window.addEventListener('resize', this.resizeChart);
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart);
    clearTimeout(this.timer);
    this.timer = null;
  }
};
</script>
<style scoped lang="scss">
.chart-box {
  margin-bottom: 10px;
}
</style>
