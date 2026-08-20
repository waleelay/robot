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

export function hasBigscreenPermission(permissions, permission) {
  return Array.isArray(permissions) && permissions.includes(permission)
}

export function hasAnyBigscreenPermission(permissions, requiredPermissions) {
  return requiredPermissions.some(permission => hasBigscreenPermission(permissions, permission))
}

export function firstPatrolRouteName(permissions) {
  return PATROL_PAGES.find(page => hasBigscreenPermission(permissions, page.permission))?.routeName || ''
}
