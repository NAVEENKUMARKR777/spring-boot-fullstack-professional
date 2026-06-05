package com.example.demo.site;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;

@Entity
@Table(
    name = "sites",
    indexes = {
        @Index(name = "idx_site_active", columnList = "active")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Site {

    @Id
    @SequenceGenerator(name = "site_sequence", sequenceName = "site_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "site_sequence")
    private Long id;

    @NotBlank(message = "Site name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Site location is required")
    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
