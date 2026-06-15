<template>
  <div class="side-box d-flex flex-column right d-flex" style="width: 1251px">
    <div class="header-box flx-justify-between">
      <div class="title" style="width: 226px;">
        实时视频
      </div>
      <div class="flx-align-center">
        <div class="divided flx-align-center">
          <div class="item" :class="{ 'active': dividedType === 1 }" @click="dividedType = 1">一分屏</div>
          <div class="item ml15" :class="{ 'active': dividedType === 4 }" @click="dividedType = 4">四分屏</div>
          <div class="item ml15" :class="{ 'active': dividedType === 6 }" @click="dividedType = 6">六分屏</div>
        </div>
        <!-- <div class="select-list ml30">
          <el-select
            v-model="selectedValue"
            placeholder="请选择"
            class="custom-select"
            popper-class="custom-select-popper"
            @change="handleSelectStream"
          >
            <el-option
              v-for="item in options"
              :key="item.value"
              :label="item.label"
              :value="item.value">
              
              <div class="flx-align-center">
                <el-radio v-model="selectedValue" :label="item.value" class="custom-radio">{{ item.label }}</el-radio>
              </div>
            </el-option>
          </el-select>
        </div> -->
        <!-- emitPath 为false时，只返回选中的子节点 -->
        <el-cascader
          v-model="cascaderValue"
          class="custom-cascader ml30"
          :options="cascaderList"
          :props="cascaderProps"
          collapse-tags
          :show-all-levels="false"
          clearable
          popper-class="custom-cascader-popper"
          @change="handleChangeCascader"
        ></el-cascader>
      </div>
    </div>
    <div class="video-content mt20">
      <div class="arrow one"></div>
      <div class="arrow two"></div>
      <div class="arrow three"></div>
      <div class="arrow four"></div>
      <!-- <Box class="w100 h100" prefixId="remote-box-" style="background: #000; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);" /> -->
      <Box class="w100 h100" prefixId="remote-box-" ref="romoteBoxRef" :videoNum="dividedType" @getDogList="getDogList" style="background: #001224;" />
      <!-- <Box class="w100 h100" prefixId="remote-box-" ref="romoteBoxRef" :videoNum="dividedType" @getDogList="getDogList" style="background: #001224;" /> -->
      <!-- <div v-if="!cascaderList.length" class="w100 h100" style="background: #001224"></div> -->
    </div>
    <div class="oper-box flx-justify-between mt20" style="align-items: flex-start">
      <div class="equipment card">
        <div class="title flx-justify-between">
          <div class="text">本体设备控制</div>
          <div class="select-list ml30">
            <el-select
              v-model="selectEquipmentId"
              placeholder="请选择"
              class="custom-select"
              popper-class="custom-select-popper"
              @change="handleSelectEquipment"
            >
              <el-option
                v-for="item in equipmentList"
                :key="item.id"
                :label="item.name"
                :value="item.id">
                
                <!-- <div class="flx-align-center">
                  <el-radio v-model="selectEquipmentId" :label="item.value" class="custom-radio">{{ item.name }}</el-radio>
                </div> -->
              </el-option>
            </el-select>
          </div>
        </div>
        <template v-if="selectEquipmentId">
          <div class="flx-justify-between mt20 pr72">
            <div class="basic-info">
              <div class="device-status-title">设备状态</div>
              <div class="flx-justify-between mt17">
                <span>当前速度：</span>
                <span>{{ currentInfo.speed ? `${currentInfo.speed}m/s` : '-' }}</span>
              </div>
              <div class="flx-justify-between mt14">
                <span>当前电量：</span>
                <span>{{ currentInfo.battery ? `${currentInfo.battery}%` : '-' }}</span>
              </div>
              <div class="flx-justify-between mt14">
                <span>控制模式：</span>
                <span>{{ currentInfo.controlMode || '-' }}</span>
              </div>
              <div class="flx-justify-between mt14">
                <span>状态：</span>
                <span v-if="currentInfo.dogState" class="status success">{{ currentInfo.dogState }}</span>
                <span v-else>-</span>
              </div>
            </div>
            <img src="@/assets/images/bi/new/dog-large.png" alt="" class="mt44" width="151px" height="97px">
            <div class="flex-column" style="height: 148px">
              <el-radio-group v-model="controlType" size="large" class="custom-radio-button">
                <el-radio-button :label="0">基础控制</el-radio-button>
                <el-radio-button :label="1">高级控制</el-radio-button>
              </el-radio-group>
              <div class="control flx-center mt20" style="width: 333px; height: 100px">
                <template v-if="controlType === 0">
                  <div class="direction" :class="{ 'custom-disabled': currentInfo.onDockState === '1' }">
                    <div class="inner">方向</div>
                    <svg-icon icon-class="d-up" style="font-size: 14px" class="arrow up" @click.native="handleControlRotation(1)" />
                    <svg-icon icon-class="d-right" style="font-size: 14px" class="arrow right" @click.native="handleControlRotation(4)" />
                    <svg-icon icon-class="d-down" style="font-size: 14px" class="arrow down" @click.native="handleControlRotation(2)" />
                    <svg-icon icon-class="d-left" style="font-size: 14px" class="arrow left" @click.native="handleControlRotation(3)" />
                  </div>
                  <div class="buttons flx-center flex-column ml40" :class="{ 'custom-disabled': currentInfo.onDockState === '1' }">
                    <div>
                      <el-button tt="primary" @click.native="handleControl(btnObj['zuoyi'].value)">左平移</el-button>
                      <el-button tt="primary" class="ml20" @click.native="handleControl(btnObj['youyi'].value)">右平移</el-button>
                    </div>
                    <div class="mt20">
                      <el-button tt="primary" @click="controlDevice('shutdown')">一键返航</el-button>
                      <el-button tt="primary" @click="controlDevice('startup')" class="ml20">退出充电</el-button>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <div class="buttons flx-center flex-column ml40" :class="{ 'custom-disabled': currentInfo.onDockState === '1' }">
                    <div>
                      <el-button tt="primary" @click.native="handleControl(btnObj['zhanli'].value)">站立</el-button>
                      <el-button tt="primary" class="ml20" @click.native="handleControl(btnObj['paxia'].value)">趴下</el-button>
                      <el-button tt="primary" class="ml20" @click.native="handleControl(btnObj['tztb'].value)" style="width: 93px">停止踏步</el-button>
                    </div>
                    <div class="mt20">
                      <el-button tt="primary" @click.native="handleControl(btnObj['jiting'].value)">急停</el-button>
                      <el-button tt="primary" class="ml20" @click.native="setSpeed">设定速度</el-button>
                      <el-select
                        v-model="butaiValue"
                        placeholder="切换步态"
                        @change="handleChangebutai"
                        class="custom-select ml20 w-93"
                        popper-class="custom-select-popper"
                        title="切换步态"
                      >
                        <el-option label="行走" :value="0" />
                        <el-option label="普通楼梯" :value="1" />
                        <el-option label="斜坡/防滑" :value="2" />
                        <el-option label="感知楼梯" :value="4" />
                      </el-select>
                    </div>
                  </div>
                </template>
                
              </div>
            </div>
          </div>
        </template>
        <div v-else class="w100" style="height: 170px; line-height: 170px; text-align: center; color: #fff">
          {{ equipmentList.length  ? '请选择设备' : '暂无装备' }}
        </div>
      </div>
      <div class="equipment-top card">
        <div class="title flx-justify-between">
          <div class="text">上装设备控制</div>
        </div>
        <template v-if="selectEquipmentInfo.qhMountedEquipmentList && selectEquipmentInfo.qhMountedEquipmentList.length">
          <div class="mt17 ml17">
            <el-radio-group v-model="topType" size="large" class="custom-radio-button">
              <el-radio-button :label="0" v-if="getMountedEquipmentInfo(selectEquipmentInfo.qhMountedEquipmentList, 'audio')">对讲机</el-radio-button>
              <el-radio-button :label="1" v-if="getMountedEquipmentInfo(selectEquipmentInfo.qhMountedEquipmentList, 'video')">云台</el-radio-button>
            </el-radio-group>
          </div>
          <div class="control flx-center mt20">
            <template v-if="topType === 0">
              <div class="progress" :style="{ opacity: showVolume ? 1 : 0, '--volume': Math.ceil(volume / 255 * 100) + '%' }">
                <div class="volume">当前音量：{{ volume }}</div>
              </div>
              <div @click="handleTalk" class="direction flx-center voice" :class="{ 'is-talk': isTalk, 'disabled': isDisabled  }">
                <svg-icon icon-class="voice" style="font-size: 50px" class="arrow" />
              </div>
              <div class="buttons flx-center flex-column ml40">
                <div>
                  <el-button tt="primary" @click="handleControlVoice('VOLUME_UP')">音量+</el-button>
                  <el-button tt="primary" class="ml20" @click="handleControlVoice('VOLUME_DOWN')">音量-</el-button>
                </div>
                <div class="mt20 w100">
                  <el-button tt="primary" class="w100" @click="handleControlVoice('MUTE')">{{ isMuted ? '取消静音' : '静音' }}</el-button>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="direction">
                <div class="inner">方向</div>
                <svg-icon icon-class="d-up" style="font-size: 14px" class="arrow up" @click.native="handleClickPTZControl(btnObj['up'])" />
                <svg-icon icon-class="d-right" style="font-size: 14px" class="arrow right" @click.native="handleClickPTZControl(btnObj['down'])" />
                <svg-icon icon-class="d-down" style="font-size: 14px" class="arrow down" @click.native="handleClickPTZControl(btnObj['left'])" />
                <svg-icon icon-class="d-left" style="font-size: 14px" class="arrow left" @click.native="handleClickPTZControl(btnObj['right'])" />
              </div>
              <div class="buttons flx-center flex-column ml40">
                <div>
                  <el-button tt="primary" @click.native="handleClickPTZControl(btnObj['snapshot'])">拍照</el-button>
                  <el-button tt="primary" class="ml20" @click.native="handleClickPTZControl(btnObj['video'])">录像</el-button>
                </div>
                <div class="mt20">
                  <el-button tt="primary" @click.native="handleClickPTZControl(btnObj['jiaojuPlus'])">焦距+</el-button>
                  <el-button tt="primary" class="ml20" @click.native="handleClickPTZControl(btnObj['jiaojuMinus'])">焦距-</el-button>
                </div>
              </div>
            </template>
          </div>
        </template>
        <div v-else class="w100" style="height: 170px; line-height: 170px; text-align: center; color: #fff">
          暂无上装设备
        </div>
      </div>
    </div>
    <Speed ref="speedRef" @changeSpeed="changeSpeed" />
  </div>
