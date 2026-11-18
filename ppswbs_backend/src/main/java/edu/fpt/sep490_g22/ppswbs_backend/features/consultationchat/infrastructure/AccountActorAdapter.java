package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.infrastructure;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.AccountFacade;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.ActorContext;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.facade.CurrentAccountActor;
import edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain.AccountActorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountActorAdapter implements AccountActorPort {

    private final AccountFacade accountFacade;

    @Override
    public Optional<CallerActor> getCurrentCaller() {
        return ActorContext.getCurrentActor().map(actor -> new CallerActor(
                actor.accountId(),
                actor.role() != null ? actor.role().name() : null,
                actor.status() != null ? actor.status().name() : null,
                actor.activeWorkshopLocationIds()
        ));
    }

    @Override
    public Optional<String> getAccountFullName(Long accountId) {
        if (accountId == null) return Optional.empty();
        return accountFacade.findAccountFullName(accountId);
    }
}
