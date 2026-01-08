package com.factory.events.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchIngestResponse {

    private int accepted;
    private int deduped;
    private int updated;
    private int rejected;
    private List<Rejection> rejections;

    public BatchIngestResponse() {
        this.rejections = new ArrayList<>();
    }

    public static class Rejection {
        private String eventId;
        private String reason;

        public Rejection(String eventId, String reason) {
            this.eventId = eventId;
            this.reason = reason;
        }

        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    // Getters and Setters
    public int getAccepted() { return accepted; }
    public void setAccepted(int accepted) { this.accepted = accepted; }

    public int getDeduped() { return deduped; }
    public void setDeduped(int deduped) { this.deduped = deduped; }

    public int getUpdated() { return updated; }
    public void setUpdated(int updated) { this.updated = updated; }

    public int getRejected() { return rejected; }
    public void setRejected(int rejected) { this.rejected = rejected; }

    public List<Rejection> getRejections() { return rejections; }
    public void setRejections(List<Rejection> rejections) { this.rejections = rejections; }

    public void addRejection(String eventId, String reason) {
        this.rejections.add(new Rejection(eventId, reason));
    }
}