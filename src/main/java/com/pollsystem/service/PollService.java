package com.pollsystem.service;

import com.pollsystem.model.Member;
import com.pollsystem.model.ParticipantProgress;
import com.pollsystem.model.Poll;
import com.pollsystem.model.PollStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns the whole poll lifecycle: scheduling, the delayed-send countdown, freezing the
 * participant list, collecting answers, the 3-minute reminder, the 5-minute deadline
 * and early closing when everyone finished.
 * <p>
 * Only one poll may be scheduled or active at any moment.
 * <p>
 * <b>Threading.</b> Every state change happens inside {@link #lock}, but no Telegram
 * call is ever made while holding it - sending a poll to N members is N HTTPS round
 * trips, and doing that under the lock (or, worse, on the Swing thread) would freeze
 * the live community table and block incoming answers. State first, network after.
 */
public class PollService {

    /**
     * The poll closes IMMEDIATELY; only the outgoing "poll closed" notices wait this long,
     * so the last voter reads "you finished" before "the poll is closed". The rule itself
     * is untouched - the status is CLOSED and answers are refused from the same instant.
     */
    private static final long CLOSE_NOTICE_DELAY_MILLIS = 400;

    private final CommunityService communityService;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(3, runnable -> {
                Thread thread = new Thread(runnable, "poll-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    private final List<PollListener> listeners = new CopyOnWriteArrayList<>();

    /** Guards currentPoll, the poll status transitions and the scheduled tasks. */
    private final Object lock = new Object();

    private volatile BotGateway botGateway;
    private volatile Poll currentPoll;
    private volatile Poll lastClosedPoll;

    private ScheduledFuture<?> startTask;
    private ScheduledFuture<?> reminderTask;
    private ScheduledFuture<?> closeTask;
    private ScheduledFuture<?> tickTask;

    public PollService(CommunityService communityService) {
        this.communityService = communityService;
    }

    public void setBotGateway(BotGateway botGateway) {
        this.botGateway = botGateway;
    }

    public void addListener(PollListener listener) {
        listeners.add(listener);
    }

    public Poll getCurrentPoll() {
        return currentPoll;
    }

    public Poll getLastClosedPoll() {
        return lastClosedPoll;
    }

    /** @return {@code true} while a poll is scheduled or running - blocks starting another one. */
    public boolean hasLivePoll() {
        Poll poll = currentPoll;
        return poll != null && (poll.getStatus() == PollStatus.SCHEDULED || poll.getStatus() == PollStatus.ACTIVE);
    }

    // ------------------------------------------------------------------
    // Starting a poll
    // ------------------------------------------------------------------

    /**
     * Validates and schedules a poll. Returns immediately: an immediate send is handed to
     * the scheduler thread so the caller (a Swing button handler) is never blocked by the
     * Telegram round trips.
     *
     * @param delayMinutes 0 for an immediate send, otherwise the delay in minutes
     * @throws IllegalStateException with a user-facing Hebrew message when not allowed
     */
    public void schedulePoll(Poll poll, int delayMinutes) {
        synchronized (lock) {
            if (hasLivePoll()) {
                throw new IllegalStateException("קיים כבר סקר פעיל במערכת. יש להמתין לסיומו לפני התחלת סקר חדש.");
            }
            if (communityService.size() < Poll.MIN_MEMBERS_TO_START) {
                throw new IllegalStateException("לא ניתן להתחיל סקר: נדרשים לפחות " + Poll.MIN_MEMBERS_TO_START
                        + " חברים בקהילה, וכרגע רשומים " + communityService.size() + ".");
            }
            if (delayMinutes < 0) {
                throw new IllegalStateException("מספר דקות ההשהיה אינו יכול להיות שלילי.");
            }

            currentPoll = poll;

            if (delayMinutes > 0) {
                poll.setScheduledFor(LocalDateTime.now().plusMinutes(delayMinutes));
                poll.setStatus(PollStatus.SCHEDULED);
                startTask = scheduler.schedule(this::startPollNow, delayMinutes * 60L, TimeUnit.SECONDS);
            }
        }

        if (delayMinutes > 0) {
            fire(listener -> listener.onPollScheduled(poll));
            startTicker();
        } else {
            startPollNow();
        }
    }

    /**
     * Freezes the participant list and arms the timers, then hands the delivery to a
     * background thread. The state change is synchronous - the participant snapshot must
     * be the community at this exact moment - while the N Telegram round trips are not.
     */
    private void startPollNow() {
        final Poll poll;
        final List<Member> snapshot;

        synchronized (lock) {
            poll = currentPoll;
            if (poll == null || poll.getStatus() == PollStatus.ACTIVE || poll.getStatus() == PollStatus.CLOSED) {
                return;
            }
            // The participants of this poll are exactly the community members at this moment.
            snapshot = communityService.getMembers();
            poll.startWithParticipants(snapshot);

            reminderTask = scheduler.schedule(this::sendReminders,
                    Poll.REMINDER_AFTER_MINUTES * 60L, TimeUnit.SECONDS);
            closeTask = scheduler.schedule(
                    () -> closePoll("הסקר נסגר עם תום הזמן שהוקצב (" + Poll.DURATION_MINUTES + " דקות)"),
                    Poll.DURATION_MINUTES * 60L, TimeUnit.SECONDS);
        }

        // Tell the UI the poll is live BEFORE the (slow) delivery loop, so the operator
        // sees the "poll was sent" banner and the tracking table straight away.
        fire(listener -> listener.onPollStarted(poll));
        startTicker();
        scheduler.execute(() -> deliver(poll, snapshot));
    }

    /** The slow part: one message per question, per participant. Never on the caller's thread. */
    private void deliver(Poll poll, List<Member> recipients) {
        BotGateway gateway = botGateway;
        if (gateway == null) return;
        for (Member member : recipients) {
            try {
                gateway.sendPollTo(member, poll);
            } catch (Exception e) {
                System.err.println("שליחת הסקר נכשלה עבור " + member.getFullName() + ": " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Answers
    // ------------------------------------------------------------------

    /**
     * Records an answer coming from Telegram.
     *
     * @return a result telling the bot exactly what to reply to the user.
     */
    public AnswerResult submitAnswer(long chatId, int pollId, int questionIndex, int optionIndex) {
        final Poll poll;
        final AnswerResult result;
        final boolean everyoneFinished;

        // The whole decision runs under the same lock as closePoll, so an answer arriving
        // at the deadline is reported as "poll closed" and never as "already answered".
        synchronized (lock) {
            poll = currentPoll;
            if (poll == null || poll.getId() != pollId) {
                return AnswerResult.POLL_NOT_FOUND;
            }
            if (poll.getStatus() != PollStatus.ACTIVE) {
                return AnswerResult.POLL_CLOSED;
            }
            if (!poll.isParticipant(chatId)) {
                return AnswerResult.NOT_PARTICIPANT;
            }
            // A malformed / hand-crafted callback must not be recorded as an answer.
            if (questionIndex < 0 || questionIndex >= poll.getQuestionCount()
                    || optionIndex < 0 || optionIndex >= poll.getQuestion(questionIndex).getOptionCount()) {
                return AnswerResult.INVALID_CHOICE;
            }
            ParticipantProgress progress = poll.getProgress(chatId);
            if (progress.hasAnswered(questionIndex)) {
                return AnswerResult.ALREADY_ANSWERED;
            }
            if (!poll.registerAnswer(chatId, questionIndex, optionIndex)) {
                return AnswerResult.ALREADY_ANSWERED;
            }
            result = progress.isCompleted() ? AnswerResult.ACCEPTED_COMPLETED : AnswerResult.ACCEPTED;

            // Early close: only when EVERY participant answered EVERY question.
            everyoneFinished = poll.allParticipantsCompleted();
        }

        fire(listener -> listener.onAnswerReceived(poll));

        // Closed on the spot, as the rules require - the notices are what get deferred.
        if (everyoneFinished) {
            closePoll("הסקר נסגר מוקדם - כל המשתתפים השלימו את כל השאלות");
        }
        return result;
    }

    /** Outcome of an answer attempt, so the bot can give precise feedback. */
    public enum AnswerResult {
        ACCEPTED,
        ACCEPTED_COMPLETED,
        ALREADY_ANSWERED,
        POLL_CLOSED,
        NOT_PARTICIPANT,
        POLL_NOT_FOUND,
        INVALID_CHOICE
    }

    // ------------------------------------------------------------------
    // Reminders / closing
    // ------------------------------------------------------------------

    private void sendReminders() {
        final Poll poll;
        final List<ParticipantProgress> toRemind = new ArrayList<>();

        synchronized (lock) {
            poll = currentPoll;
            if (poll == null || poll.getStatus() != PollStatus.ACTIVE) {
                return; // poll already closed - no reminders at all
            }
            for (ParticipantProgress progress : poll.getIncompleteParticipants()) {
                // at most one reminder per participant, claimed under the lock
                if (progress.markReminderSent()) toRemind.add(progress);
            }
        }

        fire(listener -> listener.onRemindersSent(poll, toRemind.size()));

        BotGateway gateway = botGateway;
        if (gateway == null) return;
        for (ParticipantProgress progress : toRemind) {
            try {
                gateway.sendReminder(progress.getMember(), poll,
                        progress.getAnsweredCount(), poll.getQuestionCount());
            } catch (Exception e) {
                System.err.println("שליחת תזכורת נכשלה: " + e.getMessage());
            }
        }
    }

    private void closePoll(String reason) {
        final Poll poll;
        final List<ParticipantProgress> participants;

        synchronized (lock) {
            poll = currentPoll;
            if (poll == null || poll.getStatus() == PollStatus.CLOSED) {
                return;
            }
            cancel(startTask);
            cancel(reminderTask);
            cancel(closeTask);
            cancel(tickTask);

            poll.close(reason);
            lastClosedPoll = poll;
            participants = poll.getParticipants();
        }

        fire(listener -> listener.onPollClosed(poll));

        BotGateway gateway = botGateway;
        if (gateway == null) return;
        scheduler.schedule(() -> {
            for (ParticipantProgress progress : participants) {
                try {
                    gateway.sendPollClosed(progress.getMember(), poll);
                } catch (Exception e) {
                    System.err.println("הודעת סגירה נכשלה: " + e.getMessage());
                }
            }
        }, CLOSE_NOTICE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Cancels a poll that was scheduled but not sent yet. Nothing was delivered to any
     * user, so this is not a "closed poll" and produces no results.
     *
     * @return {@code true} when a scheduled poll was actually cancelled
     */
    public boolean cancelScheduledPoll() {
        final Poll poll;
        synchronized (lock) {
            poll = currentPoll;
            if (poll == null || poll.getStatus() != PollStatus.SCHEDULED) {
                return false;
            }
            cancel(startTask);
            cancel(tickTask);
            poll.setStatus(PollStatus.DRAFT);
            currentPoll = null;
        }
        fire(listener -> listener.onScheduledPollCancelled(poll));
        return true;
    }

    /** Lets the operator end the poll manually from the UI, without blocking the UI thread. */
    public void closeManually() {
        scheduler.execute(() -> closePoll("הסקר נסגר ידנית על ידי מפעיל המערכת"));
    }

    // ------------------------------------------------------------------
    // Countdown ticker (drives both the "time until send" and "time remaining" labels)
    // ------------------------------------------------------------------

    private void startTicker() {
        synchronized (lock) {
            cancel(tickTask);
            tickTask = scheduler.scheduleAtFixedRate(() -> {
                Poll poll = currentPoll;
                if (poll == null) return;
                if (poll.getStatus() == PollStatus.SCHEDULED || poll.getStatus() == PollStatus.ACTIVE) {
                    fire(listener -> listener.onCountdownTick(poll));
                }
            }, 0, 1, TimeUnit.SECONDS);
        }
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) task.cancel(false);
    }

    private void fire(java.util.function.Consumer<PollListener> action) {
        for (PollListener listener : new ArrayList<>(listeners)) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                System.err.println("שגיאה בעדכון מאזין: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
