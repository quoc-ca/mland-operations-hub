package edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.eligibility;

import edu.fpt.sep490_g22.ppswbs_backend.features.reviewmanagement.domain.ContactType;

import java.util.List;
import java.util.Optional;

public interface WorkshopAttendanceReviewPort {

    List<EligibleSourceItem> findEligibleByMemberId(Long memberAccountId);

    List<EligibleSourceItem> findEligibleByContact(ContactType contactType, String contactValue);

    Optional<EligibleSourceItem> verifySource(String sourceReference, Long memberAccountId, ContactType contactType, String contactValue);

    Optional<EligibleSourceItem> verifySource(String sourceReference);
}

