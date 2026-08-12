package com.robot.control.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EdgeTaskEventHandlerTest {

    private final MediaWebSocketPublisher webSocketPublisher = mock(MediaWebSocketPublisher.class);
    private final EdgeTaskEventHandler handler = new EdgeTaskEventHandler(new ObjectMapper(), webSocketPublisher);

    @Test
    void publishesPanoramaTaskChangedForProgressReport() {
        handler.handleProgress("eiop/v1/edge/PATROL-001/tasks/progress", """
                {
                  "messageId": "msg-progress-001",
                  "messageType": "TASK_PROGRESS_REPORT",
                  "schemaVersion": "1.0",
                  "timestamp": "2026-07-01T10:01:00Z",
                  "payload": {
                    "taskInstanceId": 1001,
                    "taskId": 50,
                    "commandId": "cmd-patrol-001",
                    "status": "RUNNING",
                    "programKey": "patrol_route",
                    "nodeId": "navigate_fire_point",
                    "actionRef": "ptz_reset_home:dual_light_ptz",
                    "resultCode": "NODE_RUNNING",
                    "currentLocation": {"x": 9.2, "y": 7.8, "z": 0, "yaw": 88},
                    "message": "navigating to fire point"
                  }
                }
                """);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).publish(eq("panorama.task.changed"), captor.capture());
        Map<String, Object> data = captor.getValue();
        assertThat(data)
                .containsEntry("taskId", 50)
                .containsEntry("workflowInstanceId", 1001)
                .containsEntry("robotId", "PATROL-001")
                .containsEntry("status", "running")
                .containsEntry("statusName", "执行中")
                .containsEntry("currentLocation", "x:9.2,y:7.8")
                .containsEntry("programKey", "patrol_route")
                .containsEntry("source", "EDGE_TASK_MQTT");
        assertThat(data).doesNotContainKeys("task", "deviceTaskInstanceId");
        assertThat(map(data.get("location")))
                .containsEntry("x", 9.2)
                .containsEntry("y", 7.8);
    }

    @Test
    void publishesPanoramaTaskChangedForControlResultReport() {
        handler.handleControlResult("eiop/v1/edge/PATROL-001/tasks/control-results", """
                {
                  "messageId": "msg-control-result-001",
                  "messageType": "TASK_CONTROL_RESULT_REPORT",
                  "schemaVersion": "1.0",
                  "timestamp": "2026-07-01T10:01:32Z",
                  "payload": {
                    "taskInstanceId": 1001,
                    "controlCommandId": "ctrl-001",
                    "result": "SUCCEEDED"
                  }
                }
                """);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(webSocketPublisher).publish(eq("panorama.task.changed"), captor.capture());
        Map<String, Object> data = captor.getValue();
        assertThat(data)
                .containsEntry("robotId", "PATROL-001")
                .containsEntry("workflowInstanceId", 1001)
                .containsEntry("controlCommandId", "ctrl-001")
                .containsEntry("controlResult", "SUCCEEDED");
        assertThat(data).doesNotContainKeys("task", "deviceTaskInstanceId");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
