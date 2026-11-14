package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.WorkshopSellingLocationAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkshopSellingLocationAdapterTest {
    @Test
    void returnsOnlyActiveLocationsAsStableImmutableFacts() {
        WorkshopLocationRepository repository = mock(WorkshopLocationRepository.class);
        WorkshopLocation active = WorkshopLocation.builder().id(1L).displayName("Main")
                .addressSnapshot("12 Main Street").active(true).build();
        when(repository.findByActiveTrue()).thenReturn(List.of(active));

        var result = new WorkshopSellingLocationAdapter(repository).activeSellingLocations();

        assertThat(result).containsExactly(new edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation(
                1L, "Main", "12 Main Street"));
        assertThat(result).noneMatch(location -> "Closed".equals(location.displayName()));
    }
}
