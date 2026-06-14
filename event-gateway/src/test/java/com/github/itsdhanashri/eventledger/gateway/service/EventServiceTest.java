package com.github.itsdhanashri.eventledger.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import com.github.itsdhanashri.eventledger.gateway.exception.AccountServiceException;
import com.github.itsdhanashri.eventledger.gateway.model.Event;
import com.github.itsdhanashri.eventledger.gateway.model.EventStatus;
import com.github.itsdhanashri.eventledger.gateway.model.EventType;
import com.github.itsdhanashri.eventledger.gateway.repository.EventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AccountServiceClient accountServiceClient;

    @Test
    void duplicateEventReturnsStoredEventWithoutCallingAccountService() {
        Event existing = event(EventStatus.PROCESSED);
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.of(existing));

        EventService service = new EventService(eventRepository, accountServiceClient, new SimpleMeterRegistry());
        SubmissionResult result = service.submitEvent(request("evt-001"), "trace-1");

        assertThat(result.duplicate()).isTrue();
        assertThat(result.response().status()).isEqualTo(EventStatus.PROCESSED);
        verify(accountServiceClient, never()).applyTransaction(any(), any());
    }

    @Test
    void accountServiceFailureMarksEventFailedAndPropagates503Exception() {
        when(eventRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountServiceClient.applyTransaction(any(), any()))
                .thenThrow(new AccountServiceException("Account Service unavailable"));

        EventService service = new EventService(eventRepository, accountServiceClient, new SimpleMeterRegistry());

        assertThatThrownBy(() -> service.submitEvent(request("evt-001"), "trace-1"))
                .isInstanceOf(AccountServiceException.class);
        ArgumentCaptor<Event> savedEvents = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, org.mockito.Mockito.atLeast(2)).save(savedEvents.capture());
        assertThat(savedEvents.getAllValues().getLast().getStatus()).isEqualTo(EventStatus.FAILED);
    }

    private EventRequest request(String eventId) {
        return new EventRequest(eventId, "acct-123", "CREDIT", new BigDecimal("150.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);
    }

    private Event event(EventStatus status) {
        Event event = new Event();
        event.setEventId("evt-001");
        event.setAccountId("acct-123");
        event.setType(EventType.CREDIT);
        event.setAmount(new BigDecimal("150.00"));
        event.setCurrency("USD");
        event.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));
        event.setReceivedAt(Instant.parse("2026-05-15T14:03:00Z"));
        event.setProcessedAt(Instant.parse("2026-05-15T14:03:01Z"));
        event.setTraceId("trace-1");
        event.setStatus(status);
        return event;
    }
}
