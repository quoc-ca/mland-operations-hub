package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.common.CorrelationIdFilter;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaReconciliationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaSyncDeliveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.integration.OtaAuditCorrelationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOtaResyncFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, authorization and application service; persistence is mocked. */
class OtaReconciliationApiTest {
    private static final String PATH = "/api/v1/ota-reconciliation-work-items";
    private final AccountFacade accounts = mock(AccountFacade.class);
    private final OtaReconciliationWorkItemRepository items = mock(OtaReconciliationWorkItemRepository.class);
    private final OtaReconciliationDecisionRepository decisions = mock(OtaReconciliationDecisionRepository.class);
    private MockMvc mvc;
    private OtaReconciliationWorkItem item;

    @BeforeEach
    void setup() {
        var authorization = new OtaAuthorizationService(accounts);
        ReflectionTestUtils.setField(authorization, "allowTestTokens", true);
        var service = new OtaReconciliationService(mock(OtaChannelMappingRepository.class), items, decisions,
                mock(OtaOutboundChangeRepository.class), mock(OtaSyncDeliveryService.class),
                mock(WorkshopOtaResyncFacade.class), new OtaAuditCorrelationService());
        mvc = MockMvcBuilders.standaloneSetup(new OtaReconciliationController(authorization, service))
                .setControllerAdvice(new OtaExceptionHandler()).addFilters(new CorrelationIdFilter()).build();
        var mapping = OtaChannelMapping.builder().id(21L).publicReference(UUID.randomUUID().toString())
                .connection(OtaChannelConnection.builder().providerCode(OtaProvider.KLOOK).build())
                .locationReference("location-outside-actor-scope").status(OtaMappingStatus.SALES_CLOSED).build();
        item = OtaReconciliationWorkItem.builder().id(42L).publicReference(UUID.randomUUID().toString())
                .mapping(mapping).status("OPEN").category("SOURCE_VERSION_GAP")
                .auditReference(UUID.randomUUID().toString()).build();
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MANAGER", "ADMIN"})
    void authorizedGlobalListReturnsPersistedCorrelationAndOnlyPublicIds(ActorRole role) throws Exception {
        authenticate(role, ActorStatus.ACTIVE);
        when(items.findMappingLinkedByStatusOrderByCreatedAtAsc("OPEN")).thenReturn(List.of(item));
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(get(PATH).param("status", "OPEN").header("Authorization", "Bearer test-uid:actor"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].workItemId").value(item.getPublicReference()))
                    .andExpect(jsonPath("$.items[0].mappingId").value(item.getMapping().getPublicReference()))
                    .andExpect(jsonPath("$.items[0].correlationId").value(item.getAuditReference()))
                    .andExpect(jsonPath("$.items[0].id").doesNotExist())
                    .andExpect(jsonPath("$.items[0].mapping").doesNotExist());
        }
        verify(items, never()).findAll();
        verify(items, never()).save(any());
        verify(accounts, never()).getLocationScope(any());
    }

    @Test
    void emptyListIsSuccessful() throws Exception {
        authenticate(ActorRole.MANAGER, ActorStatus.ACTIVE);
        when(items.findMappingLinkedOrderByCreatedAtAsc()).thenReturn(List.of());
        mvc.perform(get(PATH).header("Authorization", "Bearer test-uid:actor"))
                .andExpect(status().isOk()).andExpect(content().json("{\"items\":[]}"));
    }

    @Test
    void invalidStatusReturnsSafeValidationErrorBeforeQueryingItems() throws Exception {
        authenticate(ActorRole.MANAGER, ActorStatus.ACTIVE);
        mvc.perform(get(PATH).param("status", "unknown").header("Authorization", "Bearer test-uid:actor"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").isString());
        verifyNoInteractions(items);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MEMBER", "STAFF"})
    void unauthorizedRolesCannotListItems(ActorRole role) throws Exception {
        authenticate(role, ActorStatus.ACTIVE);
        mvc.perform(get(PATH).header("Authorization", "Bearer test-uid:actor"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(items);
    }

    @Test
    void missingIdentityIsRejectedBeforeReadingItems() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(items);
    }

    @Test
    void resolveReturnsAndReplaysItsRecordedCorrelation() throws Exception {
        authenticate(ActorRole.MANAGER, ActorStatus.ACTIVE);
        when(items.findMappingLinkedByPublicReferenceForUpdate(item.getPublicReference())).thenReturn(Optional.of(item));
        when(items.findByIdForUpdate(item.getId())).thenReturn(Optional.of(item));
        AtomicReference<OtaReconciliationDecision> saved = new AtomicReference<>();
        when(decisions.findByActorAccountReferenceAndIdempotencyKeyHash(any(), any()))
                .thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(decisions.saveAndFlush(any())).thenAnswer(call -> {
            OtaReconciliationDecision decision = call.getArgument(0);
            saved.set(decision);
            return decision;
        });
        String before = item.getAuditReference();
        var request = post(PATH + "/" + item.getPublicReference() + "/resolve")
                .header("Authorization", "Bearer test-uid:actor").header("Idempotency-Key", "test-resolution-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"ACCEPTED\",\"reason\":\"Reviewed test item\"}");
        String first = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.mappingId").value(item.getMapping().getPublicReference()))
                .andExpect(jsonPath("$.status").value("RESOLVED")).andReturn().getResponse().getContentAsString();
        assertThat(item.getAuditReference()).isNotEqualTo(before).isEqualTo(saved.get().getCorrelationReference());
        mvc.perform(request).andExpect(status().isOk()).andExpect(content().json(first))
                .andExpect(jsonPath("$.correlationId").value(saved.get().getCorrelationReference()));
        verify(decisions, times(1)).saveAndFlush(any());
    }

    @Test
    void resolveCannotAddressAnItemOutsideTheMappingLinkedQuery() throws Exception {
        authenticate(ActorRole.MANAGER, ActorStatus.ACTIVE);
        when(items.findMappingLinkedByPublicReferenceForUpdate(item.getPublicReference())).thenReturn(Optional.empty());
        mvc.perform(post(PATH + "/" + item.getPublicReference() + "/resolve")
                        .header("Authorization", "Bearer test-uid:actor").header("Idempotency-Key", "test-resolution-2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"ACCEPTED\",\"reason\":\"Review\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(decisions);
    }

    private void authenticate(ActorRole role, ActorStatus status) {
        when(accounts.resolveAccount("actor")).thenReturn(Optional.of(new AccountSummary(7L, role, status, null, null, null)));
    }
}
