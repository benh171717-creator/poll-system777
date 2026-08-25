package com.pollsystem.model;

/** Lifecycle of a poll. Only one poll may be SCHEDULED or ACTIVE at any moment. */
public enum PollStatus {

    /** Created in the UI, not scheduled yet. */
    DRAFT("טיוטה"),

    /** Waiting for a delayed start - the UI shows a live countdown. */
    SCHEDULED("ממתין לשליחה"),

    /** Sent to participants, collecting answers. */
    ACTIVE("פעיל"),

    /** Finished - no more answers are accepted. */
    CLOSED("הסתיים");

    private final String hebrewLabel;

    PollStatus(String hebrewLabel) {
        this.hebrewLabel = hebrewLabel;
    }

    public String getHebrewLabel() {
        return hebrewLabel;
    }
}
