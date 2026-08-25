package com.pollsystem.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A member of the GLOBAL community.
 * <p>
 * Deliberately holds no poll-related state (no "answered / did not answer" flag):
 * a member's answering state is different for every poll, so it is tracked
 * per-poll inside {@link ParticipantProgress}.
 */
public class Member {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final long chatId;
    private final String fullName;
    private final String telegramUsername; // may be null - not every Telegram user has one
    private final LocalDateTime joinedAt;

    public Member(long chatId, String fullName, String telegramUsername, LocalDateTime joinedAt) {
        this.chatId = chatId;
        this.fullName = (fullName == null || fullName.isBlank()) ? ("משתמש " + chatId) : fullName.trim();
        this.telegramUsername = (telegramUsername == null || telegramUsername.isBlank()) ? null : telegramUsername.trim();
        this.joinedAt = joinedAt;
    }

    public long getChatId() {
        return chatId;
    }

    public String getFullName() {
        return fullName;
    }

    /** @return "@username" or "—" when the user has no Telegram username. */
    public String getUsernameDisplay() {
        return telegramUsername == null ? "—" : "@" + telegramUsername;
    }

    public String getTelegramUsername() {
        return telegramUsername;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public String getJoinedAtShort() {
        return joinedAt.format(TIME_FORMAT);
    }

    public String getJoinedAtFull() {
        return joinedAt.format(FULL_FORMAT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member)) return false;
        return chatId == ((Member) o).chatId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(chatId);
    }

    @Override
    public String toString() {
        return fullName + " (" + getUsernameDisplay() + ")";
    }
}
