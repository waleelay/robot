ARG BASE_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${BASE_IMAGE}

ARG APT_MIRROR=
ARG APT_PORTS_MIRROR=

RUN if [ -n "$APT_MIRROR" ]; then \
      sed -i "s|http://archive.ubuntu.com/ubuntu|$APT_MIRROR|g; s|http://security.ubuntu.com/ubuntu|$APT_MIRROR|g" /etc/apt/sources.list; \
    fi \
    && if [ -n "$APT_PORTS_MIRROR" ]; then \
      sed -i "s|http://ports.ubuntu.com/ubuntu-ports|$APT_PORTS_MIRROR|g" /etc/apt/sources.list; \
    fi \
    && apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*
