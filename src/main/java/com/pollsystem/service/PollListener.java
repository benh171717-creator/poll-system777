package com.pollsystem.service;

import com.pollsystem.model.Poll;

/** Poll lifecycle events. All callbacks are marshalled onto the Swing thread by the UI. */
public interface PollListener {

    /** A poll was scheduled for a delayed send - the UI starts its countdown. */
    void onPollScheduled(Poll poll);

    /** Ticks once per second while a poll is scheduled or active. */
    void onCountdownTick(Poll poll);

    /** The poll was actually sent to its participants. */
    void onPollStarted(Poll poll);

    /** A participant answered a question - the live tracking table must refresh. */
    void onAnswerReceived(Poll poll);

    /** Reminders were sent to the participants that had not completed the poll. */
    void onRemindersSent(Poll poll, int remindedCount);

    /** The poll closed (time is up, or everybody completed it). Results are ready. */
    void onPollClosed(Poll poll);

    /** A scheduled poll was cancelled before it was ever sent. */
    void onScheduledPollCancelled(Poll poll);
}
