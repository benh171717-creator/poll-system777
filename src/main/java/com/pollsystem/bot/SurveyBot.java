package com.pollsystem.bot;

import com.pollsystem.config.AppConfig;
import com.pollsystem.model.Member;
import com.pollsystem.model.Poll;
import com.pollsystem.model.PollStatus;
import com.pollsystem.model.Question;
import com.pollsystem.service.BotGateway;
import com.pollsystem.service.CommunityService;
import com.pollsystem.service.PollService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * The Telegram side of the system.
 * <p>
 * Responsibilities: joining the community, broadcasting new joins, delivering poll
 * questions as inline-button messages, collecting one answer per question, and sending
 * reminders / closing notices. All poll rules live in {@link PollService}; this class
 * only translates between Telegram and the services.
 */
public class SurveyBot extends TelegramLongPollingBot implements BotGateway {

    /** Callback payload format: v:<pollId>:<questionIndex>:<optionIndex> */
    private static final String CALLBACK_PREFIX = "v";

    private static final String[] OPTION_MARKERS = {"1️⃣", "2️⃣", "3️⃣", "4️⃣"};

    private final AppConfig config;
    private final CommunityService communityService;
    private final PollService pollService;

    public SurveyBot(AppConfig config, CommunityService communityService, PollService pollService) {
        super(config.getBotToken());
        this.config = config;
        this.communityService = communityService;
        this.pollService = pollService;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    // ==================================================================
    // Incoming updates
    // ==================================================================

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleVote(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            System.err.println("שגיאה בטיפול בעדכון מטלגרם: " + e.getMessage());
        }
    }

    /** Only /start, "היי" and "Hi" join the community. Every other message does not. */
    private void handleTextMessage(Message message) {
        long chatId = message.getChatId();
        String text = message.getText().trim();
        boolean isJoinCommand = text.equalsIgnoreCase("/start")
                || text.equals("היי")
                || text.equalsIgnoreCase("hi");

        if (!isJoinCommand) {
            if (!communityService.isMember(chatId)) {
                // Explicitly NOT joining - just explaining how to join.
                sendText(chatId, "👋 שלום!\n\n"
                        + "כדי להצטרף לקהילת הסקרים שלחו <b>היי</b> או <b>Hi</b>, "
                        + "או לחצו על הכפתור <b>Start</b>.");
            } else {
                sendText(chatId, describeMembershipState(chatId));
            }
            return;
        }

        User from = message.getFrom();
        String fullName = buildFullName(from);
        String username = from == null ? null : from.getUserName();

        Member member = communityService.join(chatId, fullName, username);

        if (member == null) {
            // Already a member - never joined twice, never re-broadcast.
            sendText(chatId, "✅ אתם כבר רשומים בקהילה.\n"
                    + "מספר חברי הקהילה כרגע: <b>" + communityService.size() + "</b>");
            return;
        }

        int total = communityService.size();
        sendText(chatId, "🎉 <b>ברוכים הבאים לקהילת הסקרים!</b>\n\n"
                + "נרשמתם בהצלחה בשם <b>" + escape(member.getFullName()) + "</b>.\n"
                + "אתם החבר/ה מספר <b>" + total + "</b> בקהילה.\n\n"
                + "מעכשיו תקבלו כאן כל סקר חדש שייפתח 📩");

        Poll running = pollService.getCurrentPoll();
        if (running != null && running.getStatus() == PollStatus.ACTIVE) {
            // Correct per the rules, but the silence needs explaining.
            sendText(chatId, "ℹ️ כרגע מתנהל סקר שכבר נשלח למשתתפיו, ולכן לא ניתן להצטרף אליו באמצע.\n"
                    + "הוא ייסגר בעוד <b>" + Poll.formatDuration(running.getSecondsRemaining())
                    + "</b>, ותוכלו להשתתף בסקר הבא 👍");
        }

        // Everyone else is notified about the new member and the updated community size.
        String announcement = "👥 <b>הצטרפות חדשה לקהילה</b>\n\n"
                + escape(member.getFullName()) + " הצטרף/ה לקהילה.\n"
                + "מספר החברים כעת: <b>" + total + "</b>";
        for (Member other : communityService.getMembersExcept(chatId)) {
            sendText(other.getChatId(), announcement);
        }
    }

