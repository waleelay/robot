# Docker 打包与部署手册

本文档按实际操作顺序说明：项目组成、打包前准备、构建安装包、上传部署、配置生效、更新、卸载和常见问题。

## 1. 项目模块

安装包包含以下服务：

```text
media-service      媒体服务，来源 backend
control-service    控制服务，来源 control-service
bigscreen-bff      大屏 BFF，来源 bigscreen-bff
livekit-server     LiveKit Server
livekit-egress     LiveKit Egress
nginx              前端静态资源和反向代理
tts                可选 TTS 服务，默认不启动
```

MySQL、Redis、EMQX、MinIO、Elasticsearch 等中间件不在安装包内，需要在 `.env` 中配置为容器可访问地址。

默认端口：

```text
Nginx HTTP：        80/tcp
Nginx HTTPS：       4443/tcp
Nginx 备用 HTTP：   280/tcp
Nginx 备用 HTTPS：  24443/tcp
媒体服务：          8088/tcp
控制服务：          8082/tcp
大屏 BFF：          8090/tcp
LiveKit API：       7880/tcp
LiveKit RTC TCP：   7881/tcp
LiveKit RTC UDP：   50000-50100/udp
TTS：               5050/tcp，默认不启动
```

对外访问通常使用：

```text
前端页面：      https://服务器IP:4443/
地图瓦片：      https://服务器IP:4443/tdt/...
LiveKit：       ws://服务器IP:7880
```

## 2. 打包前准备

在构建机进入项目根目录：

```bash
cd /Users/leelay/Documents/robot-mediaserver
```

### 2.1 准备目录

```bash
mkdir -p deploy/docker/config/livekit
mkdir -p deploy/docker/config/nginx/html
mkdir -p deploy/docker/config/tts
mkdir -p deploy/docker/tool-images/amd64
mkdir -p deploy/docker/tool-images/arm64
mkdir -p dist
```

### 2.2 准备 LiveKit 配置

如果已有配置文件，放到：

```text
deploy/docker/config/livekit/livekit.yaml
deploy/docker/config/livekit/livekit-egress.yaml
```

如果不提供，安装时会按 `.env` 自动生成默认配置。

注意：安装后 LiveKit 使用单文件挂载，目标路径必须是文件，不能是目录：

```text
~/mounts/media/livekit.yaml        -> /livekit.yaml
~/mounts/media/livekit-egress.yaml -> /livekit-egress.yaml
```

### 2.3 准备 Nginx 配置和前端

Nginx 配置优先读取：

```text
deploy/docker/config/nginx/nginx.conf
```

如果不存在，打包时会使用：

```text
deploy/nginx/robot-mediaserver.conf
```

前端不需要手动构建。`package.sh` 默认每次都会执行：

```bash
cd robot-ui
npm run build:prod
```

然后复制最新的：

```text
robot-ui/dist -> config/nginx/html/dist
```

如果只是调试打包流程，确认已有 `robot-ui/dist` 且想跳过前端编译，可以在构建时设置：

```bash
ROBOT_UI_BUILD=false TARGET_ARCH=amd64 ./package.sh
```

### 2.4 准备 tdt 地图

tdt 文件很多时，推荐准备 `tar.gz`，Linux 离线服务器通常自带 `tar`，不依赖 `unzip`：

```text
deploy/docker/config/nginx/html/tdt.tar.gz
```

安装时会解压到：

```text
~/mounts/media/nginx/html/tdt
```

也支持直接放目录：

```text
deploy/docker/config/nginx/html/tdt
```

如果 `tar.gz` 在其他路径，构建时指定：

```bash
cd deploy/docker
TDT_TAR=/path/to/tdt.tar.gz TARGET_ARCH=amd64 ./package.sh
```

也兼容旧的 `zip`：

```text
deploy/docker/config/nginx/html/tdt.zip
```

如果 zip 在其他路径，构建时指定：

```bash
cd deploy/docker
TDT_ZIP=/path/to/tdt.zip TARGET_ARCH=amd64 ./package.sh
```

如果服务器没有 `unzip`，优先改用 `tdt.tar.gz`。也可以先跳过地图，部署后再把解压好的 `tdt` 目录放到：

