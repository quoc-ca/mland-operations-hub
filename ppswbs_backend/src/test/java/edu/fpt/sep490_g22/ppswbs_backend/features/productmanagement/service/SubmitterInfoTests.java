package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.ActorRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos.SubmitterBranch;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductReview;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-060/FR-061 (Session 2026-11-16): actor labels and the submitter's branch snapshot. */
class SubmitterInfoTests {

    @Nested
    class ActorNames {

        private AccountFacade accounts;
        private CatalogueActorNames names;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            accounts = mock(AccountFacade.class);
            ObjectProvider<AccountFacade> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(accounts);
            names = new CatalogueActorNames(provider);
        }

        @Test
        void accountWithFullNameShowsTheName() {
            when(accounts.findAccountFullName(12L)).thenReturn(Optional.of("  Trần Thị Staff "));
            assertThat(names.resolve(List.of("12"))).containsEntry("12", new ActorRef("ACCOUNT", "Trần Thị Staff"));
        }

        @Test
        void accountWithoutNameOrUnreadableShowsAccountNumberOnly() {
            when(accounts.findAccountFullName(12L)).thenReturn(Optional.of("   "));
            when(accounts.findAccountFullName(13L)).thenReturn(Optional.empty());
            when(accounts.findAccountFullName(14L)).thenThrow(new IllegalStateException("db down"));

            Map<String, ActorRef> labels = names.resolve(List.of("12", "13", "14"));

            assertThat(labels).containsEntry("12", new ActorRef("ACCOUNT", "Tài khoản #12"))
                    .containsEntry("13", new ActorRef("ACCOUNT", "Tài khoản #13"))
                    .containsEntry("14", new ActorRef("ACCOUNT", "Tài khoản #14"));
        }

        @Test
        void systemActorIsSystemWithoutLookup() {
            assertThat(names.resolve(List.of("system:catalogue-seed")))
                    .containsEntry("system:catalogue-seed", new ActorRef("SYSTEM", "Hệ thống"));
            verify(accounts, never()).findAccountFullName(anyLong());
        }

        @Test
        void nonNumericIdIsNotLookedUpAndNullsAreIgnored() {
            Map<String, ActorRef> labels = names.resolve(Arrays.asList("staff-1", null));
            assertThat(labels).containsOnlyKeys("staff-1");
            assertThat(labels.get("staff-1").displayName()).isEqualTo("Tài khoản #staff-1");
            verify(accounts, never()).findAccountFullName(anyLong());
        }

        @Test
        void longNameIsCutAtTwoHundredCodePoints() {
            when(accounts.findAccountFullName(12L)).thenReturn(Optional.of("ấ".repeat(250)));
            assertThat(names.resolve(List.of("12")).get("12").displayName()).hasSize(200);
        }
    }

    @Nested
    class BranchSnapshot {

        private CatalogueBranchScope scope;
        private SellingLocationPort port;
        private SubmitterBranchSnapshot snapshot;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() {
            scope = mock(CatalogueBranchScope.class);
            port = mock(SellingLocationPort.class);
            ObjectProvider<SellingLocationPort> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(port);
            snapshot = new SubmitterBranchSnapshot(scope, provider);
        }

        @Test
        void managerIsEveryBranch() {
            when(scope.current()).thenReturn(new CatalogueBranchScope.Scope(true, Set.of()));
            String json = snapshot.capture("7");
            assertThat(json).isEqualTo("{\"all\":true}");
            assertThat(SubmitterBranchSnapshot.read(json, "7").kind()).isEqualTo("ALL");
        }

        @Test
        void staffKeepsAssignedBranchesWithNamesOfThatMoment() {
            when(scope.current()).thenReturn(new CatalogueBranchScope.Scope(false, Set.of(5L, 3L)));
            when(port.activeSellingLocations()).thenReturn(List.of(new SellingLocation(3L, "Hoàn Kiếm", "HN")));

            String json = snapshot.capture("12");

            var read = SubmitterBranchSnapshot.read(json, "12");
            assertThat(read.kind()).isEqualTo("LISTED");
            assertThat(read.branches()).containsExactly(new SubmitterBranch("3", "Hoàn Kiếm"),
                    new SubmitterBranch("5", null));
        }

        @Test
        void branchNameSourceDownKeepsIds() {
            when(scope.current()).thenReturn(new CatalogueBranchScope.Scope(false, Set.of(3L)));
            when(port.activeSellingLocations()).thenThrow(new IllegalStateException("down"));
            assertThat(SubmitterBranchSnapshot.read(snapshot.capture("12"), "12").branches())
                    .containsExactly(new SubmitterBranch("3", null));
        }

        @Test
        void identityFailureFailsTheSubmit() {
            when(scope.current()).thenThrow(new IllegalStateException("identity down"));
            assertThatThrownBy(() -> snapshot.capture("12")).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void systemActorRecordsNothing() {
            assertThat(snapshot.capture("system:catalogue-seed")).isNull();
            verify(scope, never()).current();
            assertThat(SubmitterBranchSnapshot.read(null, "system:catalogue-seed").kind()).isEqualTo("SYSTEM");
        }

        @Test
        void notSubmittedOldOrUnreadableRows() {
            assertThat(SubmitterBranchSnapshot.read(null, null)).isNull();
            assertThat(SubmitterBranchSnapshot.read(null, "12").kind()).isEqualTo("NOT_RECORDED");
            assertThat(SubmitterBranchSnapshot.read("not json", "12").kind()).isEqualTo("NOT_RECORDED");
        }

        @Test
        void reviewSubmitStoresTheSnapshot() {
            ProductReview review = ProductReview.draft(1L, 1, 1);
            review.submit("12", Instant.parse("2026-11-16T03:00:00Z"), "{}", "{\"all\":true}");
            assertThat(review.getSubmitterBranches()).isEqualTo("{\"all\":true}");
        }
    }
}
