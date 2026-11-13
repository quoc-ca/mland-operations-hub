package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.TicketBookingRef;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacadeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 003.1 T099: 003.3 resolves a 003.2 ticket reference to the ticket code of its folio. */
@ExtendWith(MockitoExtension.class)
class TicketBookingReferenceLookupTest {

    @Mock
    private TicketBookingRepository ticketBookingRepository;
    @InjectMocks
    private WorkshopBookingFacadeImpl facade;

    @Test
    void aKnownTicketReferenceGivesItsTicketAndGroupCodes() {
        BookingGroup group = BookingGroup.builder().groupCode("GRP-T99").build();
        when(ticketBookingRepository.findByOperationsTicketReference("9b6f2c1e-0000-4000-8000-000000000001"))
                .thenReturn(Optional.of(TicketBooking.builder().ticketCode("TK-T99").group(group).build()));

        assertThat(facade.findTicketBookingByOperationsReference(" 9b6f2c1e-0000-4000-8000-000000000001 "))
                .contains(new TicketBookingRef("TK-T99", "GRP-T99"));
    }

    @Test
    void anUnknownOrBlankReferenceGivesNothing() {
        assertThat(facade.findTicketBookingByOperationsReference("unknown")).isEmpty();
        assertThat(facade.findTicketBookingByOperationsReference(" ")).isEmpty();
        assertThat(facade.findTicketBookingByOperationsReference(null)).isEmpty();
    }

    @Test
    void blankInputNeverQueries() {
        facade.findTicketBookingByOperationsReference(null);
        verifyNoInteractions(ticketBookingRepository);
    }
}
