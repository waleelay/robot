export default {
  data() {
    return {
      motionId: null,
      yuntaiCommand: {
        up: { label: '上', value: 21 },
        down: { label: '下', value: 22 },
        left: { label: '左', value: 23 },
        right: { label: '右', value: 24 },
        snapshot: { label: '拍照', value: 25 },
        video: { label: '录像', value: 26 },
        jiaojuPlus: { label: '焦距+', value: 27 },
        jiaojuMinus: { label: '焦距-', value: 28 }
      },
    }
  },
  methods: {
    videoMotionControl(item) {
      this.motionId = item
      this.handleClickPTZControl(item)
    },
    handleClickPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex)
    },
    
    mouseDownPTZControl(iPTZIndex) {
      this.yuntaiControl(iPTZIndex, 0)
    },
    mouseUpPTZControl() {
      this.yuntaiControl(23, 1)
    },
    async yuntaiControl(dwPTZCommand, dwStop) {
      try {
        const url = process.env.NODE_ENV === 'development'
          ? process.env.VUE_APP_YUNTAI_CONTROL
          : `${location.origin}/yuntai`;
        const response = await axios.get(url, {
          params: {
            dwPTZCommand,
            deviceId: Cookies.get('targetId')
          },
          responseType: 'blob'
        });

        // =============================================
        // 仅从响应头获取文件名，没有则不触发下载
        // =============================================
        const disposition = response.headers['content-disposition'];

        // 有下载头才执行下载
        if (disposition) {
          // 正则精确提取 filename="xxx"
          const filenameMatch = disposition.match(/filename="([^"]+)"/);
          const filename = filenameMatch ? filenameMatch[1] : 'download';

          // 触发下载
          const blob = new Blob([response.data], {
            type: response.headers['content-type']
          });
          const link = document.createElement('a');
          link.href = URL.createObjectURL(blob);
          link.download = filename;
          document.body.appendChild(link);
          link.click();
          link.remove();
          URL.revokeObjectURL(link.href);
        }

      } catch (err) {
        console.error('云台控制失败：', err);
      }
    },
  }
}