package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountSummary;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaForbiddenException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.exception.OtaLocationScopeException;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort.CreateDirectBillCommand;
import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.facade.OtaAddonSettlementPort.DirectBillCreationOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("T046: Authorization and Settlement-Facade Contract for OTA Add-on Bill")
class OtaAddonSettlementContractTest {

    @Mock private AccountFacade accountFacade;
    private OtaAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new OtaAuthorizationService(accountFacade);
        ReflectionTestUtils.setField(authorizationService, "allowTestTokens", true);
    }

    @Nested
    @DisplayName("Authorization: Staff location-scoped authority")
    class AuthorizationTests {

        @Test
        @DisplayName("Active Staff with matching location scope is authorized")
        void activeStaffInLocationScope_isAuthorized() {
            AccountSummary staff = new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.test", Instant.now(), Instant.now());
            when(accountFacade.resolveAccount("staff-1")).thenReturn(Optional.of(staff));
            when(accountFacade.getLocationScope(101L)).thenReturn(List.of(1001L, 1002L));

            AccountSummary result = authorizationService.requireStaffLocation("Bearer test-uid:staff-1", 1001L);

            assertThat(result).isNotNull();
            assertThat(result.accountId()).isEqualTo(101L);
            assertThat(result.role()).isEqualTo(ActorRole.STAFF);
        }

        @Test
        @DisplayName("Active Staff attempting action on location outside scope is rejected with OtaLocationScopeException")
        void staffOutsideLocationScope_isRejected() {
            AccountSummary staff = new AccountSummary(101L, ActorRole.STAFF, ActorStatus.ACTIVE, "staff@mland.test", Instant.now(), Instant.now());
            when(accountFacade.resolveAccount("staff-1")).thenReturn(Optional.of(staff));
            when(accountFacade.getLocationScope(101L)).thenReturn(List.of(1001L));

            assertThatThrownBy(() -> authorizationService.requireStaffLocation("Bearer test-uid:staff-1", 9999L))
                    .isInstanceOf(OtaLocationScopeException.class)
                    .hasMessageContaining("outside the Staff member's active location scope");
        }

        @Test
        @DisplayName("Non-staff role (e.g. Member) is rejected with OtaForbiddenException")
        void nonStaffRole_isForbidden() {
            AccountSummary member = new AccountSummary(202L, ActorRole.MEMBER, ActorStatus.ACTIVE, "member@mland.test", Instant.now(), Instant.now());
            when(accountFacade.resolveAccount("member-1")).thenReturn(Optional.of(member));

            assertThatThrownBy(() -> authorizationService.requireStaffLocation("Bearer test-uid:member-1", 1001L))
                    .isInstanceOf(OtaForbiddenException.class)
                    .hasMessageContaining("requires an active Staff account");
        }
    }

    @Nested
    @DisplayName("Settlement Facade: Direct Bill Creation Contract")
    class SettlementFacadeContractTests {

        @Test
        @DisplayName("Valid accepted quote with customer consent creates direct bill")
        void validQuoteWithConsent_createsDirectBill() {
            OtaAddonSettlementPort port = command -> {
                if (command.customerConsentReference() == null || command.customerConsentReference().isBlank()) {
                    return DirectBillCreationOutcome.rejected("MISSING_CONSENT", "Customer consent is required");
                }
                return DirectBillCreationOutcome.success("bill-settlement-001", "https://vnpay.mock/qr/bill-001");
            };

            CreateDirectBillCommand cmd = new CreateDirectBillCommand(
                    "ota-booking-ref-001",
                    "quote-ref-001",
                    "ENGRAVE-ADDON",
                    "Laser engraving personalization",
                    150_000L,
                    "VND",
                    "consent-sig-guest-001",
                    "staff-actor-001",
                    1001L
            );

            DirectBillCreationOutcome outcome = port.createDirectBill(cmd);

            assertThat(outcome.created()).isTrue();
            assertThat(outcome.settlementBillReference()).isEqualTo("bill-settlement-001");
            assertThat(outcome.paymentUrlOrQr()).contains("https://vnpay.mock/qr/bill-001");
            assertThat(outcome.errorCode()).isNull();
        }

        @Test
        @DisplayName("Missing customer consent is rejected at facade boundary without creating a bill")
        void missingCustomerConsent_isRejectedAtBoundary() {
            OtaAddonSettlementPort port = command -> {
                if (command.customerConsentReference() == null || command.customerConsentReference().isBlank()) {
                    return DirectBillCreationOutcome.rejected("MISSING_CONSENT", "Customer consent is required");
                }
                return DirectBillCreationOutcome.success("bill-settlement-001", "url");
            };

            CreateDirectBillCommand cmd = new CreateDirectBillCommand(
                    "ota-booking-ref-001",
                    "quote-ref-001",
                    "ENGRAVE-ADDON",
                    "Laser engraving",
                    150_000L,
                    "VND",
                    null, // Missing consent
                    "staff-actor-001",
                    1001L
            );

            DirectBillCreationOutcome outcome = port.createDirectBill(cmd);

            assertThat(outcome.created()).isFalse();
            assertThat(outcome.errorCode()).isEqualTo("MISSING_CONSENT");
            assertThat(outcome.settlementBillReference()).isNull();
        }
    }
}
