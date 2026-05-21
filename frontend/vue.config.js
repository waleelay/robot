module.exports = {
  devServer: {
    proxy: {
      '/api': {
        target: process.env.VUE_APP_API_BASE || 'http://localhost:8088',
        changeOrigin: true
      },
      '/ws/media': {
        target: process.env.VUE_APP_WS_BASE || 'http://localhost:8088',
        ws: true,
        changeOrigin: true
      }
    }
  }
}
