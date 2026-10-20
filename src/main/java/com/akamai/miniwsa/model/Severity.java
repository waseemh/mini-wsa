package com.akamai.miniwsa.model;

public enum Severity {
    CRITICAL(40), HIGH(30), MEDIUM(20), LOW(10);

    private final int score;

    Severity(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }
}
