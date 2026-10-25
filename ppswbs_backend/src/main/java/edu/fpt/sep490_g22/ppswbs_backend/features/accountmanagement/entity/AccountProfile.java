package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "account_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountProfile {

    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "full_name", nullable = false, length = 400)
    private String fullName;

    @Column(name = "phone", length = 30)
    private String phone;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
