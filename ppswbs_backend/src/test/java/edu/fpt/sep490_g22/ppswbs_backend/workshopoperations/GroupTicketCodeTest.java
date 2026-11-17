package edu.fpt.sep490_g22.ppswbs_backend.workshopoperations;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.dao.AttendanceTicketRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.entity.AttendanceTicket;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopoperations.service.GroupTicketCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Check-in and attendance actions accept the group ticket code (GRP-…-L2-T1) beside the Operations code (TK-…). */
class GroupTicketCodeTest {

    @Test
    @DisplayName("parse: a group ticket code is the line reference plus the person's number; anything else is not")
    void parse() {
        assertThat(GroupTicketCode.parse("GRP-5E849940018F442D8C9C-L2-T1"))
                .contains(new GroupTicketCode("GRP-5E849940018F442D8C9C-L2", 1));
        assertThat(GroupTicketCode.parse(" GRP-AB-L10-T12 ")).contains(new GroupTicketCode("GRP-AB-L10", 12));
        assertThat(GroupTicketCode.parse("TK-701F404C")).isEmpty();
        assertThat(GroupTicketCode.parse("GRP-AB-L2")).isEmpty();
        assertThat(GroupTicketCode.parse("GRP-AB-L2-T99999999999999")).isEmpty();
        assertThat(GroupTicketCode.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("lookup: the Operations code wins, a group ticket code falls back to line reference and number")
    void lookup() {
        AttendanceTicketRepository repository = mock(AttendanceTicketRepository.class);
        when(repository.findByAnyTicketCode(org.mockito.ArgumentMatchers.anyString())).thenCallRealMethod();
        AttendanceTicket byOwnCode = AttendanceTicket.builder().ticketCode("TK-701F404C").build();
        AttendanceTicket byGroupCode = AttendanceTicket.builder().ticketCode("TK-FB0F371B").build();
        when(repository.findByTicketCode("TK-701F404C")).thenReturn(Optional.of(byOwnCode));
        when(repository.findByTicketCode("GRP-X-L2-T1")).thenReturn(Optional.empty());
        when(repository.findByBookingReferenceAndTicketSequence("GRP-X-L2", 1)).thenReturn(Optional.of(byGroupCode));

        assertThat(repository.findByAnyTicketCode("TK-701F404C")).containsSame(byOwnCode);
        assertThat(repository.findByAnyTicketCode("GRP-X-L2-T1")).containsSame(byGroupCode);
        assertThat(repository.findByAnyTicketCode("GRP-X-L2-T2")).isEmpty();
        assertThat(repository.findByAnyTicketCode("NOPE")).isEmpty();
    }
}
