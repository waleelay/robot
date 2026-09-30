package com.robot.mediaserver.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 申请指定分片的上传地址；服务会校验会话有效性、编号范围并去重。 */
public class FilePartUrlsRequest {
    /**
     * 要签名的分片编号；必须非空，服务校验范围、数量并去重。
     */
    @NotEmpty
    @Schema(description = "要签名的分片编号；必须非空，服务校验范围、数量并去重")
    private List<Integer> partNumbers;

    /**
     * 读取{@link #partNumbers}。
     *
     * @return 当前值，含义与约束见{@link #partNumbers}
     */
    public List<Integer> getPartNumbers() { return partNumbers; }
    /**
     * 更新{@link #partNumbers}。
     *
     * @param partNumbers 新值，含义与约束见{@link #partNumbers}
     */
    public void setPartNumbers(List<Integer> partNumbers) { this.partNumbers = partNumbers; }
}
