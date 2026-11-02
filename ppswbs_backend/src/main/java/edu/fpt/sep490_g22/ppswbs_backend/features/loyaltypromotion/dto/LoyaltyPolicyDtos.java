package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.LoyaltyPolicy;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.PolicyTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyPolicyService.PolicyUpdate;

import java.util.Iterator;
import java.util.Set;

/**
 * Strict DTOs of the points policy API (T033, OpenAPI {@code Policy} and {@code PolicyUpdate}). Values are decimal
 * strings; rates and the minimum are positive whole numbers; the cap is a percentage greater than 0 and at most
 * 100 with up to six decimals. The rounding mode is fixed (FLOOR) and cannot be sent.
 */
public final class LoyaltyPolicyDtos {

    private static final Set<String> UPDATE_FIELDS = Set.of("expectedVersion", "earnUnitAmount", "redeemPointValue",
            "minRedeemPoints", "maxRedeemPercent");

    private LoyaltyPolicyDtos() {
    }

    public record Policy(String id, String version, String earnUnitAmount, String redeemPointValue,
                         String minRedeemPoints, String maxRedeemPercent, String roundingMode, String createdAt) {

        public static Policy of(LoyaltyPolicy policy) {
            return new Policy(policy.getId().toString(), Long.toString(policy.getRevisionNo()),
                    policy.getEarnUnitAmount().toString(), policy.getRedeemPointValue().toString(),
                    policy.getMinRedeemPoints().toString(), LoyaltyScalarCodec.format(policy.getMaxRedeemPercent()),
                    policy.getRoundingMode(), policy.getCreatedAt().toString());
        }
    }

    public static PolicyUpdate parseUpdate(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw LoyaltyException.invalidField("body", "A JSON object is required.");
        }
        Iterator<String> names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!UPDATE_FIELDS.contains(name)) {
                throw LoyaltyException.invalidField(name, "This field is not accepted.");
            }
        }
        long expectedVersion = LoyaltyScalarCodec.parseVersion("expectedVersion", text(body, "expectedVersion"));
        PolicyTerms terms = new PolicyTerms(
                LoyaltyScalarCodec.parsePositiveAmount("earnUnitAmount", text(body, "earnUnitAmount")),
                LoyaltyScalarCodec.parsePositiveAmount("redeemPointValue", text(body, "redeemPointValue")),
                LoyaltyScalarCodec.parsePositiveAmount("minRedeemPoints", text(body, "minRedeemPoints")),
                LoyaltyScalarCodec.parsePercent("maxRedeemPercent", text(body, "maxRedeemPercent")));
        return new PolicyUpdate(expectedVersion, terms);
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            throw LoyaltyException.invalidField(field, "A string value is required.");
        }
        return node.asText();
    }
}
