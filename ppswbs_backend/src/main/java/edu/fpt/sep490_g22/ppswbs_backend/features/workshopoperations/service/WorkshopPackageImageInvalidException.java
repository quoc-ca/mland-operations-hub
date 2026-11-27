package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service;

/** FR-073: the chosen cover image cannot be used (400 PACKAGE_IMAGE_INVALID). */
public class WorkshopPackageImageInvalidException extends RuntimeException {
    public WorkshopPackageImageInvalidException(Throwable cause) {
        super("The chosen image cannot be used. Upload it again.", cause);
    }
}
