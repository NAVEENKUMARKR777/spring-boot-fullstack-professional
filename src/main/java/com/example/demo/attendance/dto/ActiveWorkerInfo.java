package com.example.demo.attendance.dto;

import com.example.demo.worker.Designation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveWorkerInfo {
    private Long workerId;
    private String workerName;
    private Designation designation;
    private Long siteId;
    private String siteName;
    private LocalDateTime clockInTime;
}