```text
~/mounts/media/nginx/html/tdt
```

### 2.5 准备 TTS

TTS 默认不启动。需要启动时，在部署服务器 `.env` 中设置：

```text
COMPOSE_PROFILES=tts
```

如果 TTS 镜像要求挂载 `app.py`，打包前准备：

```text
deploy/docker/config/tts/app.py
```

安装后会复制到：

```text
~/mounts/media/tts/app.py
```

### 2.6 准备第三方镜像

非代码镜像需要提前放到目标架构目录：

```text
deploy/docker/tool-images/amd64/
deploy/docker/tool-images/arm64/
```

推荐文件名：

```text
java-runtime.tar.gz
livekit-server.tar.gz
livekit-egress.tar.gz
nginx.tar.gz
tts.tar.gz
```

示例，准备 amd64：

```bash
cd /Users/leelay/Documents/robot-mediaserver

docker buildx build --platform linux/amd64 --load -t robot/java17-ffmpeg-runtime:amd64 deploy/docker/java-runtime
docker pull --platform linux/amd64 livekit/livekit-server:latest
docker pull --platform linux/amd64 livekit/egress:latest
docker pull --platform linux/amd64 nginx:alpine

mkdir -p deploy/docker/tool-images/amd64

docker image save --platform linux/amd64 robot/java17-ffmpeg-runtime:amd64 | gzip -9 > deploy/docker/tool-images/amd64/java-runtime.tar.gz
docker image save --platform linux/amd64 livekit/livekit-server:latest | gzip -9 > deploy/docker/tool-images/amd64/livekit-server.tar.gz
docker image save --platform linux/amd64 livekit/egress:latest | gzip -9 > deploy/docker/tool-images/amd64/livekit-egress.tar.gz
docker image save --platform linux/amd64 nginx:alpine | gzip -9 > deploy/docker/tool-images/amd64/nginx.tar.gz
```

示例，准备 arm64：

```bash
cd /Users/leelay/Documents/robot-mediaserver

docker buildx build --platform linux/arm64 --load -t robot/java17-ffmpeg-runtime:arm64 deploy/docker/java-runtime
docker pull --platform linux/arm64 livekit/livekit-server:latest
docker pull --platform linux/arm64 livekit/egress:latest
docker pull --platform linux/arm64 nginx:alpine

mkdir -p deploy/docker/tool-images/arm64

docker image save --platform linux/arm64 robot/java17-ffmpeg-runtime:arm64 | gzip -9 > deploy/docker/tool-images/arm64/java-runtime.tar.gz
docker image save --platform linux/arm64 livekit/livekit-server:latest | gzip -9 > deploy/docker/tool-images/arm64/livekit-server.tar.gz
docker image save --platform linux/arm64 livekit/egress:latest | gzip -9 > deploy/docker/tool-images/arm64/livekit-egress.tar.gz
docker image save --platform linux/arm64 nginx:alpine | gzip -9 > deploy/docker/tool-images/arm64/nginx.tar.gz
```

如果 apt 源较慢，可以指定 Ubuntu 镜像源，例如：

```bash
docker buildx build --platform linux/amd64 --load \
  --build-arg APT_MIRROR=http://mirrors.aliyun.com/ubuntu \
  -t robot/java17-ffmpeg-runtime:amd64 \
  deploy/docker/java-runtime
```

arm64 需要使用 Ubuntu ports 源：

```bash
docker buildx build --platform linux/arm64 --load \
  --build-arg APT_PORTS_MIRROR=http://mirrors.aliyun.com/ubuntu-ports \
  -t robot/java17-ffmpeg-runtime:arm64 \
  deploy/docker/java-runtime
```

确认 Java runtime 镜像包含 HLS 需要的命令：

```bash
docker run --rm --platform linux/amd64 robot/java17-ffmpeg-runtime:amd64 sh -c 'java -version && ffmpeg -version && ffprobe -version'
docker run --rm --platform linux/arm64 robot/java17-ffmpeg-runtime:arm64 sh -c 'java -version && ffmpeg -version && ffprobe -version'
```

`livekit/egress` 镜像较大，gzip 变小不明显时可以用 `xz` 或 `zstd`：

