<template>
  <ScaleScreen :width="1920" :height="1080" class="scale-wrap">
    <div class="bg">
      <dv-loading v-if="loading">Loading...</dv-loading>
      <div v-else class="host-body">
        <div class="d-flex jc-center title_wrap">
          <div class="d-flex jc-center">
            <div class="title">
              <span class="title-text">警用具身智能平台</span>
              <img :src="require('../../assets/bigScreen/icon/change.png')" @click="changeClick"/>
            </div>
          </div>
          <div class="timers">
            {{ dateYear }}  {{ dateWeek }}  {{ dateDay }}
            <i class="blq-icon-shezhi02" style="margin-left: 10px" @click="showSetting"></i>
          </div>
          <div class="backstage">
            <button class="backstage-but" @click="enterBackstage">后台系统</button>
          </div>
        </div>
        <div class="content_index">
          <Index :change="change"/>
        </div>
      </div>
    </div>
  </ScaleScreen>
</template>

<script>
import Index from "./index"
import { formatTime } from "../../utils/bigScreen/index";
import ScaleScreen from "../../components/bigScreen/scale-screen";
import { getBasicMessage } from "@/api/menu"

export default {
  name: 'Home',
  components: { ScaleScreen, Index },
  data() {
    return {
      timing: null,
      loading: true,
      dateDay: null,
      dateYear: null,
      dateWeek: null,
      weekday: ["周日", "周一", "周二", "周三", "周四", "周五", "周六"],
      redirect: undefined,
      change: true,
    };
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect;
      },
      immediate: true
    }
  },
  mounted() {
    this.timeFn();
    this.cancelLoading();
    // this.getRobotMessage()
  },
  beforeDestroy() {
    clearInterval(this.timing);
  },
  methods: {
    // getRobotMessage() {
    //   // 记得放开
    //   getBasicMessage()
    // },
    enterBackstage() {
      this.$router.push({ path: this.redirect || "/robot/map" }).catch(()=>{});
    },
    showSetting() {
      this.$refs.setting.init();
    },
    changeClick() {
      // 切换 change 状态
      this.change = !this.change; // true/false 切换
      // 或者使用 1/0 切换：this.change = this.change === 1 ? 0 : 1;
      console.log('Change value updated to:', this.change);
    },
    timeFn() {
      this.timing = setInterval(() => {
        this.dateDay = formatTime(new Date(), "HH: mm: ss");
        this.dateYear = formatTime(new Date(), "yyyy-MM-dd");
        this.dateWeek = this.weekday[new Date().getDay()];
      }, 1000);
    },
    cancelLoading() {
      let timer = setTimeout(() => {
        this.loading = false;
        clearTimeout(timer);
      }, 500);
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

  .bg {
    width: 100%;
    height: 100%;
    padding: 16px 16px 10px 16px;
    box-sizing: border-box;
    background-image: url("../../assets/bigScreen/backgroundpicture.png");
    background-size: cover;
    background-position: center center;
  }

  .host-body {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;

    .title_wrap {
      height: $item-title-height;
      background-size: cover;
      background-position: center center;
      position: relative;
      margin-bottom: 4px;

      .backstage {
        position: absolute;
        right: 3%;
        top: 25px;
        display: flex;
        align-items: center;

        .backstage-but {
          font-size: 20px;
          font-weight: bold;
          background-color: rgba(255, 255, 255, 0.00);
          color: rgba(255, 255, 255, 0.87);
          border: none; // 去掉边框
        }

        .backstage-but:hover {
          background-color: rgba(255, 255, 255, 0.07);
          color: #00eaff;
        }
      }

      .timers {
        position: absolute;
        right: 20%;
        top: 26px;
        font-size: 18px;
        display: flex;
        align-items: center;

        .blq-icon-shezhi02 {
          cursor: pointer;
        }
      }
    }

    .content_index {
      height: $item_title_content-height;
    }

    .title {
      position: relative;
      background-size: cover;
      color: transparent;
      height: 60px;
      line-height: 75px;

      .title-text {
        padding-left: 20px;
        margin-top: 10px;
        font-size: 28px;
        font-weight: bold;
        //letter-spacing: 4px;
        width: 100%;
        background: white;
        font-family: '黑体', SimHei, sans-serif;
        font-style: oblique;
        //background: linear-gradient(92deg, #0072FF 0%, #00EAFF 48.8525390625%, #01AAFF 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
      }
    }
  }
}

.scale-wrap {
  .pagetab {
    position: absolute;
    top: -35px;
    display: flex;

    .item {
      width: 130px;
      height: 36px;
      border-radius: 18px 0px 0px 18px;
      color: #00FBF8;
      text-indent: 26px;
      line-height: 36px;
      font-size: 16px;
      margin-right: 20px;
      background: linear-gradient(to right, rgba(76, 245, 255, .5), rgba(76, 245, 255, 0));
    }
  }
}

.setting {
  position: fixed;
  width: 100%;
  height: 100%;
  z-index: 999;
  top: 0;
  left: 0;

  .left_shu {
    color: #000;
    font-weight: 900;
    position: relative;
    text-indent: 10px;
    padding: 16px 0 10px 0;

    &::before {
      display: block;
      content: " ";
      height: 16px;
      width: 4px;
      border-radius: 2px;
      background: #0072FF;
      position: absolute;
      left: 0px;
    }
  }

  .setting_dislog {
    background-color: rgba($color: #000000, $alpha: .5);
    position: absolute;
    width: 100%;
    height: 100%;
    z-index: 0;
    right: 0;
    top: 0;
  }

  .setting_inner {
    box-sizing: border-box;
    background: #FFF;
    width: 340px;
    height: 100%;
    position: absolute;
    right: 0px;
    top: 0;
    z-index: 1;
    color: #000000;
    box-shadow: 0 8px 10px -5px rgba(0, 0, 0, .2), 0 16px 24px 2px rgba(0, 0, 0, .14), 0 6px 30px 5px rgba(0, 0, 0, .12);

    .setting_header {
      font-size: 20px;
      color: rgb(0, 0, 0);
      font-weight: 900;
      text-align: center;
      line-height: 40px;
    }

    .setting_body {
      padding: 0px 16px;
      box-sizing: border-box;
      position: relative;
    }

    .setting_item {
      font-size: 14px;
      line-height: 1.5;

      .setting_label {
        color: #555454;
      }

      .setting_label_tip {
        font-size: 12px;
        color: #838282;
      }
    }
  }

  .setting_inner {
    animation: rtl-drawer-out .3s;
  }
}

.settingShow {
  .setting_inner {
    animation: rtl-drawer-in .3s 1ms;
  }
}

.yh-setting-fade-enter-active {
  animation: yh-setting-fade-in .3s;
}

.yh-setting-fade-leave-active {
  animation: yh-setting-fade-out .3s;
}

@keyframes yh-setting-fade-in {
  0% {
    opacity: 0;
  }

  100% {
    opacity: 1;
  }
}

@keyframes yh-setting-fade-out {
  0% {
    opacity: 1;
  }

  100% {
    opacity: 0;
  }
}

@keyframes rtl-drawer-in {
  0% {
    transform: translate(100%, 0);
  }

  100% {
    -webkit-transform: translate(0, 0);
    transform: translate(0, 0);
  }
}

@keyframes rtl-drawer-out {
  0% {
    transform: translate(0, 0);
  }

  100% {
    transform: translate(100%, 0);
  }
}
</style>