</template>

<script>
import axios from "axios";
import { controlVoice, controlDevice, controlVoiceStatus } from "../../../../../api/bi";
import { listAllMotion } from "./../../../../../api/rsp/equipment";
import { motionControl } from '@/api/login'
import Speed from './speed.vue'
import Box from './../../../box/box.vue'
import { locationObj, motionStateObj, motorStateObj, chargeStateObj, controlModeObj } from '../../../dog';
export default {
  name: 'CljgRight',
  dicts: ['qh_motion_equipment_type', 'qh_motion_equipment_model_number'],
  data() {
    return {
      dividedType: 1,
      equipmentList: [],
      // 选择的设备对象信息
      selectEquipmentInfo: {},
      // 选择的设备id
      selectEquipmentId: '',
      cascaderValue: [],
      cascaderList: [],
      // 基础控制、高级控制
      controlType: 0,
      // 对讲机、云台
      topType: 0,
      //语音对讲
      isTalk: false,
      isDisabled: false,
      btnObj: {
        up: 21,
        down: 22,
        left: 23,
        right: 24,
        snapshot: 25,
        video: 26,
        jiaojuPlus: 27,
        jiaojuMinus: 28
      },
      volume: 0,
      isMuted: false,
      showVolume: false,
      showTimer: null,
      currentInfo: {},
      speed: '',
      butaiValue: 0,
      allRobotInfo: [],
      currentInfo: {},
      deviceObj: {
        // 装备名称
        deviceName: "",
        // 装备状态
        gearState: '-空闲中-',
        // 运动状态
        motionState: '',
        // 累计里程
        sumOdom: 0,
        // 当次里程
        curOdom: 0,
        // 定位状态
        location: '',
        // 电机状态
        motorState: '',
        // 自主充电状态
        chargeState: '',
        // 控制模式
        controlMode: '',
        // 速度
        speed: 0,
        dogState: '正常',
        // 电量
        battery: 0,
        status: "离线",
        dogType: '',
        controlMode: '',
        model: '绝影X30 Pro',
        task: '无',
        isWarning: '否'
      },
      //地图的长宽
      mapHeight: 0,
      mapWidth: 0,
      displayWidth: 0,
      displayHeight: 0,
      X0: 0.0,
      Y0: 0.0,
      Res: 0.1,
    }
  },
  components: {Speed, Box},
  computed: {
    // 语音系统是否连接
    voiceConnect() {
      return this.$store.getters['voiceCall/getConnected'];
    },
    // 语音websocket
    voiceWebSocket() {
      return this.$store.getters['voiceCall/getVoiceWebSocket'];
    },
    // 告警
    refreshWarningTime() {
      return this.$store.getters['websocket/getRefreshWarningTime'];
    },
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
    cascaderProps() {
      return {
        multiple: this.dividedType !== 1,
        value: 'id',
        label: 'name',
        children: 'boxSources',
        emitPath: false,
        expandTrigger: 'hover',
        disabled: (node) => {
          // 如果已经达到最大数量，且当前节点未被选中，则禁用
          return this.dividedType > 1 && this.cascaderValue.length >= this.dividedType &&  !this.cascaderValue.includes(node.value);
        }
      }
    }
  },
  mounted() {
    // this.getDogList()
  },
  methods: {
    getDogList(cameras) {
      listAllMotion({ stateIn: '1,2,3' }).then(response => {
        console.log('数据源', cameras, response.data);
        this.cascaderList = [];
        this.equipmentList = [];
        // 筛选存在摄像头的设备
        // this.equipmentList = response.data.filter(item => {
        //   return item.qhMountedEquipmentList.filter(m => m.type === 'video').length
        // });
        response.data.map((item, index) => {
            const sources = cameras.filter(camera => camera.sourceId.startsWith(item.id))
            item.boxSources = sources.map(source => {
                // source.name = item.name + source.desc
                source.name = source.desc
                return source
            })
            if (index === 0) {
                this.selectEquipmentId = item.id
                this.selectEquipmentInfo = item
            }
            // 是否有数据源的才显示
            // if (item.boxSources.length) {
              this.cascaderList.push(item)
            // }
              this.equipmentList.push(item)
        })
        console.log('筛选后this.equipmentList', this.equipmentList)
        // Cookies.set('targetId', response.data[0].id)
        // Cookies.set('targetName', response.data[0].name)
      });
    },
    // 级联选择器改变时触发
    handleChangeCascader(value) {
      console.log('级联=====', value);
      this.$refs.romoteBoxRef.handleClickDevice(value)
    },
    // 切换视频流
    handleSelectStream(name) {
      // this.$refs.carouselRef.setActiveItem(name)
      this.selectEquipmentInfo = this.equipmentList.filter(item => item.id === name)[0] || {}
    },
    // 切换本体设备
    handleSelectEquipment(equipmentId) {
      this.selectEquipmentInfo = this.equipmentList.filter(item => item.id === equipmentId)[0] || {}
      const info = this.allRobotInfo.filter(item => item.endpoint === this.selectEquipmentInfo.endpoint)
      this.currentInfo = info.length ? info[0].customDevInfo : {}
    },
    getMountedEquipmentInfo(qhMountedEquipmentList = [], typeValue) {
      const arr = qhMountedEquipmentList.filter(item => item.type === typeValue)
      return arr.length ? arr[0] : null
    },
    // 设定速度
    setSpeed() {
      this.$refs.speedRef.dialogVisible = true
    },
    controlDevice(type) {
      controlDevice(type, this.selectEquipmentId).then(res => {
        if (res.code === 200) {
          this.$message.success(res.msg)
        } else {
          this.$message.error(res.msg)
        } 
      })
    },
    // 控制转向
    handleControlRotation(code) {
      if (this.currentInfo.onDockState === '1') return
      if (code === 1 || code === 2) {
        this.getMotionControlApi(code, this.speed === '' ? -1 : this.speed)
      } else {
        this.getMotionControlApi(code)
      }
    },
    handleControl(command) {
      this.getMotionControlApi(command)
    },
    // 切换步态
    handleChangebutai(commandValue) {
      this.getMotionControlApi(0, commandValue)
    },
    //调用控制行动的接口
    getMotionControlApi(command, commandValue) {
      this.motion = command
      motionControl(command, commandValue, this.selectEquipmentInfo.endpoint).then(res => {
        // console.log("调用控制行动的接口"+res)
      })
    },
    changeSpeed(speed) {
      this.speed = speed
    },
    videoMotionControl(item) {
      this.motionId = item
      this.handleClickPTZControl(item)
    },
    // 麦克风控制
    handleControlVoice(command) {
      if (this.showTimer) clearTimeout(this.showTimer)
      controlVoice(command, this.selectEquipmentInfo.endpoint.split(':')[0]).then(res => {
        if (res.code === 200) {
          this.volume = res.data.volume
          this.isMuted = res.data.isMuted
          this.showVolume = true
          this.showTimer = setTimeout(() => {
            this.showTimer = false
          }, 3000);
        }
      })
    },
    handleClickPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex)
    },
    mouseDownPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex,0)
    },
    mouseUpPTZControl() {
      this.yuntaiControl(23,1)
    },
    async yuntaiControl(dwPTZCommand, dwStop) {
      try{
        const response = await axios.get(process.env.NODE_ENV === 'development' ? process.env.VUE_APP_YUNTAI_CONTROL : `${location.origin}/yuntai`, {
          params: {
            dwPTZCommand,
            // dwStop: dwStop,
            // deviceId: Cookies.get('targetId')
            deviceId: this.selectEquipmentId
          },
        })
      }catch (err) {}
    },
    // 语音、云台
    async handleTalk() {
      if (this.isDisabled) {
        this.$message.warning('请勿重复点击！间隔3s后可再次点击。');
        return;
      }
      this.isTalk = !this.isTalk;
      this.isDisabled = true;
      setTimeout(() => {
        this.isDisabled = false;
        if (!this.voiceConnect) {
          this.isTalk = false
          this.$message.error('语音设备连接失败')
        }
      }, 3000);

      if (this.voiceConnect) {
        if (this.isTalk) {
          const res = await controlVoiceStatus('on', this.selectEquipmentInfo.endpoint)
           console.log('%c打开语音对讲麦克风', 'color: #f0f', res)
          if (res.code !== 200) {
            this.$message.warning('语音设备麦克风打开异常');
            return
          }
          await this.startRecording();
          this.voiceWebSocket.sendMessage({ type: 'startTalk', clientId: this.voiceWebSocket.clientId });
        } else {
          const res = await controlVoiceStatus('off', this.selectEquipmentInfo.endpoint)
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
        await this.voiceWebSocket.startRecording(this.selectEquipmentInfo.id);
        console.log('A: 语音对讲已开启');
      } catch (error) {
        console.error('开启语音对讲失败:', error);
        this.$message.error('无法开启语音对讲，请检查麦克风权限');
        this.isTalk = false;
      }
    },
    async stopRecording() {
      try {
        this.voiceWebSocket.stopRecording(this.selectEquipmentInfo.id);
        console.log('A: 语音对讲已关闭');
      } catch (error) {
        console.error('关闭语音对讲失败:', error);
        this.$message.error('关闭语音对讲失败');
      }
    },
  },
  watch: {
    // 监听到有异常刷新告警
    refreshWarningTime: {
      handler(val) {
        if (val) {
          // this.getXlxcData()
          // this.getWarnList(1, 'page')
        }
      }
    },
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          // TODO:增加经纬度定位
          // const message = newMessage[newMessage.length - 1];
          // if (message) {
          //   this.handlebasicMessage(message)
          // }
          this.allRobotInfo = newMessage.map(item => {
            if (item.items && item.items.posX != null) {
              const { posX, posY, res } = item.items
              const { latitude, longitude } = item.wgs84Result || {}
              this.Res = res
              let PixelX = (posX - (this.X0))/(0.1);
              let PixelY = this.mapHeight - (posY - (this.Y0))/(0.1)
              this.robot[item.endpoint] = {
                x: PixelX/this.mapWidth*100,
                y: PixelY/this.mapHeight*100,
                lat: latitude,
                lng: longitude
                // status: items.electricity !== null ? '在线' : '离线',
                // imagePath: require(`../../assets/bigScreen/icon/robot_${items.electricity === null ? 'error' : 'normal'}.png`)
              }
            }
            if (item.items && item.items.electricity != null) {
              const { motionState, sumOdom, curOdom, location, motorState, chargeState, controlMode, speed, electricity, onDockState } = item.items
              const { type, modelNumber, name } = item.equipment
              const { latitude, longitude } = item.wgs84Result || {}
              this.equipmentList.filter((dog, index) => {
                if (item.endpoint === dog.endpoint) {
                  // item.lat = lat || '30.748000';
                  // item.lng = lng || '106.040000'
                  item.lat = latitude;
                  item.lng = longitude
                }
                // this.pointMarkers[index].setLatLng([lat, lng])
                // this.updatePopups(index)
                return item.endpoint === dog.endpoint
              })
              return {
                ...item,
                customDevInfo: {
                  ...JSON.parse(JSON.stringify(this.deviceObj)),
                  status: '在线',
                  sumOdom,
                  curOdom,
                  motionState: motionStateObj[motionState],
                  location: locationObj[location],
                  motorState: motorStateObj[motorState],
                  chargeState: chargeStateObj[chargeState],
                  controlMode: controlModeObj[controlMode],
                  onDockState,
                  speed,
                  battery: electricity,
                  dogType: this.selectDictLabel(this.dict.type.qh_motion_equipment_type, type),
                  model: this.selectDictLabel(this.dict.type.qh_motion_equipment_model_number, modelNumber),
                  deviceName: name,
                  dogState: '正常'
                },
                lat: latitude || '30.748000',
                lng: longitude || '106.040000'
              }
            }
          })
          // if (!this.loadMap && isSlam) return
        }
      },
      immediate: true
    },
    dividedType: {
      handler(newVal, oldVal) {
        // if (newVal < oldVal && this.$refs.romoteBoxRef.showSourceIdList.length > newVal) {
        //   this.showSourceIdList = this.showSourceIdList.filter(item => item !== this.ZQL_playingSource[item])
        // }
        // this.$refs.romoteBoxRef.videoNum = val
        // this.ZQL_playingSource.videoNum = val
        this.cascaderValue = newVal === 1 ? null : []
        // this.$refs.romoteBoxRef.videoNum = newVal
        this.$nextTick(() => {
          // this.$refs.romoteBoxRef.clear()
        })
      }
    }
  },
  beforeDestroy() {
    // 清理资源
    if (this.voiceConnect) {
      this.voiceWebSocket.stopRecording();
    }
    if (this.timer) {
      clearInterval(this.timer)
    }
    if (this.showTimer) clearTimeout(this.showTimer)
  },
}
</script>
<style lang="scss" scoped>
@import "./index.scss";
</style>