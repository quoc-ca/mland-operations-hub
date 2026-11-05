package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import java.util.List;
import java.util.Optional;

public interface RetailFulfilmentReviewPort {

    List<EligibleSourceItem> findEligibleByMemberId(Long memberAccountId);

    Optional<EligibleSourceItem> verifySource(String sourceReference, Long memberAccountId);
}

