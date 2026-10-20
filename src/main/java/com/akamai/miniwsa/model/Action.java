package com.akamai.miniwsa.model;

public enum Action {
    DENY(20), ALERT(10), MONITOR(0);

    private final int score;

    Action(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }
}
