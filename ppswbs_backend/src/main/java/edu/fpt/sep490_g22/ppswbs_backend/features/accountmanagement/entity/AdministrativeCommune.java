package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CommuneType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "administrative_communes", uniqueConstraints = {
    @UniqueConstraint(name = "uq_ac_version_code", columnNames = {"directory_version", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdministrativeCommune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "directory_version", nullable = false, length = 64)
    private String directoryVersion;

    @Column(name = "province_code", nullable = false, length = 64)
    private String provinceCode;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private CommuneType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
