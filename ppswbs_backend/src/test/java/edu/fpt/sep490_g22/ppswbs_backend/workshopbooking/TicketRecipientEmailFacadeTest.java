package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketRecipientRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketRecipient;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacadeImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 003.3 T112 / FR-043: the recipient email Settlement needs to tell a recipient that a session was cancelled. */
@ExtendWith(MockitoExtension.class)
class TicketRecipientEmailFacadeTest {

    @Mock private TicketBookingRepository ticketBookingRepository;
    @Mock private TicketRecipientRepository ticketRecipientRepository;
    @InjectMocks private WorkshopBookingFacadeImpl facade;

    private TicketBooking ticket(String buyerEmail) {
        BookingGroup group = BookingGroup.builder().id(10L).groupCode("GRP-1").contactEmail(buyerEmail).build();
        return TicketBooking.builder().id(5L).ticketCode("T-1").group(group).build();
    }

    private TicketRecipient recipient(String email) {
        return TicketRecipient.builder().recipientEmail(email).build();
    }

    @Test
    @DisplayName("returns the recipient's email, trimmed")
    void returnsRecipientEmail() {
        when(ticketBookingRepository.findByTicketCode("T-1")).thenReturn(Optional.of(ticket("buyer@example.invalid")));
        when(ticketRecipientRepository.findByTicketId(5L)).thenReturn(Optional.of(recipient(" guest@example.invalid ")));

        assertThat(facade.getTicketRecipientEmail("T-1")).isEqualTo("guest@example.invalid");
    }

    @Test
    @DisplayName("null when the ticket is unknown, has no recipient, or the recipient is the buyer")
    void nullWhenNothingToSend() {
        assertThat(facade.getTicketRecipientEmail(null)).isNull();
        assertThat(facade.getTicketRecipientEmail(" ")).isNull();
        when(ticketBookingRepository.findByTicketCode("NOPE")).thenReturn(Optional.empty());
        assertThat(facade.getTicketRecipientEmail("NOPE")).isNull();

        when(ticketBookingRepository.findByTicketCode("T-1")).thenReturn(Optional.of(ticket("buyer@example.invalid")));
        when(ticketRecipientRepository.findByTicketId(5L)).thenReturn(Optional.empty());
        assertThat(facade.getTicketRecipientEmail("T-1")).isNull();

        when(ticketRecipientRepository.findByTicketId(5L)).thenReturn(Optional.of(recipient("BUYER@example.invalid")));
        assertThat(facade.getTicketRecipientEmail("T-1")).as("the buyer already gets the offer").isNull();
    }
}
