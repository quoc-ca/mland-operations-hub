package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade;

public record AccountId(Long value) {
    public AccountId {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("AccountId must be a positive integer");
        }
    }

    public static AccountId of(Long value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

