<template>
  <div id="groupBarChart1" class="chart-container w100 h100"></div>
</template>

<script>
export default({
  props: {
    items: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      // echarts 实例
      barChart: null,
      resizeHandler: null
    }
  },
  watch: {
      // 数据变化重新渲染柱状图（确保分组柱状图更新）
      items: {
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
      const dom = document.getElementById('groupBarChart1');
      if (dom) {
        this.barChart = this.$echarts.init(dom);
        this.renderGroupBarChart();
      }
    },
    renderGroupBarChart() {
      const source = this.items && this.items.length
        ? this.items
        : [
          // { name: '语音播报', count: 100 },
          // { name: '自动调度', count: 200 },
          // { name: '远程控制', count: 300 },
          // { name: '人工处理', count: 400 }
        ]
      const names = source.map(item => item.name)
      const nums = source.map(item => item.count || item.value || 0)
      if (!this.barChart) return;
      // 分组柱状图配置：三个系列，分别代表运行中、故障、离线，通过barCategoryGap和barGap控制并排不重叠
      const option = {
        grid: {
          left: 0,
          right: 0,
          top: 33,
          bottom: 0,
          containLabel: true
        },
        legend: {
          show: false
        },
        xAxis: {
          type: 'category',
          axisTick: {
            show: false,
            alignWithLabel: true
          },
          axisLine: {
            show: false
          },
          axisLabel: {
            fontSize: 12,
            // padding: [20, 0],
            fontFamily: 'Microsoft YaHei',
            lineHeight: 16,
            color: '#BED0DB',
            textAlign: 'center'
          },
          data: names
        },
        yAxis: {
          name: '单位（次）',
          nameTextStyle: {
            padding: [0, 0, 0, 10],
            // align: 'left',
            fontSize: 14,
            fontFamily: 'Microsoft YaHei',
            fontWeight: 400,
            color: '#8AB6E3',
            lineHeight: 20
          },
          type: 'value',
          minInterval: 1,
          axisLabel: {
            fontSize: 14,
            fontFamily: 'Bahnschrift',
            fontWeight: 350,
            color: '#8AB6E3',
            lineHeight: 20
          },
          axisTick: {
            show: false
          },
          axisLine: {
            show: false
          },
          splitLine: {
            show: true, // 不显示横轴
            lineStyle: {
              type: 'dashed',
              color: '#7B8893',
              opacity: 0.25
            }
          }
        },
        series: [
          {
            name: '哈哈哈',
            type: 'bar',
            barWidth: 18,
            showBackground: true,
            backgroundStyle: { color: 'rgba(185, 230, 245, 0.20)' },
            itemStyle: {
              normal: {
                color: new this.$echarts.graphic.LinearGradient(0, 0, 0, 1, [
                  {
                    offset: 0,
                    color: 'rgba(24, 144, 255, 1)'
                  },
                  {
                    offset: 1,
                    color: 'rgba(24, 144, 255, .2)'
                  }
                ])
              }
            },
            data: nums
          }
        ]
      };
      this.barChart.setOption(option, true);
    }
  }
});
</script>
