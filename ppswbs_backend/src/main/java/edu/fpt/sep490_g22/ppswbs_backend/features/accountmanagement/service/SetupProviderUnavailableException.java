package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.service;

public class SetupProviderUnavailableException extends RuntimeException {
    public SetupProviderUnavailableException() {
        super("Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại.");
    }

    public SetupProviderUnavailableException(Throwable cause) {
        super("Dịch vụ xác thực tạm thời không khả dụng. Vui lòng thử lại.", cause);
    }
}
