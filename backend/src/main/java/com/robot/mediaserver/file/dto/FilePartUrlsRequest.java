package com.robot.mediaserver.file.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class FilePartUrlsRequest {
    @NotEmpty
    private List<Integer> partNumbers;

    public List<Integer> getPartNumbers() { return partNumbers; }
    public void setPartNumbers(List<Integer> partNumbers) { this.partNumbers = partNumbers; }
}
