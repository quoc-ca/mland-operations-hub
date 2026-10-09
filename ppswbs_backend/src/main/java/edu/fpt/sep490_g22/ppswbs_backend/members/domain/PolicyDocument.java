package edu.fpt.sep490_g22.ppswbs_backend.members.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "policy_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 64)
    private PolicyType policyType;

    @Column(name = "version", nullable = false, length = 64)
    private String version;

    @Column(name = "content_uri", nullable = false, length = 512)
    private String contentUri;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 64)
    private PolicyDocumentState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
