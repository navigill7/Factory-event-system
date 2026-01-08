package com.factory.events.service;

import com.factory.events.dto.*;
import com.factory.events.model.MachineEvent;
import com.factory.events.repository.MachineEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private MachineEventRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    /**
     * Test 1: Identical duplicate eventId should be deduped.
     */
    @Test
    void testIdenticalDuplicateDeduped() {
        Instant now = Instant.now();

        EventDTO event1 = createEventDTO("E-1", now, "M-001", 1000L, 0);
        EventDTO event2 = createEventDTO("E-1", now, "M-001", 1000L, 0);

        BatchIngestResponse response = eventService.ingestBatch(Arrays.asList(event1, event2));

        assertEquals(1, response.getAccepted());
        assertEquals(1, response.getDeduped());
        assertEquals(0, response.getUpdated());
        assertEquals(0, response.getRejected());

        assertEquals(1, repository.count());
    }

    /**
     * Test 2: Different payload with newer receivedTime should trigger update.
     */
    @Test
    void testDifferentPayloadNewerReceivedTimeUpdates() throws InterruptedException {
        Instant eventTime = Instant.now().minus(1, ChronoUnit.HOURS);

        // First ingestion
        EventDTO event1 = createEventDTO("E-2", eventTime, "M-001", 1000L, 5);
        eventService.ingestBatch(Collections.singletonList(event1));

        // Wait to ensure different receivedTime
        Thread.sleep(10);

        // Second ingestion with different payload
        EventDTO event2 = createEventDTO("E-2", eventTime, "M-001", 2000L, 10);
        BatchIngestResponse response = eventService.ingestBatch(Collections.singletonList(event2));

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getUpdated());
        assertEquals(0, response.getDeduped());

        // Verify update happened
        MachineEvent stored = repository.findByEventId("E-2").orElseThrow();
        assertEquals(2000L, stored.getDurationMs());
        assertEquals(10, stored.getDefectCount());
    }

    /**
     * Test 3: Different payload with older receivedTime should be ignored.
     */
    @Test
    void testDifferentPayloadOlderReceivedTimeIgnored() {
        Instant eventTime = Instant.now().minus(1, ChronoUnit.HOURS);

        // Store initial event with newer receivedTime simulation
        EventDTO event1 = createEventDTO("E-3", eventTime, "M-001", 1000L, 5);
        eventService.ingestBatch(Collections.singletonList(event1));

        // Get the stored event and manually set an older receivedTime for event2
        MachineEvent stored = repository.findByEventId("E-3").orElseThrow();
        Instant newerReceivedTime = stored.getReceivedTime();

        // Simulate an event that would have an older receivedTime by manipulating the test
        // In real scenario, this would come from a delayed sensor
        // For test purposes, we verify the logic by checking the service doesn't update
        // when we send a different payload now (which would have newer receivedTime)

        // Actually, let's test this properly by ingesting in correct order
        repository.deleteAll();

        // First: ingest with explicit older time (simulated)
        EventDTO oldEvent = createEventDTO("E-3", eventTime, "M-001", 1000L, 5);
        eventService.ingestBatch(Collections.singletonList(oldEvent));

        // Modify stored to have future receivedTime (simulating it came later)
        MachineEvent existing = repository.findByEventId("E-3").orElseThrow();
        existing.setReceivedTime(Instant.now().plus(1, ChronoUnit.HOURS));
        repository.save(existing);

        // Now send different payload - should be ignored as our current time is older
        EventDTO newEvent = createEventDTO("E-3", eventTime, "M-001", 2000L, 10);
        BatchIngestResponse response = eventService.ingestBatch(Collections.singletonList(newEvent));

        assertEquals(1, response.getDeduped());
        assertEquals(0, response.getUpdated());

        // Verify no update
        MachineEvent stillStored = repository.findByEventId("E-3").orElseThrow();
        assertEquals(1000L, stillStored.getDurationMs());
    }

    /**
     * Test 4: Invalid duration should be rejected.
     */
    @Test
    void testInvalidDurationRejected() {
        Instant now = Instant.now();

        // Negative duration
        EventDTO event1 = createEventDTO("E-4", now, "M-001", -100L, 0);

        // Duration > 6 hours
        EventDTO event2 = createEventDTO("E-5", now, "M-001", 7 * 60 * 60 * 1000L, 0);

        BatchIngestResponse response = eventService.ingestBatch(Arrays.asList(event1, event2));

        assertEquals(2, response.getRejected());
        assertEquals(2, response.getRejections().size());
        assertTrue(response.getRejections().stream()
                .anyMatch(r -> r.getReason().equals("INVALID_DURATION")));

        assertEquals(0, repository.count());
    }

    /**
     * Test 5: Future eventTime (> 15 min) should be rejected.
     */
    @Test
    void testFutureEventTimeRejected() {
        Instant farFuture = Instant.now().plus(20, ChronoUnit.MINUTES);

        EventDTO event = createEventDTO("E-6", farFuture, "M-001", 1000L, 0);
        BatchIngestResponse response = eventService.ingestBatch(Collections.singletonList(event));

        assertEquals(1, response.getRejected());
        assertEquals("FUTURE_EVENT_TIME", response.getRejections().get(0).getReason());
        assertEquals(0, repository.count());
    }

    /**
     * Test 6: DefectCount = -1 should be stored but ignored in defect calculations.
     */
    @Test
    void testDefectCountNegativeOneIgnoredInCalculations() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        EventDTO event1 = createEventDTO("E-7", start.plus(10, ChronoUnit.MINUTES), "M-001", 1000L, 5);
        EventDTO event2 = createEventDTO("E-8", start.plus(20, ChronoUnit.MINUTES), "M-001", 1000L, -1);
        EventDTO event3 = createEventDTO("E-9", start.plus(30, ChronoUnit.MINUTES), "M-001", 1000L, 3);

        eventService.ingestBatch(Arrays.asList(event1, event2, event3));

        StatsResponse stats = eventService.getStats("M-001", start, end);

        assertEquals(3, stats.getEventsCount());
        assertEquals(8, stats.getDefectsCount()); // 5 + 3, ignoring -1
        assertEquals(8.0, stats.getAvgDefectRate()); // 8 defects / 1 hour
    }

    /**
     * Test 7: Start/end boundary correctness (inclusive/exclusive).
     */
    @Test
    void testStartEndBoundaryCorrectness() {
        Instant start = Instant.parse("2026-01-15T10:00:00Z");
        Instant end = Instant.parse("2026-01-15T12:00:00Z");

        // Event exactly at start - should be included
        EventDTO event1 = createEventDTO("E-10", start, "M-001", 1000L, 1);

        // Event exactly at end - should be excluded
        EventDTO event2 = createEventDTO("E-11", end, "M-001", 1000L, 1);

        // Event between start and end - should be included
        EventDTO event3 = createEventDTO("E-12", start.plus(30, ChronoUnit.MINUTES), "M-001", 1000L, 1);

        eventService.ingestBatch(Arrays.asList(event1, event2, event3));

        StatsResponse stats = eventService.getStats("M-001", start, end);

        assertEquals(2, stats.getEventsCount()); // Only event1 and event3
        assertEquals(2, stats.getDefectsCount());
    }

    /**
     * Test 8: Thread-safety - concurrent ingestion doesn't corrupt data.
     */
    @Test
    void testConcurrentIngestionThreadSafety() throws InterruptedException, ExecutionException {
        int numThreads = 10;
        int eventsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Instant baseTime = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Future<BatchIngestResponse>> futures = new ArrayList<>();

        // Submit concurrent batches
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Future<BatchIngestResponse> future = executor.submit(() -> {
                List<EventDTO> events = new ArrayList<>();
                for (int i = 0; i < eventsPerThread; i++) {
                    String eventId = String.format("E-T%d-I%d", threadId, i);
                    events.add(createEventDTO(
                            eventId,
                            baseTime.plus(i, ChronoUnit.SECONDS),
                            "M-" + threadId,
                            1000L,
                            i % 10
                    ));
                }
                return eventService.ingestBatch(events);
            });
            futures.add(future);
        }

        // Wait for all to complete
        int totalAccepted = 0;
        for (Future<BatchIngestResponse> future : futures) {
            BatchIngestResponse response = future.get();
            totalAccepted += response.getAccepted();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify all events were stored correctly
        assertEquals(numThreads * eventsPerThread, totalAccepted);
        assertEquals(numThreads * eventsPerThread, repository.count());

        // Test concurrent updates to same event
        ExecutorService updateExecutor = Executors.newFixedThreadPool(5);
        List<Future<BatchIngestResponse>> updateFutures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int updateValue = i;
            Future<BatchIngestResponse> future = updateExecutor.submit(() -> {
                EventDTO event = createEventDTO(
                        "E-CONCURRENT",
                        baseTime,
                        "M-001",
                        1000L + updateValue,
                        updateValue
                );
                return eventService.ingestBatch(Collections.singletonList(event));
            });
            updateFutures.add(future);
        }

        for (Future<BatchIngestResponse> future : updateFutures) {
            future.get();
        }

        updateExecutor.shutdown();
        updateExecutor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify event exists and has one of the valid states
        MachineEvent concurrentEvent = repository.findByEventId("E-CONCURRENT").orElseThrow();
        assertNotNull(concurrentEvent);
        assertTrue(concurrentEvent.getDurationMs() >= 1000L && concurrentEvent.getDurationMs() <= 1004L);
    }

    // Helper method to create EventDTO
    private EventDTO createEventDTO(String eventId, Instant eventTime, String machineId,
                                    Long durationMs, Integer defectCount) {
        EventDTO dto = new EventDTO();
        dto.setEventId(eventId);
        dto.setEventTime(eventTime);
        dto.setMachineId(machineId);
        dto.setDurationMs(durationMs);
        dto.setDefectCount(defectCount);
        dto.setLineId("L-001");
        dto.setFactoryId("F-01");
        return dto;
    }
}