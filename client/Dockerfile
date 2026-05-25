FROM golang:1.24-alpine AS build
WORKDIR /src
RUN apk add --no-cache build-base pkgconf opus-dev
COPY go.mod ./
COPY go.sum ./
RUN go mod download
COPY . .
RUN go build -tags nolibopusfile -o /out/robot-media-client ./cmd/robot-media-client

FROM alpine:3.20
RUN apk add --no-cache ffmpeg ca-certificates opus gstreamer gst-plugins-base gst-plugins-good
COPY --from=build /out/robot-media-client /usr/local/bin/robot-media-client
ENTRYPOINT ["robot-media-client"]