    /** Handles a tap on an answer button. */
    private void handleVote(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();

        String[] parts = data == null ? new String[0] : data.split(":");
        if (parts.length != 4 || !CALLBACK_PREFIX.equals(parts[0])) {
            answerCallback(callbackQuery.getId(), "פעולה לא מזוהה", true);
            return;
        }

        int pollId, questionIndex, optionIndex;
        try {
            pollId = Integer.parseInt(parts[1]);
            questionIndex = Integer.parseInt(parts[2]);
            optionIndex = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            answerCallback(callbackQuery.getId(), "פעולה לא מזוהה", true);
            return;
        }

        PollService.AnswerResult result = pollService.submitAnswer(chatId, pollId, questionIndex, optionIndex);
        Poll poll = pollService.getCurrentPoll();

        switch (result) {
            case ACCEPTED:
            case ACCEPTED_COMPLETED: {
                Question question = poll.getQuestion(questionIndex);
                String chosen = question.getOption(optionIndex);
                answerCallback(callbackQuery.getId(), "✅ נקלט: " + chosen, false);
                editToAnswered(chatId, messageId, questionIndex, poll, chosen);

                if (result == PollService.AnswerResult.ACCEPTED_COMPLETED) {
                    sendText(chatId, "🏁 <b>סיימתם את הסקר!</b>\n\n"
                            + "עניתם על כל " + questionsLabel(poll.getQuestionCount())
                            + ". תודה על ההשתתפות 🙏");
                }
                break;
            }
            case ALREADY_ANSWERED:
                answerCallback(callbackQuery.getId(), "⚠️ כבר עניתם על שאלה זו - לא ניתן לשנות תשובה", true);
                break;
            case POLL_CLOSED:
            case POLL_NOT_FOUND:
                answerCallback(callbackQuery.getId(), "⏳ הסקר כבר נסגר, לא ניתן לענות יותר", true);
                break;
            case NOT_PARTICIPANT:
                answerCallback(callbackQuery.getId(), "הצטרפתם לקהילה לאחר תחילת הסקר - תוכלו להשתתף בסקר הבא", true);
                break;
            case INVALID_CHOICE:
                answerCallback(callbackQuery.getId(), "האפשרות שנבחרה אינה קיימת בסקר זה", true);
                break;
            default:
                break;
        }
    }

    // ==================================================================
    // BotGateway - outgoing messages driven by PollService
    // ==================================================================

    @Override
    public void sendPollTo(Member member, Poll poll) {
        long chatId = member.getChatId();
        sendText(chatId, "📊 <b>סקר חדש: " + escape(poll.getTitle()) + "</b>\n\n"
                + "מספר שאלות: <b>" + questionsLabel(poll.getQuestionCount()) + "</b>\n"
                + "זמן למענה: <b>" + Poll.DURATION_MINUTES + " דקות</b>\n"
                + "ניתן לבחור תשובה אחת לכל שאלה, ולא ניתן לשנותה לאחר מכן.");

        for (int i = 0; i < poll.getQuestionCount(); i++) {
            Question question = poll.getQuestion(i);
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setParseMode(ParseMode.HTML);
            message.setText(formatQuestion(i, poll.getQuestionCount(), question));
            message.setReplyMarkup(buildKeyboard(poll.getId(), i, question));
            execute(message, "שליחת שאלה " + (i + 1));
        }
    }

    @Override
    public void sendReminder(Member member, Poll poll, int answered, int total) {
        sendText(member.getChatId(), "⏰ <b>תזכורת</b>\n\n"
                + "טרם השלמתם את הסקר \"" + escape(poll.getTitle()) + "\".\n"
                + "עניתם עד כה על <b>" + answered + "/" + total + "</b> שאלות.\n"
                + "נותר זמן של <b>" + Poll.formatDuration(poll.getSecondsRemaining()) + "</b> עד לסיום ⏳");
    }

    @Override
    public void sendPollClosed(Member member, Poll poll) {
        var progress = poll.getProgress(member.getChatId());
        String personal = progress == null ? "" :
                "\nעניתם על <b>" + progress.getProgressText() + "</b> שאלות.";
        sendText(member.getChatId(), "🔒 <b>הסקר \"" + escape(poll.getTitle()) + "\" נסגר.</b>"
                + personal + "\n\nתודה על ההשתתפות! 🙏");
    }

    // ==================================================================
    // Message building helpers
    // ==================================================================

    private String formatQuestion(int index, int total, Question question) {
        StringBuilder builder = new StringBuilder();
        builder.append("❓ <b>שאלה ").append(index + 1).append(" מתוך ").append(total).append("</b>\n\n");
        builder.append(escape(question.getText())).append("\n\n");
        for (int i = 0; i < question.getOptionCount(); i++) {
            builder.append(marker(i)).append("  ").append(escape(question.getOption(i))).append('\n');
        }
        builder.append("\n<i>בחרו אפשרות אחת:</i>");
        return builder.toString();
    }

