package com.example.demo.overtime;

import com.example.demo.exception.BusinessRuleException;
import com.example.demo.exception.ConflictException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.overtime.dto.OvertimeDailyBreakdown;
import com.example.demo.overtime.dto.OvertimeSummaryResponse;
import com.example.demo.overtime.dto.SettlementResponse;
import com.example.demo.overtime.event.SettlementEvent;
import com.example.demo.overtime.external.MinimumWageApiClient;
import com.example.demo.worker.Worker;
import com.example.demo.worker.WorkerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OvertimeService {

    private final OvertimeRepository      overtimeRepository;
    private final WorkerRepository        workerRepository;
    private final MinimumWageApiClient    minimumWageApiClient;
    private final ApplicationEventPublisher eventPublisher;

    // ── Summary ───────────────────────────────────────────────────────────────
    //
    // LF-205: This method is intentionally NOT @Transactional.
    //
    // The original bug: getOvertimeSummary was @Transactional, and inside it
    // made a synchronous call to minimumWageApiClient (3-5 seconds). That call
    // held an open DB connection for the entire duration. With pool-size=10 and
    // 20 concurrent users, the pool was exhausted in seconds.
    //
    // Fix: fetch external data FIRST (no DB connection held), then do all DB
    // reads (short-lived auto-commit per repository call). The Spring proxy trap
    // is avoided because there's no self-call to a @Transactional method here.

    public OvertimeSummaryResponse getSummary(Long workerId, String month) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        YearMonth yearMonth = parseYearMonth(month);

        // ── External API call OUTSIDE any transaction (LF-205) ────────────────
        BigDecimal stateMinWage = minimumWageApiClient.fetchMinimumWage(worker.getDesignation());
        log.debug("Fetched min wage ₹{} for designation {} from government API",
                stateMinWage, worker.getDesignation());

        // ── DB reads (no transaction needed for read-only queries) ────────────
        LocalDate start   = yearMonth.atDay(1);
        LocalDate end     = yearMonth.atEndOfMonth();
        List<OvertimeEntry> entries = overtimeRepository.findByWorkerIdAndDateRange(workerId, start, end);

        BigDecimal totalHours  = entries.stream()
                .map(OvertimeEntry::getOvertimeHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = entries.stream()
                .map(OvertimeEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OvertimeDailyBreakdown> breakdown = entries.stream()
                .map(e -> new OvertimeDailyBreakdown(
                        e.getAttendance().getId(),
                        e.getDate(),
                        e.getOvertimeHours(),
                        e.getAmount(),
                        e.getSettlementStatus()))
                .collect(Collectors.toList());

        String overallStatus = deriveOverallStatus(entries);

        return OvertimeSummaryResponse.builder()
                .workerId(workerId)
                .workerName(worker.getName())
                .month(month)
                .totalOvertimeHours(totalHours)
                .dailyBreakdown(breakdown)
                .totalPayoutAmount(totalAmount)
                .settlementStatus(overallStatus)
                .build();
    }

    // ── Settlement ────────────────────────────────────────────────────────────
    //
    // LF-204: The entire settlement for a worker+month is ONE atomic transaction.
    // If any entry fails (bad data, constraint violation, etc.), the whole batch
    // rolls back — no partial state. The SMS fires AFTER commit via
    // @TransactionalEventListener(AFTER_COMMIT), not during.
    //
    // LF-204 Spring proxy trap avoided: this is a PUBLIC method called from
    // OvertimeController (a different Spring bean), so the @Transactional proxy
    // intercepts correctly. If it were called from another method on THIS bean,
    // the annotation would be silently ignored.

    @Transactional
    public SettlementResponse settle(Long workerId, String month) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found: " + workerId));

        YearMonth yearMonth   = parseYearMonth(month);
        YearMonth currentMonth = YearMonth.now();

        // Cannot settle current or future months
        if (!yearMonth.isBefore(currentMonth)) {
            throw new BusinessRuleException("INVALID_SETTLEMENT_MONTH",
                    "Cannot settle the current or a future month. Only past months can be settled.");
        }

        LocalDate start = yearMonth.atDay(1);
        LocalDate end   = yearMonth.atEndOfMonth();

        List<OvertimeEntry> allEntries = overtimeRepository
                .findByWorkerIdAndDateRange(workerId, start, end);

        if (allEntries.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No overtime entries found for worker " + workerId + " in " + month);
        }

        List<OvertimeEntry> pending = allEntries.stream()
                .filter(e -> e.getSettlementStatus() == SettlementStatus.PENDING)
                .collect(Collectors.toList());

        if (pending.isEmpty()) {
            throw new ConflictException("ALREADY_SETTLED",
                    "Overtime for " + month + " is already fully settled");
        }

        // Mark all pending entries SETTLED — atomic within this transaction
        pending.forEach(e -> e.setSettlementStatus(SettlementStatus.SETTLED));
        overtimeRepository.saveAll(pending);

        BigDecimal totalAmount = pending.stream()
                .map(OvertimeEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Publish event — SMS fires AFTER this transaction commits (LF-204)
        eventPublisher.publishEvent(new SettlementEvent(
                this, workerId, worker.getName(), worker.getPhone(), totalAmount, month));

        return SettlementResponse.builder()
                .workerId(workerId)
                .workerName(worker.getName())
                .month(month)
                .settledEntries(pending.size())
                .totalAmount(totalAmount)
                .status("SETTLED")
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private YearMonth parseYearMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessRuleException("INVALID_MONTH_FORMAT",
                    "Month must be in YYYY-MM format, got: " + month);
        }
    }

    private String deriveOverallStatus(List<OvertimeEntry> entries) {
        if (entries.isEmpty()) return "NONE";
        long pending  = entries.stream().filter(e -> e.getSettlementStatus() == SettlementStatus.PENDING).count();
        long settled  = entries.stream().filter(e -> e.getSettlementStatus() == SettlementStatus.SETTLED).count();
        if (pending == 0)              return "SETTLED";
        if (settled == 0)              return "PENDING";
        return "PARTIAL";
    }
}
