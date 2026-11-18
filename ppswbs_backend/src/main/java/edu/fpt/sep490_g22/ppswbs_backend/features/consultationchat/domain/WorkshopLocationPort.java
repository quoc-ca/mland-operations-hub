package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import java.util.List;
import java.util.Optional;

public interface WorkshopLocationPort {

    record LocationInfo(Long id, String code, String displayName, boolean active) {}

    Optional<LocationInfo> findLocationByCode(String code);

    Optional<LocationInfo> findLocationById(Long id);

    List<LocationInfo> listActiveLocations();
}