```bash
docker image save --platform linux/amd64 livekit/egress:latest | xz -T0 -9e > deploy/docker/tool-images/amd64/livekit-egress.tar.xz
```

## 3. 构建安装包

构建 amd64：

```bash
cd /Users/leelay/Documents/robot-mediaserver/deploy/docker
TARGET_ARCH=amd64 JAVA_RUNTIME_IMAGE=robot/java17-ffmpeg-runtime:amd64 ./package.sh
```

构建 arm64：

```bash
cd /Users/leelay/Documents/robot-mediaserver/deploy/docker
TARGET_ARCH=arm64 JAVA_RUNTIME_IMAGE=robot/java17-ffmpeg-runtime:arm64 ./package.sh
```

`JAVA_RUNTIME_IMAGE` 不是必须每次都传。`package.sh` 会优先读取命令行环境变量，其次读取 `.env` / `.env.example` 中的 `JAVA_RUNTIME_IMAGE`，最后默认使用 `robot/java17-ffmpeg-runtime:latest`。但在同一台 MacBook 上交替构建 amd64 和 arm64 时，建议像上面一样显式传入架构专用镜像，避免 `.env` 或 `latest` 指向上一次构建的架构。

`package.sh` 会做这些事：

```text
1. Maven clean package 重新编译 backend / control-service / bigscreen-bff
2. npm run build:prod 重新编译 robot-ui
3. 构建指定架构的 Java 服务镜像
4. 保存 Java 服务镜像到 images/
5. 复制 tool-images/<arch> 下的第三方镜像
6. 复制 LiveKit / Nginx / TTS / 前端 / tdt 配置和资源
7. 生成最终 tar.gz 安装包
```

Java 发布包结构：

```text
bin/
boot/     # 只放启动 jar
config/
lib/      # 依赖 jar
logs/
```

安装包输出：

```text
dist/robot-mediaserver-installer-<arch>-<timestamp>.tar.gz
```

## 4. 上传部署

以下示例假设服务器用户是 `jszn`，安装包上传到 `/home/jszn`。

### 4.1 解压安装包

```bash
cd /home/jszn
tar -xzf robot-mediaserver-installer-amd64-*.tar.gz
cd robot-mediaserver-installer-amd64-*
cp .env.example .env
```

### 4.2 基础配置

amd64 bridge 模式示例：

```bash
sed -i 's#^TARGET_ARCH=.*#TARGET_ARCH=amd64#' .env
sed -i 's#^TARGET_PLATFORM=.*#TARGET_PLATFORM=linux/amd64#' .env
sed -i 's#^DEPLOY_NETWORK_MODE=.*#DEPLOY_NETWORK_MODE=bridge#' .env
sed -i 's#^DOCKER_NETWORK_SUBNET=.*#DOCKER_NETWORK_SUBNET=10.253.10.0/24#' .env
sed -i 's#^APP_WORKSPACE_ROOT=.*#APP_WORKSPACE_ROOT=/root/mounts/media#' .env
sed -i 's#^INSTALL_MODE=.*#INSTALL_MODE=overwrite#' .env
```

控制服务和大屏 BFF 都需要访问管理中心。管理中心部署在当前服务器时，bridge 模式可保留默认值；部署在其他服务器时改为实际内部地址：

```bash
sed -i 's#^CENTER_MANAGE_BASE_URL=.*#CENTER_MANAGE_BASE_URL=http://管理中心IP:8866#' .env
```

如果是 MacBook / OrbStack 部署，运行目录建议使用当前用户目录：

```bash
sed -i '' 's#^APP_WORKSPACE_ROOT=.*#APP_WORKSPACE_ROOT=/Users/用户名/mounts/media#' .env
```

### 4.3 一键配置 IP

如果内外访问都是同一个 IP：

```bash
./configure-env-ip.sh 192.168.124.77
```

如果容器访问中间件用内网 IP，下发给浏览器/机器人用公网 IP：

```bash
./configure-env-ip.sh --internal-ip 10.222.123.5 --external-ip 175.155.35.79
```

脚本会自动：

