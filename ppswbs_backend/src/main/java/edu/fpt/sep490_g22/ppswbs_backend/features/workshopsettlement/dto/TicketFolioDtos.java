package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TicketFolioDtos {

    private TicketFolioDtos() {}

    public record TicketFolioDto(
            String ticketCode,
            String status,
            String totalAmount,
            String paidAmount,
            String pendingAmount,
            String balanceDue,
            List<CollectionDto> collections,
            List<ChargeDto> charges
    ) {}

    /**
     * One collection of a ticket folio. {@code paymentUrl} and {@code paymentExpiresAt} are set only for a VNPAY
     * part that is still pending and whose checkout has not expired (T108); the merchant reference behind the
     * URL is created by the server.
     */
    public record CollectionDto(
            String collectionId,
            String method,
            String amount,
            String status,
            String changeReturned,
            String paymentUrl,
            String paymentExpiresAt
    ) {
        public CollectionDto(String collectionId, String method, String amount, String status, String changeReturned) {
            this(collectionId, method, amount, status, changeReturned, null, null);
        }
    }

    public record ChargeDto(
            String chargeReference,
            String description,
            String amount,
            String status
    ) {}

    public record GuestFolioLookupRequest(
            @Email @Size(max = 254) String contactEmail,
            @Size(max = 32) String contactPhone
    ) {
        public boolean hasOneProof() {
            boolean hasEmail = contactEmail != null && !contactEmail.isBlank();
            boolean hasPhone = contactPhone != null && !contactPhone.isBlank();
            return (hasEmail && !hasPhone) || (!hasEmail && hasPhone);
        }
    }

    /**
     * {@code customerTransactionReference} is the customer's bank reference of a transfer or the cash receipt;
     * it is ignored for VNPAY, whose merchant reference only the server creates (T108).
     */
    public record TicketCollectionRequest(
            @NotBlank String method,
            String amount,
            Long amountVnd,
            String cashReceived,
            Long cashReceivedVnd,
            String customerTransactionReference,
            String payerLabel,
            Long branchId
    ) {}

    public record ConfirmBankTransferRequest(
            @NotBlank @Size(max = 128) String bankTransactionReference
    ) {}

    public record CatalogueAddOnOptionDto(
            String catalogueAddOnReference,
            String name,
            String price
    ) {
        public CatalogueAddOnOptionDto(String ref, String desc, long amount) {
            this(ref, desc, String.valueOf(amount));
        }
    }
}

