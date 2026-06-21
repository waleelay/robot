<template>
  <div class="personalControl">
    <div class="top-box">
      <div class="title" @click="toggleDialog">人员清点信息</div>
<!--      任务总点数以及预计剩余时间-->
      <div class="index-box">
        <div class="index-item" v-for="item in indexList">
          <div class="index-name">{{ item.name }}</div>
          <div class="index-value">
            <div class="value">{{ item.value }}</div>
            <div class="unit">{{ item.unit }}</div>
          </div>
        </div>
      </div>
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
<!--    未到人员列表-->
    <div class="btm-box">
      <div class="title">未到人员</div>
      <div class="list-box">
        <div
          class="list-item"
          :class="{ 'is-dangerous': item.isImportant }"
          v-for="item in currDataList"
          @click="toggleDialog(item)"
        >
          <img :src="getImageSrc(item.pictureData)" class="img" />
          <div class="item-title">{{ item.userName }}</div>
          <div class="item-area">{{ item.pointName }}</div>
        </div>
      </div>
      <div class="btn-box" v-if="unarrivedUsers.length > 4">
        <div
          class="btn-item"
          :class="{ active: currIndex == index }"
          v-for="(item, index) in btnList"
          @click="handleIndex(index)"
        ></div>
      </div>
    </div>
<!--    点击人员展示人员详情-->
    <el-dialog
      class="dialog-wrapper"
      v-dialogDrag
      width="1270px"
      height="912px"
      :visible.sync="dialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      :before-close="handleClose"
      title="人员信息"
    >
      <div class="dialog-content">
        <div class="info-box" v-for="item in infoList">
          <div class="info-title">{{ item.title }}</div>
          <div class="info-details">
            <div class="info-row" v-for="data in item.dataList">
              <span class="label">{{ data.label }}：</span>
              <span class="value">{{ infoData[data.key] }}{{ data.unit }}</span>
            </div>
          </div>
        </div>
        <img :src="getImageSrc(infoData.imgName)" class="info-image" />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { mapState } from "vuex";
import { getRobotList } from "@/api/robot/robotMessage.js";
import Echarts from "@/components/largeScreen/EchartsTemp.vue";
import {getExportUserList} from '@/api/rollCall/people'

