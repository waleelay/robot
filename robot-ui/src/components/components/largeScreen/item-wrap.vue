<template>
  <div class="item-wrap" :style="{ height: height }">
    <div class="item-title" :class="className">
      {{ title }}
    </div>
    <div class="contain">
      <slot></slot>
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
    height: {
      type: String,
      default: () => "",
    },
    className: {
      type: String,
      default: () => "",
    },
  },
  created() {},

  mounted() {},
  methods: {},
};
</script>
<style lang="scss" scoped>
$item-title-height: 50px;
$item_title_content-height: calc(100% - 50px);

.item-wrap {
  box-sizing: border-box;
  height: 100%;
  position: relative;
  // border: 2px solid red;

  .item-title {
    width: 426px;
    height: $item-title-height;
    line-height: $item-title-height;
    font-size: 18px;
    color: #ffffff;
    position: relative;
    // font-family: "YouSheBiaoTiHei";
    background-image: url("../../assets/images/largescreen/item-title-bg.png");
    background-size: 100% 100%;
    padding-left:60px;
    &::before {
      content: "";
      width: 24px;
      height: 24px;
      position: absolute;
      left: 28px;
      top: 0;
      bottom: 0;
      margin: auto 0;
    }
    &.icon0 {
      &::before {
        background-image: url("../../assets/images/largescreen/item-title-icon0.png");
      }
    }
    &.icon1 {
      &::before {
        background-image: url("../../assets/images/largescreen/item-title-icon1.png");
      }
    }
    &.icon2 {
      &::before {
        background-image: url("../../assets/images/largescreen/item-title-icon2.png");
      }
    }
    &.icon3 {
      &::before {
        background-image: url("../../assets/images/largescreen/item-title-icon3.png");
      }
    }
    &.icon4 {
      &::before {
        background-image: url("../../assets/images/largescreen/item-title-icon4.png");
      }
    }
  }
  .contain {
    height: $item_title_content-height;
  }
}
</style>
