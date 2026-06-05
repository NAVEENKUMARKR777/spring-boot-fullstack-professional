package com.example.demo.attendance;

import com.example.demo.site.Site;
import com.example.demo.worker.Worker;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "attendance_logs",
    indexes = {
        @Index(name = "idx_attendance_worker_id",   columnList = "worker_id"),
        @Index(name = "idx_attendance_site_id",     columnList = "site_id"),
        @Index(name = "idx_attendance_clock_in",    columnList = "clock_in"),
        @Index(name = "idx_attendance_worker_open", columnList = "worker_id, clock_out")
    },
    uniqueConstraints = {
        // A worker can have at most one open (clock_out IS NULL) record at a time.
        // Enforced at DB level via partial unique index — see migration note.
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AttendanceLog {

    @Id
    @SequenceGenerator(name = "attendance_seq", sequenceName = "attendance_log_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "attendance_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "clock_in", nullable = false)
    private LocalDateTime clockIn;

    @Column(name = "clock_out")
    private LocalDateTime clockOut;

    @Column(name = "total_hours", precision = 6, scale = 2)
    private BigDecimal totalHours;

    @Column(name = "overtime_hours", precision = 6, scale = 2)
    private BigDecimal overtimeHours;

    // Set true when total shift > 16 hours (missed clock-out or data issue)
    @Column(nullable = false)
    @Builder.Default
    private boolean flagged = false;
}
