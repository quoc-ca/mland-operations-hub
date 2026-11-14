package edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.accountmanagement.entity.AdministrativeCommune;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdministrativeCommuneRepository extends JpaRepository<AdministrativeCommune, Long> {

    List<AdministrativeCommune> findAllByDirectoryVersionAndProvinceCodeOrderByNameAsc(String directoryVersion, String provinceCode);

    Optional<AdministrativeCommune> findByDirectoryVersionAndCode(String directoryVersion, String code);

    Optional<AdministrativeCommune> findByDirectoryVersionAndProvinceCodeAndCode(String directoryVersion, String provinceCode, String code);
}
