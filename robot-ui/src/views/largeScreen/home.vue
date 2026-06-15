<template>
  <ScaleScreen :width="1920" :height="1080" class="scale-wrap">
    <div class="bg" style="position: relative;">
      <!-- <GisMap /> -->
      <div class="header w100">
        <div class="flx-justify-between">
          <div class="backstage-entry" @click="enterBackstage">后台管理</div>
          <div class="tabs left d-flex">
            <div class="item" v-if="routeDetails.key" @click="back()" style="position: absolute; left: 12px;">返回</div>
            <div class="item" :class="{ 'active': $route.name === 'shouye' }" @click="goToPage('shouye')">首页</div>
            <div class="item" :class="{ 'active': $route.name === 'xlxc' }" @click="goToPage('xlxc')">巡逻巡查</div>
          </div>
          <div class="center-title">警用具身智能平台</div>
          <div class="tabs right d-flex">
            <div class="item" :class="{ 'active': $route.name === 'cljg' }" @click="goToPage('cljg')">车辆监管</div>
            <div class="item" :class="{ 'active': $route.name === 'rygk' }" @click="goToPage('rygk')">人员管控</div>
          </div>
        </div>
        <!-- <div class="item" :class="{ 'active': $route.name === 'default' }" @click="goToPage('default')">上一版</div> -->
        <!-- <div class="title1 wp350">警用AI机器人指挥调度平台</div>
        <div class="operation flx-align-center">
          <div class="backstage mr40">
            <button class="backstage-but" @click="enterBackstage">后台系统</button>
          </div>
          <div class="avator"></div>
          <div class="name ml8">{{ $store.getters.nickName }}</div>
        </div> -->
      </div>
      <!-- <div class="right_top">
        <div class="choose_box">
          <span class="choose-title">机器人：</span>
          <ItemSelect v-model="selectedRobot" :dataList="dogList" @change="handleRobotChange"/>
        </div>
        <div class="backstage">
          <button class="backstage-but" @click="enterBackstage">后台系统</button>
        </div>
      </div> -->
      <!-- <Index :dogId="selectedRobot" :dogName="selectedRobotName" class="pdt20 pdr30 pdb20 pdl30"/> -->
      <router-view></router-view>
    </div>
  </ScaleScreen>
</template>

<script>
import Index from "./index";
import shouye from "./1_shouye";
import xlxc from "./2_xlxc";
import rygk from "./3_rygk";
import cljg from "./4_cljg";
import GisMap from "./gis/index.vue";

import ScaleScreen from "../../components/bigScreen/scale-screen";
import Cookies from 'js-cookie'

export default {
  name: "Home",
  components: { ScaleScreen, Index, shouye, xlxc, rygk, cljg, GisMap },
  data() {
    return {
      redirect: undefined,
    };
  },
  computed: {
    // 语音系统是否连接
    routeDetails() {
      return this.$store.getters['extra/getRouteDetails'];
    },
  },
  watch: {
    $route: {
      handler: function (route) {
        this.redirect = route.query && route.query.redirect;
      },
      immediate: true,
    },
  },
  created() {
    console.log('//////////////////////////////');
    
    // this.$store.dispatch('websocket/initWebSocket');
    // this.$store.dispatch('voiceCall/initWebsocket');
  },
  beforeDestroy() {
    Cookies.remove('targetId');
    Cookies.remove('targetName');
  },
  methods: {
    back() {
      this.$router.push({ name: this.routeDetails.key })
      this.$store.dispatch('extra/setRouteDetails', {})
    },
    goToPage(name) {
      this.$router.push({ name })
    },
    enterBackstage() {
      this.$router.push({ path: this.redirect || "/home" }).catch(()=>{});
    },
  },
};
</script>

<style lang="scss">
$item-title-height: 60px;
$item_title_content-height: calc(100% - 60px);

