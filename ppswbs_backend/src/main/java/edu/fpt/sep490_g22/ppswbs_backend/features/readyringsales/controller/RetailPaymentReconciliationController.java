package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailPaymentReconciliationRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/manager/retail-payment-reconciliations")
public class RetailPaymentReconciliationController {
    private final RetailPaymentReconciliationRepository repository;

    public record Entry(String checkoutId, String merchantReference, String providerTransaction,
                        Long amountVnd, String currency, String reason, Instant createdAt) { }

    @GetMapping @Transactional(readOnly = true)
    public List<Entry> list(HttpServletRequest request, @RequestParam(defaultValue = "0") int page) {
        Object value = request.getAttribute("currentAccountActor");
        if (!(value instanceof CurrentAccountActor actor) || actor.accountId() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        if (actor.role() != ActorRole.MANAGER || actor.status() != ActorStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active Manager access is required");
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        return repository.findAll(PageRequest.of(page, 50, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .stream().map(row -> new Entry(row.getCheckoutId(), row.getMerchantReference(),
                        row.getProviderTransaction(), row.getAmountVnd(), row.getCurrencyCode(),
                        row.getReason(), row.getCreatedAt())).toList();
    }
}
