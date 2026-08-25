package com.pollsystem.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks a single participant's state INSIDE ONE SPECIFIC POLL.
 * <p>
 * This is intentionally separate from {@link Member}: the same person can complete
 * poll #1, skip poll #2 and half-answer poll #3, so answering state can never be a
 * property of the global community member.
 */
public class ParticipantProgress {

    /** Answering state of a participant within one poll. */
    public enum State {
        NOT_STARTED("טרם ענה"),
        IN_PROGRESS("בתהליך"),
        COMPLETED("השלים");

        private final String hebrewLabel;

        State(String hebrewLabel) {
            this.hebrewLabel = hebrewLabel;
        }

        public String getHebrewLabel() {
            return hebrewLabel;
        }
    }

    private final Member member;
    private final int totalQuestions;
    /** questionIndex -> chosen optionIndex. Presence of a key means "already answered". */
    private final Map<Integer, Integer> answers = new LinkedHashMap<>();
    private boolean reminderSent = false;

    public ParticipantProgress(Member member, int totalQuestions) {
        this.member = member;
        this.totalQuestions = totalQuestions;
    }

    public Member getMember() {
        return member;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public synchronized boolean hasAnswered(int questionIndex) {
        return answers.containsKey(questionIndex);
    }

    /**
     * Records an answer.
     *
     * @return {@code true} if this was a new answer, {@code false} if the question
     *         was already answered (a second answer is never allowed).
     */
    public synchronized boolean recordAnswer(int questionIndex, int optionIndex) {
        if (answers.containsKey(questionIndex)) {
            return false;
        }
        answers.put(questionIndex, optionIndex);
        return true;
    }

    public synchronized int getAnsweredCount() {
        return answers.size();
    }

    public synchronized boolean isCompleted() {
        return answers.size() >= totalQuestions;
    }

    public synchronized State getState() {
        if (isCompleted()) return State.COMPLETED;
        if (answers.isEmpty()) return State.NOT_STARTED;
        return State.IN_PROGRESS;
    }

    /** "2/3" - the progress string shown in the live tracking table. */
    public synchronized String getProgressText() {
        return getAnsweredCount() + "/" + totalQuestions;
    }

    public synchronized double getProgressRatio() {
        return totalQuestions == 0 ? 0 : (double) getAnsweredCount() / totalQuestions;
    }

    public synchronized boolean isReminderSent() {
        return reminderSent;
    }

    /** @return {@code true} the first time only - guarantees at most one reminder per poll. */
    public synchronized boolean markReminderSent() {
        if (reminderSent) return false;
        reminderSent = true;
        return true;
    }
}
