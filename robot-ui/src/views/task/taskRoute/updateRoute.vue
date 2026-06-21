<template>
  <div class="app-container">
    <!--    主页面-->
    <div class="image-selector">
      <div class="buttons-container">
        <el-button @click="closePage">取消</el-button>
        <el-button type="primary" @click="confirmRoute">确定</el-button>
      </div>
      <div class="image-container">
        <!--        图片-->
        <img v-if="isInitialized" :src="imageSrc" alt="Selected Image" class="scaled-image" @load="getMapSize" ref="mapImage">
        <!--        点-->
        <el-tooltip v-for="(point, index) in pointList" :key="index" effect="dark" :content="point.PointName" placement="top">
          <div
            :style="getPointStyle(point)"
            class="point-marker"
            @click="showPointConfig(point, $event)">
          </div>
        </el-tooltip>
        <!--                线条-->
        <svg class="line-container" :width="displayWidth" :height="displayHeight">
          <line v-for="(line, index) in lines" :key="index"
                :x1="line.x1" :y1="line.y1" :x2="line.x2" :y2="line.y2"
                stroke="blue" stroke-width="2"/>
        </svg>
      </div>
    </div>
    <!--    配置弹窗-->
    <el-dialog :visible.sync="open" width="600px" :title="title">
      <el-row :gutter="15">
        <el-form ref="pointFormRef" :model="pointForm" size="medium" label-width="100px">
          <el-col :span="12">
            <el-form-item label="目标点类型" prop="PointInfo">
              <el-select v-model="pointForm.PointInfo" placeholder="请选择目标点类型" :style="{width: '100%'}">
                <el-option v-for="(item, index) in PointInfoOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地形图模式" prop="Terrain">
              <el-select v-model="pointForm.Terrain" placeholder="请选择地形图模式" :style="{width: '100%'}">
                <el-option v-for="(item, index) in TerrainOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="步态" prop="Gait">
              <el-select v-model="pointForm.Gait" placeholder="请选择步态" :style="{width: '100%'}">
                <el-option v-for="(item, index) in GaitOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="速度" prop="Speed">
              <el-select v-model="pointForm.Speed" placeholder="请选择速度" :style="{width: '100%'}">
                <el-option v-for="(item, index) in SpeedOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身体高度" prop="Posture">
              <el-select v-model="pointForm.Posture" placeholder="请选择身体高度" clearable :style="{width: '100%'}">
                <el-option v-for="(item, index) in PostureOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="运动方式" prop="Manner">
              <el-select v-model="pointForm.Manner" placeholder="请选择运动方式" :style="{width: '100%'}">
                <el-option v-for="(item, index) in MannerOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="避障功能" prop="ObsMode">
              <el-select v-model="pointForm.ObsMode" placeholder="请选择避障功能" clearable :style="{width: '100%'}">
                <el-option v-for="(item, index) in ObsModeOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="导航方式" prop="NavMode">
              <el-select v-model="pointForm.NavMode" placeholder="请选择导航方式" clearable :style="{width: '100%'}">
                <el-option v-for="(item, index) in NavModeOptions" :key="index" :label="item.label"
                           :value="item.value" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <!--          <el-col :span="12" v-if="pointForm.PointInfo == 1">-->
          <!--            <el-form-item label="等待时间(S)" prop="WaitTime">-->
          <!--              <el-input v-model="pointForm.WaitTime" placeholder="请输入在点位的等待时间" clearable></el-input>-->
          <!--            </el-form-item>-->
          <!--          </el-col>-->
          <!--          <el-col :span="12" v-if="pointForm.PointInfo == 1">-->
          <!--            <el-form-item label="点到失败等待时间(S)" prop="NoNamedTime">-->
          <!--              <el-input v-model="pointForm.NoNamedTime" placeholder="请输入点到失败后增加的等待时间" clearable></el-input>-->
          <!--            </el-form-item>-->
          <!--          </el-col>-->
        </el-form>
      </el-row>
      <el-row :gutter="15" v-if="pointForm.PointInfo == 1">
        <el-form ref="actionFormRef" :model = "ActionList" size="medium" label-width="100px">
          <el-form-item>
            <el-button type="primary" circle icon="el-icon-plus" @click="addActionSelect"></el-button>
          </el-form-item>
          <!-- 动态生成的动作select和删除按钮 -->
          <div v-for="(action, index) in ActionList.Action" :key="index">
            <el-form-item label="动作选择">
              <el-select
                v-model="action.ActionId"
                placeholder="请选择动作"
                style="width: 300px;"
                @change="onActionChange(action,index)"
              >
                <el-option
                  v-for="option in actionOptions"
                  :key="option.ActionId"
                  :label="option.ActionName"
                  :value="option.ActionId"
                ></el-option>
              </el-select>
              <!-- 删除按钮 -->
              <el-button
                type="danger"
                circle
                icon="el-icon-minus"
                @click="removeActionSelect(index)"
                style="margin-left: 10px;"
              ></el-button>
            </el-form-item>

            <!-- 动态生成的输入框 -->
            <div v-if="action.ActionParams.length > 0">
              <el-form-item
                v-for="(param, i) in action.ActionParams"
                :key="i"
                :label="`输入 ${param.name}`"
                :rules="getValidationRules(param.type)"
              >
                <el-input v-model="param.content" />
              </el-form-item>
            </div>
          </div>
        </el-form>
      </el-row>
      <div slot="footer">
        <el-button @click="close">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </div>
    </el-dialog>
    <!--    展示某个点被选中的所有次数-->
    <el-dialog :visible.sync="pointShowOpen" width="400px" :title="pointShowTitle">
      <div v-for="(item, index) in dialogPointShow" :key="index">
        <span>路径中第{{item.Value}}个点</span>
        <el-button @click="deletePoint(item)">删除</el-button>
        <el-button @click="updatePoint(item)">修改</el-button>
      </div>
      <div slot="footer">
        <el-button @click="chooseAdd">新增</el-button>
        <el-button type="primary" @click="closePointShow">确认</el-button>
      </div>
    </el-dialog>
    <!--    配置任务名称的弹窗-->
    <el-dialog :visible.sync="LineNameOpen" draggable width="600px" :close-on-click-modal="false" title="请为该路径命名" >
      <el-row :gutter="15" >
        <el-form ref="dataRef" :model = "data" size="medium" :rules="rules" label-width="100px">
          <el-col :span="24">
            <el-form-item label="路径名称" prop="LineName">
              <el-input v-model="data.LineName" placeholder="请输入路径名称" :maxlength="20" :style="{width: '100%'}" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="是点名路径" prop="LineType">
              <el-switch v-model="switchValue" active-color="#13ce66" inactive-color="#969797"></el-switch>
            </el-form-item>
          </el-col>
        </el-form>
      </el-row>
      <div slot="footer">
        <el-button @click="closeNamed">取消</el-button>
        <el-button type="primary" @click="confirmNamed">确认</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { getRouteInfoById,searchAllAction,getMapPointbyId } from '@/api/task/taskRoute'
