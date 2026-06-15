<template>
  <div class="app-container" v-loading="loading">
    <el-form ref="form" :model="form" :rules="rules" label-width="130px" :disabled="disabled" class="h100 ovya">
    <el-row :gutter="20" class="mr0 ml0">
      <el-col :span="16">
        <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">路径基础信息</div>
        <el-row>
          <el-col :span="12">
            <el-form-item label="路径名称" prop="pathName" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
              <el-input :disabled="disabled" v-model="form.pathName" placeholder="请输入路径名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务场景" prop="sceneId" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
              <el-select class="w100" :disabled="disabled" v-model="form.sceneId" @change="handleChangeScene" placeholder="请选择业务场景" clearable style="width: 100%;">
                <el-option
                  v-for="dict in obj.sceneList"
                  :key="dict.sceneId"
                  :label="dict.sceneName"
                  :value="dict.sceneId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="无人装备" prop="equipmentId" :rules="{ required: true, message: '不允许为空', trigger: 'blur' }">
              <el-select class="w100" :disabled="disabled" v-model="form.equipmentId" @change="handleChangeEquipment" placeholder="请选择无人装备" clearable style="width: 100%;">
                <el-option
                  v-for="dict in obj.equipmentList"
                  :key="dict.id"
                  :label="dict.name"
                  :value="dict.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input :disabled="disabled" v-model="form.remark" type="textarea" :rows="5" placeholder="请输入内容" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="创建时间" prop="createTime">
              <el-input v-model="form.createTime" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="创建人" prop="createBy">
              <el-input v-model="form.createBy" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="18">
            <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">地图信息</div>
            <div class="dtxx">
              <!-- <div class="operation-btn">
                <el-button type="primary" @click="submitForm">添加点位</el-button>
                <el-button type="primary" @click="publish">结束添加</el-button>
              </div> -->
              <!-- <img src="./../../assets/images/bi/map-center.png" alt="请上传地图文件" width="85%" height="auto" class="mt20" /> -->
              <point-select :obj="obj" :pointTabsValue="pointTabsValue" @getPointList="getPointList" @getPointInfo="getPointInfo" @getAddStatus="getAddStatus" :disabled="disabled" :initedInfo="initedInfo" ref="pointSelectRef" />
            </div>
          </el-col>
          <el-col :span="6" class="h100" style="min-height: 500px;">
            <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">路径点信息</div>
            <div class="height: calc(100% - 35px)" v-if="pointTabs.length">
              <el-tabs
                v-model="pointTabsValue"
                type="card"
                class="demo-tabs"
                :closable="!disabled"
                @tab-remove="removeTab"
                tabPosition="left"
                @tab-click="handleClickTab"
              >
                <el-tab-pane
                  v-for="(item, index) in pointTabs"
                  :key="item.pointId"
                  :name="`posX${item.posX}posY${item.posY}`"
                >
                  <!-- {{ item.content }} -->
                    <template #label>
                      <!-- <div class="w100 text-ellipsis">{{ '路径点' + (index + 1) }}[{{ item.posX + ',' + item.posY }}]</div> -->
                      <div class="w100 text-ellipsis">{{ item.pointName || ('路径点' + (index + 1)) }}[{{ item.posX + ',' + item.posY }}]</div>
                    </template>
                </el-tab-pane>
              </el-tabs>
            </div>
          </el-col>
        </el-row>
      </el-col>
      <el-col :span="8" class="flx-center h100">
        <!-- <el-drawer size="280px" :visible="visible" :with-header="false" :append-to-body="true" :show-close="false"></el-drawer> -->
        <el-drawer
          :visible="drawerVisible"
          title=""
          :with-header="false"
          :append-to-body="true"
          :before-close="handleClose"
          size="23%"
        >
          <div v-for="(point, index) in form.points" :key="index" class="pr20 pl20">
            <!-- <template #header="{ close, titleId, titleClass }"> -->
            <div class="mt20" v-if="tabIndex === index">
              <h4 title="点击重命名" v-if="!showEditPointName" @click="editPointName(point.pointName)">{{ point.pointName || drawerTitle }}</h4>
              <el-form-item
                class="mt20"
                v-if="showEditPointName"
                label="" label-width="0"
                :prop="'points.' + index + '.pointName'"
                :rules="[{
                  validator: handleValidatePointName,
                  trigger: ['blur'],
                }]"
              >
                <el-input v-model="point.pointName" placeholder="请输入路径点名称" @blur="handleChangePointName" class="w80" />
              </el-form-item>
            <!-- </template> -->
             <!-- v-if="index === tabIndex" -->
              <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">无人装备参数配置</div>
              <el-form-item label="目标点类型" :prop="'points.' + index + '.pointInfo'"
                :rules="{
                  required: true,
                  message: '不允许为空',
                  trigger: 'blur',
                }"
              >
                <el-select class="w80" :disabled="point.pointInfo === 3" v-model="point.pointInfo" placeholder="请选择目标点类型">
                  <el-option
                    v-for="dict in dictss.pointType"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="步态" :prop="'points.' + index + '.gait'">
                <el-select class="w80" v-model="point.gait" placeholder="请选择步态">
                  <el-option
                    v-for="dict in dictss.gaitType"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="速度" :prop="'points.' + index + '.speed'">
                <el-select class="w80" v-model="point.speed" placeholder="请选择速度">
                  <el-option
                    v-for="dict in dictss.speedType"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="运动方式" :prop="'points.' + index + '.manner'">
                <el-select class="w80" v-model="point.manner" placeholder="请选择运动方式">
                  <el-option
                    v-for="dict in dictss.mannerType"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="导航方式" :prop="'points.' + index + '.navMode'">
                <el-select class="w80" v-model="point.navMode" placeholder="请选择导航方式">
                  <el-option
                    v-for="dict in dictss.navType"
                    :key="dict.value"
                    :label="dict.label"
                    :value="dict.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="避障模式" :prop="'points.' + index + '.obsMode'">
                <el-select class="w80" v-model="point.obsMode" placeholder="请选择避障模式">
                  <el-option label="开启" :value="0" />
                  <el-option label="关闭" :value="1" />
                </el-select>
              </el-form-item>
              <el-form-item label="点位停留时长" :prop="'points.' + index + '.waitTime'"
                :rules="{
                  required: true,
                  message: '不允许为空',
                  trigger: 'blur',
                }"
              >
                <el-input-number v-model="point.waitTime" placeholder="请输入点位停留时长，0-300" :min="0" :max="300" />（秒）
              </el-form-item>
              <template v-if="obj.topEquipment.length">
                <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">上装设备参数配置</div>
                <template v-if="hasTopEquipment('video')">
                  <div style="margin-bottom: 15px; color: #666; font-size: 18px; line-height: 20px">{{ selectDictLabel(dict.type.qh_mounted_equipment_type, 'video') }}</div>
                  <el-form-item label="是否开启" :prop="'points.' + index + '.videoOpen'"
                    :rules="{
                      required: true,
                      message: '不允许为空',
                      trigger: 'blur',
                    }"
                  >
                    <el-select class="w80" v-model="point.videoOpen" placeholder="请选择">
                      <el-option label="开启" :value="0" />
                      <el-option label="关闭" :value="1" />
                      <el-option
                        v-for="dict in dict.type.sys_yes_no"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="扫描方式" :prop="'points.' + index + '.videoScan'"
                    :rules="{
                      required: true,
                      message: '不允许为空',
                      trigger: 'blur',
                    }"
                  >
                    <el-select class="w80" v-model="point.videoScan" placeholder="请选择扫描方式">
                      <el-option
                        v-for="dict in dictss.videoScanType"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="旋转速度" :prop="'points.' + index + '.videoRotateSpeed'"
                    :rules="{
                      required: true,
                      message: '不允许为空',
                      trigger: 'blur',
                    }"
                  >
                    <el-input v-model="point.videoRotateSpeed" :disabled="point.videoOpen === 1 || point.videoScan === 0" placeholder="请输入旋转速度" class="wp180" />（度/秒）
                  </el-form-item>
                  <el-form-item label="算法使用" :prop="'points.' + index + '.videoAlg'">
                    <el-checkbox-group v-model="point.videoAlg">
                      <el-checkbox
                        v-for="dict in dict.type.qh_mounted_equipment_config_algorithm_used"
                        :key="dict.value"
                        :label="dict.value"
                      >
                        {{ dict.label }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </template>
                <template v-if="hasTopEquipment('audio')">
                  <div style="margin-bottom: 15px; color: #666; font-size: 18px; line-height: 20px">{{ selectDictLabel(dict.type.qh_mounted_equipment_type, 'audio') }}</div>
                  <el-form-item label="是否开启" :prop="'points.' + index + '.audioOpen'"
                    :rules="{
                      required: true,
                      message: '不允许为空',
                      trigger: 'blur',
                    }"
                  >
                    <el-select class="w80" v-model="point.audioOpen" placeholder="请选择">
                      <!-- <el-option label="开启" :value="0" />
                      <el-option label="关闭" :value="1" /> -->
                      <el-option
                        v-for="dict in dict.type.sys_yes_no"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                      />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="算法使用" :prop="'points.' + index + '.audioAlg'">
                    <!-- <el-select class="w80" v-model="point.audioAlg" placeholder="请选择">
                      <el-option
                        v-for="dict in dictss.audioAlgType"
                        :key="dict.value"
                        :label="dict.label"
                        :value="dict.value"
                      />
                    </el-select> -->
                    <el-checkbox-group v-model="point.audioAlg">
                      <el-checkbox
                        v-for="dict in dict.type.qh_mounted_equipment_config_audio_algorithm_used"
                        :key="dict.value"
                        :label="dict.value"
                      >
                        {{ dict.label }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </el-form-item>
                </template>
              </template>
              <div style="margin-bottom: 15px; color: #333; font-weight: 700; font-size: 18px; line-height: 20px">路径配置</div>
              <el-form-item label="是否等待告警处理" :prop="'points.' + index + '.alarmWaitEnabled'" label-width="144px"
                :rules="{
                  required: true,
                  message: '不允许为空',
                  trigger: 'blur',
                }"
              >
                <el-select class="w80" v-model="point.alarmWaitEnabled" placeholder="请选择">
                  <el-option label="是" value="Y" />
                  <el-option label="否" value="N" />
                </el-select>
              </el-form-item>
            </div>
          </div>
        </el-drawer>
      </el-col>
    </el-row>
    <el-form-item label="" class="mt20">
      <el-button type="primary" @click.native.prevent="submitForm()">保存路径</el-button>
      <el-button @click="cancel" :disabled="false">取 消</el-button>
      <!-- <el-button type="primary" @click="submitForm('1')">发布路径</el-button> -->
    </el-form-item>
  </el-form>
  </div>
