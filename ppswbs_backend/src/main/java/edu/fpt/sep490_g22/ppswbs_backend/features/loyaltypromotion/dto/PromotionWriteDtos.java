package edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.dto;

import com.fasterxml.jackson.databind.JsonNode;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.entity.ProgramKind;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.LoyaltyException;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetDimension;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.facade.TargetScope;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyCalculator.DiscountTerms;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues;
import edu.fpt.sep490_g22.ppswbs_backend.features.loyaltypromotion.service.LoyaltyValues.NormalizedCode;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict write DTOs of the programme API (T026, OpenAPI {@code CreateProgram}). The body is parsed field by field
 * from JSON so that the three create variants stay discriminated and nothing slips through: unknown fields, owner,
 * counter or status fields, numbers sent as JSON numbers, and wrong types are all rejected as INVALID_INPUT with the
 * field path. Money, ids and quotas are decimal strings; percentages have at most six decimals and are never
 * rounded; timestamps are ISO-8601 instants in UTC ({@code Z}).
 */
public final class PromotionWriteDtos {

    private static final Set<String> COMMON = Set.of("kind", "scope", "name", "startAt", "endAt", "discount",
            "minimumEligibleAmount", "allowPoints", "targets");
    private static final Set<String> VOUCHER_ONLY = Set.of("code", "totalLimit", "perMemberLimit");

    private PromotionWriteDtos() {
    }

    /** A validated create request. {@code code}, {@code totalLimit} and {@code perMemberLimit} are null for AUTO. */
    public record CreateProgram(ProgramKind kind, TargetScope scope, String name, Instant startAt, Instant endAt,
                                DiscountTerms terms, boolean allowPoints, List<TargetRef> targets,
                                NormalizedCode code, Long totalLimit, Long perMemberLimit) {

        public CreateProgram {
            targets = List.copyOf(targets);
        }

        /** Normalized request for the idempotency digest: same meaning gives the same digest. */
        public Map<String, Object> canonical() {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("kind", kind.name());
            view.put("scope", scope.name());
            view.put("name", name);
            view.put("startAt", startAt.toString());
            view.put("endAt", endAt.toString());
            view.put("percent", terms.percent() == null ? null : LoyaltyScalarCodec.format(terms.percent()));
            view.put("fixedAmount", terms.fixedAmount() == null ? null : terms.fixedAmount().toString());
            view.put("maxDiscount", terms.maxDiscount().toString());
            view.put("minimumEligible", terms.minimumEligible().toString());
            view.put("allowPoints", allowPoints);
            view.put("targets", targets.stream().map(t -> t.dimension() + ":" + t.targetKey()).sorted().toList());
            view.put("code", code == null ? null : code.key());
            view.put("codeOriginal", code == null ? null : code.original());
            view.put("totalLimit", totalLimit);
            view.put("perMemberLimit", perMemberLimit);
            return view;
        }
    }

    public static CreateProgram parseCreate(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw LoyaltyException.invalidField("body", "A JSON object is required.");
        }
        String kindText = text(body, "kind");
        ProgramKind kind = switch (kindText) {
            case "VOUCHER" -> ProgramKind.VOUCHER;
            case "AUTO_DISCOUNT" -> ProgramKind.AUTO_DISCOUNT;
            default -> throw LoyaltyException.invalidField("kind", "Must be VOUCHER or AUTO_DISCOUNT.");
        };
        TargetScope scope = switch (text(body, "scope")) {
            case "PRODUCT" -> TargetScope.PRODUCT;
            case "WORKSHOP" -> TargetScope.WORKSHOP;
            default -> throw LoyaltyException.invalidField("scope", "Must be PRODUCT or WORKSHOP.");
        };
        if (kind == ProgramKind.AUTO_DISCOUNT && scope != TargetScope.PRODUCT) {
            throw LoyaltyException.invalidField("scope", "An automatic discount applies to products only.");
        }
        Set<String> allowed = new LinkedHashSet<>(COMMON);
        if (kind == ProgramKind.VOUCHER) {
            allowed.addAll(VOUCHER_ONLY);
        }
        onlyFields(body, allowed, "");

        String name = LoyaltyValues.normalizeName("name", text(body, "name"));
        Instant startAt = instant(body, "startAt");
        Instant endAt = instant(body, "endAt");
        LoyaltyValues.requireInterval("startAt", startAt, endAt);
        DiscountTerms terms = discount(object(body, "discount"),
                LoyaltyScalarCodec.parseAmount("minimumEligibleAmount", text(body, "minimumEligibleAmount")));

        boolean allowPoints;
        if (kind == ProgramKind.AUTO_DISCOUNT) {
            if (body.has("allowPoints") && !bool(body, "allowPoints")) {
                throw LoyaltyException.invalidField("allowPoints", "An automatic discount always allows points.");
            }
            allowPoints = true;
        } else {
            allowPoints = bool(body, "allowPoints");
        }

