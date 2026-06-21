<template>
  <!-- width="353px"
  height="410px" -->
  <el-dialog
    class="custom-dialog__wrapper robot-dialog flx-align-center"
    :visible.sync="dialogVisible"
    :modal-append-to-body="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    append-to-body
    title="异常报告"
  >
    <template slot="footer"></template>
    <div class="flex">
      <!-- 快照 -->
      <div class="custom-modal-container snapshot-container" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">抓拍详情</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt20 pr20 pb20 pl20 flex">
            <div class="wp640">
              <div class="title">抓拍画面</div>
              <div class="img mt10 wp640 hp360 mt10">

              </div>
            </div>
            <div class="ml20 flex1">
              <div class="title">抓拍信息</div>
              <div class="info p20 mt10">
                <div class="item time">
                  <div class="flx-align-center label">
                    <svg-icon icon-class="time" />
                    <span class="ml10">抓拍时间</span>
                  </div>
                  <div class="value mt10 pb10">2023-08-10 15:30:00</div>
                </div>
                <div class="item time">
                  <div class="flx-align-center label">
                    <svg-icon icon-class="robot" />
                    <span class="ml10">来源设备</span>
                  </div>
                  <div class="value mt10 pb10">地面四足机器人</div>
                </div>
                <div class="item time">
                  <div class="flx-align-center label">
                    <svg-icon icon-class="address" />
                    <span class="ml10">发生位置</span>
                  </div>
                  <div class="value mt10 pb10">A区-主干道</div>
                </div>
                <div class="item time">
                  <div class="flx-align-center label">
                    <svg-icon icon-class="address" />
                    <span class="ml10">标签类型</span>
                  </div>
                  <div class="mt6">
                    <el-select placeholder="请选择类型" clearable style="width: 100%" class="snapshot-select" popper-class="custom-select-dropdown">
                      <el-option label="业务告警" value="0" />
                      <el-option label="任务告警" value="1" />
                      <el-option label="设备告警" value="2" />
                    </el-select>
                  </div>
                </div>
                <div class="operation mt17 flx-center">
                  <el-button type="primary" class="flex1 flx-center">下载原图</el-button>
                  <el-button type="primary" class="flex1 flx-center">保存</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- 快照--选择类型 -->
      <div class="custom-modal-container snapshot-container1" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">快速抓拍</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt20 pr20 pb30 pl28 flex">
            <div class="type-div">
              <div class="second-title">告警类型</div>
              <div class="mt10 flex">
                <div class="flx-center type is-active">
                  <span class="symbol flx-center">
                    <svg-icon icon-class="shopping"></svg-icon>
                  </span>
                  <span class="ml10">任务告警</span>
                </div>
                <div class="flx-center type">
                  <span class="symbol flx-center">
                    <svg-icon icon-class="shopping"></svg-icon>
                  </span>
                  <span class="ml10">任务告警</span>
                </div>
                <div class="flx-center type">
                  <span class="symbol flx-center">
                    <svg-icon icon-class="shopping"></svg-icon>
                  </span>
                  <span class="ml10">任务告警</span>
                </div>
                <div class="flx-center type">
                  <span class="symbol flx-center">
                    <svg-icon icon-class="shopping"></svg-icon>
                  </span>
                  <span class="ml10">任务告警</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <!-- 机器狗控制 -->
      <div class="custom-modal-container robot-control-container" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
          <div class="flx-align-center">
            <div class="title ml10">监区巡逻机器狗-01</div>
            <div class="status success ml10">空闲中</div>
          </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt10 pr20 pb20 pl20 flex" style="align-items: flex-start">
            <div class="wp480 hp270 video">
            </div>
            <div class="flex1 ml28 control flex">
              <div class="w50">
                <div class="custom-tab-button flex">
                  <div v-for="item in controlInfo.tabList" :key="item.value" class="tab-button-item pr10 pl10" :class="{ 'is-active': controlInfo.tabIndex === item.value }" @click="controlInfo.tabIndex = item.value" style="font-size: 14px; line-height: 19px">{{ item.label }}</div>
                </div>
                <div class="mt28 ml28">
                  <img src="@/assets/images/new-bi/dog.png" alt="" srcset="" width="148" height="95">
                  <div class="mt26" style="color: #FFF;font-family: 'Microsoft YaHei'; font-size: 12px; line-height: 16px;">任务名称：A区-夜间巡逻</div>
                  <div class="mt12" style="color: #FFF;font-family: 'Microsoft YaHei'; font-size: 12px; line-height: 16px;">任务时间：20:00-22:00</div>
                  <div class="mt12" style="color: #FFF;font-family: 'Microsoft YaHei'; font-size: 12px; line-height: 16px;">任务类型：status success</div>
                </div>
              </div>
              <div class="w50 pt20 flex-column robot-control" style="justify-content: center">
                <Control />
              </div>
            </div>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
      <!-- 添加任务 -->
      <div class="custom-modal-container add-task-container" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="title ml10">添加新任务</div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt10 pr20 pb20 pl20 flx-justify-between">
            <div class="flex1 task">
              <div class="second-title">选择预设任务</div>
              <div class="mt10 pr4 list">
                <div v-for="item in taskInfo.taskList" :key="item.name" class="item p20 flx-justify-between" style="align-items: flex-start;" :class="{ selected: taskInfo.selectedTaskRows.includes(item) }" @click="handleClickTaskRow(item)">
                  <div>
                    <div class="name">{{ item.name }}</div>
                    <div class="flx-center mt10">
                      <div> 
                        <svg-icon icon-class="time"></svg-icon>
                        <span class="ml10">{{ item.time }}</span>
                      </div>
                      <div class="ml10">
                        <svg-icon icon-class="address"></svg-icon>
                        <span class="ml10">{{ item.address }}</span>
                      </div>
                    </div>
                  </div>
                  <div :class="'p4 status ' + (item.status === 0 ? 'primary' : 'purple')">{{ item.status === 0 ? '单设备任务' : '多设备任务' }}</div>
                </div>
              </div>
            </div>
            <div class="flex1 robot">
              <div class="flx-justify-between">
                <div class="second-title">执行装备列表</div>
                <div class="selected-info flx-align-center">
                  <template v-if="taskInfo.selectedRobotRows.length !== taskInfo.count">
                    <svg-icon icon-class="info"></svg-icon>
                    <span class="ml6">当前{{ taskInfo.selectedRobotRows.length }}/{{ taskInfo.count }}台</span>
                  </template>
                  <template v-else>
                    <svg-icon icon-class="success-l" style="color: #0BF9FE;"></svg-icon>
                    <span class="ml6" style="color: #0BF9FE;">准备就绪</span>
                  </template>
                </div>
              </div>
              <div class="mt10" style="position: relative;">
                <div class="pb82 list p20" style="padding-right: 16px !important;">
                  <div v-for="(item, index) in taskInfo.robotList" :key="item.name">
                    <div class="item pt9 pr10 pb9 pl10 flx-justify-between" :class="{ selected: taskInfo.selectedRobotRows.includes(item) || (index === 0 && taskInfo.fromMain) }" @click="handleClickRobotRow(item, index)">
                      <div class="flx-align-center">
                        <span class="p4 flx-center symbol">
                          <svg-icon icon-class="robot"></svg-icon>
                        </span>
                        <div class="ml10">
                          <div class="name">{{ item.name }}</div>
                          <div class="flx-align-center mt4"> 
                            <svg-icon
                              :icon-class="item.battery >= 90 ? 'battery-4' : item.battery >= 80 ? 'battery-3' : item.battery >= 50 ? 'battery-2' : item.battery >= 40 ? 'battery-1' : 'battery-0'"
                              :style="{ color: item.battery < 50 ? '#D33333' : '#3DB56A' }"
                            >
                            </svg-icon>
                            <span class="ml4">{{ item.battery }}%</span>
                          </div>
                        </div>
                      </div>
                      <div class="selected-symbol">
                        <svg-icon icon-class="success"></svg-icon>
                      </div>
                    </div>
                    <div v-if="index === 0 && taskInfo.fromMain">
                      <div class="w100 hp1 mt10 mb10" style="background: #1B2839;"></div>
                      <div style="color: #D0DEEE; font-size: 14px; line-height: 19px">选择空闲装备</div>
                    </div>
                  </div>
                </div>
                <div class="btns flx-center">
                  <el-button v-if="taskInfo.selectedRobotRows.length !== taskInfo.count" type="primary" :disabled="true" class="w100 flx-center is-disabled">还需选择{{ taskInfo.count - taskInfo.selectedRobotRows.length }}台设备</el-button>
                  <el-button v-else type="primary" class="w100 flx-center">立即执行任务</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
      <!-- 无人机信息 -->
      <div class="custom-modal-container uav-container" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">无人机机场</div>
              <div class="status ml10">正常</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt20 pr10 pb20 pl10 flex flex-wrap">
            <div class="flx-justify-between task-info w100 hp66 pr10 pl10">
              <div class="item h100 flx-center flex-column">
                <div>
                  <span class="count">128</span>
                  <span class="unit"> 个</span>
                </div>
                <div class="title mt6">待执行任务</div>
              </div>
              <div class="item h100 flx-center flex-column">
                <div>
                  <span class="count">888</span>
                  <span class="unit"> 个</span>
                </div>
                <div class="title mt6">已完成任务</div>
              </div>
              <div class="item h100 flx-center flex-column">
                <div>
                  <span class="count">2</span>
                  <span class="unit"> 个</span>
                </div>
                <div class="title mt6">异常任务</div>
              </div>
            </div>
            <div class="list mt20">
              <div class="w100 flx-align-center flex title">
                <div class="flex1">无人机名称</div>
                <div class="wp47 ml12">状态</div>
                <div class="wp40 ml32 mr20">电量</div>
                <div class="flex1 ml12">无人机位置</div>
                <div class="flex1 ml12">任务状况</div>
              </div>
              <div class="content w100 flx-align-center flex pt6 pb6">
                <div v-for="item in uavInfo.uavList" :key="item.name" class="item hp36 w100 flx-align-center flex">
                  <div class="name flex1 text-ellipsis" :title="item.name">{{item.name}}</div>
                  <div class="status wp47 ml12">{{item.status}}</div>
                  <div class="battery wp40 ml32 mr20">{{item.battery}}</div>
                  <div class="address flex1 ml12 text-ellipsis" :title="item.address">{{ item.address }}</div>
                  <div class="task-status flex1 ml12 text-ellipsis" :title="item.taskStatus">{{ item.taskStatus }}</div>
                </div>
              </div>
            </div>
          </div>
          <div class="btns m10 mb20 mt6 flx-align-center" style="margin-top: 0 !important;">
            <el-button type="primary" class="flex1">设为优先返航点</el-button>
            <el-button type="primary" class="flex1 error">紧急召回</el-button>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
      <!-- 充电桩信息 -->
      <div class="custom-modal-container battery-container" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">智能充电站01</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <div class="info-content pt20 pr6 pl6 pb20">
            <div class="item">
              <div class="label flx-align-center">
                <svg-icon icon-class="verify"></svg-icon>
                <span class="ml6">充电站状态</span>
              </div>
              <div class="value with-border mt10 status success">正常运行</div>
            </div>
            <div class="item mt20">
              <div class="label flx-align-center">
                <svg-icon icon-class="robot"></svg-icon>
                <span class="ml6">正在充电设备</span>
              </div>
              <div class="value with-border mt10" style="color: #fff;">监区机器人-01</div>
            </div>
            <div class="item mt20">
              <div class="label flx-justify-between">
                <div class="flx-align-center">
                  <svg-icon icon-class="battery-charge"></svg-icon>
                  <span class="ml6">充电情况</span>
                </div>
                <span>60%</span>
              </div>
              <div class="value mt10 with-border progress hp10 p0" :style="{'--rate': 60 + '%'}"></div>
            </div>
            <div class="item mt20">
              <div class="label flx-align-center">
                <svg-icon icon-class="time"></svg-icon>
                <span class="ml6">预计充满时间</span>
              </div>
              <div class="value with-border mt10">2小时</div>
            </div>
          </div>
          <div class="btns m10 mb20 flx-align-center" style="margin-top: 0 !important;">
            <el-button type="primary" class="w100">设为优先返航点</el-button>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
      <!-- 机器狗信息 -->
      <div class="custom-modal-container robot-container" style="display: block;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">监区点名机器人</div>
              <div class="status ml10">空闲中</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <img src="@/assets/images/bi/bg.png" alt="" class="p10 w100" height="190" style="padding-bottom: 0 !important;">
          <div class="info-content pr10 pl10 flex flex-wrap">
            <div class="item wp154 mt10">装备类型：四足机器人</div>
            <div class="item wp151 ml26 mt10">当前电量：98%</div>
            <div class="item wp154 mt10">装备型号：绝影X30PRO</div>
            <div class="item wp151 ml26 mt10">是否告警：否</div>
            <div class="item wp154 mt10">控制模型：自动控制</div>
            <div class="item wp151 ml26 mt10">上装设备：3台</div>
            <div class="item wp154 mt10">当前速度：1.5m/s</div>
            <div class="item wp151 ml26 mt10 flx-align-center">
              当前状态：
              <span class="status">正常</span>
            </div>
          </div>
          <div class="btns m10 mb20 flx-align-center">
            <el-button type="primary">远程控制</el-button>
            <el-button type="primary">一键返航</el-button>
            <el-button type="primary">退出充电桩</el-button>
            <el-button type="primary">添加任务</el-button>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
      <!-- 异常信息 -->
      <div class="custom-modal-container robot-container error" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">监区点名机器人</div>
              <div class="status ml10">空闲中</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <img src="@/assets/images/bi/bg.png" alt="" class="p10 w100" height="190" style="padding-bottom: 0 !important;">
          <div class="info-content pr10 pl10 flex flex-wrap">
            <div class="item wp154 mt10">装备类型：四足机器人</div>
            <div class="item wp151 ml26 mt10">当前电量：98%</div>
            <div class="item wp154 mt10">装备型号：绝影X30PRO</div>
            <div class="item wp151 ml26 mt10">是否告警：否</div>
            <div class="item wp154 mt10">控制模型：自动控制</div>
            <div class="item wp151 ml26 mt10">上装设备：3台</div>
            <div class="item wp154 mt10">当前速度：1.5m/s</div>
            <div class="item wp151 ml26 mt10 flx-align-center">
              当前状态：
              <span class="status">正常</span>
            </div>
            <div class="w100 mt30 desc p10">哈哈哈哈哈哈</div>
          </div>
          <div class="btns m10 mt20 mb20 flx-align-center">
            <el-button type="primary" class="w100 error">强制返航</el-button>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
      <!-- 离线信息 -->
      <div class="custom-modal-container robot-container off" style="display: none;">
        <div class="decoration wp167 hp5">
          <svg-icon icon-class="decoration" class="w100 h100"></svg-icon>
        </div>
        <div class="box">
          <div class="top m4 flx-justify-between">
            <div class="flx-align-center">
              <div class="title ml10">监区点名机器人</div>
              <div class="status ml10">空闲中</div>
            </div>
            <div class="close mr10" @click="dialogVisible = false">
              <svg-icon icon-class="close"></svg-icon>
            </div>
          </div>
          <img src="@/assets/images/bi/bg.png" alt="" class="p10 w100" height="190" style="padding-bottom: 0 !important;">
          <div class="info-content pr10 pl10 flex flex-wrap">
            <div class="item wp154 mt10">装备类型：四足机器人</div>
            <div class="item wp151 ml26 mt10">当前电量：98%</div>
            <div class="item wp154 mt10">装备型号：绝影X30PRO</div>
            <div class="item wp151 ml26 mt10">是否告警：否</div>
            <div class="item wp154 mt10">控制模型：自动控制</div>
            <div class="item wp151 ml26 mt10">上装设备：3台</div>
            <div class="item wp154 mt10">当前速度：1.5m/s</div>
            <div class="item wp151 ml26 mt10 flx-align-center">
              当前状态：
              <span class="status">正常</span>
            </div>
            <div class="w100 mt30 desc p10">哈哈哈哈哈哈</div>
          </div>
          <div class="btns m10 mt20 mb20 flx-align-center">
            <el-button type="primary" class="w100 off">派人回收</el-button>
          </div>
        </div>
        <div class="guideline wp157 hp29 mt9 ml161">
          <svg-icon icon-class="guideline" class="w100 h100" style="vertical-align: top;"></svg-icon>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import Control from './Control.vue';
