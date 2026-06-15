<template>
  <div class="middle_left">
    <div class="call_progress">
      <div class="nonamed">
        <div class="nonamed-title">
          <div class="num-font"><span class="num-point">■</span>未到人员：</div>
        </div>
        <div class="nonamed-list-wrapper">
          <div v-if="nonamedListLength > 5" class="nonamed-list"
               :style="{ transform: `translateX(${-currentIndex * 40 / 6}%)`, transitionDuration: isAnimating ? `${animationDuration}ms` : '0ms' }"
               @transitionend="handleTransitionEnd">
            <div v-for="(item, index) in nonamedList.concat(nonamedList).concat(nonamedList)"
                 :key="index"
                 class="nonamed-item">
              <div class="nonamed-decorate">
                <div class="nonamed-img">
                  <div class="box-dec">
                    <img :src="getImageSrc(item.pictureData)" alt="User Image" class="userImg">
                  </div>
                </div>
                <div class="nonamed-name">{{ item.userName }}</div>
                <div class="nonamed-point">{{ item.pointName }}</div>
              </div>
            </div>
          </div>
          <div v-else class="nonamed-list-little">
            <div v-for="(item, index) in nonamedList" :key="index" class="nonamed-item-little">
              <div class="nonamed-decorate-little">
                <div class="nonamed-img-little">
                  <div class="box-dec">
                    <img :src="getImageSrc(item.pictureData)" alt="User Image" class="userImg-little">
                  </div>
                </div>
                <div class="nonamed-name-little">{{ item.userName }}</div>
                <div class="nonamed-point-little">{{ item.pointName }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="notnamed_list">
      <div class="nonamed-title">
        <div class="num-font"><span class="num-point">■</span>点名列表：</div>
      </div>
      <div class="list_table">
        <dv-scroll-board :config="resultList" ref="scrollBoard" />
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
    };
  },
  computed: {
    ...mapState({
      nonamedList: state => state.bigScreen.unarrivedUsers.length !== 0? state.bigScreen.unarrivedUsers : [],
      resultListData: state => state.bigScreen.signResultDatas,
    }),
    // 新增计算属性，获取 nonamedList 的长度
    nonamedListLength() {
      return this.nonamedList.length;
      // return 6
    },
    resultList() {
      return {
        header: ['时间', '点位', '机器人', '人员', '状态'],
        data: this.resultListData.length!== 0?this.resultListData : [],
        index: true,
        columnWidth: [50],
        align: ['center'],
        headerBGC: '#093661',
        oddRowBGC:'#063462',
        evenRowBGC:'#062B50',
      };
    }
  },
  watch: {
    rollCallMessage: {
      handler(newData) {
        // 当 resultListData 发生变化时，手动调用 updateRows 更新 scroll board 数据
        if (this.$refs.scrollBoard) {
          const formattedData = this.getResultList(newData);
          this.$refs.scrollBoard.updateRows(formattedData);
        }
      },
      immediate: true, // 页面加载时就执行一次
      deep: true // 监听深层次对象的变化
    }
  },
  mounted() {
    this.startCarousel()
    // this.getDogList()
  },
  methods: {
    getDogList() {
      getRobotList(this.queryParams).then(res => {
        console.log(res)
        this.dogList = res.rows.map(row => {
          return {
            value: row.dogId,
            label: row.dogName,
          }
        })
        this.value=this.dogList[0].value
      })
    },
    getResultList(data) {
      return data.map(item => {
        let date = new Date(item.resultDate);
        let formattedDate = `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()} ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`;
        return [
          formattedDate,
          item.pointName,
          item.dogName,
          item.userName,
          item.result=0?'未到':'已到'
        ];
      });
    },
    //获取图片路径
    getImageSrc(imgName) {
      return `${process.env.VUE_APP_BASE_API.replaceAll("'", '')}/images/users/${imgName}.jpg`;
    },
    realTimeMessage(message) {
      this.$store.dispatch('bigScreen/updateVariables', message);
      this.$nextTick(() => {
        console.log('Data updated:', this.resultList.data);
      });
    },

    startCarousel() {
      setInterval(() => {
        this.isAnimating = true;
        this.currentIndex++;
        if (this.currentIndex >= this.nonamedList.length) {
          setTimeout(() => {
            this.isAnimating = false;
            this.currentIndex = 0;
          }, this.animationDuration);
        }
      }, 3000);
    },
    handleTransitionEnd() {
      if (this.currentIndex >= this.nonamedList.length) {
        this.isAnimating = false;
        this.currentIndex = 0;
      }
    }
  }
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
  .call_progress {
    height: 35%;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    .nonamed {
      height: 100%;
      display: flex;
      flex-direction: column;
      .nonamed-title {
        height: 20%;
        display: flex;
        align-items: center;
        .num-font {
          font-weight: 400;
          font-size: 14px;
          color: #C5E5FF;
          text-align: left;
          font-style: normal;
          margin: 2%;

          .num-point {
            color: #7BFFF4;
            padding-right: 4px;
            animation: blink 1s infinite; /* 添加闪烁动画 */
          }
        }
      }

      .nonamed-list-wrapper {
        height: 80%;
        width: 100%;
        overflow: hidden;

        .nonamed-list {
          width: 300%; /* 保持轮播宽度，包含三倍数据 */
          display: flex;
          transition: transform 0.5s ease-in-out;

          .nonamed-item {
            flex: 0 0 6.6666666%; /* 每个项占 1/15，适配三倍数据 */
            transition: transform 0.5s ease-in-out;
            text-align: center;

            .nonamed-decorate {
              height: 90%;
              width: 90%;
              display: flex;
              flex-direction: column;
              justify-content: space-around;
              background: radial-gradient(273% 137% at 47% 100%, #67A4E1 0%, rgba(103, 164, 225, 0) 100%);

              .nonamed-img {
                height: 60%;
                text-align: center;
                display: flex;              /* Flex 布局 */
                justify-content: center;    /* 水平居中 */
                align-items: center;        /* 垂直居中 */

                .box-dec {
                  height: 80%;              /* 留出边框空间 */
                  width: 80%;               /* 留出边框空间 */
                  position: relative;       /* 相对定位，用于动画 */
                  border-radius: 10px;      /* 圆角边框 */
                  overflow: hidden;         /* 防止内容溢出 */

                  /* 发光边框效果 */
                  box-shadow: 0 0 10px #7BFFF4, /* 外层发光 */
                  0 0 20px #67A4E1; /* 内层发光 */
                  animation: glow 2s ease-in-out infinite; /* 动态发光动画 */

                  .userImg {
                    height: 100%;
                    width: 100%;
                    object-fit: cover;      /* 确保图片按比例填充 */
                    border-radius: 2px;    /* 与容器圆角一致 */
                  }
                }
              }

              .nonamed-name {
                height: 20%;
                text-align: center;
                font-weight: 500;
                font-size: 14px;
                color: #000000;
                line-height: 20px;
                font-style: normal;
              }

              .nonamed-point {
                height: 20%;
                text-align: center;
                font-weight: 400;
                font-size: 12px;
                color: #000000;
                line-height: 17px;
                font-style: normal;
              }
            }
          }
        }
        .nonamed-list-little {
          height: 100%;
          display: flex;
          flex-direction: row;
          justify-content: left;

          .nonamed-item-little {
            height: 100%;
            width: 20%;

            .nonamed-decorate-little {
              height: 100%;
              width: 90%;
              display: flex;
              flex-direction: column;
              justify-content: space-evenly;
              background: radial-gradient(273% 137% at 47% 100%, #67A4E1 0%, rgba(103, 164, 225, 0) 100%);

              .nonamed-img-little {
                height: 60%;
                text-align: center;
                display: flex;
                justify-content: center;
                align-items: center;

                .box-dec {
                  height: 80%;              /* 稍微缩小以显示边框 */
                  width: 80%;               /* 稍微缩小以显示边框 */
                  position: relative;       /* 相对定位，用于动画 */
                  border-radius: 10px;      /* 圆角边框 */
                  overflow: hidden;         /* 防止内容溢出 */

                  /* 发光边框效果 */
                  box-shadow: 0 0 10px #7BFFF4, /* 外层发光 */
                  0 0 20px #67A4E1; /* 内层发光 */
                  animation: glow 2s ease-in-out infinite; /* 动态发光动画 */

                  .userImg-little {
                    height: 100%;
                    width: 100%;
                    object-fit: cover;      /* 确保图片按比例填充 */
                    border-radius: 2px;    /* 与容器圆角一致 */
                  }
                }
              }

              .nonamed-name-little {
                height: 15%;
                text-align: center;
                font-weight: 500;
                font-size: 14px;
                color: #000000;
                line-height: 20px;
                font-style: normal;
              }

              .nonamed-point-little {
                height: 15%;
                text-align: center;
                font-weight: 400;
                font-size: 12px;
                color: #000000;
                line-height: 17px;
                font-style: normal;
              }
            }
          }
        }
      }
    }
  }
  .notnamed_list {
    height: 65%;
    display: flex;
    justify-content: space-around;
    flex-direction: column;
    .nonamed-title {
      height: 15%;
      display: flex;
      align-items: center;
      .num-font {
        font-weight: 400;
        font-size: 14px;
        color: #C5E5FF;
        text-align: left;
        font-style: normal;
        margin: 2%;

        .num-point {
          color: #7BFFF4;
          padding-right: 4px;
          animation: blink 1s infinite; /* 添加闪烁动画 */
        }
      }
    }

    .list_table {
      height: 85%;
    }
  }
}
</style>
