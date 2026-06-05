package com.example.demo.overtime;

import com.example.demo.overtime.dto.OvertimeSummaryResponse;
import com.example.demo.overtime.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimeService;

    /**
     * Monthly overtime summary: total hours, daily breakdown, total payout, settlement status.
     * The external government API call happens OUTSIDE the DB transaction (LF-205 fix).
     */
    @GetMapping("/summary/{workerId}")
    public ResponseEntity<OvertimeSummaryResponse> getSummary(
            @PathVariable Long workerId,
            @RequestParam String month) {
        return ResponseEntity.ok(overtimeService.getSummary(workerId, month));
    }

    /**
     * Settle all PENDING overtime entries for a worker+month atomically (LF-204 fix).
     * Only past months can be settled. Returns total settled amount.
     */
    @PostMapping("/settle/{workerId}")
    public ResponseEntity<SettlementResponse> settle(
            @PathVariable Long workerId,
            @RequestParam String month) {
        return ResponseEntity.ok(overtimeService.settle(workerId, month));
    }
}
