<template>
  <div class="middle_left">
    <div class="dog_choose">
      <div class="choose_box">
        <span class="choose-title">机器人：</span>
        <ItemSelect v-model="value" :dataList="dogList"/>
      </div>
    </div>
    <div class="call_progress">
      <div class="progress-title">
        <span class="decorate">|</span>
        <span class="title_font"> 巡航进度</span>
      </div>
      <div class="num-info">
        <!--        任务点数以及剩余时间-->
        <div class="number">
          <div class="num-item">
            <div class="num-font"><span class="num-point">■</span>任务总点数：</div>
            <div class="num-font"><span class="num">{{taskPointNum}}</span>次</div>
          </div>
          <div class="num-item">
            <div class="num-font"><span class="num-point">■</span>预计剩余：</div>
            <div class="num-font"><span class="num">{{needTime}}</span>min</div>
          </div>
        </div>
        <!--        进度条-->
        <div class="progress">
          <CommonProcess :num="processNum || 0"/>
        </div>
        <!--        那两个框-->
        <div class="num-icon">
          <div class="icon-item">
            <div class="cricle">{{severity}}</div>
            <div class="icon-font">紧急异常</div>
          </div>
          <div class="icon-item">
            <div class="cricle">{{normal}}</div>
            <div class="icon-font">普通异常</div>
          </div>
        </div>
      </div>
    </div>
    <div class="notnamed_list">
      <div class="list_title">
        <span class="decorate">|</span>
        <span class="title_font"> 异常监控</span>
      </div>
      <div class="list_table">
        <div v-if="showInfo" class="info-card">
          <img :src="errorInfo.imageUrl" alt="异常信息图片" class="info-image" />
          <div class="info-details">
            <p><strong>异常类型：</strong> {{ errorInfo.errorType }}</p>
            <p><strong>地图名称：</strong> {{ errorInfo.mapName }}</p>
            <p><strong>点位名称：</strong> {{ errorInfo.pointName }}</p>
            <!-- 可根据 rollCall 数据添加更多字段 -->
          </div>
        </div>
        <el-empty v-else description="当前没有点名信息"></el-empty>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import CommonProcess from "../../components/bigScreen/CommonProcess";
import { getRobotList} from '@/api/robot/robotMessage.js'

