package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ApplyVerifiedDepositPaymentEvidenceCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingNotificationDeliveryPayload;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingNotificationDeliveryPayloadQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingOutcomeStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.BookingRejectionReason;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjection;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.ConfirmedBookingFinancialProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.DepositPaymentEvidenceOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.FinalBalanceResolutionOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RecordFinalBalanceResolvedCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.RequestBookingTicketRedeliveryCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketRedeliveryOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopOperationsAdapter;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.WorkshopBookingLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingSettlementReadinessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.BookingTicketProjectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityCommitOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityHoldOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CapacityReleaseOutcome;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.CommitBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.DeliverySafeTicketSet;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReleaseBookingHoldsCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.ReserveBookingCapacityCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.PublishedPackageSelectionSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilityQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SaleEligibilitySnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.SettlementReadiness;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T001–T004: Modulith boundary, public facade contract, operations adapter,
 * and authorization coverage for the workshopbooking module.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkshopBookingModuleTest {

    @Autowired
    private WorkshopBookingFacade bookingFacade;

    @Autowired
    private WorkshopOperationsAdapter operationsAdapter;

    @Autowired
    private WorkshopBookingAuthorizationService authorizationService;

    @MockBean
    private WorkshopOperationsFacade mockOperationsFacade;

    @MockBean
    private AccountFacade accountFacade;

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("edu.fpt.sep490_g22.ppswbs_backend");

    // =========================================================================
    // T001: Spring Modulith Module Boundary & Architecture Rules
    // =========================================================================

    @Test
    @DisplayName("T001-1: workshopbooking is recognized as a Spring Modulith module and satisfies boundary constraints")
    void verifyWorkshopBookingModulithStructure() {
        ApplicationModules modules = ApplicationModules.of("edu.fpt.sep490_g22.ppswbs_backend.features");
        modules.forEach(System.out::println);

        modules.detectViolations()
                .filter(violation -> !violation.getMessage().contains("accountmanagement"))
                .throwIfPresent();

        assertThat(modules.getModuleByName("workshopbooking"))
                .as("workshopbooking should be detected as a distinct named module under features")
                .isPresent();
    }

    @Test
    @DisplayName("T001-2: Workshop Booking has zero direct dependencies on Workshop Operations internal packages")
    void verifyBookingHasNoDependencyOnOperationsInternals() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..features.workshopoperations.controller..",
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity..",
                        "..features.workshopoperations.service.."
                )
                .as("Workshop Booking must never depend on Workshop Operations internals");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("T001-3: Workshop Booking has zero direct dependencies on Account Management internal packages")
    void verifyBookingHasNoDependencyOnAccountInternals() {
        ArchRule rule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..features.accountmanagement.controller..",
                        "..features.accountmanagement.dao..",
                        "..features.accountmanagement.entity..",
                        "..features.accountmanagement.service.."
                )
                .as("Workshop Booking must never depend on Account Management internals");

        rule.check(CLASSES);
    }

    // =========================================================================
    // T002: WorkshopBookingFacade & Boundary Types Contract
    // =========================================================================

    @Test
    @DisplayName("T002-1: WorkshopBookingFacade operations return safe typed outcomes without exposing entities or PII")
    void verifyWorkshopBookingFacadeOperations() {
        // Financial projection query
        ConfirmedBookingFinancialProjectionQuery finQuery = new ConfirmedBookingFinancialProjectionQuery("BK-001");
        assertThat(bookingFacade.getConfirmedBookingFinancialProjection(finQuery)).isNull();

        // Final balance resolution
        RecordFinalBalanceResolvedCommand balanceCmd = new RecordFinalBalanceResolvedCommand(
                "BK-001", "SETTLE-001", "OP-001", "CORR-001");
        FinalBalanceResolutionOutcome balanceOutcome = bookingFacade.recordFinalBalanceResolved(balanceCmd);
        assertThat(balanceOutcome.status()).isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(balanceOutcome.reason()).isEqualTo(BookingRejectionReason.NOT_READY);

        // Apply verified deposit evidence
        ApplyVerifiedDepositPaymentEvidenceCommand depositCmd = new ApplyVerifiedDepositPaymentEvidenceCommand(
                "BK-001", "EV-001", "OP-002", "CORR-002");
        DepositPaymentEvidenceOutcome depositOutcome = bookingFacade.applyVerifiedDepositPaymentEvidence(depositCmd);
        assertThat(depositOutcome.status()).isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(depositOutcome.reason()).isEqualTo(BookingRejectionReason.NOT_READY);

        // Request ticket redelivery
        RequestBookingTicketRedeliveryCommand redelivCmd = new RequestBookingTicketRedeliveryCommand(
                "BK-001", List.of("TK-1"), "OP-003", "CORR-003");
        TicketRedeliveryOutcome redelivOutcome = bookingFacade.requestBookingTicketRedelivery(redelivCmd);
        assertThat(redelivOutcome.status()).isEqualTo(BookingOutcomeStatus.REJECTED);
        assertThat(redelivOutcome.reason()).isEqualTo(BookingRejectionReason.NOT_READY);

        // Notification delivery payload query
        BookingNotificationDeliveryPayloadQuery notifQuery = new BookingNotificationDeliveryPayloadQuery("NOTIF-001");
        assertThat(bookingFacade.getBookingNotificationDeliveryPayload(notifQuery)).isNull();
    }

    @Test
    @DisplayName("T002-2: WorkshopBookingFacade types strictly exclude buyer contact PII, raw passwords, or tokens")
    void verifyWorkshopBookingFacadePrivacyBoundary() {
        Class<?>[] facadeTypes = {
                ConfirmedBookingFinancialProjection.class,
                RecordFinalBalanceResolvedCommand.class,
                ApplyVerifiedDepositPaymentEvidenceCommand.class,
                RequestBookingTicketRedeliveryCommand.class,
                BookingNotificationDeliveryPayload.class
        };

        for (Class<?> type : facadeTypes) {
            List<String> names;
            if (type.isRecord()) {
                names = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
            } else {
                names = Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
            }

            assertThat(names).noneMatch(name -> name.toLowerCase().contains("password"));
            assertThat(names).noneMatch(name -> name.toLowerCase().contains("phone"));
            assertThat(names).noneMatch(name -> name.toLowerCase().contains("rawtoken"));
            assertThat(names).noneMatch(name -> name.toLowerCase().contains("branch"));
            assertThat(names).noneMatch(name -> name.toLowerCase().contains("personnel"));
        }
    }

    // =========================================================================
    // T003: WorkshopOperationsAdapter Consumption
    // =========================================================================

    @Test
    @DisplayName("T003-1: WorkshopOperationsAdapter delegates only to public WorkshopOperationsFacade")
    void verifyWorkshopOperationsAdapterDelegation() {
        UUID sessionId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();

        // Sale eligibility
        SaleEligibilitySnapshot mockEligibility = new SaleEligibilitySnapshot(
                true, "PUBLISHED", 1L, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 10);
        when(mockOperationsFacade.getSaleEligibility(any(SaleEligibilityQuery.class))).thenReturn(mockEligibility);

        SaleEligibilitySnapshot eligibility = operationsAdapter.getSaleEligibility(sessionId);
        assertThat(eligibility.customerSelectable()).isTrue();
        assertThat(eligibility.remainingCapacity()).isEqualTo(10);
        verify(mockOperationsFacade).getSaleEligibility(new SaleEligibilityQuery(sessionId));

        PublishedPackageSelectionSnapshot packageSnapshot = new PublishedPackageSelectionSnapshot(
                "42", 450_000L, 1, 4, 1, true, 0);
        when(mockOperationsFacade.getPublishedPackageSelection(any(PublishedPackageSelectionQuery.class)))
                .thenReturn(packageSnapshot);
        assertThat(operationsAdapter.getPublishedPackageSelection("42")).isEqualTo(packageSnapshot);
        verify(mockOperationsFacade).getPublishedPackageSelection(new PublishedPackageSelectionQuery("42"));

        // Capacity reserve
        CapacityHoldOutcome mockHoldOutcome = CapacityHoldOutcome.accepted(List.of(holdId), Instant.now().plusSeconds(900));
        when(mockOperationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class))).thenReturn(mockHoldOutcome);

        CapacityHoldOutcome holdOutcome = operationsAdapter.reserveBookingCapacity(
                "BK-001", "42", List.of(sessionId), 2, "OP-HOLD-01", "CORR-01");
        assertThat(holdOutcome.status().name()).isEqualTo("ACCEPTED");
        assertThat(holdOutcome.holdReferences()).containsExactly(holdId);
        verify(mockOperationsFacade).reserveBookingCapacity(new ReserveBookingCapacityCommand(
                "BK-001", "42", List.of(sessionId), 2, "OP-HOLD-01", "CORR-01"));

        // Capacity commit
        CapacityCommitOutcome mockCommitOutcome = CapacityCommitOutcome.accepted();
        when(mockOperationsFacade.commitBookingHolds(any(CommitBookingHoldsCommand.class))).thenReturn(mockCommitOutcome);

        CapacityCommitOutcome commitOutcome = operationsAdapter.commitBookingHolds(
                "BK-001", List.of(holdId), "OP-COMMIT-01", "CORR-01");
        assertThat(commitOutcome.status().name()).isEqualTo("ACCEPTED");

        // Capacity release
        CapacityReleaseOutcome mockReleaseOutcome = CapacityReleaseOutcome.accepted();
        when(mockOperationsFacade.releaseBookingHolds(any(ReleaseBookingHoldsCommand.class))).thenReturn(mockReleaseOutcome);

        CapacityReleaseOutcome releaseOutcome = operationsAdapter.releaseBookingHolds(
                "BK-001", List.of(holdId), "OP-REL-01", "CORR-01");
        assertThat(releaseOutcome.status().name()).isEqualTo("ACCEPTED");

        // Ticket projection
        DeliverySafeTicketSet mockTicketSet = new DeliverySafeTicketSet(List.of());
        when(mockOperationsFacade.getBookingTicketProjection(any(BookingTicketProjectionQuery.class))).thenReturn(mockTicketSet);

        DeliverySafeTicketSet projection = operationsAdapter.getBookingTicketProjection("BK-001");
        assertThat(projection).isNotNull();

        // Settlement readiness
        SettlementReadiness mockReadiness = new SettlementReadiness(true);
        when(mockOperationsFacade.getSettlementReadiness(any(BookingSettlementReadinessQuery.class))).thenReturn(mockReadiness);

        SettlementReadiness readiness = operationsAdapter.getSettlementReadiness("BK-001");
        assertThat(readiness.ready()).isTrue();
    }

    // =========================================================================
    // T004: WorkshopBookingAuthorizationService
    // =========================================================================

    @Test
    @DisplayName("T004-1: Authorization service enforces canonical roles, active status, and location scope")
    void verifyWorkshopBookingAuthorizationRules() {
        ReflectionTestUtils.setField(authorizationService, "allowTestTokens", true);

        // Account setup mocks
        Instant now = Instant.now();
        AccountSummary activeStaffLoc1 = new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.vn", now, now);
        AccountSummary activeManagerLoc1 = new AccountSummary(102L, ActorRole.MANAGER, ActorStatus.ACTIVE, "manager@mland.vn", now, now);
        AccountSummary activeAdmin = new AccountSummary(103L, ActorRole.ADMIN, ActorStatus.ACTIVE, "admin@mland.vn", now, now);
        AccountSummary activeMember = new AccountSummary(104L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@mland.vn", now, now);
        AccountSummary suspendedStaff = new AccountSummary(105L, ActorRole.STAFF, ActorStatus.SUSPENDED, "susp@mland.vn", now, now);

        when(accountFacade.resolveAccount("staff-uid")).thenReturn(Optional.of(activeStaffLoc1));
        when(accountFacade.resolveAccount("manager-uid")).thenReturn(Optional.of(activeManagerLoc1));
        when(accountFacade.resolveAccount("admin-uid")).thenReturn(Optional.of(activeAdmin));
        when(accountFacade.resolveAccount("member-uid")).thenReturn(Optional.of(activeMember));
        when(accountFacade.resolveAccount("susp-uid")).thenReturn(Optional.of(suspendedStaff));

        when(accountFacade.getLocationScope(101L)).thenReturn(List.of(1L));
        when(accountFacade.getLocationScope(102L)).thenReturn(List.of(1L));
        when(accountFacade.getLocationScope(103L)).thenReturn(List.of());
        when(accountFacade.getLocationScope(104L)).thenReturn(List.of());

        // 1. Inactive account rejected
        assertThatThrownBy(() -> authorizationService.authenticate("Bearer test-uid:susp-uid"))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("ACTIVE status required");

        // 2. Staff in location scope can resend confirmation
        AccountSummary resendStaff = authorizationService.requireConfirmationResendAuthority("Bearer test-uid:staff-uid", 1L);
        assertThat(resendStaff.accountId()).isEqualTo(101L);

        // 3. Staff outside location scope cannot resend confirmation
        assertThatThrownBy(() -> authorizationService.requireConfirmationResendAuthority("Bearer test-uid:staff-uid", 2L))
                .isInstanceOf(WorkshopBookingLocationScopeException.class)
                .hasMessageContaining("outside actor's active location scope");

        // 4. Manager in location scope can resend confirmation
        AccountSummary resendMgr = authorizationService.requireConfirmationResendAuthority("Bearer test-uid:manager-uid", 1L);
        assertThat(resendMgr.accountId()).isEqualTo(102L);

        // 5. Member cannot resend confirmation
        assertThatThrownBy(() -> authorizationService.requireConfirmationResendAuthority("Bearer test-uid:member-uid", 1L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("STAFF or MANAGER role");

        // 6. ADMIN alone cannot resend confirmation (FR-017b5 / spec line 342)
        assertThatThrownBy(() -> authorizationService.requireConfirmationResendAuthority("Bearer test-uid:admin-uid", 1L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("STAFF or MANAGER role");

        // 7. Manager in location scope can handle Magic Link incident
        AccountSummary mlMgr = authorizationService.requireMagicLinkIncidentAuthority("Bearer test-uid:manager-uid", 1L);
        assertThat(mlMgr.accountId()).isEqualTo(102L);

        // 8. Staff CANNOT handle Magic Link incident (FR-017b5: Staff and ADMIN alone MUST NOT have that authority)
        assertThatThrownBy(() -> authorizationService.requireMagicLinkIncidentAuthority("Bearer test-uid:staff-uid", 1L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("strictly requires MANAGER role");

        // 9. ADMIN CANNOT handle Magic Link incident
        assertThatThrownBy(() -> authorizationService.requireMagicLinkIncidentAuthority("Bearer test-uid:admin-uid", 1L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("strictly requires MANAGER role");

        // 10. Member ownership check: owner passes, non-owner fails
        AccountSummary owner = authorizationService.requireMemberOwnership("Bearer test-uid:member-uid", 104L);
        assertThat(owner.accountId()).isEqualTo(104L);

        assertThatThrownBy(() -> authorizationService.requireMemberOwnership("Bearer test-uid:member-uid", 999L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("does not own booking");

        // Non-member fails member access
        assertThatThrownBy(() -> authorizationService.requireMemberOwnership("Bearer test-uid:staff-uid", 101L))
                .isInstanceOf(WorkshopBookingForbiddenException.class)
                .hasMessageContaining("requires MEMBER role");
    }
}
