ARG JAVA_RUNTIME_IMAGE=robot/java17-ffmpeg-runtime:latest
FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app

COPY target/*-dist.tar.gz /tmp/app.tar.gz
RUN tar -xzf /tmp/app.tar.gz -C /app --strip-components=1 \
    && rm -f /tmp/app.tar.gz

EXPOSE 8082
ENV JAVA_OPTS=""
ENV SPRING_ARGS="--spring.config.additional-location=/app/config/"
ENV APP_RUN_MODE="foreground"
ENTRYPOINT ["/app/bin/startup.sh"]
