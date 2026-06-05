package com.example.demo.attendance.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ClockInRequest {

    @NotNull(message = "workerId is required")
    private Long workerId;

    @NotNull(message = "siteId is required")
    private Long siteId;
}
