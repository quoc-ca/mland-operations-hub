package edu.fpt.sep490_g22.ppswbs_backend.designreview.facade;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.BookingDesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure.BookingDesignRequestRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DesignAnalysisFacade {

    private final BookingDesignRequestRepository requestRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingDesignSummaryDto {
        private String designRequestId;
        private String bookingCode;
        private Integer designVersion;
        private String analysisStatus;
        private String reviewStatus;
        private Long estimatedValue;
        private String nextAction;
    }

    @Transactional(readOnly = true)
    public Optional<BookingDesignSummaryDto> getLatestDesignStatusForBooking(String bookingCode) {
        if (bookingCode == null || bookingCode.isBlank()) {
            return Optional.empty();
        }
        return requestRepository.findFirstByBookingCodeOrderByDesignVersionDesc(bookingCode.trim())
                .map(this::toSummaryDto);
    }

    private BookingDesignSummaryDto toSummaryDto(BookingDesignRequest req) {
        return BookingDesignSummaryDto.builder()
                .designRequestId(req.getId())
                .bookingCode(req.getBookingCode())
                .designVersion(req.getDesignVersion())
                .analysisStatus(req.getAnalysisStatus().name())
                .reviewStatus(req.getReviewStatus().name())
                .estimatedValue(req.getEstimatedValue())
                .nextAction(req.getNextAction())
                .build();
    }
}
