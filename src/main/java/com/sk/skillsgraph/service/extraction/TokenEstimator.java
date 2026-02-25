package com.sk.skillsgraph.service.extraction;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return (int) Math.ceil(words.length * 1.3D);
    }
}
