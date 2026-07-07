<template>
  <div id="lineChart" class="chart-container w100 h100"></div>
</template>

<script>
export default({
  props: {
    points: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      list: [4, 5, 4, 7, 4, 3, 3],
      xTimeData: [6.13, 6.12, 6.11, 6.10, 6.09, 6.08, 6.07],
      barChart: null,
      resizeHandler: null
    }
  },
  computed: {
    chartLabels() {
      return this.points && this.points.length ? this.points.map(item => item.label || item.date) : this.xTimeData;
    },
    chartValues() {
      return this.points && this.points.length ? this.points.map(item => item.count || 0) : this.list;
    }
  },
  watch: {
    points: {
      deep: true,
      handler() {
        this.renderGroupBarChart();
      }
    }
  },
  mounted() {
    this.initChart();
    // 窗口自适应
    this.resizeHandler = () => {
      if (this.barChart) this.barChart.resize();
    };
    window.addEventListener('resize', this.resizeHandler);
  },
  beforeDestroy() {
    if (this.barChart) {
      this.barChart.dispose();
      this.barChart = null;
    }
    if (this.resizeHandler) {
      window.removeEventListener('resize', this.resizeHandler);
    }
  },
  methods: {
    initChart() {
      const dom = document.getElementById('lineChart');
      if (dom) {
        this.barChart = this.$echarts.init(dom);
        this.renderGroupBarChart();
      }
    },
    renderGroupBarChart() {
      if (!this.barChart) return;
      // 分组柱状图配置：三个系列，分别代表运行中、故障、离线，通过barCategoryGap和barGap控制并排不重叠
      const option = {
        color: '#678FE6',
        grid: {
          top: 60,
          left: 0,
          bottom: 0,
          right: 0,
          containLabel: true
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'line',
            lineStyle: {
              type: 'solid',
              // color: '#1968FF'
              color: new this.$echarts.graphic.LinearGradient(
                0,
                0,
                0,
                1,
                [
                  {
                    offset: 0,
                    color: 'rgba(54, 161, 255, 0.00)'
                  },
                  {
                    offset: 0.064,
                    color: 'rgba(49, 151, 255, 0.10)'
                  },
                  {
                    offset: 0.374,
                    color: '#1968FF'
                  },
                  {
                    offset: 0.69,
                    color: 'rgba(25, 104, 255, 0.85)'
                  },
                  {
                    offset: 0.924,
                    color: 'rgba(52, 157, 255, 0.10)'
                  },
                  {
                    offset: 1,
                    color: 'rgba(54, 161, 255, 0.00)'
                  }
                ],
                false
              ),
            }
          },
          transitionDuration: 0,
          backgroundColor: '#336EDF',
          padding: [7, 10],
          borderWidth: 0,
          borderColor: '#336EDF',
          borderRadius: 4,
          textStyle: {
            fontSize: 14,
            color: '#FFF',
            fontFamily: 'Bahnschrift',
            lineHeight: 17
          },
          formatter: `{b}：{c}次`
        },
        xAxis: {
          axisLine: {
            //坐标轴轴线相关设置。数学上的x轴
            show: true,
            lineStyle: {
              color: ' rgba(187, 199, 208, 0.50)'
            }
          },
          axisLabel: {
            fontSize: 12,
            fontFamily: 'Bahnschrift, sans-serif',
            color: '#BED0DB',
            lineHeight: 16,
            padding: [10, 0, 0, 0]
          },
          // boundaryGap: false,
          axisTick: { show: false },
          data: this.chartLabels
        },
        yAxis: {
          name: '单位（次）',
          nameTextStyle: {
            padding: [0, 0, 0, 20],
            fontSize: 14,
            fontFamily: 'Microsoft YaHei',
            color: 'rgba(122, 155, 189, 0.80)',
            lineHeight: 20
          },
          // min: 0,
          // max: 200,

          // min: 0,
          // max: 200,
          minInterval: 1,
          // interval: 1,
          // splitNumber: 5,
          type: 'value',
          splitLine: {
            show: true,
            lineStyle: {
              color: '#7B8893',
              type: 'dashed',
              opacity: 0.25
            }
          },
          axisLine: {
            show: false
          },
          axisTick: {
            show: false
          },
          axisLabel: {
            fontSize: 14,
            fontFamily: 'Bahnschrift',
            color: '8AB6E3',
            lineHeight: 14,
            color: '#8AB6E3',
            fontWeight: 350,
            lineHeight: 20 /* 142.857% */
          }
        },
        series: [
          {
            data: this.chartValues,
            type: 'line',
            smooth: true,
            symbol: 'none',
            // symbol: 'emptyCircle', //空心小圆点。线条小圆点形状
            // symbolSize: 0.01,
            emphasis: {
              scale: 999
            },
            lineStyle: {
              color: new this.$echarts.graphic.LinearGradient(
                0,
                0,
                0,
                1,
                [
                  {
                    offset: 0,
                    color: '#3DC5FF'
                  },
                  {
                    offset: 1,
                    color: '#0E65FF'
                  }
                ],
                false
              ),
              width: 2
            },
            areaStyle: {
              color: new this.$echarts.graphic.LinearGradient(
                0,
                0,
                0,
                1,
                [
                  {
                    offset: 0,
                    color: 'rgba(14, 101, 255, 0.5)'
                  },
                  {
                    offset: 0.8,
                    color: 'rgba(14, 101, 255, 0)'
                  }
                ],
                false
              ),
              shadowColor: 'rgba(0, 0, 0, 0.1)',
              shadowBlur: 10
            }
          }
        ]
      };
      this.barChart.setOption(option, true);
    },
    setData(num) {
      if (this.xTimeData.length === 7) {
        this.xTimeData.shift();
        this.list.shift();
      }
      const time = new Date().toLocaleTimeString();
      this.xTimeData.push(time);
      this.list.push(num);

      this.renderGroupBarChart();
    },
    renderChart(el) {
      if (this.barChart) {
        const option = this.barChart.getOption();
        option.xAxis[0].data = this.xTimeData;
        option.series[0].data = this.list;
        this.barChart.setOption(option);
        return;
      } else {
        this.renderGroupBarChart();
      }
    }
  }
});
</script>
