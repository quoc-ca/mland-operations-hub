package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ring_difficulty_catalogue_revision")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RingDifficultyCatalogueRevision {

    public static final String DEFAULT_SCOPE = "WORKSHOP_RING_CATALOGUE";

    @Id
    @Column(name = "revision_scope", nullable = false, length = 64)
    private String revisionScope;

    @Column(name = "revision_number", nullable = false)
    private Long revisionNumber;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
