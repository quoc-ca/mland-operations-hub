package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

public interface OtaAddonBillLinkRepository extends Repository<OtaAddonBillLink, Long> {

    OtaAddonBillLink save(OtaAddonBillLink link);

    Optional<OtaAddonBillLink> findById(Long id);

    Optional<OtaAddonBillLink> findByChannelBooking_IdAndSettlementQuoteReference(Long channelBookingId, String settlementQuoteReference);

    Optional<OtaAddonBillLink> findBySettlementQuoteReference(String settlementQuoteReference);

    List<OtaAddonBillLink> findAllByChannelBooking_IdOrderByCreatedAtAsc(Long channelBookingId);
}

