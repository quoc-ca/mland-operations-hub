package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyAuthorization.Capability;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Role and status rules of FE-06 (spec FR-001, US1.6, US3.6, US6.4, US8.3): no role implies another. */
class LoyaltyAuthorizationTests {

    private final LoyaltyAuthorization authorization = new LoyaltyAuthorization();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(ActorRole role, ActorStatus status, Long accountId) {
        CurrentAccountActor actor = new CurrentAccountActor(accountId, role, status, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(actor, "n/a"));
    }

    private static void assertCode(Runnable action, LoyaltyErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(LoyaltyException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void anonymousCallerNeedsAuthentication() {
        assertCode(() -> authorization.requireManager(Capability.MANAGE_PROGRAMS), LoyaltyErrorCode.AUTHENTICATION_REQUIRED);
        assertCode(authorization::requireMember, LoyaltyErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void activeManagerManagesProgramsAndPolicy() {
        signIn(ActorRole.MANAGER, ActorStatus.ACTIVE, 41L);
        assertThat(authorization.requireManager(Capability.MANAGE_PROGRAMS)).isEqualTo("41");
        assertThat(authorization.requireManager(Capability.MANAGE_POLICY)).isEqualTo("41");
    }

    @Test
    void adminStaffAndMemberHoldNoManagerCapability() {
        for (ActorRole role : new ActorRole[] {ActorRole.ADMIN, ActorRole.STAFF, ActorRole.MEMBER}) {
            signIn(role, ActorStatus.ACTIVE, 7L);
            assertCode(() -> authorization.requireManager(Capability.MANAGE_PROGRAMS), LoyaltyErrorCode.ACCESS_DENIED);
            assertCode(() -> authorization.requireManager(Capability.MANAGE_POLICY), LoyaltyErrorCode.ACCESS_DENIED);
        }
    }

    @Test
    void onlyAnActiveMemberUsesOwnBenefitsAndTheIdComesFromTheActor() {
        signIn(ActorRole.MEMBER, ActorStatus.ACTIVE, 9_000_001L);
        assertThat(authorization.requireMember()).isEqualTo(9_000_001L);
        for (ActorRole role : new ActorRole[] {ActorRole.MANAGER, ActorRole.ADMIN, ActorRole.STAFF}) {
            signIn(role, ActorStatus.ACTIVE, 7L);
            assertCode(authorization::requireMember, LoyaltyErrorCode.ACCESS_DENIED);
        }
    }

    @Test
    void notActiveAccountsAreDeniedForEveryRole() {
        for (ActorStatus status : new ActorStatus[] {ActorStatus.PENDING_SETUP, ActorStatus.SUSPENDED}) {
            signIn(ActorRole.MANAGER, status, 41L);
            assertCode(() -> authorization.requireManager(Capability.MANAGE_PROGRAMS), LoyaltyErrorCode.ACCESS_DENIED);
            signIn(ActorRole.MEMBER, status, 9L);
            assertCode(authorization::requireMember, LoyaltyErrorCode.ACCESS_DENIED);
        }
    }

    @Test
    void anActorWithoutAnAccountIdIsDenied() {
        signIn(ActorRole.MANAGER, ActorStatus.ACTIVE, null);
        assertCode(() -> authorization.requireManager(Capability.MANAGE_PROGRAMS), LoyaltyErrorCode.ACCESS_DENIED);
    }

    @Test
    void aPrincipalThatIsNotTheVerifiedActorGrantsNothing() {
        // e.g. a plain Firebase token principal or a string name: never mapped to a role (constitution II).
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("manager@example.com", "n/a",
                "ROLE_MANAGER", "ROLE_OWNER"));
        assertCode(() -> authorization.requireManager(Capability.MANAGE_PROGRAMS), LoyaltyErrorCode.AUTHENTICATION_REQUIRED);
        assertCode(authorization::requireMember, LoyaltyErrorCode.AUTHENTICATION_REQUIRED);
    }

    @Test
    void aMemberCapabilityCannotBeAskedThroughTheManagerGate() {
        signIn(ActorRole.MANAGER, ActorStatus.ACTIVE, 41L);
        assertThatThrownBy(() -> authorization.requireManager(Capability.USE_OWN_BENEFITS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
