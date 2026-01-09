package com.factory.events.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class EventDTO {
    
    @JsonProperty("eventId")
    private String eventId;
    
    @JsonProperty("eventTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant eventTime;
    
    @JsonProperty("receivedTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, timezone = "UTC")
    private Instant receivedTime;
    
    @JsonProperty("machineId")
    private String machineId;
    
    @JsonProperty("durationMs")
    private Long durationMs;
    
    @JsonProperty("defectCount")
    private Integer defectCount;
    
    @JsonProperty("lineId")
    private String lineId;
    
    @JsonProperty("factoryId")
    private String factoryId;
    
    
    public EventDTO() {}
    
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
    
 
    public String generatePayloadHash() {
        try {
            String payload = String.format("%s|%s|%s|%d|%d|%s|%s",
                eventId, eventTime, machineId, durationMs, defectCount,
                lineId != null ? lineId : "", factoryId != null ? factoryId : "");
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate payload hash", e);
        }
    }
}