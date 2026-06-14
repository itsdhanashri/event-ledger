package com.github.itsdhanashri.eventledger.gateway.repository;

import java.util.List;
import java.util.Optional;

import com.github.itsdhanashri.eventledger.gateway.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<Event, String> {
    Optional<Event> findByEventId(String eventId);

    List<Event> findByAccountIdOrderByEventTimestampAsc(String accountId);

    boolean existsByEventId(String eventId);
}
