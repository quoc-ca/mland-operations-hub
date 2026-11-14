package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.WorkshopLocationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Binds product-management selling locations to active Workshop Operations locations. */
@Component
public class WorkshopSellingLocationAdapter implements SellingLocationPort {
    private final WorkshopLocationRepository locations;

    public WorkshopSellingLocationAdapter(WorkshopLocationRepository locations) {
        this.locations = locations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellingLocation> activeSellingLocations() {
        return locations.findByActiveTrue().stream()
                .sorted(Comparator.comparing(edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopLocation::getId))
                .map(location -> new SellingLocation(location.getId(), location.getDisplayName(), location.getAddressSnapshot()))
                .toList();
    }
}
