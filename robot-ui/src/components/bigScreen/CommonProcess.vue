<template>
  <div class="common_process">
    <div class="common_process_item">
      <div class="common_process_item_line">
        <div
          class="common_process_item_line_process"
          :style="{ '--width': num + '%' }"
        />
      </div>
      <div class="common_process_item_dept cyan">{{ num }}%</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'CommonProcess',
  props: {
    num: {
      type: Number,
      required: true,
      validator: (value) => value >= 0 && value <= 100
    }
  }
}
</script>

<style lang='scss' scoped>
.common_process {
  padding: 0 10px;
  &_item {
    display: flex;
    align-items: center;
    padding: 7px 0;
    &_dept {
      margin-left: 10px;
      font-size: 13px;
      color: #fff;
      background: linear-gradient(90deg, #236cdc 0%, #00c0ff 100%);
      padding: 2px 6px;
      border-radius: 3px;
    }
    &_line {
      flex-grow: 1;
      height: 8px;
      background-color: rgba(64, 158, 255, 0.1);
      border-top-right-radius: 8px;
      border-bottom-right-radius: 8px;
      &_process {
        position: relative;
        width: var(--width);
        height: 100%;
        background: linear-gradient(90deg, #236cdc 0%, #00c0ff 100%);
        animation: move 1s;

        @keyframes move {
          0% {
            width: 0%;
          }
          100% {
            width: var(--width);
          }
        }

        &::after {
          content: '';
          position: absolute;
          top: -2px;
          right: 0;
          width: 6px;
          height: 12px;
          border-radius: 2px;
          background-color: #fff;
          box-shadow: 0 0 10px #faad14;
        }
      }
    }
  }
}
</style>
