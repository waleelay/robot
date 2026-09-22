# 运行配置模板目录

`deploy/docker/package.sh` 会把本目录中的配置模板复制进最终安装包。

配置模板可以使用 `{{变量名}}` 占位，服务器安装时会根据安装包中的 `.env` 渲染。

示例：

```text
{{LIVEKIT_REDIS_ADDRESS}}
{{LIVEKIT_WEBHOOK_URL}}
{{NGINX_LIVEKIT_UPSTREAM}}
{{NGINX_API_GATEWAY_UPSTREAM}}
```

支持的文件和目录：

```text
livekit/livekit.yaml
livekit/livekit-ingress.yaml
livekit/livekit-egress.yaml
livekit/egress.yaml
nginx/nginx.conf
nginx/html/dist
nginx/html/tdt.zip
nginx/html/tdt
tts/app.py
```

说明：

- `livekit/livekit.yaml` 会在服务器安装成 `/home/jszn/mounts/media/livekit.yaml`。
- `livekit/livekit-ingress.yaml` 会在服务器安装成 `/home/jszn/mounts/media/livekit-ingress.yaml`。
- Ingress 的 WHIP 监听固定使用宿主机内部端口 `18081`，避免与现有服务的 `8080` 冲突；首期 RTMP 接入不需要对公网开放该端口。
- `livekit/livekit-egress.yaml` 会在服务器安装成 `/home/jszn/mounts/media/livekit-egress.yaml`。
- `nginx/nginx.conf` 是唯一 Nginx 配置模板；缺失时打包或安装会直接失败。
- `nginx/html/dist` 由 `package.sh` 从 `robot-ui/dist` 复制生成，通常不需要手工维护。
- 覆盖安装 Robot UI 时不会先清空 `dist`：新静态资源先合并，`index.html` 最后原子替换，旧哈希 JS/CSS 默认保留 7 天；可通过 `ROBOT_UI_ASSET_RETENTION_DAYS` 调整宽限期。
- 推荐把 tdt 地图文件打成 `nginx/html/tdt.zip`，安装时会解压到 `/home/jszn/mounts/media/nginx/html/tdt`。
- 也兼容直接使用 `nginx/html/tdt` 目录。
- 如果需要覆盖 TTS 服务的 `/opt/tts-service/app.py`，将文件放到 `tts/app.py`。
