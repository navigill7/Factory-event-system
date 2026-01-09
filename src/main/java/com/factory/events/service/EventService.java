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

    @Transactional
    public BatchIngestResponse ingestBatch(List<EventDTO> events) {
        BatchIngestResponse response = new BatchIngestResponse();
        Instant now = Instant.now();


        Set<String> eventIds = events.stream()
                .map(EventDTO::getEventId)
                .collect(Collectors.toSet());

        
        Map<String, MachineEvent> existingEvents = repository.findAll().stream()
                .filter(e -> eventIds.contains(e.getEventId()))
                .collect(Collectors.toMap(MachineEvent::getEventId, e -> e));

    
        Map<String, MachineEvent> batchSeen = new HashMap<>();

        List<MachineEvent> toSave = new ArrayList<>();

        for (EventDTO dto : events) {

            
            String validationError = validateEvent(dto, now);
            if (validationError != null) {
                response.addRejection(dto.getEventId(), validationError);
                response.setRejected(response.getRejected() + 1);
                continue;
            }

            
            dto.setReceivedTime(now);
            String payloadHash = dto.generatePayloadHash();

            MachineEvent existing = existingEvents.get(dto.getEventId());
            MachineEvent batchExisting = batchSeen.get(dto.getEventId());

            
            if (existing == null && batchExisting == null) {
                MachineEvent newEvent = createEventFromDTO(dto, payloadHash);
                toSave.add(newEvent);
                batchSeen.put(dto.getEventId(), newEvent);
                response.setAccepted(response.getAccepted() + 1);
                continue;
            }

            
            MachineEvent target = (existing != null) ? existing : batchExisting;

            
            if (target.getPayloadHash().equals(payloadHash)) {
                response.setDeduped(response.getDeduped() + 1);
                continue;
            }

            
            if (dto.getReceivedTime().isAfter(target.getReceivedTime())) {
                updateEventFromDTO(target, dto, payloadHash);
                toSave.add(target);
                batchSeen.put(dto.getEventId(), target);
                response.setUpdated(response.getUpdated() + 1);
            } else {
                response.setDeduped(response.getDeduped() + 1);
            }
        }

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }

        return response;
    }

   
    private String validateEvent(EventDTO dto, Instant now) {

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

        if (dto.getDurationMs() < 0 || dto.getDurationMs() > MAX_DURATION_MS) {
            return "INVALID_DURATION";
        }

        Instant maxFutureTime = now.plus(Duration.ofMinutes(MAX_FUTURE_MINUTES));
        if (dto.getEventTime().isAfter(maxFutureTime)) {
            return "FUTURE_EVENT_TIME";
        }

        return null;
    }

 
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


    @Transactional(readOnly = true)
    public StatsResponse getStats(String machineId, Instant start, Instant end) {
        long eventsCount = repository.countEventsByMachineAndTimeRange(machineId, start, end);
        long defectsCount = repository.sumDefectsByMachineAndTimeRange(machineId, start, end);

        double hours = Duration.between(start, end).getSeconds() / 3600.0;
        double avgDefectRate = hours > 0 ? defectsCount / hours : 0.0;
        avgDefectRate = Math.round(avgDefectRate * 10.0) / 10.0;

        String status = avgDefectRate < HEALTHY_DEFECT_RATE_THRESHOLD ? "Healthy" : "Warning";

        return new StatsResponse(machineId, start, end, eventsCount, defectsCount, avgDefectRate, status);
    }


    @Transactional(readOnly = true)
    public List<TopDefectLineResponse> getTopDefectLines(String factoryId, Instant from, Instant to, int limit) {
        List<Object[]> results = repository.findTopDefectLines(factoryId, from, to);

        return results.stream()
                .limit(limit)
                .map(row -> new TopDefectLineResponse(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()
                ))
                .collect(Collectors.toList());
    }
}
