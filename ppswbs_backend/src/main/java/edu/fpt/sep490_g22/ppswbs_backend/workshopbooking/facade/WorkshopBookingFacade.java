package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.facade;

import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.BookingStatus;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.domain.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.workshopbooking.infrastructure.WorkshopBookingRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkshopBookingFacade {

    private final WorkshopBookingRepository bookingRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkshopBookingDto {
        private Long id;
        private String bookingCode;
        private Long memberId;
        private String contactEmail;
        private String canonicalEmail;
        private String bookingStatus;
        private String paymentStatus;
        private Instant createdAt;
    }

    @Transactional(readOnly = true)
    public List<WorkshopBookingDto> getEligibleGuestBookingsForImport(String canonicalEmail) {
        if (canonicalEmail == null || canonicalEmail.isBlank()) {
            return List.of();
        }
        String sanitized = canonicalEmail.trim().toLowerCase();
        List<WorkshopBooking> eligible = bookingRepository.findByCanonicalEmailAndBookingStatusAndMemberIdIsNull(
                sanitized, BookingStatus.CONFIRMED);

        return eligible.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public int linkGuestBookingsToMember(List<Long> bookingIds, Long memberId) {
        if (bookingIds == null || bookingIds.isEmpty() || memberId == null) {
            return 0;
        }
        int linkedCount = 0;
        for (Long id : bookingIds) {
            WorkshopBooking booking = bookingRepository.findById(id).orElse(null);
            if (booking != null && booking.getMemberId() == null && booking.getBookingStatus() == BookingStatus.CONFIRMED) {
                booking.setMemberId(memberId);
                bookingRepository.save(booking);
                linkedCount++;
            }
        }
        return linkedCount;
    }

    private WorkshopBookingDto toDto(WorkshopBooking b) {
        return WorkshopBookingDto.builder()
                .id(b.getId())
                .bookingCode(b.getBookingCode())
                .memberId(b.getMemberId())
                .contactEmail(b.getContactEmail())
                .canonicalEmail(b.getCanonicalEmail())
                .bookingStatus(b.getBookingStatus().name())
                .paymentStatus(b.getPaymentStatus().name())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
