
class FaceLibManager {
  constructor() {
    this.page = 1;
    this.size = 10;
    this.group_id = '';
    this.groupData = [];
    this.data = [];
    this.isEditing = false;
    this.currentEditId = null;
    this.table = null;

    this.init();
  }

  async init() {
    this.initLayui();
    this.bindEvents();
    await this.getGroupData();
    this.getTableData();
  }

  // 初始化Layui
  initLayui() {
    layui.use(['table', 'layer', 'form'], () => {
      const table = layui.table;
      const layer = layui.layer;
      const form = layui.form;
      // 初始化表格
      this.table = table.render({
        elem: '#dataTable',
        data: this.data,
        cols: [[
          // { field: 'id', title: 'ID', width: 80, sort: true },
          { field: 'external_id', title: '外部ID', width: 150 },
          { field: 'name', title: '姓名', width: 150 },
          { field: 'image', title: '人脸图片', width: 150, templet: '#image' },
          { field: 'age', width: 100, title: '年龄', },
          { field: 'desc', title: '简介' },
          { field: 'update_time', width: 100, title: '更新时间', },
          { title: '操作', width: 200, toolbar: '#operationBar', fixed: 'right' }
        ]],
        page: true,
        limit: 10,
        limits: [10, 20, 50],
        height: 'full-220',
        text: {
          none: '暂无数据'
        }
      });

      // 监听工具条事件
      table.on('tool(dataTable)', (obj) => {
        const data = obj.data;
        if (obj.event === 'edit') {
          this.editFaceModal(data, 'edit');
        } else if (obj.event === 'del') {
          this.deleteData(data.id);
        }
      });
    });
  }

  getGroupData() {
    return new Promise((resolve, reject) => {
      faceLibApis.getGrpup().then(res => {
        this.groupData = res.data.map(item => {
          item.ext = JSON.parse(item.ext);
          item.quality = item.ext.quality;
          return item
        });
        if (this.groupData.length > 0) {
          this.group_id = this.groupData[0].id;
          resolve()
        }
        let el = document.querySelector("#group-container");
        let innerHTML = ''
        for (let i = 0; i < this.groupData.length; i++) {
          if (i == 0) {
            innerHTML += `<li class="group active" id="${this.groupData[i].id}">
            ${this.groupData[i].name}
              <i class="layui-icon  layui-icon-edit" editId="${this.groupData[i].id}"></i>
              <i class="layui-icon  layui-icon-delete" delId="${this.groupData[i].id}"></i>
            </li>`;
          } else {
            innerHTML += `<li class="group" id="${this.groupData[i].id}">
            ${this.groupData[i].name}
              <i class="layui-icon  layui-icon-edit" editId="${this.groupData[i].id}"></i>
              <i class="layui-icon  layui-icon-delete" delId="${this.groupData[i].id}"></i>
            </li>`;
          }
        }
        el.innerHTML = innerHTML;

        let editBtns = document.querySelectorAll("#group-container .layui-icon-edit");
        editBtns.forEach(editBtn => {
          editBtn.addEventListener('click', (e) => {
            let group_id = e.target.getAttribute("editId");
            this.editGroupModal(this.groupData.find(item => item.id == group_id), "edit")
          })
        })

        let delBtns = document.querySelectorAll("#group-container .layui-icon-delete");
        delBtns.forEach(delBtn => {
          delBtn.addEventListener('click', (e) => {
            let group_id = e.target.getAttribute("delId");
            faceLibApis.delGroup({ ids: [group_id] }).then(res => {
              this.getGroupData();
              this.showNotification('数据更新成功！');
            })
          })
        })
      })
    })

  }
  getTableData() {
    faceLibApis.getFaceList({
      page: this.page,
      size: this.size,
      group_id: this.group_id
    }).then(res => {
      this.data = res.data.data.map(item => {
        // item.image = ZQLGLOBAL.serverIp + ':9092/staticdata' + item.image;
        item.image = `http://${ZQLGLOBAL.serverIp}:9092/staticdata${item.image}`
        return item
      });
      if (this.table) {
        layui.use('table', () => {
          layui.table.reload('dataTable', {
            data: this.data
          });
        });
      }
    })
  }

