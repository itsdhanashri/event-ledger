package com.github.itsdhanashri.eventledger.accountservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.github.itsdhanashri.eventledger.accountservice.dto.request.TransactionRequest;
import com.github.itsdhanashri.eventledger.accountservice.model.Account;
import com.github.itsdhanashri.eventledger.accountservice.model.Transaction;
import com.github.itsdhanashri.eventledger.accountservice.model.TransactionType;
import com.github.itsdhanashri.eventledger.accountservice.repository.AccountRepository;
import com.github.itsdhanashri.eventledger.accountservice.repository.TransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Test
    void appliesTransactionAndRecomputesBalanceFromHistory() {
        Account account = account("acct-123", BigDecimal.ZERO);
        Transaction debit = transaction("evt-000", TransactionType.DEBIT, "25.00");
        Transaction credit = transaction("evt-001", TransactionType.CREDIT, "150.00");
        when(transactionRepository.findByEventId("evt-001")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountId("acct-123")).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(credit);
        when(transactionRepository.findByAccountId("acct-123")).thenReturn(List.of(debit, credit));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountService service = new AccountService(accountRepository, transactionRepository, new SimpleMeterRegistry());
        var result = service.applyTransaction("acct-123", request("evt-001", "CREDIT", "150.00"), "trace-1");

        assertThat(result.duplicate()).isFalse();
        assertThat(result.response().newBalance()).isEqualByComparingTo("125.00");
        ArgumentCaptor<Account> savedAccount = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(savedAccount.capture());
        assertThat(savedAccount.getValue().getBalance()).isEqualByComparingTo("125.00");
    }

    @Test
    void duplicateTransactionReturnsExistingWithoutSaving() {
        Account account = account("acct-123", new BigDecimal("150.00"));
        Transaction existing = transaction("evt-001", TransactionType.CREDIT, "150.00");
        when(transactionRepository.findByEventId("evt-001")).thenReturn(Optional.of(existing));
        when(accountRepository.findByAccountId("acct-123")).thenReturn(Optional.of(account));

        AccountService service = new AccountService(accountRepository, transactionRepository, new SimpleMeterRegistry());
        var result = service.applyTransaction("acct-123", request("evt-001", "CREDIT", "150.00"), "trace-1");

        assertThat(result.duplicate()).isTrue();
        assertThat(result.response().newBalance()).isEqualByComparingTo("150.00");
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(accountRepository, never()).save(any(Account.class));
    }

    private TransactionRequest request(String eventId, String type, String amount) {
        return new TransactionRequest(eventId, type, new BigDecimal(amount), "USD", Instant.parse("2026-05-15T14:02:11Z"));
    }

    private Account account(String accountId, BigDecimal balance) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setCurrency("USD");
        account.setBalance(balance);
        return account;
    }

    private Transaction transaction(String eventId, TransactionType type, String amount) {
        Transaction transaction = new Transaction();
        transaction.setEventId(eventId);
        transaction.setAccountId("acct-123");
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCurrency("USD");
        transaction.setEventTimestamp(Instant.parse("2026-05-15T14:02:11Z"));
        transaction.setAppliedAt(Instant.parse("2026-05-15T14:03:11Z"));
        return transaction;
    }
}
