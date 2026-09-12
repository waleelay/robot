package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.mediaserver.config.MediaProperties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VideoSchedulerLeaseServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private VideoSchedulerLeaseService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new VideoSchedulerLeaseService(jdbcTemplate, new MediaProperties(), transactionManager);
    }

    @Test
    void executesAndReleasesClaimedTask() {
        AtomicBoolean executed = new AtomicBoolean();
        when(jdbcTemplate.update(contains("set lease_owner = ?"), any(Object[].class)))
                .thenReturn(1);

        assertThat(service.execute("video-maintenance", () -> executed.set(true))).isTrue();

        assertThat(executed).isTrue();
        verify(jdbcTemplate).update(
                contains("locked_until = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6))"),
                any(Object[].class));
        verify(jdbcTemplate).update(contains("set lease_owner = null"), any(Object[].class));
    }

    @Test
    void insertsLeaseWhenTaskHasNeverRun() {
        AtomicBoolean executed = new AtomicBoolean();
        when(jdbcTemplate.update(contains("set lease_owner = ?"), any(Object[].class)))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("insert ignore"), any(Object[].class)))
                .thenReturn(1);

        assertThat(service.execute("video-maintenance", () -> executed.set(true))).isTrue();

        assertThat(executed).isTrue();
        verify(jdbcTemplate).update(contains("set lease_owner = null"), any(Object[].class));
    }

    @Test
    void skipsTaskOwnedByAnotherInstance() {
        AtomicBoolean executed = new AtomicBoolean();
        when(jdbcTemplate.update(contains("set lease_owner = ?"), any(Object[].class)))
                .thenReturn(0);
        when(jdbcTemplate.update(contains("insert ignore"), any(Object[].class)))
                .thenReturn(0);

        assertThat(service.execute("video-maintenance", () -> executed.set(true))).isFalse();

        assertThat(executed).isFalse();
        verify(jdbcTemplate, never()).update(
                contains("set lease_owner = null"), any(Object[].class));
    }

    @Test
    void releasesLeaseWhenTaskFails() {
        when(jdbcTemplate.update(contains("set lease_owner = ?"), any(Object[].class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.execute("video-maintenance", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(jdbcTemplate).update(contains("set lease_owner = null"), any(Object[].class));
    }
}
