export const robotControlList = {

}
// 机器狗本体控制指令
export const robotControlObj = {
  advance: { label: '前进', value: 1, class: 'up', key: 'base-forward' },
  back: { label: '后退', value: 2, class: 'down', key: 'base-backward' },
  'turn-left': { label: '左转', value: 3, class: 'left', key: 'base-left' },
  'turn-right': { label: '右转', value: 4, class: 'right', key: 'base-right' },
  zuoyi: { label: '左移', value: 11, class: 'left', key: 'base-strafe-left' },
  youyi: { label: '右移', value: 12, class: 'right', key: 'base-strafe-right' },
  zhanli: { label: '站立', value: 1 },//跟前进一致
  paxia: { label: '趴下', value: 15 },
  tztb: { label: '停止踏步', value: 14 },
  jiting: { label: '急停', value: 13 },
  // 步态：行走0 普通楼梯1 斜坡/防滑步态2 感知楼梯步态 4
  qhbt: { label: '切换步态', value: 20 },
}

export const butaiList = [
  { label: '行走', value: 0 },
  { label: '普通楼梯', value: 1 },
  { label: '斜坡/防滑', value: 2 },
  { label: '感知楼梯', value: 3 }
]