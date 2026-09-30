package com.robot.mediaserver.video.service;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 按完整 SQLException 图识别 Ingress 专用数据库竞争与连接故障。 */
@Component
public class FixedCameraIngressPersistenceExceptionClassifier {

    private static final int MAX_NODES = 64;

    /**
     * 沿异常原因链识别锁竞争、依赖不可用和内部失败，避免把不可重试错误误报为繁忙。
     *
     * @param failure 本次操作的失败原因
     * @return 持久化异常所属处理类别
     */
    public Classification classify(Throwable failure) {
        boolean connectionFailure = false;
        boolean integrityOrSyntaxFailure = false;
        boolean lockBusy = false;
        ArrayDeque<Throwable> queue = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(failure);
        int nodes = 0;
        while (!queue.isEmpty() && nodes++ < MAX_NODES) {
            Throwable current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (current.getCause() != null) {
                queue.addLast(current.getCause());
            }
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                connectionFailure |= state != null && state.startsWith("08");
                integrityOrSyntaxFailure |= state != null && (state.startsWith("23") || state.startsWith("42"));
                lockBusy |= sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213 || "40001".equals(state);
                if (sql.getNextException() != null) {
                    queue.addLast(sql.getNextException());
                }
            }
        }
        if (connectionFailure) {
            return Classification.UNAVAILABLE;
        }
        if (integrityOrSyntaxFailure) {
            return Classification.INTERNAL;
        }
        if (lockBusy) {
            return Classification.BUSY;
        }
        return Classification.INTERNAL;
    }

    /** 将持久化异常区分为锁竞争、存储不可用和内部错误。 */
    public enum Classification {
        /**
         * 锁竞争或并发占用造成的可重试冲突。
         */
        BUSY,
        /**
         * 数据库连接或暂时性基础设施故障。
         */
        UNAVAILABLE,
        /**
         * 无法归为可重试占用或依赖不可用的内部错误。
         */
        INTERNAL
    }
}
