package edu.fpt.sep490_g22.ppswbs_backend.workshopbooking;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.BookingGroupRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.TicketBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao.WorkshopBookingRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroup;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.TicketBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.WorkshopBooking;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.CustomerBookingAccessQuery;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.facade.WorkshopBookingFacadeImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 003.1 FR-030 / FR-017a: the facade's customer access check by contact phone compares through
 * {@code ContactPhones.sameContact}, for the booking, ticket and group branches.
 */
@ExtendWith(MockitoExtension.class)
class ContactPhoneFacadeLookupTest {

    private static final String[] SAME = {"0338905173", "+84338905173", "84338905173", "+84 338 905 173"};

    @Mock
    private WorkshopBookingRepository bookingRepository;
    @Mock
    private TicketBookingRepository ticketBookingRepository;
    @Mock
    private BookingGroupRepository bookingGroupRepository;
    @InjectMocks
    private WorkshopBookingFacadeImpl facade;

    private boolean allowed(String ref, String phone) {
        return facade.verifyCustomerBookingAccess(new CustomerBookingAccessQuery(ref, null, null, phone)).authorized();
    }

    private void assertMatchesEveryWriting(String ref) {
        for (String typed : SAME) {
            assertThat(allowed(ref, typed)).as(typed).isTrue();
        }
        assertThat(allowed(ref, "0338905174")).isFalse();
        assertThat(allowed(ref, "+14155552671")).isFalse();
    }

    @Test
    void aBookingStoredWithTheOldFormMatchesEveryWriting() {
        when(bookingRepository.findByBookingCode("BK-1")).thenReturn(
                Optional.of(WorkshopBooking.builder().bookingCode("BK-1").contactPhone("0338905173").build()));
        assertMatchesEveryWriting("BK-1");
    }

    @Test
    void aTicketMatchesThroughItsGroupPhone() {
        BookingGroup group = BookingGroup.builder().groupCode("GRP-1").contactPhone("+84338905173").build();
        when(bookingRepository.findByBookingCode("TK-1")).thenReturn(Optional.empty());
        when(ticketBookingRepository.findByTicketCode("TK-1"))
                .thenReturn(Optional.of(TicketBooking.builder().ticketCode("TK-1").group(group).build()));
        assertMatchesEveryWriting("TK-1");
    }

    @Test
    void aGroupMatchesEveryWriting() {
        when(bookingRepository.findByBookingCode("GRP-2")).thenReturn(Optional.empty());
        when(ticketBookingRepository.findByTicketCode("GRP-2")).thenReturn(Optional.empty());
        when(bookingGroupRepository.findByGroupCode("GRP-2")).thenReturn(
                Optional.of(BookingGroup.builder().groupCode("GRP-2").contactPhone("0338905173").build()));
        assertMatchesEveryWriting("GRP-2");
    }
}
