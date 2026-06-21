<template>
  <div class="info-content flx-justify-between h100 xlxc">
    <div class="side-box d-flex flex-column left d-flex pb30">
      <div class="card xlxcqk h100 ovya">
        <div class="title flx-justify-between">
          <div class="text">车辆进场</div>
          <!-- <div class="buttons">
            <el-button tt="primary">上一步</el-button>
            <el-button tt="primary" class="ml10">下一步</el-button>
          </div> -->
        </div>
        <div class="content mt10 p10">
          <div class="step1" :class="{ 'disabled': step < 0 }">
            <div class="box p10 pt12 pl12">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">1</div>
                  <div class="ml10">
                    <div class="t2">调度处置设备</div>
                    <div class="t3">调度设备前往车辆入口，协助现场人员进行处置</div>
                  </div>
                </div>
                <div class="flx-center">
                  <!-- <div class="btn-status danger flx-center">
                    <svg-icon icon-class="notice1" style="font-size: 12px" class="mr10" />
                    <span>告警1条</span>
                  </div> -->
                  <div class="btn-status success">已完成</div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between">
                <div class="t4 mt17">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：车间大门身份核验点
                </div>
                <!-- <div class="buttons">
                  <el-button tt="primary">一键调度</el-button>
                </div> -->
              </div>
              <div class="t5 mt10">设备位置状态信息</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>距离目的地距离</div>
                  <div>位置状态</div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗1</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status pending">前往中</span>
                  </div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗2</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status success">已到达</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="flx-justify-between mt10">
              <div class="t4 text-c-error">所有设备已到位，请进行下一步:</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 0">下一步</el-button>
                <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 0">跳过</el-button>
              </div>
            </div>
          </div>
          <div class="step2" :class="{ 'disabled': step < 1 }">
            <div class="box p10 pb20 mt10">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">2</div>
                  <div class="ml10">
                    <div class="t2">身份核验 </div>
                    <div class="t3">通过机器狗核验现场人员和车辆的身份信息</div>
                  </div>
                </div>
                <div class="flx-center">
                  <!-- <div class="btn-status danger flx-center">
                    <svg-icon icon-class="notice1" style="font-size: 12px" class="mr10" />
                    <span>告警1条</span>
                  </div> -->
                  <div class="btn-status pending">等待中</div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="t4 mt10">预约信息</div>
              <div class="yuyue p10 mt10 flx-center">
                <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="24px" style="max-width: 20px; max-height: 24px;">
                <span class="ml10">驾驶员:张俊峰</span>
                <span class="ml20">车牌:川AI9T58</span>
                <span class="ml20">货物类型:日用品</span>
                <span class="ml20">预约时间:2025-10-23 9:00</span>
              </div>
              <div class="buttons mt10" style="text-align: right;">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 1">开始身份核验</el-button> 
                <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 1">重新身份核验</el-button> 
              </div>
              <div class="mt10">
                <div class="t4">识别结果</div>
                <div class="flx-justify-between mt10">
                  <div class="person-photo flx-center">
                    <div class="yuan flx-center flex-column">
                      <img src="@/assets/images/bi/new/icon1.png" alt="" width="53px" height="64px">
                      <span class="mt10">张三</span>
                    </div>
                    <div class="yuan verify flx-center flex-column ml50">
                      <div class="img-modal flx-center flex-column" style="width: 53px; height: 64px">
                        <svg-icon icon-class="error" style="font-size: 20px" />
                        <span class="mt4 t4">失败</span>
                      </div>
                      <img src="@/assets/images/bi/new/icon1.png" alt="" width="53px" height="64px">
                      <span class="mt10">张三</span>
                    </div>
                  </div>
                  <div class="car-photo flx-center">
                    <div class="flx-center flex-column">
                      <div class="yuan" style="width: 101px; height: 54px; line-height: 54px;">川A I9T58</div>
                      <span class="mt10">川AI9T58</span>
                    </div>
                    <div class="verify success flx-center flex-column ml30">
                      <div class="img-modal flx-center flex-column" style="width: 94px; height: 54px">
                        <svg-icon icon-class="success" style="font-size: 20px" />
                        <span class="mt4 t4">成功</span>
                      </div>
                      <img src="@/assets/images/bi/new/car.png" alt="" width="94px" height="54px">
                      <span class="mt10">川AI9T58</span>
                    </div>
                  </div>
                </div>
                <div class="verify-result p10">
                  <div class="result success">核验通过！</div>
                  <div class="notice mt10 flx-center">
                    <svg-icon icon-class="info" style="font-size: 14px" />
                    <span class="ml10">身份核验成功，请引导车辆前往作业区，并进入下一步</span>
                  </div>
                </div>
                <div class="flx-justify-between mt10">
                  <div class="t4">语音引导</div>
                  <div class="flx-center mt10">
                    <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                    <div class="t4 ml10" style="line-height: 12px">机器狗A</div>
                  </div> 
                </div>
                <div class="flx-justify-between">
                  <div class="notice p10 mt10 flx-center flex1">
                    <svg-icon icon-class="notice" style="font-size: 14px" />
                    <span class="ml10">身份核验通过，请上车跟随前往卸货区！</span>
                  </div>
                  <div class="buttons ml10">
                    <el-button tt="primary" :disabled="btnDisabledIndex <= 1">立即引导</el-button>
                  </div>
                </div>
              </div>
            </div>
            <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary" :disabled="btnDisabledIndex <= 1">下一步</el-button> 
            </div>
          </div>
          <div class=" mt10" :class="{ 'disabled': step < 2 }">
            <div class="box p10 pt12 pl12">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">3</div>
                  <div class="ml10">
                    <div class="t2">前往作业区</div>
                    <div class="t3">引导车辆前往作业区，观察记录途中的违规事件</div>
                  </div>
                </div>
                <div class="flx-center">
                  <!-- <div class="btn-status danger flx-center">
                    <svg-icon icon-class="notice1" style="font-size: 12px" class="mr10" />
                    <span>告警1条</span>
                  </div> -->
                  <div class="btn-status success">已完成</div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between">
                <div class="t4 mt17">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：车间大门身份核验点
                </div>
              </div>
              <div class="t5 mt10">设备位置状态信息</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>距离目的地距离</div>
                  <div>位置状态</div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗1</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status pending">前往中</span>
                  </div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗2</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status success">已到达</span>
                  </div>
                </div>
              </div>
              <div class="t4 mt10">告警事件</div>
              <div class="notice p10 mt10 flx-center">
                <svg-icon icon-class="info" style="font-size: 14px" />
                <span class="ml10">请通过右侧实时视频注意观察车门、车窗、车轮等情况，出现以下情况可快速点击告警</span>
              </div>
              <div class="buttons mt10">
                <el-button tt="primary">车辆偏离轨迹</el-button>
                <el-button tt="primary" class="ml10">车速过慢</el-button>
                <el-button tt="primary" class="ml10">车辆超速</el-button>
                <el-button tt="primary" class="ml10">无故停车</el-button>
              </div>
            </div>
            <div class="flx-justify-between mt10">
              <div class="t4 text-c-error">所有设备已经到达作业区，请点击进入下一步:</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 2">下一步</el-button>
                <!-- <el-button tt="primary" class="ml10">跳过</el-button> -->
              </div>
            </div>
          </div>
          <div class="step4" :class="{ 'disabled': step < 3 }">
            <div class="box p10 pb20 mt10">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">4</div>
                  <div class="ml10">
                    <div class="t2">车辆作业</div>
                    <div class="t3">车辆到达作业区，观察记录作业过程中的违规事件</div>
                  </div>
                </div>
                <div class="btn-status pending">等待中</div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="t4">语音引导</div>
                <div class="flx-center mt10">
                  <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                  <div class="t4 ml10" style="line-height: 12px">机器狗A</div>
                </div> 
              </div>
              <div class="flx-justify-between">
                <div class="notice p10 mt10 flx-center flex1">
                  <svg-icon icon-class="notice" style="font-size: 14px" />
                  <span class="ml10">请规范卸货，全程监控记录！</span>
                </div>
                <div class="buttons ml10">
                  <el-button tt="primary" :disabled="btnDisabledIndex <= 3">立即引导</el-button>
                </div>
              </div>
              <div class="t4 mt10">设备控制</div>
              <div class="notice p10 mt10 flx-center">
                <svg-icon icon-class="info" style="font-size: 14px" />
                <span class="ml10">请通过右侧控制台控制机器狗跟拍卸货区域，并观察控制无人机，观察车顶部和周围。</span>
              </div>
              <div class="t4 mt10">告警事件</div>
              <div class="notice p10 mt10 flx-justify-between" style="align-items: flex-start;">
                <svg-icon icon-class="info" style="font-size: 14px" />
                <div class="ml10" style="text-align: left">请通过右侧实时视频注意观察确认顶部是否有夹层，是否有无关人员靠近，出现以下情况可快速点击告警</div>
              </div>
              <div class="buttons mt10">
                <el-button tt="primary">车顶发现夹层</el-button>
                <el-button tt="primary" class="ml10">无关人员靠近</el-button>
              </div>
            </div>
            <div class="flx-justify-between mt10">
              <div class="t4 text-c-error">请等待现场司机完成卸货并喊话申请返程，并进入下一步！:</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 3">下一步</el-button>
                <!-- <el-button tt="primary" class="ml10">跳过</el-button> -->
              </div>
            </div>
            <!-- <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary">下一步</el-button>
              <el-button tt="primary" class="ml10">跳过</el-button>
            </div> -->
          </div>
          <div class="step5" :class="{ 'disabled': step < 4 }">
            <div class="box p10 pb20 mt10">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">5</div>
                  <div class="ml10">
                    <div class="t2">车辆返程</div>
                    <div class="t3">车辆完成作业后返程区前往出口，观察记录返程过程中的违规事件</div>
                  </div>
                </div>
                <div class="btn-status pending">等待中</div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="w60">
                  <div class="t4">司机申请返程信息</div>
                  <div class="mt10 flx-justify-between p10">
                    <img src="@/assets/images/bi/new/dog_l.png" alt="" width="92px" height="59px">
                    <div>
                      <div class="t4">接收设备名称：A门机器狗</div>
                      <div class="flx-justify-between mt10">
                        <div class="t4 mr10">汇报语音：</div>
                        <div class="play flx-justify-between">
                          <svg-icon icon-class="notice" style="font-size: 14px" />
                          <span class="ml10">60'</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="w40 pl10">
                  <div class="t4">语音转文本</div>
                  <el-input type="textarea" :rows="4" class="mt10" placeholder="卸货完成，申请返程" disabled />
                </div>
              </div>
              <div class="flx-justify-between mt10">
                <div class="t4">中心语音回复</div>
                <div class="btn-status pending">待回复</div>
                <!-- <div class="btn-status success">已回复</div> -->
              </div>
              <div class="mt10">
                <el-input type="textarea" :rows="4" placeholder="中心已收到，车辆检查合格，请关闭车厢门，跟随机器狗引导返程" />
              </div>
              <div class="flx-justify-between mt10">
                <span class="t4"></span>
                <div class="buttons" style="text-align: right;">
                  <el-button tt="primary" :disabled="btnDisabledIndex <= 4">点击回复</el-button>
                </div>
              </div>
              <div class="t4 mt10">设备控制</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>操作</div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗2</div>
                  <div style="text-align: center ;">
                    <div class="buttons" style="text-align: right;">
                      <el-button tt="primary" :disabled="btnDisabledIndex <= 4">立即引导返航</el-button>
                      <el-button tt="primary" :disabled="btnDisabledIndex <= 4">自动伴飞跟随</el-button>
                    </div>
                  </div>
                </div>
              </div>
              <div class="flx-justify-between">
                <div class="t4 mt17">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：车间大门身份核验点
                </div>
              </div>
              <div class="t5 mt10">设备位置状态信息</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>距离目的地距离</div>
                  <div>位置状态</div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗1</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status pending">前往中</span>
                  </div>
                </div>
                <div class="item">
                  <div style="text-align: left;">机器狗2</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status success">已到达</span>
                  </div>
                </div>
              </div>
              <div class="t4 mt10">告警事件</div>
              <div class="notice p10 mt10 flx-justify-between" style="align-items: flex-start;">
                <svg-icon icon-class="info" style="font-size: 14px" />
                <div class="ml10" style="text-align: left">提示：请通过右侧实时视频注意观察车门、车窗、车轮等情况，出现以下情况可快速点击告警</div>
              </div>
              <div class="buttons mt10">
                <el-button tt="primary">车辆偏离轨迹</el-button>
                <el-button tt="primary" class="ml10">车速过慢</el-button>
                <el-button tt="primary" class="ml10">车辆超速</el-button>
                <el-button tt="primary" class="ml10">无故停车</el-button>
              </div>
              <!-- <div class="mt10">
                <el-input type="textarea" :rows="5" placeholder="处置总结" />
              </div>
              <div class="mt10 flx-justify-between">
                <span class="t4">处置完成系统自动记录生成处置信息</span>
                <div class="buttons">
                  <el-button tt="primary">处置完成</el-button>
                </div>
              </div> -->
            </div>
          </div>
          <div class="step6" :class="{ 'disabled': step < 5 }">
            <div class="box p10 pb20 mt10">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">6</div>
                  <div class="ml10">
                    <div class="t2">车辆离场</div>
                    <div class="t3">车辆到达出口，离场</div>
                  </div>
                </div>
                <div class="flx-center">
                  <!-- <div class="btn-status danger flx-center">
                    <svg-icon icon-class="notice1" style="font-size: 12px" class="mr10" />
                    <span>告警1条</span>
                  </div> -->
                  <div class="btn-status success">已完成</div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="t4">语音引导</div>
                <div class="flx-center mt10">
                  <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                  <div class="t4 ml10" style="line-height: 12px">机器狗A</div>
                </div> 
              </div>
              <div class="flx-justify-between">
                <div class="notice p10 mt10 flx-center flex1">
                  <svg-icon icon-class="notice" style="font-size: 14px" />
                  <span class="ml10">请司机检查是否有物品遗漏后离场！</span>
                </div>
                <div class="buttons ml10">
                  <el-button tt="primary" :disabled="btnDisabledIndex <= 5">立即引导</el-button>
                </div>
              </div>
              <div class="mt10 flx-justify-between">
                <span class="t4">车辆是否已经离场？ </span>
                <div class="buttons">
                  <el-button tt="primary" :disabled="btnDisabledIndex <= 5">确认车辆离场</el-button>
                </div>
              </div>
            </div>
            <div class="mt10 flx-justify-between">
              <span class="t4">点击结束任务，所有设备自动返航，系统自动生成处置记录</span>
              <div class="buttons">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 5">结束任务 </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <CljgRight />
  </div>
</template>

<script>
import CljgRight from './right/index.vue'
export default {
  name: 'xlxc-detail',
  components: { CljgRight },
  data() {
    return {
      step: 6,
      btnDisabledIndex: 6,
    }
  },
  methods: {
    next() {

    }
  }
}
</script>

<style scoped lang="scss">
@import './index.scss'
</style>