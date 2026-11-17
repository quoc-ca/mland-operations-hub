package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.service.PickupOperationsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/ready-ring-orders/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PickupOperationsController {
    private final PickupOperationsService pickup;
    @PostMapping("/preparation")
    public PickupOperationsService.Result prepare(HttpServletRequest request, @PathVariable String orderId) {
        return pickup.prepare(actor(request), orderId);
    }
    @PostMapping("/pickup-confirmations")
    public PickupOperationsService.Result handOver(HttpServletRequest request, @PathVariable String orderId) {
        return pickup.handOver(actor(request), orderId);
    }
    private CurrentAccountActor actor(HttpServletRequest request) {
        Object actor = request.getAttribute("currentAccountActor");
        return actor instanceof CurrentAccountActor value ? value : null;
    }
}
