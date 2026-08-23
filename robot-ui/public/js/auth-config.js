window.__BIGSCREEN_AUTH_CONFIG__ = window.__BIGSCREEN_AUTH_CONFIG__ || {
  // 公司内网
  // keycloakUrl: 'https://192.168.124.235:18443',
  // 公司外网
  keycloakUrl: 'https://211.137.109.150:18443',
  // 联通云
  // keycloakUrl: 'https://175.155.35.79:18443',
  keycloakRealm: 'iam-auth',
  keycloakClientId: 'bigscreen-web',
  keycloakLocale: 'zh-CN',
  // 菜单权限验证：true 或不填则按权限过滤菜单和路由；false 展示全部菜单和路由；在客户环境需去掉开关并修改代码中的开关判断，严格验证权限
  permissionEnabled: false
}
