package com.example.demo.overtime;

import com.example.demo.attendance.AttendanceLog;
import com.example.demo.worker.Worker;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "overtime_entries",
    indexes = {
        @Index(name = "idx_overtime_worker_id", columnList = "worker_id"),
        @Index(name = "idx_overtime_date",      columnList = "date"),
        @Index(name = "idx_overtime_status",    columnList = "settlement_status"),
        @Index(name = "idx_overtime_worker_date", columnList = "worker_id, date")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OvertimeEntry {

    @Id
    @SequenceGenerator(name = "overtime_seq", sequenceName = "overtime_entry_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "overtime_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false, unique = true)
    private AttendanceLog attendance;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "overtime_hours", nullable = false, precision = 6, scale = 2)
    private BigDecimal overtimeHours;

    // 1.5 = first-tier rate reached; 2.0 = second-tier rate reached
    @Column(name = "overtime_rate_applied", nullable = false, precision = 4, scale = 2)
    private BigDecimal overtimeRateApplied;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 10)
    @Builder.Default
    private SettlementStatus settlementStatus = SettlementStatus.PENDING;
}
