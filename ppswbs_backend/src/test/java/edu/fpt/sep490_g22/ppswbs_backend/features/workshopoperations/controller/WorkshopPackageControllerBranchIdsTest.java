package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto.PackageResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.OperationsRestIdempotencyService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageAuthorizationService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageDiscoveryService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BUG-INT-0001: public package list and detail must carry the branches where the package is sellable. */
class WorkshopPackageControllerBranchIdsTest {

    private final WorkshopPackageService packageService = mock(WorkshopPackageService.class);
    private final WorkshopPackageDiscoveryService discoveryService = mock(WorkshopPackageDiscoveryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new WorkshopPackageController(packageService, discoveryService,
                mock(WorkshopPackageAuthorizationService.class), mock(OperationsRestIdempotencyService.class))).build();
        when(discoveryService.sellableLocationIds(3L)).thenReturn(List.of(1L));
        when(discoveryService.sellableLocationIds(5L)).thenReturn(List.of(1L, 2L));
    }

    @Test
    void listCarriesBranchIdsPerPackage() throws Exception {
        when(packageService.listPublished()).thenReturn(List.of(pkg(3L), pkg(5L)));

        mvc.perform(get("/api/v1/workshop-packages").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branchIds[0]").value(1))
                .andExpect(jsonPath("$[0].branchIds.length()").value(1))
                .andExpect(jsonPath("$[1].branchIds.length()").value(2));
    }

    @Test
    void detailCarriesBranchIds() throws Exception {
        when(packageService.getPublished(3L)).thenReturn(pkg(3L));

        mvc.perform(get("/api/v1/workshop-packages/3").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchIds[0]").value(1))
                .andExpect(jsonPath("$.branchIds.length()").value(1));
    }

    private static PackageResponse pkg(Long id) {
        return PackageResponse.builder().packageId(id).name("Package " + id).build();
    }
}