import { getMapIdOptions } from '@/api/robot/point'
import {getMapInfoById} from '@/api/robot/map'
import {updateRoute} from "../../../api/task/taskRoute";
export default {
  name: "updateRoute",
  data() {
    return {
      switchValue: false,
      //查看点在路线中的值
      showPoint: undefined,
      pointShowOpen: false,
      dialogPointShow: [],
      pointShowTitle: '',
      //存储这个路径的信息
      data: {
        LineId: undefined,
        LineType: 0,
        LineName: undefined,
        MapID: undefined,
        MapName: undefined,
        pointInfos:[],
        actionAllList: [],
      },
      //运动配置的表单(要传后端data中的actionAllList)
      ActionList: {
        PointName: undefined,
        PointId: undefined,
        Action:[],
      },
      // 默认选中的运动类型
      selectAction: {},
      //查找到的运动列表
      actionOptions: [],
      MapIdOptions: [],
      //地图的图片
      // imageSrc: `${process.env.VUE_APP_BASE_API}/mapImages/1.jpg`,
      imageSrc: ``,
      X0: undefined,
      Y0: undefined,
      //图片本身大小
      mapHeight: 0,
      mapWidth: 0,
      //在页面上实际显示大小
      displayWidth: 0,
      displayHeight: 0,
      //获取到的点的列表(坐标是（x，y）)
      pointList: [],
      //被选中的点的集合
      selectedPoints: [],
      //被选中的点的运动集合
      selectPointsActions: [],
      //弹窗
      open: false,
      LineNameOpen: false,
      title:'',
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
      lines: [],
      //对点进行配置
      PointInfoOptions: [
        {"label": "过渡点",
          "value": 0
        }, {
          "label": "任务点",
          "value": 1
        }, {
          "label": "充电点",
          "value": 3
        }, {
          "label": "站立点",
          "value": 2
        }
      ],
      TerrainOptions: [
        {
          "label": "实心",
          "value": 0
        }, {
          "label": "镂空",
          "value": 1
        }, {
          "label": "累积帧",
          "value": 3
        }
      ],
      GaitOptions: [
        {
          "label": "行走步态",
          "value": 0
        }, {
          "label": "复杂路面越障步态",
          "value": 1
        }, {
          "label": "斜坡/防滑步态",
          "value": 2
        }, {
          "label": "感知楼梯步态",
          "value": 4
        }, {
          "label": "累积帧楼梯步态",
          "value": 6
        }, {
          "label": "累积帧45°楼梯步态",
          "value": 7
        }
      ],
      SpeedOptions: [
        {
          "label": "正常",
          "value": 0
        }, {
          "label": "低俗",
          "value": 1
        }, {
          "label": "高速",
          "value": 2
        }
      ],
      PostureOptions: [
        {
          "label": "正常高度",
          "value": 0
        }, {
          "label": "匍匐高度",
          "value": 1
        }
      ],
      MannerOptions: [
        {
          "label": "前进行走",
          "value": 0
        }, {
          "label": "倒退行走",
          "value": 1
        }
      ],
      ObsModeOptions: [
        {
          "label": "开启",
          "value": 0
        }, {
          "label": "关闭",
          "value": 1
        }
      ],
      NavModeOptions: [
        {
          "label": "直线导航",
          "value": 0
        }, {
          "label": "自主导航",
          "value": 1
        }
      ],
      isInitialized: false,
    }
  },
  async created() {
    this.data.LineId = this.$route.params.id;
    this.data.MapID = this.$route.params.MapID;
    await this.getActionList()
    await this.getRouteInfo()
    await this.getMapIdOptions()
    this.isInitialized = true;
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
  },
  methods: {
    //根据传来的LineId获取信息
    async getRouteInfo() {
      return getRouteInfoById(this.data.LineId).then(res => {
        // console.log(res)
        let data = res.data[0]
        this.data.LineId = data.LineInfo.LineId
        this.data.LineName = data.LineInfo.LineName
        this.data.LineType = data.LineInfo.LineType
        this.data.MapID = data.LineInfo.MapID
        this.data.MapName = data.LineInfo.MapName
        this.data.pointInfos = data.pointInfos
        this.selectedPoints = this.data.pointInfos
        this.data.actionAllList = data.actionAllList
        this.selectPointsActions = this.getselectPointsActions(data.actionAllList)
        return res;
      })
    },
    getselectPointsActions(actionAllList){
      let selectPointsActions = actionAllList.map(point => {
        let transformedActions = []
        if (point.ActionList !== null && point.ActionList.length !== 0) {
          transformedActions = point.ActionList.map(action => {
            let paramsArray = []
            let selectAction = this.actionOptions.find(a => a.ActionId === action.ActionId)
            let selectParams = selectAction.ActionParams
            if (selectParams.length !== 0) {
              //如果有参数的话
              const contentArray = action.ActionParams.split(',')
              paramsArray = selectParams.map((param,index) => ({
                content: contentArray[index],
                name: param.name,
                type: param.type
              }))
            }
            return {
              ActionId: action.ActionId,
              ActionName: action.ActionName,
              ActionUrl: action.ActionUrl,
              ActionParams: paramsArray,
              ActionString: action.ActionString,
            }
          })
        } else {
          transformedActions = []
        }
        return {
          PointId:point.PointId,
          PointName: point.PointName,
          Value: point.Value,
          Action: transformedActions,
        }
      })
      return selectPointsActions
    },
    async getMapIdOptions() {
      const res = await getMapIdOptions()
      this.MapIdOptions = res.data.map(item=>({
        value: item.MapID,
        label: item.MapName,
        X0: item.X0,
        Y0: item.Y0,
      }))
      let selectedMapInfo = this.MapIdOptions.find(m => m.value === this.data.MapID)
      this.imageSrc = `${process.env.VUE_APP_BASE_API.replaceAll("'", '')}/maps/${selectedMapInfo.label}.jpg`
      this.X0 = selectedMapInfo.X0
      this.Y0 = selectedMapInfo.Y0
    },
    //尺寸
    async getMapSize() {
      this.$nextTick(() => {
        let img = this.$refs.mapImage;
        if (img) {
          this.mapWidth = img.naturalWidth;
          this.mapHeight = img.naturalHeight;
          // 获取图片在页面上的实际显示大小
          this.displayWidth = img.clientWidth;
          this.displayHeight = img.clientHeight;
          //有了图片的长度宽度信息后获取xy的值
          this.selectedPoints = this.changeCoordinate(this.data.pointInfos)
          this.data.pointInfos = this.selectedPoints
          // console.log(this.data.pointInfos)
          // console.log(this.selectedPoints)
          this.updateLines()
          this.getPointList(this.data.MapID)
        }
      })
    },
    //根据MapID获取pointList(更新尺寸)
    getPointList(MapID) {
      getMapPointbyId(MapID).then(res=>{
        this.pointList = this.changeCoordinate(res.data)
      })
    },
    //根据X0,Y0获得x,y的值
    changeCoordinate(array) {
      array = array.map(item =>{
        let PixelX = (item.PosX - (this.X0))/(0.1)
        let x = PixelX / this.mapWidth * 100
        let PixelY = this.mapHeight - (item.PosY - (this.Y0))/(0.1)
        let y = PixelY / this.mapHeight * 100
        return {
          ...item,
          x: x,
          y: y
        };
      })
      return array
    },
    //获取每个点的位置样式
    getPointStyle(point) {
      return {
        left: point.x+'%',
        top: point.y+'%',
      }
    },
    //根据pointInfos来连接线条
    updateLines() {
      this.lines = []; // 重置线条数组
      // 将点按 Value 排序
      // console.log('ffff')
      const sortedPoints = [...this.selectedPoints].sort((a, b) => a.Value - b.Value);
      // console.log(this.selectedPoints)
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
        // console.log(this.lines)
      }
    },
    //获取动作列表
    getActionList() {
      searchAllAction().then(res=> {
        let paramsArray = []
        this.actionOptions = res.data.map(item => {
          if (item.ActionParams != "" && item.ActionParams !== null) {
            paramsArray = item.ActionParams.split(';').map(param => {
              let [type, name] = param.split(',')
              return {type: type.trim(),name: name.trim(),content:this.$set({},'content',undefined)}
            });
          } else {
            paramsArray = []
          }
          return {
            ActionId: item.ActionId,
            ActionName: item.ActionName,
            ActionUrl: item.ActionUrl,
            ActionParams: paramsArray
          }
        })
      })
    },
    // 新增动作选择
    addActionSelect() {
      this.ActionList.Action.push({
        ActionParams: [],
      });
    },
    // 删除动作选择
    removeActionSelect(index) {
      this.ActionList.Action.splice(index, 1);
    },
    //点击点的操作
    showPointConfig(point) {
      this.showPoint = point
      //判断是不是已经加过的点如果找到匹配的点对象，则 selectedPoint 会存储该对象；如果没有找到，则 selectedPoint 为 undefined
      const selectedPoint = this.selectedPoints.find(p => p.PointId === point.PointId);
      // const selectedPointAction = this.selectPointsActions.find(a => a.PointId === point.PointId)
      if (selectedPoint)  {
        //有这个点，则搜索所有在数组中的点相同的点，展示弹窗，每个弹窗中有value、删除、修改
        this.dialogPointShow = this.selectedPoints.filter(p => p.PointId === point.PointId);
        this.pointShowTitle = '点 '+selectedPoint.PointName+' 在路径中的配置'
        this.pointShowOpen = true
      } else {
        this.addPoint(this.showPoint)
      }
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
      this.title = "修改"+ item.PointName+'配置'
    },
    chooseAdd() {
      this.pointShowOpen = false
      this.addPoint(this.showPoint)
    },
    closePointShow() {
      this.pointShowOpen = false
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
    // 选择动作时更新参数
    onActionChange: function (action, index) {
      // 查找所选动作在 actionOptions 中对应的 action
      const selectedAction = this.actionOptions.find(option => option.ActionId === action.ActionId);
      // 更新 selectedAction 和 ActionParams
      this.$set(this.ActionList.Action, index, {
        selectedAction: selectedAction.ActionId,
        ActionId: selectedAction.ActionId,
        ActionName: selectedAction.ActionName,
        ActionUrl: selectedAction.ActionUrl,
        ActionParams: selectedAction.ActionParams.map(param => ({
          ...param,
          content: '' // 初始化 content，确保为响应式
        }))
      });
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
    close() {
      this.open = false
      this.reset()
      this.pointForm.PointId = undefined
      this.pointForm.PointName = undefined
      this.pointForm.MapID = undefined
      this.pointForm.PosX = undefined
      this.pointForm.PosY = undefined
      this.pointForm.PosZ = undefined
      this.pointForm.AngleYaw = undefined
      this.pointForm.Value = undefined
      this.pointForm.x = 0
      this.pointForm.y = 0
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
        // this.ActionList={
        //   PointName: undefined,
        //     PointId: undefined,
        //     Action:[],
        // }
      } else {
        //新增
        //这里是一个深拷贝
        const newPointForm = JSON.parse(JSON.stringify(this.pointForm));
        newPointForm.Value = this.selectedPoints.length + 1;
        this.selectedPoints.push(newPointForm);

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
    closePage() {
      this.$router.push({ path:"/task/taskRoute" })
    },
    confirmRoute() {
      this.LineNameOpen = true
    },
    closeNamed() {
      this.LineNameOpen = false
    },
    confirmNamed() {
      this.$refs.dataRef.validate(valid => {
        if (valid) {
          let actionAll = this.selectPointsActions.map(point => {
            let transformedActions = point.Action.map(action => {
              let paramsContent = action.ActionParams.map(param => param.content).join(',')
              return {
                ActionId: action.ActionId,
                ActionName: action.ActionName,
                ActionUrl: action.ActionUrl,
                ActionParams: paramsContent
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
          updateRoute(this.data).then(res=> {
            this.LineNameOpen = false
            const obj = { path: "/task/taskRoute" };
            this.$tab.closeOpenPage(obj);
            // this.$router.push({ path:"/task/taskRoute" })
          })
        }
      })
    },
  }
}
</script>

<style>
.image-selector {
  position: relative;
  height: 100%;
  width: 100%;
  overflow: hidden;
  /*padding-top: 50px;*/
}

.image-select {
  position: absolute;
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
  height: 100%;
  width: 100%;
  /*object-fit: contain;*/
}

.point-marker {
  position: absolute;
  width: 6px;
  height: 6px;
  background-color: #4d995f;
  border-radius: 50%;
  cursor: pointer;
}
.line-container {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
</style>
