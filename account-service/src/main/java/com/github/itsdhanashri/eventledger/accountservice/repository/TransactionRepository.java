package com.github.itsdhanashri.eventledger.accountservice.repository;

import java.util.List;
import java.util.Optional;

import com.github.itsdhanashri.eventledger.accountservice.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransactionRepository extends MongoRepository<Transaction, String> {
    Optional<Transaction> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<Transaction> findByAccountId(String accountId);

    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
