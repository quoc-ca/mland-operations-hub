package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.ExecutiveReportService;
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

/** FE-10 `GET /api/v1/executive-reports`: Manager-only, read-only, reloaded only on load, filter or refresh. */
@RestController
@RequestMapping("/api/v1/executive-reports")
public class ExecutiveReportController {
    static final String CURRENT_ACTOR_ATTRIBUTE = "currentAccountActor";

    private final ExecutiveReportService service;
    private final ManagerReportingAccess access;

    public ExecutiveReportController(ExecutiveReportService service, ManagerReportingAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public ResponseEntity<ExecutiveReportDtos.ExecutiveReportResponse> getReport(
            HttpServletRequest request,
            @RequestParam(required = false) String periodType,
            @RequestParam(required = false) LocalDate anchorDate,
            @RequestParam(name = "locationId", required = false) List<Long> locationIds) {
        List<Long> scope = access.authorize(request.getAttribute(CURRENT_ACTOR_ATTRIBUTE), locationIds);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.report(periodType, anchorDate, scope));
    }
}
