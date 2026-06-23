<template>
  <div class="screen-border">
<!--    标题-->
    <div class="screen-title">
      <div class="zuo">
        <div class="line">|</div>
        <div class="item_title" v-if="title !== ''">
          <span class="title-inner"> &nbsp;&nbsp;{{ title }}&nbsp;&nbsp; </span>
        </div>
      </div>
      <div class="you">
        <div class="decorate"></div>
        <div class="you-line">|</div>
      </div>
    </div>
<!--    内容-->
    <div class="contain">
      <dv-border-box-12>
        <slot></slot>
      </dv-border-box-12>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {};
  },
  props: {
    title: {
      type: String,
      default: () => "",
    },
  },
  created() {},

  mounted() {},
  methods: {},
};
</script>
<style lang='scss' scoped>
$item-title-height: 40px;
$item_title_content-height: calc(100% - 25px);

.screen-border {
  box-sizing: border-box;
  height: 100%;

    .screen-title {
      height: $item-title-height;
      background-color: rgb(19,83,137);
      display: flex;
      justify-content: space-between;
      align-items: center;

      .zuo {
        display: flex;
        align-items: center;
        .line {
          font-size: 35px;
          margin-top: -4px;
          color: rgb(123,255,244);
        }
        .item_title {
          .title-inner {
            color: white;
            font-family: '黑体', SimHei, sans-serif;
            font-size: 20px;
            font-style: oblique;
            font-weight: bold;
          }
        }
      }
      .you {
        display: flex;
        align-items: center;
        .you-line {
          font-size: 35px;
          margin-top: -4px;
          color: rgb(123,255,244);
        }
      }
    }
    .contain {
      background-color: rgba(255,255,255,0.03);
      height: $item_title_content-height;
    }

}
</style>
