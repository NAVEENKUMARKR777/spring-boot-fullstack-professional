package com.example.demo.attendance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceLog, Long> {

    // Active (open) attendance for a specific worker
    Optional<AttendanceLog> findByWorkerIdAndClockOutIsNull(Long workerId);

    // All open attendance records — used by the scheduled flag job
    @Query("SELECT a FROM AttendanceLog a WHERE a.clockOut IS NULL")
    List<AttendanceLog> findAllOpen();

    // Attendance history with JOIN FETCH to avoid N+1 — LF-203
    // Separate countQuery is required because JPQL JOIN FETCH is incompatible with COUNT(*)
    @EntityGraph(attributePaths = {"worker", "site"})
    @Query(
        value      = "SELECT a FROM AttendanceLog a WHERE a.worker.id = :workerId AND a.clockIn BETWEEN :from AND :to",
        countQuery = "SELECT COUNT(a) FROM AttendanceLog a WHERE a.worker.id = :workerId AND a.clockIn BETWEEN :from AND :to"
    )
    Page<AttendanceLog> findHistoryByWorker(
            @Param("workerId") Long workerId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable pageable
    );

    // Open attendance records older than the given threshold — used to flag missed clock-outs
    @Query("SELECT a FROM AttendanceLog a WHERE a.clockOut IS NULL AND a.clockIn < :threshold")
    List<AttendanceLog> findOpenOlderThan(@Param("threshold") LocalDateTime threshold);
}
