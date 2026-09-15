window.__BIGSCREEN_AUTH_CONFIG__ = window.__BIGSCREEN_AUTH_CONFIG__ || {
  // 留空时使用 https://<地址栏主机>:18443；大屏与 IAM 分离部署时填写完整 IAM 地址。
  // 开发环境不留空
  keycloakUrl: 'https://211.137.109.150:18443',
  keycloakRealm: 'iam-auth',
  keycloakClientId: 'bigscreen-web',
  keycloakLocale: 'zh-CN',
  // 菜单权限验证：true 或不填则按权限过滤菜单和路由；false 展示全部菜单和路由；在客户环境需去掉开关并修改代码中的开关判断，严格验证权限
  permissionEnabled: true,
  // EIOP 管理后台地址；未配置时由 VUE_APP_ADMIN_CONSOLE_URL 或主机默认端口解析
  // adminConsoleUrl: 'http://localhost:5173'
}
