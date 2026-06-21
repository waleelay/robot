<template>
  <div class="image-selector">
    <!-- <el-select :disabled="disabled" v-model="selectedMap" placeholder="选择地图" class="image-select" @change="getPointList" v-if="MapIdOptions.length">
      <el-option
        v-for="(image, index) in MapIdOptions"
        :key="index"
        :label="image.label"
        :value="image.value">
      </el-option>
    </el-select> -->
    <div class="image-container">
      <!--        图片-->
      <img :src="currentImage" alt="暂无地图" class="scaled-image" @load="getMapSize" ref="mapImage" style="z-index: 1;">
      <!-- <canvas id="drawingCanvas"></canvas> -->
      <!-- 线条 -->
      <CustomLine ref="lineRef" style="z-index: 2;" />
      <!--        点-->
      <el-tooltip v-for="(point, index) in obj.points || []"
        :key="point.pointId" effect="dark"
        :content="getAddStatus(point)"
        placement="top" style="z-index: 3;">
        <div
          :style="getPointStyle(point)"
          class="point-marker"
          :class="{ 'is-active': `posX${point.posX}posY${point.posY}` === pointTabsValue, 'big': getAddStatus(point).includes('充电点') }"
          @click="clickPoint(point, index, $event)">
        </div>
      </el-tooltip>
      <!-- <svg class="line-container" :width="displayWidth" :height="displayHeight">
        <line v-for="(line, index) in lines" :key="index"
              :x1="line.x1" :y1="line.y1" :x2="line.x2" :y2="line.y2"
              stroke="blue" stroke-width="2"/>
      </svg> -->
    </div>
  </div>
</template>

