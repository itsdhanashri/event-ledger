package com.github.itsdhanashri.eventledger.gateway.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
    }
}
