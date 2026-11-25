package edu.fpt.sep490_g22.ppswbs_backend.features.reporting;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorRole;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller.ExecutiveReportController;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller.ExecutiveTopSellerController;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller.ExecutiveTrendController;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller.ReportingExceptionHandler;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveReportService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveTopSellerService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveTrendService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ManagerReportingAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ReportingPeriodResolver;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.RetailBranchRevenue;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.SessionCapacity;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.WorkshopBranchRevenue;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingSourcePort.WorkshopRevenue;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTopSellerPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.source.ReportingTrendPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FE-10 API integration (T033, T042; quickstart QS-01..QS-06, QS-09, QS-11..QS-15): the real controllers, Manager
 * access check, period resolver, report service and safe error handler over HTTP, with only the read-only source
 * ports replaced. SQL against the real schema is covered by {@link ExecutiveReportReadOnlyMySqlTest}.
 */
class ExecutiveReportApiTest {
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-11-14T03:00:00Z"), ZoneOffset.UTC);
    private static final String REPORT = "/api/v1/executive-reports";

    private final ReportingSourcePort sources = mock(ReportingSourcePort.class);
    private final ReportingTrendPort trends = mock(ReportingTrendPort.class);
    private final ReportingTopSellerPort topSellers = mock(ReportingTopSellerPort.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var periods = new ReportingPeriodResolver(NOW);
        var access = new ManagerReportingAccess();
        mvc = MockMvcBuilders.standaloneSetup(
                new ExecutiveReportController(new ExecutiveReportService(sources, periods), access),
                new ExecutiveTrendController(new ExecutiveTrendService(trends, periods), access),
                new ExecutiveTopSellerController(new ExecutiveTopSellerService(topSellers, periods), access))
            .setControllerAdvice(new ReportingExceptionHandler())
            .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
            .build();
        when(sources.branchNames(anyCollection())).thenReturn(Map.of(1L, "MLAND Hoan Kiem", 2L, "MLAND Cau Giay", 3L, "MLAND Tay Ho"));
    }

    private static CurrentAccountActor actor(ActorRole role, Long... locations) {
        return new CurrentAccountActor(7L, role, ActorStatus.ACTIVE, true, List.of(locations));
    }

    private static MockHttpServletRequestBuilder as(CurrentAccountActor actor, String path) {
        return get(path).requestAttr("currentAccountActor", actor);
    }

    private void sourcesReturn(List<RetailBranchRevenue> retail, WorkshopRevenue workshop, List<SessionCapacity> sessions) {
        when(sources.retailRevenue(anyCollection(), any(), any())).thenReturn(retail);
        when(sources.workshopRevenue(anyCollection(), any(), any())).thenReturn(workshop);
        when(sources.completedSessions(anyCollection(), any(), any())).thenReturn(sessions);
    }

    private void qs06Facts() {
        sourcesReturn(
            List.of(new RetailBranchRevenue(1, "VND", BigInteger.valueOf(120_000_000), 80)),
            new WorkshopRevenue(List.of(new WorkshopBranchRevenue(1, BigInteger.valueOf(45_000_000), 3)), 0, 0),
            List.of(new SessionCapacity(1, 2, 100, 60)));
    }

    @Test
    void qs01_managerWithoutFiltersGetsTheCurrentVietnamMonthUpToNowForAllAssignedBranches() throws Exception {
        qs06Facts();
        mvc.perform(as(actor(ActorRole.MANAGER, 2L, 1L), REPORT))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.scope.periodType").value("MONTH"))
            .andExpect(jsonPath("$.scope.startAt").value("2026-10-31T17:00:00Z"))
            .andExpect(jsonPath("$.scope.endAt").value("2026-11-14T03:00:00Z"))
            .andExpect(jsonPath("$.scope.timezone").value("Asia/Ho_Chi_Minh"))
            .andExpect(jsonPath("$.scope.locationIds[0]").value(1))
            .andExpect(jsonPath("$.scope.locationIds[1]").value(2))
            .andExpect(jsonPath("$.currency").value("VND"))
            .andExpect(jsonPath("$.developmentDemo").value(false));
        verify(sources).retailRevenue(eq(List.of(1L, 2L)), eq(Instant.parse("2026-10-31T17:00:00Z")),
            eq(Instant.parse("2026-11-14T03:00:00Z")));
    }