<script>
import { getMapIdOptions } from '@/api/robot/point'
import { addRoute,getMapPointbyId,searchAllAction } from '@/api/task/taskRoute.js'
import CustomLine from './line.vue'
export default {
  name: 'point-select',
  props: {
    obj: {
      type: Object,
      default: () => ({}),
    },
    disabled: {
      type: Boolean,
      default: false
    },
    initedInfo: {
      type: Object,
      default: () => ({}),
    },
    pointTabsValue: {
      type: String,
      default: ''
    }
  },
  components: { CustomLine },
  data() {
    return {
      switchValue: false,
      //查看点在路线中的值
      showPoint: undefined,
      pointShowOpen: false,
      dialogPointShow: [],
      pointShowTitle: '',
      //初始坐标
      X0: undefined,
      Y0: undefined,
      //选择地图
      MapIdOptions:[],
      //默认选择地图0
      selectedMap: null,
      currentImage: ``,  // default image
      //获取到的点的列表(坐标是（x，y）)
      pointList: [],
      //被选中的点的集合
      selectedPoints: [],
      //被选中的点的运动集合
      selectPointsActions: [],
      //图片本身大小
      mapHeight: 0,
      mapWidth: 0,
      //在页面上实际显示大小
      displayWidth: 0,
      displayHeight: 0,
      //弹窗
      open: false,
      LineNameOpen: false,
      title:'',
      //要给后端传的数据
      data: {
        LineName: undefined,
        MapID: undefined,
        MapName: undefined,
        LineType: 0,
        pointInfos:[],
        actionAllList: [],
      },
      //规则
      rules: {LineName: [{required: true,message:'请输入路径名称！' ,trigger: 'blur'}]},
      //弹窗的表单配置(要传后端data中的pointInfos)
      pointForm: {
        PointId: undefined,
        PointName: undefined,
        Value: undefined,
        MapID: 1,
        PosX: 0.0,
        PosY: 0.0,
        PosZ: 0.0,
        AngleYaw: 0.0,
        PointInfo: 0,
        Terrain: 0,
        Gait: 0,
        Speed: 0,
        Posture: 0,
        Manner: 0,
        ObsMode: 0,
        NavMode: 0,
        x: 0,
        y: 0,
        WaitTime:0,
        NoNamedTime: 0,
      },
      //运动配置的表单(要传后端data中的actionAllList)
      ActionList: {
        PointName: undefined,
        PointId: undefined,
        Value: undefined,
        Action:[],
      },
      //ActionList中的Action
      actionInfo: {
        ActionId: undefined,
        ActionName: undefined,
        ActionUrl: undefined,
        ActionParams: [],
      },
      //查找到的运动列表
      actionOptions: [],
      // 默认选中的运动类型
      selectAction: {},
      lines: [],
      formPoints: [],
    };
  },
  async created() {
    // await this.getMapIdOptions();
    // await this.getPointList();
    // this.getActionList()
  },
  computed: {
    computedLineType: {
      get() {
        return this.switchValue ? 1 : 0;
      },
      set(value) {
        this.switchValue = value === 1;
        this.data.LineType = value;
      },
    },
  },

  watch: {
    // 监听 switchValue 的变化，同步更新 data.LineType
    switchValue(newVal) {
      this.data.LineType = newVal ? 1 : 0;
    },
    // 监听 data.LineType 的变化，同步更新 switchValue
    'data.LineType'(newVal) {
      this.switchValue = newVal === 1;
    },
    obj: {
      handler(newVal, oldVal) {
        if (newVal) {
          if (newVal.mapInfo && newVal.mapInfo.id) {
            if (this.selectedMap !== newVal.mapInfo.id) {
              const { name, id, startXCoordinate, startYCoordinate, mapFileUrl } = this.obj.mapInfo
              this.MapIdOptions = [{ label: name, value: id, X0: startXCoordinate, Y0: startYCoordinate }]
              this.selectedMap = this.MapIdOptions[0].value
              // this.currentImage = `${process.env.VUE_APP_BASE_API}/maps/${this.MapIdOptions[0].label}.jpg`
              // this.currentImage = `${process.env.VUE_APP_BASE_API}/maps/测试.jpg`
              // this.currentImage = require('./地图1.jpg')
              // this.currentImage = require('./李磊的地图.jpg')
              // this.currentImage = require('file://D:\\resources\\robot\\maps\\李磊的地图.jpg')
              // const d = 'D:\\resources\\robot\\maps\\地图1.jpg'
              // console.log(newVal.mapInfo.mapFileUrl);
              const url = newVal.mapInfo.mapFileUrl + ''
              // console.log('1111111', url);
              
              // this.currentImage = require('D:\\resources\\robot\\maps\\地图1.jpg')
              // this.currentImage = url
              // this.currentImage = require('D:/resources/robot/maps/地图1.jpg')
              this.currentImage = `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${newVal.mapInfo.mapFileUrl}`
              // const t = 'resources\\robot\\maps\\地图1.jpg'
              // this.currentImage = require('D:\\' + t)
              // this.currentImage = require('D:/resources/robot/maps/李磊的地图.jpg')
              // const fs = require('fs')
              // const path = require('path')
              // const data = fs.readFileSync(url)
              // const ext = path.extname(url).slice(1)
              // const currentImage1 = `data:image/${ext};base64,${data.toString('base64')}`
              // console.log(currentImage1)
              setTimeout(() => {
                this.getPointList()
              }, 1000);
            }
          } else {
            this.MapIdOptions = []
            this.selectedMap = null
            this.currentImage = ''
          }
        }
      },
      deep: true,
      // immediate: true
    }
  },
  methods: {
    //最开始获取地图列表
    async getMapIdOptions() {
      const res = await getMapIdOptions()
        this.MapIdOptions = res.data.map(item=>({
          value: item.MapID,
          label: item.MapName,
          X0: item.X0,
          Y0: item.Y0,
        }))
      this.selectedMap = this.MapIdOptions[0].value
      this.currentImage = `${process.env.VUE_APP_BASE_API.replaceAll("'", '')}/maps/${this.MapIdOptions[0].label}.jpg`
    },
    //根据MapID获取pointList(更新尺寸)
    async getPointList() {
      // 更改 X0, Y0
      let selectedMapInfo = this.MapIdOptions.find(m => m.value === this.selectedMap);
      this.X0 = selectedMapInfo.X0;
      this.Y0 = selectedMapInfo.Y0;
      // this.X0 = -29.900000;
      // this.Y0 = -19.900000;
      // this.data.MapID = this.selectedMap;
      // this.data.MapName = selectedMapInfo.label;

      // 确保图片尺寸已经获取
      await this.getMapSize();

      // 检查 mapWidth 和 mapHeight 是否有效
      if (!this.mapWidth || !this.mapHeight) {
        console.error("地图尺寸未正确加载", this.mapWidth, this.mapHeight);
        return;
      }

      // 获取对应 MapID 的图像以及点位
      // getMapPointbyId(this.selectedMap).then((res) => {
      //   this.pointList = res.data.map((item) => {
      //     let PixelX = (item.posX - this.X0) / 0.1;
      //     let x = (PixelX / this.mapWidth) * 100;
      //     let PixelY = this.mapHeight - (item.posY - this.Y0) / 0.1;
      //     let y = (PixelY / this.mapHeight) * 100;
      //     return {
      //       ...item,
      //       x: x,
      //       y: y,
      //     };
      //   });
      //   // console.log(this.pointList);
      //   this.$emit('getPointList', this.pointList)
      // });

      this.obj.points = this.obj.points.map((item) => {
        // const Res = this.mapWidth / this.displayHeight
        let PixelX = (item.posX - this.X0) / 0.1;
        let x = (PixelX / this.mapWidth) * 100;
        let PixelY = this.mapHeight - (item.posY - this.Y0) / 0.1;
        let y = (PixelY / this.mapHeight) * 100;
        return {
          ...item,
          x,
          y,
        };
        
      });      
      this.$emit('getPointList', this.formPoints)
    },
    //更新尺寸
    async getMapSize() {
      const img = this.$refs.mapImage;
      await new Promise((resolve) => {
        // 如果图片已经加载完成，直接解析
        if (img.complete) {
          resolve();
        } else {
          img.onload = () => resolve();
          img.onerror = () => resolve(); // 如果图片加载失败，也需要解析
        }
      });
      
      this.mapWidth = img.naturalWidth;
      this.mapHeight = img.naturalHeight;

      // 获取图片在页面上的实际显示大小
      this.displayWidth = img.clientWidth;
      this.displayHeight = img.clientHeight;
      // console.log("长宽已经改变了", this.mapWidth, this.mapHeight);
    },
    //获取动作列表
    getActionList() {
      searchAllAction().then(res=> {
        // console.log(res.data)
        let paramsArray = []
        this.actionOptions = res.data.map(item => {
          if (item.ActionParams !== "" && item.ActionParams !== null) {
            paramsArray = item.ActionParams.split(';').map(param => {
              let [type, name] = param.split(',')
              return {type: type,name: name,content:this.$set({},'content',undefined)}
            });
          } else {
            paramsArray = []
          }
          return {
            ActionString: item.ActionParams,
            ActionId: item.ActionId,
            ActionName: item.ActionName,
            ActionUrl: item.ActionUrl,
            ActionParams: paramsArray
          }
        })
        // console.log(this.actionOptions)
      })
    },
    // 新增动作选择
    addActionSelect() {
      this.ActionList.Action.push({
        ActionParams: [],
      });
       // console.log(this.ActionList)
    },
    // 删除动作选择
    removeActionSelect(index) {
      this.ActionList.Action.splice(index, 1);
    },
    // 选择动作时更新参数
    onActionChange: function (action, index) {
      // console.log(action)
      // 查找所选动作在 actionOptions 中对应的 action
      const selectedAction = this.actionOptions.find(option => option.ActionId === action.ActionId);
      // console.log(selectedAction)
      // 更新 selectedAction 和 ActionParams
      this.$set(this.ActionList.Action, index, {
        ActionId: selectedAction.ActionId,
        ActionName: selectedAction.ActionName,
        ActionUrl: selectedAction.ActionUrl,
        ActionString: selectedAction.ActionString,
        ActionParams: selectedAction.ActionParams.map(param => ({
          ...param,
          content: '' // 初始化 content，确保为响应式
        }))
      });
      // console.log(this.ActionList)
    },
    // 获取表单验证规则
    getValidationRules(type) {
      if (type === 'int') {
        return [{ required: true, message: '请输入数字', pattern: /^[0-9]+$/, trigger: 'blur' }];
      } else if (type === 'char') {
        return [{ required: true, message: '请输入文字', pattern: /^[\u4e00-\u9fa5]+$/, trigger: 'blur' }];
      }
      return [];
    },

    //获取每个点的位置样式
    getPointStyle(point) {
      return {
        left: point.x+'%',
        top: point.y+'%',
      }
    },
    getAddStatus(point) {
      let addIndex = 0
      this.$emit('getAddStatus', point, (index, tabPoint) => {
        const pointInfo = tabPoint.pointInfo
        const pointName = tabPoint.pointName
        const text = pointInfo == 3 ? '，充电点' : ''
        if (pointName) {
          addIndex = pointName + (index ? `(${index === 1 ? '起始点' : ('已添加' + text)})` : '')
        } else {
          addIndex = '路径点' + (index ? `${index}(${index === 1 ? '起始点' : ('已添加' + text)})` : '')
        }
      })
      return addIndex || (point.qhMotionEquipmentConfig?.pointInfo == 3 ? '路径点（充电点）' : '路径点')
    },
    clickPoint(point, index, event) {
      if (!this.disabled)
      this.$emit('getPointInfo', { point, index, event })
    },
    deletePoint(item) {
      this.selectedPoints.splice(item.Value-1)
      this.selectPointsActions.splice(item.Value-1)//Action
      this.updateLines(); // 更新线条
      this.pointShowOpen = false
    },
    updatePoint(item) {
      const selectedPoint = this.selectedPoints.find(p => p.Value === item.Value);
      const selectedPointAction = this.selectPointsActions.find(a => a.Value === item.Value)
      Object.assign(this.pointForm, selectedPoint);
      Object.assign(this.ActionList, selectedPointAction);//Action
      this.open = true
      this.pointShowOpen = false
      // console.log(this.ActionList)
      this.title = "修改"+ item.PointName+'配置'
    },
    // 新增
    chooseAdd() {
      this.pointShowOpen = false
      this.addPoint(this.showPoint)
    },
    addPoint(point) {
      this.open = true
      this.pointForm.PointId = point.PointId
      this.pointForm.PointName = point.PointName
      this.pointForm.MapID = point.MapID;
      this.pointForm.PosX = point.PosX;
      this.pointForm.PosY = point.PosY;
      this.pointForm.PosZ = point.PosZ;
      this.pointForm.AngleYaw = point.AngleYaw;
      this.pointForm.x = point.x
      this.pointForm.y = point.y
      this.title = '新增路径点位'+ point.PointName
      this.reset()
      this.ActionList.PointId = point.PointId
      this.ActionList.PointName = point.PointName
      this.ActionList.Action = []
    },
    reset(){
      this.pointForm.PointInfo = 0
      this.pointForm.Terrain = 0
      this.pointForm.Gait = 0
      this.pointForm.Speed = 0
      this.pointForm.Posture = 0
      this.pointForm.Manner = 0
      this.pointForm.ObsMode = 0
      this.pointForm.NavMode = 0
      this.pointForm.WaitTime = 0
      this.pointForm.NoNamedTime = 0
    },
    //验证表单规则
    handleConfirm() {
      let valid = true;
      // 遍历 ActionList.Action 数组
      this.ActionList.Action.forEach((action, actionIndex) => {
        // 遍历每个动作的参数
        action.ActionParams.forEach((param, paramIndex) => {
          const rules = this.getValidationRules(param.type);

          // 如果有规则则进行验证
          if (rules && rules.length > 0) {
            rules.forEach(rule => {
              if (rule.required && !param.content) {
                valid = false;
                this.$message.error(`请填写 ${param.name}`);
              } else if (rule.pattern && !rule.pattern.test(param.content)) {
                valid = false;
                this.$message.error(`请正确填写 ${param.name},该参数类型是${param.type}`);
              }
            });
          }
        });
      });

      if (valid) {
        // 如果验证通过，执行提交操作
        this.afterHandleConfirm();
      }
    },
    //点击确认按钮
    afterHandleConfirm() {
      if (this.pointForm.Value != undefined) {
        //修改
        //返回这个已经被选中了的点对应的序号
        const selectedPointIndex = this.selectedPoints.findIndex(p => p.Value === this.pointForm.Value);
        const selectedPointAction = this.selectPointsActions.findIndex(a => a.Value === this.pointForm.Value)
        if (selectedPointIndex !== -1) {
          // 更新已选中的点
          Object.assign(this.selectedPoints[selectedPointIndex], this.pointForm);
          //如果不是任务点
          if (this.pointForm.PointInfo !== 1 ) {
            this.ActionList.Action = []
          }
          Object.assign(this.selectPointsActions[selectedPointAction], this.ActionList);
        }
        this.pointForm.Value = undefined
      } else {
        //新增
        //这里是一个深拷贝
        const newPointForm = JSON.parse(JSON.stringify(this.pointForm));
        newPointForm.Value = this.selectedPoints.length + 1;
        this.selectedPoints.push(newPointForm);
        // console.log(this.selectedPoints)

        //这里是对动作的深拷贝
        const newAction = JSON.parse(JSON.stringify(this.ActionList));
        newAction.Value = this.selectedPoints.length;
        //如果不是任务点
        if (this.pointForm.PointInfo !== 1 ) {
          newAction.Action = []
        }
        this.selectPointsActions.push(newAction)
        this.pointForm.Value = undefined
      }
      this.updateLines(); // 更新线条
      this.open = false
    },
    //更新线条(控制线条样式)
    updateLines() {
      this.lines = []; // 重置线条数组
      // 将点按 Value 排序
      const sortedPoints = [...this.selectedPoints].sort((a, b) => a.Value - b.Value);

      for (let i = 0; i < sortedPoints.length - 1; i++) {
        const startPoint = sortedPoints[i];
        const endPoint = sortedPoints[i + 1];
        // 计算线条的起点和终点
        this.lines.push({
          x1: startPoint.x / 100 * this.displayWidth,
          y1: startPoint.y / 100 * this.displayHeight,
          x2: endPoint.x / 100 * this.displayWidth,
          y2: endPoint.y / 100 * this.displayHeight,
        });
      }
    },

    // 数据处理
    confirmNamed() {
      // console.log(this.selectPointsActions)
      this.$refs.dataRef.validate(valid => {
        if (valid) {
          let actionAll = this.selectPointsActions.map(point => {
            let transformedActions = point.Action.map(action => {
              let paramsContent = action.ActionParams.map(param => param.content).join(',')
              return {
                ActionId: action.ActionId,
                ActionName: action.ActionName,
                ActionUrl: action.ActionUrl,
                ActionParams: paramsContent,
                ActionString: action.ActionString,
              };
            });
            return {
              PointId: point.PointId,
              PointName: point.PointName,
              Value: point.Value,
              ActionList: transformedActions
            }
          })
          this.data.pointInfos = this.selectedPoints
          this.data.actionAllList = actionAll
          // console.log(this.data)
          addRoute(this.data).then(res=> {
            // console.log(res)
            const obj = { path: "/task/taskRoute" };
            this.$tab.closeOpenPage(obj);
            // this.$router.push({ path:"/task/taskRoute" })
          })
        }
      })
    },
  }
};
</script>

<style lang="scss">
.image-selector {
  position: relative;
  height: 100%;
  width: 100%;
  overflow: hidden;
  /*padding-top: 50px;*/
}

.image-select {
  position: absolute;
  top: 20px;
  left: 20px;
  z-index: 10;
}
.buttons-container {
  position: absolute;
  z-index: 10;
  top: 10px;
  right: 10px;
}
.image-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  width: 100%;
  position: relative;
}

.scaled-image {
  min-height: 600px;
  max-height: 800px;
  height: 100%;
  width: 100%;
  /*object-fit: contain;*/
}

.point-marker {
  position: absolute;
  width: 5px;
  height: 5px;
  background-color: #4d995f;
  border-radius: 50%;
  cursor: pointer;
  &.is-active {
    width: 8px;
    height: 8px;
    background-color: #36f162;
  }
  &.big {
    width: 8px;
    height: 8px;
    background-color: #026d26;
  }
}
.line-container {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
</style>
