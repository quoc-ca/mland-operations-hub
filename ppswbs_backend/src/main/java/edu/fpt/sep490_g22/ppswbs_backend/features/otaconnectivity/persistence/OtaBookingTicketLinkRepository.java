package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaBookingTicketLinkRepository extends JpaRepository<OtaBookingTicketLink, Long> {
    List<OtaBookingTicketLink> findAllByChannelBookingIdOrderByParticipantOrdinalAsc(Long channelBookingId);

    Optional<OtaBookingTicketLink> findByChannelBookingIdAndParticipantOrdinal(
            Long channelBookingId, Integer participantOrdinal);

    boolean existsByOperationsTicketReference(String operationsTicketReference);
}
