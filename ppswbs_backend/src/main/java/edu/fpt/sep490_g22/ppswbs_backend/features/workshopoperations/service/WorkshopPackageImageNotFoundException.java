package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** FR-073: no readable cover image for this reader (404 WORKSHOP_PACKAGE_IMAGE_NOT_FOUND). */
public class WorkshopPackageImageNotFoundException extends RuntimeException {
    public WorkshopPackageImageNotFoundException() {
        super("Workshop Package image not found");
    }
}
