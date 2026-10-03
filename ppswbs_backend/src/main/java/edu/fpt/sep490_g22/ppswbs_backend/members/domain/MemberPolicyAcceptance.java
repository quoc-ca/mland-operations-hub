package edu.fpt.sep490_g22.ppswbs_backend.members.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "member_policy_acceptances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberPolicyAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "policy_document_id", nullable = false)
    private Long policyDocumentId;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @PrePersist
    protected void onCreate() {
        if (this.acceptedAt == null) {
            this.acceptedAt = Instant.now();
        }
    }
}
