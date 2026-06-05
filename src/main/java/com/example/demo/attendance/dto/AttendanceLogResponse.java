package com.example.demo.attendance.dto;

import com.example.demo.attendance.AttendanceLog;
import com.example.demo.worker.Designation;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AttendanceLogResponse {

    private Long id;
    private Long workerId;
    private String workerName;
    private Designation designation;
    private Long siteId;
    private String siteName;
    private LocalDateTime clockIn;
    private LocalDateTime clockOut;
    private BigDecimal totalHours;
    private BigDecimal overtimeHours;
    private boolean flagged;

    public static AttendanceLogResponse from(AttendanceLog log) {
        AttendanceLogResponse r = new AttendanceLogResponse();
        r.setId(log.getId());
        r.setWorkerId(log.getWorker().getId());
        r.setWorkerName(log.getWorker().getName());
        r.setDesignation(log.getWorker().getDesignation());
        r.setSiteId(log.getSite().getId());
        r.setSiteName(log.getSite().getName());
        r.setClockIn(log.getClockIn());
        r.setClockOut(log.getClockOut());
        r.setTotalHours(log.getTotalHours());
        r.setOvertimeHours(log.getOvertimeHours());
        r.setFlagged(log.isFlagged());
        return r;
    }
}
