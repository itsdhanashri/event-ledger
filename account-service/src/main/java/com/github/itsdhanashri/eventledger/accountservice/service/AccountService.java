package com.github.itsdhanashri.eventledger.accountservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import com.github.itsdhanashri.eventledger.accountservice.dto.request.TransactionRequest;
import com.github.itsdhanashri.eventledger.accountservice.dto.response.AccountResponse;
import com.github.itsdhanashri.eventledger.accountservice.dto.response.BalanceResponse;
import com.github.itsdhanashri.eventledger.accountservice.dto.response.TransactionResponse;
import com.github.itsdhanashri.eventledger.accountservice.exception.AccountNotFoundException;
import com.github.itsdhanashri.eventledger.accountservice.model.Account;
import com.github.itsdhanashri.eventledger.accountservice.model.Transaction;
import com.github.itsdhanashri.eventledger.accountservice.model.TransactionType;
import com.github.itsdhanashri.eventledger.accountservice.repository.AccountRepository;
import com.github.itsdhanashri.eventledger.accountservice.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.meterRegistry = meterRegistry;
    }

    public AppliedTransactionResult applyTransaction(String accountId, TransactionRequest request, String traceId) {
        var existing = transactionRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate transaction received: eventId={}, accountId={}", request.eventId(), accountId);
            incrementDuplicateCounter();
            Account account = accountRepository.findByAccountId(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            return new AppliedTransactionResult(toTransactionResponse(existing.get(), account.getBalance(), traceId), true);
        }

        Account account = accountRepository.findByAccountId(accountId)
                .orElseGet(() -> newAccount(accountId, request.currency()));

        Transaction transaction = new Transaction();
        transaction.setEventId(request.eventId());
        transaction.setAccountId(accountId);
        transaction.setType(TransactionType.from(request.type()));
        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency().toUpperCase());
        transaction.setEventTimestamp(request.eventTimestamp());
        transaction.setAppliedAt(Instant.now());
        transaction.setTraceId(traceId);

        try {
            transaction = transactionRepository.save(transaction);
        } catch (DuplicateKeyException ex) {
            Transaction duplicate = transactionRepository.findByEventId(request.eventId()).orElseThrow(() -> ex);
            Account savedAccount = accountRepository.findByAccountId(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            incrementDuplicateCounter();
            return new AppliedTransactionResult(toTransactionResponse(duplicate, savedAccount.getBalance(), traceId), true);
        }

        BigDecimal balance = computeBalance(accountId);
        account.setBalance(balance);
        account.setCurrency(request.currency().toUpperCase());
        account = accountRepository.save(account);

        meterRegistry.counter("transactions_applied_total", "type", transaction.getType().name().toLowerCase()).increment();
        log.info("Transaction applied: eventId={}, accountId={}, balance={}", request.eventId(), accountId, balance);
        return new AppliedTransactionResult(toTransactionResponse(transaction, account.getBalance(), traceId), false);
    }

    public BalanceResponse getBalance(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        long transactionCount = transactionRepository.findByAccountId(accountId).size();
        return new BalanceResponse(account.getAccountId(), account.getBalance(), account.getCurrency(), transactionCount,
                account.getUpdatedAt());
    }

    public AccountResponse getAccount(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        List<AccountResponse.RecentTransactionResponse> recentTransactions = transactionRepository
                .findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .sorted(Comparator.comparing(Transaction::getEventTimestamp).reversed())
                .limit(20)
                .sorted(Comparator.comparing(Transaction::getEventTimestamp))
                .map(transaction -> new AccountResponse.RecentTransactionResponse(
                        transaction.getEventId(), transaction.getType(), transaction.getAmount(), transaction.getEventTimestamp()))
                .toList();
        return new AccountResponse(account.getAccountId(), account.getBalance(), account.getCurrency(), account.getCreatedAt(),
                account.getUpdatedAt(), recentTransactions);
    }

    public BigDecimal computeBalance(String accountId) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction transaction : transactionRepository.findByAccountId(accountId)) {
            if (transaction.getType() == TransactionType.CREDIT) {
                balance = balance.add(transaction.getAmount());
            } else {
                balance = balance.subtract(transaction.getAmount());
            }
        }
        return balance;
    }

    private Account newAccount(String accountId, String currency) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setCurrency(currency.toUpperCase());
        account.setBalance(BigDecimal.ZERO);
        return account;
    }

    private TransactionResponse toTransactionResponse(Transaction transaction, BigDecimal balance, String traceId) {
        return new TransactionResponse(transaction.getEventId(), transaction.getAccountId(), transaction.getType(),
                transaction.getAmount(), transaction.getCurrency(), transaction.getEventTimestamp(), balance,
                transaction.getAppliedAt(), traceId);
    }

    private void incrementDuplicateCounter() {
        Counter.builder("duplicate_events_total")
                .tag("service", "account-service")
                .register(meterRegistry)
                .increment();
    }
}