```text
将 host.docker.internal 替换成 internal-ip
设置 LIVEKIT_URL=ws://external-ip:7880
设置 LIVEKIT_NODE_IP=external-ip
设置 NGINX_TLS_HOST=external-ip
追加 https://external-ip:4443 到 BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS
```

外部中间件不在本机时，继续按实际情况修改：

```bash
grep --color=never -nE '^(MYSQL_URL|MYSQL_USERNAME|MYSQL_PASSWORD|REDIS_HOST|REDIS_PORT|MINIO_ENDPOINT|MQTT_BROKER_URL|ELASTICSEARCH_URIS|CENTER_MANAGE_BASE_URL)=' .env
```

MySQL 首次部署希望自动创建 `robot_media` 数据库时，`MYSQL_URL` 需要包含：

```text
createDatabaseIfNotExist=true
```

前提是 MySQL 用户有 `CREATE DATABASE` 权限。

### 4.4 OpenStack bridge 模式预检

OpenStack 或云服务器使用 bridge 模式前先看路由：

```bash
ip route
ip addr
```

确认 `DOCKER_NETWORK_SUBNET` 不和业务网段、VPN 网段、已有 Docker 网络冲突，再执行：

```bash
./preflight-network.sh
```

预检通过才安装：

```bash
./install.sh
```

如果服务器一启动 Docker daemon 就断网，说明问题发生在 Compose 之前，需要先处理 Docker daemon 的 `bip/default-address-pools`，并保留控制台/VNC 等带外入口。

### 4.5 安装后验证

```bash
docker ps -a | grep robot-mediaserver
docker network inspect robot-mediaserver --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}'
ip route | grep -E 'docker0|br-|10.253'
```

端口验证：

```bash
curl -I --max-time 5 http://127.0.0.1:8088
curl -I --max-time 5 http://127.0.0.1:8082
curl -I --max-time 5 http://127.0.0.1:8090
curl -I --max-time 5 http://127.0.0.1:7880
curl -kI --max-time 5 https://127.0.0.1:4443
```

日志验证：

```bash
docker logs robot-mediaserver-media-service --tail 80
docker logs robot-mediaserver-control-service --tail 80
docker logs robot-mediaserver-bigscreen-bff --tail 80
docker logs robot-mediaserver-livekit-server --tail 80
```

浏览器跨域预检验证：

```bash
curl -k -i -X OPTIONS \
  'https://175.155.35.79:4443/api/control/robots/test111/cameras/camera01/video/start' \
  -H 'Origin: https://175.155.35.79:4443' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

## 5. 配置修改如何生效

修改安装包目录中的 `.env` 后，最稳的一键生效方式是：

```bash
./preflight-network.sh && sed -i 's#^INSTALL_MODE=.*#INSTALL_MODE=overwrite#' .env && ./prepare-workspace.sh && docker compose -f docker-compose.yml up -d --force-recreate
```

这条命令会完成：

1. 检查 bridge 网络是否和服务器路由冲突。
2. 根据 `.env` 重新渲染 LiveKit / Nginx 等配置文件。
3. 重建容器，让新的环境变量进入容器。

注意：只执行 `docker compose restart` 不会重新读取 `.env` 中的容器环境变量。

### 5.1 只改 `.env` 容器环境变量

例如改了 `MYSQL_URL`、`REDIS_HOST`、`LIVEKIT_URL`、`CENTER_*`、`BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS`：

```bash
docker compose -f docker-compose.yml up -d --force-recreate
```

### 5.2 改了 LiveKit / Nginx 模板相关配置

例如改了 `LIVEKIT_NODE_IP`、`LIVEKIT_REDIS_ADDRESS`、`NGINX_*_UPSTREAM`、`NGINX_TLS_HOST`：

```bash
sed -i 's#^INSTALL_MODE=.*#INSTALL_MODE=overwrite#' .env
./prepare-workspace.sh
docker compose -f docker-compose.yml up -d --force-recreate
```

### 5.3 改了服务目录里的配置文件

如果直接改的是：

```text
~/mounts/media/media-service/config
~/mounts/media/control-service/config
~/mounts/media/bigscreen-bff/config
```

重启对应容器：

```bash
docker compose -f docker-compose.yml restart media-service control-service bigscreen-bff
```

## 6. 更新

### 6.1 全量更新

上传新的安装包后：

```bash
cd /home/jszn
tar -xzf robot-mediaserver-installer-amd64-*.tar.gz
cd robot-mediaserver-installer-amd64-*
cp .env.example .env
```

按第 4 节重新配置 `.env`，然后覆盖安装：

```bash
sed -i 's#^INSTALL_MODE=.*#INSTALL_MODE=overwrite#' .env
./preflight-network.sh
./install.sh
```

### 6.2 增量更新 Java 服务

本地重新打包：

```bash
cd /Users/leelay/Documents/robot-mediaserver/backend
mvn -DskipTests clean package