export default {
  name: 'Modal',
  components: { Control },
  data() {
    return {
      dialogVisible: true,
      uavInfo : {
        uavList: [
          {
            name: '无人机1',
            status: '充电中',
            battery: '98%',
            address: '123456',
            taskStatus: '任务中',
          },
          {
            name: '无人机2',
            status: '外出中',
            battery: '80%',
            address: '654321',
            taskStatus: '任务中',
          },
          {
            name: '空闲中',
            status: '正常',
            battery: '90%',
            address: '112233',
            taskStatus: '任务中',
          },
        ]
      },
      taskInfo: {
        taskList: [
          {
            name: '全域核心区域安全巡检',
            time: '14:30（预计45分钟）',
            address: 'A区产业园北侧',
            status: 0,
          },
          {
            name: '监狱上空A区多维度监控',
            time: '14:30（预计45分钟）',
            address: 'A区产业园北侧',
            status: 1,
          },
          {
            name: '夜间巡逻2号大门',
            time: '14:30（预计45分钟）',
            address: 'A区产业园北侧',
            status: 0,
          },
        ],
        robotList: [
          {
            name: '绝影X30PRO',
            battery: 60,
          },
          {
            name: '绝影002',
            battery: 60,
          },
          {
            name: '绝影003',
            battery: 60,
          },
          {
            name: '绝影004',
            battery: 30,
          },
          {
            name: '绝影005',
            battery: 20,
          },
        ],
        count: 3,
        selectedTaskRows: [],
        selectedRobotRows: [],
        fromMain: true
      },
      controlInfo: {
        tabList: [
          {
            label: '基础控制',
            value: 0
          },
          {
            label: '高级控制',
            value: 1
          }
        ],
        tabIndex: 0,
      }
    }
  },
  methods: {
    handleClickRobotRow(item) {
      if (this.taskInfo.selectedRobotRows.includes(item)) {
        this.taskInfo.selectedRobotRows = this.taskInfo.selectedRobotRows.filter(row => row !== item)
      } else if (this.taskInfo.selectedRobotRows.length < this.taskInfo.count) {
        this.taskInfo.selectedRobotRows.push(item)
      }
    },
    handleClickTaskRow(item) {
      if (index && !this.taskInfo.fromMain) {
        this.taskInfo.selectedTaskRows = [item]
      }
    },
  }
}
</script>