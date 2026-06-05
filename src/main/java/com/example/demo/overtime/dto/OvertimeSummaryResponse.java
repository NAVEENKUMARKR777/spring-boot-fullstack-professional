package com.example.demo.overtime.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OvertimeSummaryResponse {
    private Long workerId;
    private String workerName;
    private String month;
    private BigDecimal totalOvertimeHours;
    private List<OvertimeDailyBreakdown> dailyBreakdown;
    private BigDecimal totalPayoutAmount;
    private String settlementStatus;   // PENDING / SETTLED / PARTIAL
}
