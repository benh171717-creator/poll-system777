package com.pollsystem.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single poll question: free text + between 2 and 4 answer options.
 * Vote counters live here so results can be computed without re-scanning participants.
 */
public class Question {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 4;

    private final String text;
    private final List<String> options;
    private final int[] votes;

    public Question(String text, List<String> options) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("נוסח השאלה אינו יכול להיות ריק");
        }
        if (options == null || options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("לכל שאלה חייבות להיות בין " + MIN_OPTIONS + " ל-" + MAX_OPTIONS + " אפשרויות תשובה");
        }
        this.text = text.trim();
        this.options = new ArrayList<>();
        for (String option : options) {
            if (option == null || option.isBlank()) {
                throw new IllegalArgumentException("אפשרות תשובה אינה יכולה להיות ריקה");
            }
            this.options.add(option.trim());
        }
        this.votes = new int[this.options.size()];
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public int getOptionCount() {
        return options.size();
    }

    public String getOption(int index) {
        return options.get(index);
    }

    synchronized void registerVote(int optionIndex) {
        if (optionIndex >= 0 && optionIndex < votes.length) {
            votes[optionIndex]++;
        }
    }

    public synchronized int getVotes(int optionIndex) {
        return votes[optionIndex];
    }

    public synchronized int getTotalVotes() {
        int total = 0;
        for (int v : votes) {
            total += v;
        }
        return total;
    }

    /**
     * @return results for this question, sorted by popularity (most voted first),
     *         exactly as required for the results screen.
     */
    public synchronized List<OptionResult> getSortedResults() {
        int total = getTotalVotes();
        List<OptionResult> results = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            double percent = total == 0 ? 0.0 : (votes[i] * 100.0 / total);
            results.add(new OptionResult(options.get(i), votes[i], percent));
        }
        results.sort((a, b) -> Integer.compare(b.getVoteCount(), a.getVoteCount()));
        return results;
    }

    /** One row of the per-question results table. */
    public static class OptionResult {
        private final String optionText;
        private final int voteCount;
        private final double percentage;

        OptionResult(String optionText, int voteCount, double percentage) {
            this.optionText = optionText;
            this.voteCount = voteCount;
            this.percentage = percentage;
        }

        public String getOptionText() {
            return optionText;
        }

        public int getVoteCount() {
            return voteCount;
        }

        public double getPercentage() {
            return percentage;
        }
    }
}
