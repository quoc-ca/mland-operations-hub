package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountLocationAssignmentRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountProfileRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.AccountRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao.MemberAddressRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.Account;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountLocationAssignment;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AccountProfile;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.enums.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountFacadeImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountLocationAssignmentRepository locationAssignmentRepository;

    @Mock
    private AccountProfileRepository profileRepository;

    @Mock
    private MemberAddressRepository memberAddressRepository;

    @InjectMocks
    private AccountFacadeImpl accountFacade;

    @Test
    @DisplayName("UTC-001-001: findEligibleRoster returns only active STAFF candidates and excludes active MANAGERs")
    void testFindEligibleRoster_ExcludesManagers() {
        Long workshopLocationId = 101L;

        AccountLocationAssignment staffAssignment = AccountLocationAssignment.builder()
            .id(1L)
            .accountId(1L)
            .locationId(workshopLocationId)
            .active(true)
            .build();

        AccountLocationAssignment managerAssignment = AccountLocationAssignment.builder()
            .id(2L)
            .accountId(2L)
            .locationId(workshopLocationId)
            .active(true)
            .build();

        when(locationAssignmentRepository.findByLocationIdAndActiveTrue(workshopLocationId))
            .thenReturn(List.of(staffAssignment, managerAssignment));

        Account staffAccount = Account.builder()
            .id(1L)
            .role(AccountRole.STAFF)
            .status(AccountStatus.ACTIVE)
            .loginEmail("staff@mland.vn")
            .build();

        Account managerAccount = Account.builder()
            .id(2L)
            .role(AccountRole.MANAGER)
            .status(AccountStatus.ACTIVE)
            .loginEmail("manager@mland.vn")
            .build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(staffAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(managerAccount));

        AccountProfile staffProfile = AccountProfile.builder()
            .accountId(1L)
            .fullName("Le Van Staff")
            .build();

        when(profileRepository.findByAccountId(1L)).thenReturn(Optional.of(staffProfile));

        RosterOutcome outcome = accountFacade.findEligibleRoster(workshopLocationId);

        assertThat(outcome).isNotNull();
        assertThat(outcome).isInstanceOf(RosterOutcome.Available.class);
        RosterOutcome.Available available = (RosterOutcome.Available) outcome;
        assertThat(available.entries()).hasSize(1);

        InternalRosterEntry entry = available.entries().get(0);
        assertThat(entry.accountId().value()).isEqualTo(1L);
        assertThat(entry.role()).isEqualTo(AccountRole.STAFF);
        assertThat(entry.displayName()).isEqualTo("Le Van Staff");

        verify(profileRepository, never()).findByAccountId(2L);
    }

    @Test
    @DisplayName("UTC-001-002: resolveActor sets MANAGER role independently of location scope")
    void testManagerAuthorization_IndependentOfLocationScope() {
        String firebaseUid = "firebase-manager-uid-123";
        Account managerAccount = Account.builder()
            .id(55L)
            .firebaseUid(firebaseUid)
            .role(AccountRole.MANAGER)
            .status(AccountStatus.ACTIVE)
            .loginEmail("manager55@mland.vn")
            .build();

        when(accountRepository.findByFirebaseUid(firebaseUid))
            .thenReturn(Optional.of(managerAccount));
        when(locationAssignmentRepository.findByAccountIdAndActiveTrue(55L))
            .thenReturn(List.of());

        Optional<CurrentAccountActor> actorOpt = accountFacade.resolveActor(firebaseUid, true);

        assertThat(actorOpt).isPresent();
        CurrentAccountActor actor = actorOpt.get();
        assertThat(actor.accountId()).isEqualTo(55L);
        assertThat(actor.role()).isEqualTo(ActorRole.MANAGER);
        assertThat(actor.status()).isEqualTo(ActorStatus.ACTIVE);
        assertThat(actor.activeWorkshopLocationIds()).isEmpty();
    }
}
