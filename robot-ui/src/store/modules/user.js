import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'

const user = {
  state: {
    token: getToken(),
    id: '',
    name: '',
    nickName: '',
    avatar: '',
    roles: [],
    permissions: []
  },

  mutations: {
    SET_TOKEN: (state, token) => {
      state.token = token
    },
    SET_ID: (state, id) => {
      state.id = id
    },
    SET_NAME: (state, name) => {
      state.name = name
    },
    SET_NICKNAME: (state, name) => {
      state.nickName = name
    },
    SET_AVATAR: (state, avatar) => {
      state.avatar = avatar
    },
    SET_ROLES: (state, roles) => {
      state.roles = roles
    },
    SET_PERMISSIONS: (state, permissions) => {
      state.permissions = permissions
    }
  },

  actions: {
    // 登录
    Login({ commit }, userInfo) {
      const username = userInfo.username.trim()
      const password = userInfo.password
      const code = userInfo.code
      const uuid = userInfo.uuid
      return new Promise((resolve, reject) => {
        login(username, password, code, uuid).then(res => {
          setToken(res.token)
          commit('SET_TOKEN', res.token)
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    },

    // 获取用户信息
    GetInfo({ commit, state }) {
      return new Promise((resolve, reject) => {
        getInfo().then(res => {
          // const res = {
          //   "msg": "操作成功",
          //   "code": 200,
          //   "permissions": [
          //       "*:*:*"
          //   ],
          //   "roles": [
          //       "admin"
          //   ],
          //   "user": {
          //       "createBy": "admin",
          //       "createTime": "2024-06-11 10:46:10",
          //       "updateBy": null,
          //       "updateTime": null,
          //       "remark": "管理员",
          //       "params": {
          //           "@type": "java.util.HashMap"
          //       },
          //       "userId": 1,
          //       "deptId": 103,
          //       "userName": "admin",
          //       "nickName": "yueyue",
          //       "email": "ry@163.com",
          //       "phonenumber": "15888888888",
          //       "sex": "1",
          //       "avatar": "/profile/avatar/2024/10/10/证件照_20241010161347A001.jpg",
          //       "password": "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2",
          //       "status": "0",
          //       "delFlag": "0",
          //       "loginIp": "192.168.1.2",
          //       "loginDate": "2025-07-04T16:57:52.000+08:00",
          //       "dept": {
          //           "createBy": null,
          //           "createTime": null,
          //           "updateBy": null,
          //           "updateTime": null,
          //           "remark": null,
          //           "params": {
          //               "@type": "java.util.HashMap"
          //           },
          //           "deptId": 103,
          //           "parentId": 101,
          //           "ancestors": "0,100,101",
          //           "deptName": "研发部门",
          //           "orderNum": 1,
          //           "leader": "若依",
          //           "phone": null,
          //           "email": null,
          //           "status": "0",
          //           "delFlag": null,
          //           "parentName": null,
          //           "children": []
          //       },
          //       "roles": [
          //           {
          //               "createBy": null,
          //               "createTime": null,
          //               "updateBy": null,
          //               "updateTime": null,
          //               "remark": null,
          //               "params": {
          //                   "@type": "java.util.HashMap"
          //               },
          //               "roleId": 1,
          //               "roleName": "超级管理员",
          //               "roleKey": "admin",
          //               "roleSort": 1,
          //               "dataScope": "1",
          //               "menuCheckStrictly": false,
          //               "deptCheckStrictly": false,
          //               "status": "0",
          //               "delFlag": null,
          //               "flag": false,
          //               "menuIds": null,
          //               "deptIds": null,
          //               "permissions": null,
          //               "admin": true
          //           }
          //       ],
          //       "roleIds": null,
          //       "postIds": null,
          //       "roleId": null,
          //       "admin": true
          //   }
          // }
          const user = res.user
          const avatar = (user.avatar == "" || user.avatar == null) ? require("@/assets/images/profile.jpg") : process.env.VUE_APP_BASE_API + user.avatar;
          if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
            commit('SET_ROLES', res.roles)
            commit('SET_PERMISSIONS', res.permissions)
          } else {
            commit('SET_ROLES', ['ROLE_DEFAULT'])
          }
          commit('SET_ID', user.userId)
          commit('SET_NAME', user.userName)
          commit('SET_NICKNAME', user.nickName)
          commit('SET_AVATAR', avatar)
          resolve(res)
        }).catch(error => {
          reject(error)
        })
      })
    },

    // 退出系统
    LogOut({ commit, state }) {
      return new Promise((resolve, reject) => {
        logout(state.token).then(() => {
          commit('SET_TOKEN', '')
          commit('SET_ROLES', [])
          commit('SET_PERMISSIONS', [])
          removeToken()
          resolve()
        }).catch(error => {
          reject(error)
        })
      })
    },

    // 前端 登出
    FedLogOut({ commit }) {
      return new Promise(resolve => {
        commit('SET_TOKEN', '')
        removeToken()
        resolve()
      })
    }
  }
}

export default user