export default {
  name: 'MiddleLeft',
  components: {
    CommonProcess
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
      animationDuration: 500, // 动画持续时间
      isAnimating: false,
      showInfo: false,
      errorInfo:{
        imageUrl:`${process.env.VUE_APP_BASE_API.replaceAll("'", '')}/images/users/yue.jpg`,
        mapName:'hhh',
        pointName: '11',
        dogName: '11',
        type: '22'
      },
      timer: null
    };
  },
  computed: {
    ...mapState({
      taskPointNum: state => state.bigScreen.cruiseTaskPointNum,
      needTime: state => state.bigScreen.cruiseNeedTime,
      processNum: state => state.bigScreen.cruiseProssesNum !== null?state.bigScreen.cruiseProssesNum : 0 ,
      severity: state => state.bigScreen.severity,
      normal: state => state.bigScreen.normal,
    }),
    firePersonError() {
      return this.$store.getters['websocket/getFirePersonError'];
    }
  },
  watch: {
    firePersonError: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          console.log('jiantingdl')
          const message = newMessage[newMessage.length -1];
          if (message) {
            this.handleErrorMsg(message)
          }
        }
      },
      immediate: true,
    }
  },
  mounted() {
    this.getDogList()
  },
  methods: {
    getDogList() {
      getRobotList(this.queryParams).then(res => {
        // console.log(res)
        this.dogList = res.rows.map(row => {
          return {
            value: row.dogId,
            label: row.dogName,
          }
        })
        // console.log(this.dogList)
        this.value=this.dogList[0].value
      })
    },
    handleErrorMsg(message) {
      this.errorInfo = {
          errorType:message.errorType==1 ? '紧急': '普通',
          mapName: message.mapName,
          pointName: message.pointName,
          imageUrl:"http://"+ message.imageUrl
        }
      this.showInfo = true
      if (this.timer) {
        clearTimeout(this.timer);
      }
      // 三秒后隐藏信息
      this.timer = setTimeout(() => {
        this.showInfo = false;
        this.personInfo = { imageUrl: '', name: '', age: '' }; // 重置数据
      }, 300000);
    },
    //获取图片路径
    // getImageSrc(imgName) {
    //   return `${process.env.VUE_APP_BASE_API}/images/users/${imgName}.jpg`;
    // },
  },
  beforeDestroy() {
    if (this.timer) {
      clearTimeout(this.timer); // 组件销毁时清除定时器
    }
  },
};
</script>
<style lang="scss" scoped>
/* 定义闪烁动画 */
@keyframes blink {
  0% {
    opacity: 1; /* 完全可见 */
  }
  50% {
    opacity: 0; /* 完全透明 */
  }
  100% {
    opacity: 1; /* 恢复可见 */
  }
}
@keyframes breathe {
  0% {
    transform: scale(1); /* 原始大小 */
  }
  50% {
    transform: scale(1.1); /* 放大 10% */
  }
  100% {
    transform: scale(1); /* 恢复原始大小 */
  }
}
/* 发光动画 */
@keyframes glow {
  0% {
    box-shadow: 0 0 0px #7BFFF4, 0 0 20px #2387e8; /* 初始状态 */
  }
  50% {
    box-shadow: 0 0 10px #7BFFF4, 0 0 40px #67A4E1; /* 增强发光 */
  }
  100% {
    box-shadow: 0 0 0px #7BFFF4, 0 0 20px #67A4E1; /* 恢复 */
  }
}
.middle_left {
  height: 100%;
  display: flex;
  justify-content: space-between;
  flex-direction: column;
  padding: 5%;

  .dog_choose {
    height: 4%;
    display: flex;
    justify-content: center;

    .choose_box {
      display: flex;
      //width: 50%;
      //没有批次时候长一点
      width: 80%;
      height: 100%;
      .choose-title {
        width: 50%;
        text-align: center;
      }
    }
  }
  .call_progress {
    height: 37%;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    .progress-title {
      height: 32px;
      background: linear-gradient( 270deg, rgba(32,123,175,0.54) 0%, rgba(24,98,149,0.5) 24%, #0E4577 100%);
      .decorate {
        color: rgb(123,255,244);
        font-size: 22px;
        text-align: left;
      }
      .title_font{
        font-family: PingFangSC, PingFang SC;
        font-weight: 800;
        font-size: 16px;
        line-height: 22px;
        text-align: left;
      }
    }
    .num-info {
      height: 90%;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      .number {
        height: 20%;
        display: flex;
        .num-item {
          width: 50%;
          .num-font{
            font-family: PingFangSC, PingFang SC;
            font-weight: 400;
            font-size: 14px;
            color: #C5E5FF;
            line-height: 20px;
            text-align: left;
            font-style: normal;
            margin: 2%;

            .num-point {
              color:#7BFFF4;
              padding-right: 4px;
              animation: blink 1s infinite; /* 添加闪烁动画 */
            }
            .num {
              font-family: FFGothic, FFGothic;
              font-weight: normal;
              font-size: 26px;
              line-height: 24px;
              font-style: normal;
              margin: 10px;
            }
          }
        }
      }
      .progress {
        height: 10%;
      }
      .num-icon {
        height: 65%;
        display: flex;
        justify-content: space-around;
        margin-top: 5px;
        .icon-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          width: 44%;
          height: 90%;
          background-color: rgba(53, 78, 102, 0.6);

          .cricle {
            height: 70%;
            width: 65%;
            left: 50%;
            background-repeat: no-repeat;
            background-position: center;
            background-image: url("../../assets/bigScreen/icon/cricle.png");
            font-weight: normal;
            font-size: 24px;
            display: flex;
            justify-content: center; /* 水平居中 */
            align-items: center;     /* 垂直居中 */
            animation: breathe 2s ease-in-out infinite;
          }
          .icon-font {
            height: 20%;
            display: flex;
            justify-content: center; /* 水平居中 */
            align-items: center;     /* 垂直居中 */
            animation: breathe 2s ease-in-out infinite;
          }
        }
      }
    }
  }
  .notnamed_list {
    height: 55%;
    display: flex;
    justify-content: space-around;
    flex-direction: column;

    .list_title {
      height: 32px;
      background: linear-gradient( 270deg, rgba(32,123,175,0.54) 0%, rgba(24,98,149,0.5) 24%, #0E4577 100%);
      .decorate {
        color: rgb(123,255,244);
        font-size: 22px;
        text-align: left;
      }
      .title_font{
        font-family: PingFangSC, PingFang SC;
        font-weight: 800;
        font-size: 16px;
        line-height: 22px;
        text-align: left;
      }
    }
    .list_table {
      height: 100%;
      width: 100%;
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      .info-card {
        height: 100%;
        width: 100%;
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
        align-items: center;
        background: rgba(255, 255, 255, 0.2);
        //border: 1px solid #dcdcdc;
        border-radius: 8px;
        padding: 20px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }

      .info-image {
        width: 100%;
        height: 55%;
        //border-radius: 20%;
        margin-bottom: 10px;
      }
      .info-details {
        height: 30%;
        text-align: left;
      }
      .info-details p {
        margin: 10px 0;
        font-size: 20px;
      }
    }
  }
}
</style>