cd /Users/leelay/Documents/robot-mediaserver/control-service
mvn -DskipTests clean package
```

上传到服务器：

```bash
scp -P 32272 /Users/leelay/Documents/robot-mediaserver/backend/target/robot-mediaserver-dist.tar.gz jszn@175.155.35.79:/tmp/
scp -P 32272 /Users/leelay/Documents/robot-mediaserver/control-service/target/robot-control-service-dist.tar.gz jszn@175.155.35.79:/tmp/
```

服务器解压确认：

```bash
rm -rf /tmp/update-media /tmp/update-control
mkdir -p /tmp/update-media /tmp/update-control

tar -xzf /tmp/robot-mediaserver-dist.tar.gz -C /tmp/update-media --strip-components=1
tar -xzf /tmp/robot-control-service-dist.tar.gz -C /tmp/update-control --strip-components=1

ls -l /tmp/update-media/boot
ls -l /tmp/update-control/boot
```

替换运行目录：

```bash
WORKSPACE=/home/jszn/mounts/media

rm -rf "$WORKSPACE/media-service/bin" "$WORKSPACE/media-service/boot" "$WORKSPACE/media-service/lib"
cp -a /tmp/update-media/bin /tmp/update-media/boot /tmp/update-media/lib "$WORKSPACE/media-service/"

rm -rf "$WORKSPACE/control-service/bin" "$WORKSPACE/control-service/boot" "$WORKSPACE/control-service/lib"
cp -a /tmp/update-control/bin /tmp/update-control/boot /tmp/update-control/lib "$WORKSPACE/control-service/"

