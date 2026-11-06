package edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.fulfilment.service.PickupOperationsService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PickupOperationsControllerTest {
    @Test void passesAuthenticatedActorToServiceAndReturnsEvidence() throws Exception {
        var service = mock(PickupOperationsService.class);
        var actor = new CurrentAccountActor(7L, ActorRole.STAFF, ActorStatus.ACTIVE, true, List.of(1L));
        when(service.prepare(actor, "order")).thenReturn(new PickupOperationsService.Result("order", "READY_FOR_PICKUP", "PREPARED", Instant.parse("2026-10-30T00:00:00Z")));
        var mvc = MockMvcBuilders.standaloneSetup(new PickupOperationsController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())).build();
        mvc.perform(post("/api/v1/ready-ring-orders/order/preparation").requestAttr("currentAccountActor", actor))
            .andExpect(status().isOk()).andExpect(jsonPath("$.milestone").value("PREPARED"));
        verify(service).prepare(actor, "order");
    }

    @Test void missingIdentityIsDenied() throws Exception {
        var service = mock(PickupOperationsService.class);
        when(service.handOver(null, "order")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var mvc = MockMvcBuilders.standaloneSetup(new PickupOperationsController(service)).build();
        mvc.perform(post("/api/v1/ready-ring-orders/order/pickup-confirmations")).andExpect(status().isUnauthorized());
    }
}
