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
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private MachineEventRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

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

    @Test
    void testDifferentPayloadNewerReceivedTimeUpdates() throws InterruptedException {
        Instant eventTime = Instant.now().minus(1, ChronoUnit.HOURS);

        EventDTO event1 = createEventDTO("E-2", eventTime, "M-001", 1000L, 5);
        eventService.ingestBatch(Collections.singletonList(event1));

        Thread.sleep(10);

        EventDTO event2 = createEventDTO("E-2", eventTime, "M-001", 2000L, 10);
        BatchIngestResponse response = eventService.ingestBatch(Collections.singletonList(event2));

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getUpdated());
        assertEquals(0, response.getDeduped());

        MachineEvent stored = repository.findByEventId("E-2").orElseThrow();
        assertEquals(2000L, stored.getDurationMs());
        assertEquals(10, stored.getDefectCount());
    }

    @Test
    void testDifferentPayloadOlderReceivedTimeIgnored() {
        Instant eventTime = Instant.now().minus(1, ChronoUnit.HOURS);

        EventDTO event1 = createEventDTO("E-3", eventTime, "M-001", 1000L, 5);
        eventService.ingestBatch(Collections.singletonList(event1));

        MachineEvent stored = repository.findByEventId("E-3").orElseThrow();
        Instant newerReceivedTime = stored.getReceivedTime();

        repository.deleteAll();

        EventDTO oldEvent = createEventDTO("E-3", eventTime, "M-001", 1000L, 5);
        eventService.ingestBatch(Collections.singletonList(oldEvent));

        MachineEvent existing = repository.findByEventId("E-3").orElseThrow();
        existing.setReceivedTime(Instant.now().plus(1, ChronoUnit.HOURS));
        repository.save(existing);

        EventDTO newEvent = createEventDTO("E-3", eventTime, "M-001", 2000L, 10);
        BatchIngestResponse response = eventService.ingestBatch(Collections.singletonList(newEvent));

        assertEquals(1, response.getDeduped());
        assertEquals(0, response.getUpdated());

        MachineEvent stillStored = repository.findByEventId("E-3").orElseThrow();
        assertEquals(1000L, stillStored.getDurationMs());
    }

    @Test
    void testInvalidDurationRejected() {
        Instant now = Instant.now();

        EventDTO event1 = createEventDTO("E-4", now, "M-001", -100L, 0);

        EventDTO event2 = createEventDTO("E-5", now, "M-001", 7 * 60 * 60 * 1000L, 0);

        BatchIngestResponse response = eventService.ingestBatch(Arrays.asList(event1, event2));

        assertEquals(2, response.getRejected());
        assertEquals(2, response.getRejections().size());
        assertTrue(response.getRejections().stream()
                .anyMatch(r -> r.getReason().equals("INVALID_DURATION")));

        assertEquals(0, repository.count());
    }

    @Test
    void testFutureEventTimeRejected() {
        Instant farFuture = Instant.now().plus(20, ChronoUnit.MINUTES);

        EventDTO event = createEventDTO("E-6", farFuture, "M-001", 1000L, 0);
        BatchIngestResponse response = eventService.ingestBatch(Collections.singletonList(event));

        assertEquals(1, response.getRejected());
        assertEquals("FUTURE_EVENT_TIME", response.getRejections().get(0).getReason());
        assertEquals(0, repository.count());
    }

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
        assertEquals(8, stats.getDefectsCount());
        assertEquals(8.0, stats.getAvgDefectRate());
    }

    @Test
    void testStartEndBoundaryCorrectnessDebug() {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        EventDTO event1 = createEventDTO("E-10", start, "M-001", 1000L, 1);

        EventDTO event2 = createEventDTO("E-11", end, "M-001", 1000L, 1);

        EventDTO event3 = createEventDTO("E-12", start.plus(30, ChronoUnit.MINUTES), "M-001", 1000L, 1);

        BatchIngestResponse batchResponse = eventService.ingestBatch(Arrays.asList(event1, event2, event3));
        
        assertEquals(3, batchResponse.getAccepted(), "All 3 events should be accepted");
        assertEquals(0, batchResponse.getRejected(), "No events should be rejected");
        
        assertEquals(3, repository.count(), "3 events should be in database");

        StatsResponse stats = eventService.getStats("M-001", start, end);

        assertEquals(2, stats.getEventsCount());
        assertEquals(2, stats.getDefectsCount());
    }

    @Test
    void testConcurrentIngestionThreadSafety() throws InterruptedException, ExecutionException {
        int numThreads = 10;
        int eventsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Instant baseTime = Instant.now().minus(1, ChronoUnit.HOURS);
        List<Future<BatchIngestResponse>> futures = new ArrayList<>();

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

        int totalAccepted = 0;
        for (Future<BatchIngestResponse> future : futures) {
            BatchIngestResponse response = future.get();
            totalAccepted += response.getAccepted();
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(numThreads * eventsPerThread, totalAccepted);
        assertEquals(numThreads * eventsPerThread, repository.count());

        repository.deleteAll();
        
        EventDTO initialEvent = createEventDTO(
                "E-CONCURRENT",
                baseTime,
                "M-001",
                1000L,
                0
        );
        eventService.ingestBatch(Collections.singletonList(initialEvent));
        
        ExecutorService updateExecutor = Executors.newFixedThreadPool(5);
        List<Future<BatchIngestResponse>> updateFutures = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            final int updateValue = i;
            Future<BatchIngestResponse> future = updateExecutor.submit(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 10));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
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

        int totalUpdates = 0;
        int totalDedupes = 0;
        for (Future<BatchIngestResponse> future : updateFutures) {
            BatchIngestResponse response = future.get();
            totalUpdates += response.getUpdated();
            totalDedupes += response.getDeduped();
        }

        updateExecutor.shutdown();
        updateExecutor.awaitTermination(10, TimeUnit.SECONDS);

        MachineEvent concurrentEvent = repository.findByEventId("E-CONCURRENT").orElseThrow();
        assertNotNull(concurrentEvent);
        assertTrue(concurrentEvent.getDurationMs() >= 1000L && concurrentEvent.getDurationMs() <= 1004L);
        
        assertEquals(5, totalUpdates + totalDedupes);
    }

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