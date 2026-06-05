package com.example.demo.attendance.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ClockOutRequest {

    @NotNull(message = "workerId is required")
    private Long workerId;
}
