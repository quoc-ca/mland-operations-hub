package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import java.util.List;
import java.util.Optional;
import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;

public interface RetailFulfilmentReviewPort {

    List<EligibleSourceItem> findEligibleByMemberId(Long memberAccountId);

    List<EligibleSourceItem> findEligibleByContact(ContactType contactType, String contactValue);

    Optional<EligibleSourceItem> verifySource(String sourceReference, Long memberAccountId,
                                              ContactType contactType, String contactValue);

    @Deprecated
    default Optional<EligibleSourceItem> verifySource(String sourceReference, Long memberAccountId) {
        return verifySource(sourceReference, memberAccountId, null, null);
    }

    Optional<EligibleSourceItem> verifySource(String sourceReference);
}

