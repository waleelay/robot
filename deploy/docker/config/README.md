# 运行配置模板目录

`deploy/docker/package.sh` 会把本目录中的配置模板复制进最终安装包。

配置模板可以使用 `{{变量名}}` 占位，服务器安装时会根据安装包中的 `.env` 渲染。

示例：

```text
{{LIVEKIT_REDIS_ADDRESS}}
{{NGINX_LIVEKIT_UPSTREAM}}
```

支持的文件和目录：

```text
livekit/livekit.yaml
livekit/livekit-egress.yaml
livekit/egress.yaml
nginx/nginx.conf
nginx/html/dist
nginx/html/tdt.zip
nginx/html/tdt
tts/app.py
```

说明：

- `livekit/livekit.yaml` 会在服务器安装成 `~/mounts/media/livekit.yaml`。
- `livekit/livekit-egress.yaml` 会在服务器安装成 `~/mounts/media/livekit-egress.yaml`。
- 如果 `nginx/nginx.conf` 不存在，打包时会使用 `deploy/nginx/robot-mediaserver.conf` 作为默认 Nginx 配置。
- `nginx/html/dist` 由 `package.sh` 从 `robot-ui/dist` 复制生成，通常不需要手工维护。
- 推荐把 tdt 地图文件打成 `nginx/html/tdt.zip`，安装时会解压到 `~/mounts/media/nginx/html/tdt`。
- 也兼容直接使用 `nginx/html/tdt` 目录。
- 如果需要覆盖 TTS 服务的 `/opt/tts-service/app.py`，将文件放到 `tts/app.py`。
