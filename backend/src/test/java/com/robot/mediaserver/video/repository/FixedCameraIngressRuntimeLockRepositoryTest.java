package com.robot.mediaserver.video.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.jdbc.Work;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FixedCameraIngressRuntimeLockRepositoryTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final HikariDataSource hikari = mock(HikariDataSource.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Session session = mock(Session.class);
    private final Connection connection = mock(Connection.class);
    private final Statement statement = mock(Statement.class);
    private final ResultSet resultSet = mock(ResultSet.class);
    private final VideoSourceRuntime runtime = new VideoSourceRuntime();
    private FixedCameraIngressRuntimeLockRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FixedCameraIngressRuntimeLockRepository(dataSource);
        ReflectionTestUtils.setField(repository, "entityManager", entityManager);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(50);
        when(entityManager.find(any(), any(), any(), any())).thenReturn(runtime);
        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikari);
        doAnswer(invocation -> invocation.getArgument(0, ReturningWork.class).execute(connection))
                .when(session).doReturningWork(any());
        doAnswer(invocation -> {
            invocation.getArgument(0, Work.class).execute(connection);
            return null;
        }).when(session).doWork(any());
    }

    @Test
    void restoresSessionLockWaitTimeoutAfterLock() throws Exception {
        assertThat(repository.lock("runtime-1", Duration.ofMillis(1200))).contains(runtime);

        verify(statement).execute("SET SESSION innodb_lock_wait_timeout = 1");
        verify(statement).execute("SET SESSION innodb_lock_wait_timeout = 50");
    }

    @Test
    void evictsConnectionWhenSessionVariableCannotBeRestored() throws Exception {
        when(statement.execute("SET SESSION innodb_lock_wait_timeout = 50"))
                .thenThrow(new SQLException("connection lost", "08006"));

        assertThatThrownBy(() -> repository.lock("runtime-1", Duration.ofSeconds(1)))
                .hasMessageContaining("connection lost");

        verify(hikari).evictConnection(connection);
    }
}
