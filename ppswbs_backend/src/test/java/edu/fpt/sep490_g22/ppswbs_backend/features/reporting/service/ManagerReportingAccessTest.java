package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagerReportingAccessTest {
    private final ManagerReportingAccess access = new ManagerReportingAccess();

    private static CurrentAccountActor actor(ActorRole role, ActorStatus status, List<Long> locations) {
        return new CurrentAccountActor(7L, role, status, true, locations);
    }

    private static String code(Runnable call) {
        try { call.run(); } catch (ReportingException e) { return e.status().value() + " " + e.code(); }
        return "allowed";
    }

    @Test void activeManagerGetsAllAssignedBranchesByDefault() {
        assertThat(access.authorize(actor(ActorRole.MANAGER, ActorStatus.ACTIVE, List.of(20L, 10L)), null))
            .containsExactly(10L, 20L);
    }

    @Test void managerMayNarrowButNeverWidenTheScope() {
        var manager = actor(ActorRole.MANAGER, ActorStatus.ACTIVE, List.of(10L, 20L));
        assertThat(access.authorize(manager, List.of(20L))).containsExactly(20L);
        assertThat(code(() -> access.authorize(manager, List.of(10L, 30L)))).isEqualTo("403 MANAGER_REPORTING_FORBIDDEN");
        assertThat(code(() -> access.authorize(manager, List.of(10L, 10L)))).isEqualTo("400 REPORTING_FILTER_INVALID");
    }

    @Test void onlyAnActiveAssignedManagerMaySeeRevenue() {
        assertThat(code(() -> access.authorize(null, null))).isEqualTo("401 AUTHENTICATION_REQUIRED");
        for (ActorRole role : List.of(ActorRole.MEMBER, ActorRole.STAFF, ActorRole.ADMIN)) {
            assertThat(code(() -> access.authorize(actor(role, ActorStatus.ACTIVE, List.of(10L)), null)))
                .as(role.name()).isEqualTo("403 MANAGER_REPORTING_FORBIDDEN");
        }
        assertThat(code(() -> access.authorize(actor(ActorRole.MANAGER, ActorStatus.SUSPENDED, List.of(10L)), null)))
            .isEqualTo("403 MANAGER_REPORTING_FORBIDDEN");
        assertThat(code(() -> access.authorize(actor(ActorRole.MANAGER, ActorStatus.ACTIVE, List.of()), null)))
            .isEqualTo("403 MANAGER_REPORTING_FORBIDDEN");
    }
}
