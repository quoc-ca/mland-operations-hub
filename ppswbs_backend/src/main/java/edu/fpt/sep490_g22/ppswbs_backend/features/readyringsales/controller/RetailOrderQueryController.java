package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dto.RetailOrderHistoryView;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailActorResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service.RetailOrderQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/members/me/ready-ring-orders", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RetailOrderQueryController {
    private final RetailActorResolver actors;
    private final RetailOrderQueryService orders;

    @GetMapping
    public List<RetailOrderHistoryView> list(HttpServletRequest request) {
        return orders.list(actors.requireActiveMember(request).accountId());
    }

    @GetMapping("/{orderId}")
    public RetailOrderHistoryView detail(HttpServletRequest request, @PathVariable String orderId) {
        return orders.find(actors.requireActiveMember(request).accountId(), orderId);
    }
}
