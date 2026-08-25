package com.pollsystem.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One poll: its questions, its own frozen participant list and their per-poll answers.
 * <p>
 * The participant list is a SNAPSHOT of the community taken at the moment the poll
 * starts, so a user who joins the community while the poll is running is a community
 * member but not a participant of this poll.
 */
public class Poll {

    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 3;
    public static final int MIN_MEMBERS_TO_START = 3דד;
    public static final int DURATION_MINUTES = 5;
    public static final int REMINDER_AFTER_MINUTES = 3;

    private static final AtomicInteger ID_SEQUENCE = new AtomicInteger(1);

    private final int id;
    private final String title;
    private final List<Question> questions;

    /** chatId -> per-poll progress. Frozen at start time. */
    private final Map<Long, ParticipantProgress> participants = new LinkedHashMap<>();

    private volatile PollStatus status = PollStatus.DRAFT;
    private volatile LocalDateTime scheduledFor;
    private volatile LocalDateTime scheduleCreatedAt;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime closedAt;
    private volatile String closeReason = "";

    public Poll(String title, List<Question> questions) {
        if (questions == null || questions.size() < MIN_QUESTIONS || questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("סקר חייב לכלול בין " + MIN_QUESTIONS + " ל-" + MAX_QUESTIONS + " שאלות");
        }
        this.id = ID_SEQUENCE.getAndIncrement();
        this.title = (title == null || title.isBlank()) ? "סקר #" + id : title.trim();
        this.questions = new ArrayList<>(questions);
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<Question> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getQuestionCount() {
        return questions.size();
    }

    public Question getQuestion(int index) {
        return questions.get(index);
    }

    public PollStatus getStatus() {
        return status;
    }

    public void setStatus(PollStatus status) {
        this.status = status;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(LocalDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
        this.scheduleCreatedAt = LocalDateTime.now();
    }

    /** 0.0 right after scheduling, 1.0 when the poll is about to be sent. */
    public double getScheduleProgress() {
        LocalDateTime from = scheduleCreatedAt;
        LocalDateTime to = scheduledFor;
        if (from == null || to == null) return 0;
        double total = Duration.between(from, to).getSeconds();
        if (total <= 0) return 1;
        double done = Duration.between(from, LocalDateTime.now()).getSeconds();
        return Math.max(0, Math.min(1, done / total));
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }

    // ------------------------------------------------------------------
    // Participants (poll-scoped, never global)
    // ------------------------------------------------------------------

    /** Freezes the current community as this poll's participant list and starts the clock. */
    public synchronized void startWithParticipants(Collection<Member> communitySnapshot) {
        participants.clear();
        for (Member member : communitySnapshot) {
            participants.put(member.getChatId(), new ParticipantProgress(member, questions.size()));
        }
        this.startedAt = LocalDateTime.now();
        this.status = PollStatus.ACTIVE;
    }

    public synchronized void close(String reason) {
        this.status = PollStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.closeReason = reason == null ? "" : reason;
    }

    public synchronized boolean isParticipant(long chatId) {
        return participants.containsKey(chatId);
    }

    public synchronized ParticipantProgress getProgress(long chatId) {
        return participants.get(chatId);
    }

    public synchronized List<ParticipantProgress> getParticipants() {
        return new ArrayList<>(participants.values());
    }

    public synchronized int getParticipantCount() {
        return participants.size();
    }

    public synchronized int getCompletedCount() {
        int count = 0;
        for (ParticipantProgress progress : participants.values()) {
            if (progress.isCompleted()) count++;
        }
        return count;
    }

    public synchronized int getNotCompletedCount() {
        return getParticipantCount() - getCompletedCount();
    }

    public synchronized boolean allParticipantsCompleted() {
        if (participants.isEmpty()) return false;
        for (ParticipantProgress progress : participants.values()) {
            if (!progress.isCompleted()) return false;
        }
        return true;
    }

    public synchronized List<ParticipantProgress> getIncompleteParticipants() {
        List<ParticipantProgress> incomplete = new ArrayList<>();
        for (ParticipantProgress progress : participants.values()) {
            if (!progress.isCompleted()) incomplete.add(progress);
        }
        return incomplete;
    }

    /**
     * Registers a vote for the given participant, if and only if the poll is active,
     * the user is a participant of THIS poll, and the question was not answered before.
     *
     * @return {@code true} when the answer was accepted and counted.
     */
    public synchronized boolean registerAnswer(long chatId, int questionIndex, int optionIndex) {
        if (status != PollStatus.ACTIVE) return false;
        ParticipantProgress progress = participants.get(chatId);
        if (progress == null) return false;
        if (questionIndex < 0 || questionIndex >= questions.size()) return false;
        // Guard the option too: an out-of-range index would otherwise be stored as a
        // valid answer while the vote itself is dropped, inflating the completion count.
        if (optionIndex < 0 || optionIndex >= questions.get(questionIndex).getOptionCount()) return false;
        if (!progress.recordAnswer(questionIndex, optionIndex)) return false;
        questions.get(questionIndex).registerVote(optionIndex);
        return true;
    }

    // ------------------------------------------------------------------
    // Timing helpers used by the live UI
    // ------------------------------------------------------------------

    /** Seconds left until a scheduled poll is sent (0 once it has been sent). */
    public long getSecondsUntilStart() {
        LocalDateTime target = scheduledFor;
        if (status != PollStatus.SCHEDULED || target == null) return 0;
        long seconds = Duration.between(LocalDateTime.now(), target).getSeconds();
        return Math.max(0, seconds);
    }

    /** Seconds left until the poll closes automatically (0 when not active). */
    public long getSecondsRemaining() {
        LocalDateTime start = startedAt;
        if (status != PollStatus.ACTIVE || start == null) return 0;
        long seconds = Duration.between(LocalDateTime.now(), start.plusMinutes(DURATION_MINUTES)).getSeconds();
        return Math.max(0, seconds);
    }

    /** Formats a number of seconds as mm:ss for the countdown labels. */
    public static String formatDuration(long totalSeconds) {
        long safe = Math.max(0, totalSeconds);
        return String.format("%02d:%02d", safe / 60, safe % 60);
    }
}
