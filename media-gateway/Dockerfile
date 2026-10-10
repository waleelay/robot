FROM golang:1.26-alpine AS build
WORKDIR /src
RUN apk add --no-cache build-base git pkgconf gstreamer-dev gst-plugins-base-dev
COPY go.mod ./
COPY go.sum ./
RUN go mod download
COPY . .
RUN go build -o /out/media-gateway ./cmd/media-gateway
RUN GOPROXY=https://goproxy.cn,direct sh scripts/install-gstreamer-publisher.sh && cp /root/.local/bin/gstreamer-publisher /out/gstreamer-publisher

FROM alpine:3.20
LABEL robot.media-gateway.protocol="edge-media-v1"
RUN apk add --no-cache bash ffmpeg ca-certificates gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad
WORKDIR /app
COPY --from=build /out/media-gateway /usr/local/bin/media-gateway
COPY --from=build /out/gstreamer-publisher /usr/local/bin/gstreamer-publisher
COPY scripts/ ./scripts/
RUN chmod +x ./scripts/*.sh
ENTRYPOINT ["media-gateway"]
