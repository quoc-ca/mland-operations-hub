package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.MagicLinkAccessRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dto.BookingSummaryResponse;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkAccess;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.MagicLinkLifecycleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exchanges an opaque Magic Link bearer value for the current safe booking projection. */
@Service
@RequiredArgsConstructor
public class MagicLinkService {

    private final MagicLinkAccessRepository magicLinks;
    private final BookingLookupService bookingLookup;

    @Transactional
    public BookingSummaryResponse exchange(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new BookingNotFoundException("Booking not found");

        MagicLinkAccess link = magicLinks.findByTokenHashForUpdate(hashToken(rawToken))
                .filter(candidate -> candidate.getLifecycleStatus() == MagicLinkLifecycleStatus.ACTIVE)
                .filter(candidate -> candidate.getExpiresAt() != null && candidate.getExpiresAt().isAfter(java.time.Instant.now()))
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));

        return bookingLookup.projectForMagicLink(link.getBooking());
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
