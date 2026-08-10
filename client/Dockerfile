FROM golang:1.26-alpine AS build
WORKDIR /src
RUN apk add --no-cache build-base git pkgconf opus-dev soxr-dev gstreamer-dev gst-plugins-base-dev
COPY go.mod ./
COPY go.sum ./
RUN go mod download
COPY . .
RUN go build -tags nolibopusfile -o /out/robot-media-client ./cmd/robot-media-client
RUN go build -tags nolibopusfile -o /out/fixed-camera-gateway ./cmd/fixed-camera-gateway
RUN GOPROXY=https://goproxy.cn,direct sh scripts/install-gstreamer-publisher.sh && cp /root/.local/bin/gstreamer-publisher /out/gstreamer-publisher

FROM alpine:3.20
RUN apk add --no-cache bash ffmpeg ca-certificates opus gstreamer gst-plugins-base gst-plugins-good gst-plugins-bad
WORKDIR /app
COPY --from=build /out/robot-media-client /usr/local/bin/robot-media-client
COPY --from=build /out/fixed-camera-gateway /usr/local/bin/fixed-camera-gateway
COPY --from=build /out/gstreamer-publisher /usr/local/bin/gstreamer-publisher
COPY scripts/ ./scripts/
RUN chmod +x ./scripts/*.sh
ENTRYPOINT ["robot-media-client"]
