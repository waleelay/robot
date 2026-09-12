import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const source = readFileSync(new URL('../src/views/bi/gis/globalMap/GlobalGisMap.vue', import.meta.url), 'utf8')
  .split('<script>')[1].split('</script>')[0]
const markers = []
const paths = []
const leaflet = {
  latLng: (lat, lng) => ({ lat, lng }),
  marker(point) {
    const marker = {
      point,
      setLatLng(next) { this.point = next },
      getLatLng() { return this.point },
      setIcon() {},
      addTo() { markers.push(this); return this },
      off() {},
      on() {}
    }
    return marker
  },
  polyline() {
    const path = { addTo() { paths.push(this); return this } }
    return path
  }
}
const exports = {}
vm.runInNewContext(require('@babel/core').transformSync(source, {
  babelrc: false, configFile: false, plugins: ['@babel/plugin-transform-modules-commonjs']
}).code, {
  exports, console,
  require(name) {
    if (name === 'leaflet') return leaflet
    if (name === 'vuex') return { mapState: () => ({}), mapActions: () => ({}) }
    if (name.endsWith('constants/robot.js')) return {
      isFixedCamera: robot => ['FIXED_CAMERA', '固定摄像头']
        .some(type => [robot.type, robot.typeCode, robot.sourceType, robot.equipmentType].includes(type))
    }
    return {}
  }
})
const methods = exports.default.methods

test('GIS 停用或无经纬度的固定摄像头不显示，刷新会移除旧 marker 和路径', () => {
  markers.length = 0
  paths.length = 0
  const camera = { robotId: 'camera', typeCode: 'FIXED_CAMERA', sourceType: 'FIXED_CAMERA', enabled: true, status: 'offline' }
  const mobile = { robotId: 'mobile', type: 'WHEELED_ROBOT' }
  const removedMarkers = []
  const removedPaths = []
  let popupClosed = 0
  const context = {
    map: { removeLayer: path => removedPaths.push(path) },
    markersLayer: { removeLayer: marker => removedMarkers.push(marker) },
    robotList: [camera, mobile],
    robotBaseInfo: { camera, mobile },
    robotLocation: {
      camera: { lat: 30, lng: 106 }, mobile: { lat: 31, lng: 107 }
    },
    gisMapCenterPoint: [0, 0],
    mapSearchValue: 'camera',
    pointMarkers: [],
    activeMarkerIndex: null,
    robotAlarmObj: {},
    measureActive: false,
    getIcon: () => ({}),
    updatePopups() {},
    closeAll() { popupClosed++ },
    hasGisCoordinate(value) { return methods.hasGisCoordinate.call(this, value) },
    isGisRobotVisible(robot) { return methods.isGisRobotVisible.call(this, robot) },
    getSearchRobot(robot) { return methods.getSearchRobot.call(this, robot) },
    resolveGisLatLng(location) { return methods.resolveGisLatLng.call(this, location) },
    initPoints() { return methods.initPoints.call(this) }
  }

  methods.initPoints.call(context)
  assert.deepEqual(Array.from(context.pointMarkers, marker => marker.meta.robot.robotId), ['camera', 'mobile'])
  assert.equal(paths.length, 2)
  const cameraMarker = context.pointMarkers[0]
  context.activeMarkerIndex = 0
  camera.enabled = false
  methods.initPoints.call(context)
  assert.deepEqual(Array.from(context.pointMarkers, marker => marker.meta.robot.robotId), ['mobile'])
  assert.deepEqual(removedMarkers, [cameraMarker])
  assert.deepEqual(removedPaths, [cameraMarker._movementPath])
  assert.equal(popupClosed, 1)
  assert.equal(context.activeMarkerIndex, null)
  assert.equal(paths.length, 2, '重复刷新不新建已有装备路径')
  assert.equal(context.getSearchRobot(), undefined, '停用摄像头不再作为 GIS 搜索结果')

  camera.enabled = true
  context.robotLocation.camera = { lat: null, lng: null }
  methods.initPoints.call(context)
  assert.deepEqual(Array.from(context.pointMarkers, marker => marker.meta.robot.robotId), ['mobile'])

  context.robotLocation.camera = { lat: 0, lng: 0 }
  exports.default.watch.robotLocation.handler.call(context, context.robotLocation)
  assert.deepEqual(Array.from(context.pointMarkers, marker => marker.meta.robot.robotId), ['camera', 'mobile'])
  assert.equal(context.pointMarkers[0].getLatLng().lat, 0)
  assert.equal(context.pointMarkers[0].getLatLng().lng, 0)
  assert.equal(context.getSearchRobot().robotId, 'camera')

  context.robotLocation.camera = { lat: null, lng: null }
  exports.default.watch.robotLocation.handler.call(context, context.robotLocation)
  assert.deepEqual(Array.from(context.pointMarkers, marker => marker.meta.robot.robotId), ['mobile'])
})
