package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for provisional target account semantic query on AccountFacade.
 * Verifies contract expectations required by Workshop Operations 003.2.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AccountFacadeTargetAccountIT {

    @Autowired
    private AccountFacade accountFacade;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountProfileRepository profileRepository;

    @Autowired
    private AccountLocationAssignmentRepository locationAssignmentRepository;

    @BeforeEach
    void cleanUp() {
        locationAssignmentRepository.deleteAll();
        profileRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("Non-existent or null accountId returns Optional.empty()")
    void nonExistentAccountReturnsEmpty() {
        Optional<TargetAccountSnapshot> nullResult = accountFacade.getTargetAccount(null);
        assertThat(nullResult).isEmpty();

        Optional<TargetAccountSnapshot> missingResult = accountFacade.getTargetAccount(999999L);
        assertThat(missingResult).isEmpty();

        // Convenience alias test
        Optional<TargetAccountSnapshot> aliasResult = accountFacade.resolveTargetAccount(999999L);
        assertThat(aliasResult).isEmpty();
    }

    @Test
    @DisplayName("Canonical role mapping correctly returns MEMBER, STAFF, MANAGER, ADMIN")
    void canonicalRoleMapping() {
        for (AccountRole entityRole : AccountRole.values()) {
            Account account = accountRepository.save(Account.builder()
                    .firebaseUid("uid-role-" + entityRole.name())
                    .loginEmail("role-" + entityRole.name().toLowerCase() + "@mland.vn")
                    .role(entityRole)
                    .status(AccountStatus.ACTIVE)
                    .build());

            Optional<TargetAccountSnapshot> snapshotOpt = accountFacade.getTargetAccount(account.getId());
            assertThat(snapshotOpt).isPresent();

            TargetAccountSnapshot snapshot = snapshotOpt.get();
            assertThat(snapshot.accountId()).isEqualTo(account.getId());
            assertThat(snapshot.canonicalRole()).isEqualTo(ActorRole.valueOf(entityRole.name()));
            assertThat(snapshot.role()).isEqualTo(ActorRole.valueOf(entityRole.name()));
        }
    }

    @Test
    @DisplayName("Account status mapping correctly returns ACTIVE, PENDING_SETUP, and SUSPENDED")
    void accountStatusMapping() {
        for (AccountStatus entityStatus : AccountStatus.values()) {
            Account account = accountRepository.save(Account.builder()
                    .firebaseUid("uid-status-" + entityStatus.name())
                    .loginEmail("status-" + entityStatus.name().toLowerCase() + "@mland.vn")
                    .role(AccountRole.STAFF)
                    .status(entityStatus)
                    .build());

            Optional<TargetAccountSnapshot> snapshotOpt = accountFacade.getTargetAccount(account.getId());
            assertThat(snapshotOpt).isPresent();

            TargetAccountSnapshot snapshot = snapshotOpt.get();
            assertThat(snapshot.status()).isEqualTo(ActorStatus.valueOf(entityStatus.name()));
        }
    }

    @Test
    @DisplayName("activeLocationIds includes only active assignments and excludes inactive assignments")
    void activeVsInactiveLocationAssignments() {
        Account account = accountRepository.save(Account.builder()
                .firebaseUid("uid-loc-" + UUID.randomUUID())
                .loginEmail("mgr-loc@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .build());

        // Assign active location 101L and 103L
        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(account.getId())
                .locationId(101L)
                .active(true)
                .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(account.getId())
                .locationId(103L)
                .active(true)
                .build());

        // Assign inactive location 102L
        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(account.getId())
                .locationId(102L)
                .active(false)
                .build());

        Optional<TargetAccountSnapshot> snapshotOpt = accountFacade.getTargetAccount(account.getId());
        assertThat(snapshotOpt).isPresent();

        TargetAccountSnapshot snapshot = snapshotOpt.get();
        assertThat(snapshot.accountId()).isEqualTo(account.getId());
        assertThat(snapshot.canonicalRole()).isEqualTo(ActorRole.MANAGER);
        assertThat(snapshot.status()).isEqualTo(ActorStatus.ACTIVE);

        // Crucial invariant: activeLocationIds contains 101 and 103, strictly excludes inactive 102
        assertThat(snapshot.activeWorkshopLocationIds()).containsExactlyInAnyOrder(101L, 103L);
        assertThat(snapshot.activeLocationIds()).containsExactlyInAnyOrder(101L, 103L);
        assertThat(snapshot.activeLocationIds()).doesNotContain(102L);
    }

    @Test
    @DisplayName("TargetAccountSnapshot does not leak Account entities, credentials, or PII")
    void noEntityOrPiiLeakage() {
        // Reflection check on TargetAccountSnapshot record components / fields
        List<String> fieldNames = Arrays.stream(TargetAccountSnapshot.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        // Must declare only the 4 canonical snapshot fields
        assertThat(fieldNames).containsExactlyInAnyOrder(
                "accountId",
                "canonicalRole",
                "status",
                "activeWorkshopLocationIds"
        );

        // Strictly verify no PII or internal security details exist
        assertThat(fieldNames).noneMatch(f ->
                f.toLowerCase().contains("firebase") ||
                f.toLowerCase().contains("uid") ||
                f.toLowerCase().contains("email") ||
                f.toLowerCase().contains("phone") ||
                f.toLowerCase().contains("token") ||
                f.toLowerCase().contains("entity") ||
                f.toLowerCase().contains("repo")
        );
    }

    @Test
    @DisplayName("findTargetFacts returns FOUND with active locations, and NOT_FOUND for unknown account")
    void findTargetFactsTests() {
        TargetFactsOutcome notFound = accountFacade.findTargetFacts(AccountId.of(999999L));
        assertThat(notFound).isInstanceOf(TargetFactsOutcome.NotFound.class);

        Account account = accountRepository.save(Account.builder()
                .firebaseUid("uid-facts-" + UUID.randomUUID())
                .loginEmail("staff-facts@mland.vn")
                .role(AccountRole.STAFF)
                .status(AccountStatus.ACTIVE)
                .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(account.getId())
                .locationId(501L)
                .active(true)
                .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(account.getId())
                .locationId(502L)
                .active(false)
                .build());

        TargetFactsOutcome found = accountFacade.findTargetFacts(AccountId.of(account.getId()));
        assertThat(found).isInstanceOf(TargetFactsOutcome.Found.class);
        TargetAccountFacts facts = ((TargetFactsOutcome.Found) found).facts();
        assertThat(facts.accountId()).isEqualTo(AccountId.of(account.getId()));
        assertThat(facts.role()).isEqualTo(AccountRole.STAFF);
        assertThat(facts.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(facts.activeWorkshopLocationIds()).containsExactly(501L);
    }

    @Test
    @DisplayName("findEligibleRoster returns ACTIVE staff assigned to location with Profile full_name and excludes managers")
    void findEligibleRosterTests() {
        Account staff = accountRepository.save(Account.builder()
                .firebaseUid("uid-roster-staff-" + UUID.randomUUID())
                .loginEmail("staff-roster@mland.vn")
                .role(AccountRole.STAFF)
                .status(AccountStatus.ACTIVE)
                .build());

        profileRepository.save(AccountProfile.builder()
                .accountId(staff.getId())
                .fullName("Nguyen Van Staff")
                .phone("0901234567")
                .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(staff.getId())
                .locationId(600L)
                .active(true)
                .build());

        Account manager = accountRepository.save(Account.builder()
                .firebaseUid("uid-roster-mgr-" + UUID.randomUUID())
                .loginEmail("manager-roster@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .build());

        profileRepository.save(AccountProfile.builder()
                .accountId(manager.getId())
                .fullName("Tran Van Manager")
                .phone("0907654321")
                .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(manager.getId())
                .locationId(600L)
                .active(true)
                .build());

        // Canonical 1-parameter signature: findEligibleRoster(workshopLocationId)
        RosterOutcome outcome = accountFacade.findEligibleRoster(600L);
        assertThat(outcome).isInstanceOf(RosterOutcome.Available.class);
        List<InternalRosterEntry> entries = ((RosterOutcome.Available) outcome).entries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).accountId()).isEqualTo(AccountId.of(staff.getId()));
        assertThat(entries.get(0).displayName()).isEqualTo("Nguyen Van Staff");
        assertThat(entries.get(0).role()).isEqualTo(AccountRole.STAFF);

        // Deprecated 2-parameter signature: MANAGER role explicitly returns empty
        RosterOutcome mgrOutcome = accountFacade.findEligibleRoster(600L, AccountRole.MANAGER);
        assertThat(mgrOutcome).isInstanceOf(RosterOutcome.Available.class);
        assertThat(((RosterOutcome.Available) mgrOutcome).entries()).isEmpty();
    }

    @Test
    @DisplayName("Active Manager is authorized from CurrentAccountActor without Workshop location lookup")
    void managerAuthorizedGloballyFromCurrentAccountActor() {
        String mgrUid = "uid-mgr-global-" + UUID.randomUUID();
        Account manager = accountRepository.save(Account.builder()
                .firebaseUid(mgrUid)
                .loginEmail("mgr-global@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .build());

        locationAssignmentRepository.save(AccountLocationAssignment.builder()
                .accountId(manager.getId())
                .locationId(101L)
                .active(true)
                .build());

        Optional<CurrentAccountActor> actorOpt = accountFacade.resolveActor(mgrUid, true);
        assertThat(actorOpt).isPresent();
        CurrentAccountActor actor = actorOpt.get();
        assertThat(actor.accountId()).isEqualTo(manager.getId());
        assertThat(actor.role()).isEqualTo(ActorRole.MANAGER);
        assertThat(actor.status()).isEqualTo(ActorStatus.ACTIVE);
        assertThat(actor.emailVerified()).isTrue();
        // Manager assignments are organizational; operational authorization in FE-003 is global
        assertThat(actor.role()).isEqualTo(ActorRole.MANAGER);
    }

    @Test
    @DisplayName("FR-045: listActiveManagerEmails returns only ACTIVE Manager emails sorted and de-duplicated")
    void listActiveManagerEmails_ReturnsOnlyActiveManagers_Sorted() {
        // Active Managers
        accountRepository.save(Account.builder()
                .firebaseUid("uid-mgr-b")
                .loginEmail("manager.b@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .build());

        accountRepository.save(Account.builder()
                .firebaseUid("uid-mgr-a")
                .loginEmail("manager.a@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.ACTIVE)
                .build());

        // Inactive Managers
        accountRepository.save(Account.builder()
                .firebaseUid("uid-mgr-suspended")
                .loginEmail("manager.suspended@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.SUSPENDED)
                .build());

        accountRepository.save(Account.builder()
                .firebaseUid("uid-mgr-pending")
                .loginEmail("manager.pending@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.PENDING_SETUP)
                .build());

        // Non-Manager roles (ACTIVE)
        accountRepository.save(Account.builder()
                .firebaseUid("uid-admin")
                .loginEmail("admin@mland.vn")
                .role(AccountRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());

        accountRepository.save(Account.builder()
                .firebaseUid("uid-staff")
                .loginEmail("staff@mland.vn")
                .role(AccountRole.STAFF)
                .status(AccountStatus.ACTIVE)
                .build());

        accountRepository.save(Account.builder()
                .firebaseUid("uid-member")
                .loginEmail("member@mland.vn")
                .role(AccountRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build());

        List<String> emails = accountFacade.listActiveManagerEmails();

        assertThat(emails).isNotNull();
        assertThat(emails).containsExactly("manager.a@mland.vn", "manager.b@mland.vn");
    }

    @Test
    @DisplayName("FR-045: listActiveManagerEmails returns empty list when no ACTIVE Managers exist")
    void listActiveManagerEmails_WhenNoActiveManagers_ReturnsEmptyList() {
        accountRepository.save(Account.builder()
                .firebaseUid("uid-admin-only")
                .loginEmail("admin@mland.vn")
                .role(AccountRole.ADMIN)
                .status(AccountStatus.ACTIVE)
                .build());

        accountRepository.save(Account.builder()
                .firebaseUid("uid-staff-only")
                .loginEmail("staff@mland.vn")
                .role(AccountRole.STAFF)
                .status(AccountStatus.ACTIVE)
                .build());

        accountRepository.save(Account.builder()
                .firebaseUid("uid-suspended-mgr")
                .loginEmail("manager.suspended@mland.vn")
                .role(AccountRole.MANAGER)
                .status(AccountStatus.SUSPENDED)
                .build());

        List<String> emails = accountFacade.listActiveManagerEmails();

        assertThat(emails).isNotNull();
        assertThat(emails).isEmpty();
    }
}