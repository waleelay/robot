<template>
  <div :id="id" :ref="id +'Ref'" :class="className" :style="{ height, width }" />
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'PieChart',
  props: {
    id: {
      type: String,
      default: 'pieChart'
    },
    width: {
      type: String,
      default: '100%'
    },
    height: {
      type: String,
      default: '100%'
    },
    name: {
      type: String,
      default: ''
    },
    className: {
      type: String,
      default: ''
    },
    data: {
      type: Object,
      default: () => ({}),
      required: true
    }
  },
  data() {
    return {
      chart: null,
      colors: ['#5689ee', '#55ce9f', '#586b8c', '#eab516']
    };
  },
  mounted() {
    this.initChart();
  },
  methods: {
    initChart() {
      this.chart = echarts.init(this.$refs[`${this.id}Ref`]);
      const option = {
        tooltip: {
          trigger: 'item',
          formatter: '{a} <br/>{b}: {c} ({d}%)'
        },
        legend: {
          orient: 'vertical',
          top: 'center',
          right: 0,
          data: this.data.legendData || [],
          itemWidth: 12,
          itemHeight: 12,
          itemGap: 12,
          itemStyle: {
            borderRadius: 1,
            lineHeight: 19,
            borderWidth: 0
          },
          textStyle: {
            color: '#FFF',
            align: 'center',
            fontFamily: 'Microsoft YaHei',
            fontSize: 14,
            lineHeight: 19
          }
        },
        series: [
          {
            name: '机器人类型',
            type: 'pie',
            radius: '50%',
            left: '-30%',
            // avoidLabelOverlap: false,
            itemStyle: {
                // borderRadius: 10,
                borderColor: '#fff',
                borderWidth: 1
            },
            label: {
                show: true,
                color: '#fff',
                formatter: '{b}: {c}',
                
            },
            emphasis: {
              // scaleSize: 1,
              label: {
                show: true,
                fontSize: 12,
                fontWeight: 'bold'
              },
            },
            labelLine: {
              show: true
            },
            data: this.data.legendData.map(
              (item, index) => ({
                ...this.data.data[index],
                itemStyle: {
                  color: this.colors[index]
                }
              }),
            )
          }
        ]
      };
      this.chart.setOption(option);
    }
  },
  watch: {
    data: function (newVal, oldVal) {
      if (this.chart) {
        const option = this.chart.getOption()
        option.legend[0].data = this.data.legendData || []
        option.series[0].data = this.data.legendData.map(
          (item, index) => ({
            ...this.data.data[index],
            itemStyle: {
              color: this.colors[index]
            }
          }),
        )
        this.chart.setOption(option);
      } else {
        this.initChart();
      }
    }
  },
  beforeDestroy() {
    clearInterval(this.timer);
  }
};
</script>
