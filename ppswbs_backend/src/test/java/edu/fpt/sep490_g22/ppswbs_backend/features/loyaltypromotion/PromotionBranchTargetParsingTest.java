package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto.PromotionWriteDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyErrorCode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T083 (Session 2026-11-09): a product voucher takes optional {@code branchIds}; an automatic discount does not. */
class PromotionBranchTargetParsingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode body(String kind, String targets) throws Exception {
        String voucherFields = kind.equals("VOUCHER")
                ? "\"code\":\"MLABCDEFGH\",\"totalLimit\":\"10\",\"perMemberLimit\":\"1\",\"allowPoints\":true,"
                : "";
        return JSON.readTree("{\"kind\":\"" + kind + "\",\"scope\":\"PRODUCT\",\"name\":\"Ưu đãi Hoàn Kiếm\","
                + "\"startAt\":\"2026-11-10T00:00:00Z\",\"endAt\":\"2026-11-30T00:00:00Z\","
                + "\"discount\":{\"kind\":\"PERCENT\",\"percentage\":\"10\",\"maxDiscountAmount\":\"100000\"},"
                + "\"minimumEligibleAmount\":\"0\"," + voucherFields + "\"targets\":" + targets + "}");
    }

    @Test
    void aVoucherKeepsItsBranchesAsBranchTargets() throws Exception {
        PromotionWriteDtos.CreateProgram request = PromotionWriteDtos.parseCreate(
                body("VOUCHER", "{\"productIds\":[\"1\"],\"branchIds\":[\"7\",\"9\"]}"));
        assertThat(request.targets()).containsExactly(new TargetRef(TargetDimension.PRODUCT, "1"),
                new TargetRef(TargetDimension.BRANCH, "7"), new TargetRef(TargetDimension.BRANCH, "9"));
    }

    @Test
    void aVoucherWithoutBranchesAppliesEverywhere() throws Exception {
        PromotionWriteDtos.CreateProgram request = PromotionWriteDtos.parseCreate(
                body("VOUCHER", "{\"productIds\":[\"1\"]}"));
        assertThat(request.targets()).noneMatch(target -> target.dimension() == TargetDimension.BRANCH);
    }

    @Test
    void anAutomaticDiscountWithBranchesOrBranchesAloneAreRefused() {
        assertThatThrownBy(() -> PromotionWriteDtos.parseCreate(
                body("AUTO_DISCOUNT", "{\"productIds\":[\"1\"],\"branchIds\":[\"7\"]}")))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> PromotionWriteDtos.parseCreate(body("VOUCHER", "{\"branchIds\":[\"7\"]}")))
                .isInstanceOfSatisfying(LoyaltyException.class,
                        e -> assertThat(e.code()).isEqualTo(LoyaltyErrorCode.INVALID_INPUT));
    }
}
