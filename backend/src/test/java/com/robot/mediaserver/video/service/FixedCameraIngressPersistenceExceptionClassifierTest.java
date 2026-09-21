package com.robot.mediaserver.video.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class FixedCameraIngressPersistenceExceptionClassifierTest {

    private final FixedCameraIngressPersistenceExceptionClassifier classifier =
            new FixedCameraIngressPersistenceExceptionClassifier();

    @Test
    void connectionFailureHasPriorityOverNestedLockTimeout() {
        SQLException connection = new SQLException("connection lost", "08006", 0);
        connection.setNextException(new SQLException("lock timeout", "HY000", 1205));

        assertThat(classifier.classify(new RuntimeException(connection)))
                .isEqualTo(FixedCameraIngressPersistenceExceptionClassifier.Classification.UNAVAILABLE);
    }

    @Test
    void recognizesLockTimeoutFromNextException() {
        SQLException root = new SQLException("wrapper", "HY000", 0);
        root.setNextException(new SQLException("lock timeout", "HY000", 1205));

        assertThat(classifier.classify(root))
                .isEqualTo(FixedCameraIngressPersistenceExceptionClassifier.Classification.BUSY);
    }

    @Test
    void doesNotGuessIntegrityFailureAsBusy() {
        assertThat(classifier.classify(new SQLException("duplicate", "23000", 1062)))
                .isEqualTo(FixedCameraIngressPersistenceExceptionClassifier.Classification.INTERNAL);
    }
}
