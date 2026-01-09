package com.factory.events.repository;

import com.factory.events.model.MachineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MachineEventRepository extends JpaRepository<MachineEvent, Long> {

    Optional<MachineEvent> findByEventId(String eventId);

    
    List<MachineEvent> findByEventIdIn(List<String> eventIds);

    @Query("SELECT COUNT(e) FROM MachineEvent e WHERE e.machineId = :machineId " +
            "AND e.eventTime >= :start AND e.eventTime < :end")
    long countEventsByMachineAndTimeRange(
            @Param("machineId") String machineId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT COALESCE(SUM(e.defectCount), 0) FROM MachineEvent e " +
            "WHERE e.machineId = :machineId AND e.eventTime >= :start AND e.eventTime < :end " +
            "AND e.defectCount >= 0")
    long sumDefectsByMachineAndTimeRange(
            @Param("machineId") String machineId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    @Query("SELECT e.lineId as lineId, " +
            "COALESCE(SUM(CASE WHEN e.defectCount >= 0 THEN e.defectCount ELSE 0 END), 0) as totalDefects, " +
            "COUNT(e) as eventCount " +
            "FROM MachineEvent e " +
            "WHERE e.factoryId = :factoryId AND e.lineId IS NOT NULL " +
            "AND e.eventTime >= :from AND e.eventTime < :to " +
            "GROUP BY e.lineId " +
            "ORDER BY totalDefects DESC")
    List<Object[]> findTopDefectLines(
            @Param("factoryId") String factoryId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}