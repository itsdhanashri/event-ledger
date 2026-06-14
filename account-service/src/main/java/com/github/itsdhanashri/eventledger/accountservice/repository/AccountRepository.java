package com.github.itsdhanashri.eventledger.accountservice.repository;

import java.util.Optional;

import com.github.itsdhanashri.eventledger.accountservice.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AccountRepository extends MongoRepository<Account, String> {
    Optional<Account> findByAccountId(String accountId);
}
