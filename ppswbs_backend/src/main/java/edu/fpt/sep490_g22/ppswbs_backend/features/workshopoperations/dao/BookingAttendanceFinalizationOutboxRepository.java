package edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.BookingAttendanceFinalizationOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface BookingAttendanceFinalizationOutboxRepository extends JpaRepository<BookingAttendanceFinalizationOutboxEvent, Long> {
    boolean existsByBookingReferenceAndEventType(String bookingReference, String eventType);
    List<BookingAttendanceFinalizationOutboxEvent> findTop50ByPublishedAtIsNullOrderByIdAsc();

    /**
     * Claims one unpublished row. Returns 1 only for the single run (thread or instance)
     * that wins the row; a concurrent run blocks on the row lock and then sees 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BookingAttendanceFinalizationOutboxEvent o SET o.publishedAt = :publishedAt "
            + "WHERE o.id = :id AND o.publishedAt IS NULL")
    int markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);
}
