<template>
  <div class="personalControl">
    <div class="top-box">
      <!--      统计本次（重点人员）已到人员数、未到人员数、-->
      <div class="index-box1">
        <div class="index-item" v-for="(item, index) in indexList1">
          <div class="chart-box">
            <Echarts v-if="showEchart[index]" :option="options[index]" :ref="'chart'+index" />
          </div>
          <div class="index-name">{{ item.name }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from "vuex";
import { getRobotList } from "@/api/robot/robotMessage.js";
import Echarts from "@/components/largeScreen/EchartsTemp.vue";
import {getExportUserList} from '@/api/rollCall/people'

export default {
  name: "personalControl",
  components: {
    Echarts,
  },
  data() {
    return {
      indexList1: [{ name: "所有人员" }, { name: "重点人员" }],
      showEchart: [false, false],
      options: [[], []],
    };
  },
  computed: {
    ...mapState({
      arrivedNum: (state) => state.bigScreen.arrivedNum !==null?state.bigScreen.arrivedNum:0,
      unarrivedNum: (state) => state.bigScreen.unarrivedNum !==null?state.bigScreen.unarrivedNum:0,
      importArrivedNum: (state) => state.bigScreen.importArrivedNum !==null?state.bigScreen.importArrivedNum:0,
      importUnArrivedNum: (state) => state.bigScreen.importUnArrivedNum !==null?state.bigScreen.importUnArrivedNum:0,
    }),
    //统计已到未到人数
    chartData() {
      return [
        { name: "已到", value: this.arrivedNum },
        { name: "未到", value: this.unarrivedNum },
      ];
    },
    //统计重点人群已到未到人数
    chartData1() {
      return [
        { name: "已到", value: this.importArrivedNum},
        { name: "未到", value: this.importUnArrivedNum},
      ];
    },
  },
  watch: {
    chartData: {
      deep: true,
      handler(newData) {
        this.options[0].series[0].data = newData;

        // this.$refs.chart0.setOption(this.options[0]);
      },
    },
    chartData1: {
      deep: true,
      handler(newData) {
        this.options[1].series[0].data = newData;
        // this.$refs.chart1.setOption(this.options[1]);
      },
    },
  },
  mounted() {
    this.showEchart[0] = true;
    this.initOption();
    this.showEchart[1] = true;
    this.initOption1();
    this.$forceUpdate();
  },
  methods: {
    initOption() {
      this.options[0] = {
        tooltip: {
          show: false,
        },
        color: ["#6AE083", "#5FBAFF"],
        series: [
          {
            name: "",
            type: "pie",
            startAngle: "90",
            center: ["50%", "50%"],
            radius: ["40%", "50%"],
            label: {
              show: true,
              position: "outside",
              formatter: (params) => {
                return `{name|${params.name}}\n{num|${params.value}}`;
              },
              rich: {
                name: {
                  align: "center",
                  fontSize: 14,
                  color: "#c5e5ff",
                },
                num: {
                  align: "center",
                  fontSize: 20,
                  color: "#fff",
                  padding: [5, 0],
                },
              },
            },
            labelLine: {
              length: 10,
              length2: 30,
              show: true,
              color: "#fff",
            },
            emphasis: {
              scale: false,
            },
            data: this.chartData,
          },
        ],
      };
    },
    initOption1() {
      this.options[1] = {
        tooltip: {
          show: false,
        },
        color: ["#FF9F18", "#FF7218"],
        series: [
          {
            name: "",
            type: "pie",
            startAngle: "90",
            center: ["50%", "50%"],
            radius: ["40%", "50%"],
            label: {
              show: true,
              position: "outside",
              formatter: (params) => {
                return `{name|${params.name}}\n{num|${params.value}}`;
              },
              rich: {
                name: {
                  align: "center",
                  fontSize: 14,
                  color: "#c5e5ff",
                },
                num: {
                  align: "center",
                  fontSize: 20,
                  color: "#fff",
                  padding: [5, 0],
                },
              },
            },
            labelLine: {
              length: 10,
              length2: 30,
              show: true,
              color: "#fff",
            },
            emphasis: {
              scale: false,
            },
            data: this.chartData1,
          },
        ],
      };
    },
  },
};
</script>
