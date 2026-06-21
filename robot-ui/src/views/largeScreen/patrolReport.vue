<template>
  <div class="patrolReport">
    <div class="table-box">
      <el-table :data="tableData" @header-click="getReportList" @row-click="handleClick" height="240">
        <el-table-column
          v-for="item in tableList"
          :key="item.key"
          :prop="item.key"
          :label="item.name"
          :width="item.width"
        >
          <template slot-scope="scope">
            <span
              :class="{
                'is-abnormal':
                  item.key == 'isAbnormal' && scope.row[item.key] === '是',
              }"
            >
              {{ scope.row[item.key] }}
            </span>
          </template>
        </el-table-column>
        <!-- <el-table-column label="操作">
        <template slot-scope="scope">
          <el-button type="text" @click="handleClick(scope.row)"
            >详情</el-button
          >
        </template>
      </el-table-column> -->
      </el-table>
    </div>
    <el-dialog
      class="dialog-wrapper"
      v-dialogDrag
      width="1270px"
      height="758px"
      :visible.sync="dialogVisible"
      :modal-append-to-body="false"
      :close-on-click-modal="false"
      :modal="false"
      :show-close="true"
      :before-close="handleClose"
      title="巡查报告"
    >
      <div class="dialog-content">
        <div class="info-box">
          <div class="info-title">巡查信息</div>
          <div class="info-details">
            <div
              class="info-row"
              v-for="(item, index) in infoList"
              :key="index"
            >
              <span class="label">{{ item.label }}：</span>
              <span class="value">{{ infoData[item.key] }}</span>
            </div>
          </div>
        </div>
        <div class="info-box info-box1">
          <div class="info-title">异常信息</div>
          <el-table :data="infoTtableData" height="230">
            <el-table-column type="index" width="100"> </el-table-column>
            <el-table-column
              v-for="item in infoTableList"
              :key="item.key"
              :prop="item.key"
              :label="item.name"
            ></el-table-column>
            <el-table-column label="图片">
              <template slot-scope="scope">
                <el-button type="text" @click="handleImg(scope.row)"
                  >点击查看</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="info-image">
          <img v-if="showImg" :src="getImageSrc(errorImageUrl)" class="image" />
          <el-empty v-else description="请点击查看异常图片"></el-empty>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { recordInfo } from '@/api/cruise/resultandError'
export default {
  name: "patrolReport",
  props: {
    dogId: {
      type: [Number, String],
      required: true,
    }
  },
  watch: {
    dogId(newVal) {
      this.getReportList()
    }
  },
  data() {
    return {
      tableList: [
        { name: "时间", key: "time" },
        { name: "任务名称", key: "TaskName" },
        { name: "设备名称", key: "dogName" },
        { name: "是否发现异常", key: "isAbnormal", width: 110 },
      ],
      tableData: [],
      dialogVisible: false,
      infoList: [
        { label: "开始时间", key: "StartTime" },
        { label: "结束时间", key: "time" },
        { label: "任务名称", key: "TaskName" },
        { label: "设备名称", key: "dogName" },
        { label: "是否发现异常", key: "isAbnormal" },
        { label: "异常数量", key: "ErrorCounts" },
      ],
      infoData: {},
      infoTableList: [
        { name: "时间", key: "CreateTime" },
        { name: "地点", key: "area" },
        { name: "详情", key: "ErrorName" },
      ],
      infoTtableData: [],
      errorImageUrl: ``,
      showImg: false,
      timer:null,
    };
  },
  methods: {
    getReportList() {
      let query = {dogId:this.dogId }
      recordInfo(query).then(res => {
        // console.log(res)
        let data = res.data
        this.tableData = data.map(item => {
          return {
            ...item,
            isAbnormal: item.ErrorInfos.length === 0 ? '否' : '是',
            time:item.EndTime===null?item.StartTime:item.EndTime.split('.')[0]
          }
        })
      })

    },
    handleClick(row) {
      this.infoData = row;
      // console.log(row)
      this.infoTtableData = row.ErrorInfos.map(item => {
        return {
          ...item,
          area: item.MapName + item.PointName
        }
      })
      this.toggleDialog();
    },
    //点击查看图片
    handleImg(row) {
      // console.log(row)
      this.showImg = true
      this.errorImageUrl = row.ImageUrl
    },
    //获取图片路径
    getImageSrc(imgUrl) {
      return `https://${imgUrl}`;
    },
    toggleDialog() {
      this.dialogVisible = true;
    },
    handleClose() {
      this.dialogVisible = false;
      this.showImg = false
    },
  },
};
</script>
<style lang="scss" scoped>
.patrolReport {
  .table-box {
    width: calc(100% - 5px);
    padding-top: 10px;
    box-sizing: border-box;
    ::v-deep .el-table {
      &::before {
        height: 0;
      }
      .el-table__cell {
        border-bottom: none;
        text-align: center;
        padding: 0;
      }
      .el-table__header-wrapper {
        th {
          height: 32px;
          line-height: 32px;
          background: #093661;
          font-size: 12px;
          color: #ffffff;
        }
      }
      .el-table__body-wrapper {
        background: #062b50;
        .el-table__row {
          font-size: 12px;
          color: #ffffff;
          height: 32px;
          line-height: 32px;
          &:nth-child(odd) {
            background: #062b50;
          }
          &:nth-child(even) {
            background: #063462;
          }
          &:hover {
            background-color: #435f7a;
          }
        }
      }
    }
    ::v-deep .el-table--enable-row-hover .el-table__body tr {
      cursor: pointer;
      &:hover > td {
        background-color: transparent;
      }
    }
    .is-abnormal {
      color: #ffa17f;
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
      height: 694px;
      background-image: url("../../assets/images/largescreen/dialog-bg1.png");
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
    flex-wrap: wrap;
    position: relative;

    .info-box {
      width: 581px;
      margin-bottom: 10px;
      &.info-box1 {
        width: 1198px;
        ::v-deep .el-table {
          &::before {
            height: 0;
          }
          .el-table__cell {
            border-bottom: none;
          }
          .el-table__header-wrapper {
            th {
              height: 40px;
              line-height: 40px;
              background: #355e87;
              font-size: 14px;
              color: #ffffff;
            }
          }
          .el-table__body-wrapper {
            background: #062b50;
            .el-table__row {
              font-size: 14px;
              color: #ffffff;
              height: 40px;
              line-height: 40px;
              &:nth-child(odd) {
                background: #12334b;
              }
              &:nth-child(even) {
                background: #233f5c;
              }
              &:hover {
                background-color: #355e87;
              }
            }
          }
        }
        ::v-deep .el-table--enable-row-hover .el-table__body tr:hover > td {
          background-color: transparent;
        }
      }

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
      height: 267px;
      background-image: url("../../assets/images/largescreen/dialog-img-bg1.png");
      background-size: 100% 100%;
      position: absolute;
      right: 20px;
      top: 50px;
      display: flex;
      justify-content: center;
      align-items: center;
      .image {
        width: 90%;
        height: 95%;
      }
    }
  }
}
</style>
