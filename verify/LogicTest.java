import com.pollsystem.model.Member;
import com.pollsystem.model.ParticipantProgress;
import com.pollsystem.model.Poll;
import com.pollsystem.model.PollStatus;
import com.pollsystem.model.Question;
import com.pollsystem.service.BotGateway;
import com.pollsystem.service.CommunityService;
import com.pollsystem.service.PollService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Headless verification of every business rule in the specification. */
public class LogicTest {

    static int passed = 0, failed = 0;

    /** Waits (briefly) for the deferred early close to run. */
    static void awaitClosed(Poll poll) throws Exception {
        for (int i = 0; i < 50 && poll.getStatus() != PollStatus.CLOSED; i++) {
            Thread.sleep(40);
        }
    }

    static void check(String name, boolean condition) {
        if (condition) { passed++; System.out.println("  PASS  " + name); }
        else { failed++; System.out.println("  FAIL  " + name); }
    }

    static class FakeBot implements BotGateway {
        List<String> sentPolls = new ArrayList<>();
        List<String> reminders = new ArrayList<>();
        List<String> closes = new ArrayList<>();
        public void sendPollTo(Member m, Poll p) { sentPolls.add(m.getFullName()); }
        public void sendReminder(Member m, Poll p, int a, int t) { reminders.add(m.getFullName() + ":" + a + "/" + t); }
        public void sendPollClosed(Member m, Poll p) { closes.add(m.getFullName()); }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== 1. Community ==");
        CommunityService community = new CommunityService();
        List<String> broadcasts = new ArrayList<>();
        community.addListener((member, total) -> broadcasts.add(member.getFullName() + "|" + total));

        check("first join accepted", community.join(100, "דני כהן", "danny") != null);
        check("duplicate join rejected", community.join(100, "דני כהן", "danny") == null);
        check("size after duplicate is 1", community.size() == 1);
        community.join(200, "יעל לוי", "yael");
        community.join(300, "אורי ישראלי", null);
        check("three members", community.size() == 3);
        check("listener fired 3 times", broadcasts.size() == 3);
        check("broadcast carries updated size", broadcasts.get(2).endsWith("|3"));
        check("others excluded correctly", community.getMembersExcept(200).size() == 2);
        check("null username shown as dash", community.getMember(300).getUsernameDisplay().equals("—"));
        check("username shown with @", community.getMember(100).getUsernameDisplay().equals("@danny"));

        System.out.println("== 2. Poll creation limits ==");
        boolean threw = false;
        try { new Question("q", List.of("a")); } catch (IllegalArgumentException e) { threw = true; }
        check("question with 1 option rejected", threw);
        threw = false;
        try { new Question("q", List.of("a", "b", "c", "d", "e")); } catch (IllegalArgumentException e) { threw = true; }
        check("question with 5 options rejected", threw);
        threw = false;
        try { new Poll("t", List.of(q("q1"), q("q2"), q("q3"), q("q4"))); } catch (IllegalArgumentException e) { threw = true; }
        check("poll with 4 questions rejected", threw);

        System.out.println("== 3. Minimum community size ==");
        CommunityService small = new CommunityService();
        small.join(1, "A", "a");
        small.join(2, "B", "b");
        PollService smallService = new PollService(small);
        threw = false;
        try { smallService.schedulePoll(new Poll("t", List.of(q("q1"))), 0); }
        catch (IllegalStateException e) { threw = true; }
        check("cannot start with 2 members", threw);
        smallService.shutdown();

        System.out.println("== 4. Start, participants snapshot ==");
        PollService service = new PollService(community);
        FakeBot bot = new FakeBot();
        service.setBotGateway(bot);
        Poll poll = new Poll("סקר בדיקה", List.of(q("שאלה 1"), q("שאלה 2"), q("שאלה 3")));
        service.schedulePoll(poll, 0);
        check("poll is active", poll.getStatus() == PollStatus.ACTIVE);
        check("3 participants frozen", poll.getParticipantCount() == 3);
        // Delivery runs off the caller's thread so the UI never freezes - wait for it.
        for (int i = 0; i < 50 && bot.sentPolls.size() < 3; i++) Thread.sleep(40);
        check("poll sent to 3 members", bot.sentPolls.size() == 3);

