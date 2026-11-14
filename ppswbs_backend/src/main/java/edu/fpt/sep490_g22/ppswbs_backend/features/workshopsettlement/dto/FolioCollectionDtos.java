package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** Requests and responses of the folio collections and the cash day close (003.3 FR-031..FR-036). */
public final class FolioCollectionDtos {

    private FolioCollectionDtos() {
    }

    /** Cash taken at the counter. {@code cashReceivedVnd} is what the customer handed over. */
    public record CashCollectionRequest(
            @NotNull Long branchId,
            @NotNull @Positive Long amountVnd,
            @NotNull @Positive Long cashReceivedVnd,
            @Size(max = 128) String receiptReference,
            @Size(max = 200) String payerLabel) {
    }

    /** A transfer the customer says they made; it is proven later by someone who sees the shop account. */
    public record BankTransferRequest(
            @NotNull Long branchId,
            @NotNull @Positive Long amountVnd,
            @NotBlank @Size(max = 128) String transactionReference,
            @Size(max = 200) String payerLabel) {
    }

    public record CollectionResponse(
            String collectionReference,
            String method,
            long amountVnd,
            long excessOwedVnd,
            String status,
            long balanceDueVnd,
            long pendingVerificationVnd,
            String folioStatus) {
    }

    public record ManagerReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record DebtRequest(
            @NotNull @Positive Long amountVnd,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record CashCloseRequest(
            @NotNull Long branchId,
            LocalDate businessDay,
            @NotNull @PositiveOrZero Long countedCashVnd,
            @Size(max = 1000) String reason) {
    }

    public record CashCloseResponse(
            String closeReference,
            Long branchId,
            LocalDate businessDay,
            long expectedCashVnd,
            long countedCashVnd,
            long differenceVnd,
            String differenceReason,
            String status) {
    }
}
