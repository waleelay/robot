package com.robot.mediaserver.file.repository;

import com.robot.mediaserver.file.model.MediaVideoFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaVideoFileRepository extends JpaRepository<MediaVideoFile, String> {
}
