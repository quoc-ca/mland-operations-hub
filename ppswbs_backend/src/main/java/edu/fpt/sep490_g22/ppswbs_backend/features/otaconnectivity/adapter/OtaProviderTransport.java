package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.adapter;

@FunctionalInterface
public interface OtaProviderTransport {
    OtaTransportResponse send(OtaTransportRequest request) throws Exception;
}

