package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.DaySummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopDaySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** navigation shell and dashboard (014 R-1): {@code GET /api/v1/workshop-day-summaries?date=yyyy-MM-dd[&locationId=]}. Manager only. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkshopDaySummaryController {

    private final WorkshopDaySummaryService service;

    @GetMapping("/workshop-day-summaries")
    public DaySummaryResponse daySummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "locationId", required = false) Long locationId) {
        return service.summarize(authHeader, date, locationId);
    }
}
