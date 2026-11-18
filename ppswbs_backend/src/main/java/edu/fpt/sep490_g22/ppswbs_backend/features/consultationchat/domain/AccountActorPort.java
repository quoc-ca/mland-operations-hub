package edu.fpt.sep490_g22.ppswbs_backend.features.consultationchat.domain;

import java.util.List;
import java.util.Optional;

public interface AccountActorPort {

    record CallerActor(
            Long accountId,
            String role,
            String status,
            List<Long> activeLocationIds
    ) {}

    Optional<CallerActor> getCurrentCaller();

    Optional<String> getAccountFullName(Long accountId);
}
