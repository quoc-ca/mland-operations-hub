package edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.PreviousLocationAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.PreviousLocationAddOnService.PreviousLocationAddOnDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** T187 (FR-060): Manager-only read of the add-ons sold at the ticket's former location. */
@RestController
@RequestMapping("/api/v1/workshop-tickets/{ticketCode}")
@RequiredArgsConstructor
public class TicketPreviousLocationAddOnController {

    private final PreviousLocationAddOnService service;

    @GetMapping("/folio/previous-location-add-ons")
    public ResponseEntity<List<PreviousLocationAddOnDto>> list(@PathVariable String ticketCode,
                                                               Authentication authentication) {
        CurrentAccountActor actor = authentication != null
                && authentication.getPrincipal() instanceof CurrentAccountActor resolved ? resolved : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(ticketCode, actor));
    }
}
