package com.factory.events.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "machine_events", indexes = {
        @Index(name = "idx_event_id", columnList = "eventId", unique = true),
        @Index(name = "idx_machine_time", columnList = "machineId,eventTime"),
        @Index(name = "idx_line_time", columnList = "lineId,eventTime")
})
public class MachineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private Instant eventTime;

    @Column(nullable = false)
    private Instant receivedTime;

    @Column(nullable = false)
    private String machineId;

    @Column(nullable = false)
    private Long durationMs;

    @Column(nullable = false)
    private Integer defectCount;

    @Column
    private String lineId;

    @Column
    private String factoryId;

    @Version
    private Long version;

    
    @Column(nullable = false)
    private String payloadHash;

    
    public MachineEvent() {}

    public MachineEvent(String eventId, Instant eventTime, Instant receivedTime,
                        String machineId, Long durationMs, Integer defectCount,
                        String lineId, String factoryId, String payloadHash) {
        this.eventId = eventId;
        this.eventTime = eventTime;
        this.receivedTime = receivedTime;
        this.machineId = machineId;
        this.durationMs = durationMs;
        this.defectCount = defectCount;
        this.lineId = lineId;
        this.factoryId = factoryId;
        this.payloadHash = payloadHash;
    }

    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public Instant getReceivedTime() { return receivedTime; }
    public void setReceivedTime(Instant receivedTime) { this.receivedTime = receivedTime; }

    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getDefectCount() { return defectCount; }
    public void setDefectCount(Integer defectCount) { this.defectCount = defectCount; }

    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }

    public String getFactoryId() { return factoryId; }
    public void setFactoryId(String factoryId) { this.factoryId = factoryId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MachineEvent that = (MachineEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}