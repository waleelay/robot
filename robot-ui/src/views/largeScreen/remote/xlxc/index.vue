<template>
  <div class="info-content flx-justify-between h100 xlxc">
    <div style="display: block" class="side-box d-flex flex-column left d-flex pb30">
      <div class="card xlxcqk h100 ovya">
        <div class="title flx-justify-between">
          <div class="text">预案流程</div>
          <div class="buttons">
            <el-button tt="primary">上一步</el-button>
            <el-button tt="primary" class="ml10">下一步</el-button>
          </div>
        </div>
        <div class="content mt10 p10 ">
          <div class="step1">
            <div class="box p10 pt12 pl12">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">1</div>
                  <div class="ml10">
                    <div class="t2">调度处置设备</div>
                    <div class="t3">处置应急事件时调度空闲设备前往现场进行配合民警进行处置</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ pending: currentStep === 1 , success: currentStep > 1}">
                  {{ currentStep === 1 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="t4 mt17">选择设备:</div>
              <div class="container-bg p20 pt20 pb20 mt10 ovya" style="height: 92px">
                <el-checkbox-group v-model="checkList" style="margin-top: -10px; margin-left: -30px">
                  <el-checkbox label="复选框 A" disabled></el-checkbox>
                  <el-checkbox label="复选框 B"></el-checkbox>
                  <el-checkbox label="复选框 C"></el-checkbox>
                  <el-checkbox label="复选框 D"></el-checkbox>
                  <el-checkbox label="复选框 E"></el-checkbox>
                  <el-checkbox label="复选框 F"></el-checkbox>
                  <el-checkbox label="复选框 G"></el-checkbox>
                  <el-checkbox label="复选框 H"></el-checkbox>
                  <el-checkbox label="复选框 I"></el-checkbox>
                  <el-checkbox label="复选框 J"></el-checkbox>
                  <el-checkbox label="禁用" disabled></el-checkbox>
                  <el-checkbox label="选中且禁用" disabled></el-checkbox>
                </el-checkbox-group>
              </div>
              <div class="flx-justify-between mt10">
                <div class="t4">
                  <svg-icon icon-class="address" style="font-size: 14px; color: #0BF9FE" class="mr10" />
                  前往目的地：AAAAAAAAAAAAA车间大门
                </div>
                <div class="buttons">
                  <el-button tt="primary">一键调度</el-button>
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
            </div>
            <div class="flx-justify-between mt10">
              <div class="t4 text-c-error">{{ getNoReachStatus(1) ? '' : '所有设备已到位，请进入下一步！' }}</div>
              <div class="buttons">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 0">下一步</el-button>
                <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 0">跳过</el-button>
              </div>
            </div>
          </div>
          <div class="step2 mt10" :class="{ 'disabled': currentStep < 2 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">2</div>
                  <div class="ml10">
                    <div class="t2">通知值班民警 </div>
                    <div class="t3">通知值班民警前往现场配合设备进行处置</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 2, pending: currentStep === 2, success: currentStep > 2}">
                  {{ currentStep < 2 ? '等待中' : currentStep === 2 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt14">
                <img src="" alt="" width="110px" height="136px" class="mr10">
                <div class="info">
                  <div class="mt10 person">
                    <span>值班民警：张俊</span>
                    <span class="ml20">联系电话：15297841206</span>
                  </div>
                  <div class="desc p10 mt20">
                    车间A-装配线3号工位发生疑似打架斗殴事件，请值班民警立即前往处理。现场已派遣机器狗进行监控，请携带必要装备，注意安全。
                  </div>
                  <div class="buttons mt20">
                    <el-button tt="primary" :disabled="btnDisabledIndex <= 1">自动下发通知</el-button>
                    <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 1">已确认通知</el-button>
                  </div>
                </div>
              </div>
            </div>
            <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary" :disabled="btnDisabledIndex <= 1">下一步</el-button> 
            </div>
          </div>
          <div class="step3 mt10" :class="{ 'disabled': currentStep < 3 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">3</div>
                  <div class="ml10">
                    <div class="t2">抵近威慑 </div>
                    <div class="t3">在民警到达前，可选择远程操控设备对现场人员进行语音威慑</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 3, pending: currentStep === 3, success: currentStep > 3}">
                  {{ currentStep < 3 ? '等待中' : currentStep === 3 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="t4">威慑语音</div>
                <div class="flx-center mt10">
                  <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                  <div class="t4 ml10" style="line-height: 12px">机器狗A</div>
                </div> 
              </div>
              <div class="notice p10 mt10 flx-center">
                <svg-icon icon-class="notice" style="font-size: 14px" />
                <span class="ml10">警告！立即停止斗殴，否则采取物理干预！</span>
              </div>
              <div class="buttons mt10" style="text-align: right">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 2">立即播报</el-button>
                <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 2">停止播报</el-button>
              </div>
            </div>
            <div class="t4 mt10">设备控制</div>
            <div class="notice p10 mt10 flx-center">
              <svg-icon icon-class="info" style="font-size: 14px" />
              <span class="ml10">提示：请通过右侧控制台控制机器狗靠近人员进行语音威慑！</span>
            </div>
            <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary" :disabled="btnDisabledIndex <= 2">下一步</el-button>
              <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 2">跳过</el-button>
            </div>
          </div>
          <div class="step4 mt10" :class="{ 'disabled': currentStep < 4 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">4</div>
                  <div class="ml10">
                    <div class="t2">人群驱散</div>
                    <div class="t3">在民警前往前，若语音威慑无效，可选择远程操控设备对现场人员进行物理干预</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 4, pending: currentStep === 4, success: currentStep > 4}">
                  {{ currentStep < 4 ? '等待中' : currentStep === 4 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="t4">威慑语音</div>
                <div class="flx-center mt10">
                  <img src="@/assets/images/bi/new/dog.png" alt="" width="20px" height="13px">
                  <div class="t4 ml10" style="line-height: 12px">机器狗A</div>
                </div> 
              </div>
              <div class="notice p10 mt10 flx-center">
                <svg-icon icon-class="notice" style="font-size: 14px" />
                <span class="ml10">即将采取物理干预，无关人员撤离至3米外！</span>
              </div>
              <div class="buttons mt10" style="text-align: right;">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 3">立即播报</el-button>
                <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 3">停止播报</el-button>
              </div>
              <div class="t4 mt10">设备控制</div>
              <div class="notice p10 mt10 flx-center">
                <svg-icon icon-class="info" style="font-size: 14px" />
                <span class="ml10">提示：请通过右侧控制台控制机器狗逼近人员，注意控制行驶速度到最低！</span>
              </div>
            </div>
            <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary" :disabled="btnDisabledIndex <= 3">下一步</el-button>
              <el-button tt="primary" class="ml10">跳过</el-button>
            </div>
          </div>
          <div class="step5 mt10" :class="{ 'disabled': currentStep < 5 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">5</div>
                  <div class="ml10">
                    <div class="t2">民警现场处置</div>
                    <div class="t3">当民警到达现场后,可通过机器狗配合民警进行执法记录,并通过无人机视频为现场提供情报信息</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 5, pending: currentStep === 5, success: currentStep > 5}">
                  {{ currentStep < 5 ? '等待中' : currentStep === 5 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="t4">民警到达现场</div>
              </div>
              <div class="notice p10 mt10 flx-center pr70 pl70">
                <span class="ml10">提示：请通过右侧直播画面确认民警是否到达现场</span>
              </div>
              <div class="buttons mt10" style="text-align: right;">
                <el-button tt="primary" :disabled="btnDisabledIndex <= 4">确认到达</el-button>
              </div>
              <div class="t4 mt10">设备控制</div>
              <div class="notice p10 mt10 flx-center pr70 pl70">
                <span class="ml10">请通过右侧控制台控制机器狗跟拍民警执法过程，并控制观察无人机画面为现场提供情报信息</span>
              </div>
            </div>
            <div class="buttons mt10" style="text-align: right;">
              <el-button tt="primary" :disabled="btnDisabledIndex <= 4">下一步</el-button>
            </div>
          </div>
          <div class="step6 mt10" :class="{ 'disabled': currentStep < 6 }">
            <div class="box p10 pb20">
              <div class="flx-justify-between">
                <div class="flx-align-center">
                  <div class="index">6</div>
                  <div class="ml10">
                    <div class="t2">处置完成</div>
                    <div class="t3">当民警到达现场后,当民警完成处置后，向中心报告处置结果，中心确认后完成处置,并通过无人机视频为现场提供情报信息</div>
                  </div>
                </div>
                <div class="btn-status" :class="{ waiting: currentStep < 6, pending: currentStep === 6, success: currentStep > 6}">
                  {{ currentStep < 6 ? '等待中' : currentStep === 6 ? '进行中' : '已完成' }}
                </div>
              </div>
              <div class="title-line1 mt10"></div>
              <div class="flx-justify-between mt10">
                <div class="w60">
                  <div class="t4">民警处置信息</div>
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
                  <el-input type="textarea" :rows="4" class="mt10" placeholder="机器狗.已记录在A车间发现打架斗殴" disabled />
                </div>
              </div>
              <div class="flx-justify-between mt10">
                <div class="t4">中心语音回复</div>
                <div class="btn-status pending">待回复</div>
                <!-- <div class="btn-status success">已回复</div> -->
              </div>
              <div class="mt10">
                <el-input type="textarea" :rows="4" placeholder="中心已收到，确认处置完成" />
              </div>
              <div class="flx-justify-between mt10">
                <span class="t4">XXXXXXXXXXXXXXXXXXX</span>
                <div class="buttons" style="text-align: right;">
                  <el-button tt="primary" :disabled="btnDisabledIndex <= 5">点击回复</el-button>
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
                      <el-button tt="primary" :disabled="btnDisabledIndex <= 5">一键返航</el-button>
                      <el-button tt="primary" class="ml10" :disabled="btnDisabledIndex <= 5">继续任务</el-button>
                    </div>
                  </div>
                </div>
              </div>
              <div class="mt10">
                <el-input type="textarea" :rows="5" placeholder="处置总结" />
              </div>
              <div class="mt10 flx-justify-between">
                <span class="t4">处置完成系统自动记录生成处置信息</span>
                <div class="buttons">
                  <el-button tt="primary" :disabled="btnDisabledIndex <= 5">处置完成</el-button>
                </div>
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
import { playVoice } from '../../../../api/bi';
import { listAllMotion } from '../../../../api/rsp/equipment';
import CljgRight from './../cljg/right/index.vue'

export default {
  name: 'xlxc-detail',
  components: { CljgRight },
  data() {
    return {
      checkList: ['复选框 A'],
      selectedValue: '',
      dividedType: 1,
      options: [
        { value: '选项1', label: '黄金糕', url: '' },
        { value: '选项2', label: '双皮奶', url: '' },
        { value: '选项3', label: '蚵仔煎', url: '' },
        { value: '选项4', label: '龙须面', url: '' },
        { value: '选项5',label: '北京烤鸭', url: '' },
        { value: '选项15',label: '北京烤鸭', url: '' },
        { value: '选项52',label: '北京烤鸭', url: '' }
      ],
      currentStep: 7,
      step: 0,
      btnDisabledIndex: 0,
      equipmentList: [],
      defaultInfo: { }
    }
  },
  methods: {
    /** 查询装备列表 */
    getMotionList() {
      this.loading = true;
      listAllMotion({ stateIn: '1,2,3' }).then(response => {
        this.equipmentList = response.data;
      });
    },
    handleChangeSelect(name) {
      // this.$refs.carouselRef.setActiveItem(name)
    },
    // 到达状态
    getNoReachStatus(step) {
      return this.equipmentList.filter(item => item[`status${step}`]).length !== this.equipmentList.length
    },
    getImg(url) {
      return `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${url}`
      // return `http://192.168.124.204:8181/file/${url}`
    },
    // 发送语音
    sendVoice(text) {
      playVoice({ text, audioNum: 2, equipmentEndpoint: this.equipmentList[0].endpoint})
    },
    next() {

    }
  }
}
</script>

<style scoped lang="scss">
@import './index.scss'
</style>