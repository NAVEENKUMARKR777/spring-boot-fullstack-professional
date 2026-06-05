package com.example.demo.overtime;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface OvertimeRepository extends JpaRepository<OvertimeEntry, Long> {

    // Used by AttendanceService to check monthly cap before creating a new entry
    @Query("SELECT COALESCE(SUM(o.overtimeHours), 0) FROM OvertimeEntry o " +
           "WHERE o.worker.id = :workerId AND o.date BETWEEN :startDate AND :endDate")
    BigDecimal sumOvertimeHoursByWorkerAndDateRange(
            @Param("workerId")  Long workerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    // Fetch all entries for a worker within a month.
    // @EntityGraph loads the attendance association in the same JOIN query, avoiding N+1
    // when the summary endpoint accesses e.getAttendance().getId().
    @EntityGraph(attributePaths = "attendance")
    @Query("SELECT o FROM OvertimeEntry o WHERE o.worker.id = :workerId " +
           "AND o.date BETWEEN :startDate AND :endDate ORDER BY o.date ASC")
    List<OvertimeEntry> findByWorkerIdAndDateRange(
            @Param("workerId")  Long workerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    // Only PENDING entries — used by settlement
    @Query("SELECT o FROM OvertimeEntry o WHERE o.worker.id = :workerId " +
           "AND o.date BETWEEN :startDate AND :endDate AND o.settlementStatus = 'PENDING' ORDER BY o.date ASC")
    List<OvertimeEntry> findPendingByWorkerIdAndDateRange(
            @Param("workerId")  Long workerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );
}
