package com.factory.events.controller;

import com.factory.events.dto.*;
import com.factory.events.service.EventService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    
    @PostMapping("/events/batch")
    public ResponseEntity<BatchIngestResponse> ingestBatch(@RequestBody List<EventDTO> events) {
        BatchIngestResponse response = eventService.ingestBatch(events);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(
            @RequestParam String machineId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {

        StatsResponse response = eventService.getStats(machineId, start, end);
        return ResponseEntity.ok(response);
    }

 
    @GetMapping("/stats/top-defect-lines")
    public ResponseEntity<List<TopDefectLineResponse>> getTopDefectLines(
            @RequestParam String factoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") int limit) {

        List<TopDefectLineResponse> response = eventService.getTopDefectLines(factoryId, from, to, limit);
        return ResponseEntity.ok(response);
    }
}