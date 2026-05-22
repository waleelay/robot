module.exports = {
  devServer: {
    proxy: {
      '/api': {
        target: process.env.VUE_APP_API_BASE || 'http://192.168.124.77:8088',
        changeOrigin: true
      },
      '/internal': {
        target: process.env.VUE_APP_API_BASE || 'http://192.168.124.77:8088',
        changeOrigin: true
      },
      '/ws/control': {
        target: process.env.VUE_APP_WS_BASE || 'http://192.168.124.77:8088',
        ws: true,
        changeOrigin: true
      }
    }
  }
}
