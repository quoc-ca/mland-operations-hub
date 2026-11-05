package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.facade;

public interface WorkshopOtaResyncFacade {
    WorkshopSessionOtaSnapshot resync(String sessionReference);
}
