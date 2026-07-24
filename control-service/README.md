# robot-control-service

Standalone control service split from `backend`.

Responsibilities:

- Expose `/api/control/**` and `/ws/control`.
- Subscribe robot MQTT status topics.
- Merge management device registration with robot runtime status.
- Maintain robot online/device state in `com.robot.control.robot`.
- Call media backend `/internal/media/**` for video sessions and files.

## Device capability and state

- Robot and component registration comes from `CENTER_MANAGE_BASE_URL`.
- Management component IDs are the platform-facing `devices[].deviceId`.
- Client `devices[].status` is preserved as runtime state; the service does not generate fake online, light, volume, launcher, or tube state.
- If a driver ID differs from the platform ID, report it as `devices[].status.driverDeviceId`.
- Registered management actions take precedence. Integrated device types retain production compatibility actions and parameter limits so their existing MQTT `action/params` contracts do not change.
- Management device data is cached for 30 seconds. A refresh failure uses the most recent successful snapshot.
- Every `robot/{robotId}/media/client/status` message is merged first and then published once as `robot.state`.

## Run

```bash
mvn spring-boot:run
```

Default port: `8082`.

Useful environment variables:

```bash
CONTROL_SERVER_PORT=8082
MEDIA_SERVICE_BASE_URL=http://localhost:8088
CENTER_MANAGE_BASE_URL=http://localhost:8866
MQTT_BROKER_URL=tcp://192.168.124.77:1883
MQTT_CLIENT_ID=robot-control-service-main
MQTT_ENABLED=true
```

For local startup without MQTT:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--control.mqtt.enabled=false
```

The service exposes the existing control API surface under `/api/control/**` and `/ws/control`, and calls the media backend through `/internal/media/**`.
