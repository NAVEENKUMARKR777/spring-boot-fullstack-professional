package com.example.demo.worker;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

@Entity
@Table(
    name = "workers",
    indexes = {
        @Index(name = "idx_worker_phone", columnList = "phone"),
        @Index(name = "idx_worker_active", columnList = "active")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Worker {

    @Id
    @SequenceGenerator(name = "worker_sequence", sequenceName = "worker_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "worker_sequence")
    private Long id;

    @NotBlank(message = "Worker name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Phone number is required")
    @Column(nullable = false, length = 20)
    private String phone;

    @NotNull(message = "Designation is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Designation designation;

    @NotNull(message = "Daily wage rate is required")
    @Positive(message = "Daily wage rate must be positive")
    @Column(name = "daily_wage_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyWageRate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