  // 绑定事件
  bindEvents() {
    // 添加按钮事件
    document.getElementById('addGroup').addEventListener('click', () => {
      this.editGroupModal({
        name: '',
        quality: 0.35,
      }, 'add');
    });

    document.getElementById('addBtn').addEventListener('click', () => {
      this.editFaceModal({
        name: '',
        age: '',
        sex: '',
        desc: '',
        external_id: '',
        feature: '',
      }, 'add');
    });

    // 搜索功能
    document.getElementById('searchBtn').addEventListener('click', () => {
      this.getTableData();
    });
  }

  // 编辑分组
  editGroupModal(data, type) {
    layui.use(['layer', 'form'], () => {
      const layer = layui.layer;
      const form = layui.form;

      layer.open({
        type: 1,
        title: type == 'add' ? '添加分组' : '编辑分组',
        area: ['40vw', '40vh'],
        content: `
                    <form class="layui-form" lay-filter="dataForm" style="padding: 20px;">
                        <input type="hidden" name="id" value="${data.id}">
                        <div class="layui-form-item">
                            <label class="layui-form-label">分组名称</label>
                            <div class="layui-input-block">
                                <input type="text" name="name" value="${data.name}" required lay-verify="required" placeholder="请输入描述" autocomplete="off" class="layui-input">
                            </div>
                        </div>

                        <div class="layui-form-item">
                            <label class="layui-form-label">人脸质量</label>
                            <div class="layui-input-block">
                                <input type="text" name="quality" value="${data.quality}" required lay-verify="required" placeholder="请输入描述" autocomplete="off" class="layui-input">
                            </div>
                        </div>
                        
                        <div class="layui-form-item">
                            <div class="layui-input-block">
                                <button class="layui-btn" lay-submit lay-filter="saveData">保存</button>
                            </div>
                        </div>
                    </form>
                `,
        success: () => {
          form.render();
          // 监听表单提交
          form.on('submit(saveData)', (formData) => {
            if (type == 'add') {
              faceLibApis.addGroup({
                alg: 'face',
                ext: { quality: formData.field.quality },
                name: formData.field.name
              }).then(res => {
                this.getGroupData();

                // 关闭模态框
                layui.use('layer', () => {
                  layui.layer.closeAll();
                });
                this.showNotification('数据更新成功！');
              })
            } else {
              faceLibApis.editGroup({
                id: data.id,
                alg: 'face',
                ext: { quality: formData.field.quality },
                name: formData.field.name
              }).then(res => {
                this.getGroupData();
                // 关闭模态框
                layui.use('layer', () => {
                  layui.layer.closeAll();
                });
                this.showNotification('数据更新成功！');
              })
            }
            return false;
          });
        }
      });
    });
  }

  // 打开模态框
  editFaceModal(data, type) {
    layui.use(['layer', 'form'], () => {
      const layer = layui.layer;
      const form = layui.form;

      layer.open({
        type: 1,
        title: type == 'add' ? '添加数据' : '编辑数据',
        area: ['80vw', '80vh'],
        content: `
                    <form class="layui-form" lay-filter="dataForm" style="padding: 20px;">
                        <input type="hidden" name="id" value="${data.id}">
                        <div class="layui-form-item">
                            <label class="layui-form-label">图片</label>
                            <div class="layui-input-block">
                                <input type="file" id="faceImage" placeholder="选择人脸" style="cursor: pointer;">
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">姓名</label>
                            <div class="layui-input-block">
                                <input type="text" name="name" value="${data.name}" required lay-verify="required" placeholder="请输入姓名" autocomplete="off" class="layui-input">
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">外部ID</label>
                            <div class="layui-input-block">
                                <input type="text" name="external_id" value="${data.external_id}" autocomplete="off" class="layui-input">
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">性别</label>
                            <div class="layui-input-block">
                                <input type="text" name="sex" value="${data.sex}"  placeholder="请输入性别" autocomplete="off" class="layui-input">
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">年龄</label>
                            <div class="layui-input-block">
                                <input type="number" name="age" value="${data.age}"  placeholder="请输入年龄" autocomplete="off" class="layui-input">
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <label class="layui-form-label">简介</label>
                            <div class="layui-input-block">
                                <input type="text" name="desc" value="${data.desc}"  placeholder="请输入简介" autocomplete="off" class="layui-input">
                            </div>
                        </div>
                        <div class="layui-form-item">
                            <div class="layui-input-block">
                                <button class="layui-btn" lay-submit lay-filter="saveData">保存</button>
                            </div>
                        </div>
                    </form>
                `,
        success: () => {
          form.render();
          document.getElementById('faceImage').addEventListener('change', (event) => {
            const files = event.target.files;
            data.file = files[0]
          });

          // 监听表单提交
          form.on('submit(saveData)', (formData) => {
            console.log(formData)
            this.handleFormSubmit(formData.field, data);
            return false;
          });
        }
      });
    });
  }

