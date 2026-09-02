package com.robot.control.trajectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robot.control.messaging.EquipmentControlCommandPublisher;
import com.robot.control.ws.MediaWebSocketPublisher;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class TrajectoryCoordinatorTest {

    @Test
    void restoresForAllWatchersWhenTaskStarts() throws Exception {
        Harness harness = new Harness();
        WebSocketSession first = harness.session("ws-1");
        WebSocketSession second = harness.session("ws-2");
        harness.watch(first);
        harness.watch(second);

        harness.coordinator.observeTaskInstance("robot-1", 42);
        Map<String, Object> probe = harness.nextCommand();
        assertThat(probe).containsEntry("taskInstanceId", 42L).containsEntry("format", "summary");
        harness.reply(probe, "recording", 1, List.of(), false);

        Map<String, Object> restore = harness.nextCommand();
        assertThat(restore).containsEntry("format", "full").containsEntry("maxPoints", 500);
        harness.reply(restore, "recording", 1, List.of(harness.point(0, 1, 2)), false);

        assertThat(harness.actions(first)).containsExactly("RESET");
        assertThat(harness.actions(second)).containsExactly("RESET");
    }

    @Test
    void restoresNewWatcherWithoutResettingExistingWatcher() throws Exception {
        Harness harness = new Harness();
        WebSocketSession first = harness.session("ws-1");
        harness.watch(first);
        harness.coordinator.observeTaskInstance("robot-1", 42);
        Map<String, Object> probe = harness.nextCommand();
        harness.reply(probe, "recording", 1, List.of(), false);
        Map<String, Object> initialRestore = harness.nextCommand();
        harness.reply(initialRestore, "recording", 1, List.of(harness.point(0, 1, 2)), false);
        harness.clearEvents();

        WebSocketSession second = harness.session("ws-2");
        harness.watch(second);
        int scheduledBeforeThirdWatcher = harness.tasks.size();
        WebSocketSession third = harness.session("ws-3");
        harness.watch(third);
        assertThat(harness.tasks).hasSize(scheduledBeforeThirdWatcher);
        Map<String, Object> joiningRestore = harness.nextCommand();
        assertThat(joiningRestore).containsEntry("format", "full").doesNotContainKey("sinceTimestamp");
        harness.reply(joiningRestore, "recording", 2, List.of(
                harness.point(0, 1, 2), harness.point(1, 3, 4)), false);

        Map<String, Object> firstEvent = harness.events(first).get(0);
        Map<String, Object> secondEvent = harness.events(second).get(0);
        assertThat(firstEvent.get("action")).isEqualTo("APPEND");
        assertThat((List<?>) firstEvent.get("points")).hasSize(1);
        assertThat(secondEvent.get("action")).isEqualTo("RESET");
        assertThat((List<?>) secondEvent.get("points")).hasSize(2);
        assertThat(harness.actions(third)).containsExactly("RESET");
    }

    @Test
    void repeatedSyncForSameSessionDoesNotRestartRestore() throws Exception {
        Harness harness = new Harness();
        WebSocketSession session = harness.session("ws-1");
        harness.watch(session);
        harness.coordinator.observeTaskInstance("robot-1", 42);
        Map<String, Object> probe = harness.nextCommand();
        harness.reply(probe, "recording", 1, List.of(), false);
        Map<String, Object> restore = harness.nextCommand();
        harness.reply(restore, "recording", 1, List.of(harness.point(0, 1, 2)), false);

        int scheduledBefore = harness.tasks.size();
        harness.watch(session);

        assertThat(harness.tasks).hasSize(scheduledBefore);
    }

    private static final class Harness {
        private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private final Deque<Map<String, Object>> commands = new ArrayDeque<>();
        private final Map<String, List<TextMessage>> messages = new HashMap<>();
        private final TrajectoryCoordinator coordinator;

        @SuppressWarnings("unchecked")
        private Harness() throws Exception {
            EquipmentControlCommandPublisher commandPublisher = mock(EquipmentControlCommandPublisher.class);
            MediaWebSocketPublisher webSocketPublisher = mock(MediaWebSocketPublisher.class);
            TaskScheduler scheduler = mock(TaskScheduler.class);
            when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                return mock(ScheduledFuture.class);
            });
            doAnswer(invocation -> {
                commands.add(new HashMap<>((Map<String, Object>) invocation.getArgument(1)));
                return null;
            }).when(commandPublisher).publishTrajectoryQuery(any(), any());
            doAnswer(invocation -> {
                WebSocketSession session = invocation.getArgument(0);
                messages.computeIfAbsent(session.getId(), ignored -> new ArrayList<>())
                        .add((TextMessage) invocation.getArgument(1));
                return null;
            }).when(webSocketPublisher).send(any(), any());
            coordinator = new TrajectoryCoordinator(objectMapper, commandPublisher, webSocketPublisher, scheduler);
        }

        private WebSocketSession session(String id) {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn(id);
            when(session.isOpen()).thenReturn(true);
            return session;
        }

        private void watch(WebSocketSession session) {
            coordinator.sync(session, Map.of("targets", List.of(Map.of(
                    "robotId", "robot-1", "workflowInstanceId", 9001))));
        }

        private Map<String, Object> nextCommand() {
            for (int i = 0; commands.isEmpty() && i < 20 && !tasks.isEmpty(); i++) tasks.remove().run();
            assertThat(commands).as("应产生下一条轨迹查询命令").isNotEmpty();
            return commands.remove();
        }

        private void reply(Map<String, Object> command, String status, long totalPoints,
                List<Map<String, Object>> points, boolean hasMore) throws Exception {
            Map<String, Object> summary = Map.of(
                    "totalPoints", totalPoints,
                    "startTime", 1000,
                    "lastUpdateTime", 1000 + Math.max(0, totalPoints - 1),
                    "startPose", Map.of("x", 1, "y", 2, "yaw", 0),
                    "currentPose", Map.of("x", totalPoints * 2 - 1, "y", totalPoints * 2, "yaw", 0));
            Map<String, Object> report = new HashMap<>();
            report.put("commandId", command.get("commandId"));
            report.put("taskInstanceId", command.get("taskInstanceId"));
            report.put("status", status);
            report.put("format", command.get("format"));
            report.put("summary", summary);
            report.put("points", points);
            report.put("hasMore", hasMore);
            coordinator.handleSnapshot("eiop/v1/edge/robot-1/trajectory/snapshot",
                    objectMapper.writeValueAsString(report));
        }

        private Map<String, Object> point(double t, double x, double y) {
            return Map.of("t", t, "x", x, "y", y, "yaw", 0);
        }

        private List<String> actions(WebSocketSession session) throws Exception {
            return events(session).stream().map(event -> String.valueOf(event.get("action"))).toList();
        }

        @SuppressWarnings("unchecked")
        private List<Map<String, Object>> events(WebSocketSession session) throws Exception {
            List<Map<String, Object>> result = new ArrayList<>();
            for (TextMessage message : messages.getOrDefault(session.getId(), List.of())) {
                Map<String, Object> event = objectMapper.readValue(message.getPayload(), MAP_TYPE);
                result.add((Map<String, Object>) event.get("data"));
            }
            return result;
        }

        private void clearEvents() {
            messages.clear();
        }
    }
}
