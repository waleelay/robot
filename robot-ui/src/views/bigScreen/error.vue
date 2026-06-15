<template>
  <div class="middle_left">
    <div class="abnormal_statistics">
      <div class="item">
        <div class="icon error">
          <img :src="require('../../assets/bigScreen/icon/error.png')"/>
        </div>
        <div class="inform">
          <div class="font">异常总数</div>
          <div class="num">{{errorNum}}</div>
        </div>
      </div>
      <div class="item">
        <div class="icon handle">
          <img :src="require('../../assets/bigScreen/icon/handle.png')"/>
        </div>
        <div class="inform">
          <div class="font">未解决异常</div>
          <div class="num">{{noDealNum}}</div>
        </div>
      </div>
      <div class="item">
        <div class="icon errorpeople">
          <img :src="require('../../assets/bigScreen/icon/errorpeople.png')"/>
        </div>
        <div class="inform">
          <div class="font">紧急异常</div>
          <div class="num">{{severity}}</div>
        </div>
      </div>
      <div class="item">
        <div class="icon errorsport">
          <img :src="require('../../assets/bigScreen/icon/errorsport.png')"/>
        </div>
        <div class="inform">
          <div class="font">普通异常</div>
          <div class="num">{{normal}}</div>
        </div>
      </div>
    </div>
    <div class="abnormal_list">
      <dv-scroll-board :config="anomalyList" ref="scrollBoard"/>
    </div>
  </div>
</template>


<script>
import {ErrorStatistic} from '@/api/bigScreen'
import { getRobotList} from '@/api/robot/robotMessage.js'
export default {

  data() {
    return {
      //异常数
      errorNum:0,
      noDealNum:0,
      severity:0,
      normal:0,
      resultListData:[],
      dogId: 1,
      queryParams: {
        dogState: 1,
        dogName: "",
        pageNum: 1,
        pageSize: 50,
      },
    };
  },
  computed: {
    //异常表格
    anomalyList() {
      return {
        header: ['地图', '点位', '负责机器','类型','状态'],
        data: this.resultListData.length !== 0 ? this.resultListData : [],
        index: true,
        columnWidth: [40],
        align: ['center'],
        headerBGC: '#093661',
        oddRowBGC: '#063462',
        evenRowBGC: '#062B50'
      };
    }
  },
  created() {
    this.initData()
  },
  methods: {
    async initData() {
      // await this.getDogList();
      await this.getErrorDate();
    },
    getDogList() {
      return getRobotList(this.queryParams).then(res => {
        this.dogId=res.rows[0].dogId
      })
    },
    getErrorDate() {
      let param = {dogId : 1}
      ErrorStatistic(param).then(res => {
        console.log('ErrorStatistic response:', res);
        this.errorNum = res.data.ErrorTotalNum;    // 根据实际字段名调整
        this.noDealNum = res.data.NotDealNum;  // 根据实际字段名调整
        this.severity = res.data.ServerErrorNum;    // 根据实际字段名调整
        this.normal = res.data.NormErrorNum;        // 根据实际字段名调整
        this.resultListData = res.data.AllErrorInfo.map(item => {
          return [
            item.MapName,
            item.PointName,
            item.dogName,
            item.ErrorType === 1? '紧急':'普通',
            item === 0? '未处理':'已处理'
          ]
        }); // 更新表格数据
        if (this.$refs.scrollBoard) {
          this.$refs.scrollBoard.updateRows(this.resultListData);
        }
      }).catch(err => {
        console.error('获取异常统计失败:', err);
      });
    },
    // getErrorDate() {
    //   ErrorStatistic(this.dogId).then(res => {
    //     console.log(res)
    //     // this.errorNum = res.
    //     // this.noDealNum = res.
    //     // this.severity = res.
    //     // this.normal = res.
    //     // this.resultListData = res.rows
    //     this.$refs.scrollBoard.updateRows(this.resultListData);
    //   })
    // }
  },
};
</script>
<style lang="scss" scoped>
.middle_left {
  height: 100%;
  padding: 5%;
  display: flex;
  justify-content: space-between;
  flex-direction: column;
  align-items: center;

  .abnormal_statistics {
    height: 40%;
    width: 90%;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    grid-template-rows: repeat(2, 1fr);
    gap: 10px;
    .item {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 5%;
      background-color: rgba(53, 78, 102, 0.6);

      .icon {
        width: 45%;
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .error {
        background-image: url("../../assets/bigScreen/icon/errorBac.png");
      }
      .handle {
        background-image: url("../../assets/bigScreen/icon/handleBac.png");
      }
      .errorpeople {
        background-image: url("../../assets/bigScreen/icon/errorpeopleBac.png");
      }
      .errorsport {
        background-image: url("../../assets/bigScreen/icon/errorsportBac.png");
      }
      .inform {
        width: 50%;
        height: 100%;
        display: flex;
        flex-direction: column;
        justify-content: center;

        .font {
          text-align: center;
          font-size: 16px;
        }
        .num {
          text-align: center;
          font-size: 30px;
          color: #00eaff;
        }
      }
    }
  }
  .abnormal_list {
    height: 57%;
    width: 100%;
  }
}
</style>
