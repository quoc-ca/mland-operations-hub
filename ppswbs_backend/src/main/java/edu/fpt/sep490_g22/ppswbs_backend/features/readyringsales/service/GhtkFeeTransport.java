package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import java.math.BigDecimal;

interface GhtkFeeTransport {
    FeeResponse quote(FeeRequest request);

    record FeeRequest(String pickupAddress, String pickupProvince, String pickupDistrict, String pickupWard,
                      String address, String province, String district, String ward, int weightGrams,
                      BigDecimal declaredValue) { }

    record FeeResponse(boolean success, boolean deliveryAvailable, BigDecimal charge, String message) { }
}
