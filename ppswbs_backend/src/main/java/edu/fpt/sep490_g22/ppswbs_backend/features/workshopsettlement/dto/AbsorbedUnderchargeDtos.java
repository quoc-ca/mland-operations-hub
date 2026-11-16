package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Absorbed-undercharge records of a FINALIZED ticket folio (003.3 FR-057, T135). Money is the contract's whole-VND string. */
public final class AbsorbedUnderchargeDtos {

    private AbsorbedUnderchargeDtos() {
    }

    /** {@code amountVnd} is a whole VND string above 0 (the contract's {@code Vnd} without the value 0). */
    public record RecordRequest(
            @NotNull @Pattern(regexp = "^[1-9][0-9]{0,14}$") String amountVnd,
            @NotBlank @Size(max = 1000) String reason) {

        public long amount() {
            return Long.parseLong(amountVnd);
        }
    }

    public record AbsorbedUnderchargeDto(
            String underchargeReference,
            String ticketCode,
            String groupCode,
            Long locationId,
            String amountVnd,
            String reason,
            String status,
            String recordedBy,
            Instant recordedAt,
            String voidReason,
            String voidedBy,
            Instant voidedAt) {
    }

    /** The branch/period list; {@code totalVnd} sums only ACTIVE records ("0" when none). */
    public record AbsorbedUnderchargeListDto(
            Long locationId,
            LocalDate from,
            LocalDate to,
            String totalVnd,
            List<AbsorbedUnderchargeDto> records) {
    }
}
