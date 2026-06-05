package com.example.demo.overtime.dto;

import com.example.demo.overtime.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class OvertimeDailyBreakdown {
    private Long attendanceId;
    private LocalDate date;
    private BigDecimal overtimeHours;
    private BigDecimal amount;
    private SettlementStatus status;
}
