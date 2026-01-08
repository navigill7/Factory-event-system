package com.factory.events.service;

import com.factory.events.dto.*;
import com.factory.events.model.MachineEvent;
import com.factory.events.repository.MachineEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final long MAX_DURATION_MS = 6 * 60 * 60 * 1000L; // 6 hours
    private static final long MAX_FUTURE_MINUTES = 15;
    private static final double HEALTHY_DEFECT_RATE_THRESHOLD = 2.0;

    private final MachineEventRepository repository;

    public EventService(MachineEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Batch ingest events with validation, deduplication, and update logic.
     * Thread-safe through database transactions and optimistic locking.
     */
    @Transactional
    public BatchIngestResponse ingestBatch(List<EventDTO> events) {
        BatchIngestResponse response = new BatchIngestResponse();
        Instant now = Instant.now();

        // Collect all eventIds to check for existing records
        Set<String> eventIds = events.stream()
                .map(EventDTO::getEventId)
                .collect(Collectors.toSet());

        // Fetch existing events in one query for performance
        Map<String, MachineEvent> existingEvents = repository.findAll().stream()
                .filter(e -> eventIds.contains(e.getEventId()))
                .collect(Collectors.toMap(MachineEvent::getEventId, e -> e));

        List<MachineEvent> toSave = new ArrayList<>();

        for (EventDTO dto : events) {
            // Validation
            String validationError = validateEvent(dto, now);
            if (validationError != null) {
                response.addRejection(dto.getEventId(), validationError);
                response.setRejected(response.getRejected() + 1);
                continue;
            }

            // Set receivedTime to current time (ignore any value in request)
            dto.setReceivedTime(now);
            String payloadHash = dto.generatePayloadHash();

            MachineEvent existing = existingEvents.get(dto.getEventId());

            if (existing == null) {
                // New event - accept it
                MachineEvent newEvent = createEventFromDTO(dto, payloadHash);
                toSave.add(newEvent);
                response.setAccepted(response.getAccepted() + 1);
            } else {
                // Event with same ID exists - check for dedup or update
                if (existing.getPayloadHash().equals(payloadHash)) {
                    // Identical payload - deduplicate
                    response.setDeduped(response.getDeduped() + 1);
                } else {
                    // Different payload - check receivedTime to decide update
                    if (dto.getReceivedTime().isAfter(existing.getReceivedTime())) {
                        // Newer receivedTime - update
                        updateEventFromDTO(existing, dto, payloadHash);
                        toSave.add(existing);
                        response.setUpdated(response.getUpdated() + 1);
                    } else {
                        // Older receivedTime - ignore
                        response.setDeduped(response.getDeduped() + 1);
                    }
                }
            }
        }

        // Batch save all new/updated events
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }

        return response;
    }

    /**
     * Validate event according to business rules.
     * Returns null if valid, error message if invalid.
     */
    private String validateEvent(EventDTO dto, Instant now) {
        // Check required fields
        if (dto.getEventId() == null || dto.getEventId().isEmpty()) {
            return "MISSING_EVENT_ID";
        }
        if (dto.getMachineId() == null || dto.getMachineId().isEmpty()) {
            return "MISSING_MACHINE_ID";
        }
        if (dto.getEventTime() == null) {
            return "MISSING_EVENT_TIME";
        }
        if (dto.getDurationMs() == null) {
            return "MISSING_DURATION";
        }
        if (dto.getDefectCount() == null) {
            return "MISSING_DEFECT_COUNT";
        }

        // Validate duration
        if (dto.getDurationMs() < 0 || dto.getDurationMs() > MAX_DURATION_MS) {
            return "INVALID_DURATION";
        }

        // Validate eventTime not too far in future
        Instant maxFutureTime = now.plus(Duration.ofMinutes(MAX_FUTURE_MINUTES));
        if (dto.getEventTime().isAfter(maxFutureTime)) {
            return "FUTURE_EVENT_TIME";
        }

        return null;
    }

    /**
     * Create new MachineEvent entity from DTO.
     */
    private MachineEvent createEventFromDTO(EventDTO dto, String payloadHash) {
        return new MachineEvent(
                dto.getEventId(),
                dto.getEventTime(),
                dto.getReceivedTime(),
                dto.getMachineId(),
                dto.getDurationMs(),
                dto.getDefectCount(),
                dto.getLineId(),
                dto.getFactoryId(),
                payloadHash
        );
    }

    /**
     * Update existing MachineEvent from DTO.
     */
    private void updateEventFromDTO(MachineEvent event, EventDTO dto, String payloadHash) {
        event.setEventTime(dto.getEventTime());
        event.setReceivedTime(dto.getReceivedTime());
        event.setMachineId(dto.getMachineId());
        event.setDurationMs(dto.getDurationMs());
        event.setDefectCount(dto.getDefectCount());
        event.setLineId(dto.getLineId());
        event.setFactoryId(dto.getFactoryId());
        event.setPayloadHash(payloadHash);
    }

    /**
     * Get statistics for a machine in a time window.
     */
    @Transactional(readOnly = true)
    public StatsResponse getStats(String machineId, Instant start, Instant end) {
        long eventsCount = repository.countEventsByMachineAndTimeRange(machineId, start, end);
        long defectsCount = repository.sumDefectsByMachineAndTimeRange(machineId, start, end);

        // Calculate window duration in hours
        double windowHours = Duration.between(start, end).getSeconds() / 3600.0;
        double avgDefectRate = windowHours > 0 ? defectsCount / windowHours : 0.0;

        // Round to 1 decimal place
        avgDefectRate = Math.round(avgDefectRate * 10.0) / 10.0;

        String status = avgDefectRate < HEALTHY_DEFECT_RATE_THRESHOLD ? "Healthy" : "Warning";

        return new StatsResponse(machineId, start, end, eventsCount, defectsCount, avgDefectRate, status);
    }

    /**
     * Get top defect lines for a factory.
     */
    @Transactional(readOnly = true)
    public List<TopDefectLineResponse> getTopDefectLines(String factoryId, Instant from, Instant to, int limit) {
        List<Object[]> results = repository.findTopDefectLines(factoryId, from, to);

        return results.stream()
                .limit(limit)
                .map(row -> new TopDefectLineResponse(
                        (String) row[0],      // lineId
                        ((Number) row[1]).longValue(),  // totalDefects
                        ((Number) row[2]).longValue()   // eventCount
                ))
                .collect(Collectors.toList());
    }
}