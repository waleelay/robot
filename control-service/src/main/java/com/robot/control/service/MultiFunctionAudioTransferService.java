package com.robot.control.service;

import com.robot.control.auth.CurrentUser;
import com.robot.control.client.ControlMediaServiceClient;
import com.robot.media.common.file.FileListItemResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 多合一设备音频文件下发服务。
 */
@Service
public class MultiFunctionAudioTransferService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "wav");

    private final ControlMediaServiceClient mediaServiceClient;
    private final EquipmentControlService equipmentControlService;

    public MultiFunctionAudioTransferService(
            ControlMediaServiceClient mediaServiceClient,
            EquipmentControlService equipmentControlService) {
        this.mediaServiceClient = mediaServiceClient;
        this.equipmentControlService = equipmentControlService;
    }

    /**
     * 校验通用文件并通知机器人客户端从 Media Service 下载。
     *
     * @param robotId 机器人 ID
     * @param deviceId 多合一设备 ID
     * @param request 包含 fileId 的请求
     * @param user 当前用户
     * @return MQTT 发布结果
     */
    public Map<String, Object> transfer(
            String robotId,
            String deviceId,
            Map<String, Object> request,
            CurrentUser user) {
        if (user == null || !user.hasRole("EQUIPMENT_OPERATOR")) {
            throw new IllegalArgumentException("当前用户无装备控制权限");
        }
        String fileId = requiredString(request, "fileId");
        FileListItemResponse file = mediaServiceClient.file(fileId, user);
        validateFile(robotId, deviceId, file);

        String transferId = "mat_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> params = object(
                "transferId", transferId,
                "fileId", file.fileId(),
                "fileName", file.fileName(),
                "fileSize", file.fileSize(),
                "orgId", user.orgId());
        Map<String, Object> published =
                equipmentControlService.publishMultiFunctionAudioTransfer(robotId, deviceId, params);
        Map<String, Object> response = new LinkedHashMap<>(published);
        response.put("transferId", transferId);
        response.put("fileId", file.fileId());
        response.put("fileName", file.fileName());
        response.put("fileSize", file.fileSize());
        return response;
    }

    private void validateFile(String robotId, String deviceId, FileListItemResponse file) {
        if (file == null || !"AUDIO".equals(file.fileType()) || !"READY".equals(file.status())) {
            throw new IllegalArgumentException("音频文件不存在或尚未就绪");
        }
        if (!Objects.equals(robotId, file.robotId()) || !Objects.equals(deviceId, file.deviceId())) {
            throw new IllegalArgumentException("音频文件不属于目标机器人或设备");
        }
        if (file.fileSize() <= 0 || file.fileSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("音频文件大小必须大于 0 且不超过 20MB");
        }
        String fileName = file.fileName() == null ? "" : file.fileName().trim();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")
                || !SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("只支持合法的 mp3、wav 音频文件");
        }
    }

    private static String requiredString(Map<String, Object> values, String key) {
        String value = values == null || values.get(key) == null ? "" : String.valueOf(values.get(key)).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return value;
    }

    private static Map<String, Object> object(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length - 1; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
