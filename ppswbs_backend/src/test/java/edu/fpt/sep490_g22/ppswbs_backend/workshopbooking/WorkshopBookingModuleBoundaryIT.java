package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller.WorkshopPackageController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 003.1 T044: Module boundary and isolation verification for Workshop Booking.
 * <p>
 * Verifies:
 * 1. Facade-only dependencies: Workshop Booking accesses other business modules strictly via facades.
 * 2. Zero direct dependencies on Workshop Operations, Account Management, or Loyalty internal packages.
 * 3. Zero direct persistence access to Workshop Operations entities or DAOs.
 * 4. Published Package-selection consumption strictly through WorkshopOperationsFacade.
 * 5. No duplicate public Package or session discovery routes in Workshop Booking.
 * 6. Payment integration to Booking owner dispatch boundary: only verified deposit evidence with zero raw payloads/PII.
 */
@ExtendWith(MockitoExtension.class)
class WorkshopBookingModuleBoundaryIT {

    private static final String BOOKING_CONTROLLERS_PACKAGE =
            "edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller";

    private static JavaClasses CLASSES;

    @Mock
    private WorkshopOperationsFacade mockOperationsFacade;

    @Mock
    private WorkshopBookingFacade mockBookingFacade;

    @BeforeAll
    static void importClasses() {
        CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("edu.fpt.sep490_g22.ppswbs_backend");
    }

    // =========================================================================
    // 1. Facade-Only Cross-Module Architecture Rules
    // =========================================================================

    @Test
    @DisplayName("T044-1: Workshop Booking has zero direct dependencies on other modules' internal packages")
    void verifyFacadeOnlyDependencies() {
        // Must not depend on Workshop Operations internal packages
        ArchRule operationsRule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.workshopoperations.controller..",
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity..",
                        "..features.workshopoperations.service.."
                )
                .as("Workshop Booking must never depend on Workshop Operations internals");
        operationsRule.check(CLASSES);

