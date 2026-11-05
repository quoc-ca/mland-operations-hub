package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.QuoteReasonDetail;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps the English reasons returned by the Loyalty preview to a code plus a Vietnamese message. Unknown reasons keep
 * their original text under code OTHER, so a new Loyalty reason is never hidden.
 */
final class QuoteReasonTranslator {
    private static final Pattern POINTS_RANGE = Pattern.compile("Points must be between ([0-9]+) and ([0-9]+)[.]");

    private QuoteReasonTranslator() { }

    static List<QuoteReasonDetail> translate(List<String> reasons) {
        return reasons.stream().map(QuoteReasonTranslator::one).toList();
    }

    private static QuoteReasonDetail one(String reason) {
        Matcher range = POINTS_RANGE.matcher(reason);
        if (range.matches()) {
            return new QuoteReasonDetail("POINTS_OUT_OF_RANGE",
                    "Số điểm phải từ " + range.group(1) + " đến " + range.group(2) + ".");
        }
        return switch (reason) {
            case "Points cannot be used with this voucher." ->
                    new QuoteReasonDetail("POINTS_NOT_ALLOWED", "Voucher này không cho phép dùng điểm.");
            case "Workshops have no automatic discount; enter a voucher code instead." ->
                    new QuoteReasonDetail("NO_AUTO_DISCOUNT", "Workshop không có ưu đãi tự động; hãy nhập hoặc chọn mã voucher.");
            case "The voucher code was not found." ->
                    new QuoteReasonDetail("VOUCHER_NOT_FOUND", "Không tìm thấy mã voucher.");
            case "This voucher does not cover this package, branch, date or session." ->
                    new QuoteReasonDetail("VOUCHER_NOT_APPLICABLE", "Voucher không áp dụng cho gói, cơ sở, ngày hoặc ca này.");
            case "The package fee does not reach the minimum amount of this voucher." ->
                    new QuoteReasonDetail("BELOW_MIN_AMOUNT", "Giá gói chưa đạt mức tối thiểu của voucher.");
            case "The amount to pay after discounts must be greater than zero." ->
                    new QuoteReasonDetail("NET_AMOUNT_INVALID", "Số tiền cần thanh toán sau ưu đãi phải lớn hơn 0.");
            default -> new QuoteReasonDetail("OTHER", reason);
        };
    }
}
