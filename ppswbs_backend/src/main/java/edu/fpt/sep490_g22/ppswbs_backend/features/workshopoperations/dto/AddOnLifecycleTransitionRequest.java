package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dto;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.WorkshopAddOnState;
import jakarta.validation.constraints.NotNull;

public record AddOnLifecycleTransitionRequest(@NotNull WorkshopAddOnState targetState) { }
