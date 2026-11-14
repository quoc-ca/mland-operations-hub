package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.SessionState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * T020: Request DTO for applying an authorized lifecycle transition to a workshop session.
 * <p>
 * OpenAPI Contract:
 * - Path: /workshop-sessions/{sessionId}/state-transitions (POST)
 * - Required: targetState
 * - Permitted values: PUBLISHED, SALES_PAUSED, IN_PROGRESS, ATTENDANCE_FINALIZED
 * (Cancellation transitions use reasoned cancellation endpoints).
 * <p>
 * Does not leak internal entities, Firebase tokens, PII, or audit metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateTransitionRequest {

    private static final Set<SessionState> ALLOWED_TARGET_STATES = Set.of(
            SessionState.PUBLISHED,
            SessionState.SALES_PAUSED,
            SessionState.IN_PROGRESS,
            SessionState.ATTENDANCE_FINALIZED
    );

    @NotNull(message = "targetState is required")
    private SessionState targetState;

    @JsonIgnore
    @AssertTrue(message = "targetState must be one of: PUBLISHED, SALES_PAUSED, IN_PROGRESS, ATTENDANCE_FINALIZED")
    public boolean isTargetStateAllowed() {
        if (targetState == null) {
            return true;
        }
        return ALLOWED_TARGET_STATES.contains(targetState);
    }
}
