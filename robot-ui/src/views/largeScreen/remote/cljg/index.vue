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
          <div class="step1">
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
                  <div class="btn-status" :class="{ pending: currentStep === 1 , success: currentStep > 1}">{{ currentStep === 1 ? '进行中' : '已完成' }}</div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between">
                <div class="t4 mt17">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：{{ pointInfo.rw1.pointName }}
                </div>
                <!-- <div class="buttons">
                  <el-button tt="primary">一键调度</el-button>
                </div> -->
              </div>
              <div class="t5 mt10">设备位置状态信息</div>
              <div class="list mt10" style="height: 108px">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>距离目的地距离</div>
                  <div>位置状态</div>
                </div>
                <div class="item" v-for="(item, index) in defaultInfo.equipmentList" :key="`step1${item.id}`">
                  <div style="text-align: left;">{{ item.name }}</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status" :class="{ pending: !item.status1, 'success': item.status1 }">{{ item.status1 ? '已到达' : '前往中' }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="flx-justify-between mt10">
              <div class="t4 text-c-error">{{ getNoReachStatus(1) ? '' : '所有设备已到位，请进入下一步！' }}</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="getNoReachStatus(1) || currentStep !== 1" @click="updateStep(1)">下一步</el-button>
                <el-button tt="primary" class="ml10" :disabled="getNoReachStatus(1) || currentStep !== 1" @click="updateStep(1)">跳过</el-button>
              </div>
            </div>
          </div>
          <div class="step2 mt10" :class="{ disabled: currentStep < 2 }">
            <div class="box p10 pb20">
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
                  <div class="btn-status" :class="{ waiting: currentStep < 2, pending: currentStep === 2, success: currentStep > 2}">
                    {{ currentStep < 2 ? '等待中' : currentStep === 2 ? '进行中' : '已完成' }}
                  </div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="t4 mt10">预约信息</div>
              <div class="yuyue p10 mt10 flx-center">
                <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="24px" style="max-width: 20px; max-height: 24px;">
                <span class="ml10">驾驶员:{{ defaultInfo.driverName }}</span>
                <span class="ml20">车牌:{{ defaultInfo.licensePlate }}</span>
                <span class="ml20">货物类型:{{ defaultInfo.goodsType }}</span>
                <span class="ml20">预约时间:{{ defaultInfo.reservationTime }}</span>
              </div>
              <div class="buttons mt10" style="text-align: right;">
                <el-button tt="primary" :disabled="currentStep !== 2" @click="verify">开始身份核验</el-button> 
                <el-button tt="primary" class="ml10" :disabled="currentStep !== 2" @click="verify">重新身份核验</el-button> 
              </div>
              <div class="mt10">
                <div class="t4">识别结果</div>
                <div class="flx-justify-between mt10">
                  <div class="person-photo flx-center">
                    <div class="yuan flx-center flex-column">
                      <img :src="getImg(defaultInfo.driverPhoto)" alt="" width="53px" height="64px" style="vertical-align: middle;">
                      <span class="mt10">{{ defaultInfo.driverName }}</span>
                    </div>
                    <div class="yuan verify flx-center flex-column ml50">
                      <div class="img-modal flx-center flex-column" style="width: 53px; height: 64px">
                        <!-- <span class="t4" v-if="verifyInfo.driver.success === null">待核验</span> -->
                        <template v-if="verifyInfo.driver.success !== null">
                          <svg-icon :icon-class="verifyInfo.driver.success ? 'success' : 'error'" style="font-size: 20px" />
                          <span class="mt4 t4">{{ verifyInfo.driver.success ? '成功' : '失败' }}</span>
                        </template>
                      </div>
                      <img v-if="verifyInfo.driver.success" src="@/assets/images/bi/new/icon1.png" alt="" width="53px" height="64px">
                      <div v-else style="width: 53px; height: 64px"></div>
                      <span class="mt10" style="height: 24px">{{ verifyInfo.driver.success ? defaultInfo.driverName : '' }}</span>
                    </div>
                  </div>
                  <div class="car-photo flx-center">
                    <div class="flx-center flex-column">
                      <div class="yuan" style="width: 101px; height: 54px; line-height: 54px;">{{ defaultInfo.licensePlate }}</div>
                      <span class="mt10">{{ defaultInfo.licensePlate }}</span>
                    </div>
                    <div class="verify success flx-center flex-column ml30">
                      <div class="img-modal flx-center flex-column" style="width: 94px; height: 54px">
                        <template v-if="verifyInfo.licensePlate.success !== null">
                          <svg-icon :icon-class="verifyInfo.licensePlate.success ? 'success' : 'error'" style="font-size: 20px" />
                          <span class="mt4 t4">{{ verifyInfo.licensePlate.success ? '成功' : '失败' }}</span>
                        </template>
                      </div>
                      <img v-if="verifyInfo.licensePlate.success" :src="verifyInfo.licensePlate.url" alt="" width="94px" height="54px">
                      <div v-else style="width: 94px; height: 54px"></div>
                      <span class="mt10" style="height: 24px">{{ verifyInfo.licensePlate.success ? defaultInfo.licensePlate : '' }}</span>
                    </div>
                  </div>
                </div>
                <div class="verify-result p10" style="height: 75px">
                  <template v-if="currentStep === 2">
                    <template v-if="verifyInfo.driver.success && verifyInfo.licensePlate.success">
                      <div class="result success">核验通过！</div>
                      <div class="notice mt10 flx-center">
                        <svg-icon icon-class="info" style="font-size: 14px" />
                        <span class="ml10">身份核验成功，请引导车辆前往作业区，并进入下一步</span>
                      </div>
                    </template>
                    <template v-else-if="verifyInfo.driver.success === false && verifyInfo.licensePlate.success === false">
                      <div class="result success">识别失败</div>
                      <div class="notice mt10 flx-center">
                        <svg-icon icon-class="info" style="font-size: 14px" />
                        <span class="ml10">提示：人脸校验失败，请重新核验或人工确认</span>
                      </div>
                    </template>
                    <template v-else>
                      <div class="result">匹配中...</div>
                      <div class="notice mt10 flx-center">
                        <svg-icon icon-class="info" style="font-size: 14px" />
                        <span class="ml10">提示：正在身份核验中，请耐心等待</span>
                      </div>
                    </template>
                  </template>
                </div>
                <div class="flx-justify-between mt10">
                  <div class="t4">语音引导</div>
                  <div class="flx-center mt10">
                    <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                    <div class="t4 ml10" style="line-height: 12px">{{ defaultEquipemnt.name }}</div>
                  </div> 
                </div>
                <div class="flx-justify-between">
                  <div class="notice p10 mt10 flx-center flex1">
                    <svg-icon icon-class="notice" style="font-size: 14px" />
                    <span class="ml10">身份核验通过，请上车跟随前往卸货区！</span>
                  </div>
                  <div class="buttons ml10">
                    <el-button tt="primary" :disabled="!verifyInfo.driver.success || !verifyInfo.licensePlate.success" @click="openStepBtn('身份核验通过，请上车跟随前往卸货区！', 'step2Disabled')">立即引导</el-button>
                  </div>
                </div>
              </div>
            </div>
            <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary" :disabled="step2Disabled || currentStep !== 2" @click="executeStep(2)">下一步</el-button> 
            </div>
          </div>
          <div class="step3 mt10" :class="{ 'disabled': currentStep < 3 }">
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
                  <div class="btn-status" :class="{ waiting: currentStep < 3, pending: currentStep === 3, success: currentStep > 3}">
                    {{ currentStep < 3 ? '等待中' : currentStep === 3 ? '进行中' : '已完成' }}
                  </div>
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between">
                <div class="t4 mt17">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：{{ pointInfo.rw2.pointName }}
                </div>
              </div>
              <div class="t5 mt10">设备位置状态信息</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>距离目的地距离</div>
                  <div>位置状态</div>
                </div>
                <div class="item" v-for="(item, index) in (defaultInfo.equipmentList || [])">
                  <div style="text-align: left;">{{ item.name }}</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status" :class="{ pending: !item.status3, 'success': item.status3 }">{{ item.status3 ? '已到达' : '前往中' }}</span>
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
              <div class="t4 text-c-error">{{ getNoReachStatus(3) ? '' : '所有设备已经到达作业区，请点击进入下一步！' }}:</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="getNoReachStatus(3) || currentStep !== 3" @click="updateStep(3)">下一步</el-button>
                <!-- <el-button tt="primary" class="ml10">跳过</el-button> -->
              </div>
            </div>
          </div>
          <div class="step4 mt10" :class="{ 'disabled': currentStep < 4 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">4</div>
                  <div class="ml10">
                    <div class="t2">车辆作业</div>
                    <div class="t3">车辆到达作业区，观察记录作业过程中的违规事件</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 4, pending: currentStep === 4, success: currentStep > 4}">
                  {{ currentStep < 4 ? '等待中' : currentStep === 4 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="t4">语音引导</div>
                <div class="flx-center mt10">
                  <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                  <div class="t4 ml10" style="line-height: 12px">{{ defaultEquipemnt.name }}</div>
                </div> 
              </div>
              <div class="flx-justify-between">
                <div class="notice p10 mt10 flx-center flex1">
                  <svg-icon icon-class="notice" style="font-size: 14px" />
                  <span class="ml10">请规范卸货，全程监控记录！</span>
                </div>
                <div class="buttons ml10">
                  <el-button tt="primary" :disabled="currentStep !== 4" @click="openStepBtn('请规范卸货，全程监控记录！', 'step4Disabled')">立即引导</el-button>
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
                <el-button tt="primary" :disabled="step4Disabled || currentStep !== 4" @click="updateStep(4)">下一步</el-button>
                <!-- <el-button tt="primary" class="ml10">跳过</el-button> -->
              </div>
            </div>
          </div>
          <div class="step5 mt10" :class="{ 'disabled': currentStep < 5 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">5</div>
                  <div class="ml10">
                    <div class="t2">车辆返程</div>
                    <div class="t3">车辆完成作业后返程区前往出口，观察记录返程过程中的违规事件</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 5, pending: currentStep === 5, success: currentStep > 5}">
                  {{ currentStep < 5 ? '等待中' : currentStep === 5 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="w60">
                  <div class="t4">司机申请返程信息</div>
                  <div class="mt10 flx-justify-between p10">
                    <img src="@/assets/images/bi/new/dog_l.png" alt="" width="92px" height="59px">
                    <div>
                      <div class="t4">接收设备名称：{{ defaultEquipemnt.name }}</div>
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
                <div class="btn-status" :class="{waiting: !step5Reply, success: step5Reply}">{{ step5Reply ? '已回复' : '待回复' }}</div>
              </div>
              <div class="mt10">
                <el-input type="textarea" :rows="4" placeholder="请输入回复信息" v-model="replyValue" />
              </div>
              <div class="flx-justify-between mt10">
                <span class="t4"></span>
                <div class="buttons" style="text-align: right;">
                  <el-button tt="primary" :disabled="step5Reply || !replyValue" @click="reply">点击回复</el-button>
                </div>
              </div>
              <div class="t4 mt10">设备控制</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>操作</div>
                </div>
                <div class="item" v-for="(item, index) in (defaultInfo.equipmentList || [])">
                  <div style="text-align: left;">{{ item.name }}</div>
                  <div style="text-align: center ;">
                    <div class="buttons" style="text-align: center;">
                      <el-button tt="primary" :disabled="!step5Reply" @click="executeStep(5)">立即引导返航</el-button>
                      <!-- <el-button tt="primary">自动伴飞跟随</el-button> -->
                    </div>
                  </div>
                </div>
              </div>
              <div class="flx-justify-between">
                <div class="t4 mt17">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：{{ pointInfo.end.pointName }}
                </div>
              </div>
              <div class="t5 mt10">设备位置状态信息</div>
              <div class="list mt10">
                <div class="item th">
                  <div style="text-align: left;">设备名称</div>
                  <div>距离目的地距离</div>
                  <div>位置状态</div>
                </div>
                <div class="item" v-for="(item, index) in (defaultInfo.equipmentList || [])">
                  <div style="text-align: left;">{{ item.name }}</div>
                  <div>10米</div>
                  <div style="text-align: center ;">
                    <span class="td-status" :class="{ pending: !item.status5, 'success': item.status5 }">{{ item.status5 ? '已到达' : '前往中' }}</span>
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
            </div>
            <div class="flx-justify-between mt10">
              <div class="t4 text-c-error">{{ getNoReachStatus(5) ? '' : '设备已经到达出口区，请点击进入下一步！' }}:</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="getNoReachStatus(5) || currentStep !== 5" @click="currentStep = 6">下一步</el-button>
              </div>
            </div>
          </div>
          <div class="step6 mt10" :class="{ 'disabled': currentStep < 6 }">
            <div class="box p10 pb20">
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
                  <div class="btn-status" :class="{ waiting: currentStep < 6, pending: currentStep === 6, success: currentStep > 6}">
                    {{ currentStep < 6 ? '等待中' : currentStep === 6 ? '进行中' : '已完成' }}
                  </div>
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
                  <el-button tt="primary" :disabled="currentStep !== 6" @click="openStepBtn('请司机检查是否有物品遗漏后离场！', 'step6Disabled')">立即引导</el-button>
                </div>
              </div>
              <!-- <div class="mt10 flx-justify-between">
                <span class="t4">车辆是否已经离场？ </span>
                <div class="buttons">
                  <el-button tt="primary">确认车辆离场</el-button>
                </div>
              </div> -->
            </div>
            <div class="mt10 flx-justify-between">
              <span class="t4">点击结束任务，所有设备自动返航，系统自动生成处置记录</span>
              <div class="buttons">
                <el-button tt="primary" :disabled="step6Disabled" @click="executeStep(6)">结束任务</el-button>
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
import { controlVoiceStatus, executeTaskByStep, playVoice, updateCar, verifyVehicle } from '../../../../api/bi';
import CljgRight from './right/index.vue'
export default {
  name: 'xlxc-detail',
  computed: {
    // 语音系统是否连接
    voiceConnect() {
      return this.$store.getters['voiceCall/getConnected'];
    },
    // 语音websocket
    voiceWebSocket() {
      return this.$store.getters['voiceCall/getVoiceWebSocket'];
    },
    routeDetails() {
      return this.$store.getters['extra/getRouteDetails'];
    },
    //获取基础信息
    vehicleMessage() {
      return this.$store.getters['websocket/getVehicle'];
    },
  },
  components: { CljgRight },
  data() {
    return {
      currentStep: 1,
      defaultInfo: { equipmentList: [] },
      pointInfo: {
        rw1: {},
        rw2: {},
        end: {}
      },
      equipmentId: '',
      defaultEquipemnt: {},
      verifyInfo: {
        driver: {
          success: null,
          url: ''
        },
        licensePlate: {
          success: null,
          url: ''
        },
      },
      step2Disabled: true,
      step4Disabled: true,
      replyValue: '中心已收到，车辆检查合格，请关闭车厢门，跟随机器狗引导返程',
      step5Reply: false,
      step6Disabled: false,
      isTalk: false,
    }
  },
  mounted() {
    // this.defaultInfo = this.routeDetails.row || { equipmentList: [] }
    // this.currentStep = this.defaultInfo.step ? Number(this.defaultInfo.step) : 1
    // if (this.defaultInfo.equipmentList.length) {
    //   this.defaultEquipemnt = this.defaultInfo.equipmentList[0]
    //   this.equipmentId = this.defaultInfo.equipmentList[0].id
    //   this.defaultInfo.bindPaths.points.map(item => {
    //     // 充电点
    //       if (item.qhMotionEquipmentConfig.pointInfo === 3) {
    //         this.pointInfo.end = { pointName: item.pointName }
    //       } else if (item.qhMotionEquipmentConfig.pointInfo === 1) {
    //         // 任务点
    //         if (this.pointInfo.rw1.pointName) {
    //           this.pointInfo.rw2 = { pointName: item.pointName }
    //           return
    //         }
    //         this.pointInfo.rw1 = { pointName: item.pointName }
    //       }
    //   })
    // }
    // console.log('mounted', this.routeDetails);
    
    this.getData(this.routeDetails.row)

    // setTimeout(() => {
    //   console.log('执行了');
      
    //   this.$store.dispatch('websocket/setVehicle', { type: 'arrived', success: true })
      
    // }, 5000);
  },
  watch: {
    routeDetails: {
      handler(newVal) {
        // console.log('newVal', newVal);
        
        // this.defaultInfo = this.routeDetails.row || { equipmentList: [] }
        this.getData(newVal.row)
      }
    },
    // 获取基础信息
    vehicleMessage: {
      handler(newVal) {
      // console.log('改变了', newVal, this.defaultInfo.equipmentList);
        if (newVal && newVal.type) {
          const { type, success, msg, data } = newVal
          // "pathId": 123, "url": "xxx"
          // ['detect_driver', 'detect_license_plate', 'arrived']
          switch (type) {
            case 'arrived':
              this.defaultInfo.equipmentList.map((item, index) => {
                this.$set(this.defaultInfo.equipmentList[index], `status${this.currentStep}`, success)
                if (this.currentStep === 6 && success) {
                  this.currentStep = 7
                }
                if (this.currentStep === 3 && success) {
                  this.handleTalk()
                }
                if (this.currentStep === 5 && success) {
                  this.handleTalk()
                }
              })
              break
            case 'detect_driver':
              this.verifyInfo.driver = {
                success,
                url: success ? this.getImg(data?.url || '') : ''
              }
              break
            case 'detect_license_plate':
              this.verifyInfo.licensePlate = {
                success,
                url: success ? this.getImg(data?.url || '') : ''
              }
              break
          }
        }
      },
      immediate: true
    }
  },
  methods: {
    getData(newVal) {
      this.defaultInfo = newVal || { equipmentList: [] }
      this.currentStep = this.defaultInfo.step ? (Number(this.defaultInfo.step) + 1) : 1
      if (this.defaultInfo.equipmentList.length) {
        this.defaultEquipemnt = this.defaultInfo.equipmentList[0]
        this.equipmentId = this.defaultInfo.equipmentList[0].id
        this.defaultInfo.bindPaths.points.map(item => {
          // 充电点
            if (item.qhMotionEquipmentConfig.pointInfo === 3) {
              this.pointInfo.end = { pointName: item.pointName }
            } else if (item.qhMotionEquipmentConfig.pointInfo === 1) {
              // 任务点
              if (this.pointInfo.rw1.pointName) {
                this.pointInfo.rw2 = { pointName: item.pointName }
                return
              }
              this.pointInfo.rw1 = { pointName: item.pointName }
            }
        })
      }
    },
    // 到达状态
    getNoReachStatus(step) {
      return this.defaultInfo.equipmentList.filter(item => item[`status${step}`]).length !== this.defaultInfo.equipmentList.length
    },
    getImg(url) {
      return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${url}`
      // return `http://192.168.124.204:8181/file/${url}`
    },
    // 身份核验
    verify() {
      verifyVehicle({equipmentId: this.equipmentId})
    },
    // 发送语音
    sendVoice(text) {
      playVoice({ text, audioNum: 2, equipmentEndpoint: this.defaultInfo.equipmentList[0].endpoint})
    },
    openStepBtn(text, disabledKey) {
      this.sendVoice(text)
      this[disabledKey] = false
    },
    reply() {
      this.sendVoice(this.replyValue)
      this.step5Reply = true
    },
    executeStep(step) {
      const obj = JSON.parse(JSON.stringify(this.defaultInfo))
      delete obj.equipmentList
      delete obj.bindPaths
      obj.step = step
      executeTaskByStep(obj).then(res => {
        // if (res.code === 200) {
        //   this.$modal.msgSuccess(res.msg);
        // } else {
        //   this.$modal.msgSuccess(res.msg);
        // }
      });
      if (step !== 5) {
        this.currentStep = step + 1
      }
    },
    updateStep(step) {
      const formData = new FormData();
      // Object.keys(this.defaultInfo).map(key => {
      //   if(this.defaultInfo[key] === null || key === 'step' || ['equipmentList', 'bindPaths'].includes(key)) return
      //   formData.append(key, this.defaultInfo[key])
      // })
      formData.append('id', this.defaultInfo.id)
      formData.append('step', step.toString())
      updateCar(formData).then(res => {
        if (res.code === 200) {
          // this.$modal.msgSuccess(res.msg);
          // this.$emit('refresh')
          // this.dialogVisible = false
          // 打开详情页面
          console.log('更新步骤', this.currentStep, res.msg);
          
        } else {
          // this.$modal.msgSuccess(res.msg);
          console.error('更新步骤', this.currentStep, res.msg);
        }
      });
      this.currentStep = step + 1
    },


    // 麦克风
    // 语音、云台
    async handleTalk() {
      // if (this.isDisabled) {
      //   this.$message.warning('请勿重复点击！间隔3s后可再次点击。');
      //   return;
      // }
      this.isTalk = !this.isTalk;
      // this.isDisabled = true;
      setTimeout(() => {
        // this.isDisabled = false;
        if (!this.voiceConnect) {
          this.isTalk = false
          this.$message.error('语音设备连接失败')
        }
      }, 3000);

      if (this.voiceConnect) {
        if (this.isTalk) {
          const res = await controlVoiceStatus('on', this.defaultEquipemnt.endpoint)
           console.log('%c打开语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.startRecording();
          this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
        } else {
          const res = await controlVoiceStatus('off', this.defaultEquipemnt.endpoint)
           console.log('%c关闭语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.stopRecording();
          this.voiceWebSocket.sendMessage({ type: 'stopTalk', clientId: this.voiceWebSocket.clientId });
        }
      }
    },
    async startRecording() {
      try {
        await this.voiceWebSocket.startRecording(this.defaultEquipemnt.id);
        console.log('A: 语音对讲已开启');
      } catch (error) {
        console.error('开启语音对讲失败:', error);
        this.$message.error('无法开启语音对讲，请检查麦克风权限');
        this.isTalk = false;
      }
    },
    async stopRecording() {
      try {
        this.voiceWebSocket.stopRecording(this.defaultEquipemnt.id);
        console.log('A: 语音对讲已关闭');
      } catch (error) {
        console.error('关闭语音对讲失败:', error);
        this.$message.error('关闭语音对讲失败');
      }
    },
  }
}
</script>

<style scoped lang="scss">
@import './index.scss'
</style>