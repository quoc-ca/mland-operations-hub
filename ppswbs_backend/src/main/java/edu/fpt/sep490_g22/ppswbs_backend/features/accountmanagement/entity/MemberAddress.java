package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CommuneType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "member_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "phone_number", nullable = false, length = 15)
    private String phoneNumber;

    @Column(name = "province_code", nullable = false, length = 64)
    private String provinceCode;

    @Column(name = "province_name", nullable = false, length = 128)
    private String provinceName;

    @Column(name = "commune_code", nullable = false, length = 64)
    private String communeCode;

    @Column(name = "commune_name", nullable = false, length = 128)
    private String communeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "commune_type", nullable = false, length = 32)
    private CommuneType communeType;

    @Column(name = "detail_address", nullable = false)
    private String detailAddress;

    @Column(name = "directory_version", nullable = false, length = 64)
    private String directoryVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 16)
    @Builder.Default
    private AddressType addressType = AddressType.HOME;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