    @Test
    void qs02_unauthenticatedRequestIs401WithoutTouchingSources() throws Exception {
        mvc.perform(get(REPORT).header("X-Correlation-Id", "corr-401"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.correlationId").value("corr-401"))
            .andExpect(jsonPath("$.totals").doesNotExist());
        verifyNoInteractions(sources);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"MEMBER", "STAFF", "ADMIN"})
    void qs02_qs03_memberStaffAndAdminAreForbidden(ActorRole role) throws Exception {
        mvc.perform(as(actor(role, 1L), REPORT))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MANAGER_REPORTING_FORBIDDEN"))
            .andExpect(jsonPath("$.totals").doesNotExist());
        verifyNoInteractions(sources);
    }

    @Test
    void inactiveManagerOrManagerWithoutBranchIsForbidden() throws Exception {
        var suspended = new CurrentAccountActor(7L, ActorRole.MANAGER, ActorStatus.SUSPENDED, true, List.of(1L));
        mvc.perform(as(suspended, REPORT)).andExpect(status().isForbidden());
        mvc.perform(as(actor(ActorRole.MANAGER), REPORT)).andExpect(status().isForbidden());
        verifyNoInteractions(sources);
    }

    @Test
    void qs04_branchOutsideTheAssignmentRejectsTheWholeRequest() throws Exception {
        mvc.perform(as(actor(ActorRole.MANAGER, 1L, 2L), REPORT).param("locationId", "1", "3"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("MANAGER_REPORTING_FORBIDDEN"));
        verifyNoInteractions(sources);
    }

    @Test
    void qs05_authorizedSubsetIsExactlyTheRequestedBranches() throws Exception {
        sourcesReturn(
            List.of(new RetailBranchRevenue(2, "VND", BigInteger.valueOf(761_634), 1)),
            new WorkshopRevenue(List.of(), 0, 0),
            List.of());
        mvc.perform(as(actor(ActorRole.MANAGER, 1L, 2L, 3L), REPORT).param("locationId", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope.locationIds.length()").value(1))
            .andExpect(jsonPath("$.branches.length()").value(1))
            .andExpect(jsonPath("$.branches[0].locationId").value(2))
            .andExpect(jsonPath("$.branches[0].branchName").value("MLAND Cau Giay"))
            .andExpect(jsonPath("$.totals.retailRevenue").value("761634"));
        verify(sources).retailRevenue(eq(List.of(2L)), any(), any());
    }

    @Test
    void duplicateBranchFilterIsAnInvalidFilter() throws Exception {
        mvc.perform(as(actor(ActorRole.MANAGER, 1L, 2L), REPORT).param("locationId", "1", "1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REPORTING_FILTER_INVALID"));
    }

    @Test
    void qs06_totalRevenueAndUtilizationFromSourceFacts() throws Exception {
        qs06Facts();
        mvc.perform(as(actor(ActorRole.MANAGER, 1L), REPORT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETE"))
            .andExpect(jsonPath("$.totals.retailRevenue").value("120000000"))
            .andExpect(jsonPath("$.totals.workshopRevenue").value("45000000"))
            .andExpect(jsonPath("$.totals.totalRevenue").value("165000000"))
            .andExpect(jsonPath("$.totals.paidRetailOrderCount").value(80))
            .andExpect(jsonPath("$.totals.checkedInParticipants").value(60))
            .andExpect(jsonPath("$.totals.completedSessionCapacity").value(100))
            .andExpect(jsonPath("$.totals.workshopUtilizationPercent").value(60.0));
    }

    @Test
    void qs09_utilizationIsConsolidatedNotAnAverageOfPercentages() throws Exception {
        sourcesReturn(List.of(), new WorkshopRevenue(List.of(), 0, 0),
            List.of(new SessionCapacity(1, 1, 10, 8), new SessionCapacity(2, 1, 40, 20)));
        mvc.perform(as(actor(ActorRole.MANAGER, 1L, 2L), REPORT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.checkedInParticipants").value(28))
            .andExpect(jsonPath("$.totals.completedSessionCapacity").value(50))
            .andExpect(jsonPath("$.totals.workshopUtilizationPercent").value(56.0));
    }

    @Test
    void qs10_pastPeriodTypesResolveToHalfOpenVietnamRanges() throws Exception {
        qs06Facts();
        var manager = actor(ActorRole.MANAGER, 1L);
        mvc.perform(as(manager, REPORT).param("periodType", "DAY").param("anchorDate", "2026-10-15"))
            .andExpect(jsonPath("$.scope.startAt").value("2026-10-14T17:00:00Z"))
            .andExpect(jsonPath("$.scope.endAt").value("2026-10-15T17:00:00Z"));
        mvc.perform(as(manager, REPORT).param("periodType", "WEEK").param("anchorDate", "2026-10-15"))
            .andExpect(jsonPath("$.scope.startAt").value("2026-10-11T17:00:00Z"))
            .andExpect(jsonPath("$.scope.endAt").value("2026-10-18T17:00:00Z"));
        mvc.perform(as(manager, REPORT).param("periodType", "YEAR").param("anchorDate", "2025-06-01"))
            .andExpect(jsonPath("$.scope.startAt").value("2024-12-31T17:00:00Z"))
            .andExpect(jsonPath("$.scope.endAt").value("2025-12-31T17:00:00Z"));
    }

    @Test
    void invalidPeriodOrDateIs400WithASafeBody() throws Exception {
        var manager = actor(ActorRole.MANAGER, 1L);
        mvc.perform(as(manager, REPORT).param("periodType", "DECADE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REPORTING_FILTER_INVALID"))
            .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(as(manager, REPORT).param("anchorDate", "not-a-date"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("REPORTING_FILTER_INVALID"));
        verifyNoInteractions(sources);
    }

    @Test
    void qs11_noQualifyingFactsIsEmptyWithKnownZeros() throws Exception {
        sourcesReturn(List.of(), new WorkshopRevenue(List.of(), 0, 0), List.of());
        mvc.perform(as(actor(ActorRole.MANAGER, 1L), REPORT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EMPTY"))
            .andExpect(jsonPath("$.totals.retailRevenue").value("0"))
            .andExpect(jsonPath("$.totals.workshopRevenue").value("0"))
            .andExpect(jsonPath("$.totals.totalRevenue").value("0"))
            .andExpect(jsonPath("$.totals.paidRetailOrderCount").value(0))
            .andExpect(jsonPath("$.totals.workshopUtilizationPercent").value(nullValue()));
    }

    @Test
    void qs12_attendanceUnavailableKeepsRevenueAndNullsUtilization() throws Exception {
        qs06Facts();
        when(sources.completedSessions(anyCollection(), any(), any())).thenThrow(new DataAccessResourceFailureException("down"));
        mvc.perform(as(actor(ActorRole.MANAGER, 1L), REPORT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIAL"))
            .andExpect(jsonPath("$.totals.totalRevenue").value("165000000"))
            .andExpect(jsonPath("$.totals.checkedInParticipants").value(nullValue()))
            .andExpect(jsonPath("$.totals.workshopUtilizationPercent").value(nullValue()))
            .andExpect(jsonPath("$.quality[?(@.source=='ATTENDANCE')].status").value("UNAVAILABLE"));
    }

    @Test
    void qs13_unavailableRevenueSourceIsNullNotZero() throws Exception {
        qs06Facts();
        when(sources.workshopRevenue(anyCollection(), any(), any())).thenThrow(new DataAccessResourceFailureException("down"));
        mvc.perform(as(actor(ActorRole.MANAGER, 1L), REPORT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIAL"))
            .andExpect(jsonPath("$.totals.retailRevenue").value("120000000"))
            .andExpect(jsonPath("$.totals.workshopRevenue").value(nullValue()))
            .andExpect(jsonPath("$.totals.totalRevenue").value(nullValue()))
            .andExpect(jsonPath("$.quality[?(@.source=='WORKSHOP_REVENUE')].safeReasonCode").value("SOURCE_UNAVAILABLE"));
    }

    @Test
    void qs14_allSourcesUnavailableIs503WithoutInternalDetails() throws Exception {
        var failure = new DataAccessResourceFailureException("SELECT secret FROM retail_orders");
        when(sources.retailRevenue(anyCollection(), any(), any())).thenThrow(failure);
        when(sources.workshopRevenue(anyCollection(), any(), any())).thenThrow(failure);
        when(sources.completedSessions(anyCollection(), any(), any())).thenThrow(failure);
        mvc.perform(as(actor(ActorRole.MANAGER, 1L), REPORT).header("X-Correlation-Id", "corr-503"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("REPORTING_SOURCES_UNAVAILABLE"))
            .andExpect(jsonPath("$.correlationId").value("corr-503"))
            .andExpect(jsonPath("$.message", not(containsString("SELECT"))))
            .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void qs15_nonVndFactIsNotSummedAndMarksTheSourcePartial() throws Exception {
        sourcesReturn(
            List.of(new RetailBranchRevenue(1, "VND", BigInteger.valueOf(1_000_000), 2),
                new RetailBranchRevenue(1, "USD", BigInteger.valueOf(50), 1)),
            new WorkshopRevenue(List.of(new WorkshopBranchRevenue(1, BigInteger.valueOf(2_000_000), 1)), 0, 0),
            List.of(new SessionCapacity(1, 1, 10, 5)));
        mvc.perform(as(actor(ActorRole.MANAGER, 1L), REPORT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIAL"))
            .andExpect(jsonPath("$.totals.retailRevenue").value("1000000"))
            .andExpect(jsonPath("$.totals.paidRetailOrderCount").value(2))
            .andExpect(jsonPath("$.quality[?(@.source=='RETAIL')].safeReasonCode").value("NON_VND_EXCLUDED"));
    }

    @Test
    void trendAndTopSellerEndpointsUseTheSameManagerAccess() throws Exception {
        for (String path : List.of(REPORT + "/trends", REPORT + "/top-sellers")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(as(actor(ActorRole.STAFF, 1L), path)).andExpect(status().isForbidden());
            mvc.perform(as(actor(ActorRole.MANAGER, 1L), path).param("locationId", "9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MANAGER_REPORTING_FORBIDDEN"));
        }
        verifyNoInteractions(trends, topSellers);
        verify(sources, never()).retailRevenue(any(Collection.class), any(), any());
    }

}
