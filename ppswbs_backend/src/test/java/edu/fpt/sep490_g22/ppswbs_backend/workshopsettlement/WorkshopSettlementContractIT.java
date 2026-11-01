package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementCancellationCaseRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementHandoffIdempotencyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementEvidenceConclusionRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementFinalBillRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementChargeRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementNotificationHandoffRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.HandoffIdempotencyStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementHandoffIdempotency;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.CancellationHandoffOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.OpenCancellationHandoffCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.WorkshopSettlementFacadeImpl;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.service.FinalPaymentService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller.CustomerBookingCancellationController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller.MlandCancellationRemedyController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller.RequiredSessionChangeController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller.WorkshopFinalBillController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.controller.WorkshopRefundPolicyController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkshopSettlementContractIT {
    @Mock SettlementCancellationCaseRepository caseRepository;
    @Mock SettlementHandoffIdempotencyRepository idempotencyRepository;
    @Mock SettlementEvidenceConclusionRepository evidenceRepository;
    @Mock SettlementFinalBillRepository billRepository;
    @Mock SettlementChargeRepository chargeRepository;

    @Test
    void publicControllerOperationsStayAlignedWithSettlementOpenApiRoutesAndRequireIdempotency() {
        Map<String, Method> actual = controllerOperations();

        assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(Set.of(
                "GET /api/v1/workshop-bookings/{bookingCode}/final-bill",
                "POST /api/v1/workshop-bookings/{bookingCode}/final-bill",
                "POST /api/v1/workshop-bookings/{bookingCode}/final-bill/charges",
                "POST /api/v1/workshop-bookings/{bookingCode}/final-bill/payment-attempts",
                "POST /api/v1/workshop-bookings/{bookingCode}/final-bill/finalizations",
                "POST /api/v1/workshop-bookings/{bookingCode}/cancellation-cases",
                "GET /api/v1/workshop-bookings/{bookingCode}/cancellation-cases/{caseReference}",
                "POST /api/v1/workshop-bookings/{bookingCode}/required-session-change-cases",
                "GET /api/v1/workshop-bookings/{bookingCode}/required-session-change-cases/{caseReference}",
                "POST /api/v1/workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases",
                "GET /api/v1/workshop-bookings/{bookingCode}/mland-cancellation-remedy-cases/{caseReference}",
                "GET /api/v1/workshop-refund-policies",
                "POST /api/v1/workshop-refund-policies"));

        actual.forEach((operation, method) -> {
            if (operation.startsWith("POST ")) {
                assertThat(hasIdempotencyKey(method))
                        .as("OpenAPI Idempotency-Key parameter on %s", operation)
                        .isTrue();
            }
        });
    }

    @Test
    void acceptedHandoffUsesTypedBoundaryAndPersistsNoEntityOutsideModule() {
        when(idempotencyRepository.findByOperationReference("OP-1")).thenReturn(Optional.empty());
        WorkshopSettlementFacadeImpl facade = new WorkshopSettlementFacadeImpl(
                caseRepository, idempotencyRepository, new ObjectMapper(), org.mockito.Mockito.mock(FinalPaymentService.class),
                org.mockito.Mockito.mock(SettlementNotificationHandoffRepository.class));

        CancellationHandoffOutcome outcome = facade.openCancellationHandoff(
                new OpenCancellationHandoffCommand("SES-1", List.of("BK-1"),
                        "FACILITY_ISSUE", "OP-1", "CORR-1"));

        assertThat(outcome.status()).isEqualTo(CancellationHandoffOutcome.Status.ACCEPTED);
        assertThat(outcome.cancellationCaseReference()).startsWith("SETTLE-CASE-");
        verify(caseRepository).save(any());
        verify(idempotencyRepository).saveAndFlush(any());
    }

    @Test
    void sameOperationAndDifferentSemanticContentReturnsTypedConflict() {
        SettlementHandoffIdempotency existing = SettlementHandoffIdempotency.builder()
                .operationReference("OP-1")
                .requestFingerprint("different")
                .correlationReference("CORR-1")
                .lifecycleStatus(HandoffIdempotencyStatus.COMPLETED)
                .cancellationCaseReference("CASE-1")
                .build();
        when(idempotencyRepository.findByOperationReference("OP-1")).thenReturn(Optional.of(existing));
        WorkshopSettlementFacadeImpl facade = new WorkshopSettlementFacadeImpl(
                caseRepository, idempotencyRepository, new ObjectMapper(), org.mockito.Mockito.mock(FinalPaymentService.class),
                org.mockito.Mockito.mock(SettlementNotificationHandoffRepository.class));

        CancellationHandoffOutcome outcome = facade.openCancellationHandoff(
                new OpenCancellationHandoffCommand("SES-1", List.of("BK-1"),
                        "FACILITY_ISSUE", "OP-1", "CORR-1"));

        assertThat(outcome.status()).isEqualTo(CancellationHandoffOutcome.Status.REJECTED);
        assertThat(outcome.rejectionReason()).isEqualTo("IDEMPOTENCY_CONFLICT");
        verifyNoInteractions(caseRepository);
    }

    private static Map<String, Method> controllerOperations() {
        List<Class<?>> controllers = List.of(WorkshopFinalBillController.class,
                CustomerBookingCancellationController.class, RequiredSessionChangeController.class,
                MlandCancellationRemedyController.class, WorkshopRefundPolicyController.class);
        return controllers.stream()
                .flatMap(controller -> {
                    String basePath = controller.getAnnotation(RequestMapping.class).value()[0];
                    return List.of(controller.getDeclaredMethods()).stream()
                            .flatMap(method -> {
                                GetMapping get = method.getAnnotation(GetMapping.class);
                                PostMapping post = method.getAnnotation(PostMapping.class);
                                if (get != null) {
                                    return java.util.stream.Stream.of(Map.entry(
                                            "GET " + basePath + suffix(get.value(), get.path()), method));
                                }
                                if (post != null) {
                                    return java.util.stream.Stream.of(Map.entry(
                                            "POST " + basePath + suffix(post.value(), post.path()), method));
                                }
                                return java.util.stream.Stream.empty();
                            });
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String suffix(String[] value, String[] path) {
        String[] candidates = path.length > 0 ? path : value;
        return candidates.length == 0 ? "" : candidates[0];
    }

    private static boolean hasIdempotencyKey(Method method) {
        for (Parameter parameter : method.getParameters()) {
            RequestHeader header = parameter.getAnnotation(RequestHeader.class);
            if (header != null && "Idempotency-Key".equals(header.value())) {
                return true;
            }
        }
        return false;
    }
}
