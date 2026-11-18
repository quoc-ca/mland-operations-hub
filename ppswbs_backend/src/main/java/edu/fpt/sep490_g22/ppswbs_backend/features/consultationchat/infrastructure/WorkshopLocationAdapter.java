package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.WorkshopLocationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopLocationSnapshot;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade.WorkshopOperationsFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WorkshopLocationAdapter implements WorkshopLocationPort {

    private final WorkshopOperationsFacade workshopOperationsFacade;

    @Override
    public Optional<LocationInfo> findLocationByCode(String code) {
        return workshopOperationsFacade.findLocationByCode(code).map(this::toInfo);
    }

    @Override
    public Optional<LocationInfo> findLocationById(Long id) {
        return workshopOperationsFacade.findLocationById(id).map(this::toInfo);
    }

    @Override
    public List<LocationInfo> listActiveLocations() {
        return workshopOperationsFacade.listActiveLocations().stream().map(this::toInfo).toList();
    }

    private LocationInfo toInfo(WorkshopLocationSnapshot snap) {
        return new LocationInfo(snap.id(), snap.code(), snap.displayName(), snap.active());
    }
}