  // 提交:添加/编辑
  handleFormSubmit(formData, data) {
    // 验证数据
    if (!this.validateData(formData)) {
      return;
    }
    let params = {
      group_id: this.group_id,
      name: formData.name,
      age: formData.age,
      sex: formData.sex,
      desc: formData.desc,
      external_id: formData.external_id
    }

    if (!data.id) {
      faceLibApis.addFace(params).then(res => {
        if (data.file) {
          let formdata = new FormData();
          formdata.append('id', res.data);
          formdata.append('image', data.file)
          faceLibApis.addImage(formdata).then(res => {
            this.facecb('add')
          })
        } else {
          this.facecb('add')
        }

      })
    } else {
      params.id = data.id;
      faceLibApis.editFace(params).then(res => {
        if (data.file) {
          let formdata = new FormData();
          formdata.append('id', params.id);
          formdata.append('image', data.file)
          faceLibApis.addImage(formdata).then(res => {
            this.facecb('edit')
          })
        } else {
          this.facecb('edit')
        }
      })
    }


  }

  facecb(type) {
    // 关闭模态框
    layui.use('layer', () => {
      layui.layer.closeAll();
    });
    this.showNotification(type == 'add' ? '数据添加成功！' : '数据更新成功！');
    setTimeout(() => {
      this.getTableData();
    }, 1500)    
  }

  // 验证数据
  validateData(data) {
    if (!data.name) {
      this.showNotification('请输入姓名', 'error');
      return false;
    }
    return true;
  }

  // 删除数据
  deleteData(id) {
    layui.use('layer', () => {
      layui.layer.confirm('确定要删除这条数据吗？', {
        icon: 3,
        title: '提示'
      }, (index) => {
        faceLibApis.delSource({ id: id }).then(res => {
          this.getTableData();
          this.showNotification('数据删除成功！');
          layui.layer.close(index);
        })
      });
    });
  }

  // 显示通知
  showNotification(message, type = 'success') {
    layui.use('layer', () => {
      layui.layer.msg(message, {
        icon: type === 'success' ? 1 : 2,
        time: 2000
      });
    });
  }
}

// 等待Layui加载完成后初始化
layui.use(['table', 'layer', 'form'], () => {
  // 初始化人脸列表
  window.faceLibManager = new FaceLibManager();
});

const faceLibApis = {
  getFaceList: (params) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.face}?page=${params.page}&size=${params.size}&group_id=${params.group_id}`,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  addFace: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "POST",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.face}`,
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
  editFace: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "PUT",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.face}`,
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
  delFace: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "DELETE",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.face}`,
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
  addImage: (formdata) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "PUT",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.image}`,
        data: formdata, // 使用FormData对象
        processData: false,  // 告诉jQuery不要处理发送的数据
        contentType: false,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  getGrpup: () => {
    let type = 'face';
    // 获取人脸分组
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "GET",
        dataType: "json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.group}?alg=${type}`,
        success: function (res) {
          resolve(res)
        },
        error: function (err) {
          reject(err)
        }
      });
    })
  },
  addGroup: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "POST",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.group}`,
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
  editGroup: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "PUT",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.group}`,
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
  delGroup: (data) => {
    return new Promise((resolve, reject) => {
      $.ajax({
        type: "DELETE",
        contentType: "application/json",
        url: `http://${ZQLGLOBAL.serverIp}${ZQLGLOBAL.group}`,
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