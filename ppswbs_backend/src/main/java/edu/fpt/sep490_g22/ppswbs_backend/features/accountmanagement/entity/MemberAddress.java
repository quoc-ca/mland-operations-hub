package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AddressType;
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

    @Column(name = "province_id", length = 64)
    private String provinceId;

    @Column(name = "province_name", nullable = false, length = 128)
    private String provinceName;

    @Column(name = "district_id", length = 64)
    private String districtId;

    @Column(name = "district_name", nullable = false, length = 128)
    private String districtName;

    @Column(name = "ward_id", length = 64)
    private String wardId;

    @Column(name = "ward_name", nullable = false, length = 128)
    private String wardName;

    @Column(name = "hamlet_id", length = 64)
    private String hamletId;

    @Column(name = "hamlet_name", length = 128)
    private String hamletName;

    @Column(name = "detail_address", nullable = false)
    private String detailAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 16)
    @Builder.Default
    private AddressType addressType = AddressType.HOME;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

