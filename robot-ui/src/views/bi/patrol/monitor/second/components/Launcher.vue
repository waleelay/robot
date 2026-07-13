<template>
  <div class="box p30 mt10 flx-center flex-column catcher">
    <div class="img mt10 wp156 hp116">
      <img src="@/assets/images/new-bi/launcher.png" alt="" class="w100 h100" />
    </div>
    <div class="mt20 flx-center desc">
      <div>连接状态：{{ launcherConnected ? '已连接' : '未连接' }}</div>
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
    <div class="mt20 flx-center flex-wrap" style="position: relative; margin-top: -10px; margin-left: -10px;">
      <div class="item p10 flx-center flex-column mt10 ml10" :class="{ 'is-active': item.state === 1, 'is-disabled': !canFireTube(item) }" v-for="(item, index) in launcherTubes" :key="item.tube">
        <div class="text">{{ item.tube }}号位</div>
        <div class="status pl11 mt4">{{ launcherTubeLabel(item) }}</div>
        <div class="btns mt4">
          <el-button type="primary" class="wp58 hp30" :disabled="!canFireTube(item)" @click="handleChangeConfirm(true, index)">发射</el-button>
        </div>
      </div>
      <div class="confirm-div w100 h100 flx-center flex-column wp266 hp206 mt10 ml23" v-if="showConfirm">
        <div class="desc">是否确认发射</div>
        <div class="btns mt14">
          <el-button type="primary" class="wp58 hp30" @click="execute">是</el-button>
          <el-button type="primary" class="wp58 hp30" @click="handleChangeConfirm(false)">否</el-button>
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
      showConfirm: false,
      index: null
    }
  },
  computed: {
    launcherStatus() {
      const device = this.launcherDevice || {}
      return device.status || device.runtimeStatus || {}
    },
    launcherConnected() {
      return this.launcherStatus.connected !== false
    },
    launcherTubes() {
      const status = this.launcherStatus
      if (Array.isArray(status.tubes) && status.tubes.length) {
        return status.tubes.map(item => this.normalizeLauncherTube(item))
      }
      const profile = (this.launcherDevice && this.launcherDevice.controlProfile) || {}
      const tubes = Array.isArray(profile.tubes) && profile.tubes.length ? profile.tubes : [1, 2, 3, 4, 5, 6]
      return tubes.map(tube => this.normalizeLauncherTube({ tube }))
    }
  },
  methods: {
    handleChangeConfirm(val, index) {
      this.showConfirm = val
      this.index = index
    },
    async execute() {
      const tube = this.launcherTubes[this.index]
      if (!tube) return
      await this.firePayload(this.launcherDevice, tube.tube, `launcher_${tube.tube}`)
      this.showConfirm = false
    },
    normalizeLauncherTube(tube) {
      const number = Number(tube.tube) || 0
      const state = tube.state === undefined ? 255 : Number(tube.state)
      return {
        tube: number,
        state,
        stateName: tube.stateName || this.launcherTubeStateName(state)
      }
    },
    launcherTubeStateName(state) {
      return {
        0: 'EMPTY',
        1: 'LOADED',
        2: 'FIRING',
        3: 'BLOCKED',
        255: 'UNKNOWN'
      }[state] || 'UNKNOWN'
    },
    launcherTubeLabel(tube) {
      return {
        EMPTY: '空',
        LOADED: '已装填',
        FIRING: '发射中',
        BLOCKED: '堵塞',
        UNKNOWN: '未知'
      }[tube.stateName] || '未知'
    },
    canFireTube(tube) {
      return this.launcherConnected && this.isLauncherSafetyOn(this.launcherDevice) && tube && tube.state === 1
    }
  }
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
    &.is-active:not(.is-disabled) {
      border-color: #0063AF;
      box-shadow: 0 0 20px 0 rgba(0, 145, 223, 0.50) inset;
      .status::before {
        background: #45E780;
      }
      .btns {
        ::v-deep .el-button {
          background: #021328;
          box-shadow: 0 0 14px 2px #09F inset;
          &.is-disabled {
            background: #0A0A0A;
            box-shadow: 0 0 14px 2px #A6A6A6 inset;
            cursor: not-allowed;
            // pointer-events: none;
          }
          &:not(.is-disabled) {
            &:active {
              color: #0BF9FE;
              box-shadow: 0 0 10px 3px #0BF9FE inset;
            }
          }
        }
      }
      &.is-disabled {
        background: linear-gradient(180deg, rgba(18, 20, 43, 0.00) 0%, #12142B 100%);
        border: 1px solid #747474;
        box-shadow: 0 0 20px 0 rgba(162, 162, 162, 0.50) inset;
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
