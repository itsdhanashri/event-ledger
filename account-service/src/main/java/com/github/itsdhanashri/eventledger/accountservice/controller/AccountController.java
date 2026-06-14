package com.github.itsdhanashri.eventledger.accountservice.controller;

import com.github.itsdhanashri.eventledger.accountservice.dto.request.TransactionRequest;
import com.github.itsdhanashri.eventledger.accountservice.dto.response.AccountResponse;
import com.github.itsdhanashri.eventledger.accountservice.dto.response.BalanceResponse;
import com.github.itsdhanashri.eventledger.accountservice.dto.response.TransactionResponse;
import com.github.itsdhanashri.eventledger.accountservice.service.AccountService;
import com.github.itsdhanashri.eventledger.accountservice.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(@PathVariable String accountId,
                                                                @Valid @RequestBody TransactionRequest request,
                                                                HttpServletRequest servletRequest) {
        String traceId = (String) servletRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        var result = accountService.applyTransaction(accountId, request, traceId);
        return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(result.response());
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@PathVariable String accountId) {
        return accountService.getAccount(accountId);
    }
}