export default {
  name: "personalControl",
  props: {
    dogId: {
      type: [Number, String],
      required: true,
    }
  },
  components: {
    Echarts,
  },
  data() {
    return {
      value: 1,
      dogList: [],
      queryParams: {
        dogState: 1,
        dogName: "",
        pageNum: 1,
        pageSize: 50,
      },
      currentIndex: 0,
      showInfo: false,
      personInfo: {},
      timer: null,
      dialogVisible: false,

      indexList1: [{ name: "所有人员" }, { name: "重点人员" }],
      infoList: [
        {
          title: "基本信息",
          dataList: [
            { label: "姓名", key: "userName" },
            { label: "年龄", key: "age", unit: "岁" },
            { label: "特征", key: "feature" },
            { label: "文化程度", key: "education" },
            { label: "捕前地址", key: "address" },
            { label: "捕前职业", key: "job" },
          ],
        },
        {
          title: "犯罪情况",
          dataList: [
            { label: "犯罪性质", key: "type" },
            { label: "刑罚种类", key: "originalType" },
            { label: "刑期", key: "period" },
            { label: "释放日期", key: "releaseDate" },
            { label: "犯罪案情", key: "case" },
          ],
        },
        {
          title: "家庭社会关系",
          dataList: [
            { label: "籍贯", key: "origin" },
            { label: "亲属", key: "family" },
            { label: "主要社会关系", key: "social" },
          ],
        },
        {
          title: "改造表现情况",
          dataList: [
            { label: "认罪态度", key: "attitude" },
            { label: "行为表现", key: "behavior" },
            { label: "奖惩情况", key: "reward" },
          ],
        },
      ],
      infoData: {
        // 待对接（假数据）
        userName: "XXX",
        age: "25",
        feature: "小巧玲珑，到处纹身",
        education: "高中",
        address: "四川省成都市",
        job: "无固定职业",
        type: "盗窃罪",
        originalType: "有期徒刑",
        period: "3年",
        releaseDate: "2023-01-01",
        case: "2022年12月25日，因盗窃被逮捕",
        origin: "四川省成都市",
        family: "无",
        social: "无",
        attitude: "良好",
        behavior: "良好",
        reward: "无",
        imgName: "",
      },
      // disappearList: [
      //   { name: "XXX", area: "区域一", isDangerous: true },
      //   { name: "XXX", area: "区域二", isDangerous: true },
      //   { name: "XXX", area: "区域三", isDangerous: false },
      //   { name: "XXX", area: "区域四", isDangerous: false },
      //   { name: "XXX", area: "区域五", isDangerous: false },
      // ],
      currIndex: 0,
      showEchart: [false, false],
      options: [[], []],
    };
  },
  computed: {
    ...mapState({
      taskPointNum: (state) => state.bigScreen.taskPointNum !==null?state.bigScreen.taskPointNum:0,
      needTime: (state) => state.bigScreen.needTime !==null?state.bigScreen.needTime:0,
      arrivedNum: (state) => state.bigScreen.arrivedNum !==null?state.bigScreen.arrivedNum:0,
      unarrivedNum: (state) => state.bigScreen.unarrivedNum !==null?state.bigScreen.unarrivedNum:0,
      importArrivedNum: (state) => state.bigScreen.importArrivedNum !==null?state.bigScreen.importArrivedNum:0,
      importUnArrivedNum: (state) => state.bigScreen.importUnArrivedNum !==null?state.bigScreen.importUnArrivedNum:0,
      //进度
      // processNum: (state) =>
      //   state.bigScreen.processNum !== null ? state.bigScreen.processNum : 0,
      unarrivedUsers: state => state.bigScreen.unarrivedUsers.length !== 0? state.bigScreen.unarrivedUsers : [],
    }),
    //根据上面的taskPointNum,needTime改变indexList
    indexList() {
      return [
        { name: "任务总点数", value: this.taskPointNum, unit: "次" },
        { name: "预计剩余", value: this.needTime, unit: "min" },
      ];
    },
    //统计已到未到人数
    chartData() {
      // console.log(this.arrivedNum,this.unarrivedNum)
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
    personRollCall() {
      return this.$store.getters["websocket/getPersonRollCall"];
    },
    btnList() {
      return new Array(Math.ceil(this.unarrivedUsers.length / 4));
    },
    currDataList() {
      return this.unarrivedUsers.slice(
        this.currIndex * 4,
        (this.currIndex + 1) * 4
      );
    },
  },
  watch: {
    dogId(newVal) {
      // console.log("当前机器人 ID 变化:", newVal);
      // 可以在这里调用 API 加载该机器人对应的数据
    },
    chartData: {
      deep: true,
      handler(newData) {
        // console.log(newData)
        // this.options[0].series[0].data = newData;
        // this.$set(this.options[0].series[0], "data", newData);
        this.options[0] = { ...this.options[0], series: [{ ...this.options[0].series[0], data: newData }] };
        // this.$refs.chart0.setOption(this.options[0]);
      },
    },
    chartData1: {
      deep: true,
      handler(newData) {
        // console.log(newData)
        // this.options[1].series[0].data = newData;
        // this.$set(this.options[1].series[0], "data", newData);
        this.options[1] = { ...this.options[1], series: [{ ...this.options[1].series[0], data: newData }] };
        // this.$refs.chart1.setOption(this.options[1]);
      },
    },
    personRollCall: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          const message = newMessage[newMessage.length - 1];
          if (message) {
            this.handlePersonRollCall(message);
          }
        }
      },
      immediate: true,
    },
  },
  mounted() {
    this.getDogList();
    // 模拟已获取数据后修改状态
    this.showEchart[0] = true;
    this.initOption();
    this.showEchart[1] = true;
    this.initOption1();
    this.$forceUpdate();
  },
  methods: {
    getDogList() {
      getRobotList(this.queryParams).then((res) => {
        // console.log(res);
        this.dogList = res.rows.map((row) => {
          return {
            value: row.dogId,
            label: row.dogName,
          };
        });
        this.value = this.dogList[0].value;
      });
    },
    //获取图片路径
    getImageSrc(imgName) {
      return `${process.env.VUE_APP_BASE_API.replaceAll("'", '')}/users/${imgName}.jpg`;
    },
    handlePersonRollCall(message) {
      // console.log(message);
      this.personInfo = {
        dogName: message.dogName,
        pictureData: message.pictureData,
        result: (message.result = 0 ? "未到" : "已到"),
        userName: message.userName,
        pointName: message.pointName,
      };
      this.personInfo = message;
      this.showInfo = true;
      if (this.timer) {
        clearTimeout(this.timer);
      }
      // 三秒后隐藏信息
      this.timer = setTimeout(() => {
        this.showInfo = false;
        this.personInfo = { imageUrl: "", name: "", age: "" }; // 重置数据
      }, 3000);
    },
    toggleDialog(User) {
      // console.log(User)
      let queryParams ={pageNum: 1, pageSize: 1, UserId: User.userId, UserName: undefined,}
      getExportUserList(queryParams).then(res => {
        // console.log(res.rows[0])
        let UserInfo = res.rows[0];
        this.infoData.userName = UserInfo.UserName
        this.infoData.age = UserInfo.Age
        this.infoData.feature = UserInfo.Trait
        this.infoData.education = UserInfo.EducationLevel
        this.infoData.address = UserInfo.ArrestAddress
        this.infoData.job = UserInfo.Occupation
        this.infoData.type = UserInfo.CrimeType
        this.infoData.originalType = UserInfo.CrimeQuality
        this.infoData.period = UserInfo.Sentence
        this.infoData.releaseDate = UserInfo.ReleaseDate
        this.infoData.case = UserInfo.CaseDetails
        this.infoData.origin = UserInfo.NativePlace
        this.infoData.family = UserInfo.FamilyRelation
        this.infoData.social = UserInfo.SocialRelation
        this.infoData.attitude = UserInfo.ReformAttitude
        this.infoData.behavior = UserInfo.Behavior
        this.infoData.reward = UserInfo.Rewards
        this.infoData.imgName = UserInfo.PictureData

        // console.log(res)
        this.dialogVisible = true
      },);
    },
    handleClose() {
      this.dialogVisible = false;
    },
    handleIndex(index) {
      this.currIndex = index;
    },
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
  beforeDestroy() {
    if (this.timer) {
      clearTimeout(this.timer); // 组件销毁时清除定时器
    }
  },
};
</script>
<style lang="scss" scoped>
@keyframes fade-right {
  0% {
    opacity: 0;
    transform: translateX(-10px);
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: translateX(10px);
  }
}

