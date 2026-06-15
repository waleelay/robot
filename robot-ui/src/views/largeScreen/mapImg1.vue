<template>
  <div>
    <img :src="currentImage" alt="地图" class="image" @load="getMapSize" ref="mapImage">

    <div class="robot"
         slot="reference"
         :style="getRobotStyle(robot)">
    </div>
  </div>
</template>

<script>
export default {
  name: 'mapImg',
  props: {

  },
  data() {
    return {

    }
  },
  methods: {
    //控制机器人样式（正常or异常，以及位置）
    getRobotStyle(robot) {
      let imagePath;
      switch(robot.state) {
        case 'normal':
          imagePath = require('../../assets/bigScreen/icon/robot_normal.png');
          break;
        case 'error':
          imagePath = require('../../assets/bigScreen/icon/robot_error.png');
          break;
        default:
          imagePath = require('../../assets/bigScreen/icon/robot_normal.png')
      }
      return {
        left: robot.x+'%',
        top: robot.y+'%',
        backgroundImage:`url(${imagePath})`,
      }
    },
  }
}
</script>