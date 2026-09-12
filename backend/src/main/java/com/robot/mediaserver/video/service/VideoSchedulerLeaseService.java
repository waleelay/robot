package com.robot.mediaserver.video.service;

import com.robot.mediaserver.config.MediaProperties;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于数据库短租约保证视频周期任务在多 Media 实例中只有一个执行者。
 */
@Service
public class VideoSchedulerLeaseService {

    private static final Logger log = LoggerFactory.getLogger(VideoSchedulerLeaseService.class);

    private static final String CLAIM_EXPIRED_SQL = """
            update media_scheduler_lease
               set lease_owner = ?, locked_until = ?, updated_at = ?
             where lease_name = ? and locked_until <= ?
            """;

    private static final String INSERT_CLAIM_SQL = """
            insert ignore into media_scheduler_lease (
                lease_name, lease_owner, locked_until, created_at, updated_at
            ) values (?, ?, ?, ?, ?)
            """;

    private static final String RELEASE_SQL = """
            update media_scheduler_lease
               set lease_owner = null, locked_until = ?, updated_at = ?
             where lease_name = ? and lease_owner = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MediaProperties properties;
    private final TransactionTemplate transactionTemplate;

    public VideoSchedulerLeaseService(
            JdbcTemplate jdbcTemplate,
            MediaProperties properties,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * 领取任务后执行；租约被其他实例持有时直接跳过本轮。
     *
     * @return 当前实例是否领取并执行了任务
     */
    public boolean execute(String leaseName, Runnable task) {
        String owner = UUID.randomUUID().toString();
        OffsetDateTime claimedAt = now();
        OffsetDateTime lockedUntil = claimedAt.plusSeconds(
                Math.max(10, properties.getSession().getSchedulerLeaseSeconds()));
        if (!tryAcquire(leaseName, owner, claimedAt, lockedUntil)) {
            log.debug("视频周期任务租约已被其他实例持有 leaseName={}", leaseName);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(leaseName, owner);
        }
    }

    private boolean tryAcquire(
            String leaseName,
            String owner,
            OffsetDateTime claimedAt,
            OffsetDateTime lockedUntil) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int updated = jdbcTemplate.update(
                    CLAIM_EXPIRED_SQL,
                    owner,
                    lockedUntil,
                    claimedAt,
                    leaseName,
                    claimedAt);
            if (updated == 1) {
                return true;
            }
            return jdbcTemplate.update(
                    INSERT_CLAIM_SQL,
                    leaseName,
                    owner,
                    lockedUntil,
                    claimedAt,
                    claimedAt) == 1;
        }));
    }

    private void release(String leaseName, String owner) {
        try {
            OffsetDateTime releasedAt = now();
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    RELEASE_SQL,
                    releasedAt,
                    releasedAt,
                    leaseName,
                    owner));
        } catch (RuntimeException exception) {
            // 释放失败时不覆盖业务结果；租约到期后其他实例仍可自动接管。
            log.warn("释放视频周期任务租约失败 leaseName={}", leaseName, exception);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
