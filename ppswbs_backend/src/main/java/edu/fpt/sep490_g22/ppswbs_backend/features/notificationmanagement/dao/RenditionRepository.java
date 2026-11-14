package edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.notificationmanagement.entity.Rendition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RenditionRepository extends JpaRepository<Rendition, Long> {

    Optional<Rendition> findByNotificationRequestId(Long notificationRequestId);

    void deleteByNotificationRequestId(Long notificationRequestId);
}

