# robot-control-service

Standalone control service split from `backend`.

Responsibilities:

- Expose `/api/control/**` and `/ws/control`.
- Subscribe robot MQTT status topics.
- Maintain robot online/device state in `com.robot.control.robot`.
- Call media backend `/internal/media/**` for video sessions and files.

## Run

```bash
mvn spring-boot:run
```

Default port: `8082`.

Useful environment variables:

```bash
CONTROL_SERVER_PORT=8082
MEDIA_SERVICE_BASE_URL=http://localhost:8088
MQTT_BROKER_URL=tcp://192.168.124.44:1883
MQTT_CLIENT_ID=robot-control-service
MQTT_ENABLED=true
```

For local startup without MQTT:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--control.mqtt.enabled=false
```

The service exposes the existing control API surface under `/api/control/**` and `/ws/control`, and calls the media backend through `/internal/media/**`.
