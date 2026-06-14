package com.github.itsdhanashri.eventledger.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import com.github.itsdhanashri.eventledger.gateway.dto.response.EventResponse;
import com.github.itsdhanashri.eventledger.gateway.exception.EventNotFoundException;
import com.github.itsdhanashri.eventledger.gateway.model.EventStatus;
import com.github.itsdhanashri.eventledger.gateway.model.EventType;
import com.github.itsdhanashri.eventledger.gateway.service.EventService;
import com.github.itsdhanashri.eventledger.gateway.service.SubmissionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EventService eventService;

    @Test
    void postEvents_returns201_forNewEvent() throws Exception {
        EventRequest req = new EventRequest("evt-01", "acct-1", "CREDIT", new BigDecimal("10.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);
        EventResponse resp = new EventResponse("evt-01", "acct-1", EventType.CREDIT, new BigDecimal("10.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), EventStatus.PROCESSED, Instant.now(), Instant.now(), "trace-1", null);
        when(eventService.submitEvent(any(EventRequest.class), any())).thenReturn(new SubmissionResult(resp, false));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-01"));
    }

    @Test
    void postEvents_returns200_forDuplicate() throws Exception {
        EventRequest req = new EventRequest("evt-02", "acct-2", "DEBIT", new BigDecimal("5.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);
        EventResponse resp = new EventResponse("evt-02", "acct-2", EventType.DEBIT, new BigDecimal("5.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), EventStatus.PROCESSED, Instant.now(), Instant.now(), "trace-2", null);
        when(eventService.submitEvent(any(EventRequest.class), any())).thenReturn(new SubmissionResult(resp, true));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-02"));
    }

    @Test
    void postEvents_returns400_forInvalidInput() throws Exception {
        // missing accountId
        String body = "{" +
                "\"eventId\":\"bad-1\"," +
                "\"type\":\"CREDIT\"," +
                "\"amount\": 1.00," +
                "\"currency\": \"USD\"," +
                "\"eventTimestamp\": \"2026-05-15T14:02:11Z\"}";

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void getEvent_returns200_and404() throws Exception {
        EventResponse resp = new EventResponse("evt-03", "acct-3", EventType.CREDIT, new BigDecimal("20.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), EventStatus.PROCESSED, Instant.now(), Instant.now(), "trace-3", null);
        when(eventService.getEvent("evt-03")).thenReturn(resp);

        mockMvc.perform(get("/events/evt-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-03"));

        when(eventService.getEvent("missing")).thenThrow(new EventNotFoundException("missing"));

        mockMvc.perform(get("/events/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listEvents_byAccount_and_missingParam() throws Exception {
        EventResponse a = new EventResponse("e1", "acct-x", EventType.CREDIT, new BigDecimal("1.00"), "USD",
                Instant.parse("2026-05-15T10:00:00Z"), EventStatus.PROCESSED, Instant.now(), Instant.now(), "t1", null);
        EventResponse b = new EventResponse("e2", "acct-x", EventType.DEBIT, new BigDecimal("2.00"), "USD",
                Instant.parse("2026-05-15T11:00:00Z"), EventStatus.PROCESSED, Instant.now(), Instant.now(), "t2", null);
        when(eventService.listEventsByAccount("acct-x")).thenReturn(List.of(a, b));

        mockMvc.perform(get("/events").param("account", "acct-x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("e1"));

        // missing account param -> 400
        mockMvc.perform(get("/events"))
                .andExpect(status().isBadRequest());
    }
}

