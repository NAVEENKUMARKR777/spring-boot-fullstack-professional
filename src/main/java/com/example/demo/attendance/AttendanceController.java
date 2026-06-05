package com.example.demo.attendance;

import com.example.demo.attendance.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/clock-in")
    public ResponseEntity<AttendanceLogResponse> clockIn(@Valid @RequestBody ClockInRequest request) {
        return ResponseEntity.ok(attendanceService.clockIn(request));
    }

    @PostMapping("/clock-out")
    public ResponseEntity<AttendanceLogResponse> clockOut(@Valid @RequestBody ClockOutRequest request) {
        return ResponseEntity.ok(attendanceService.clockOut(request.getWorkerId()));
    }

    /**
     * Served exclusively from Redis — LF-203 requirement.
     * Returns empty list when Redis is unavailable (not an error — LF-202).
     */
    @GetMapping("/active")
    public List<ActiveWorkerInfo> getActiveWorkers() {
        return attendanceService.getActiveWorkers();
    }

    /**
     * Paginated attendance history — N+1-free via @EntityGraph (LF-203).
     * Old unpaginated calls (no page/size params) still work: defaults to page=0, size=20.
     */
    @GetMapping("/log")
    public PageResponse<AttendanceLogResponse> getAttendanceLog(
            @RequestParam Long workerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return attendanceService.getAttendanceHistory(workerId, from, to, page, size);
    }
}