.scale-wrap {
  color: #d3d6dd;
  width: 1920px;
  height: 1080px;
  overflow: hidden;

  .header {
    position: absolute;
    top: 0;
    left: 0;
    height: 93px;
    background-image: url("../../assets/images/bi/new/header.png");
    background-size: 100% 100%;
    & > div {
      align-items: end;
      height: 78px;
      padding: 0 245.5px;
      position: relative;
    }
    .backstage-entry {
      position: absolute;
      left: 6px;
      top: 4px;
      width: 56px;
      height: 20px;
      line-height: 20px;
      text-align: center;
      cursor: pointer;
      border-radius: 2px;
      border: 1px solid rgba(66, 229, 255, 0.22);
      background: rgba(8, 44, 88, 0.25);
      color: rgba(216, 237, 255, 0.45);
      font-size: 10px;
      letter-spacing: 0;
      user-select: none;
      transition: all 0.2s ease;
      z-index: 2;
      &:hover {
        border-color: rgba(66, 229, 255, 0.5);
        background: rgba(8, 44, 88, 0.5);
        color: rgba(216, 237, 255, 0.9);
      }
    }
    .center-title {
      padding-bottom: 11px;
      text-align: center;
      text-shadow: 0 2px 10px #205DC5, 0 4px 6px #1973D6, 0 1px 2px #165EAE;
      font-family: YouSheBiaoTiHei;
      font-size: 38px;
      line-height: 51.247px; /* 134.86% */
      letter-spacing: 5.336px;
      // background: linear-gradient(199deg, #FFF 24.5%, #91CDF7 73.27%);
      // background-clip: text;
      // -webkit-background-clip: text;
      // -webkit-text-fill-color: transparent;
    }
    .tabs {
      align-items: flex-start;
      .item {
        width: 138px;
        height: 27px;
        background-size: 100% 100%;
        color: rgba(255, 255, 255, 0.65);
        text-align: center;
        font-family: "Alibaba PuHuiTi";
        font-size: 16px;
        line-height: 21.952px; /* 137.2% */
        cursor: pointer;
        .active {
          color: #fff;
        }
        & + .item {
          margin-left: 20px;
        }
      }
      &.left {
        .item {
          background-image: url("../../assets/images/bi/new/header-title-left.png");
          &.active {
            background-image: url("../../assets/images/bi/new/header-title-left-active.png");
          }
        }
      }
      &.right {
        .item {
          background-image: url("../../assets/images/bi/new/header-title-right.png");
          &.active {
            background-image: url("../../assets/images/bi/new/header-title-right-active.png");
          }
        }
      }
      .left, .right {
        // height: 78px;
        // display: flex;
        // align-items: center;
      }
    }
    .title1 {
      padding-top: 15px;
      padding-left: 30px;
      font-family: 'Arial Negreta', 'Arial Normal', 'Arial';
      font-weight: 700;
      font-size: 20px;
      letter-spacing: 5px;
      line-height: 23px;
      color: #ffffff;
    }
    // .tabs {
    //   .item {
    //     width: 150px;
    //     line-height: 64px;
    //     color: rgba(255, 255, 255, 0.65);
    //     text-align: center;
    //     list-style: none;
    //     cursor: pointer;
    //     &.active {
    //       color: #fff;
    //       border-bottom: 5px solid #fff;
    //     }
    //   }
    // }
    .operation {
      padding-right: 50px;
      .avator {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.46);
      }
      .name {
        font-family: "Microsoft YaHei Regular", "Microsoft YaHei";
        font-size: 14px;
        color: #ffffff;
      }
    }
  }

  .bg {
    width: 100%;
    height: 100%;
    // padding: 80px 20px 50px 20px;
    box-sizing: border-box;
    // background-image: url("../../assets/images/bi/bg.png");
    background: #001529;
    background-size: cover;
    background-position: center center;
    position: relative;
    // &::before {
    //   content: "";
    //   width: 100%;
    //   height: 100%;
    //   position: absolute;
    //   // background-image: url("../../assets/images/bi/bg.svg");
    //   // background-size: 100% 100%;
    //   top: 0;
    //   left: 0;
    //   right: 0;
    //   margin: 0 auto;
    // }
    .right_top {
      width: 20%;
      position: absolute;
      top: 30px;
      right: 20px;
      display: flex;
      align-items: center;
      //gap: 10px;
      .choose_box {
        display: flex;
        width: 50%;
        height: 100%;
        .choose-title {
          width: 60%;
          text-align: center;
          font-size: 18px;
          font-weight: bold;
          margin-top: 6px;
        }
      }
      .backstage {
        position: absolute;
        right: 3%;
        width: 40%;
        display: flex;
        align-items: center;

        .backstage-but {
          font-size: 20px;
          font-weight: bold;
          background-color: rgba(255, 255, 255, 0);
          color: rgba(255, 255, 255, 0.87);
          border: none; // 去掉边框
        }

        .backstage-but:hover {
          background-color: rgba(255, 255, 255, 0.07);
          color: #00eaff;
        }
      }
    }
  }
}
</style>
