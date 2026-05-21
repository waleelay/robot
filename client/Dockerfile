FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN go build -o /out/robot-media-client ./cmd/robot-media-client

FROM alpine:3.20
RUN apk add --no-cache ffmpeg ca-certificates
COPY --from=build /out/robot-media-client /usr/local/bin/robot-media-client
ENTRYPOINT ["robot-media-client"]