        List<TargetRef> targets = scope == TargetScope.PRODUCT
                ? productTargets(object(body, "targets"))
                : workshopTargets(object(body, "targets"));

        NormalizedCode code = null;
        Long totalLimit = null;
        Long perMemberLimit = null;
        if (kind == ProgramKind.VOUCHER) {
            code = LoyaltyValues.normalizeCode("code", text(body, "code"));
            totalLimit = LoyaltyScalarCodec.parseId("totalLimit", text(body, "totalLimit"));
            perMemberLimit = LoyaltyScalarCodec.parseId("perMemberLimit", text(body, "perMemberLimit"));
        }
        return new CreateProgram(kind, scope, name, startAt, endAt, terms, allowPoints, targets, code, totalLimit,
                perMemberLimit);
    }

    /**
     * A validated edit: {@code null} means "keep the current value". Code, scope, kind and targets are not editable
     * (spec FR-006, US2.2), so they are rejected here as unknown fields.
     */
    public record UpdateProgram(long expectedVersion, String name, Instant startAt, Instant endAt,
                                DiscountTerms discount, BigInteger minimumEligibleAmount, Boolean allowPoints,
                                Long totalLimit, Long perMemberLimit) {

        public Map<String, Object> canonical(long programId) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("programId", programId);
            view.put("expectedVersion", expectedVersion);
            view.put("name", name);
            view.put("startAt", startAt == null ? null : startAt.toString());
            view.put("endAt", endAt == null ? null : endAt.toString());
            view.put("percent", discount == null || discount.percent() == null ? null
                    : LoyaltyScalarCodec.format(discount.percent()));
            view.put("fixedAmount", discount == null || discount.fixedAmount() == null ? null
                    : discount.fixedAmount().toString());
            view.put("maxDiscount", discount == null ? null : discount.maxDiscount().toString());
            view.put("minimumEligible", minimumEligibleAmount == null ? null : minimumEligibleAmount.toString());
            view.put("allowPoints", allowPoints);
            view.put("totalLimit", totalLimit);
            view.put("perMemberLimit", perMemberLimit);
            return view;
        }
    }

    private static final Set<String> UPDATE_FIELDS = Set.of("expectedVersion", "name", "startAt", "endAt", "discount",
            "minimumEligibleAmount", "allowPoints", "totalLimit", "perMemberLimit");

    public static UpdateProgram parseUpdate(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw LoyaltyException.invalidField("body", "A JSON object is required.");
        }
        if (body.has("targets")) {
            throw LoyaltyException.invalidField("targets", "The scope of a programme cannot change after creation.");
        }
        onlyFields(body, UPDATE_FIELDS, "");
        if (body.size() < 2) {
            throw LoyaltyException.invalidField("body", "At least one field to change is required.");
        }
        long expectedVersion = LoyaltyScalarCodec.parseVersion("expectedVersion", text(body, "expectedVersion"));
        String name = body.has("name") ? LoyaltyValues.normalizeName("name", text(body, "name")) : null;
        Instant startAt = body.has("startAt") ? instant(body, "startAt") : null;
        Instant endAt = body.has("endAt") ? instant(body, "endAt") : null;
        BigInteger minimum = body.has("minimumEligibleAmount")
                ? LoyaltyScalarCodec.parseAmount("minimumEligibleAmount", text(body, "minimumEligibleAmount")) : null;
        DiscountTerms discount = body.has("discount") ? discount(object(body, "discount"), BigInteger.ZERO) : null;
        Boolean allowPoints = body.has("allowPoints") ? bool(body, "allowPoints") : null;
        Long totalLimit = body.has("totalLimit") ? LoyaltyScalarCodec.parseId("totalLimit", text(body, "totalLimit")) : null;
        Long perMemberLimit = body.has("perMemberLimit")
                ? LoyaltyScalarCodec.parseId("perMemberLimit", text(body, "perMemberLimit")) : null;
        return new UpdateProgram(expectedVersion, name, startAt, endAt, discount, minimum, allowPoints, totalLimit,
                perMemberLimit);
    }

    /** Body of pause and resume: only the expected version. */
    public static long parseExpectedVersion(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw LoyaltyException.invalidField("body", "A JSON object is required.");
        }
        onlyFields(body, Set.of("expectedVersion"), "");
        return LoyaltyScalarCodec.parseVersion("expectedVersion", text(body, "expectedVersion"));
    }

    private static DiscountTerms discount(JsonNode discount, BigInteger minimum) {
        String kind = text(discount, "kind", "discount.kind");
        return switch (kind) {
            case "PERCENT" -> {
                onlyFields(discount, Set.of("kind", "percentage", "maxDiscountAmount"), "discount.");
                yield DiscountTerms.percent(
                        LoyaltyScalarCodec.parsePercent("discount.percentage",
                                text(discount, "percentage", "discount.percentage")),
                        LoyaltyScalarCodec.parsePositiveAmount("discount.maxDiscountAmount",
                                text(discount, "maxDiscountAmount", "discount.maxDiscountAmount")),
                        minimum);
            }
            case "FIXED" -> {
                onlyFields(discount, Set.of("kind", "amount", "maxDiscountAmount"), "discount.");
                yield DiscountTerms.fixed(
                        LoyaltyScalarCodec.parsePositiveAmount("discount.amount",
                                text(discount, "amount", "discount.amount")),
                        LoyaltyScalarCodec.parsePositiveAmount("discount.maxDiscountAmount",
                                text(discount, "maxDiscountAmount", "discount.maxDiscountAmount")),
                        minimum);
            }
            default -> throw LoyaltyException.invalidField("discount.kind", "Must be PERCENT or FIXED.");
        };
    }

    private static List<TargetRef> productTargets(JsonNode targets) {
        onlyFields(targets, Set.of("productIds", "variantIds"), "targets.");
        List<TargetRef> refs = new ArrayList<>();
        refs.addAll(ids(targets, "productIds", TargetDimension.PRODUCT));
        refs.addAll(ids(targets, "variantIds", TargetDimension.VARIANT));
        if (refs.isEmpty()) {
            throw LoyaltyException.invalidField("targets", "At least one product or size is required.");
        }
        return refs;
    }

    private static List<TargetRef> workshopTargets(JsonNode targets) {
        onlyFields(targets, Set.of("packageIds", "branchIds", "workshopDates", "sessionIds"), "targets.");
        if (!targets.has("packageIds")) {
            throw LoyaltyException.invalidField("targets.packageIds", "At least one package is required.");
        }
        List<TargetRef> refs = new ArrayList<>();
        refs.addAll(ids(targets, "packageIds", TargetDimension.PACKAGE));
        refs.addAll(ids(targets, "branchIds", TargetDimension.BRANCH));
        refs.addAll(ids(targets, "sessionIds", TargetDimension.SESSION));
        for (String date : uniqueStrings(targets, "workshopDates")) {
            try {
                LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                throw LoyaltyException.invalidField("targets.workshopDates", "Dates use the YYYY-MM-DD format.");
            }
            refs.add(new TargetRef(TargetDimension.WORKSHOP_DATE, date));
        }
        return refs;
    }

    private static List<TargetRef> ids(JsonNode targets, String field, TargetDimension dimension) {
        List<TargetRef> refs = new ArrayList<>();
        for (String raw : uniqueStrings(targets, field)) {
            refs.add(new TargetRef(dimension, Long.toString(LoyaltyScalarCodec.parseId("targets." + field, raw))));
        }
        return refs;
    }

    /** Absent gives an empty list; present must be a non-empty array of distinct strings. */
    private static List<String> uniqueStrings(JsonNode parent, String field) {
        if (!parent.has(field)) {
            return List.of();
        }
        JsonNode array = parent.get(field);
        String path = "targets." + field;
        if (!array.isArray() || array.isEmpty()) {
            throw LoyaltyException.invalidField(path, "Must be a non-empty array.");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual() || !seen.add(item.asText())) {
                throw LoyaltyException.invalidField(path, "Items must be distinct strings.");
            }
        }
        return List.copyOf(seen);
    }

    private static void onlyFields(JsonNode node, Set<String> allowed, String prefix) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw LoyaltyException.invalidField(prefix + name, "This field is not accepted.");
            }
        }
    }

    private static JsonNode object(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) {
            throw LoyaltyException.invalidField(field, "An object is required.");
        }
        return node;
    }

    private static String text(JsonNode parent, String field) {
        return text(parent, field, field);
    }

    private static String text(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw LoyaltyException.invalidField(path, "A string value is required.");
        }
        return node.asText();
    }

    private static boolean bool(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isBoolean()) {
            throw LoyaltyException.invalidField(field, "A boolean value is required.");
        }
        return node.asBoolean();
    }

    private static Instant instant(JsonNode parent, String field) {
        String raw = text(parent, field);
        if (!raw.endsWith("Z")) {
            throw LoyaltyException.invalidField(field, "Use an ISO-8601 UTC instant ending with Z.");
        }
        try {
            Instant value = Instant.parse(raw);
            if (!LoyaltyValues.truncateToMicros(value).equals(value)) {
                throw LoyaltyException.invalidField(field, "At most microsecond precision is supported.");
            }
            return value;
        } catch (DateTimeParseException e) {
            throw LoyaltyException.invalidField(field, "Use an ISO-8601 UTC instant ending with Z.");
        }
    }
}
