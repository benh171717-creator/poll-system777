package com.pollsystem.service;

import com.pollsystem.model.Member;
import com.pollsystem.model.Poll;

/**
 * Everything the poll logic needs from the messaging channel.
 * Keeping this an interface lets {@link PollService} stay free of any Telegram types.
 */
public interface BotGateway {

    /** Sends the whole poll (all its questions) to one participant. */
    void sendPollTo(Member member, Poll poll);

    /** Sends the single reminder allowed per participant per poll. */
    void sendReminder(Member member, Poll poll, int answered, int total);

    /** Tells a participant the poll has closed. */
    void sendPollClosed(Member member, Poll poll);
}
