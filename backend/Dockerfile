ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app

COPY target/*-dist.tar.gz /tmp/app.tar.gz
RUN tar -xzf /tmp/app.tar.gz -C /app --strip-components=1 \
    && rm -f /tmp/app.tar.gz

EXPOSE 8088
ENV JAVA_OPTS=""
ENV SPRING_ARGS="--spring.config.additional-location=/app/config/"
ENV APP_RUN_MODE="foreground"
ENTRYPOINT ["/app/bin/startup.sh"]
