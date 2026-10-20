package com.akamai.miniwsa.api;

import com.akamai.miniwsa.ingest.IngestService;
import com.akamai.miniwsa.model.SecurityEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/events")
public class IngestController {

    public record IngestResponse(int accepted) {
    }

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    /** Accepts a single event or an array of events. */
    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResponse ingest(@RequestBody List<SecurityEvent> events) {
        return new IngestResponse(ingestService.ingest(events));
    }
}
