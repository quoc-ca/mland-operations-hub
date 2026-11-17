package edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.ringdesignconfiguration.domain.RingAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RingAttachmentRepository extends JpaRepository<RingAttachment, Long> {

    Optional<RingAttachment> findByAttachmentCode(String attachmentCode);

    List<RingAttachment> findAllByIsActiveTrue();
}

