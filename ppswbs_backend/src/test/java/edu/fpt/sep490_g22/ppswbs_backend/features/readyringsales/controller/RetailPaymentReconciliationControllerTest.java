package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.controller;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.*;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailPaymentReconciliationRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailPaymentReconciliationControllerTest {
    @Test void onlyActiveManagerCanRead() {
        var repository = mock(RetailPaymentReconciliationRepository.class);
        var request = mock(HttpServletRequest.class);
        var controller = new RetailPaymentReconciliationController(repository);
        assertThatThrownBy(() -> controller.list(request, 0)).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
        when(request.getAttribute("currentAccountActor")).thenReturn(
                new CurrentAccountActor(1L, ActorRole.MEMBER, ActorStatus.ACTIVE, true, List.of()));
        assertThatThrownBy(() -> controller.list(request, 0)).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(repository);
        when(request.getAttribute("currentAccountActor")).thenReturn(
                new CurrentAccountActor(2L, ActorRole.MANAGER, ActorStatus.ACTIVE, true, List.of()));
        when(repository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(controller.list(request, 0)).isEmpty();
    }
}
