<template>
  <div class="box p30 mt10 flx-center flex-column catcher">
    <div class="img mt10 wp166 hp113">
      <img src="@/assets/images/new-bi/catcher.png" alt="" class="w100 h100" />
    </div>
    <div class="mt27 flx-center desc">
      <div>连接状态：已连接</div>
      <div class="ml35">
        安全开关：<el-switch
          :value="isNetGunSafetyOn(netGunDevice)"
          active-text="开启"
          inactive-text="关闭"
          active-color="#159AFF"
          inactive-color="#5E5E5E"
          @change="setNetGunSafety(netGunDevice, $event)">
        </el-switch>
      </div>
    </div>
    <div class="btns mt30">
      <el-button type="primary" class="wp124 hp30" @click="handleChangeConfirm(true)" :disabled="!isNetGunSafetyOn(netGunDevice)">发射</el-button>
    </div>
    <div class="confirm-div w100 h100 flx-center flex-column" v-if="showConfirm">
      <div class="desc">是否确认发射</div>
      <div class="btns mt14">
        <el-button type="primary" class="wp58 hp30" @click="execute">是</el-button>
        <el-button type="primary" class="wp58 hp30" @click="handleChangeConfirm(false)">否</el-button>
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
      showConfirm: false
    }
  },
  methods: {
    handleChangeConfirm(val) {
      this.showConfirm = val
    },
    async execute() {
      await this.firePayload(this.netGunDevice, 1, 'net_gun_fire')
      this.showConfirm = false
    }
  }
}
</script>
<style scoped lang="scss">
.box {
  position: relative;
  background: linear-gradient(180deg, rgba(18, 20, 43, 0) 0%, #12142B 100%);
  box-shadow: 0 0 20px 0 rgba(33, 108, 149, 0.3) inset;
  .desc {
    color: rgba(255, 255, 255, 0.80);
    font-family: "Microsoft YaHei";
    font-size: 14px;
    line-height: 18px;
  }
  .confirm-div {
    position: absolute;
    top: 0;
    left: 0;
    border-radius: 4px;
    border: 2px solid #0BF9FE;
    background: rgba(4, 24, 65, 0.60);
    backdrop-filter: blur(8px);
    transition: all linear .3s;
    z-index: 2001;
    .desc {
      color: #0BF9FE;
      font-family: "Microsoft YaHei";
      font-size: 20px;
      font-weight: 600;
      line-height: 27px;
    }
    ::v-deep {
      .el-button {
        color: #FFF;
        font-family: "Alibaba PuHuiTi";
        border-radius: 4px;
        background: #021328;
        box-shadow: 0 0 14px 2px #09F inset;
        font-size: 12px;
        letter-spacing: 0.24px;
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
    background: #021328;
    border-radius: 4px;
    border: none;
    box-shadow: 0 0 14px 2px #09F inset;
    text-align: center;
    .svg-icon {
      font-size: 16px;
      cursor: pointer;
    }
  }
}
</style>