package com.akamai.miniwsa.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /** Largest JSON body the API reads; bigger ones are cut off while parsing, so memory use per request is bounded. */
    public static final long MAX_BODY_BYTES = 8L * 1024 * 1024;

    @Bean
    Jackson2ObjectMapperBuilderCustomizer bodySizeLimit() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxDocumentLength(MAX_BODY_BYTES).build()));
    }
}
