package com.pollsystem.service;

import com.pollsystem.model.Member;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The GLOBAL community of users registered to the bot.
 * <p>
 * The community is not owned by any poll: members stay after a poll ends and take
 * part in future polls. Thread-safe, because members are added from the bot thread
 * while the Swing thread reads the list.
 */
public class CommunityService {

    /** chatId -> member, insertion-ordered by join time. */
    private final Map<Long, Member> members = new ConcurrentHashMap<>();
    private final List<Long> joinOrder = new CopyOnWriteArrayList<>();
    private final List<CommunityListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(CommunityListener listener) {
        listeners.add(listener);
    }

    /**
     * Adds a user to the community. A user who is already a member is never added twice.
     *
     * @return the newly created member, or {@code null} when the user was already a member.
     */
    public Member join(long chatId, String fullName, String telegramUsername) {
        Member member = new Member(chatId, fullName, telegramUsername, LocalDateTime.now());

        // putIfAbsent, not containsKey+put: two /start taps can arrive on two Telegram
        // handler threads at once, and a check-then-act would let both through.
        if (members.putIfAbsent(chatId, member) != null) {
            return null; // already a member - do not join again, do not re-broadcast
        }
        joinOrder.add(chatId);

        int total = members.size();
        for (CommunityListener listener : listeners) {
            listener.onMemberJoined(member, total);
        }
        return member;
    }

    public boolean isMember(long chatId) {
        return members.containsKey(chatId);
    }

    public Member getMember(long chatId) {
        return members.get(chatId);
    }

    /** @return all members ordered by the time they joined. */
    public List<Member> getMembers() {
        List<Member> ordered = new ArrayList<>();
        for (Long chatId : joinOrder) {
            Member member = members.get(chatId);
            if (member != null) ordered.add(member);
        }
        return ordered;
    }

    /** @return every member except the given one - used to broadcast "a new member joined". */
    public List<Member> getMembersExcept(long chatId) {
        List<Member> others = new ArrayList<>();
        for (Member member : getMembers()) {
            if (member.getChatId() != chatId) others.add(member);
        }
        return others;
    }

    public int size() {
        return members.size();
    }
}
