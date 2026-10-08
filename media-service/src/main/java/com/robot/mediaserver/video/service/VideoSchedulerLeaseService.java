package com.robot.mediaserver.video.service;

import com.robot.mediaserver.config.MediaProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通过数据库短租约协调多实例视频周期任务；当前不续租，任务超过租期时仍可能被其他实例接管。
 */
@Service
public class VideoSchedulerLeaseService {

    private static final Logger log = LoggerFactory.getLogger(VideoSchedulerLeaseService.class);

    private static final String CLAIM_EXPIRED_SQL = """
            update media_scheduler_lease
               set lease_owner = ?, locked_until = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                   updated_at = UTC_TIMESTAMP(6)
             where lease_name = ? and (lease_owner is null or locked_until <= UTC_TIMESTAMP(6))
            """;

    private static final String INSERT_CLAIM_SQL = """
            insert ignore into media_scheduler_lease (
                lease_name, lease_owner, locked_until, created_at, updated_at
            ) values (?, ?, TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                      UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """;

    private static final String RELEASE_SQL = """
            update media_scheduler_lease
               set lease_owner = null, locked_until = UTC_TIMESTAMP(6),
                   updated_at = UTC_TIMESTAMP(6)
             where lease_name = ? and lease_owner = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MediaProperties properties;
    private final TransactionTemplate transactionTemplate;

    /**
     * 初始化 VideoSchedulerLeaseService，保存所需依赖及初始运行状态。
     *
     * @param jdbcTemplate 执行参数化 SQL 的 JDBC 访问器
     * @param properties 服务配置
     * @param transactionManager 数据库事务管理器
     */
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
     * @return 当前实例是否领取并执行了任务
     * @param leaseName 跨实例调度任务的租约名称
     * @param task 取得租约后在当前线程执行的任务，应在配置租期内完成
     */
    public boolean execute(String leaseName, Runnable task) {
        String owner = UUID.randomUUID().toString();
        long leaseSeconds = Math.max(10, properties.getSession().getSchedulerLeaseSeconds());
        if (!tryAcquire(leaseName, owner, leaseSeconds)) {
            log.debug("视频周期任务租约已被其他实例持有 租约名称={}", leaseName);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(leaseName, owner);
        }
    }

    private boolean tryAcquire(String leaseName, String owner, long leaseSeconds) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int updated = jdbcTemplate.update(
                    CLAIM_EXPIRED_SQL,
                    new Object[] {owner, leaseSeconds, leaseName});
            if (updated == 1) {
                return true;
            }
            return jdbcTemplate.update(
                    INSERT_CLAIM_SQL,
                    new Object[] {leaseName, owner, leaseSeconds}) == 1;
        }));
    }

    private void release(String leaseName, String owner) {
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    RELEASE_SQL,
                    new Object[] {leaseName, owner}));
        } catch (RuntimeException exception) {
            // 释放失败时不覆盖业务结果；租约到期后其他实例仍可自动接管。
            log.warn("释放视频周期任务租约失败 租约名称={}", leaseName, exception);
        }
    }

}
