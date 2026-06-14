package com.github.itsdhanashri.eventledger.gateway.controller;

import java.util.List;

import com.github.itsdhanashri.eventledger.gateway.dto.request.EventRequest;
import com.github.itsdhanashri.eventledger.gateway.dto.response.EventResponse;
import com.github.itsdhanashri.eventledger.gateway.service.EventService;
import com.github.itsdhanashri.eventledger.gateway.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventRequest request,
                                                     HttpServletRequest servletRequest) {
        String traceId = (String) servletRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        var result = eventService.submitEvent(request, traceId);
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result.response());
    }

    @GetMapping("/events/{eventId}")
    public EventResponse getEvent(@PathVariable String eventId) {
        return eventService.getEvent(eventId);
    }

    @GetMapping("/events")
    public List<EventResponse> listEvents(@RequestParam("account") @NotBlank(message = "account query parameter is required") String accountId) {
        return eventService.listEventsByAccount(accountId);
    }
}
