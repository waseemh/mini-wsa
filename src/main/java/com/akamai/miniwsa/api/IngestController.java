package com.akamai.miniwsa.api;

import com.akamai.miniwsa.ingest.IngestService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/events")
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** Accepts a single event or an array of events. */
    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestService.Result ingest(@RequestBody JsonNode body) {
        return ingestService.ingest(body);
    }
}
