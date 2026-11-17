package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A reason why a preview leaves out a benefit (FR-013, T081): a stable code plus its parameters (decimal strings), so
 * the frontend can show it in the reader's language. {@link #message()} is the English text of the same reason.
 */
public record BenefitReason(BenefitReasonCode code, Map<String, String> params) {

    public BenefitReason {
        if (code == null) {
            throw new IllegalArgumentException("code is required");
        }
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static BenefitReason of(BenefitReasonCode code) {
        return new BenefitReason(code, Map.of());
    }

    public static BenefitReason of(BenefitReasonCode code, String name, BigInteger value) {
        return new BenefitReason(code, Map.of(name, value.toString()));
    }

    public static BenefitReason of(BenefitReasonCode code, String name1, BigInteger value1, String name2, BigInteger value2) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(name1, value1.toString());
        params.put(name2, value2.toString());
        return new BenefitReason(code, params);
    }

    /** English text with the parameters filled in. */
    public String message() {
        String text = code.template();
        for (Map.Entry<String, String> param : params.entrySet()) {
            text = text.replace("{" + param.getKey() + "}", param.getValue());
        }
        return text;
    }
}
