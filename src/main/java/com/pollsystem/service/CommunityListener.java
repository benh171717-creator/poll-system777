package com.pollsystem.service;

import com.pollsystem.model.Member;

/** Notified whenever the global community changes, so the UI can refresh in real time. */
public interface CommunityListener {

    /**
     * @param newMember   the member that just joined
     * @param totalMembers community size after the join
     */
    void onMemberJoined(Member newMember, int totalMembers);
}
