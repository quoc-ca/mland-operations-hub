package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.api;

import edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.application.OtaReconciliationWorkItemView;

import java.util.List;

public record OtaReconciliationWorkItemListResponse(List<OtaReconciliationWorkItemView> items) {
    public OtaReconciliationWorkItemListResponse {
        items = List.copyOf(items);
    }
}
