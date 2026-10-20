package com.akamai.miniwsa.api;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.SamplesPage;
import com.akamai.miniwsa.samples.SamplesService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/events")
public class SamplesController {

    private final SamplesService samplesService;

    public SamplesController(SamplesService samplesService) {
        this.samplesService = samplesService;
    }

    /** Matching events, newest first. All filters are optional; {@code from} is inclusive, {@code to} exclusive. */
    @GetMapping("/samples")
    public SamplesPage samples(
            @RequestParam(required = false) Long configId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Action action,
            @RequestParam(defaultValue = "" + SamplesService.DEFAULT_LIMIT) int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return samplesService.samples(new EventFilter(configId, from, to, category, action), limit, offset);
    }
}
