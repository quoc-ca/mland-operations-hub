package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface OtaCustomerPaymentEvidenceRepository
        extends Repository<OtaCustomerPaymentEvidence, Long> {

    OtaCustomerPaymentEvidence save(OtaCustomerPaymentEvidence evidence);

    Optional<OtaCustomerPaymentEvidence> findByChannelBookingIdAndExternalEvidenceReference(
            Long channelBookingId, String externalEvidenceReference);

    List<OtaCustomerPaymentEvidence> findAllByChannelBookingIdOrderByRecordedAtAscIdAsc(Long channelBookingId);
}
