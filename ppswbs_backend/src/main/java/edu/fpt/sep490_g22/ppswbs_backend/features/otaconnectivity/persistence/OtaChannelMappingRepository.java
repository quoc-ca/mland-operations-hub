package edu.fpt.sep490_g22.ppswbs_backend.features.otaconnectivity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OtaChannelMappingRepository extends JpaRepository<OtaChannelMapping, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mapping from OtaChannelMapping mapping join fetch mapping.connection where mapping.id = :id")
    Optional<OtaChannelMapping> findByIdForUpdate(@Param("id") Long id);

    Optional<OtaChannelMapping> findByConnectionIdAndSessionReference(Long connectionId, String sessionReference);

    Optional<OtaChannelMapping> findByConnectionIdAndExternalProductIdAndExternalOptionId(
            Long connectionId, String externalProductId, String externalOptionId);

    Optional<OtaChannelMapping> findByPublicReference(String publicReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mapping from OtaChannelMapping mapping join fetch mapping.connection where mapping.publicReference = :reference")
    Optional<OtaChannelMapping> findByPublicReferenceForUpdate(@Param("reference") String publicReference);

    boolean existsByConnection_IdAndExternalProductIdAndExternalOptionIdAndIdNot(
            Long connectionId, String externalProductId, String externalOptionId, Long id);

    boolean existsByConnection_IdAndSessionReferenceAndIdNot(Long connectionId, String sessionReference, Long id);

    @Query("select mapping from OtaChannelMapping mapping join fetch mapping.connection order by mapping.id asc")
    List<OtaChannelMapping> findAllWithConnectionOrderByIdAsc();

    List<OtaChannelMapping> findAllBySessionReference(String sessionReference);

    List<OtaChannelMapping> findByConnectionIdAndStatusOrderByIdAsc(Long connectionId, OtaMappingStatus status);
}
