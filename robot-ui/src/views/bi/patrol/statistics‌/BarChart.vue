<template>
  <div id="groupBarChart" class="chart-container w100 h100"></div>
</template>

<script>
export default({
  data() {
    return {
      // 设备基础数据 (从题目获取)
      devices: [
        { name: '无人车', total: 90836 },
        { name: '机器狗', total: 905236 },
        { name: '无人机', total: 925436 },
        { name: '机器人', total: 925436 }
      ],
      // 状态比例: 运行中70%，故障15%，离线15%
      runningRatio: 0.70,
      faultRatio: 0.15,
      offlineRatio: 0.15,
      // echarts 实例
      barChart: null,
      resizeHandler: null
    }
  },
  computed: {
    // 计算每个设备的三个精确数值（确保整数，总和等于总量）
    deviceStatusData() {
      return this.devices.map(device => {
        const total = device.total;
        let running = Math.floor(total * this.runningRatio);
        let fault = Math.floor(total * this.faultRatio);
        // 剩余全部分配给离线，避免精度丢失
        let offline = total - running - fault;
        // 极小数矫正（防止负值）
        if (offline < 0) offline = 0;
        return {
          name: device.name,
          running: running,
          fault: fault,
          offline: offline
        };
      });
    },
    // x轴类别：设备名称列表
    categories() {
      return this.deviceStatusData.map(item => item.name);
    },
    // 运行中数据数组
    runningValues() {
      return this.deviceStatusData.map(item => item.running);
    },
    // 故障数据数组
    faultValues() {
      return this.deviceStatusData.map(item => item.fault);
    },
    // 离线数据数组
    offlineValues() {
      return this.deviceStatusData.map(item => item.offline);
    }
  },
  watch: {
      // 数据变化重新渲染柱状图（确保分组柱状图更新）
      deviceStatusData: {
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
      const dom = document.getElementById('groupBarChart');
      if (dom) {
        this.barChart = this.$echarts.init(dom);
        this.renderGroupBarChart();
      }
    },
    renderGroupBarChart() {
      if (!this.barChart) return;
      // 分组柱状图配置：三个系列，分别代表运行中、故障、离线，通过barCategoryGap和barGap控制并排不重叠
      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: function(params) {
            // params 是每个系列对应的数据项
            let result = `<strong>${params[0].axisValue}</strong><br/>`;
            params.forEach(p => {
              result += `${p.marker} ${p.seriesName}: ${p.value.toLocaleString()} 台<br/>`;
            });
            return result;
          }
        },
        legend: {
          data: ['运行中', '故障', '离线'],
          left: 'center',
          bottom: 0,
          itemWidth: 12,
          itemHeight: 12,
          itemGap: 20,
          textStyle: { color: '#FFF', fontSize: 14, fontStyle: 'normal', fontFamily: 'Microsoft YaHei', fontWeight: 400, lineHeight: 28, paddingLeft: 10 },
          // backgroundColor: 'rgba(255,255,245,0.8)',
          borderRadius: 1,
          padding: 0,
        },
        grid: {
          left: 0,
          right: 0,
          top: 35,
          bottom: 30,
          containLabel: true,
          borderWidth: 0
        },
        xAxis: {
          type: 'category',
          data: this.categories,
          axisLabel: {
            interval: 1,
            margin: 16,
            color: 'rgba(255, 255, 255, 0.65)',
            fontFamily: 'Microsoft YaHei',
            fontSize: 14,
            fontStyle: 'normal',
            fontWeight: 600,
            lineHeight: 16.156, /* 115.397% */
          },
          axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.20)', } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          name: '装备运行时长：小时',
          nameTextStyle: { padding: [0, 0, 0, 60], fontSize: 14, lineHeight: 18, color: 'rgba(255, 255, 255, 0.70)', fontFamily: 'Microsoft YaHei', textAlign: 'left' },
          axisLabel: {
            color: 'rgba(255, 255, 255, 0.7)',
            fontFamily: 'Microsoft YaHei',
            fontSize: 12,
            lineHeight: 16.156, /* 115.397% */
            formatter: function(value) {
              if (value >= 1e6) return (value / 1e6).toFixed(1) + 'M';
              if (value >= 1e3) return (value / 1e3).toFixed(0) + 'k';
              return value;
            }
          },
          splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.10)' } }
        },
        series: [
          {
            name: '运行中',
            type: 'bar',
            data: this.runningValues,
            color: '#0C96FF',
            barWidth: '20%',           // 每组柱子总宽度的20% (相对宽度)
            barGap: '30%',              // 不同系列之间的间隙百分比，由于三个系列，控制并排且无重叠
            // 为了让三个柱子并排完全分开: 每个系列独立柱子，间隙合适即可
            itemStyle: {
              borderRadius: [2, 2, 0, 0],
              color: '#0C96FF',
              shadowColor: 'rgba(0,0,0,0.05)',
              shadowBlur: 4
            },
            // 柱子上方显示的数量 
            label: {
              show: false,
              position: 'top',
              formatter: (params) => {
                  return params.value > 500 ? params.value.toLocaleString() : '';
              },
              fontSize: 10,
              fontWeight: 'bold',
              color: '#1e5e2d'
            }
          },
          {
            name: '故障',
            type: 'bar',
            data: this.faultValues,
            color: '#FF9000',
            barWidth: '20%',
            barGap: '30%',
            itemStyle: {
              borderRadius: [2, 2, 0, 0],
              color: '#FF9000',
              shadowColor: 'rgba(0,0,0,0.05)',
              shadowBlur: 4
            },
            label: {
              show: false,
              position: 'top',
              formatter: (params) => {
                return params.value > 200 ? params.value.toLocaleString() : '';
              },
              fontSize: 10,
              color: '#b45f1b'
            }
          },
          {
            name: '离线',
            type: 'bar',
            data: this.offlineValues,
            color: '#B6B6B6',
            barWidth: '20%',
            barGap: '30%',
            itemStyle: {
              borderRadius: [2, 2, 0, 0],
              color: '#B6B6B6',
              shadowColor: 'rgba(0,0,0,0.05)',
              shadowBlur: 4
            },
            label: {
              show: false,
              position: 'top',
              formatter: (params) => {
                return params.value > 200 ? params.value.toLocaleString() : '';
              },
              fontSize: 10,
              color: '#f00 '
            }
          }
        ],
        // 增加背景网格轻量化
        backgroundColor: 'transparent',
        toolbox: {
          show: false,
          feature: {
            saveAsImage: { title: '保存为图片' },
            restore: { title: '重置' }
          },
          right: 20,
          iconSize: 16
        }
      };
      this.barChart.setOption(option, true);
    }
  }
});
</script>