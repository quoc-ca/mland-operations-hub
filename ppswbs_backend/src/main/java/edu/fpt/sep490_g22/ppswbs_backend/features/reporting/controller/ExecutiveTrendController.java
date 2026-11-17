package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveTrendDtos.ExecutiveTrendResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveTrendService;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ManagerReportingAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** FE-10 `GET /api/v1/executive-reports/trends`: same filters and same Manager scope rule as the KPI report. */
@RestController
@RequestMapping("/api/v1/executive-reports/trends")
public class ExecutiveTrendController {

    private final ExecutiveTrendService service;
    private final ManagerReportingAccess access;

    public ExecutiveTrendController(ExecutiveTrendService service, ManagerReportingAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ResponseEntity<ExecutiveTrendResponse> getTrend(
            HttpServletRequest request,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate anchorDate,
            @RequestParam(name = "locationId", required = false) List<Long> locationIds) {
        List<Long> scope = access.authorize(request.getAttribute(ExecutiveReportController.CURRENT_ACTOR_ATTRIBUTE), locationIds);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.trend(periodType, anchorDate, scope));
    }
}
