package com.example.demo.overtime.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Mock SMS service. In production this would call Twilio / MSG91 / AWS SNS.
 * The architectural contract is what matters: called only after DB commit via
 * @TransactionalEventListener(AFTER_COMMIT) — never inside the transaction.
 */
@Service
@Slf4j
public class SmsService {

    public void sendSettlementNotification(String phone, String workerName,
                                           BigDecimal amount, String month) {
        log.info("[SMS] To: {} | Worker: {} | Message: Your {} overtime of ₹{} has been settled.",
                phone, workerName, month, amount);
    }
}
