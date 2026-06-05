package com.example.demo.overtime.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class SettlementEvent extends ApplicationEvent {

    private final Long workerId;
    private final String workerName;
    private final String workerPhone;
    private final BigDecimal totalAmount;
    private final String month;

    public SettlementEvent(Object source, Long workerId, String workerName,
                           String workerPhone, BigDecimal totalAmount, String month) {
        super(source);
        this.workerId    = workerId;
        this.workerName  = workerName;
        this.workerPhone = workerPhone;
        this.totalAmount = totalAmount;
        this.month       = month;
    }
}
