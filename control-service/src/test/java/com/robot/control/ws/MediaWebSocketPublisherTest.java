package com.robot.control.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

class MediaWebSocketPublisherTest {

    @Test
    void serializesConcurrentWritesForSameSession() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        AtomicInteger activeWrites = new AtomicInteger();
        AtomicBoolean concurrentWrite = new AtomicBoolean();
        doAnswer(invocation -> {
                    if (activeWrites.incrementAndGet() > 1) {
                        concurrentWrite.set(true);
                    }
                    try {
                        Thread.sleep(10);
                    } finally {
                        activeWrites.decrementAndGet();
                    }
                    return null;
                })
                .when(session)
                .sendMessage(any());

        MediaWebSocketPublisher publisher = new MediaWebSocketPublisher(new ObjectMapper());
        publisher.addSession(session);
        int taskCount = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await();
                publisher.publish("control.command.sent", index);
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(concurrentWrite).isFalse();
        verify(session, times(taskCount)).sendMessage(any(WebSocketMessage.class));
    }

    @Test
    void removesInvalidSessionWithoutInterruptingPublish() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException("TEXT_PARTIAL_WRITING"))
                .when(session)
                .sendMessage(any());
        MediaWebSocketPublisher publisher = new MediaWebSocketPublisher(new ObjectMapper());
        publisher.addSession(session);

        assertThatCode(() -> publisher.publish("control.command.sent", "first")).doesNotThrowAnyException();
        assertThatCode(() -> publisher.publish("control.command.sent", "second")).doesNotThrowAnyException();

        verify(session, times(1)).sendMessage(any(WebSocketMessage.class));
    }
}
