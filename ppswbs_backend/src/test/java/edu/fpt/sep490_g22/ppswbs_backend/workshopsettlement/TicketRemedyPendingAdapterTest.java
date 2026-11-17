package edu.fpt.sep490_g22.ppswbs_backend.workshopsettlement;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.dao.SettlementTicketMlandRemedyRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.entity.SettlementTicketMlandRemedy;
import edu.fpt.sep490_g22.ppswbs_backend.features.workshopsettlement.facade.TicketRemedyPendingAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketRemedyPendingAdapterTest {

    @Mock SettlementTicketMlandRemedyRepository remedies;
    @InjectMocks TicketRemedyPendingAdapter adapter;

    @Test
    void returnsOnlyTicketCodesWithPendingAnswerOffer() {
        when(remedies.findByTicketCodeInAndOutcome(List.of("TK-1", "TK-2", "TK-3"), "PENDING_ANSWER"))
                .thenReturn(List.of(
                        SettlementTicketMlandRemedy.builder().ticketCode("TK-1").build(),
                        SettlementTicketMlandRemedy.builder().ticketCode("TK-3").build()));

        assertThat(adapter.pendingTicketCodes(List.of("TK-1", "TK-2", "TK-3"))).containsExactlyInAnyOrder("TK-1", "TK-3");
    }

    @Test
    void emptyOrNullInputSkipsTheQuery() {
        assertThat(adapter.pendingTicketCodes(List.of())).isEqualTo(Set.of());
        assertThat(adapter.pendingTicketCodes(null)).isEqualTo(Set.of());
        verifyNoInteractions(remedies);
    }
}
