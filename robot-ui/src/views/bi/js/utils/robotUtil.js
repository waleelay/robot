import Cookies from "js-cookie";
import { motionControl } from '@/api/login'
import { controlDevice } from '@/api/bi';
import { Message } from 'element-ui';

export function controlRobot(type) {
  controlDevice(type, Cookies.get('targetId')).then(res => {
    if (res.code === 200) {
      Message.success(res.msg)
    } else {
      Message.error(res.msg)
    } 
  })
}
//调用控制行动的接口
export function getMotionControlApi(endpoint, command, commandValue) {
  // this.motion = command
  motionControl(command, commandValue, endpoint).then(res => {
    if (res.code === 200) {
      Message.success(res.msg)
    } else {
      Message.error(res.msg)
    }
  })
}
function changeSpeed(speed) {
  // this.speed = speed
}