<template>
  <div class="con p20">
    <el-select v-model="selectedMapId" placeholder="选择地图" class="item-select" @change="changeMap">
      <el-option
        v-for="(image, index) in MapIdOptions"
        :key="index"
        :label="image.label"
        :value="image.value">
      </el-option>
    </el-select>
    <el-select v-model="selectDogId" placeholder="请选择装备" @change="handleChange" style="width: 200px;" class="ml20">
      <el-option
        v-for="dog in dogList"
        :key="dog.id"
        :label="dog.name"
        :value="dog.id"
      />
    </el-select>
    <Index ref="dialogRef" v-if="selectInfo.id" :dogName="selectInfo.name" :dogId="selectInfo.id" class="mt30" />
  </div>
</template>

<script>
import { listMapInfo } from '../../api/rsp/map';
import { listMotion } from '../../api/rsp/motion';
import Index from './../largeScreen/dialog/index.vue'
import Cookies from 'js-cookie'
export default {
  components: { Index },
  data() {
    return {
      selectedMapId: null,
      selectDogId: null,
      MapIdOptions:[],
      dogList: [],
      dogId: '',
      dogName: '',
      currentImage: '',
      selectInfo: {}
    }
  },
  created() {
    this.getMapIdOptions()
  },
  methods: {
    async getMapIdOptions() {
      const res = await listMapInfo()
      this.MapIdOptions = res.rows.map(item=>({
        value: item.id,
        label: item.name,
        X0: item.startXCoordinate,
        Y0: item.startYCoordinate,
        mapFileUrl: item.mapFileUrl
      }))
      // if (this.MapIdOptions.length) {
      //   this.selectedMapId = this.MapIdOptions[0].value
      // }
    },
    async changeMap(e) {
      if (e === '') {
        this.selectDogId = null
        this.selectInfo = {}
        return
      } 
      let selectedMapInfo = this.MapIdOptions.find(m => m.value === e)
      if (selectedMapInfo) {
        // this.currentImage = require('D:/resources/robot/maps/地图1.jpg')
        this.currentImage = `${process.env.NODE_ENV === 'development' ? process.env.VUE_APP_BASE_IP.replaceAll("'", '') : location.origin}/file/${selectedMapInfo.mapFileUrl}`
        this.X0 = selectedMapInfo.X0
        this.Y0 = selectedMapInfo.Y0
        this.getDogList(e)
      }
    },
    getDogList(mapInfoId) {
      // let queryParams = {dogState: 1, pageNum: 1, pageSize: 50,}
      listMotion({ mapInfoId, stateIn: '1,2,3' }).then(res => {
        this.dogList = res.rows
      })
    },
    handleChange(e) {
      if (e === '') {
        this.selectInfo = {}
      } else {
        this.selectInfo = this.dogList.filter(item => item.id === e)[0]
        this.$nextTick(() => {
          this.$refs.dialogRef.open({
            currentImage: this.currentImage,
            dogId: this.selectInfo.id,
            dogName: this.selectInfo.name,
            Res: this.Res,
            X0: this.X0,
            Y0: this.Y0,
            deviceData: this.deviceData,
            dogInfo: this.selectInfo,
            endpoint: this.selectInfo.endpoint
          })
        })
        Cookies.set('targetId', this.dogId)
        Cookies.set('targetName', this.dogName)
      }
    }
  },
  beforeDestroy() {
    Cookies.remove('targetId');
    Cookies.remove('targetName');
  },
}
</script>

<style lang="scss">
  .con {
    .side-box.left {
      flex: 1;
      & + .middle {
        padding-top: 0 !important;
      }
    }
    .side-box.right {
      width: 430px;
    }
    .card .title {
      height: 40px;
      margin: 0;
      text-align: left;
      line-height: 40px;
      padding-left: 13px;
      font-family: "Arial Negreta", "Arial Normal", "Arial";
      font-weight: 700;
      letter-spacing: 5px;
      color: #FFFFFF;
      background: #0079fe;
    }
    .t3 {
      height: 30px;
      line-height: 30px;
      padding-left: 10px;
      color: #fff;
      font-size: 14px;
      font-weight: normal;
      background: rgba($color: #000000, $alpha: .8);
    }
    .contents.my-map-dialog {
      height: calc(100vh - 194px) !important;
    }
  }
</style>