/*
 * @Author: dengxumei
 * @Date: 2026-04-16 15:59:16
 * @LastEditors: dengxumei
 * @LastEditTime: 2026-04-17 17:13:51
 * @Description: 
 * @FilePath: \qihang-eiop-ui\src\views\bi\js\mixins\robotControl.js
 * @Version: 
 */
import { butaiList, robotControlObj } from '../constants/robot-control';
import { controlRobot, getMotionControlApi } from "../utils/robotUtil"
import SpeedDialog from './../../components/modal/Speed.vue'

export default {
  props: {
    endpoint: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      butaiList,
      robotControlObj,
      deviceData: {
        battery: 0,
        status: "离线",
        deviceName: "",
        onDockState: ''
        // LineName: "路径1XXX区域",
      },
      butaiValue: 0,
      speed: ''
    }
  },
  computed: {
    //获取基础信息
    basicMessage() {
      return this.$store.getters['websocket/getBasic'];
    },
  },
  components: { SpeedDialog },
  methods: {
    // 设定速度
    setSpeed() {
      this.$refs.speedRef.dialogVisible = true
    },
    // 控制转向
    handleControl(item, commandValue) {
      if (this.deviceData.onDockState === '1') return
      if (item.label === '前进' || item.label === '后退') {
        // getMotionControlApi(this.endpoint, item.value, this.speed === '' ? -1 : this.speed)
        getMotionControlApi(this.endpoint, item.value, commandValue === undefined ? this.speed === '' ? -1 : this.speed : commandValue)
      } else {
        getMotionControlApi(this.endpoint, item.value)
      }
    },
    changeSpeed(speed) {
      this.speed = speed
    },
    controlRobot,
    //取到基础信息以后
    handleBasicMessage(message) {
      // console.log(message)
      //拿里面的电池信息
      if (message && message.items && message.items.electricity != null) {
        this.deviceData.battery = message.items.electricity
        this.deviceData.onDockState = message.items.onDockState
        this.deviceData.status = '在线'
      } else {
        console.error("电量信息格式不正确", message);
      }
      //其他操作-----------------------------------------------
    },
  },
  watch: {
    // 获取基础信息
    basicMessage: {
      handler(newMessage) {
        if (newMessage && newMessage.length > 0) {
          // console.log("获取基础信息");
          // const message = newMessage[newMessage.length - 1];
          // if (message) {
          //   // console.log(JSON.stringify(message, null, 2));
          //   //获取基础信息后的行动
          //   this.handleBasicMessage(message);
          // }
          const arr = newMessage.filter(item => item.endpoint === this.endpoint)
          if (arr.length) {
            this.handlebasicMessage(arr[0])
          }
        }
      },
      immediate: true
    },
    // motionId(newVal) {
    //   if (newVal === 100) {
    //     this.mouseUpPTZControl()
    //   } else {
    //     this.mouseDownPTZControl(newVal)
    //   }
    // },
  }
}