package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOn;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;

public record AddOnResponse(String addOnReference, Long packageId, Long locationId,
                             String description, Long priceVnd, String currencyCode,
                             WorkshopAddOnState state) {
    public static AddOnResponse from(WorkshopAddOn addOn) {
        return new AddOnResponse(addOn.getReference(), addOn.getPackageId(), addOn.getLocationId(),
                addOn.getDescription(), addOn.getPriceVnd(), "VND", addOn.getState());
    }
}
