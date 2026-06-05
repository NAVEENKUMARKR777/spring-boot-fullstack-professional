package com.example.demo.attendance;

import com.example.demo.attendance.dto.ActiveWorkerInfo;
import com.example.demo.attendance.dto.AttendanceLogResponse;
import com.example.demo.attendance.dto.ClockInRequest;
import com.example.demo.attendance.dto.PageResponse;
import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.overtime.OvertimeEntry;
import com.example.demo.overtime.OvertimeRepository;
import com.example.demo.overtime.SettlementStatus;
import com.example.demo.site.Site;
import com.example.demo.site.SiteRepository;
import com.example.demo.worker.Worker;
import com.example.demo.worker.WorkerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private static final BigDecimal STANDARD_SHIFT_HOURS = BigDecimal.valueOf(8);
    private static final BigDecimal MONTHLY_OVERTIME_CAP  = BigDecimal.valueOf(60);
    private static final BigDecimal FLAG_THRESHOLD_HOURS  = BigDecimal.valueOf(16);
    private static final BigDecimal TIER1_MULTIPLIER       = BigDecimal.valueOf(1.5);
    private static final BigDecimal TIER2_MULTIPLIER       = BigDecimal.valueOf(2.0);
    private static final BigDecimal TIER1_LIMIT            = BigDecimal.valueOf(2);
    private static final int        HOURLY_DIVISOR         = 8;

    private final AttendanceRepository attendanceRepository;
    private final WorkerRepository     workerRepository;
    private final SiteRepository       siteRepository;
    private final OvertimeRepository   overtimeRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // ── Clock-in ──────────────────────────────────────────────────────────────

    @Transactional
    public AttendanceLogResponse clockIn(ClockInRequest request) {
        Worker worker = workerRepository.findByIdAndActiveTrue(request.getWorkerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Worker not found or inactive: " + request.getWorkerId()));

        Site site = siteRepository.findByIdAndActiveTrue(request.getSiteId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Site not found or inactive: " + request.getSiteId()));

        // Clock-in time is always server-side — cannot be in the future by definition.
        // The business rule "clock-in cannot be in the future" is enforced structurally.
        LocalDateTime clockIn = LocalDateTime.now();

        // Prevent double clock-in — check Redis first, fall back to DB
        boolean alreadyActive = isWorkerActiveInCache(worker.getId());
        if (!alreadyActive) {
            alreadyActive = attendanceRepository
                    .findByWorkerIdAndClockOutIsNull(worker.getId())
                    .isPresent();
        }
        if (alreadyActive) {
            String siteName = getActiveWorkerSiteName(worker.getId());
            throw new ConflictException("DUPLICATE_CLOCK_IN",
                    "Worker is already clocked in" + (siteName != null ? " at Site: " + siteName : ""));
        }

        AttendanceLog record = AttendanceLog.builder()
                .worker(worker)
                .site(site)
                .clockIn(clockIn)
                .build();

        AttendanceLog saved = attendanceRepository.save(record);
        addToActiveCache(worker, site, clockIn);
        return AttendanceLogResponse.from(saved);
    }

    // ── Clock-out ─────────────────────────────────────────────────────────────

    @Transactional
    public AttendanceLogResponse clockOut(Long workerId) {
        AttendanceLog record = attendanceRepository
                .findByWorkerIdAndClockOutIsNull(workerId)
                .orElseThrow(() -> new BusinessRuleException("NOT_CLOCKED_IN",
                        "Worker " + workerId + " is not currently clocked in"));

        LocalDateTime clockOut = LocalDateTime.now();
        record.setClockOut(clockOut);

        long minutes = Duration.between(record.getClockIn(), clockOut).toMinutes();
        BigDecimal totalHours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        record.setTotalHours(totalHours);

        // Flag if shift exceeds 16 hours
        if (totalHours.compareTo(FLAG_THRESHOLD_HOURS) > 0) {
            record.setFlagged(true);
            log.warn("Attendance {} flagged: shift of {} hours exceeds 16-hour limit", record.getId(), totalHours);
        }

        // Overtime calculation
        BigDecimal rawOvertime = totalHours.subtract(STANDARD_SHIFT_HOURS).max(BigDecimal.ZERO);
        record.setOvertimeHours(rawOvertime);

        AttendanceLog saved = attendanceRepository.save(record);

        if (rawOvertime.compareTo(BigDecimal.ZERO) > 0) {
            createOvertimeEntry(saved, rawOvertime, record.getWorker());
        }

        removeFromActiveCache(workerId);
        return AttendanceLogResponse.from(saved);
    }

    // ── Active workers (Redis-only) ───────────────────────────────────────────

    public List<ActiveWorkerInfo> getActiveWorkers() {
        try {
            Set<String> ids = redisTemplate.opsForSet().members("active_workers");
            if (ids == null || ids.isEmpty()) return List.of();

            List<ActiveWorkerInfo> result  = new ArrayList<>();
            List<Object>          toRemove = new ArrayList<>();

            for (String id : ids) {
                String json = redisTemplate.opsForValue().get("active_worker:" + id);
                if (json != null) {
                    result.add(objectMapper.readValue(json, ActiveWorkerInfo.class));
                } else {
                    // TTL expired — clean up stale set membership and flag the DB record
                    toRemove.add(id);
                    flagExpiredAttendanceForWorker(Long.parseLong(id));
                }
            }

            if (!toRemove.isEmpty()) {
                redisTemplate.opsForSet().remove("active_workers", toRemove.toArray());
            }

            return result;
        } catch (Exception e) {
            log.warn("Redis unavailable — returning empty active workers list: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Attendance history (paginated + N+1-free via @EntityGraph) ────────────

    @Transactional(readOnly = true)
    public PageResponse<AttendanceLogResponse> getAttendanceHistory(
            Long workerId, LocalDate from, LocalDate to, int page, int size) {

        if (!workerRepository.existsById(workerId)) {
            throw new ResourceNotFoundException("Worker not found: " + workerId);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("clockIn").descending());
        Page<AttendanceLog> resultPage = attendanceRepository.findHistoryByWorker(
                workerId,
                from.atStartOfDay(),
                to.atTime(23, 59, 59),
                pageable
        );

        return PageResponse.from(resultPage, AttendanceLogResponse::from);
    }

    // ── Scheduled: flag attendance records with expired 16-hour TTL ──────────

    @Scheduled(fixedDelay = 3_600_000) // every hour
    @Transactional
    public void flagExpiredAttendanceRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(16);
        List<AttendanceLog> expired = attendanceRepository.findOpenOlderThan(threshold);
        if (expired.isEmpty()) return;

        for (AttendanceLog record : expired) {
            record.setFlagged(true);
            removeFromActiveCache(record.getWorker().getId());
        }
        attendanceRepository.saveAll(expired);
        log.info("Flagged {} attendance records with expired TTL (>16h open)", expired.size());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void createOvertimeEntry(AttendanceLog attendance, BigDecimal rawOvertime, Worker worker) {
        YearMonth month = YearMonth.from(attendance.getClockOut().toLocalDate());
        LocalDate start = month.atDay(1);
        LocalDate end   = month.atEndOfMonth();

        BigDecimal usedThisMonth = overtimeRepository
                .sumOvertimeHoursByWorkerAndDateRange(worker.getId(), start, end);
        if (usedThisMonth == null) usedThisMonth = BigDecimal.ZERO;

        BigDecimal available     = MONTHLY_OVERTIME_CAP.subtract(usedThisMonth).max(BigDecimal.ZERO);
        BigDecimal cappedOvertime = rawOvertime.min(available);

        if (cappedOvertime.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Worker {} has hit the 60-hour monthly overtime cap — no entry created", worker.getId());
            return;
        }

        BigDecimal hourlyRate = worker.getDailyWageRate()
                .divide(BigDecimal.valueOf(HOURLY_DIVISOR), 4, RoundingMode.HALF_UP);
        BigDecimal amount     = calculateOvertimeAmount(cappedOvertime, hourlyRate);

        // Store the effective primary rate: 1.5x if entirely in tier-1, 2.0x if any tier-2 reached
        BigDecimal rateApplied = cappedOvertime.compareTo(TIER1_LIMIT) <= 0
                ? TIER1_MULTIPLIER : TIER2_MULTIPLIER;

        OvertimeEntry entry = OvertimeEntry.builder()
                .worker(worker)
                .attendance(attendance)
                .date(attendance.getClockOut().toLocalDate())
                .overtimeHours(cappedOvertime)
                .overtimeRateApplied(rateApplied)
                .amount(amount)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        overtimeRepository.save(entry);
    }

    private BigDecimal calculateOvertimeAmount(BigDecimal overtimeHours, BigDecimal hourlyRate) {
        BigDecimal first2    = overtimeHours.min(TIER1_LIMIT);
        BigDecimal beyond2   = overtimeHours.subtract(TIER1_LIMIT).max(BigDecimal.ZERO);
        BigDecimal tier1Pay  = first2.multiply(hourlyRate).multiply(TIER1_MULTIPLIER);
        BigDecimal tier2Pay  = beyond2.multiply(hourlyRate).multiply(TIER2_MULTIPLIER);
        return tier1Pay.add(tier2Pay).setScale(2, RoundingMode.HALF_UP);
    }

    private void addToActiveCache(Worker worker, Site site, LocalDateTime clockIn) {
        try {
            ActiveWorkerInfo info = new ActiveWorkerInfo(
                    worker.getId(), worker.getName(), worker.getDesignation(),
                    site.getId(), site.getName(), clockIn);
            String key  = "active_worker:" + worker.getId();
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(key, json, java.time.Duration.ofHours(16));
            redisTemplate.opsForSet().add("active_workers", String.valueOf(worker.getId()));
        } catch (Exception e) {
            log.warn("Redis unavailable — active worker not cached: {}", e.getMessage());
        }
    }

    private void removeFromActiveCache(Long workerId) {
        try {
            redisTemplate.delete("active_worker:" + workerId);
            redisTemplate.opsForSet().remove("active_workers", String.valueOf(workerId));
        } catch (Exception e) {
            log.warn("Redis unavailable — could not remove worker {} from active cache: {}", workerId, e.getMessage());
        }
    }

    private boolean isWorkerActiveInCache(Long workerId) {
        try {
            Boolean member = redisTemplate.opsForSet().isMember("active_workers", String.valueOf(workerId));
            return Boolean.TRUE.equals(member);
        } catch (Exception e) {
            log.warn("Redis unavailable for active-check — falling back to DB: {}", e.getMessage());
            return false;
        }
    }

    private String getActiveWorkerSiteName(Long workerId) {
        try {
            String json = redisTemplate.opsForValue().get("active_worker:" + workerId);
            if (json == null) return null;
            return objectMapper.readTree(json).get("siteName").asText();
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    void flagExpiredAttendanceForWorker(Long workerId) {
        attendanceRepository.findByWorkerIdAndClockOutIsNull(workerId).ifPresent(record -> {
            record.setFlagged(true);
            attendanceRepository.save(record);
            log.warn("Flagged open attendance {} for worker {} — Redis TTL expired (missed clock-out)",
                    record.getId(), workerId);
        });
    }
}
