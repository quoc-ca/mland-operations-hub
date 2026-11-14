package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.BookingGroupRecoveryController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.GroupPaymentController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.LatePaymentReviewController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.TicketRecipientController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopBookingGroupController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.controller.WorkshopCartQuoteController;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.WorkshopCartQuoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 003.1 T104: the direct-booking contract matches the controllers that serve it.
 *
 * <p>Every direct booking is a booking group (decision 2026-11-13, FR-044–FR-051), so
 * {@code workshop-booking.openapi.yaml} must declare exactly the group routes the module maps, must no longer
 * declare any path FR-046 removed, and must carry the cart-quote fields the response record returns. The file is
 * read as text on purpose: the point is that the published contract and the mapped routes agree, with no parser or
 * generator in between.
 */
class WorkshopBookingContractIT {

    private static final Path CONTRACT = Path.of("..", "ppswbs-spec", "features", "003-workshop-management",
            "003.1-workshop-booking", "contracts", "workshop-booking.openapi.yaml");

    private static final Path MOVED_FROM = Path.of("..", "ppswbs-spec", "features", "003-workshop-management",
            "003.1-workshop-booking", "contracts", "workshop-booking-cart.openapi.yaml");

    /** Every controller of the direct-booking (group) flow. */
    private static final List<Class<?>> CONTROLLERS = List.of(
            WorkshopCartQuoteController.class,
            WorkshopBookingGroupController.class,
            GroupPaymentController.class,
            BookingGroupRecoveryController.class,
            TicketRecipientController.class,
            LatePaymentReviewController.class);

    /** Paths FR-046 deleted; none of them may come back into the contract. */
    private static final List<String> REMOVED_BY_FR_046 = List.of(
            "/api/v1/workshop-bookings",
            "/api/v1/workshop-booking-quotes",
            "/api/v1/workshop-bookings/{bookingCode}/payment-attempts",
            "/api/v1/workshop-bookings/{bookingCode}/pending-hold-release",
            "/api/v1/workshop-bookings/{bookingCode}/confirmation-deliveries",
            "/api/v1/workshop-bookings/{bookingCode}/magic-link-incidents",
            "/api/v1/workshop-bookings/{bookingCode}/late-payment-follow-ups",
            "/api/v1/booking-lookups",
            "/api/v1/booking-magic-links/exchanges",
            "/api/v1/members/me/workshop-bookings",
            "/api/v1/members/me/workshop-bookings/{bookingCode}");

    @Test
    void theContractDeclaresExactlyTheRoutesTheGroupControllersMap() throws IOException {
        assertThat(declaredPaths()).isEqualTo(mappedPaths());
    }

    @Test
    void theContractNoLongerDeclaresAnyPathRemovedByFr046() throws IOException {
        assertThat(declaredPaths()).doesNotContainAnyElementsOf(REMOVED_BY_FR_046);
    }

    @Test
    void noGroupControllerStillMapsARemovedSingleBookingPath() {
        assertThat(mappedPaths()).doesNotContainAnyElementsOf(REMOVED_BY_FR_046);
    }

    @Test
    void theCartQuoteSchemaCarriesEveryFieldTheQuoteResponseReturns() throws IOException {
        String cartQuote = schema("CartQuote");
        Set<String> top = Arrays.stream(WorkshopCartQuoteResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertThat(top).contains("maxPoints", "minRedeemPoints", "pointValueVnd", "reasons");
        for (String field : top) {
            assertThat(cartQuote).as("CartQuote must declare %s", field).contains(field + ":");
        }
        // The quote nests its tickets inside each line, as the implemented response does (T098).
        assertThat(cartQuote).contains("lines:").doesNotContain("tickets: {type: array");
        assertThat(schema("QuotedLine")).contains("tickets:");
    }

    @Test
    void theRoutesNoLongerLiveInTheCartContractTheyMovedFrom() throws IOException {
        String moved = Files.readString(MOVED_FROM, StandardCharsets.UTF_8);
        assertThat(moved).contains("paths: {}");
        assertThat(moved).contains("workshop-booking.openapi.yaml");
    }

    /** The top-level keys under {@code paths:}. */
    private static Set<String> declaredPaths() throws IOException {
        Set<String> paths = new TreeSet<>();
        Matcher matcher = Pattern.compile("(?m)^ {2}(/[^:\\s]*):\\s*$")
                .matcher(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertThat(paths).as("the contract must declare paths").isNotEmpty();
        return paths;
    }

    /** Every path the group controllers map, class-level prefix joined with the method-level mapping. */
    private static Set<String> mappedPaths() {
        Set<String> paths = new TreeSet<>();
        for (Class<?> controller : CONTROLLERS) {
            String prefix = firstPathOf(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String suffix = firstPathOf(mapping);
                paths.add(suffix.isEmpty() ? prefix : prefix + suffix);
            }
        }
        return paths;
    }

    private static String firstPathOf(RequestMapping mapping) {
        if (mapping == null) {
            return "";
        }
        Set<String> values = new LinkedHashSet<>(Arrays.asList(mapping.value().length > 0 ? mapping.value()
                : mapping.path()));
        return values.isEmpty() ? "" : values.iterator().next();
    }

    /** The body of one schema under {@code components.schemas}, up to the next sibling schema. */
    private static String schema(String name) throws IOException {
        String contract = Files.readString(CONTRACT, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?ms)^ {4}" + Pattern.quote(name) + ":\\n(.*?)(?=^ {4}\\S|\\z)")
                .matcher(contract);
        assertThat(matcher.find()).as("the contract must declare schema %s", name).isTrue();
        return matcher.group(1);
    }
}
