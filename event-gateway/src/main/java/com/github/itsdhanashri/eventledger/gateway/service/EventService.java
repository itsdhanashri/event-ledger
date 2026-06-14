package com.github.itsdhanashri.eventledger.gateway.service;

import java.time.Instant;
import java.util.List;

import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import com.github.itsdhanashri.eventledger.gateway.dto.response.EventResponse;
import com.github.itsdhanashri.eventledger.gateway.exception.AccountServiceException;
import com.github.itsdhanashri.eventledger.gateway.exception.EventNotFoundException;
import com.github.itsdhanashri.eventledger.gateway.model.Event;
import com.github.itsdhanashri.eventledger.gateway.model.EventStatus;
import com.github.itsdhanashri.eventledger.gateway.model.EventType;
import com.github.itsdhanashri.eventledger.gateway.repository.EventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final MeterRegistry meterRegistry;

    public EventService(EventRepository eventRepository,
                        AccountServiceClient accountServiceClient,
                        MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.meterRegistry = meterRegistry;
    }

    public SubmissionResult submitEvent(EventRequest request, String traceId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        var existing = eventRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate event received: eventId={}, accountId={}", request.eventId(), request.accountId());
            meterRegistry.counter("events_submitted_total", "status", "duplicate").increment();
            meterRegistry.counter("duplicate_events_total", "service", "event-gateway").increment();
            stopTimer(sample, request.type());
            return new SubmissionResult(toResponse(existing.get()), true);
        }

        Event event = buildEvent(request, traceId);
        try {
            event = eventRepository.save(event);
        } catch (DuplicateKeyException ex) {
            Event duplicate = eventRepository.findByEventId(request.eventId()).orElseThrow(() -> ex);
            meterRegistry.counter("events_submitted_total", "status", "duplicate").increment();
            meterRegistry.counter("duplicate_events_total", "service", "event-gateway").increment();
            stopTimer(sample, request.type());
            return new SubmissionResult(toResponse(duplicate), true);
        }

        try {
            accountServiceClient.applyTransaction(request, traceId);
            event.setStatus(EventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event = eventRepository.save(event);
            meterRegistry.counter("events_submitted_total", "status", "processed").increment();
            log.info("Event processed successfully: eventId={}, accountId={}", request.eventId(), request.accountId());
            return new SubmissionResult(toResponse(event), false);
        } catch (AccountServiceException ex) {
            event.setStatus(EventStatus.FAILED);
            eventRepository.save(event);
            meterRegistry.counter("events_submitted_total", "status", "failed").increment();
            throw ex;
        } finally {
            stopTimer(sample, request.type());
        }
    }

    public EventResponse getEvent(String eventId) {
        return eventRepository.findByEventId(eventId)
                .map(this::toResponse)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public List<EventResponse> listEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Event buildEvent(EventRequest request, String traceId) {
        Event event = new Event();
        event.setEventId(request.eventId());
        event.setAccountId(request.accountId());
        event.setType(EventType.from(request.type()));
        event.setAmount(request.amount());
        event.setCurrency(request.currency().toUpperCase());
        event.setEventTimestamp(request.eventTimestamp());
        event.setMetadata(request.metadata());
        event.setStatus(EventStatus.PENDING);
        event.setReceivedAt(Instant.now());
        event.setTraceId(traceId);
        return event;
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(event.getEventId(), event.getAccountId(), event.getType(), event.getAmount(),
                event.getCurrency(), event.getEventTimestamp(), event.getStatus(), event.getReceivedAt(),
                event.getProcessedAt(), event.getTraceId(), event.getMetadata());
    }

    private void stopTimer(Timer.Sample sample, String type) {
        sample.stop(Timer.builder("events_processing_duration_seconds")
                .tag("type", type == null ? "unknown" : type.toLowerCase())
                .publishPercentileHistogram()
                .register(meterRegistry));
    }
}
