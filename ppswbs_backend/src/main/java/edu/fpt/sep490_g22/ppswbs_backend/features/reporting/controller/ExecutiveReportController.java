package edu.fpt.sep490_g22.ppswbs_backend.features.reporting.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.dto.ExecutiveReportDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.reporting.service.LocalExecutiveReportService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@RestController
@Profile("local")
@RequestMapping("/api/v1/executive-reports")
public class ExecutiveReportController {
    private final LocalExecutiveReportService service;
    public ExecutiveReportController(LocalExecutiveReportService service) { this.service = service; }
    @GetMapping
    public ExecutiveReportDtos.ExecutiveReportResponse getReport(@RequestParam(required = false) String periodType,
                                                                   @RequestParam(required = false) LocalDate anchorDate,
                                                                   @RequestParam(name = "locationId", required = false) List<Long> locationIds) {
        return service.report(periodType, anchorDate, locationIds);
    }
}
