package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.AddOnResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopAddOnService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkshopAddOnControllerTest {
    private final WorkshopAddOnService service = mock(WorkshopAddOnService.class);
    private final WorkshopPackageAuthorizationService authorization = mock(WorkshopPackageAuthorizationService.class);
    private final OperationsRestIdempotencyService idempotency = mock(OperationsRestIdempotencyService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopAddOnController(
                service, authorization)).build();
    }

    @Test
    void publicDiscoveryReturnsOnlyOpaqueAddOnReferenceAndSafePrice() throws Exception {
        when(service.listCustomerSelectable(10L, 20L)).thenReturn(List.of(
                new AddOnResponse("abc00000-0000-4000-8000-000000000001", 10L, 20L,
                        "Clay kit", 15_000L, "VND", WorkshopAddOnState.ACTIVE)));

        mvc.perform(get("/api/v1/workshop-packages/10/locations/20/add-ons")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].addOnReference").value("abc00000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$[0].priceVnd").value(15_000))
                .andExpect(jsonPath("$[0].id").doesNotExist());
        verify(service).listCustomerSelectable(10L, 20L);
        verifyNoInteractions(authorization);
    }

    @Test
    void managerAdministrationUsesGlobalManagerAuthorizationForAnyLocation() throws Exception {
        when(authorization.requireActiveManager("Bearer manager-token")).thenReturn(7L);
        when(service.createDraft(eq(10L), eq(999L), any(), eq(7L), eq("CORR-1")))
                .thenReturn(new AddOnResponse("abc00000-0000-4000-8000-000000000001", 10L, 999L,
                        "Clay kit", 15_000L, "VND", WorkshopAddOnState.DRAFT));

        mvc.perform(post("/api/v1/workshop-packages/10/locations/999/add-ons")
                        .header("Authorization", "Bearer manager-token")
                        .header("X-Correlation-Id", "CORR-1")
                        .header("Idempotency-Key", "ops-add-on-legacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Clay kit\",\"priceVnd\":15000}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("DRAFT"));

        verify(authorization).requireActiveManager("Bearer manager-token");
        verify(service).createDraft(eq(10L), eq(999L), any(), eq(7L), eq("CORR-1"));
    }

    @Test
    void managementInventoryRequiresManagerAndCanReturnDraftAndRetiredEntries() throws Exception {
        MockMvc managementMvc = standaloneSetup(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopAddOnManagementController(
                        service, authorization)).build();
        when(authorization.requireActiveManager("Bearer manager-token")).thenReturn(7L);
        when(service.listForManagement(10L, 20L)).thenReturn(List.of(
                new AddOnResponse("abc00000-0000-4000-8000-000000000001", 10L, 20L,
                        "Draft kit", 15_000L, "VND", WorkshopAddOnState.DRAFT),
                new AddOnResponse("abc00000-0000-4000-8000-000000000002", 10L, 20L,
                        "Retired kit", 20_000L, "VND", WorkshopAddOnState.RETIRED)));

        managementMvc.perform(get("/api/v1/workshop-package-management/packages/10/locations/20/add-ons")
                        .header("Authorization", "Bearer manager-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("DRAFT"))
                .andExpect(jsonPath("$[1].state").value("RETIRED"));

        verify(authorization).requireActiveManager("Bearer manager-token");
        verify(service).listForManagement(10L, 20L);
    }

    @Test
    void mutatingHttpRequestPassesIdempotencyKeyToDurableLedgerService() throws Exception {
        when(authorization.requireActiveManager("Bearer manager-token")).thenReturn(7L);
        when(service.createDraft(eq(10L), eq(999L), any(), eq(7L), eq("CORR-2")))
                .thenReturn(new AddOnResponse("abc00000-0000-4000-8000-000000000003", 10L, 999L,
                        "Clay kit", 15_000L, "VND", WorkshopAddOnState.DRAFT));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<AddOnResponse> command = invocation.getArgument(5);
            return command.get();
        }).when(idempotency).execute(eq("ops-add-on-001"), anyString(), anyString(), any(),
                eq(AddOnResponse.class), any());

        MockMvc idempotentMvc = standaloneSetup(
                new edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopAddOnController(
                        service, authorization, idempotency)).build();

        idempotentMvc.perform(post("/api/v1/workshop-packages/10/locations/999/add-ons")
                        .header("Authorization", "Bearer manager-token")
                        .header("X-Correlation-Id", "CORR-2")
                        .header("Idempotency-Key", "ops-add-on-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Clay kit\",\"priceVnd\":15000}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("abc00000-0000-4000-8000-000000000003")));

        verify(idempotency).execute(eq("ops-add-on-001"),
                eq("REST:POST:/api/v1/workshop-packages/{packageId}/locations/{locationId}/add-ons"),
                eq("addon:create:10:999"), any(), eq(AddOnResponse.class), any());
    }
}
