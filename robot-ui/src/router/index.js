import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

/* Layout */
import Layout from '@/layout'
import path from "path";

/**
 * Note: 路由配置项
 *
 * hidden: true                     // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true                 // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                  // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                  // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                  // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect             // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'               // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * query: '{"id": 1, "name": "ry"}' // 访问路由的默认传递参数
 * roles: ['admin', 'common']       // 访问路由的角色权限
 * permissions: ['a:a:a', 'b:b:b']  // 访问路由的菜单权限
 * meta : {
    noCache: true                   // 如果设置为true，则不会被 <keep-alive> 缓存(默认 false)
    title: 'title'                  // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'                // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false               // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/system/user'      // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

// 公共路由
export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register'),
    hidden: true
  },
  {
    path: '/404',
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  // {
  //   path: '/bigScreen',
  //   component: () => import('@/views/bigScreen/home.vue'),
  //   hidden: true,
  // },
  {
    path: '/bigScreen',
    component: () => import('@/views/largeScreen/home.vue'),
    hidden: true,
    redirect: '/bigScreen/shouye',
    children: [
      {
        path: 'default',
        component: () => import('@/views/largeScreen/index'),
        name: 'default',
        meta: { title: '首页' }
      },
      {
        path: 'shouye',
        component: () => import('@/views/largeScreen/1_shouye'),
        name: 'shouye',
        meta: { title: '首页' }
      },
      {
        path: 'xlxc',
        component: () => import('@/views/largeScreen/2_xlxc'),
        name: 'xlxc',
        meta: { title: '巡逻巡查' }
      },
      {
        path: 'rygk',
        component: () => import('@/views/largeScreen/3_rygk'),
        name: 'rygk',
        meta: { title: '人员管控' }
      },
      {
        path: 'cljg',
        component: () => import('@/views/largeScreen/4_cljg'),
        name: 'cljg',
        meta: { title: '车辆监管' }
      },
    ]
  },
  {
    path: '/bi',
    name: 'bi',
    hidden: true,
    redirect: '/bi/index',
    component: () => import('@/views/bi/Bi.vue'),
    meta: {
      title: '大屏',
      icon: 'el-icon-data-board',
      requiresAuth: true
    },
    children: [
      {
        path: '/bi/index',
        name: 'biIndex',
        component: () => import('@/views/bi/home/Index.vue'),
        meta: {
          title: '首页',
          icon: 'el-icon-data-board',
          requiresAuth: true
        }
      },
      {
        path: '/bi/home',
        name: 'biHome',
        component: () => import('@/views/bi/Home.vue'),
        meta: {
          title: '首页',
          icon: 'el-icon-data-board',
          requiresAuth: true
        }
      },
      {
        path: '/bi/patrol',
        name: 'biPatrol',
        redirect: '/bi/patrol/panorama',
        component: () => import('@/views/bi/patrol/Index.vue'),
        meta: {
          title: '巡逻巡查',
          icon: 'el-icon-data-board',
          requiresAuth: true
        },
        children: [
          {
            path: '/bi/patrol/panorama',
            name: 'biPatrolPanorama',
            component: () => import('@/views/bi/patrol/panorama/Index.vue'),
            meta: {
              title: '全景地图',
              icon: 'el-icon-data-board',
              requiresAuth: true
            }
          },
          {
            path: '/bi/patrol/monitor',
            name: 'biPatrolMonitor',
            component: () => import('@/views/bi/patrol/monitor/Index.vue'),
            meta: {
              title: '实时监控',
              icon: 'el-icon-data-board',
              requiresAuth: true
            }
          },
          {
            path: '/bi/patrol/business',
            name: 'biPatrolBusiness',
            component: () => import('@/views/bi/patrol/business/Index.vue'),
            meta: {
              title: '业务管理',
              icon: 'el-icon-data-board',
              requiresAuth: true
            },
            // children: [
            //   {
            //     path: '/bi/patrol/panorama',
            //     name: 'biPatrolPanorama',
            //     component: () => import('@/views/bi/patrol/panorama/Index.vue'),
            //     meta: {
            //       title: '全景地图',
            //       icon: 'el-icon-data-board',
            //       requiresAuth: true
            //     }
            //   },
            // ]
          },
          {
            path: '/bi/patrol/statistics',
            name: 'biPatrolStatistics',
            component: () => import('./../views/bi/patrol/statistics‌/Index.vue'),
            meta: {
              title: '数据统计',
              icon: 'el-icon-data-board',
              requiresAuth: true
            }
          },
        ]
      },
      {
        path: '/bi/staff',
        name: 'biStaff',
        component: () => import('@/views/bi/staff/Index.vue'),
        meta: {
          title: '人员管理',
          icon: 'el-icon-data-board',
          requiresAuth: true
        }
      },
      {
        path: '/bi/vehicle',
        name: 'biVehicle',
        component: () => import('@/views/bi/Vehicle.vue'),
        meta: {
          title: '车辆监管',
          icon: 'el-icon-data-board',
          requiresAuth: true
        }
      },
      {
        path: '/bi/demo',
        name: 'biDemo',
        component: () => import('@/views/bi/demo/Index.vue'),
        meta: {
          title: 'DEMO组件',
          icon: 'el-icon-data-board',
          requiresAuth: true
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile',
        component: () => import('@/views/system/user/profile/index'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  },
  {
    path: '/taskRoute',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'addRoute',
        component: ()=> import('@/views/task/taskRoute/addRoute'),
        name: 'addRoute',
        meta: {title: '添加路径',icon: 'user'}
      }
    ]
  },
  {
    path: '/updateRoute',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'updateRoute/:id',
        component: () => import('@/views/task/taskRoute/updateRoute'),
        name: 'updateRoute',
        meta: {title: '修改路径'}
      }
    ]
  }
]

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  {
    path: '/',
    component: Layout,
    hidden: true,
    permissions: ['rsp:map-info:add'],
    children: [
      {
        path: 'map/add',
        component: () => import('@/views/map/add'),
        name: 'mapAdd',
        meta: { title: '新建地图', activeMenu: '/map', noCache: true }
      }
    ]
  },
  {
    path: '/',
    component: Layout,
    hidden: true,
    permissions: ['rsp:motion:add'],
    children: [
      {
        path: 'equipment/add',
        component: () => import('@/views/equipment/add'),
        name: 'equipemtAdd',
        meta: { title: '接入装备', activeMenu: '/equipment', noCache: true }
      }
    ]
  },
  {
    path: '/',
    component: Layout,
    hidden: true,
    permissions: ['rsp:path:add'],
    children: [
      {
        path: 'path/add',
        component: () => import('@/views/path/add'),
        name: 'pathAdd',
        meta: { title: '新建路径', activeMenu: '/path', noCache: true }
      }
    ]
  },
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:edit'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:edit'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  },
  {
    path: '/monitor/job-log',
    component: Layout,
    hidden: true,
    permissions: ['monitor:job:list'],
    children: [
      {
        path: 'index/:jobId(\\d+)',
        component: () => import('@/views/rollCall/setTask/log'),
        name: 'JobLog',
        meta: { title: '调度日志', activeMenu: '/rollCall/setTask' }
      }
    ]
  },
  {
    path: '/tool/gen-edit',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: 'index/:tableId(\\d+)',
        component: () => import('@/views/tool/gen/editTable'),
        name: 'GenEdit',
        meta: { title: '修改生成配置', activeMenu: '/tool/gen' }
      }
    ]
  }
]

// 防止连续点击多次路由报错
let routerPush = Router.prototype.push;
let routerReplace = Router.prototype.replace;
// push
Router.prototype.push = function push(location) {
  return routerPush.call(this, location).catch(err => err)
}
// replace
Router.prototype.replace = function push(location) {
  return routerReplace.call(this, location).catch(err => err)
}

export default new Router({
  mode: 'history', // 去掉url中的#
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})
