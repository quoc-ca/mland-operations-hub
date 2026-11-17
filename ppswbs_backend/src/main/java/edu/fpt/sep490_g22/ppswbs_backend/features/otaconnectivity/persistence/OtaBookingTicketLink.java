package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One Operations ticket for one participant entitlement of a channel booking. */
@Entity
@Table(name = "ota_booking_ticket_links", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ota_ticket_link_participant",
                columnNames = {"channel_booking_id", "participant_ordinal"}),
        @UniqueConstraint(name = "uq_ota_ticket_link_operations_reference",
                columnNames = {"operations_ticket_reference"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtaBookingTicketLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_booking_id", nullable = false, updatable = false)
    private Long channelBookingId;

    @Column(name = "participant_ordinal", nullable = false, updatable = false)
    private Integer participantOrdinal;

    @Column(name = "operations_ticket_reference", nullable = false, length = 128, updatable = false)
    private String operationsTicketReference;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    public static OtaBookingTicketLink link(Long channelBookingId, int participantOrdinal,
                                            String operationsTicketReference) {
        if (channelBookingId == null || channelBookingId <= 0 || participantOrdinal <= 0
                || operationsTicketReference == null || operationsTicketReference.isBlank()
                || operationsTicketReference.length() > 128) {
            throw new IllegalArgumentException("A channel booking, positive participant ordinal and ticket reference are required");
        }
        OtaBookingTicketLink link = new OtaBookingTicketLink();
        link.channelBookingId = channelBookingId;
        link.participantOrdinal = participantOrdinal;
        link.operationsTicketReference = operationsTicketReference.trim();
        link.linkedAt = Instant.now();
        return link;
    }
}
