package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AdministrativeDirectoryVersion;
import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.DirectoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdministrativeDirectoryVersionRepository extends JpaRepository<AdministrativeDirectoryVersion, Long> {

    Optional<AdministrativeDirectoryVersion> findFirstByStatusOrderByEffectiveFromDesc(DirectoryStatus status);

    Optional<AdministrativeDirectoryVersion> findByVersion(String version);
}
