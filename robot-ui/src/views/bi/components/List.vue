<template>
  <div class="custom-list-container pb10 flex1 flex-column" style="overflow-y: hidden;">
    <div class="list wp340 flex1 flex-column" style="overflow-y: hidden;">
      <div class="w100 flx-align-center flex title">
        <div class="wp28">序号</div>
        <div class="flex1 ml32 mr20">任务名称</div>
        <div class="wp47 ml12">状态</div>
        <div class="wp72 ml12">操作</div>
      </div>
      <div class="content w100 flex1 common-scroll pr4" style="overflow-y: auto">
        <div v-for="(item, index) in listData" :key="item.name" class="item hp36 w100 flx-align-center flex" @click="handleClickItem(item)">
          <div class="serial wp28">
            {{ (listInfo.page - 1) * listInfo.size + index + 1 }}
          </div>
          <div class="name flex1 text-ellipsis ml32 mr20" :title="item.name">{{item.name}}</div>
          <div class="status wp47 ml12">{{item.status}}</div>
          <div class="wp72 ml12 operation-icons">
            <svg-icon icon-class="pause"></svg-icon>
            <svg-icon icon-class="edit1" class="ml10"></svg-icon>
            <svg-icon icon-class="delete" class="ml10"></svg-icon>
          </div>
        </div>
      </div>
    </div>
    <el-pagination
      v-model:current-page="listInfo.page"
      background
      :page-size="listInfo.size"
      :total="listInfo.total"
      layout="prev, pager, next"
      @current-change="e => listInfo.page = e" 
      class="mt16 custom-pagination"
    />
  </div>
</template>

<script>
export default {
  name: 'List',
  data() {
    return {
      listInfo: {
        page: 1,
        size: 5,
        total: 6,
        listData: [
          {
            name: '无人机1',
            status: '运行中'
          },
          {
            name: '无人机2',
            status: '已暂停'
          },
          {
            name: '无人机3',
            status: '已停止'
          },
          {
            name: '无人机4',
            status: '已取消'
          },
          {
            name: '无人机5',
            status: '已完成'
          },
          {
            name: '无人机6',
            status: '运行中'
          },
        ]
      }
    }
  },
  computed: {
    listData() {
      return this.listInfo.listData.slice((this.listInfo.page - 1) * this.listInfo.size, this.listInfo.page * this.listInfo.size);
    }
  },
  methods: {
    handleClickItem(item) {
      console.log(item);
    }
  },
}
</script>