</template>

<script>
import { parsePGM } from "../../utils/pgm";
import PointSelect from './point-select.vue'
import { listScene } from "../../api/rsp/scene";
import { line } from "./line";
import { addPath, getPath, updatePath } from "../../api/rsp/path";
import { changeDate } from "../../utils";

export default {
  name: "PathAdd",
  dicts: ['sys_yes_no', 'qh_mounted_equipment_type', 'qh_mounted_equipment_config_algorithm_used', 'qh_mounted_equipment_config_audio_algorithm_used'],
  components: { PointSelect },
  data() {
    return {
      // 遮罩层
      loading: false,
      // 表单参数
      form: {
        pathName: '',
        equipmentId: '',
        sceneId: '',
        remark: '',
        createTime: null,
        createBy: null,
        points: [
          // {
          //   // 无人装备参数
          //   pointInfo: 0,// 目标点类型
          //   gait: 0,// 步态
          //   speed: 0,// 速度
          //   manner: 0,//运动方式
          //   navMode: 0,//导航方式
          //   obsMode: 0,//避障模式
          //   // 上装设备参数
          //   // 视屏图像
          //   videoOpen: 0,//是否开启
          //   videoAlg: 0,//算法使用
          //   videoScan: 0,//扫描方式
          //   videoRotateSpeed: 0,//旋转速度
          //   // 双向语音对讲
          //   audioOpen: 0,//是否开启
          //   audioAlg: 0,//算法使用
          //   pointId: undefined,
          //   pointName: undefined,
          //   value: undefined,//顺序
          //   mapID: 1,
          //   posX: 0.0,
          //   posY: 0.0,
          //   posZ: 0.0,
          //   angleYaw: 0.0,
          //   terrain: 0,
          //   posture: 0,
          //   x: 0,
          //   y: 0,
          //   waitTime:0
          // }
        ]
      },
      // 表单校验
      rules: {
      },
      formDisabled: false,
      previewUrl: '',
      dictss: {
        pointType: [
          { label: '过渡点', value: 0 },
          { label: '任务点', value: 1 },
          { label: '站立点', value: 2 },
          { label: '充电点', value: 3 }
        ],
        gaitType: [
          { label: '行走步态', value: 0 },
          { label: '复杂路面越障步态', value: 1 },
          { label: '斜坡/防滑步态', value: 2 },
          { label: '感知楼梯步态', value: 4 },
          { label: '累积帧楼梯步态', value: 6 },
          { label: '累积帧45°楼梯步态', value: 7 },
        ],
        speedType: [
          { label: '正常', value: 0 },
          { label: '低速', value: 1 },
          { label: '高速', value: 2 },
        ],
        mannerType: [
          { label: '前进行走', value: 0 },
          { label: '倒退行走', value: 1 }
        ],
        navType: [
          { label: '直线导航', value: 0 },
          { label: '自主导航', value: 1 },
        ],
        videoAlgType: [
          { label: '人脸识别', value: 0 },
          { label: '人员对比', value: 1 },
          { label: '火灾识别', value: 2 },
        ],
        videoScanType: [
          { label: '固定角度', value: 0 },
          { label: '旋转扫描', value: 1 },
          // { label: '火灾识别', value: 2 },
        ],
        audioAlgType: [
          { label: '语音识别', value: 0 },
          { label: '关键词监测', value: 1 }
        ],
      },
      pointTabsValue: '',
      tabIndex: 0,
      pointTabs: [
        // {
        //   pointName: '测试1',
        //   pointId: 1
        // },
        // {
        //   pointName: '测试2',
        //   pointId: 2
        // }
      ],
      drawerTitle: '',
      showEditPointName: false,
      drawerVisible: false,
      obj: {
        sceneList: [],//场景下拉
        equipmentList: [],//装备下拉
        mapInfo: null,//地图
        points: [],//地图上的点位
        topEquipment: []//上装设备
      },
      pathId: this.$route.query.pathId,
      disabled: false,
      initedInfo: {},
      id: 1,
      editName: ''
    };
  },
  async created() {
    if (!this.pathId) {
      this.getSceneList()
    }
  },
  async mounted() {
    // this.pathId = this.$route.query.id
    if (this.pathId) {
      await this.getSceneList()
      this.disabled = this.$route.query.r === 't'
      getPath(this.$route.query.pathId).then(res => {
        console.log((res.data));
        // this.form = response.data;
        // this.qhPointList = response.data.qhPointList;
        const { pathName, pathState, sceneId, equipmentId, remark, qhPointList, createBy, createTime } = res.data
        if (qhPointList.length) {
          let pointIdArr = []
          const newPoints = qhPointList.map(item => {
            console.log('new', item);
            
            const { pointName, angleYaw, mapID, posX, posY, posZ, value, pointId } = item
            // const { gait, manner, navMode, obsMode, pointInfo, remark, speed, terrain, waitTime } = item.qhMotionEquipmentConfig
            const { algorithmUsed, isActive, rotationSpeed, scanMode } = item.qhVideoEquipmentConfig
            const obj = {
              customId: Date.now(),
              angleYaw, mapID, posX, posY, posZ, value, pointId,
              ...item.qhMotionEquipmentConfig,
              audioAlg: item.qhAudioEquipmentConfig.algorithmUsed.toString().split(','),
              audioOpen: item.qhAudioEquipmentConfig.isActive,
              videoAlg: algorithmUsed.split(','),
              videoOpen: isActive,
              videoRotateSpeed: rotationSpeed,
              videoScan: scanMode,
              pointName
            }
            pointIdArr.push(`posX${posX}posY${posY}`)            
            return obj
          })
          this.pointTabs = newPoints.map(item => {
            return { ...item, name: `posX${item.posX}posY${item.posY}` }
          })
          console.log(this.pointTabs);
          this.handleChangeScene(sceneId)
          this.handleChangeEquipment(equipmentId)          
          this.form = { ...res.data, points: newPoints }
          const linePoints = []
          setTimeout(() => {
            this.obj.points
            const markers = document.getElementsByClassName('point-marker')
            console.log('markers', markers);
            
            if (!markers.length) return
            console.log(pointIdArr)
            for (let index = 0; index < markers.length; index++) {
              const item = markers[index];
              // console.log(this.obj.points[index])
              // console.log(item.clientWidth, item.clientHeight, rect);
              // linePoints.push({ x: item.clientWidth - rect.left, y: item.clientHeight - rect.top })
              const { posX, posY } = this.obj.points[index]
              if (pointIdArr.includes(`posX${posX}posY${posY}`)) {
                linePoints.push({ x: item.offsetLeft, y: item.offsetTop })
              }
              if (index === markers.length - 1) {                
                this.$refs.pointSelectRef.$refs.lineRef.points = linePoints
                this.$refs.pointSelectRef.$refs.lineRef.redrawCanvas()
              }
            }
          }, 2000);

        }
      });
    } else {
      this.$set(this.form, 'createTime', changeDate(new Date()))
      this.$set(this.form, 'createBy', this.$store.getters.nickName)
    }
  },
  methods: {
    hasTopEquipment(type) {
      const arr = this.obj.topEquipment.filter(item => item.type === type)      
      return arr.length ? arr[0] : false
    },
    async getSceneList() {
      this.loading = true;
      await listScene({ sceneState: '1' }).then(response => {
        console.log('getSceneList');
        this.obj.sceneList = response.rows;
        this.loading = false;
      });
    },
    handleChangeScene(e) {
      this.form.equipmentId = null
      if (e === '') return
      
      const curSceneObj = this.obj.sceneList.filter(item => item.sceneId === e)[0]
      this.obj.equipmentList = curSceneObj.qhMotionEquipmentList
    },
    handleChangeEquipment(e) {
      if (e === '') {
        this.obj.mapInfo = {}
        this.obj.points = []
        this.obj.topEquipment = []
        // this.form.points = []
        this.form.points.splice(0)
      console.log('改变了');
        this.pointTabs = []
        return
      }
      const curEquipmentObj = this.obj.equipmentList.filter(item => item.id === e)[0]
      const mapInfo = curEquipmentObj.qhMapInfo
      const topEquipment = curEquipmentObj.qhMountedEquipmentList
      this.obj.mapInfo = mapInfo
      this.obj.points = mapInfo.points
      this.obj.topEquipment = topEquipment
    },
    // 取消按钮
    cancel() {
      this.reset();
      history.back(-1)
    },
    // 表单重置
    reset() {
      this.form = {};
      this.resetForm("form");
    },
    editPointName(pointName) {
      this.showEditPointName = true
      this.form.points[this.tabIndex].pointName = pointName || this.drawerTitle
      this.editName = pointName || this.drawerTitle
    },
    handleValidatePointName(rule, value, callback) {
      if (value) {
        if (this.pointTabs.filter(item => item.pointName === value && item.pointName !== this.editName).length) {
          callback(new Error('点位名称重复，请重新输入'))
        } else {
          this.showEditPointName = false
          this.pointTabs[this.tabIndex].pointName = value
          callback()
        }
      } else {
        this.form.points[this.tabIndex].pointName = this.drawerTitle
        this.showEditPointName = false
        callback()
      }
    },
    // 修改路径点名称
    handleChangePointName(e) {
      // this.showEditPointName = false
    },
    // 发布
    publish() {},
    /** 提交按钮 */
    submitForm(status) {
      console.log('提交submitForm');
      this.$refs["form"].validate(valid => {
        if (valid) {
          const { pathName, sceneId, equipmentId, remark, createBy } = this.form
          const obj = {
            pathName,
            pathState: status || '0',
            sceneId,
            equipmentId,
            remark,
            qhPointList: [],
            createBy
          }
          const points = this.form.points.map((item, index) => {
            
            const { pointName, angleYaw, mapID, posX, posY, posZ, gait, manner, navMode, obsMode, pointInfo, remark, speed, terrain, waitTime, alarmWaitEnabled } = item
            const qhAudioEquipmentConfig = {
              algorithmUsed: item.audioAlg.join(','),
              isActive: item.audioOpen,
              remark: '音频备注'
            }
            const qhMotionEquipmentConfig = { gait, manner, navMode, obsMode, pointInfo, remark, speed, terrain, waitTime: waitTime || 0, alarmWaitEnabled }
            const qhVideoEquipmentConfig = {
              algorithmUsed: item.videoAlg.join(','),
              // id: 'id',
              isActive: item.videoOpen,
              remark: '视频备注',
              rotationSpeed: item.videoRotateSpeed,
              scanMode: item.videoScan
            }
            return {
              mapID: this.obj.mapInfo.id, pointName, angleYaw, mapID, posX, posY, posZ, qhAudioEquipmentConfig, qhMotionEquipmentConfig, qhVideoEquipmentConfig, value: index + 1
            }
          })
          obj.qhPointList = points
          console.log(obj);
          if (this.form.pathId) {
            updatePath({ ...obj, pathId: this.form.pathId }).then(res => {
              if (res.code === 200) {
                this.$modal.msgSuccess(res.msg);
                this.$router.push({ name: 'Path' });
              } else {
                this.$modal.msgSuccess(res.msg);
              }
            });
          } else {
            addPath(obj).then(res => {
              if (res.code === 200) {
                this.$modal.msgSuccess(res.msg);
                this.$router.push({ name: 'Path' });
              } else {
                this.$modal.msgSuccess(res.msg);
              }
            });
          }
        }
      });
    },
    getFileUrl(file) {
      parsePGM(file).then(res => {
        this.loading = false
        this.previewUrl = res
      }).catch(() => {
        this.loading = false
      })
    },
    handleClose() {
      this.drawerVisible = false
      this.showEditPointName = false
    },
    handleClickTab(tab) {
      const tabIndex = Number(tab.index)
      this.tabIndex = Number(tab.index)
      // this.drawerTitle = this.pointTabs[tabIndex].pointName
      // this.form.points[tabIndex] = { ...this.form.points[tabIndex], ...this.pointList[tabIndex] }
      this.drawerTitle = '路径点' + (tabIndex + 1)
      // this.form.points[this.tabIndex].pointName = this.drawerTitle
      // this.form.points[tabIndex] = { ...this.form.points[tabIndex], ...this.pointTabs[tabIndex] }
      // console.log('点击当前===', this.form.points[tabIndex]);
      // const {
      //   // qhMotionEquipmentConfig: { obsMode, navMode, pointInfo, speed, terrain, manner, gait, waitTime },
      //   qhMotionEquipmentConfig,
      //   mapID,posX, posY, posZ, angleYaw, qhAudioEquipmentConfig, qhVideoEquipmentConfig
      // } = this.pointTabs[tabIndex]
      // const newObj = {
      //   ...this.form.points[tabIndex],
      //   ...this.pointTabs[tabIndex],
      //   ...qhMotionEquipmentConfig,
      //   ...qhAudioEquipmentConfig,
      //   ...qhVideoEquipmentConfig }
      // // 更新动态表单
      // this.$set(this.form.points, tabIndex, newObj)
      // console.log('点击了', this.form.points[tabIndex]);
      
      this.drawerVisible = true
    },
    removeTab(targetName) {
      console.log('remove-targetName', targetName)
      // if (this.pathId) return
      const tabs = this.pointTabs
      let activeName = this.pointTabsValue
      // console.log('activeName', targetName, activeName);
      if (activeName === targetName) {
        tabs.forEach((tab, index) => {
          if (tab.name === targetName) {
            const nextTab = tabs[index + 1] || tabs[index - 1]
            if (nextTab) {
              activeName = nextTab.name
            }
          }
          console.log('遍历');
          
        })
      }
      console.log('removeTab', targetName, activeName);
      this.pointTabsValue = activeName
      this.pointTabs = tabs.filter((tab, index) => {
        if (tab.name === targetName) {
          this.form.points.splice(index, 1)
          // this.$refs.pointSelectRef.$refs.lineRef.points.splice(index, 1)
          this.$refs.pointSelectRef.$refs.lineRef.deleteLine(index)
        }
        return tab.name !== targetName
      })
    },
    // 获取点信息
    getPointList(pointList) {
      // console.log('pointList', pointList);
      // this.pointTabs = pointList
      // this.form.points = this.pointTabs.map((item, index) => {
      //   const obj = {
      //     // 无人装备参数
      //     pointInfo: 0,// 目标点类型
      //     gait: 0,// 步态
      //     speed: 0,// 速度
      //     manner: 0,//运动方式
      //     navMode: 0,//导航方式
      //     obsMode: 0,//避障模式
      //     // 上装设备参数
      //     // 视屏图像
      //     videoOpen: 0,//是否开启
      //     videoAlg: 0,//算法使用
      //     videoScan: 0,//扫描方式
      //     videoRotateSpeed: 0,//旋转速度
      //     // 双向语音对讲
      //     audioOpen: 0,//是否开启
      //     audioAlg: 0,//算法使用
      //     pointId: undefined,
      //     pointName: undefined,
      //     value: undefined,//顺序
      //     mapID: 1,
      //     posX: 0.0,
      //     posY: 0.0,
      //     posZ: 0.0,
      //     angleYaw: 0.0,
      //     terrain: 0,
      //     posture: 0,
      //     x: 0,
      //     y: 0,
      //     waitTime:0
      //   }
      //   return { customId: Date.now() + index, ...obj, ...item, ...item.qhMotionEquipmentConfig, ...item.qhMotionEquipmentConfig, ...item.qhAudioEquipmentConfig  }
      // })
    },
    getAddStatus(point, callback) {
      const p = `posX${point.posX}posY${point.posY}`
      this.pointTabs.filter((item, index) => {
        const str = `posX${item.posX}posY${item.posY}`
        if (p === str) {
          callback(index + 1, item)
        }
      })
    },
    // 获取点位信息
    getPointInfo(data) {
      // console.log('getPointInfo=====', data.point, data.index);
      const event = data.event
      const p = `posX${data.point.posX}posY${data.point.posY}`
      this.pointTabsValue = p
      if (!this.pointTabs.filter(item => p === `posX${item.posX}posY${item.posY}`).length) {
        // TODO:修改名称改为跟自动命名一致的问题
        data.point.pointName = data.point.pointName || `路径点${this.pointTabs.length + 1}`
        // data.point.pointName = data.point.pointName || `路径点${this.id}`
        // this.id++
        this.pointTabs.push({ ...data.point, name: `posX${data.point.posX}posY${data.point.posY}` })
        const obj = {
          // 无人装备参数
          pointInfo: 0,// 目标点类型
          gait: 0,// 步态
          speed: 0,// 速度
          manner: 0,//运动方式
          navMode: 0,//导航方式
          obsMode: 0,//避障模式
          // 上装设备参数
          // 视屏图像
          videoOpen: 'Y',//是否开启
          videoAlg: ['0'],//算法使用
          videoScan: 0,//扫描方式
          videoRotateSpeed: 0,//旋转速度
          // 双向语音对讲
          audioOpen: 'Y',//是否开启
          audioAlg: ['0'],//算法使用
          pointId: undefined,
          pointName: undefined,
          value: undefined,//顺序
          mapID: 1,
          posX: 0.0,
          posY: 0.0,
          posZ: 0.0,
          angleYaw: 0.0,
          terrain: 0,
          posture: 0,
          x: 0,
          y: 0,
          waitTime:0,
          alarmWaitEnabled: 'Y'
        }
        this.form.points.push({ customId: Date.now(), ...obj, ...data.point, ...data.point.qhMotionEquipmentConfig, ...data.point.qhMotionEquipmentConfig, ...data.point.qhAudioEquipmentConfig  })
        if (this.pointTabs.length === 1) {
          // this.pointTabsValue = data.point.pointId + ''
        }
        this.$refs.pointSelectRef.$refs.lineRef.createLine(event)
      }
    }
  }
};
</script>

<style lang="scss" scoped>
.dtxx {
  position: relative;
  background: #ccc;
  text-align: center;
  .operation-btn {
    position: absolute;
    top: 20px;
    left: 260px;
    z-index: 1;
  }
  img {
    display: inline-block;
  }
}
::v-deep .el-tabs {
  &.demo-tabs {
    padding: 10px;
    border-radius: 5px;
    border: 1px solid #ccc;
    .el-tabs__header {
      width: 100%;
      margin: 0;
    }
    .el-tabs__content {
      display: none;
    }
    &.el-tabs--card > .el-tabs__header .el-tabs__item {
      padding-right: 20px;
      padding-left: 20px;
      border-radius: 5px;
      background: #f2f2f2;
      border: 1px solid #AAAAAA !important;
      & + .el-tabs__item {
        margin-top: 5px;
      }
      &.is-active {
        color: #666;
        border: 1px solid #0079fe !important;
        box-shadow: none !important;
      }
      .el-icon-close {
        position: absolute;
        top: 8px;
        right: 6px;
        vertical-align: unset;
        &:hover {
          background: #a8abb2;
          color: #fff;
        }
      }
    }
    // .el-tabs--left.el-tabs--card .el-tabs__item.is-left.is-active:first-child
  }
}
</style>