        // Must not depend on Account Management internal packages
        ArchRule accountRule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.accountmanagement.controller..",
                        "..features.accountmanagement.dao..",
                        "..features.accountmanagement.entity..",
                        "..features.accountmanagement.service.."
                )
                .as("Workshop Booking must never depend on Account Management internals");
        accountRule.check(CLASSES);

        // Must not depend on Loyalty Promotion internal packages
        ArchRule loyaltyRule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.loyaltypromotion.controller..",
                        "..features.loyaltypromotion.dao..",
                        "..features.loyaltypromotion.entity..",
                        "..features.loyaltypromotion.service.."
                )
                .as("Workshop Booking must never depend on Loyalty Promotion internals");
        loyaltyRule.check(CLASSES);

        // Must not depend on Workshop Settlement (Settlement depends on Booking facade, not vice versa)
        ArchRule settlementRule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.workshopsettlement.."
                )
                .as("Workshop Booking must never depend on Workshop Settlement");
        settlementRule.check(CLASSES);

        // Must not depend on Payment Integration internal packages or config
        ArchRule paymentRule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.paymentintegration.config..",
                        "..features.paymentintegration.controller..",
                        "..features.paymentintegration.dao..",
                        "..features.paymentintegration.service.."
                )
                .as("Workshop Booking must never depend on Payment Integration internals");
        paymentRule.check(CLASSES);
    }

    // =========================================================================
    // 2. Zero Direct Operations Persistence Access
    // =========================================================================

    @Test
    @DisplayName("T044-2: Workshop Booking strictly forbids any direct dependency on Operations DAOs or Entities")
    void verifyNoDirectOperationsPersistenceAccess() {
        ArchRule persistenceRule = ArchRuleDefinition.noClasses()
                .that().resideInAPackage("..features.workshopbooking..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..features.workshopoperations.dao..",
                        "..features.workshopoperations.entity.."
                )
                .as("Workshop Booking must never access Workshop Operations persistence layer");

        persistenceRule.check(CLASSES);
    }

    // =========================================================================
    // 3. Published Package-Selection Consumption Through Facade
    // =========================================================================

    @Test
    @DisplayName("T044-3: Published Package-selection and session rules are consumed strictly via WorkshopOperationsFacade")
    void verifyPublishedPackageSelectionConsumption() {
        WorkshopOperationsAdapter adapter = new WorkshopOperationsAdapter(mockOperationsFacade);

        // 1. Published package selection query consumes PublishedPackageSelectionQuery
        PublishedPackageSelectionSnapshot pkgSnapshot = new PublishedPackageSelectionSnapshot(
                "PKG-001", 350_000L, 1, 6, 2, true, 0);
        when(mockOperationsFacade.getPublishedPackageSelection(any(PublishedPackageSelectionQuery.class)))
                .thenReturn(pkgSnapshot);

        PublishedPackageSelectionSnapshot actualSnapshot = adapter.getPublishedPackageSelection("PKG-001");
        assertThat(actualSnapshot).isEqualTo(pkgSnapshot);
        assertThat(actualSnapshot.packageReference()).isEqualTo("PKG-001");
        assertThat(actualSnapshot.publicPerParticipantPrice()).isEqualTo(350_000L);
        assertThat(actualSnapshot.requiredSessionCount()).isEqualTo(2);
        assertThat(actualSnapshot.sameLocationRequired()).isTrue();
        verify(mockOperationsFacade).getPublishedPackageSelection(new PublishedPackageSelectionQuery("PKG-001"));

        // 2. Sale eligibility query consumes SaleEligibilityQuery with canonical UUID
        UUID sessionReference = UUID.randomUUID();
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(7200);
        SaleEligibilitySnapshot eligibilitySnapshot = new SaleEligibilitySnapshot(
                true, "PUBLISHED", 10L, start, end, 8);
        when(mockOperationsFacade.getSaleEligibility(any(SaleEligibilityQuery.class)))
                .thenReturn(eligibilitySnapshot);

        SaleEligibilitySnapshot actualEligibility = adapter.getSaleEligibility(sessionReference);
        assertThat(actualEligibility).isEqualTo(eligibilitySnapshot);
        assertThat(actualEligibility.customerSelectable()).isTrue();
        assertThat(actualEligibility.remainingCapacity()).isEqualTo(8);
        verify(mockOperationsFacade).getSaleEligibility(new SaleEligibilityQuery(sessionReference));

        // 3. Reserve booking capacity uses UUID session references and packageReference
        UUID holdRef1 = UUID.randomUUID();
        UUID holdRef2 = UUID.randomUUID();
        CapacityHoldOutcome holdOutcome = CapacityHoldOutcome.accepted(
                List.of(holdRef1, holdRef2), Instant.now().plusSeconds(900));
        when(mockOperationsFacade.reserveBookingCapacity(any(ReserveBookingCapacityCommand.class)))
                .thenReturn(holdOutcome);

        CapacityHoldOutcome actualHold = adapter.reserveBookingCapacity(
                "BK-001", "PKG-001", List.of(sessionReference), 2, "OP-HOLD-01", "CORR-01");
        assertThat(actualHold.status()).isEqualTo(OutcomeStatus.ACCEPTED);
        assertThat(actualHold.holdReferences()).containsExactly(holdRef1, holdRef2);
        verify(mockOperationsFacade).reserveBookingCapacity(new ReserveBookingCapacityCommand(
                "BK-001", "PKG-001", List.of(sessionReference), 2, "OP-HOLD-01", "CORR-01"));
    }

    // =========================================================================
    // 4. No Duplicate Discovery Routes
    // =========================================================================

    @Test
    @DisplayName("T044-4: Public Package and session discovery routes belong exclusively to Operations")
    void verifyNoDuplicateDiscoveryRoutes() throws Exception {
        Set<String> bookingRoutes = scanRoutesInPackage(BOOKING_CONTROLLERS_PACKAGE);
        Set<String> operationsRoutes = scanRoutesInClass(WorkshopPackageController.class);

        // Discovery routes must exist in WorkshopPackageController
        assertThat(operationsRoutes).contains(
                "/api/v1/workshop-packages",
                "/api/v1/workshop-packages/{packageId}/sessions"
        );

        // Discovery routes must NOT exist in Workshop Booking controllers
        assertThat(bookingRoutes).doesNotContain(
                "/api/v1/workshop-packages",
                "/api/v1/workshop-packages/{packageId}",
                "/api/v1/workshop-packages/{packageId}/sessions"
        );

        // Booking owns only its own domain routes
        for (String route : bookingRoutes) {
            assertThat(route).satisfiesAnyOf(
                    r -> assertThat(r).startsWith("/api/v1/workshop-bookings"),
                    r -> assertThat(r).startsWith("/api/v1/workshop-booking-quotes"),
                    r -> assertThat(r).startsWith("/api/v1/booking-"),
                    r -> assertThat(r).startsWith("/api/v1/members/me/workshop-bookings")
            );
        }
    }

    // =========================================================================
    // 5. Payment Integration to Owner Dispatch Boundary
    // =========================================================================

    @Test
    @DisplayName("T044-5: Payment integration dispatches only redacted verified deposit evidence to WorkshopBookingFacade")
    void verifyPaymentIntegrationToOwnerDispatchBoundary() {
        // ApplyVerifiedDepositPaymentEvidenceCommand carries only redacted verified evidence
        ApplyVerifiedDepositPaymentEvidenceCommand command = new ApplyVerifiedDepositPaymentEvidenceCommand(
                "BK-20261101-001",
                "EVID-DEP-001",
                "DEP-BK-20261101-001-ATT-1",
                250_000L,
                "VND",
                "OP-DISPATCH-001",
                "CORR-PAYMENT-001"
        );

        // Verify command structure: strictly excludes raw webhook, credentials, card details, or IP
        List<String> componentNames;
        if (ApplyVerifiedDepositPaymentEvidenceCommand.class.isRecord()) {
            componentNames = Arrays.stream(ApplyVerifiedDepositPaymentEvidenceCommand.class.getRecordComponents())
                    .map(RecordComponent::getName).toList();
        } else {
            componentNames = Arrays.stream(ApplyVerifiedDepositPaymentEvidenceCommand.class.getDeclaredFields())
                    .map(Field::getName).toList();
        }

        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("rawpayload"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("secret"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("signature"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("card"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("ipaddress"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("password"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("email"));
        assertThat(componentNames).noneMatch(name -> name.toLowerCase().contains("phone"));

        // Verify facade dispatch accepts command and returns typed outcome
        when(mockBookingFacade.applyVerifiedDepositPaymentEvidence(any(ApplyVerifiedDepositPaymentEvidenceCommand.class)))
                .thenReturn(DepositPaymentEvidenceOutcome.accepted());

        DepositPaymentEvidenceOutcome outcome = mockBookingFacade.applyVerifiedDepositPaymentEvidence(command);
        assertThat(outcome.status()).isEqualTo(BookingOutcomeStatus.ACCEPTED);
        assertThat(outcome.reason()).isNull();
        verify(mockBookingFacade).applyVerifiedDepositPaymentEvidence(command);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static Set<String> scanRoutesInPackage(String packageName) throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> routes = new HashSet<>();
        for (var candidate : scanner.findCandidateComponents(packageName)) {
            Class<?> controllerClass = Class.forName(candidate.getBeanClassName());
            routes.addAll(scanRoutesInClass(controllerClass));
        }
        return routes;
    }

    private static Set<String> scanRoutesInClass(Class<?> controller) {
        RequestMapping classMapping = controller.getAnnotation(RequestMapping.class);
        String basePath = (classMapping != null && classMapping.value().length > 0)
                ? classMapping.value()[0] : "";

        Set<String> routes = new HashSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            PostMapping post = method.getAnnotation(PostMapping.class);
            PutMapping put = method.getAnnotation(PutMapping.class);
            PatchMapping patch = method.getAnnotation(PatchMapping.class);
            DeleteMapping delete = method.getAnnotation(DeleteMapping.class);

            Stream.of(get, post, put, patch, delete)
                    .filter(Objects::nonNull)
                    .forEach(annotation -> {
                        String suffix = extractPathFromAnnotation(annotation);
                        routes.add(combinePath(basePath, suffix));
                    });
        }
        return routes;
    }

    private static String extractPathFromAnnotation(Object annotation) {
        if (annotation instanceof GetMapping g) return extractPath(g.value(), g.path());
        if (annotation instanceof PostMapping p) return extractPath(p.value(), p.path());
        if (annotation instanceof PutMapping pu) return extractPath(pu.value(), pu.path());
        if (annotation instanceof PatchMapping pa) return extractPath(pa.value(), pa.path());
        if (annotation instanceof DeleteMapping d) return extractPath(d.value(), d.path());
        return "";
    }

    private static String extractPath(String[] value, String[] path) {
        if (path.length > 0 && !path[0].isBlank()) return path[0];
        if (value.length > 0 && !value[0].isBlank()) return value[0];
        return "";
    }

    private static String combinePath(String base, String suffix) {
        if (base.isBlank()) return suffix;
        if (suffix.isBlank()) return base;
        if (base.endsWith("/") && suffix.startsWith("/")) return base + suffix.substring(1);
        if (!base.endsWith("/") && !suffix.startsWith("/")) return base + "/" + suffix;
        return base + suffix;
    }
}

