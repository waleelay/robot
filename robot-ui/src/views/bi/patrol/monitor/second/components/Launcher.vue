<template>
  <div class="box p30 mt10 flx-center flex-column catcher">
    <div class="img mt10 wp156 hp116">
      <img src="@/assets/images/new-bi/launcher.png" alt="" class="w100 h100" />
    </div>
    <div class="mt20 flx-center desc">
      <div>连接状态：{{ launcherInfo.connectStatus === 0 ? '已连接' : '未连接' }}</div>
      <div class="ml35">
        安全开关：<el-switch
          :value="isLauncherSafetyOn(launcherDevice)"
          active-text="开启"
          inactive-text="关闭"
          active-color="#159AFF"
          inactive-color="#5E5E5E"
          @change="setLauncherSafety(launcherDevice, $event)">
        </el-switch>
      </div>
    </div>
    <div class="mt20 flx-center flex-wrap" style="margin-top: -10px; margin-left: -10px;">
      <div class="item p10 flx-center flex-column mt10 ml10" :class="{ 'is-active': item.count > 0, 'pointer-events': (!item.count || !launcherInfo.safeSwitch || !launcherInfo.connectStatus) ? 'none' : 'auto' }" v-for="(item, index) in launcherInfo.bullets" :key="item.id">
        <div class="text">{{ index + 1 }}号位</div>
        <div class="status pl11 mt4">{{ item.count ? '有' : '无' }}发射物</div>
        <div class="btns mt4">
          <el-button type="primary" class="wp58 hp30" :disabled="!isLauncherSafetyOn(launcherDevice)" @click="firePayload(launcherDevice, index + 1, `launcher_${index + 1}`)">发射</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import yuntai from './yuntai';
export default {
  name: 'Catcher',
  mixins: [yuntai],
  data() {
    return {
      switchValue: false,
      launcherInfo: {
        connectStatus: 0,
        safeSwitch: false,
        bullets: [
          { id: 'launcher1', count: 4 },
          { id: 'launcher2', count: 4 },
          { id: 'launcher3', count: 0 },
          { id: 'launcher4', count: 4 },
          { id: 'launcher5', count: 4 },
          { id: 'launcher6', count: 4 },
        ]
      }
    }
  },

}
</script>
<style scoped lang="scss">
.box {
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
  .desc {
    color: rgba(255, 255, 255, 0.80);
    font-family: "Microsoft YaHei";
    font-size: 14px;
    line-height: 18px;
  }
  .item {
    background: linear-gradient(180deg, rgba(18, 20, 43, 0.00) 0%, #12142B 100%);
    border: 1px solid #747474;
    box-shadow: 0 0 20px 0 rgba(162, 162, 162, 0.50) inset;
    &.is-active {
      border-color: #0063AF;
      box-shadow: 0 0 20px 0 rgba(0, 145, 223, 0.50) inset;
      .status::before {
        background: #45E780;
      }
      .btns {
        ::v-deep .el-button {
          background: #021328;
          box-shadow: 0 0 14px 2px #09F inset;
        }
      }
    }
    .text {
      color: #FFF;
      font-family: "Alibaba PuHuiTi";
      font-size: 16px;      
      line-height: 22px;
      letter-spacing: 0.32px;
    }
    .status {
      position: relative;
      color: #FFF;
      font-family: "Alibaba PuHuiTi";
      font-size: 12px;
      line-height: 16px;
      letter-spacing: 0.24px;
      &::before {
        position: absolute;
        top: 4.5px;
        left: 0;
        content: '';
        width: 7px;
        height: 7px;
        background: #AFAFAF;
        border-radius: 50% ;
      }
    }
    .btns {
      ::v-deep .el-button {
        background: #0A0A0A;
        box-shadow: 0 0 14px 2px #A6A6A6 inset;
      }
    }
  }
}
::v-deep {
  .el-switch {
    line-height: 18px !important;
    line-height: 16px;
    // &.is-checked .el-switch__core {
    //   border-color: var(--success-color) !important;
    //   background-color: var(--success-color) !important;
    // }
    // &.with-text {
      .el-switch__label.el-switch__label--right {
        margin-left: 3px;
      }
      .el-switch__core {
        width: 50px !important;
        &:after {
          top: 2px;
          left: 2px;
          width: 14px;
          height: 14px;
        }
      }
    // }
    &__label {
      position: absolute;
      display: none !important;
      // height: 16px;
      font-weight: normal !important;
      z-index: 2000;
      * {
        font-size: 12px !important;
      }
      &.el-switch__label--left {
        margin-right: 0;
        margin-left: 19px;
      }
      &.el-switch__label--right {
        margin-left: 3px;
      }
      &.is-active {
        display: inline-block !important;
        color: #fff !important
      }
    }
    &.is-checked .el-switch__core::after {
      left: unset;
      right: 3px;
    }
  }
}

.btns {
  ::v-deep .el-button {
    padding: 0;
    color: #FFF;
    font-size: 12px;
    letter-spacing: 0.24px;
    border-radius: 4px;
    border: none;
    text-align: center;
    .svg-icon {
      font-size: 16px;
      cursor: pointer;
    }
  }
}
</style>