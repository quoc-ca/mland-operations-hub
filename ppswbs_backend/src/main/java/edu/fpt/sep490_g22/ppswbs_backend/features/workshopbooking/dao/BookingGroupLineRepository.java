package edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.workshopbooking.entity.BookingGroupLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingGroupLineRepository extends JpaRepository<BookingGroupLine, Long> {

    List<BookingGroupLine> findByGroupIdOrderByLineIndexAsc(Long groupId);

    @org.springframework.data.jpa.repository.Query(
            "select distinct l from BookingGroupLine l join fetch l.sessions where l.group.id in :groupIds")
    List<BookingGroupLine> findWithSessionsByGroupIdIn(
            @org.springframework.data.repository.query.Param("groupIds") java.util.Collection<Long> groupIds);
}
