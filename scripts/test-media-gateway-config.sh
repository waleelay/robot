#!/usr/bin/env sh
# 只校验配置结构，不启动容器、不访问 MQTT，也不输出凭据。
set -eu
cd "$(dirname "$0")/.."

node <<'NODE'
const assert = require('assert');
const fs = require('fs');
const { execFileSync } = require('child_process');

const application = fs.readFileSync('control-service/src/main/resources/application.yml', 'utf8');
assert(application.includes('media-gateway-id: ${MEDIA_GATEWAY_ID:default}'), 'Control 必须读取统一 Gateway ID');
assert(application.includes('gateway-timeout-seconds: ${FIXED_CAMERA_GATEWAY_TIMEOUT_SECONDS:30}'), '固定摄像头健康超时配置必须与部署变量一致');
assert(!application.includes('${EDGE_MEDIA_GATEWAY'), 'Control 不能残留无效的 Gateway 环境变量');

const updates = fs.readFileSync('deploy/docker/update-services.env', 'utf8');
for (const variable of ['FIXED_CAMERA_PUBLISHER_TRUSTED_CALLER', 'FIXED_CAMERA_PUBLISHER_PRESENCE_TIMEOUT_SECONDS', 'FIXED_CAMERA_PUBLISHER_PRESENCE_POLL_MILLIS']) {
  assert(application.includes('${' + variable + ':') && updates.includes('control-service ' + variable + '='), '更新脚本必须使用 Control 实际读取的发布切换变量');
}
assert(updates.includes('media-gateway MEDIA_GATEWAY_HEARTBEAT_INTERVAL_SECONDS='), '更新脚本必须定位统一媒体服务');

for (const file of ['deploy/docker/docker-compose.yml', 'deploy/docker/docker-compose.host.yml']) {
  // 非默认值用于发现两端都恰好使用 default 而掩盖的配置错配。
  const environment = {
    ...process.env,
    MEDIA_GATEWAY_ID: 'convergence-check',
    MEDIA_GATEWAY_CLIENT_ID: '',
    MEDIA_GATEWAY_FFMPEG_PUBLISHER_CMD: '',
    MEDIA_GATEWAY_GSTREAMER_PIPELINE: '',
    FIXED_CAMERA_GATEWAY_TIMEOUT_SECONDS: '47'
  };
  const config = JSON.parse(execFileSync('docker', ['compose', '--profile', 'media-gateway', '-f', file, 'config', '--format', 'json'], {
    encoding: 'utf8', env: environment
  }));
  const gateway = config.services['media-gateway'];
  const control = config.services['control-service'];
  assert(gateway && !config.services['fixed-camera-gateway'], '不得保留第二个固定摄像头服务入口');
  assert(gateway.profiles.includes('media-gateway'), '统一媒体 profile 无效');
  assert(gateway.image.includes('/media-gateway:') && gateway.container_name.endsWith('-media-gateway'), '发布物名称未收敛');
  assert.deepStrictEqual(gateway.entrypoint, ['media-gateway']);
  assert.strictEqual(control.environment.MEDIA_GATEWAY_ID, 'convergence-check');
  assert.strictEqual(gateway.environment.MEDIA_GATEWAY_ID, 'convergence-check');
  assert.strictEqual(gateway.environment.MEDIA_GATEWAY_CLIENT_ID, 'media-gateway-convergence-check');
  assert.strictEqual(control.environment.FIXED_CAMERA_GATEWAY_TIMEOUT_SECONDS, '47');
  for (const variable of ['FFMPEG_PUBLISHER_CMD', 'GSTREAMER_PIPELINE']) {
    assert(gateway.environment[variable].includes('{input}') && !gateway.environment[variable].includes('{rtsp}'), '默认发布模板必须使用通用输入占位符');
  }
  process.stdout.write(file + '：服务身份、模板与非默认实例配置校验通过\n');
}
process.stdout.write('Control 占位符与更新脚本变量校验通过\n');
NODE
