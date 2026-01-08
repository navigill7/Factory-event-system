package com.factory.events.benchmark;

import com.factory.events.dto.BatchIngestResponse;
import com.factory.events.dto.EventDTO;
import com.factory.events.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;


@SpringBootApplication
@ComponentScan(basePackages = "com.factory.events")
@Profile("benchmark")
public class PerformanceBenchmark implements CommandLineRunner {

    @Autowired
    private EventService eventService;

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "benchmark");
        SpringApplication.run(PerformanceBenchmark.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("=== Factory Event System Performance Benchmark ===");
        System.out.println();

        // Warm-up run
        System.out.println("Warming up...");
        runBenchmark(100, false);

        // Actual benchmark runs
        System.out.println("\n=== Running Benchmark: 1000 Events ===");
        for (int run = 1; run <= 3; run++) {
            System.out.println("\nRun " + run + ":");
            runBenchmark(1000, true);
        }

        System.out.println("\n=== Benchmark Complete ===");
        System.exit(0);
    }

    private void runBenchmark(int eventCount, boolean printResults) {
        List<EventDTO> events = generateEvents(eventCount);

        long startTime = System.nanoTime();
        BatchIngestResponse response = eventService.ingestBatch(events);
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;

        if (printResults) {
            System.out.println("  Events: " + eventCount);
            System.out.println("  Duration: " + durationMs + " ms");
            System.out.println("  Throughput: " + (eventCount * 1000.0 / durationMs) + " events/sec");
            System.out.println("  Accepted: " + response.getAccepted());
            System.out.println("  Rejected: " + response.getRejected());
            System.out.println("  Result: " + (durationMs < 1000 ? "✓ PASS" : "✗ FAIL") +
                    " (Target: < 1000ms)");
        }
    }

    private List<EventDTO> generateEvents(int count) {
        List<EventDTO> events = new ArrayList<>();
        Instant baseTime = Instant.now().minus(1, ChronoUnit.HOURS);

        for (int i = 0; i < count; i++) {
            EventDTO event = new EventDTO();
            event.setEventId("E-BENCH-" + i);
            event.setEventTime(baseTime.plus(i, ChronoUnit.SECONDS));
            event.setMachineId("M-" + (i % 10));
            event.setDurationMs(1000L + (i % 5000));
            event.setDefectCount(i % 20);
            event.setLineId("L-" + (i % 5));
            event.setFactoryId("F-01");
            events.add(event);
        }

        return events;
    }
}