        System.out.println("== 5. Second poll blocked while one is live ==");
        threw = false;
        try { service.schedulePoll(new Poll("t2", List.of(q("x"))), 0); }
        catch (IllegalStateException e) { threw = true; }
        check("second poll rejected", threw);

        System.out.println("== 6. Late joiner ==");
        community.join(400, "מאוחר", "late");
        check("community grew to 4", community.size() == 4);
        check("participants still 3", poll.getParticipantCount() == 3);
        check("late joiner is not a participant", !poll.isParticipant(400));
        check("late joiner answer rejected",
                service.submitAnswer(400, poll.getId(), 0, 0) == PollService.AnswerResult.NOT_PARTICIPANT);

        System.out.println("== 7. Answering rules ==");
        check("first answer accepted",
                service.submitAnswer(100, poll.getId(), 0, 0) == PollService.AnswerResult.ACCEPTED);
        check("same question again rejected",
                service.submitAnswer(100, poll.getId(), 0, 1) == PollService.AnswerResult.ALREADY_ANSWERED);
        check("progress is 1/3", poll.getProgress(100).getProgressText().equals("1/3"));
        check("state IN_PROGRESS", poll.getProgress(100).getState() == ParticipantProgress.State.IN_PROGRESS);
        check("untouched participant NOT_STARTED",
                poll.getProgress(300).getState() == ParticipantProgress.State.NOT_STARTED);
        check("completed count still 0", poll.getCompletedCount() == 0);

        // everyone answers question 0 only -> must NOT close early
        service.submitAnswer(200, poll.getId(), 0, 0);
        service.submitAnswer(300, poll.getId(), 0, 1);
        check("poll still active after all answered Q1 only", poll.getStatus() == PollStatus.ACTIVE);

        System.out.println("== 8. Reminders ==");
        service.submitAnswer(100, poll.getId(), 1, 0);
        service.submitAnswer(100, poll.getId(), 2, 0);
        check("danny completed", poll.getProgress(100).isCompleted());
        Method sendReminders = PollService.class.getDeclaredMethod("sendReminders");
        sendReminders.setAccessible(true);
        sendReminders.invoke(service);
        check("reminders only to the 2 incomplete", bot.reminders.size() == 2);
        check("completed user got no reminder",
                bot.reminders.stream().noneMatch(r -> r.startsWith("דני")));
        sendReminders.invoke(service);
        check("no second reminder for the same poll", bot.reminders.size() == 2);
        check("reminder flag visible in UI data", poll.getProgress(200).isReminderSent());

        System.out.println("== 9. Early close when everybody completed ==");
        service.submitAnswer(200, poll.getId(), 1, 1);
        service.submitAnswer(200, poll.getId(), 2, 1);
        check("still active (one participant left)", poll.getStatus() == PollStatus.ACTIVE);
        service.submitAnswer(300, poll.getId(), 1, 1);
        check("still active before last answer", poll.getStatus() == PollStatus.ACTIVE);
        service.submitAnswer(300, poll.getId(), 2, 0);
        // The early close is deferred by a fraction of a second on purpose, so the last
        // voter reads "you finished" before "the poll is closed".
        check("closed immediately when everybody completed", poll.getStatus() == PollStatus.CLOSED);
        // The closing NOTICES are deferred a fraction of a second so the last voter
        // reads "you finished" first; the closure itself already happened above.
        for (int i = 0; i < 50 && bot.closes.size() < 3; i++) Thread.sleep(40);
        check("close notice sent to all 3", bot.closes.size() == 3);
        check("answers rejected after close",
                service.submitAnswer(100, poll.getId(), 0, 0) == PollService.AnswerResult.POLL_CLOSED);

