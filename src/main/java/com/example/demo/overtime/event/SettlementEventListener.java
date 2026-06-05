package com.example.demo.overtime.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * LF-204: The SMS fires AFTER the DB transaction commits — never during it.
 *
 * If the transaction rolls back (e.g., entry #15 fails), this listener never
 * runs, so no premature SMS is sent. If the SMS itself fails after a successful
 * commit, we log and move on — the settlement data is still correct.
 *
 * This pattern prevents the "worker got paid SMS but DB has partial data" bug.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementEventListener {

    private final SmsService smsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettlementCommitted(SettlementEvent event) {
        try {
            smsService.sendSettlementNotification(
                    event.getWorkerPhone(),
                    event.getWorkerName(),
                    event.getTotalAmount(),
                    event.getMonth()
            );
        } catch (Exception e) {
            // SMS failure must NOT affect settlement correctness. Log and retry-queue in prod.
            log.error("SMS notification failed for worker {} ({}): {} — settlement data is correct",
                    event.getWorkerName(), event.getWorkerId(), e.getMessage());
        }
    }
}
