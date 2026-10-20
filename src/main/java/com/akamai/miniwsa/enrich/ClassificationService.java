package com.akamai.miniwsa.enrich;

import com.akamai.miniwsa.model.SecurityEvent;
import org.springframework.stereotype.Service;

@Service
public class ClassificationService {

    public String attackType(SecurityEvent event) {
        return event.rule().category().attackType();
    }
}
