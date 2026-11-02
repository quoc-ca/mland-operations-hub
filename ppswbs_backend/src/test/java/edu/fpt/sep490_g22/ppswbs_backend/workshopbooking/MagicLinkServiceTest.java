package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MagicLinkAccessRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingLookupService;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.BookingNotFoundException;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service.MagicLinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MagicLinkServiceTest {

    @Mock private MagicLinkAccessRepository links;
    @Mock private BookingLookupService bookingLookup;
    @InjectMocks private MagicLinkService service;

    @Test
    void validBearerLooksUpOnlyItsHashAndReturnsCurrentProjection() {
        String token = "opaque-magic-link-token";
        WorkshopBooking booking = WorkshopBooking.builder().id(17L).bookingCode("BK-MAGIC-17").build();
        MagicLinkAccess link = MagicLinkAccess.builder().booking(booking)
                .tokenHash(sha256(token)).lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        BookingSummaryResponse projection = BookingSummaryResponse.builder().bookingCode("BK-MAGIC-17").build();
        when(links.findByTokenHashForUpdate(sha256(token))).thenReturn(Optional.of(link));
        when(bookingLookup.projectForMagicLink(booking)).thenReturn(projection);

        assertThat(service.exchange(token)).isSameAs(projection);

        verify(links).findByTokenHashForUpdate(sha256(token));
        verify(bookingLookup).projectForMagicLink(booking);
    }

    @Test
    void activeBearerCanBeExchangedAgainUntilItsExpiry() {
        String token = "reusable-magic-link-token";
        WorkshopBooking booking = WorkshopBooking.builder().id(27L).bookingCode("BK-MAGIC-27").build();
        MagicLinkAccess link = MagicLinkAccess.builder().booking(booking)
                .tokenHash(sha256(token)).lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        BookingSummaryResponse projection = BookingSummaryResponse.builder().bookingCode("BK-MAGIC-27").build();
        when(links.findByTokenHashForUpdate(sha256(token))).thenReturn(Optional.of(link));
        when(bookingLookup.projectForMagicLink(booking)).thenReturn(projection);

        assertThat(service.exchange(token)).isSameAs(projection);
        assertThat(service.exchange(token)).isSameAs(projection);

        verify(links, times(2)).findByTokenHashForUpdate(sha256(token));
        verify(bookingLookup, times(2)).projectForMagicLink(booking);
    }

    @Test
    void revokedBearerRevealsNoProjection() {
        String token = "not-active";
        when(links.findByTokenHashForUpdate(sha256(token))).thenReturn(Optional.of(MagicLinkAccess.builder()
                .booking(WorkshopBooking.builder().id(18L).build())
                .tokenHash(sha256(token)).lifecycleStatus(MagicLinkLifecycleStatus.REVOKED)
                .expiresAt(Instant.now().plusSeconds(30)).build()));

        assertThatThrownBy(() -> service.exchange(token)).isInstanceOf(BookingNotFoundException.class);
        verifyNoInteractions(bookingLookup);
    }

    @Test
    void unknownBearerRevealsNoProjection() {
        String token = "unknown";
        when(links.findByTokenHashForUpdate(sha256(token))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(token)).isInstanceOf(BookingNotFoundException.class);
        verifyNoInteractions(bookingLookup);
    }

    @Test
    void elapsedBearerRevealsNoProjection() {
        String token = "expired";
        when(links.findByTokenHashForUpdate(sha256(token))).thenReturn(Optional.of(MagicLinkAccess.builder()
                .booking(WorkshopBooking.builder().id(19L).build())
                .tokenHash(sha256(token)).lifecycleStatus(MagicLinkLifecycleStatus.ACTIVE)
                .expiresAt(Instant.now().minusSeconds(1)).build()));

        assertThatThrownBy(() -> service.exchange(token)).isInstanceOf(BookingNotFoundException.class);
        verifyNoInteractions(bookingLookup);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
