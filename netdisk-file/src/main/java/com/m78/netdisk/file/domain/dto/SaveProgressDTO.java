package com.m78.netdisk.file.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class SaveProgressDTO {

    @NotNull
    @Min(0)
    private Integer progressSeconds;

    @NotNull
    @Min(0)
    private Integer totalDuration;

    private Boolean finished;
}
