<template>
  <div id="pieChart" class="chart-container w100 h100"></div>
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
      // projects: [
      //   { name: '打架斗殴', value: 25, color: '#26FFCB' },   // 柔和珊瑚红
      //   { name: '攀爬围栏', value: 25, color: '#24CBFF' },   // 琥珀色
      //   { name: '人员落单', value: 25, color: '#968DFF' },   // 翠绿
      //   { name: '人员聚集', value: 25, color: '#5375FF' },   // 天蓝
      //   { name: '外协陪同', value: 25, color: '#83D3FF' }    // 淡紫色
      // ],
      barChart: null,
      resizeHandler: null
    }
  },
  computed: {
    chartProjects() {
      const colors = ['#26FFCB', '#24CBFF', '#968DFF', '#5375FF', '#83D3FF'];
      const source = this.items && this.items.length ? this.items : [];
      return source.map((item, index) => ({
        name: item.name,
        value: item.percent || item.count || item.value || 0,
        color: item.color || colors[index % colors.length]
      }));
    },
    // 为第一层环形图准备数据 (每个项目原始value=25，总值125，但角度均分)
    firstLayerData() {
      return this.chartProjects.map(item => ({
        name: item.name,
        value: item.value,
        itemStyle: { color: item.color }
      }));
    },
    // 第二层阴影环形数据: 只需要一个数据占满整个圆环，实现全覆盖阴影效果
    secondLayerData() {
      // 单个数据占比100% 使圆环完整
      return [{ name: '阴影层', value: 100, itemStyle: { color: 'rgba(0, 19, 66, 0.6982)' } }];
    },
    // 图例名称列表（仅用于第一层，手动控制展示）
    legendNames() {
      return this.chartProjects.map(p => p.name);
    }
  },
  watch: {
    // projects: {
    //   deep: true,
    //   handler() {
    //     this.updateChart();
    //   }
    // },
    items: {
      deep: true,
      handler() {
        this.updateChart();
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
      const dom = document.getElementById('pieChart');
      if (dom) {
        this.barChart = this.$echarts.init(dom);
        this.renderGroupBarChart();
      }
    },
    updateChart() {
      this.renderGroupBarChart();
    },
    renderGroupBarChart() {
      if (!this.barChart) return;
      // 分组柱状图配置：三个系列，分别代表运行中、故障、离线，通过barCategoryGap和barGap控制并排不重叠
      const option = {
        // 工具箱辅助保存图片等
        toolbox: {
          show: false,
          feature: {
            saveAsImage: { title: '保存为图片' },
            restore: { title: '重置' }
          },
          right: 20,
          top: 10,
          iconSize: 16
        },
        // 图例配置: 右侧垂直居中，只显示第一层项目数据，并且展示原始占比数值
        legend: {
          orient: 'vertical',
          right: 50,           // 贴靠右侧
          top: 'center',      // 垂直居中
          align: 'left',
          itemWidth: 12,
          itemHeight: 12,
          itemGap: 10,
          textStyle: {
            color: '#FFF',
            padding: [0, 0, 0, 10],
            fontFamily: "Microsoft YaHei",
            fontSize: 16,
            fontWeight: 400,
            lineHeight: 28, /* 175% */
          },
          // 自定义图例格式：显示 “项目名 25%”
          // formatter: (name) => {
          //   const target = this.projects.find(p => p.name === name);
          //   if (target) {
          //     return `${name}  ${target.value}%`;
          //   }
          //   return name;
          // },
          // 只展示第一层项目名称，隐藏第二层系列
          data: this.legendNames,
          // backgroundColor: 'rgba(255, 255, 245, 0.85)',
          // borderRadius: 20,
          // padding: [12, 20, 12, 20],
          // itemGap: 14,
          // shadowBlur: 6,
          // shadowOffsetX: 2,
          // shadowColor: 'rgba(0,0,0,0.05)'
        },
        // 配色工具背景透明
        backgroundColor: 'transparent',
        // 系列定义: 第一层数据环形图 + 第二层阴影环形图
        series: [
          {
            // ========= 第一层：项目占比环形图 =========
            name: '项目占比',
            type: 'pie',
            // 圆心偏左 (左侧留出30%空间，饼图位于左侧区域，右边留给图例)
            center: ['28%', '50%'],
            // 半径严格按照要求: 外半径93%，内半径37.5%  -> 对应第一层 radius: ['37.5%', '93%']
            radius: ['31.5%', '78%'],
            // 避免标签重叠
            avoidLabelOverlap: true,
            // 标签与引导线配置: 展示具体占比数值（原始25%）
            label: {
              show: false,
              position: 'outside',
              formatter: (params) => {
                // params.value 为原始数值 25，显示为 25%
                return `${params.name}\n${params.value}%`;
              },
              lineHeight: 18,
              fontSize: 11,
              fontWeight: 'bold',
              color: '#1f2a3e',
              fontFamily: 'monospace'
            },
            labelLine: {
              length: 12,
              length2: 8,
              smooth: true,
              lineStyle: { width: 1.2, color: '#9aaebf' }
            },
            // 数据: 五个项目各占25%
            data: this.firstLayerData,
            // 环形样式: 圆角 + 边框白边
            // itemStyle: {
            //   borderRadius: 12,
            //   borderColor: '#ffffff',
            //   borderWidth: 2.5,
            //   shadowBlur: 6,
            //   shadowOffsetX: 2,
            //   shadowColor: 'rgba(0, 0, 0, 0.12)'
            // },
            // 高亮效果
            emphasis: {
              scale: true,
              label: { show: false, fontWeight: 'bold', fontSize: 12 }
            },
            // 不合并扇形，保持独立
            stillShowZeroSum: false,
            // 起始角度保持一致，与阴影层协调
            startAngle: 90,
            // 避免tooltip被阴影覆盖干扰
            tooltip: { valueFormatter: (value) => value + '%' }
          },
          {
            // ========= 第二层：阴影覆盖层 =========
            name: '阴影层',
            type: 'pie',
            // 圆心必须与第一层完全一致 (左侧28%, 垂直居中)
            center: ['28%', '50%'],
            // 半径按照要求: [内径37.5%, 外径57.5%]  完全覆盖第一层的内环区域
            radius: ['31.5%', '48.3%'],
            // 数据: 单个数据占满整圆，实现均匀阴影环
            data: this.secondLayerData,
            // 关闭标签、引导线、不显示任何文本
            label: { show: false },
            labelLine: { show: false },
            // 关闭鼠标悬浮高亮效果，避免干扰第一层的交互
            emphasis: { scale: false, label: { show: false } },
            // 静默模式，不触发tooltip
            silent: true,
            // 工具提示关闭
            tooltip: { show: false },
            // 扇区间隙为0，构成完整圆环
            itemStyle: {
              // 阴影颜色 #001342 + 透明度0.6982 (rgba表示)
              color: 'rgba(0, 19, 66, 0.6982)',
              borderWidth: 0,
              borderRadius: 0,
              shadowBlur: 0
            },
            // 起始角度与第一层一致保证对齐（实际上透明覆盖无影响）
            startAngle: 90,
            // 避免图例显示该系列
            legendHoverLink: false,
            // 不参与图例计算
            seriesLayoutBy: 'column'
          }
        ],
        // 全局tooltip配置, 仅第一层有有效tooltip
        tooltip: {
          trigger: 'item',
          formatter: (params) => {
            // 仅当系列名称为'项目占比'时展示详情，阴影层不展示
            if (params.seriesName === '项目占比') {
              return `<strong>${params.name}</strong><br/>占比: ${params.value}% (${this.items[params.dataIndex]})`;
            }
            return '';
          },
          backgroundColor: 'rgba(20, 28, 38, 0.9)',
          borderColor: '#2c3e4e',
          borderWidth: 1,
          textStyle: { color: '#f0f3f8', fontSize: 12 }
        },
        // 动画与额外可配置
        animation: true,
        animationDuration: 800,
        animationEasing: 'cubicOut'
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