ls -l "$WORKSPACE/media-service/boot"
ls -l "$WORKSPACE/control-service/boot"
```

重启：

```bash
docker compose -f docker-compose.yml restart media-service control-service bigscreen-bff
sleep 8
docker ps -a | grep robot-mediaserver
docker logs robot-mediaserver-media-service --tail 60
docker logs robot-mediaserver-control-service --tail 60
```

### 6.3 增量更新前端

本地重新构建并上传 `dist`：

```bash
cd /Users/leelay/Documents/robot-mediaserver/robot-ui
npm run build:prod
scp -P 32272 -r dist/* jszn@175.155.35.79:/home/jszn/mounts/media/nginx/html/dist/
```

Nginx 静态资源替换后一般不需要重启。

## 7. 卸载

默认卸载：

```bash
./uninstall.sh
```

会停止并删除 compose 容器、删除 compose 网络、删除 orphan 容器。

默认不会删除：

```text
Docker 镜像
安装包目录
.env
images/
packages/
APP_WORKSPACE_ROOT 对应的挂载目录
```

删除 compose volume：

```bash
./uninstall.sh --volumes
```

同时删除运行目录：

```bash
./uninstall.sh --purge-workspace
```

同时删除 volume 和运行目录：

```bash
./uninstall.sh --volumes --purge-workspace
```

脚本会拒绝删除 `/`、`$HOME` 等危险路径。当前脚本不会删除 Docker 镜像，如需删除镜像需手动 `docker rmi`。

## 8. 常见问题

### 8.1 OpenStack bridge 导致网络异常

先确认当前路由：

```bash
ip route
ip addr
```

选择一个不冲突的网段：

```bash
sed -i 's#^DOCKER_NETWORK_SUBNET=.*#DOCKER_NETWORK_SUBNET=10.253.10.0/24#' .env
./preflight-network.sh
```

如果预检失败，换一个网段：

```bash
sed -i 's#^DOCKER_NETWORK_SUBNET=.*#DOCKER_NETWORK_SUBNET=10.254.10.0/24#' .env
./preflight-network.sh
```

### 8.2 `tdt` 目录不存在

如果安装时跳过了 `tdt.tar.gz`，手动解压：

```bash
mkdir -p /home/jszn/mounts/media/nginx/html
tar -xzf config/nginx/html/tdt.tar.gz -C /home/jszn/mounts/media/nginx/html
```

如果压缩包内部已经包含顶层 `tdt/` 目录，解压到 `html` 即可；如果压缩包内部直接是 `latest/`，则解压到 `html/tdt`：

```bash
mkdir -p /home/jszn/mounts/media/nginx/html/tdt
tar -xzf config/nginx/html/tdt.tar.gz -C /home/jszn/mounts/media/nginx/html/tdt
```

验证：

```bash
curl -kI --max-time 5 https://127.0.0.1:4443/tdt/latest/18/208281/107518.png
```

### 8.3 `403 Invalid CORS request`

把前端访问源加入 CORS：

```bash
sed -i 's#^BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS=.*#BIGSCREEN_CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:8080,http://127.0.0.1:8080,http://192.168.*.*:8080,https://192.168.*.*:4443,https://175.155.35.79:4443#' .env
docker compose -f docker-compose.yml up -d --force-recreate bigscreen-bff
```

更推荐使用：

```bash
./configure-env-ip.sh --internal-ip 10.222.123.5 --external-ip 175.155.35.79
docker compose -f docker-compose.yml up -d --force-recreate bigscreen-bff
```

### 8.4 `Unknown database 'robot_media'`

确认 `MYSQL_URL` 包含：

```text
createDatabaseIfNotExist=true
```

例如：

```text
MYSQL_URL=jdbc:mysql://10.222.123.5:3307/robot_media?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
```

MySQL 用户必须有建库权限。没有权限时手动建库：

```sql
CREATE DATABASE IF NOT EXISTS robot_media
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

### 8.5 `host.docker.internal` 不通

Linux 服务器建议直接替换为服务器内网 IP：

```bash
./configure-env-ip.sh --internal-ip 10.222.123.5 --external-ip 175.155.35.79
```

### 8.6 LiveKit 推流成功但前端无画面

重点检查：

```bash
grep --color=never -nE '^(LIVEKIT_URL|LIVEKIT_NODE_IP|LIVEKIT_EGRESS_WS_URL)=' .env
docker logs robot-mediaserver-livekit-server --tail 80
```

外部客户端访问时通常应配置：

```text
LIVEKIT_URL=ws://外部IP:7880
LIVEKIT_NODE_IP=外部IP
```

同时确认防火墙或安全组放通：

```text
7880/tcp
7881/tcp
50000-50100/udp
```

### 8.7 单文件挂载被创建成目录

如果报类似：

```text
read /livekit.yaml is a directory
mount ... nginx.conf ... not a directory
```

说明在源文件不存在时启动过 compose，Docker 创建了同名目录。处理：

```bash
sed -i 's#^INSTALL_MODE=.*#INSTALL_MODE=overwrite#' .env
./prepare-workspace.sh
docker compose -f docker-compose.yml up -d --force-recreate
```

### 8.8 `.env` 改了但没生效

环境变量改动需要重建容器：

```bash
docker compose -f docker-compose.yml up -d --force-recreate
```

LiveKit/Nginx 模板改动还要重新渲染：

```bash
sed -i 's#^INSTALL_MODE=.*#INSTALL_MODE=overwrite#' .env
./prepare-workspace.sh
docker compose -f docker-compose.yml up -d --force-recreate
```

### 8.9 解压提示 `LIBARCHIVE.xattr.com.apple.provenance`

这是 macOS 文件扩展属性被打进 tar 包后，Linux GNU tar 不识别对应扩展头产生的提示，一般不影响安装包内容。

新版本 `package.sh` 会在生成安装包前清理 staging 目录的 xattr，并使用 `COPYFILE_DISABLE=1` 打包。旧安装包如果只是看到下面提示，可以继续安装：

```text
tar: 忽略未知的扩展头关键字‘LIBARCHIVE.xattr.com.apple.provenance’
```
