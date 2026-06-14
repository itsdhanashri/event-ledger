package com.github.itsdhanashri.eventledger.gateway.model;

public enum EventType {
    CREDIT,
    DEBIT;

    public static EventType from(String value) {
        return EventType.valueOf(value.toUpperCase());
    }
}
