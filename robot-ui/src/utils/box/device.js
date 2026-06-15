class deviceInfo {
  constructor() {
    this.info = {};

    this.init();
  }
  init() {
    deviceApis.getDeviceInfo().then(res => {
      console.log(res)
      this.info = res.data;
      this.initForm()
    })
  }
  initForm() {
    layui.use(() => {
      var form = layui.form;
      form.val('deviceForm', this.info);
      form.on('submit(saveData)', (formData) => {
        deviceApis.editDeviceInfo(Object.assign(this.info, formData.field)).then(res => {
          console.log(res)
        })
        return false;
      });
    })
  }
}

// 等待Layui加载完成后初始化
layui.use(['layer', 'form'], () => {
  // 初始化表格管理器
  window.deviceInfo = new deviceInfo();
});

const deviceApis = {
  getDeviceInfo: () => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.device}`,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  editDeviceInfo: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "PUT",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.device}`,
        data: JSON.stringify(data),
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },

}