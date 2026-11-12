package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.CheckoutDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.StaffRetailPickupView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCashCollectionService;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailCounterCashQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Counter cash for store-pickup orders: Staff pickup desk, cash confirmation, Manager daily cash (FR-08-045..047). */
@RestController
@RequiredArgsConstructor
public class RetailCounterCashController {
    private final RetailCashCollectionService cash;
    private final RetailCounterCashQueryService queries;

    @PostMapping(value = "/api/v1/ready-ring-orders/{orderId}/cash-collections", produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckoutDtos.CashCollectionView confirm(HttpServletRequest request, @PathVariable String orderId,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @Valid @RequestBody CheckoutDtos.CashCollectionRequest body) {
        return cash.confirm(actor(request), orderId, body.amountReceived(), idempotencyKey);
    }

    @GetMapping(value = "/api/v1/staff/retail-pickups", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StaffRetailPickupView> pickupDesk(HttpServletRequest request, @RequestParam(required = false) Long locationId) {
        return queries.pickupDesk(actor(request), locationId);
    }

    @GetMapping(value = "/api/v1/manager/retail-cash-collections", produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckoutDtos.DailyCashView daily(HttpServletRequest request, @RequestParam Long locationId,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return queries.daily(actor(request), locationId, businessDate);
    }

    private CurrentAccountActor actor(HttpServletRequest request) {
        Object value = request.getAttribute("currentAccountActor");
        return value instanceof CurrentAccountActor actor ? actor : null;
    }
}