    private InlineKeyboardMarkup buildKeyboard(int pollId, int questionIndex, Question question) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < question.getOptionCount(); i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(marker(i) + "  " + question.getOption(i));
            button.setCallbackData(CALLBACK_PREFIX + ":" + pollId + ":" + questionIndex + ":" + i);
            rows.add(List.of(button)); // one option per row - easier to read on a phone
        }
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    /** Replaces the question message with a confirmation and removes the buttons. */
    private void editToAnswered(long chatId, int messageId, int questionIndex, Poll poll, String chosen) {
        var progress = poll.getProgress(chatId);
        String progressLine = progress == null ? ""
                : "\n\nהתקדמות: <b>" + progress.getProgressText() + "</b> שאלות ✔️";
        EditMessageText edit = new EditMessageText();
        edit.setChatId(String.valueOf(chatId));
        edit.setMessageId(messageId);
        edit.setParseMode(ParseMode.HTML);
        edit.setText("✅ <b>שאלה " + (questionIndex + 1) + " מתוך " + poll.getQuestionCount() + " - נענתה</b>\n\n"
                + escape(poll.getQuestion(questionIndex).getText()) + "\n\n"
                + "התשובה שלכם: <b>" + escape(chosen) + "</b>" + progressLine);
        execute(edit, "עדכון הודעת שאלה");
    }

    /** "שאלה אחת" / "3 שאלות" - avoids the "1 שאלות" that hard-coded plurals produce. */
    static String questionsLabel(int count) {
        return count == 1 ? "שאלה אחת" : count + " שאלות";
    }

    /** What to answer a member who writes to the bot outside the join commands. */
    private String describeMembershipState(long chatId) {
        Poll poll = pollService.getCurrentPoll();
        if (poll != null && poll.getStatus() == PollStatus.ACTIVE && poll.isParticipant(chatId)) {
            var progress = poll.getProgress(chatId);
            if (progress != null && !progress.isCompleted()) {
                return "📊 <b>הסקר \"" + escape(poll.getTitle()) + "\" פעיל כעת.</b>\n\n"
                        + "עניתם על <b>" + progress.getProgressText() + "</b> שאלות, "
                        + "ונותר זמן של <b>" + Poll.formatDuration(poll.getSecondsRemaining()) + "</b>.\n"
                        + "גללו מעלה להודעות השאלות ולחצו על אפשרות כדי להשלים 👆";
            }
            return "✅ השלמתם את הסקר \"" + escape(poll.getTitle()) + "\".\n"
                    + "התוצאות יוצגו למפעיל המערכת עם סגירתו. תודה 🙏";
        }
        return "ℹ️ אתם כבר חברים בקהילה.\n"
                + "כאשר ייפתח סקר חדש הוא יישלח לכאן אוטומטית 📩";
    }

    private static String marker(int index) {
        return index < OPTION_MARKERS.length ? OPTION_MARKERS[index] : "▫️";
    }

    private static String buildFullName(User user) {
        if (user == null) return null;
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        return user.getUserName();
    }

    /** Escapes the characters that would break Telegram's HTML parse mode. */
    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================================================================
    // Low level send helpers (never let a Telegram error break the flow)
    // ==================================================================

    private void sendText(long chatId, String html) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setParseMode(ParseMode.HTML);
        message.setDisableWebPagePreview(true);
        message.setText(html);
        execute(message, "שליחת הודעה");
    }

    private void answerCallback(String callbackId, String text, boolean asAlert) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        answer.setText(text);
        answer.setShowAlert(asAlert);
        execute(answer, "מענה ללחיצה");
    }

    private void execute(SendMessage method, String description) {
        try {
            super.execute(method);
        } catch (TelegramApiException e) {
            System.err.println(description + " נכשלה: " + e.getMessage());
        }
    }

    private void execute(EditMessageText method, String description) {
        try {
            super.execute(method);
        } catch (TelegramApiException e) {
            System.err.println(description + " נכשלה: " + e.getMessage());
        }
    }

    private void execute(AnswerCallbackQuery method, String description) {
        try {
            super.execute(method);
        } catch (TelegramApiException e) {
            System.err.println(description + " נכשלה: " + e.getMessage());
        }
    }

    /** Used by the UI status bar. */
    public String describeConnection() {
        return "@" + getBotUsername();
    }
}
