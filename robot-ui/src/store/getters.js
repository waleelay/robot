const getters = {
  sidebar: state => state.app.sidebar,
  size: state => state.app.size,
  device: state => state.app.device,
  dict: state => state.dict.dict,
  visitedViews: state => state.tagsView.visitedViews,
  cachedViews: state => state.tagsView.cachedViews,
  token: state => state.user.token,
  avatar: state => state.user.avatar,
  name: state => state.user.name,
  nickName: state => state.user.nickName,
  introduction: state => state.user.introduction,
  roles: state => state.user.roles,
  permissions: state => state.user.permissions,
  permission_routes: state => state.permission.routes,
  topbarRouters:state => state.permission.topbarRouters,
  defaultRoutes:state => state.permission.defaultRoutes,
  sidebarRouters:state => state.permission.sidebarRouters,

  arrivedNum: state => state.bigScreen.arrivedNum,
  unarrivedNum: state => state.bigScreen.unarrivedNum,
  needTime: state => state.bigScreen.needTime,
  processNum: state => state.bigScreen.processNum,
  taskPointNum: state => state.bigScreen.taskPointNum,
  unarrivedUsers: state => state.bigScreen.unarrivedUsers,
  signResultDatas: state => state.bigScreen.signResultDatas
}
export default getters
