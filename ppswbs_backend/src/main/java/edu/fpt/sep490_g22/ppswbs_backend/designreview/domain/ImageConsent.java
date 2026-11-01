package edu.fpt.sep490_g22.ppswbs_backend.designreview.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "image_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_account_id", nullable = false)
    private Long actorAccountId;

    @Column(name = "design_request_id", length = 36)
    private String designRequestId;

    @Column(name = "purpose", nullable = false, length = 64)
    private String purpose;

    @Column(name = "consent_version", nullable = false, length = 64)
    private String consentVersion;

    @Column(name = "affirmative_action", nullable = false, length = 64)
    private String affirmativeAction;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @PrePersist
    protected void onCreate() {
        if (this.acceptedAt == null) {
            this.acceptedAt = Instant.now();
        }
        if (this.affirmativeAction == null) {
            this.affirmativeAction = "CHECKBOX_CONFIRMED";
        }
        if (this.purpose == null) {
            this.purpose = "INDEPENDENT_DESIGN_IMAGE_ANALYSIS";
        }
    }
}
