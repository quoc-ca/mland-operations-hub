package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaCurrencyMoney;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaFinancialEvidenceService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaPayoutEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.OtaPayoutEvidenceType;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api.OtaFinancialEvidenceDtos.*;

@RestController
@RequestMapping(value = "/api/v1/ota-channel-bookings", produces = MediaType.APPLICATION_JSON_VALUE)
public class OtaFinancialEvidenceController {
    private final OtaAuthorizationService authorizationService;
    private final OtaFinancialEvidenceService financialService;

    public OtaFinancialEvidenceController(OtaAuthorizationService authorizationService,
                                          OtaFinancialEvidenceService financialService) {
        this.authorizationService = authorizationService;
        this.financialService = financialService;
    }

    @GetMapping("/{channelBookingId}/financial-evidence")
    public FinancialEvidenceResponse read(@PathVariable String channelBookingId,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        return financialService.read(channelBookingId, authorizationService.requireManager(authorization));
    }

    @PostMapping(value = "/{channelBookingId}/payout-reconciliation", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FinancialEvidenceResponse> reconcile(@PathVariable String channelBookingId,
                                                                @RequestHeader(value = "Authorization", required = false) String authorization,
                                                                @Valid @RequestBody PayoutReconciliationRequest request) {
        var actor = authorizationService.requireManager(authorization);
        validateKind(request.kind(), request.evidenceType());
        String related = request.relatedEntryId();
        if (related != null) UUID.fromString(related);
        var command = new OtaPayoutEvidenceCommand(
                OtaPayoutEvidenceType.valueOf(request.evidenceType()),
                OtaCurrencyMoney.toMinor(request.amount().amount(), request.amount().currency()),
                request.amount().currency(), request.reference(), request.reason(), related,
                request.allocationReference(), request.payoutReference(), request.occurredAt());
        return ResponseEntity.status(201).body(financialService.record(channelBookingId, command, actor));
    }

    private static void validateKind(String kind, String evidenceType) {
        boolean valid = switch (evidenceType) {
            case "ELIGIBILITY", "INVOICE", "REMITTANCE", "BANK_RECEIPT" -> "PAYOUT".equals(kind);
            case "ADJUSTMENT" -> "ADJUSTMENT".equals(kind);
            case "REVERSAL" -> "REVERSAL".equals(kind);
            case "VARIANCE" -> "VARIANCE".equals(kind);
            case "DISPUTE" -> "DISPUTE".equals(kind);
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("Evidence kind does not match evidence type");
        }
    }
}
