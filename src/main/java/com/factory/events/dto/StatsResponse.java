package com.factory.events.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class StatsResponse {

    private String machineId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant start;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant end;

    private long eventsCount;
    private long defectsCount;
    private double avgDefectRate;
    private String status;

    public StatsResponse() {}

    public StatsResponse(String machineId, Instant start, Instant end,
                         long eventsCount, long defectsCount, double avgDefectRate, String status) {
        this.machineId = machineId;
        this.start = start;
        this.end = end;
        this.eventsCount = eventsCount;
        this.defectsCount = defectsCount;
        this.avgDefectRate = avgDefectRate;
        this.status = status;
    }

    
    public String getMachineId() { return machineId; }
    public void setMachineId(String machineId) { this.machineId = machineId; }

    public Instant getStart() { return start; }
    public void setStart(Instant start) { this.start = start; }

    public Instant getEnd() { return end; }
    public void setEnd(Instant end) { this.end = end; }

    public long getEventsCount() { return eventsCount; }
    public void setEventsCount(long eventsCount) { this.eventsCount = eventsCount; }

    public long getDefectsCount() { return defectsCount; }
    public void setDefectsCount(long defectsCount) { this.defectsCount = defectsCount; }

    public double getAvgDefectRate() { return avgDefectRate; }
    public void setAvgDefectRate(double avgDefectRate) { this.avgDefectRate = avgDefectRate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

