package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.FolioStatusRequest;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dto.TicketFolioDtos.FolioStatusResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.TicketFolioApiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Folio lifecycle of many tickets in one call, for the session attendance roster badge. */
@RestController
@RequiredArgsConstructor
public class TicketFolioStatusController {

    private final TicketFolioApiService folioApi;

    @PostMapping("/api/v1/workshop-ticket-folio-statuses")
    public ResponseEntity<FolioStatusResponse> read(@Valid @RequestBody FolioStatusRequest request,
                                                    Authentication authentication) {
        CurrentAccountActor actor = authentication != null && authentication.getPrincipal() instanceof CurrentAccountActor resolved
                ? resolved : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(folioApi.readFolioStatuses(request.ticketCodes(), actor));
    }
}
