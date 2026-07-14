FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY assembly ./assembly
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/target/*-dist.tar.gz /tmp/app.tar.gz
RUN tar -xzf /tmp/app.tar.gz -C /app --strip-components=1 \
    && rm -f /tmp/app.tar.gz

EXPOSE 8082
ENV JAVA_OPTS=""
ENV SPRING_ARGS="--spring.config.additional-location=/app/config/"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/boot/robot-control-service.jar $SPRING_ARGS"]
