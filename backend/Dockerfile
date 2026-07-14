FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY assembly ./assembly
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/target/*-dist.tar.gz /tmp/app.tar.gz
RUN tar -xzf /tmp/app.tar.gz -C /app --strip-components=1 \
    && rm -f /tmp/app.tar.gz

EXPOSE 8088
ENV JAVA_OPTS=""
ENV SPRING_ARGS="--spring.config.additional-location=/app/config/"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/boot/robot-mediaserver.jar $SPRING_ARGS"]
