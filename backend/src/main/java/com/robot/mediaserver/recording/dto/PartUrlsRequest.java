package com.robot.mediaserver.recording.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class PartUrlsRequest {

    @NotEmpty
    private List<Integer> partNumbers;

    public List<Integer> getPartNumbers() { return partNumbers; }
    public void setPartNumbers(List<Integer> partNumbers) { this.partNumbers = partNumbers; }
}
