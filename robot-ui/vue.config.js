'use strict'
const path = require('path')
const fs = require('fs')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const CompressionPlugin = require('compression-webpack-plugin')
const { BundleAnalyzerPlugin } = require('webpack-bundle-analyzer')
const TerserPlugin = require('terser-webpack-plugin')

const name = process.env.VUE_APP_TITLE || '具身智能平台指挥中心' // 网页标题

const port = process.env.port || process.env.npm_config_port || 8080 // 端口

// vue.config.js 配置说明
module.exports = {
  transpileDependencies: ['ol'], // 关键：告诉 Vue CLI 转译 ol 及其依赖
  // 配置1: 多线程打包
  // parallel: require('os').cpus().length > 1,
  // 部署生产环境和开发环境下的URL。
  // 默认情况下，Vue CLI 会假设你的应用是被部署在一个域名的根路径上
  // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
  publicPath: process.env.NODE_ENV === 'production' ? '/' : '/',
  // 在npm run build 或 yarn build 时 ，生成文件的目录名称（要和baseUrl的生产环境路径一致）（默认dist）
  outputDir: 'dist',
  // 用于放置生成的静态资源 (js、css、img、fonts) 的；（项目打包之后，静态资源会放在这个文件夹下）
  assetsDir: 'static',
  // 是否开启eslint保存检测，有效值：ture | false | 'error'
  lintOnSave: process.env.NODE_ENV === 'development',
  // 如果你不需要生产环境的 source map，可以将其设置为 false 以加速生产环境构建。
  productionSourceMap: true,
  // webpack-dev-server 相关配置
  devServer: {
    host: '0.0.0.0',
    // port: 443,
    // 在代理之前处理请求
    // before: function(app) {
    //   // 处理 OPTIONS 预检请求
    //   app.options('*', (req, res) => {
    //     res.header('Access-Control-Allow-Origin', '*');
    //     res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, PATCH, OPTIONS');
    //     res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');
    //     res.sendStatus(200);
    //   });
    // },
    // https: false,
    open: true,
    // https: {
    //   key: fs.readFileSync('./server.key'),
    //   cert: fs.readFileSync('./server.crt'),
    // },
    proxy: {
      '/dev-api': {
        target: process.env.VUE_APP_BASE_ORIGIN,
        // target: 'https://192.168.124.77:4443/',
        // target: 'https://192.168.124.234:4443/',
        changeOrigin: true,
        // secure: true,
        // secure: false,
        ws: true,
        onProxyReq: function(proxyReq, req, res) {
          console.log('代理请求:', req.method, req.url)
        },
        onError(err, req, res) {
          console.log('代理错误:', err)
        },
        pathRewrite: {
          ['^' + '/dev-api']: ''
        }
      },
      '/ws': {
        // target: process.env.VUE_APP_BASE_IP,
        // target: 'http://192.168.124.204:8181',
        // target: 'http://192.168.124.77:8090/',
        target: 'wss://192.168.124.77:4443/',
        ws: true,
        // target: 'https://10.2.75.230:443',
        changeOrigin: true
      }
    },
    disableHostCheck: true
  },
  css: {
    loaderOptions: {
      sass: {
        sassOptions: { outputStyle: 'expanded' }
      }
    }
  },
  configureWebpack: {
    name: name,
    devtool: 'source-map',
    module: {
      rules: [
        {
          test: /\.geojson$/,
          use: ['json-loader']
        }
      ]
    },
    resolve: {
      alias: {
        // '@': resolve('src')
        'vue$': 'vue/dist/vue.esm.js'
      }
    },
    plugins: [
      // http://doc.ruoyi.vip/ruoyi-vue/other/faq.html#使用gzip解压缩静态文件
      new CompressionPlugin({
        cache: false, // 不启用文件缓存
        test: /\.(js|css|html|jpe?g|png|gif|svg)?$/i, // 压缩文件格式
        filename: '[path][base].gz[query]', // 压缩后的文件名
        algorithm: 'gzip', // 使用gzip压缩
        minRatio: 0.8, // 压缩比例，小于 80% 的文件不会被压缩
        deleteOriginalAssets: false // 压缩后删除原文件
      })
      // new BundleAnalyzerPlugin()
    ]
    // optimization: process.env.NODE_ENV === 'production' ? {
    //   minimize: true,
    //   minimizer: [
    //     new TerserPlugin({
    //       parallel: true, // 多线程压缩
    //       terserOptions: {
    //         compress: {
    //           drop_console: true, // 移除console
    //           drop_debugger: true // 移除debugger
    //         }
    //       }
    //     })
    //   ],
    //   splitChunks: {
    //     chunks: 'all',
    //     cacheGroups: {
    //       vendor: {
    //         name: 'chunk-vendors',
    //         test: /[\\/]node_modules[\\/]/,
    //         priority: 10,
    //         chunks: 'initial'
    //       },
    //       common: {
    //         name: 'chunk-common',
    //         minChunks: 2,
    //         priority: 5,
    //         chunks: 'initial'
    //       }
    //     }
    //   }
    // } : {}
  },
  chainWebpack(config) {
    config.plugins.delete('preload') // TODO: need test
    config.plugins.delete('prefetch') // TODO: need test

    // set svg-sprite-loader
    config.module
      .rule('svg')
      .exclude.add(resolve('src/assets/icons'))
      .end()
    config.module
      .rule('icons')
      .test(/\.svg$/)
      .include.add(resolve('src/assets/icons'))
      .end()
      .use('svg-sprite-loader')
      .loader('svg-sprite-loader')
      .options({
        symbolId: 'icon-[name]'
      })
      .end()

    config.when(process.env.NODE_ENV !== 'development', config => {
      config
        .plugin('ScriptExtHtmlWebpackPlugin')
        .after('html')
        .use('script-ext-html-webpack-plugin', [{
          // `runtime` must same as runtimeChunk name. default is `runtime`
          inline: /runtime\..*\.js$/
        }])
        .end()

      config.optimization.splitChunks({
        chunks: 'all',
        cacheGroups: {
          libs: {
            name: 'chunk-libs',
            test: /[\\/]node_modules[\\/]/,
            priority: 10,
            chunks: 'initial' // only package third parties that are initially dependent
          },
          elementUI: {
            name: 'chunk-elementUI', // split elementUI into a single package
            test: /[\\/]node_modules[\\/]_?element-ui(.*)/, // in order to adapt to cnpm
            priority: 20 // the weight needs to be larger than libs and app or it will be packaged into libs or app
          },
          commons: {
            name: 'chunk-commons',
            test: resolve('src/components'), // can customize your rules
            minChunks: 3, //  minimum common number
            priority: 5,
            reuseExistingChunk: true
          }
        }
      })
      config.optimization.runtimeChunk('single')
    })
  }
}