        System.out.println("== 10. Results ==");
        // Q1: option0 got danny + yael = 2, option1 got uri = 1
        List<Question.OptionResult> results = poll.getQuestion(0).getSortedResults();
        check("results sorted by votes desc", results.get(0).getVoteCount() >= results.get(1).getVoteCount());
        check("top option has 2 votes", results.get(0).getVoteCount() == 2);
        check("top percentage is 66-67%", Math.round(results.get(0).getPercentage()) == 67);
        double sum = results.stream().mapToDouble(Question.OptionResult::getPercentage).sum();
        check("percentages sum to 100", Math.abs(sum - 100.0) < 0.001);
        check("total votes = 3", poll.getQuestion(0).getTotalVotes() == 3);

        System.out.println("== 11. Community survives the poll ==");
        check("community still has 4 members", community.size() == 4);

        System.out.println("== 12. New poll allowed after close ==");
        Poll poll2 = new Poll("סקר שני", List.of(q("שאלה יחידה")));
        service.schedulePoll(poll2, 0);
        check("second poll started", poll2.getStatus() == PollStatus.ACTIVE);
        check("participants now 4 (late joiner included)", poll2.getParticipantCount() == 4);
        check("per-poll state is independent: danny not completed in poll2",
                !poll2.getProgress(100).isCompleted());
        service.closeManually(); // runs off the UI thread, so wait for it
        awaitClosed(poll2);
        check("manual close works", poll2.getStatus() == PollStatus.CLOSED);

        System.out.println("== 13. Scheduled poll countdown ==");
        Poll poll3 = new Poll("סקר מושהה", List.of(q("שאלה")));
        service.schedulePoll(poll3, 2);
        check("status SCHEDULED", poll3.getStatus() == PollStatus.SCHEDULED);
        long secs = poll3.getSecondsUntilStart();
        check("countdown ~120s", secs > 110 && secs <= 120);
        check("countdown formatted mm:ss", Poll.formatDuration(125).equals("02:05"));
        service.cancelScheduledPoll();

        System.out.println("== 14. Malformed callbacks are rejected, not recorded ==");
        Poll poll4 = new Poll("סקר תקינות", List.of(q("שאלה 1"), q("שאלה 2")));
        service.schedulePoll(poll4, 0);
        check("out-of-range option rejected",
                service.submitAnswer(100, poll4.getId(), 0, 7) == PollService.AnswerResult.INVALID_CHOICE);
        check("out-of-range question rejected",
                service.submitAnswer(100, poll4.getId(), 9, 0) == PollService.AnswerResult.INVALID_CHOICE);
        check("negative option rejected",
                service.submitAnswer(100, poll4.getId(), 0, -1) == PollService.AnswerResult.INVALID_CHOICE);
        check("rejected callback did not mark the question answered",
                !poll4.getProgress(100).hasAnswered(0));
        check("rejected callback added no vote", poll4.getQuestion(0).getTotalVotes() == 0);
        check("a valid answer still works after a bad one",
                service.submitAnswer(100, poll4.getId(), 0, 1) == PollService.AnswerResult.ACCEPTED);
        check("valid vote counted", poll4.getQuestion(0).getTotalVotes() == 1);

        System.out.println("== 15. Closed poll reports POLL_CLOSED, never ALREADY_ANSWERED ==");
        service.closeManually();
        awaitClosed(poll4);
        check("unanswered question after close -> POLL_CLOSED",
                service.submitAnswer(200, poll4.getId(), 0, 0) == PollService.AnswerResult.POLL_CLOSED);
        check("previously answered question after close -> POLL_CLOSED",
                service.submitAnswer(100, poll4.getId(), 0, 0) == PollService.AnswerResult.POLL_CLOSED);

        System.out.println("== 16. Double join is impossible ==");
        int before = community.size();
        check("repeat join returns null", community.join(100, "דני כהן", "danny") == null);
        check("community size unchanged", community.size() == before);
        long danny = community.getMembers().stream().filter(m -> m.getChatId() == 100).count();
        check("member appears exactly once in the list", danny == 1);

        service.shutdown();

        System.out.println();
        System.out.println("passed=" + passed + "  failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static Question q(String text) {
        return new Question(text, List.of("אפשרות א", "אפשרות ב"));
    }
}
