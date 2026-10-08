package com.robot.mediaserver.video.repository;

import com.robot.mediaserver.video.model.VideoSourceRuntime;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/** 仅供 Ingress 管理使用的限时 Runtime 行锁。 */
@Repository
public class FixedCameraIngressRuntimeLockRepository {

    private static final Logger log = LoggerFactory.getLogger(FixedCameraIngressRuntimeLockRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final DataSource dataSource;

    /**
     * 初始化 FixedCameraIngressRuntimeLockRepository，保存所需依赖及初始运行状态。
     *
     * @param dataSource 数据库连接池
     */
    public FixedCameraIngressRuntimeLockRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 在剩余操作预算内锁定 Ingress 运行实例，并在结束后恢复 JDBC 锁等待配置。
     *
     * @param runtimeId 视频源运行实例 ID，供多个观看会话共享发布状态
     * @param timeout 当前操作允许的最长等待时间
     * @return 已锁定运行实例；不存在时为空
     */
    public Optional<VideoSourceRuntime> lock(String runtimeId, Duration timeout) {
        int timeoutMs = Math.max(1, Math.toIntExact(timeout.toMillis()));
        int requestedLockWaitSeconds = Math.max(1, timeoutMs / 1000);
        Session session = entityManager.unwrap(Session.class);
        SessionLockWait lockWait = session.doReturningWork(
                connection -> applyLockWaitTimeout(connection, requestedLockWaitSeconds));
        RuntimeException operationFailure = null;
        try {
            return Optional.ofNullable(entityManager.find(
                    VideoSourceRuntime.class,
                    runtimeId,
                    LockModeType.PESSIMISTIC_WRITE,
                    Map.of(
                            "jakarta.persistence.lock.timeout", timeoutMs,
                            "jakarta.persistence.query.timeout", timeoutMs)));
        } catch (RuntimeException exception) {
            operationFailure = exception;
            throw exception;
        } finally {
            try {
                session.doWork(connection -> restoreLockWaitTimeout(connection, lockWait));
            } catch (RuntimeException restoreFailure) {
                if (operationFailure != null) {
                    operationFailure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }

    private SessionLockWait applyLockWaitTimeout(Connection connection, int requestedSeconds) throws SQLException {
        int originalSeconds;
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT @@SESSION.innodb_lock_wait_timeout")) {
            if (!resultSet.next()) {
                throw new SQLException("读取 innodb_lock_wait_timeout 未返回结果");
            }
            originalSeconds = resultSet.getInt(1);
        }
        int effectiveSeconds = Math.min(originalSeconds, requestedSeconds);
        if (effectiveSeconds != originalSeconds) {
            setLockWaitTimeout(connection, effectiveSeconds);
        }
        return new SessionLockWait(connection, originalSeconds, effectiveSeconds != originalSeconds);
    }

    /**
     * 恢复本次借用连接原有的锁等待设置；连接变化或恢复失败时淘汰连接，避免污染连接池。
     */
    private void restoreLockWaitTimeout(Connection connection, SessionLockWait lockWait) throws SQLException {
        if (!lockWait.changed()) {
            return;
        }
        if (connection != lockWait.connection()) {
            evict(connection);
            evict(lockWait.connection());
            throw new SQLException("Ingress 事务恢复锁等待时间时数据库连接发生变化");
        }
        try {
            setLockWaitTimeout(connection, lockWait.originalSeconds());
        } catch (SQLException exception) {
            evict(connection);
            throw exception;
        }
    }

    private void setLockWaitTimeout(Connection connection, int seconds) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION innodb_lock_wait_timeout = " + seconds);
        }
    }

    private void evict(Connection connection) {
        try {
            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            hikari.evictConnection(connection);
        } catch (SQLException | RuntimeException evictionFailure) {
            log.warn("Ingress 锁等待会话变量恢复失败后淘汰数据库连接失败", evictionFailure);
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                evictionFailure.addSuppressed(closeFailure);
                log.warn("关闭受污染的 Ingress 数据库连接失败", closeFailure);
            }
        }
    }

    /**
     * 暂存连接原有锁等待时长，供 Ingress 临界区结束后恢复连接设置。
     *
     * @param connection 当前事务使用的 JDBC 连接
     * @param originalSeconds 当前 JDBC 会话原有的锁等待秒数，操作完成后恢复
     * @param changed 是否修改了本次操作所维护的原有状态
     */
    private record SessionLockWait(Connection connection, int originalSeconds, boolean changed) {}
}
