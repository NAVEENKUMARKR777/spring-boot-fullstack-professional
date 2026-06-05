package com.example.demo.overtime.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SettlementResponse {
    private Long workerId;
    private String workerName;
    private String month;
    private int settledEntries;
    private BigDecimal totalAmount;
    private String status;
}
