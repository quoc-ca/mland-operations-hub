package edu.fpt.sep490_g22.ppswbs_backend.designreview.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.BookingDesignRequest;
import edu.fpt.sep490_g22.ppswbs_backend.designreview.domain.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingDesignRequestRepository extends JpaRepository<BookingDesignRequest, String> {

    Optional<BookingDesignRequest> findByMemberIdAndBookingCodeAndDesignVersionAndIdempotencyKey(
            Long memberId, String bookingCode, Integer designVersion, String idempotencyKey
    );

    List<BookingDesignRequest> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<BookingDesignRequest> findByReviewStatusOrderByCreatedAtAsc(ReviewStatus reviewStatus);

    List<BookingDesignRequest> findByBookingCodeOrderByDesignVersionDesc(String bookingCode);

    Optional<BookingDesignRequest> findFirstByBookingCodeOrderByDesignVersionDesc(String bookingCode);
}
