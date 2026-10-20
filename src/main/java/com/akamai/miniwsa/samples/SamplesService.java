package com.akamai.miniwsa.samples;

import com.akamai.miniwsa.api.error.InvalidRequestException;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.SamplesPage;
import com.akamai.miniwsa.storage.EventRepository;
import org.springframework.stereotype.Service;

@Service
public class SamplesService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final EventRepository repository;

    public SamplesService(EventRepository repository) {
        this.repository = repository;
    }

    public SamplesPage samples(EventFilter filter, int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit", "must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new InvalidRequestException("offset", "must not be negative");
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("from", "'from' must not be after 'to'");
        }
        return repository.samples(filter, limit, offset);
    }
}
