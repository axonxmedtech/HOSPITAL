package com.hms.dto;

import java.time.LocalDate;

public class CalendarEventRequest {
    private String title;
    private String eventType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String description;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