@keyframes fade-jump {
  0% {
    opacity: 0.8;
    transform: translateY(-5px);
  }
  50% {
    opacity: 1;
    transform: translateY(5px);
  }
  100% {
    opacity: 0.8;
    transform: translateY(-5px);
  }
}

@-webkit-keyframes blink-1 {
  0%,
  50%,
  100% {
    opacity: 1;
  }
  25%,
  75% {
    opacity: 0;
  }
}
@keyframes blink-1 {
  0%,
  50%,
  100% {
    opacity: 1;
  }
  25%,
  75% {
    opacity: 0;
  }
}
.personalControl {
  height: 100%;
  display: flex;
  justify-content: space-around;
  flex-direction: column;
  .top-box {
    .title {
      width: 150px;
      height: 24px;
      line-height: 24px;
      font-size: 14px;
      color: #c5e5ff;
      background-image: url("../../assets/images/largescreen/title-bg.png");
      background-size: 100% 100%;
      margin: 10px 0;
      cursor: pointer;
      position: relative;
      padding-left: 25px;
      box-sizing: border-box;
      &::before {
        content: "";
        width: 9px;
        height: 13px;
        position: absolute;
        background-image: url("../../assets/images/largescreen/title-be.png");
        background-size: 100% 100%;
        right: 25px;
        top: 0;
        bottom: 0;
        margin: auto 0;
        animation: fade-right 1s linear infinite;
      }
      &::after {
        content: "";
        width: 9px;
        height: 13px;
        position: absolute;
        background-image: url("../../assets/images/largescreen/title-be.png");
        background-size: 100% 100%;
        right: 15px;
        top: 0;
        bottom: 0;
        margin: auto 0;
        animation: fade-right 1s linear infinite;
      }
    }
    .index-box {
      display: flex;
      justify-content: space-around;
      .index-item {
        flex: 1;
        height: 92px;
        display: flex;
        flex-direction: column;
        position: relative;
        padding: 20px 0 20px 79px;
        &::before {
          content: "";
          width: 28px;
          height: 28px;
          position: absolute;
          left: 19px;
          top: 15px;
          animation: fade-jump 3s linear infinite;
        }
        &::after {
          content: "";
          width: 69px;
          height: 52px;
          position: absolute;
          background-image: url("../../assets/images/largescreen/index-af.png");
          background-size: 100% 100%;
          left: 0;
          top: 0;
          bottom: 0;
          margin: auto 0;
        }
        &:nth-child(1) {
          &::before {
            background-image: url("../../assets/images/largescreen/index-be0.png");
            background-size: 100% 100%;
          }
        }
        &:nth-child(2) {
          &::before {
            background-image: url("../../assets/images/largescreen/index-be1.png");
            background-size: 100% 100%;
          }
        }
        .index-name {
          font-size: 14px;
          color: #c5e5ff;
        }
        .index-value {
          display: flex;
          align-items: baseline;
          .value {
            font-size: 24px;
            color: #ffffff;
            font-family: "YouSheBiaoTiHei";
          }
          .unit {
            font-size: 14px;
            color: #c5e5ff;
            margin-left: 5px;
          }
        }
      }
    }
    .index-box1 {
      display: flex;
      justify-content: space-around;
      .index-item {
        width: 200px;
        height: 174px;
        display: flex;
        align-items: center;
        flex-direction: column;
        position: relative;

        background-image: url("../../assets/images/largescreen/index-bg.png");
        background-size: 100% 100%;
        .chart-box {
          width: 200px;
          height: 174px;
        }
        .index-name {
          position: absolute;
          left: 0;
          right: 0;
          top: 0;
          bottom: 0;
          margin: auto;
          width: 40px;
          height: 40px;
          font-size: 14px;
          color: #c5e5ff;
          text-align: center;
        }
        // .index-value {
        //   width: 98px;
        //   height: 98px;
        //   line-height: 98px;
        //   font-size: 30px;
        //   color: #ffffff;
        //   text-align: center;
        // }
      }
    }
  }
  .btm-box {
    display: flex;
    justify-content: space-around;
    flex-direction: column;

    .title {
      font-size: 14px;
      color: #c5e5ff;
      position: relative;
      padding-left: 15px;
      line-height: 40px;
      &::before {
        content: "";
        width: 6px;
        height: 6px;
        background: #7bfff4;
        position: absolute;
        left: 0;
        top: 0;
        bottom: 0;
        margin: auto 0;
        -webkit-animation: blink-1 1s both infinite;
	        animation: blink-1 1s both infinite;
      }
    }
    .list-box {
      display: flex;
      .list-item {
        width: 100px;
        height: 152px;
        background: rgba(104, 156, 216, 0.3);
        border: 1px solid #248baf;
        display: flex;
        flex-direction: column;
        justify-content: space-around;
        align-items: center;
        cursor: pointer;
        position: relative;
        &:not(:last-child) {
          margin-right: 9px;
        }
        &.is-dangerous {
          background: rgba(255, 62, 62, 0.4);
          box-shadow: inset 0px 0px 8px 0px rgba(255, 255, 255, 0.3);
          border: 1px solid;
          border-image: linear-gradient(
              180deg,
              rgba(255, 227, 221, 0),
              rgba(255, 169, 154, 1)
            )
            1 1;
          &::before {
            content: "";
            width: 65px;
            height: 68px;
            position: absolute;
            background-image: url("../../assets/images/largescreen/index-be3.png");
            background-size: 100% 100%;
            right: 7px;
            top: 0;
          }
        }
        .img {
          width: 68px;
          height: 91px;
        }
        .item-title {
          font-weight: bold;
          font-size: 14px;
          color: #d8edff;
        }
        .item-area {
          font-size: 12px;
          color: #c5e5ff;
        }
      }
    }
    .btn-box {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 15px;
      margin: 10px 0;
      .btn-item {
        width: 20px;
        height: 6px;
        background: #a3deff;
        border-radius: 2px;
        border-radius: 2px;
        margin: 0 10px;
        cursor: pointer;
        &:hover {
          background: #1677ff;
        }
        &.active {
          width: 10px;
          background: #1677ff;
        }
      }
    }
  }
}
.dialog-wrapper {
  ::v-deep .el-dialog {
    background-color: transparent;
    .el-dialog__header {
      height: 62px;
      position: relative;
      text-align: center;
      .el-dialog__title {
        color: #fff;
        font-size: 32px;
        font-weight: bold;
        position: absolute;
        left: 0;
        right: 0;
        top: 40px;
        margin: 0 auto;
      }
      .el-dialog__headerbtn {
        width: 40px;
        height: 40px;
        background-image: url("../../assets/images/largescreen/dialog-close-btn.png");
        background-size: 100% 100%;
        top: 0;
        bottom: 0;
        margin: auto 0;
      }
    }
    .el-dialog__body {
      height: 850px;
      background-image: url("../../assets/images/largescreen/dialog-bg.png");
      background-size: 100% 100%;
    }
  }
  .dialog-content {
    height: 100%;
    color: #ffffff;
    padding: 30px 10px 0 10px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    align-items: center;
    flex-wrap: wrap;
    position: relative;

    .info-box {
      width: 581px;
      margin-bottom: 10px;

      .info-title {
        font-weight: bold;
        font-size: 16px;
        position: relative;
        padding-left: 10px;
        line-height: 50px;
        &::before {
          content: "";
          position: absolute;
          left: 0;
          top: 0;
          bottom: 0;
          margin: auto 0;
          width: 4px;
          height: 12px;
          background: #1fc6ff;
          border-radius: 3px;
        }
      }
      .info-details {
        .info-row {
          height: 40px;
          line-height: 40px;
          padding: 0 15px;
          box-sizing: border-box;

          &:nth-child(odd) {
            background: #233f5c;
          }
          &:nth-child(even) {
            background: #12334b;
          }
          .label {
            width: 110px;
            display: inline-block;
          }
        }
      }
    }
    .info-image {
      width: 581px;
      height: 548px;
      background-image: url("../../assets/images/largescreen/dialog-img-bg.png");
      background-size: 100% 100%;
      position: absolute;
      right: 20px;
      top: 50px;
    }
  }
}
</style>
