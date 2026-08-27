export const BIGSCREEN_PERMISSIONS = Object.freeze({
  HOME: 'bigscreen.home.view',
  PATROL_MAP: 'bigscreen.patrol.map.view',
  PATROL_MONITOR: 'bigscreen.patrol.monitor.view',
  PATROL_BUSINESS: 'bigscreen.patrol.business.view',
  PATROL_STATS: 'bigscreen.patrol.stats.view',
  STAFF: 'bigscreen.staff.view',
  SAFETY: 'bigscreen.safety.view',
  EMERGENCY: 'bigscreen.emergency.view'
})

export const PATROL_PAGES = Object.freeze([
  { key: 'panorama', routeName: 'biPatrolPanorama', permission: BIGSCREEN_PERMISSIONS.PATROL_MAP },
  { key: 'monitor', routeName: 'biPatrolMonitor', permission: BIGSCREEN_PERMISSIONS.PATROL_MONITOR },
  { key: 'business', routeName: 'biPatrolBusiness', permission: BIGSCREEN_PERMISSIONS.PATROL_BUSINESS },
  { key: 'statistics', routeName: 'biPatrolStatistics', permission: BIGSCREEN_PERMISSIONS.PATROL_STATS }
])

// 默认落地页顺序与顶栏/页面切换菜单一致：指挥中心 → 巡逻巡查子页 → 人员管控
export const BIGSCREEN_LANDING_PAGES = Object.freeze([
  { key: 'home', routeName: 'biIndex', permission: BIGSCREEN_PERMISSIONS.HOME },
  ...PATROL_PAGES,
  { key: 'staff', routeName: 'biStaff', permission: BIGSCREEN_PERMISSIONS.STAFF }
])

function runtimeConfig() {
  return (typeof window !== 'undefined' && window.__BIGSCREEN_AUTH_CONFIG__) || {}
}

// 菜单权限开关读 auth-config.js 的 permissionEnabled；未配置或非 false 时默认开启验证。
export function isBigscreenPermissionEnabled() {
  const value = runtimeConfig().permissionEnabled
  if (value === false || `${value}`.trim().toLowerCase() === 'false') {
    return false
  }
  return true
}

export function hasBigscreenPermission(permissions, permission) {
  if (!isBigscreenPermissionEnabled()) return true
  return Array.isArray(permissions) && permissions.includes(permission)
}

export function hasAnyBigscreenPermission(permissions, requiredPermissions) {
  return requiredPermissions.some(permission => hasBigscreenPermission(permissions, permission))
}

export const TASK_PERMISSIONS = Object.freeze({
  PLAN_VIEW: 'task.plan.view',
  PLAN_CREATE: 'task.plan.create',
  PLAN_EDIT: 'task.plan.edit',
  PLAN_EXECUTE: 'task.plan.execute',
  PLAN_DELETE: 'task.plan.delete',
  RECORD_VIEW: 'task.record.view',
  EXECUTION_PAUSE: 'task.execution.pause',
  EXECUTION_RESUME: 'task.execution.resume',
  EXECUTION_TERMINATE: 'task.execution.terminate',
  EXECUTION_FORCE_TERMINATE: 'task.execution.force-terminate'
})

// 管理端目录码是 task.execution.*；角色树常挂在任务计划下，兼容 task.plan.pause 等写法。
const TASK_PERMISSION_ALIASES = Object.freeze({
  [TASK_PERMISSIONS.EXECUTION_PAUSE]: ['task.plan.pause'],
  [TASK_PERMISSIONS.EXECUTION_RESUME]: ['task.plan.resume'],
  [TASK_PERMISSIONS.EXECUTION_TERMINATE]: ['task.plan.terminate'],
  [TASK_PERMISSIONS.EXECUTION_FORCE_TERMINATE]: ['task.plan.force-terminate']
})

function toPermissionCode(item) {
  if (typeof item === 'string') return item.trim()
  if (!item || typeof item !== 'object') return ''
  return String(item.permissionCode || item.code || item.permission || '').trim()
}

export function normalizePermissionCodes(value) {
  if (Array.isArray(value)) {
    return value.map(toPermissionCode).filter(Boolean)
  }
  if (typeof value === 'string') {
    return value.split(/[,\s]+/).map(item => item.trim()).filter(Boolean)
  }
  if (value && typeof value === 'object') {
    return Object.values(value).map(toPermissionCode).filter(Boolean)
  }
  return []
}

function permissionMatches(codes, permission) {
  if (!permission) return true
  if (codes.indexOf(permission) !== -1) return true
  if (codes.indexOf('*:*:*') !== -1 || codes.indexOf('*') !== -1) return true
  const aliases = TASK_PERMISSION_ALIASES[permission] || []
  return aliases.some(code => codes.indexOf(code) !== -1)
}

// 任务写操作权限：关大屏权限开关或管理端授权旁路时全部放行。
export function hasManagementPermission(permission, permissions, authorizationBypassed) {
  if (!permission) return true
  if (!isBigscreenPermissionEnabled()) return true
  if (authorizationBypassed) return true
  return permissionMatches(normalizePermissionCodes(permissions), permission)
}

export function firstPatrolRouteName(permissions) {
  return PATROL_PAGES.find(page => hasBigscreenPermission(permissions, page.permission))?.routeName || ''
}

export function firstAccessibleRouteName(permissions) {
  return BIGSCREEN_LANDING_PAGES.find(page => hasBigscreenPermission(permissions, page.permission))?.routeName || ''
}
