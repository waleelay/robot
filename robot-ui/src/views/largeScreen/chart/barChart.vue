<template>
  <div :id="id" :ref="id +'Ref'" :class="className" :style="{ height, width }" />
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'BarChart',
  props: {
    id: {
      type: String,
      default: 'barChart'
    },
    className: {
      type: String,
      default: ''
    },
    width: {
      type: String,
      default: '100%'
    },
    height: {
      type: String,
      default: '100%'
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
  methods: {
    initChart() {
      this.chart = echarts.init(this.$refs[`${this.id}Ref`]);
      const options = {
        grid: {
          // top: 45,
          // right: 5,
          // bottom: 32,
          // left: 45,
          left: '2%',
          right: '0%',
          bottom: '3%',
          containLabel: true
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          },
          formatter: function(params) {
            var result = params[0].axisValue + '<br/>';
            var total = 0;
            params.forEach(function(item) {
                result += item.marker + ' ' + item.seriesName + ': ' + item.value + '<br/>';
                total += item.value;
            });
            result += '<b>总计: ' + total + '</b>';
            return result;
        }
        },
        legend: {
          top: 1,
          left: 0,
          data: this.data.legendData || [],
          itemWidth: 12,
          itemHeight: 12,
          itemGap: 12,
          itemStyle: {
            borderRadius: 1,
            lineHeight: 19
          },
          textStyle: {
            color: '#FFF',
            align: 'center',
            fontFamily: 'Microsoft YaHei',
            fontSize: 14,
            lineHeight: 19
          }
        },
        xAxis: [
          {
            type: 'category',
            axisTick: { show: false },
            data: this.data.xData || [],
            axisLabel: {
              color: '#fff',
              align: 'center',
              fontFamily: 'Microsoft YaHei',
              fontSize: 12,
              lineHeight: 16,
              margin: 15
            },
            axisLine: {
              show: false
            }
          }
        ],
        yAxis: [
          {
            name: '',
            nameTextStyle: {
              color: '#fff',
              fontFamily: 'Microsoft YaHei',
              fontSize: 14,
              lineHeight: 20,
              align: 'left',
              padding: [0, 15, -5, -20]
            },
            type: 'value',
            axisLabel: {
              color: '#fff',
              fontFamily: 'Microsoft YaHei',
              fontSize: 14,
              lineHeight: 20,
              margin: 15,
              align: 'right'
            },
            splitLine: {
              show: true,
              lineStyle: {
                color: 'rgba(0, 0, 0, 0.20)',
                width: 0.7,
                type: 'dashed'
              }
            }
          }
        ],
        series: this.data.legendData.map(
          (item, index) => ({
            name: item,
            type: 'bar',
            stack: 'total',
            barGap: 0,
            barWidth: 20,
            label: { show: false },
            emphasis: {
              focus: 'series'
            },
            data: this.data.yData[index],
            color: this.colors[index]
          }),
        )
      };
      this.chart.setOption(options);
    }
  },
  mounted() {
    this.initChart();
    window.addEventListener('resize', () => {
      if (this.chart) {
        this.chart.resize();
      }
    });
  },
  watch: {
    data: function (newVal, oldVal) {
      if (this.chart) {
        const options = this.chart.getOption();
        options.legend[0].data = this.data.legendData || [];
        options.xAxis[0].data = this.data.xData || [];
        console.log(123, this.data.legendData.length);
        
        options.yAxis[0].name = this.data.legendData.length ? '次数' : '';
        options.series = this.data.legendData.map(
          (item, index) => ({
            name: item,
            type: 'bar',
            barGap: 0,
            barWidth: 14,
            label: { show: false },
            emphasis: {
              focus: 'series'
            },
            data: this.data.yData[index],
            color: this.colors[index]
          }),
        );
        this.chart.setOption(options);
      }
    }
  }
};
</script>
