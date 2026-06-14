package com.github.itsdhanashri.eventledger.gateway.service;

import com.github.itsdhanashri.eventledger.gateway.dto.response.EventResponse;

public record SubmissionResult(EventResponse response, boolean duplicate) {
}
