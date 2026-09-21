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

    public enum Classification {
        BUSY,
        UNAVAILABLE,
        INTERNAL
    }
}
