package com.akamai.miniwsa.samples;

import com.akamai.miniwsa.api.error.ErrorCode;
import com.akamai.miniwsa.api.error.InvalidRequestException;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.SamplesPage;
import com.akamai.miniwsa.storage.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SamplesService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final EventRepository repository;

    public SamplesService(EventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SamplesPage samples(EventFilter filter, int limit, int offset) {
        if (limit < 1) {
            throw new InvalidRequestException("limit", ErrorCode.OUT_OF_RANGE, "must be at least 1");
        }
        if (offset < 0) {
            throw new InvalidRequestException("offset", ErrorCode.OUT_OF_RANGE, "must not be negative");
        }
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("from", ErrorCode.OUT_OF_RANGE, "'from' must not be after 'to'");
        }
        // a larger limit is capped, not rejected; the response reports the limit actually applied
        return repository.samples(filter, Math.min(limit, MAX_LIMIT), offset);
    }
}
