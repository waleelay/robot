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

export function firstPatrolRouteName(permissions) {
  return PATROL_PAGES.find(page => hasBigscreenPermission(permissions, page.permission))?.routeName || ''
}

export function firstAccessibleRouteName(permissions) {
  return BIGSCREEN_LANDING_PAGES.find(page => hasBigscreenPermission(permissions, page.permission))?.routeName || ''
}
