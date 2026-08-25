/* =====================================================================================
 *  מערכת ניהול סקרים - בוט Telegram + ממשק ניהול ב-Java Swing
 *  -----------------------------------------------------------------------------------
 *  קובץ זה הוא איחוד אוטומטי של כל מחלקות הפרויקט לקובץ יחיד, לצורך הגשה.
 *  גרסת הפרויקט המלאה (Maven, מחלקה לכל קובץ) מסודרת בחבילות:
 *      com.pollsystem.model | .service | .bot | .ui | .config
 *
 *  הרצה:
 *      javac -encoding UTF-8 -cp "telegrambots-6.9.7.1.jar:jackson-databind-2.17.2.jar:..." Main.java
 *      java  -cp ".:telegrambots-6.9.7.1.jar:..." Main
 *  (או פשוט: mvn exec:java  בפרויקט המלא)
 *
 *  תלויות:
 *      org.telegram : telegrambots            : 6.9.7.1
 *      com.fasterxml.jackson.core : jackson-databind : 2.17.2
 *
 *  בהפעלה ראשונה נפתח חלון הגדרות להזנת טוקן הבוט ומפתח OpenAI (נשמר ב-config.properties).
 * ===================================================================================== */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
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
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

// ====================================================================================
// config/AppConfig.java
// ====================================================================================

/**
 * Loads / stores the runtime settings (bot token, bot username, OpenAI key).
 * <p>
 * Resolution order: {@code config.properties} next to the program → environment
 * variables. If nothing is found, the UI opens a setup dialog on first run, so the
 * user never has to edit files by hand.
 */
final class AppConfig {

    private static final Path CONFIG_FILE = Paths.get("config.properties");

    private static final String KEY_BOT_TOKEN = "telegram.bot.token";
    private static final String KEY_BOT_USERNAME = "telegram.bot.username";
    private static final String KEY_OPENAI_KEY = "openai.api.key";
    private static final String KEY_OPENAI_MODEL = "openai.model";
    private static final String KEY_OPENAI_BASE_URL = "openai.base.url";

    /** The official ChatGPT endpoint - used unless the operator points the app elsewhere. */
    public static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private String botToken = "";
    private String botUsername = "";
    private String openAiApiKey = "";
    private String openAiModel = "gpt-4o-mini";
    private String openAiBaseUrl = OPENAI_ENDPOINT;

    private AppConfig() {
    }

    public static AppConfig load() {
        AppConfig config = new AppConfig();
        Properties properties = new Properties();

        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("אזהרה: לא ניתן לקרוא את config.properties - " + e.getMessage());
            }
        }

        config.botToken = firstNonBlank(properties.getProperty(KEY_BOT_TOKEN), System.getenv("TELEGRAM_BOT_TOKEN"), "");
        config.botUsername = firstNonBlank(properties.getProperty(KEY_BOT_USERNAME), System.getenv("TELEGRAM_BOT_USERNAME"), "");
        config.openAiApiKey = firstNonBlank(properties.getProperty(KEY_OPENAI_KEY), System.getenv("OPENAI_API_KEY"), "");
        config.openAiModel = firstNonBlank(properties.getProperty(KEY_OPENAI_MODEL), System.getenv("OPENAI_MODEL"), "gpt-4o-mini");
        config.openAiBaseUrl = firstNonBlank(properties.getProperty(KEY_OPENAI_BASE_URL), System.getenv("OPENAI_BASE_URL"), OPENAI_ENDPOINT);
        return config;
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_BOT_TOKEN, botToken);
        properties.setProperty(KEY_BOT_USERNAME, botUsername);
        properties.setProperty(KEY_OPENAI_KEY, openAiApiKey);
        properties.setProperty(KEY_OPENAI_MODEL, openAiModel);
        properties.setProperty(KEY_OPENAI_BASE_URL, openAiBaseUrl);
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            properties.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8),
                    "Telegram Poll System settings");
        } catch (IOException e) {
            System.err.println("אזהרה: לא ניתן לשמור את config.properties - " + e.getMessage());
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate.trim();
        }
        return "";
    }

    public boolean isTelegramConfigured() {
        return !botToken.isBlank() && !botUsername.isBlank();
    }

    public boolean isOpenAiConfigured() {
        return !openAiApiKey.isBlank();
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken == null ? "" : botToken.trim();
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        String value = botUsername == null ? "" : botUsername.trim();
        this.botUsername = value.startsWith("@") ? value.substring(1) : value;
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public void setOpenAiApiKey(String openAiApiKey) {
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public void setOpenAiModel(String openAiModel) {
        this.openAiModel = (openAiModel == null || openAiModel.isBlank()) ? "gpt-4o-mini" : openAiModel.trim();
    }

    /**
     * Chat-completions endpoint. Defaults to the official ChatGPT API; any
     * OpenAI-compatible endpoint (Google Gemini, Groq, a local server) can be used
     * instead without touching the code.
     */
    public String getOpenAiBaseUrl() {
        return openAiBaseUrl;
    }

    public void setOpenAiBaseUrl(String openAiBaseUrl) {
        this.openAiBaseUrl = (openAiBaseUrl == null || openAiBaseUrl.isBlank())
                ? OPENAI_ENDPOINT : openAiBaseUrl.trim();
    }

    /** {@code true} when talking to the official ChatGPT API rather than a compatible one. */
    public boolean isOfficialOpenAi() {
        return openAiBaseUrl.contains("api.openai.com");
    }

    /**
     * {@code true} for the college's ChatGPT proxy, which is not OpenAI-shaped: it is a
     * plain {@code GET .../api-request?token=..&text=..} returning {@code {"value": "..."}}.
     * Detected from the URL so the operator only has to pick a provider, never a protocol.
     */
    public boolean isCourseProxy() {
        return openAiBaseUrl.contains("/api-request");
    }
}

// ====================================================================================
// model/Member.java
// ====================================================================================

/**
 * A member of the GLOBAL community.
 * <p>
 * Deliberately holds no poll-related state (no "answered / did not answer" flag):
 * a member's answering state is different for every poll, so it is tracked
 * per-poll inside {@link ParticipantProgress}.
 */
class Member {

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

// ====================================================================================
// model/Question.java
// ====================================================================================

/**
 * A single poll question: free text + between 2 and 4 answer options.
 * Vote counters live here so results can be computed without re-scanning participants.
 */
class Question {

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

// ====================================================================================
// model/PollStatus.java
// ====================================================================================

/** Lifecycle of a poll. Only one poll may be SCHEDULED or ACTIVE at any moment. */
enum PollStatus {

    /** Created in the UI, not scheduled yet. */
    DRAFT("טיוטה"),

    /** Waiting for a delayed start - the UI shows a live countdown. */
    SCHEDULED("ממתין לשליחה"),

    /** Sent to participants, collecting answers. */
    ACTIVE("פעיל"),

    /** Finished - no more answers are accepted. */
    CLOSED("הסתיים");

    private final String hebrewLabel;

    PollStatus(String hebrewLabel) {
        this.hebrewLabel = hebrewLabel;
    }

    public String getHebrewLabel() {
        return hebrewLabel;
    }
}

// ====================================================================================
// model/ParticipantProgress.java
// ====================================================================================

/**
 * Tracks a single participant's state INSIDE ONE SPECIFIC POLL.
 * <p>
 * This is intentionally separate from {@link Member}: the same person can complete
 * poll #1, skip poll #2 and half-answer poll #3, so answering state can never be a
 * property of the global community member.
 */
class ParticipantProgress {

    /** Answering state of a participant within one poll. */
    public enum State {
        NOT_STARTED("טרם ענה"),
        IN_PROGRESS("בתהליך"),
        COMPLETED("השלים");

        private final String hebrewLabel;

        State(String hebrewLabel) {
            this.hebrewLabel = hebrewLabel;
        }

        public String getHebrewLabel() {
            return hebrewLabel;
        }
    }

    private final Member member;
    private final int totalQuestions;
    /** questionIndex -> chosen optionIndex. Presence of a key means "already answered". */
    private final Map<Integer, Integer> answers = new LinkedHashMap<>();
    private boolean reminderSent = false;

    public ParticipantProgress(Member member, int totalQuestions) {
        this.member = member;
        this.totalQuestions = totalQuestions;
    }

    public Member getMember() {
        return member;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public synchronized boolean hasAnswered(int questionIndex) {
        return answers.containsKey(questionIndex);
    }

    /**
     * Records an answer.
     *
     * @return {@code true} if this was a new answer, {@code false} if the question
     *         was already answered (a second answer is never allowed).
     */
    public synchronized boolean recordAnswer(int questionIndex, int optionIndex) {
        if (answers.containsKey(questionIndex)) {
            return false;
        }
        answers.put(questionIndex, optionIndex);
        return true;
    }

    public synchronized int getAnsweredCount() {
        return answers.size();
    }

    public synchronized boolean isCompleted() {
        return answers.size() >= totalQuestions;
    }

    public synchronized State getState() {
        if (isCompleted()) return State.COMPLETED;
        if (answers.isEmpty()) return State.NOT_STARTED;
        return State.IN_PROGRESS;
    }

    /** "2/3" - the progress string shown in the live tracking table. */
    public synchronized String getProgressText() {
        return getAnsweredCount() + "/" + totalQuestions;
    }

    public synchronized double getProgressRatio() {
        return totalQuestions == 0 ? 0 : (double) getAnsweredCount() / totalQuestions;
    }

    public synchronized boolean isReminderSent() {
        return reminderSent;
    }

    /** @return {@code true} the first time only - guarantees at most one reminder per poll. */
    public synchronized boolean markReminderSent() {
        if (reminderSent) return false;
        reminderSent = true;
        return true;
    }
}

// ====================================================================================
// model/Poll.java
// ====================================================================================

/**
 * One poll: its questions, its own frozen participant list and their per-poll answers.
 * <p>
 * The participant list is a SNAPSHOT of the community taken at the moment the poll
 * starts, so a user who joins the community while the poll is running is a community
 * member but not a participant of this poll.
 */
class Poll {

    public static final int MIN_QUESTIONS = 1;
    public static final int MAX_QUESTIONS = 3;
    public static final int MIN_MEMBERS_TO_START = 3;
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

// ====================================================================================
// service/CommunityListener.java
// ====================================================================================

/** Notified whenever the global community changes, so the UI can refresh in real time. */
interface CommunityListener {

    /**
     * @param newMember   the member that just joined
     * @param totalMembers community size after the join
     */
    void onMemberJoined(Member newMember, int totalMembers);
}

// ====================================================================================
// service/CommunityService.java
// ====================================================================================

/**
 * The GLOBAL community of users registered to the bot.
 * <p>
 * The community is not owned by any poll: members stay after a poll ends and take
 * part in future polls. Thread-safe, because members are added from the bot thread
 * while the Swing thread reads the list.
 */
class CommunityService {

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

// ====================================================================================
// service/BotGateway.java
// ====================================================================================

/**
 * Everything the poll logic needs from the messaging channel.
 * Keeping this an interface lets {@link PollService} stay free of any Telegram types.
 */
interface BotGateway {

    /** Sends the whole poll (all its questions) to one participant. */
    void sendPollTo(Member member, Poll poll);

    /** Sends the single reminder allowed per participant per poll. */
    void sendReminder(Member member, Poll poll, int answered, int total);

    /** Tells a participant the poll has closed. */
    void sendPollClosed(Member member, Poll poll);
}

// ====================================================================================
// service/PollListener.java
// ====================================================================================

/** Poll lifecycle events. All callbacks are marshalled onto the Swing thread by the UI. */
interface PollListener {

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

// ====================================================================================
// service/PollService.java
// ====================================================================================

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
class PollService {

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

// ====================================================================================
// service/ChatGptService.java
// ====================================================================================

/**
 * Generates poll questions from a free-text topic using the OpenAI (ChatGPT) API.
 * The generated questions are returned to the UI for review before the poll is sent.
 * <p>
 * The endpoint is read from {@link AppConfig}, so the same client also works against any
 * OpenAI-compatible service (Google Gemini, Groq, a local model) without a code change.
 */
class ChatGptService {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public ChatGptService(AppConfig config) {
        this.config = config;
    }

    /**
     * Asks ChatGPT for {@code questionCount} questions about {@code topic}, each with
     * {@code optionsPerQuestion} answer options.
     *
     * @throws ChatGptException with a clear Hebrew message when the call fails
     */
    public List<Question> generateQuestions(String topic, int questionCount, int optionsPerQuestion) {
        if (!config.isOpenAiConfigured()) {
            throw new ChatGptException("לא הוגדר מפתח OpenAI API. ניתן להגדיר אותו דרך תפריט ההגדרות.");
        }
        if (topic == null || topic.isBlank()) {
            throw new ChatGptException("יש להזין נושא לסקר לפני יצירה אוטומטית.");
        }
        questionCount = Math.max(Poll.MIN_QUESTIONS, Math.min(Poll.MAX_QUESTIONS, questionCount));
        optionsPerQuestion = Math.max(Question.MIN_OPTIONS, Math.min(Question.MAX_OPTIONS, optionsPerQuestion));

        String prompt = buildPrompt(topic, questionCount, optionsPerQuestion);

        try {
            if (config.isCourseProxy()) {
                return parseQuestions(stripCodeFences(callCourseProxy(prompt)));
            }

            HttpResponse<String> response = call(prompt, true);

            // Some OpenAI-compatible services reject "response_format" - retry plainly.
            if (response.statusCode() == 400) {
                response = call(prompt, false);
            }

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ChatGptException("מפתח ה-API אינו תקין (" + response.statusCode()
                        + "). יש לבדוק אותו בחלון ההגדרות.");
            }
            if (response.statusCode() == 404) {
                throw new ChatGptException("שם המודל או כתובת השרת שגויים (404). יש לבדוק אותם בחלון ההגדרות.");
            }
            if (response.statusCode() == 429) {
                throw new ChatGptException("חריגה ממכסת השימוש ב-API (429). נסה שוב בעוד מספר רגעים.");
            }
            if (response.statusCode() / 100 != 2) {
                throw new ChatGptException("שגיאה בפנייה לשירות ה-AI (קוד " + response.statusCode() + "): "
                        + shorten(response.body()));
            }

            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ChatGptException("ChatGPT החזיר תשובה ריקה. נסה לנסח את הנושא מחדש.");
            }
            return parseQuestions(stripCodeFences(content));

        } catch (ChatGptException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ChatGptException("הפנייה ל-ChatGPT ארכה זמן רב מדי. נסה שוב.");
        } catch (java.io.IOException e) {
            throw new ChatGptException("שגיאת רשת בפנייה ל-ChatGPT: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatGptException("הפנייה ל-ChatGPT בוטלה.");
        } catch (Exception e) {
            throw new ChatGptException("שגיאה בעיבוד התשובה מ-ChatGPT: " + e.getMessage());
        }
    }

    /**
     * The college's ChatGPT proxy. Unlike the OpenAI protocol it takes the whole prompt as
     * a query parameter and answers with a small envelope:
     * <pre>{"error":false,"code":null,"value":"...the model's reply..."}</pre>
     *
     * @return the model's raw reply text
     */
    private String callCourseProxy(String prompt) throws Exception {
        String instruction = "אתה עוזר שיוצר סקרים בעברית. החזר JSON תקין בלבד, "
                + "ללא טקסט נוסף וללא סימוני קוד.\n\n" + prompt;

        String url = config.getOpenAiBaseUrl()
                + (config.getOpenAiBaseUrl().contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(config.getOpenAiApiKey(), StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(instruction, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                // The proxy rejects requests without a browser-like agent.
                .header("User-Agent", BROWSER_USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() / 100 != 2) {
            throw new ChatGptException("שרת הקורס החזיר שגיאה (קוד " + response.statusCode() + "): "
                    + shorten(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        if (root.path("error").asBoolean(false)) {
            int code = root.path("code").asInt(0);
            throw new ChatGptException("שרת הקורס דחה את הבקשה (code " + code + "). "
                    + "בדקו שהטוקן הועתק במלואו ושנותרה יתרה בחשבון הסטודנט.");
        }

        String value = root.path("value").asText("");
        if (value.isBlank()) {
            throw new ChatGptException("שרת הקורס החזיר תשובה ריקה. נסו לנסח את הנושא מחדש.");
        }
        return value;
    }

    /** Builds and sends one chat-completions request. */
    private HttpResponse<String> call(String prompt, boolean requestJsonFormat) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getOpenAiModel());
        body.put("temperature", 0.8);

        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", "אתה עוזר שיוצר סקרים בעברית. אתה מחזיר JSON תקין בלבד, ללא טקסט נוסף וללא סימוני קוד.");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        if (requestJsonFormat) {
            body.putObject("response_format").put("type", "json_object");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getOpenAiBaseUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Authorization", "Bearer " + config.getOpenAiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() > 160 ? single.substring(0, 160) + "..." : single;
    }

    private String buildPrompt(String topic, int questionCount, int optionsPerQuestion) {
        return "צור סקר בעברית בנושא: \"" + topic + "\".\n"
                + "הסקר יכלול בדיוק " + questionCount + " שאלות, ולכל שאלה בדיוק "
                + optionsPerQuestion + " אפשרויות תשובה קצרות וברורות.\n"
                + "השאלות צריכות להיות שאלות דעה או העדפה, כך שלכל אפשרות יש סיכוי להיבחר.\n"
                + "החזר JSON בפורמט הבא בלבד:\n"
                + "{\"title\": \"כותרת קצרה לסקר\", \"questions\": ["
                + "{\"text\": \"נוסח השאלה\", \"options\": [\"אפשרות 1\", \"אפשרות 2\"]}]}";
    }

    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            if (firstNewLine > 0) trimmed = trimmed.substring(firstNewLine + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private List<Question> parseQuestions(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new ChatGptException("לא התקבלו שאלות מ-ChatGPT. נסה נושא אחר.");
        }
        List<Question> questions = new ArrayList<>();
        for (JsonNode questionNode : questionsNode) {
            if (questions.size() >= Poll.MAX_QUESTIONS) break;
            String text = questionNode.path("text").asText("").trim();
            List<String> options = new ArrayList<>();
            for (JsonNode optionNode : questionNode.path("options")) {
                String option = optionNode.asText("").trim();
                if (!option.isEmpty() && options.size() < Question.MAX_OPTIONS) {
                    options.add(option);
                }
            }
            if (text.isEmpty() || options.size() < Question.MIN_OPTIONS) continue;
            questions.add(new Question(text, options));
        }
        if (questions.isEmpty()) {
            throw new ChatGptException("התשובה מ-ChatGPT לא הכילה שאלות תקינות. נסה שוב.");
        }
        return questions;
    }

    /** Title suggested by ChatGPT, extracted best-effort (never fails the flow). */
    public String suggestTitle(String topic) {
        return topic == null || topic.isBlank() ? "סקר" : topic.trim();
    }

    /** A failure the UI can present to the user as-is. */
    public static class ChatGptException extends RuntimeException {
        public ChatGptException(String message) {
            super(message);
        }
    }
}

// ====================================================================================
// bot/SurveyBot.java
// ====================================================================================

/**
 * The Telegram side of the system.
 * <p>
 * Responsibilities: joining the community, broadcasting new joins, delivering poll
 * questions as inline-button messages, collecting one answer per question, and sending
 * reminders / closing notices. All poll rules live in {@link PollService}; this class
 * only translates between Telegram and the services.
 */
class SurveyBot extends TelegramLongPollingBot implements BotGateway {

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

// ====================================================================================
// ui/Theme.java
// ====================================================================================

/**
 * Single place for every colour, font and reusable widget in the UI, so the whole
 * application looks consistent. Also provides Hebrew-friendly font selection.
 */
final class Theme {

    // ---- palette -------------------------------------------------------
    public static final Color BACKGROUND = new Color(0xF2F5FA);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE1E7EF);
    public static final Color PRIMARY = new Color(0x2D6CDF);
    public static final Color PRIMARY_DARK = new Color(0x1F4FA8);
    public static final Color PRIMARY_SOFT = new Color(0xE8F0FE);
    public static final Color SUCCESS = new Color(0x18A05B);
    public static final Color SUCCESS_SOFT = new Color(0xE3F6EC);
    public static final Color WARNING = new Color(0xD98A16);
    public static final Color WARNING_SOFT = new Color(0xFCF3E2);
    public static final Color DANGER = new Color(0xD34A4A);
    public static final Color DANGER_SOFT = new Color(0xFBEAEA);
    public static final Color TEXT = new Color(0x1B2432);
    public static final Color MUTED = new Color(0x6B7787);
    public static final Color HEADER_BG = new Color(0x16233A);

    // ---- fonts ---------------------------------------------------------
    private static final String FAMILY = pickHebrewFamily();

    public static final Font FONT_TITLE = new Font(FAMILY, Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font(FAMILY, Font.BOLD, 16);
    public static final Font FONT_BODY = new Font(FAMILY, Font.PLAIN, 14);
    public static final Font FONT_BODY_BOLD = new Font(FAMILY, Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font(FAMILY, Font.PLAIN, 12);
    public static final Font FONT_HUGE = new Font(FAMILY, Font.BOLD, 34);
    public static final Font FONT_MONO_BIG = new Font(FAMILY, Font.BOLD, 40);

    private Theme() {
    }

    /** Picks the first installed font that renders Hebrew nicely. */
    private static String pickHebrewFamily() {
        String[] preferred = {"Segoe UI", "Arial", "Noto Sans Hebrew", "David", "Tahoma", "DejaVu Sans", "SansSerif"};
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : preferred) {
            if (installed.contains(family)) return family;
        }
        return "SansSerif";
    }

    // ---- reusable widgets ---------------------------------------------

    /** A white rounded panel with a soft border - the basic building block of the UI. */
    public static JPanel card() {
        JPanel panel = new RoundedPanel(14, CARD, BORDER) {
            @Override
            public Dimension getMaximumSize() {
                // Cards always span the full width of their column, but keep their natural height.
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        return panel;
    }

    /**
     * Vertical column meant to be placed inside a {@link JScrollPane}. Unlike a plain
     * JPanel it always takes the full viewport width, so cards never leave a blank gutter.
     */
    public static class Column extends JPanel implements javax.swing.Scrollable {
        public Column() {
            setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Makes a row inside a vertical card stretch to the full card width. */
    public static <T extends JComponent> T stretchRow(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_TITLE);
        label.setForeground(TEXT);
        return label;
    }

    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SUBTITLE);
        label.setForeground(TEXT);
        return label;
    }

    public static JLabel body(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_BODY);
        label.setForeground(TEXT);
        return label;
    }

    public static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SMALL);
        label.setForeground(MUTED);
        return label;
    }

    public static JButton primaryButton(String text) {
        return new FlatButton(text, PRIMARY, Color.WHITE, PRIMARY_DARK);
    }

    public static JButton successButton(String text) {
        return new FlatButton(text, SUCCESS, Color.WHITE, new Color(0x0F7A44));
    }

    public static JButton dangerButton(String text) {
        return new FlatButton(text, DANGER_SOFT, DANGER, new Color(0xF5D6D6));
    }

    public static JButton ghostButton(String text) {
        FlatButton button = new FlatButton(text, new Color(0xEDF1F7), TEXT, new Color(0xDCE3ED));
        button.setFont(FONT_BODY);
        return button;
    }

    /** Small coloured "chip" used for statuses. */
    public static JLabel chip(String text, Color foreground, Color background) {
        JLabel label = new JLabel(text, JLabel.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setFont(FONT_SMALL);
        label.setForeground(foreground);
        label.setBackground(background);
        label.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return label;
    }

    /** Applies the shared look to a table: tall rows, no vertical grid, styled header. */
    public static JScrollPane styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setForeground(TEXT);
        table.setRowHeight(38);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(0xEFF2F7));
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(PRIMARY_SOFT);
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setBackground(CARD);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BODY_BOLD);
        header.setBackground(new Color(0xF7F9FC));
        header.setForeground(MUTED);
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(CARD);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** Vertical spacer that never steals extra height. */
    public static Component gap(int height) {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(1, height));
        spacer.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        spacer.setMinimumSize(new Dimension(1, height));
        return spacer;
    }

    // ==================================================================
    // Custom painted components
    // ==================================================================

    /** Panel with rounded corners and a 1px border. */
    public static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;
        private final Color line;

        public RoundedPanel(int radius, Color fill, Color line) {
            this.radius = radius;
            this.fill = fill;
            this.line = line;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (line != null) {
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Flat rounded button with a hover state and no focus painting. */
    public static class FlatButton extends JButton {
        private final Color base;
        private final Color hover;
        private boolean hovered;

        public FlatButton(String text, Color base, Color foreground, Color hover) {
            super(text);
            this.base = base;
            this.hover = hover;
            setForeground(foreground);
            setFont(FONT_BODY_BOLD);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = !isEnabled() ? new Color(0xD8DEE7) : (hovered ? hover : base);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }
    }

    /** Slim rounded progress bar used in tables and result charts. */
    public static void paintBar(Graphics2D g2, int x, int y, int width, int height,
                                double ratio, Color fill, Color track, boolean rightToLeft) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(track);
        g2.fillRoundRect(x, y, width, height, height, height);
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * width);
        if (filled > 0) {
            filled = Math.max(filled, height);
            g2.setColor(fill);
            // In a right-to-left interface the bar has to grow from the right edge.
            int barX = rightToLeft ? x + width - filled : x;
            g2.fillRoundRect(barX, y, filled, height, height, height);
        }
    }

    /** Makes a component keep its preferred height inside a BoxLayout column. */
    public static void lockHeight(JComponent component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setPreferredSize(new Dimension(component.getPreferredSize().width, height));
    }

    /** "שאלה אחת" / "3 שאלות" - hard-coded plurals would render "1 שאלות". */
    public static String questionsLabel(int count) {
        return count == 1 ? "שאלה אחת" : count + " שאלות";
    }
}

// ====================================================================================
// ui/HintTextField.java
// ====================================================================================

/** Text field that paints a grey hint while it is empty - clearer than a bare box. */
class HintTextField extends JTextField {

    private final String hint;

    public HintTextField(String hint) {
        this.hint = hint;
        setFont(Theme.FONT_BODY);
        setForeground(Theme.TEXT);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty() && !isFocusOwner()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.FONT_BODY);
            int textWidth = g2.getFontMetrics().stringWidth(hint);
            int x = getComponentOrientation().isLeftToRight()
                    ? getInsets().left
                    : getWidth() - getInsets().right - textWidth;
            int y = getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2;
            g2.drawString(hint, x, y);
            g2.dispose();
        }
    }
}

// ====================================================================================
// ui/SetupDialog.java
// ====================================================================================

/**
 * First-run / settings dialog. Lets the operator paste the bot token and the AI key
 * without editing any file, so the program is runnable straight after download.
 */
class SetupDialog extends JDialog {

    /**
     * A chat-completions provider. All of them speak the OpenAI protocol, so the same
     * {@code ChatGptService} works against any of them - only the URL and model differ.
     */
    private static final class Provider {
        final String label;
        final String url;
        final String model;

        Provider(String label, String url, String model) {
            this.label = label;
            this.url = url;
            this.model = model;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Provider[] PROVIDERS = {
            new Provider("ChatGPT דרך שרת הקורס — טוקן סטודנט",
                    "https://shaitest-production-3066.up.railway.app/api-request", "gpt-4o-mini"),
            new Provider("ChatGPT ‏(OpenAI) — בתשלום", AppConfig.OPENAI_ENDPOINT, "gpt-4o-mini"),
            new Provider("Google Gemini — מכסה חינמית",
                    "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.0-flash"),
            new Provider("Groq — מכסה חינמית",
                    "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
            new Provider("אחר (הזנה ידנית)", "", ""),
    };

    private final AppConfig config;
    private final JTextField botUsernameField = new HintTextField("שם המשתמש של הבוט, לדוגמה: my_survey_bot");
    private final JPasswordField botTokenField = new JPasswordField();
    private final JComboBox<Provider> providerBox = new JComboBox<>(PROVIDERS);
    private final JPasswordField openAiKeyField = new JPasswordField();
    private final JTextField openAiModelField = new HintTextField("gpt-4o-mini");
    private final JTextField openAiUrlField = new HintTextField(AppConfig.OPENAI_ENDPOINT);

    private boolean adjusting = false;
    private boolean confirmed = false;

    public SetupDialog(Frame owner, AppConfig config) {
        super(owner, "הגדרות המערכת", true);
        this.config = config;
        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(Theme.padding(18, 20, 18, 20));

        content.add(alignStart(Theme.title("חיבור המערכת")));
        content.add(Theme.gap(4));
        content.add(alignStart(Theme.hint("הנתונים נשמרים מקומית בקובץ config.properties לצד התוכנית.")));
        content.add(Theme.gap(16));

        content.add(field("שם המשתמש של הבוט (Bot Username)", botUsernameField,
                "מתקבל מ-BotFather. ניתן להזין עם או בלי @"));
        content.add(field("טוקן הבוט (Bot Token)", botTokenField,
                "מתקבל מ-BotFather בפקודה /newbot"));
        content.add(comboField("ספק ה-AI ליצירת שאלות אוטומטית", providerBox,
                "כל הספקים ברשימה עובדים באותו פרוטוקול (OpenAI Chat Completions)"));
        content.add(field("מפתח API / טוקן סטודנט (אופציונלי)", openAiKeyField,
                "נדרש רק ליצירת שאלות אוטומטית. בלעדיו ניתן ליצור סקרים ידנית"));
        content.add(field("מודל", openAiModelField,
                "ממולא אוטומטית לפי הספק. שרת הקורס אינו משתמש בשדה זה"));
        content.add(field("כתובת השרת (Base URL)", openAiUrlField,
                "ממולאת אוטומטית לפי הספק. לשנות רק אם משתמשים בשירות אחר"));

        botUsernameField.setText(config.getBotUsername());
        botTokenField.setText(config.getBotToken());
        openAiKeyField.setText(config.getOpenAiApiKey());
        openAiModelField.setText(config.getOpenAiModel());
        openAiUrlField.setText(config.getOpenAiBaseUrl());
        selectMatchingProvider(config.getOpenAiBaseUrl());

        providerBox.addActionListener(e -> {
            if (adjusting) return;
            Provider provider = (Provider) providerBox.getSelectedItem();
            if (provider == null || provider.url.isEmpty()) return; // "other" - leave the fields alone
            adjusting = true;
            openAiUrlField.setText(provider.url);
            openAiModelField.setText(provider.model);
            adjusting = false;
        });

        JButton save = Theme.primaryButton("שמור והתחבר");
        JButton cancel = Theme.ghostButton("ביטול");
        save.addActionListener(e -> onSave());
        cancel.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        content.add(Theme.gap(8));
        content.add(buttons);

        JScrollPane scroller = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(null);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        scroller.getViewport().setBackground(Theme.BACKGROUND);

        setContentPane(scroller);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(580, 660);
        setLocationRelativeTo(owner);
        getRootPane().setDefaultButton(save);
    }

    private JPanel field(String label, JTextField input, String hint) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        panel.add(alignStart(Theme.body(label)));
        panel.add(Theme.gap(4));

        input.setFont(Theme.FONT_BODY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        input.setPreferredSize(new Dimension(400, 38));
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(input);

        panel.add(Theme.gap(3));
        panel.add(alignStart(Theme.hint(hint)));
        return panel;
    }

    private JPanel comboField(String label, JComboBox<Provider> input, String hint) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));

        panel.add(alignStart(Theme.body(label)));
        panel.add(Theme.gap(4));

        input.setFont(Theme.FONT_BODY);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        input.setPreferredSize(new Dimension(400, 36));
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        input.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        panel.add(input);

        panel.add(Theme.gap(3));
        panel.add(alignStart(Theme.hint(hint)));
        return panel;
    }

    /** Highlights the provider matching a stored URL, falling back to "other". */
    private void selectMatchingProvider(String url) {
        adjusting = true;
        Provider match = PROVIDERS[PROVIDERS.length - 1];
        for (Provider provider : PROVIDERS) {
            if (!provider.url.isEmpty() && provider.url.equalsIgnoreCase(url)) {
                match = provider;
                break;
            }
        }
        providerBox.setSelectedItem(match);
        adjusting = false;
    }

    private void onSave() {
        String username = botUsernameField.getText().trim();
        String token = new String(botTokenField.getPassword()).trim();
        if (username.isEmpty() || token.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "יש להזין את שם המשתמש של הבוט ואת הטוקן שלו כדי להתחבר לטלגרם.",
                    "חסרים פרטים", JOptionPane.WARNING_MESSAGE);
            return;
        }
        config.setBotUsername(username);
        config.setBotToken(token);
        config.setOpenAiApiKey(new String(openAiKeyField.getPassword()).trim());
        config.setOpenAiModel(openAiModelField.getText().trim());
        config.setOpenAiBaseUrl(openAiUrlField.getText().trim());
        config.save();
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private static <T extends javax.swing.JComponent> T alignStart(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    /** Convenience used at startup. */
    public static boolean promptIfNeeded(Frame owner, AppConfig config) {
        if (config.isTelegramConfigured()) return true;
        SetupDialog dialog = new SetupDialog(owner, config);
        dialog.setVisible(true);
        return dialog.isConfirmed();
    }
}

// ====================================================================================
// ui/CommunityPanel.java
// ====================================================================================

/**
 * Always-visible panel showing the GLOBAL community: who is registered, their Telegram
 * username, when they joined, and how many members there are.
 * <p>
 * Deliberately shows nothing about answering state - that is poll data, not community data.
 */
class CommunityPanel extends JPanel {

    private final CommunityService communityService;
    private final CommunityTableModel tableModel = new CommunityTableModel();
    private final JLabel countLabel = new JLabel("0");
    private final JLabel readinessLabel = new JLabel();
    private boolean pollLive = false;
    private final JLabel emptyState = new JLabel();
    private final JScrollPane tableScroll;
    private JPanel centerCards;

    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";

    public CommunityPanel(CommunityService communityService) {
        this.communityService = communityService;

        setLayout(new BorderLayout(0, 12));
        setBackground(Theme.BACKGROUND);
        setBorder(Theme.padding(16, 16, 16, 16));

        // ---- header --------------------------------------------------
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BorderLayout());
        JLabel titleLabel = Theme.title("קהילת המשתמשים");
        titleBox.add(titleLabel, BorderLayout.CENTER);
        JLabel subtitleLabel = Theme.hint("רשימה גלובלית - נשמרת גם לאחר סיום סקר");
        titleBox.add(subtitleLabel, BorderLayout.SOUTH);

        countLabel.setFont(Theme.FONT_HUGE);
        countLabel.setForeground(Theme.PRIMARY);
        countLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel countBox = new JPanel(new BorderLayout());
        countBox.setOpaque(false);
        countBox.add(countLabel, BorderLayout.CENTER);
        JLabel countCaption = Theme.hint("חברים");
        countCaption.setHorizontalAlignment(SwingConstants.CENTER);
        countBox.add(countCaption, BorderLayout.SOUTH);
        countBox.setPreferredSize(new Dimension(90, 60));

        header.add(titleBox, BorderLayout.CENTER);
        header.add(countBox, BorderLayout.LINE_END);
        add(header, BorderLayout.NORTH);

        // ---- table ---------------------------------------------------
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(false);
        tableScroll = Theme.styleTable(table);
        // Wide enough for the "Telegram Username" header the assignment's example uses.
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(165);
        table.getColumnModel().getColumn(2).setPreferredWidth(72);
        table.getColumnModel().getColumn(2).setCellRenderer(new MutedCenteredRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new MutedCenteredRenderer());

        emptyState.setHorizontalAlignment(SwingConstants.CENTER);
        emptyState.setVerticalAlignment(SwingConstants.CENTER);
        emptyState.setFont(Theme.FONT_BODY);
        emptyState.setForeground(Theme.MUTED);
        emptyState.setText("<html><div style='text-align:center'>עדיין אין חברים בקהילה.<br><br>"
                + "כדי להצטרף, שלחו לבוט <b>היי</b> או <b>Hi</b>,<br>או לחצו על <b>Start</b>.</div></html>");

        centerCards = new JPanel(new CardLayout());
        centerCards.setOpaque(false);
        centerCards.add(tableScroll, CARD_TABLE);
        JPanel emptyWrapper = new JPanel(new BorderLayout());
        emptyWrapper.setBackground(Theme.CARD);
        emptyWrapper.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        emptyWrapper.add(emptyState, BorderLayout.CENTER);
        centerCards.add(emptyWrapper, CARD_EMPTY);
        add(centerCards, BorderLayout.CENTER);

        // ---- footer: readiness for starting a poll --------------------
        JPanel footer = new JPanel(new GridLayout(1, 1));
        footer.setOpaque(false);
        readinessLabel.setFont(Theme.FONT_SMALL);
        readinessLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        readinessLabel.setOpaque(true);
        footer.add(readinessLabel);
        add(footer, BorderLayout.SOUTH);

        refresh();
    }

    /** Reflects whether a poll is scheduled or running, so the footer stays truthful. */
    public void setPollLive(boolean pollLive) {
        this.pollLive = pollLive;
        refresh();
    }

    /** Rebuilds the table from the service. Must be called on the Swing thread. */
    public void refresh() {
        List<Member> members = communityService.getMembers();
        tableModel.setMembers(members);
        countLabel.setText(String.valueOf(members.size()));

        boolean empty = members.isEmpty();
        ((CardLayout) centerCards.getLayout()).show(centerCards, empty ? CARD_EMPTY : CARD_TABLE);

        int missing = Poll.MIN_MEMBERS_TO_START - members.size();
        if (pollLive) {
            // While a poll runs the community cannot start another one - saying
            // "ready to start a poll" here would contradict the create-poll tab.
            readinessLabel.setText("●  קיים סקר במערכת · לא ניתן להתחיל סקר נוסף עד לסגירתו");
            readinessLabel.setForeground(Theme.PRIMARY);
            readinessLabel.setBackground(Theme.PRIMARY_SOFT);
        } else if (missing > 0) {
            readinessLabel.setText("⚠  נדרשים עוד " + missing + " חברים כדי שניתן יהיה להתחיל סקר (מינימום "
                    + Poll.MIN_MEMBERS_TO_START + ")");
            readinessLabel.setForeground(Theme.WARNING);
            readinessLabel.setBackground(Theme.WARNING_SOFT);
        } else {
            readinessLabel.setText("✔  הקהילה מוכנה - ניתן להתחיל סקר");
            readinessLabel.setForeground(Theme.SUCCESS);
            readinessLabel.setBackground(Theme.SUCCESS_SOFT);
        }
        revalidate();
        repaint();
    }

    /** Highlights the row of a member that has just joined. */
    public void flashNewMember() {
        // The count colour pulses briefly so the operator notices the change.
        countLabel.setForeground(Theme.SUCCESS);
        javax.swing.Timer timer = new javax.swing.Timer(1200, e -> countLabel.setForeground(Theme.PRIMARY));
        timer.setRepeats(false);
        timer.start();
    }

    // ------------------------------------------------------------------

    private static class CommunityTableModel extends AbstractTableModel {
        private final String[] columns = {"שם", "Telegram Username", "הצטרפות"};
        private List<Member> members = new ArrayList<>();

        void setMembers(List<Member> members) {
            this.members = members;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return members.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Member member = members.get(rowIndex);
            switch (columnIndex) {
                case 0: return member.getFullName();
                case 1: return member.getUsernameDisplay();
                case 2: return member.getJoinedAtShort();
                default: return "";
            }
        }
    }

    private static class MutedCenteredRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            component.setForeground(isSelected ? Theme.TEXT : Theme.MUTED);
            component.setFont(Theme.FONT_SMALL);
            return component;
        }
    }
}

// ====================================================================================
// ui/CreatePollPanel.java
// ====================================================================================

/**
 * Poll creation screen: manual authoring or ChatGPT generation, plus the send schedule.
 * <p>
 * The form validates itself continuously and always explains, in one sentence, why the
 * "start poll" button is disabled - the operator is never left guessing.
 */
class CreatePollPanel extends JPanel {

    private final CommunityService communityService;
    private final PollService pollService;
    private final ChatGptService chatGptService;
    private final Consumer<String> statusReporter;

    private final JTextField titleField = new HintTextField("לדוגמה: העדפות טכנולוגיות בקרב מהנדסי תוכנה");

    private final JRadioButton manualMode = new JRadioButton("כתיבה ידנית של השאלות");
    private final JRadioButton gptMode = new JRadioButton("יצירה אוטומטית באמצעות ChatGPT");
    private final JPanel modeCards = new JPanel();
    private JComponent manualHintPanel;
    private JComponent gptPanel;

    private final JTextField topicField = new HintTextField("נושא כללי לסקר, לדוגמה: הרגלי עבודה מרחוק");
    private final JSpinner gptQuestionCount = new JSpinner(new SpinnerNumberModel(3, Poll.MIN_QUESTIONS, Poll.MAX_QUESTIONS, 1));
    private final JSpinner gptOptionCount = new JSpinner(new SpinnerNumberModel(4, Question.MIN_OPTIONS, Question.MAX_OPTIONS, 1));
    private final JButton generateButton = Theme.primaryButton("צור שאלות באמצעות ChatGPT");
    private final JLabel gptStatus = Theme.hint(" ");

    private final JPanel questionsContainer = new JPanel();
    private final List<QuestionEditor> questionEditors = new ArrayList<>();
    private final JButton addQuestionButton = Theme.ghostButton("+  הוסף שאלה");

    private final JRadioButton sendNow = new JRadioButton("שליחה מיידית");
    private final JRadioButton sendLater = new JRadioButton("שליחה מושהית בעוד");
    private final JSpinner delayMinutes = new JSpinner(new SpinnerNumberModel(2, 1, 120, 1));

    private final JLabel validationLabel = new JLabel();
    private final JButton startButton = Theme.successButton("▶  התחל סקר");

    private boolean pollLive = false;

    public CreatePollPanel(CommunityService communityService,
                           PollService pollService,
                           ChatGptService chatGptService,
                           Consumer<String> statusReporter) {
        this.communityService = communityService;
        this.pollService = pollService;
        this.chatGptService = chatGptService;
        this.statusReporter = statusReporter;

        setLayout(new BorderLayout(0, 0));
        setBackground(Theme.BACKGROUND);

        Theme.Column content = new Theme.Column();
        content.setBorder(Theme.padding(16, 16, 16, 16));

        content.add(buildDetailsCard());
        content.add(Theme.gap(14));
        content.add(buildModeCard());
        content.add(Theme.gap(14));
        content.add(buildQuestionsCard());
        content.add(Theme.gap(14));
        content.add(buildScheduleCard());
        content.add(Theme.gap(8));

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Theme.BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        add(scrollPane, BorderLayout.CENTER);

        add(buildFooter(), BorderLayout.SOUTH);

        addQuestion(); // start with one empty question
        validateForm();
    }

    // ==================================================================
    // Sections
    // ==================================================================

    private JComponent buildDetailsCard() {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(alignStart(Theme.subtitle("1. פרטי הסקר")));
        card.add(Theme.gap(4));
        card.add(alignStart(Theme.hint("שם קצר שיוצג למשתתפים בטלגרם ובמסך התוצאות")));
        card.add(Theme.gap(10));
        Theme.lockHeight(titleField, 38);
        card.add(titleField);
        titleField.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleField.getDocument().addDocumentListener(new SimpleDocumentListener(this::validateForm));
        return card;
    }

    private JComponent buildModeCard() {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(alignStart(Theme.subtitle("2. אופן יצירת השאלות")));
        card.add(Theme.gap(10));

        ButtonGroup group = new ButtonGroup();
        group.add(manualMode);
        group.add(gptMode);
        manualMode.setSelected(true);
        styleRadio(manualMode);
        styleRadio(gptMode);

        // Stacked vertically on purpose: a horizontal row wraps out of sight when the
        // work area is narrow, which would hide the ChatGPT option completely.
        JPanel radios = new JPanel();
        radios.setOpaque(false);
        radios.setLayout(new BoxLayout(radios, BoxLayout.Y_AXIS));
        manualMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        gptMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        radios.add(manualMode);
        radios.add(Box.createVerticalStrut(6));
        radios.add(gptMode);
        card.add(alignStart(radios));
        card.add(Theme.gap(10));

        modeCards.setOpaque(false);
        modeCards.setLayout(new BoxLayout(modeCards, BoxLayout.Y_AXIS));
        manualHintPanel = buildManualHint();
        gptPanel = buildGptPanel();
        gptPanel.setVisible(false);
        modeCards.add(Theme.stretchRow(manualHintPanel));
        modeCards.add(gptPanel);
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(modeCards);

        manualMode.addActionListener(e -> switchMode(false));
        gptMode.addActionListener(e -> switchMode(true));
        return card;
    }

    /** Shows only the relevant sub-form, so no empty space is left behind. */
    private void switchMode(boolean useChatGpt) {
        manualHintPanel.setVisible(!useChatGpt);
        gptPanel.setVisible(useChatGpt);
        modeCards.revalidate();
        modeCards.repaint();
        revalidate();
        repaint();
        validateForm();
    }

    private JComponent buildManualHint() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(Theme.hint("כתבו את השאלות ואת אפשרויות התשובה בחלק הבא במסך."), BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildGptPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(alignStart(Theme.body("נושא הסקר:")));
        panel.add(Theme.gap(6));
        Theme.lockHeight(topicField, 38);
        topicField.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(topicField);
        panel.add(Theme.gap(12));

        styleSpinner(gptQuestionCount);
        styleSpinner(gptOptionCount);
        JPanel counts = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        counts.setOpaque(false);
        counts.add(Theme.body("מספר שאלות:"));
        counts.add(Box.createHorizontalStrut(8));
        counts.add(gptQuestionCount);
        counts.add(Box.createHorizontalStrut(28));
        counts.add(Theme.body("אפשרויות לכל שאלה:"));
        counts.add(Box.createHorizontalStrut(8));
        counts.add(gptOptionCount);
        panel.add(Theme.stretchRow(counts));
        panel.add(Theme.gap(12));

        generateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        generateButton.setPreferredSize(new Dimension(300, 42));
        generateButton.setMaximumSize(new Dimension(300, 42));
        panel.add(generateButton);
        panel.add(Theme.gap(8));

        gptStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(gptStatus);

        topicField.addActionListener(e -> generateWithChatGpt()); // Enter generates
        generateButton.addActionListener(e -> generateWithChatGpt());
        topicField.getDocument().addDocumentListener(new SimpleDocumentListener(this::validateForm));
        return panel;
    }

    private JComponent buildQuestionsCard() {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Theme.subtitle("3. שאלות הסקר"), BorderLayout.LINE_START);
        header.add(Theme.hint("בין " + Poll.MIN_QUESTIONS + " ל-" + Poll.MAX_QUESTIONS + " שאלות · "
                + Question.MIN_OPTIONS + "-" + Question.MAX_OPTIONS + " אפשרויות לכל שאלה"), BorderLayout.LINE_END);
        card.add(Theme.stretchRow(header));
        card.add(Theme.gap(12));

        questionsContainer.setLayout(new BoxLayout(questionsContainer, BoxLayout.Y_AXIS));
        questionsContainer.setOpaque(false);
        questionsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(questionsContainer);
        card.add(Theme.gap(10));

        addQuestionButton.addActionListener(e -> addQuestion());
        card.add(alignStart(addQuestionButton));
        return card;
    }

    private JComponent buildScheduleCard() {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(alignStart(Theme.subtitle("4. מועד שליחת הסקר")));
        card.add(Theme.gap(10));

        ButtonGroup group = new ButtonGroup();
        group.add(sendNow);
        group.add(sendLater);
        sendNow.setSelected(true);
        styleRadio(sendNow);
        styleRadio(sendLater);
        styleSpinner(delayMinutes);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        row.setOpaque(false);
        row.add(sendNow);
        row.add(Box.createHorizontalStrut(24));
        row.add(sendLater);
        row.add(Box.createHorizontalStrut(8));
        row.add(delayMinutes);
        row.add(Box.createHorizontalStrut(8));
        row.add(Theme.body("דקות"));
        card.add(alignStart(row));

        card.add(Theme.gap(8));
        card.add(alignStart(Theme.hint("בשליחה מושהית יוצג במסך \"סקר פעיל\" מד ספירה לאחור עד לרגע השליחה. "
                + "משך המענה לסקר הוא " + Poll.DURATION_MINUTES + " דקות, ותזכורת נשלחת לאחר "
                + Poll.REMINDER_AFTER_MINUTES + " דקות.")));

        sendNow.addActionListener(e -> {
            delayMinutes.setEnabled(false);
            validateForm();
        });
        sendLater.addActionListener(e -> {
            delayMinutes.setEnabled(true);
            validateForm();
        });
        delayMinutes.setEnabled(false);
        return card;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(Theme.CARD);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                Theme.padding(12, 18, 12, 18)));

        validationLabel.setFont(Theme.FONT_BODY);
        footer.add(validationLabel, BorderLayout.CENTER);

        startButton.setFont(Theme.FONT_SUBTITLE);
        startButton.setPreferredSize(new Dimension(190, 46));
        startButton.addActionListener(e -> startPoll());
        footer.add(startButton, BorderLayout.LINE_END);
        return footer;
    }

    // ==================================================================
    // Question editors
    // ==================================================================

    private void addQuestion() {
        if (questionEditors.size() >= Poll.MAX_QUESTIONS) return;
        QuestionEditor editor = new QuestionEditor();
        questionEditors.add(editor);
        questionsContainer.add(editor);
        questionsContainer.add(Theme.gap(10));
        renumberQuestions();
        validateForm();
        questionsContainer.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        questionsContainer.revalidate();
        questionsContainer.repaint();
        // Show the operator the question that was just added instead of leaving it below the fold.
        SwingUtilities.invokeLater(() -> editor.scrollRectToVisible(
                new Rectangle(0, 0, editor.getWidth(), editor.getHeight())));
    }

    private void removeQuestion(QuestionEditor editor) {
        if (questionEditors.size() <= Poll.MIN_QUESTIONS) return;
        questionEditors.remove(editor);
        rebuildQuestionsContainer();
        validateForm();
    }

    private void rebuildQuestionsContainer() {
        questionsContainer.removeAll();
        for (QuestionEditor editor : questionEditors) {
            questionsContainer.add(editor);
            questionsContainer.add(Theme.gap(10));
        }
        renumberQuestions();
        // Editors built after the window's RTL pass would otherwise render mirror-flipped.
        questionsContainer.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        questionsContainer.revalidate();
        questionsContainer.repaint();
    }

    private void renumberQuestions() {
        for (int i = 0; i < questionEditors.size(); i++) {
            questionEditors.get(i).setIndex(i + 1, questionEditors.size() > Poll.MIN_QUESTIONS);
        }
        addQuestionButton.setEnabled(!pollLive && questionEditors.size() < Poll.MAX_QUESTIONS);
    }

    /** Replaces the editors with questions produced by ChatGPT, for review and editing. */
    private void loadQuestions(List<Question> questions) {
        questionEditors.clear();
        for (Question question : questions) {
            QuestionEditor editor = new QuestionEditor();
            editor.load(question);
            questionEditors.add(editor);
        }
        rebuildQuestionsContainer();
        validateForm();
    }

    // ==================================================================
    // Actions
    // ==================================================================

    private void generateWithChatGpt() {
        String topic = topicField.getText().trim();
        if (topic.isEmpty()) {
            showError("יש להזין נושא לסקר לפני יצירה אוטומטית.");
            topicField.requestFocusInWindow();
            return;
        }
        int questionCount = (Integer) gptQuestionCount.getValue();
        int optionCount = (Integer) gptOptionCount.getValue();

        generateButton.setEnabled(false);
        gptStatus.setText("…  מייצר שאלות באמצעות ChatGPT, נא להמתין...");
        gptStatus.setForeground(Theme.PRIMARY);
        statusReporter.accept("פונה ל-ChatGPT ליצירת שאלות...");

        new SwingWorker<List<Question>, Void>() {
            @Override
            protected List<Question> doInBackground() {
                return chatGptService.generateQuestions(topic, questionCount, optionCount);
            }

            @Override
            protected void done() {
                generateButton.setEnabled(!pollLive);
                try {
                    List<Question> questions = get();
                    loadQuestions(questions);
                    if (titleField.getText().isBlank()) {
                        titleField.setText(topic);
                    }
                    gptStatus.setText("✔  נוצרו " + Theme.questionsLabel(questions.size()) + ". ניתן לערוך אותן לפני השליחה.");
                    gptStatus.setForeground(Theme.SUCCESS);
                    statusReporter.accept("ChatGPT יצר " + Theme.questionsLabel(questions.size()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    gptStatus.setText("✕  " + cause.getMessage());
                    gptStatus.setForeground(Theme.DANGER);
                    showError(cause.getMessage());
                    statusReporter.accept("יצירת שאלות באמצעות ChatGPT נכשלה");
                }
            }
        }.execute();
    }

    private void startPoll() {
        List<Question> questions;
        try {
            questions = collectQuestions();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }

        String title = titleField.getText().isBlank() ? "סקר חדש" : titleField.getText().trim();
        int delay = sendLater.isSelected() ? (Integer) delayMinutes.getValue() : 0;

        try {
            Poll poll = new Poll(title, questions);
            pollService.schedulePoll(poll, delay);
            statusReporter.accept(delay == 0
                    ? "הסקר \"" + title + "\" נשלח למשתתפים"
                    : "הסקר \"" + title + "\" נקבע לשליחה בעוד " + delay + " דקות");
        } catch (IllegalStateException | IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private List<Question> collectQuestions() {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < questionEditors.size(); i++) {
            questions.add(questionEditors.get(i).toQuestion(i + 1));
        }
        return questions;
    }

    // ==================================================================
    // Validation
    // ==================================================================

    /** Recomputes the footer message and the enabled state of the start button. */
    public void validateForm() {
        String problem = findProblem();
        boolean valid = problem == null;
        startButton.setEnabled(valid);
        if (valid) {
            validationLabel.setText("✔  הכול מוכן - " + Theme.questionsLabel(questionEditors.size()) + ", "
                    + communityService.size() + " חברים בקהילה");
            validationLabel.setForeground(Theme.SUCCESS);
        } else {
            validationLabel.setText("⚠  " + problem);
            validationLabel.setForeground(Theme.WARNING);
        }
        renumberQuestions();
    }

    private String findProblem() {
        if (pollLive) {
            return "קיים סקר פעיל. ניתן להתחיל סקר חדש רק לאחר סגירתו.";
        }
        if (communityService.size() < Poll.MIN_MEMBERS_TO_START) {
            return "נדרשים לפחות " + Poll.MIN_MEMBERS_TO_START + " חברים בקהילה (כרגע " + communityService.size() + ").";
        }
        if (questionEditors.isEmpty()) {
            return "יש להוסיף לפחות שאלה אחת.";
        }
        for (int i = 0; i < questionEditors.size(); i++) {
            String problem = questionEditors.get(i).findProblem(i + 1);
            if (problem != null) return problem;
        }
        return null;
    }

    /** Called by the main window whenever a poll starts or ends. */
    public void setPollLive(boolean pollLive) {
        this.pollLive = pollLive;
        generateButton.setEnabled(!pollLive);
        for (QuestionEditor editor : questionEditors) {
            editor.setEditable(!pollLive);
        }
        titleField.setEnabled(!pollLive);
        topicField.setEnabled(!pollLive);
        sendNow.setEnabled(!pollLive);
        sendLater.setEnabled(!pollLive);
        delayMinutes.setEnabled(!pollLive && sendLater.isSelected());
        validateForm();
    }

    /** Clears the form so a new poll can be composed after the previous one closed. */
    public void resetForm() {
        titleField.setText("");
        topicField.setText("");
        gptStatus.setText(" ");
        questionEditors.clear();
        rebuildQuestionsContainer();
        addQuestion();
        sendNow.setSelected(true);
        delayMinutes.setEnabled(false);
        validateForm();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "לא ניתן להמשיך", JOptionPane.WARNING_MESSAGE);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static JComponent alignStart(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static void styleRadio(JRadioButton radio) {
        radio.setFont(Theme.FONT_BODY);
        radio.setForeground(Theme.TEXT);
        radio.setOpaque(false);
        radio.setFocusPainted(false);
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setFont(Theme.FONT_BODY);
        spinner.setPreferredSize(new Dimension(70, 34));
    }

    /** DocumentListener that runs the same action for every kind of change. */
    private static class SimpleDocumentListener implements DocumentListener {
        private final Runnable action;

        SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override public void insertUpdate(DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(DocumentEvent e) { action.run(); }
    }

    // ==================================================================
    // One question editor (text + 2..4 options)
    // ==================================================================

    private class QuestionEditor extends JPanel {

        private final JLabel indexLabel = Theme.subtitle("שאלה 1");
        private final JButton removeButton = Theme.dangerButton("הסר");
        private final JTextField textField = new HintTextField("נוסח השאלה");
        private final JPanel optionsPanel = new JPanel();
        private final List<JTextField> optionFields = new ArrayList<>();
        private final List<JButton> optionRemoveButtons = new ArrayList<>();
        private final JButton addOptionButton = Theme.ghostButton("+  הוסף אפשרות");

        QuestionEditor() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(true);
            setBackground(new java.awt.Color(0xF8FAFD));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER),
                    Theme.padding(12, 14, 12, 14)));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(indexLabel, BorderLayout.LINE_START);
            removeButton.setFont(Theme.FONT_SMALL);
            removeButton.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
            removeButton.addActionListener(e -> removeQuestion(this));
            header.add(removeButton, BorderLayout.LINE_END);
            add(alignStart(header));

            add(Theme.gap(8));
            Theme.lockHeight(textField, 36);
            textField.setAlignmentX(Component.LEFT_ALIGNMENT);
            textField.getDocument().addDocumentListener(new SimpleDocumentListener(CreatePollPanel.this::validateForm));
            add(textField);

            add(Theme.gap(10));
            JLabel optionsTitle = Theme.hint("אפשרויות התשובה:");
            add(alignStart(optionsTitle));
            add(Theme.gap(6));

            optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
            optionsPanel.setOpaque(false);
            optionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(optionsPanel);

            add(Theme.gap(8));
            addOptionButton.setFont(Theme.FONT_SMALL);
            addOptionButton.addActionListener(e -> addOption(""));
            add(alignStart(addOptionButton));

            addOption("");
            addOption("");
        }

        void setIndex(int index, boolean removable) {
            indexLabel.setText("שאלה " + index);
            removeButton.setVisible(removable);
        }

        void addOption(String text) {
            if (optionFields.size() >= Question.MAX_OPTIONS) return;
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextField field = new HintTextField("אפשרות " + (optionFields.size() + 1));
            field.setText(text);
            field.getDocument().addDocumentListener(new SimpleDocumentListener(CreatePollPanel.this::validateForm));
            row.add(field, BorderLayout.CENTER);

            JButton remove = Theme.dangerButton("×");
            remove.setFont(Theme.FONT_BODY_BOLD);
            remove.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            remove.setPreferredSize(new Dimension(38, 32));
            remove.setToolTipText("הסר אפשרות זו");
            remove.addActionListener(e -> {
                if (optionFields.size() <= Question.MIN_OPTIONS) {
                    showError("לכל שאלה חייבות להיות לפחות " + Question.MIN_OPTIONS + " אפשרויות תשובה.");
                    return;
                }
                optionFields.remove(field);
                optionRemoveButtons.remove(remove);
                optionsPanel.remove(row);
                refreshOptionButtons();
                optionsPanel.revalidate();
                optionsPanel.repaint();
                validateForm();
            });
            row.add(remove, BorderLayout.LINE_END);

            Theme.lockHeight(row, 36);
            // Rows built after the window's RTL pass have to be oriented themselves,
            // otherwise the "×" jumps to the wrong side and the hint text flips.
            row.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            optionFields.add(field);
            optionRemoveButtons.add(remove);
            optionsPanel.add(row);
            optionsPanel.add(Theme.gap(6));
            refreshOptionButtons();
            optionsPanel.revalidate();
            optionsPanel.repaint();
            validateForm();
        }

        private void refreshOptionButtons() {
            addOptionButton.setEnabled(!pollLive && optionFields.size() < Question.MAX_OPTIONS);
        }

        void load(Question question) {
            textField.setText(question.getText());
            optionFields.clear();
            optionRemoveButtons.clear();
            optionsPanel.removeAll();
            for (String option : question.getOptions()) {
                addOption(option);
            }
            optionsPanel.revalidate();
            optionsPanel.repaint();
        }

        void setEditable(boolean editable) {
            textField.setEnabled(editable);
            for (JTextField field : optionFields) {
                field.setEnabled(editable);
            }
            addOptionButton.setEnabled(editable && optionFields.size() < Question.MAX_OPTIONS);
            removeButton.setEnabled(editable);
            // The "×" buttons must freeze with the rest of the form while a poll is live.
            for (JButton button : optionRemoveButtons) {
                button.setEnabled(editable);
            }
        }

        /** @return a human readable problem, or {@code null} when this question is valid. */
        String findProblem(int index) {
            if (textField.getText().isBlank()) {
                return "שאלה " + index + ": חסר נוסח השאלה.";
            }
            int filled = 0;
            for (JTextField field : optionFields) {
                if (!field.getText().isBlank()) filled++;
            }
            if (filled < Question.MIN_OPTIONS) {
                return "שאלה " + index + ": נדרשות לפחות " + Question.MIN_OPTIONS + " אפשרויות תשובה.";
            }
            return null;
        }

        Question toQuestion(int index) {
            List<String> options = new ArrayList<>();
            for (JTextField field : optionFields) {
                if (!field.getText().isBlank()) options.add(field.getText().trim());
            }
            if (textField.getText().isBlank()) {
                throw new IllegalArgumentException("שאלה " + index + ": חסר נוסח השאלה.");
            }
            if (options.size() < Question.MIN_OPTIONS) {
                throw new IllegalArgumentException("שאלה " + index + ": נדרשות לפחות "
                        + Question.MIN_OPTIONS + " אפשרויות תשובה.");
            }
            return new Question(textField.getText().trim(), options);
        }
    }

    /** Exposes the start button so the main window can focus it. */
    public JButton getStartButton() {
        return startButton;
    }
}

// ====================================================================================
// ui/LivePollPanel.java
// ====================================================================================

/**
 * Live view of the CURRENT poll: the countdown before a delayed send, the running
 * statistics while the poll is open, and the per-participant answering progress.
 * <p>
 * This area is about ONE poll only - the global community list lives in its own panel.
 */
class LivePollPanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_SCHEDULED = "scheduled";
    private static final String CARD_RUNNING = "running";

    private final CardLayout cardLayout = new CardLayout();
    private final Runnable onCloseRequested;
    private final Runnable onCancelScheduleRequested;

    // scheduled view
    private final JLabel scheduledTitle = new JLabel("", SwingConstants.CENTER);
    private final JLabel scheduledCountdown = new JLabel("00:00", SwingConstants.CENTER);
    private final JLabel scheduledDetails = new JLabel("", SwingConstants.CENTER);
    private final ScheduleBar scheduleBar = new ScheduleBar();
    private final JButton cancelScheduleButton = Theme.dangerButton("בטל את השליחה המתוזמנת");
    // Tooltips: both buttons are destructive and their consequences differ.

    // running view
    private final JLabel banner = new JLabel();
    private final StatCard participantsCard = new StatCard("משתתפים בסקר", Theme.PRIMARY);
    private final StatCard completedCard = new StatCard("השלימו", Theme.SUCCESS);
    private final StatCard pendingCard = new StatCard("טרם השלימו", Theme.WARNING);
    private final StatCard timeCard = new StatCard("זמן שנותר", Theme.TEXT);
    private final ParticipantsTableModel tableModel = new ParticipantsTableModel();
    private final JLabel reminderNote = new JLabel(" ");
    private final JButton closeNowButton = Theme.dangerButton("סגור את הסקר עכשיו");

    private Poll poll;

    public LivePollPanel(Runnable onCloseRequested, Runnable onCancelScheduleRequested) {
        this.onCloseRequested = onCloseRequested;
        this.onCancelScheduleRequested = onCancelScheduleRequested;
        setLayout(cardLayout);
        setBackground(Theme.BACKGROUND);

        add(buildEmptyView(), CARD_EMPTY);
        add(buildScheduledView(), CARD_SCHEDULED);
        add(buildRunningView(), CARD_RUNNING);
        cardLayout.show(this, CARD_EMPTY);
    }

    // ==================================================================
    // Views
    // ==================================================================

    private JPanel buildEmptyView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);
        JLabel label = new JLabel("<html><div style='text-align:center'>"
                + "<span style='font-size:16pt'>אין כרגע סקר פעיל</span><br><br>"
                + "עברו ללשונית <b>יצירת סקר</b> כדי ליצור ולשלוח סקר חדש.</div></html>",
                SwingConstants.CENTER);
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.FONT_BODY);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildScheduledView() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(Theme.BACKGROUND);

        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(560, 300));
        card.setMaximumSize(new Dimension(560, 300));

        JLabel status = Theme.chip("ממתין לשליחה", Theme.PRIMARY_DARK, Theme.PRIMARY_SOFT);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(status);
        card.add(Theme.gap(16));

        scheduledTitle.setFont(Theme.FONT_SUBTITLE);
        scheduledTitle.setForeground(Theme.TEXT);
        scheduledTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(scheduledTitle);
        card.add(Theme.gap(10));

        JLabel caption = new JLabel("הסקר יישלח בעוד", SwingConstants.CENTER);
        caption.setFont(Theme.FONT_BODY);
        caption.setForeground(Theme.MUTED);
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(caption);

        scheduledCountdown.setFont(Theme.FONT_MONO_BIG);
        scheduledCountdown.setForeground(Theme.PRIMARY);
        scheduledCountdown.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(scheduledCountdown);
        card.add(Theme.gap(14));

        scheduleBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        scheduleBar.setPreferredSize(new Dimension(420, 12));
        scheduleBar.setMaximumSize(new Dimension(420, 12));
        scheduleBar.setOpaque(false);
        card.add(scheduleBar);
        card.add(Theme.gap(16));

        scheduledDetails.setFont(Theme.FONT_SMALL);
        scheduledDetails.setForeground(Theme.MUTED);
        scheduledDetails.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(scheduledDetails);
        card.add(Theme.gap(16));

        cancelScheduleButton.setFont(Theme.FONT_SMALL);
        cancelScheduleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancelScheduleButton.setToolTipText("הסקר לא יישלח כלל. לא נשלחו הודעות לאף משתתף, ולכן גם לא ייווצרו תוצאות.");
        cancelScheduleButton.addActionListener(e -> onCancelScheduleRequested.run());
        card.add(cancelScheduleButton);

        wrapper.add(card);
        return wrapper;
    }

    /** Thin bar showing how much of the waiting time has already elapsed. */
    private class ScheduleBar extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            double ratio = poll == null ? 0 : poll.getScheduleProgress();
            Theme.paintBar(g2, 0, 1, getWidth(), 10, ratio, Theme.PRIMARY, new Color(0xE7ECF3),
                    !getComponentOrientation().isLeftToRight());
            g2.dispose();
        }
    }

    private JPanel buildRunningView() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(Theme.padding(16, 16, 16, 16));

        // ---- banner + stats -----------------------------------------
        JPanel north = new JPanel(new BorderLayout(0, 12));
        north.setOpaque(false);

        banner.setFont(Theme.FONT_SUBTITLE);
        banner.setOpaque(true);
        banner.setBorder(Theme.padding(12, 16, 12, 16));
        north.add(banner, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(participantsCard);
        stats.add(completedCard);
        stats.add(pendingCard);
        stats.add(timeCard);
        north.add(stats, BorderLayout.CENTER);

        panel.add(north, BorderLayout.NORTH);

        // ---- participants table --------------------------------------
        JPanel tableCard = new JPanel(new BorderLayout(0, 8));
        tableCard.setOpaque(false);

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        tableHeader.add(Theme.subtitle("מצב המענה של משתתפי הסקר"), BorderLayout.LINE_START);
        tableHeader.add(Theme.hint("מתעדכן אוטומטית בזמן אמת"), BorderLayout.LINE_END);
        tableCard.add(tableHeader, BorderLayout.NORTH);

        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(false);
        table.setRowHeight(44);
        tableCard.add(Theme.styleTable(table), BorderLayout.CENTER);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setCellRenderer(new ProgressRenderer());
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setCellRenderer(new StateRenderer());
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(3).setCellRenderer(new CenteredMutedRenderer());

        panel.add(tableCard, BorderLayout.CENTER);

        // ---- footer --------------------------------------------------
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        reminderNote.setFont(Theme.FONT_SMALL);
        reminderNote.setForeground(Theme.MUTED);
        footer.add(reminderNote, BorderLayout.LINE_START);
        closeNowButton.setFont(Theme.FONT_SMALL);
        closeNowButton.setToolTipText("סוגר את הסקר מיד. לא יתקבלו תשובות נוספות, והתוצאות יוצגו בלשונית \"תוצאות\".");
        closeNowButton.addActionListener(e -> onCloseRequested.run());
        footer.add(closeNowButton, BorderLayout.LINE_END);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    // ==================================================================
    // Updates (always called on the Swing thread)
    // ==================================================================

    public void showPoll(Poll poll) {
        boolean differentPoll = this.poll == null || poll == null || this.poll.getId() != poll.getId();
        this.poll = poll;
        // The reminder line belongs to one specific poll - never carry it into the next one.
        if (differentPoll) clearReminderNote();
        refresh();
    }

    public void clear() {
        this.poll = null;
        clearReminderNote();
        cardLayout.show(this, CARD_EMPTY);
    }

    private void clearReminderNote() {
        reminderNote.setText(" ");
        reminderNote.setForeground(Theme.MUTED);
    }

    public void noteReminders(int remindedCount) {
        reminderNote.setText(remindedCount == 0
                ? "כל המשתתפים השלימו - לא נשלחו תזכורות"
                : "⏱ נשלחו תזכורות ל-" + remindedCount + " משתתפים שטרם השלימו (תזכורת אחת לכל משתתף)");
        reminderNote.setForeground(Theme.WARNING);
    }

    /** Recomputes everything from the poll's current state. */
    public void refresh() {
        if (poll == null) {
            cardLayout.show(this, CARD_EMPTY);
            return;
        }

        if (poll.getStatus() == PollStatus.SCHEDULED) {
            scheduledTitle.setText("הסקר \"" + poll.getTitle() + "\" ממתין לשליחה");
            scheduledCountdown.setText(Poll.formatDuration(poll.getSecondsUntilStart()));
            scheduledDetails.setText(Theme.questionsLabel(poll.getQuestionCount()) + " · יישלח אוטומטית לכל "
                    + "חברי הקהילה בתום הספירה · משך המענה " + Poll.DURATION_MINUTES + " דקות");
            scheduleBar.repaint();
            cardLayout.show(this, CARD_SCHEDULED);
            return;
        }

        cardLayout.show(this, CARD_RUNNING);
        tableModel.setRows(poll.getParticipants());

        participantsCard.setValue(String.valueOf(poll.getParticipantCount()));
        completedCard.setValue(String.valueOf(poll.getCompletedCount()));
        pendingCard.setValue(String.valueOf(poll.getNotCompletedCount()));

        if (poll.getStatus() == PollStatus.ACTIVE) {
            timeCard.setValue(Poll.formatDuration(poll.getSecondsRemaining()));
            // HTML so a long title wraps instead of being silently cut off.
            banner.setText("<html>●&nbsp; הסקר \"" + poll.getTitle() + "\" נשלח ופעיל כעת · "
                    + Theme.questionsLabel(poll.getQuestionCount()) + " · נותרו "
                    + Poll.formatDuration(poll.getSecondsRemaining()) + "</html>");
            banner.setForeground(Theme.SUCCESS);
            banner.setBackground(Theme.SUCCESS_SOFT);
            closeNowButton.setVisible(true);
        } else if (poll.getStatus() == PollStatus.CLOSED) {
            timeCard.setValue("—");
            banner.setText("<html>■&nbsp; " + poll.getCloseReason() + "<br>"
                    + poll.getCompletedCount() + " מתוך " + poll.getParticipantCount()
                    + " משתתפים השלימו · התוצאות זמינות בלשונית \"תוצאות\"</html>");
            banner.setForeground(Theme.TEXT);
            banner.setBackground(new Color(0xEDF1F7));
            closeNowButton.setVisible(false);
        }
    }

    // ==================================================================
    // Table model + renderers
    // ==================================================================

    private static class ParticipantsTableModel extends AbstractTableModel {
        private final String[] columns = {"שם המשתתף", "התקדמות", "מצב", "תזכורת"};
        private List<ParticipantProgress> rows = new ArrayList<>();

        void setRows(List<ParticipantProgress> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ParticipantProgress progress = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return progress.getMember().getFullName();
                case 1: return progress;
                case 2: return progress.getState();
                case 3: return progress.isReminderSent() ? "נשלחה" : "—";
                default: return "";
            }
        }
    }

    /** Draws "2/3" next to a slim progress bar. */
    private static class ProgressRenderer implements TableCellRenderer {
        private final JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (value == null) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                boolean rightToLeft = !getComponentOrientation().isLeftToRight();
                String text = value.getProgressText();
                g2.setFont(Theme.FONT_BODY_BOLD);
                int textWidth = g2.getFontMetrics().stringWidth(text) + 10;
                int barWidth = Math.max(40, getWidth() - textWidth - 16);
                int barY = getHeight() / 2 - 5;

                Color fill = value.isCompleted() ? Theme.SUCCESS
                        : (value.getAnsweredCount() == 0 ? new Color(0xC7CFDB) : Theme.PRIMARY);
                Theme.paintBar(g2, 8, barY, barWidth, 10, value.getProgressRatio(), fill, new Color(0xEDF1F7), rightToLeft);

                g2.setColor(Theme.TEXT);
                g2.drawString(text, 8 + barWidth + 8, getHeight() / 2 + 5);
                g2.dispose();
            }
        };
        private ParticipantProgress value;

        ProgressRenderer() {
            panel.setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            this.value = (ParticipantProgress) value;
            panel.setBackground(isSelected ? Theme.PRIMARY_SOFT : Theme.CARD);
            // Inherit the table's direction so the progress bar fills from the right.
            panel.applyComponentOrientation(table.getComponentOrientation());
            return panel;
        }
    }

    /** Draws the state as a coloured chip. */
    private static class StateRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            ParticipantProgress.State state = (ParticipantProgress.State) value;
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.FONT_BODY_BOLD);
            setText(state.getHebrewLabel());
            setOpaque(true);
            setBackground(isSelected ? Theme.PRIMARY_SOFT : Theme.CARD);
            switch (state) {
                case COMPLETED: setForeground(Theme.SUCCESS); break;
                case IN_PROGRESS: setForeground(Theme.PRIMARY); break;
                default: setForeground(Theme.MUTED); break;
            }
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    private static class CenteredMutedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(Theme.FONT_SMALL);
            component.setForeground(Theme.MUTED);
            return component;
        }
    }

    /** Small "headline number" card used in the statistics row. */
    private static class StatCard extends Theme.RoundedPanel {
        private final JLabel valueLabel = new JLabel("0", SwingConstants.CENTER);

        StatCard(String caption, Color color) {
            super(12, Theme.CARD, Theme.BORDER);
            setLayout(new BorderLayout());
            setBorder(Theme.padding(12, 10, 12, 10));

            valueLabel.setFont(Theme.FONT_HUGE);
            valueLabel.setForeground(color);
            add(valueLabel, BorderLayout.CENTER);

            JLabel captionLabel = new JLabel(caption, SwingConstants.CENTER);
            captionLabel.setFont(Theme.FONT_SMALL);
            captionLabel.setForeground(Theme.MUTED);
            add(captionLabel, BorderLayout.SOUTH);

            setPreferredSize(new Dimension(140, 92));
        }

        void setValue(String value) {
            valueLabel.setText(value);
        }
    }
}

// ====================================================================================
// ui/ResultsPanel.java
// ====================================================================================

/**
 * Results of the last closed poll: every question with its options sorted by popularity,
 * each showing its share of the votes as a percentage and a bar.
 */
class ResultsPanel extends JPanel {

    private static final String CARD_EMPTY = "empty";
    private static final String CARD_RESULTS = "results";

    private static final Color[] BAR_COLORS = {
            new Color(0x2D6CDF), new Color(0x18A05B), new Color(0xD98A16), new Color(0x7B5EC7)
    };

    private final CardLayout cardLayout = new CardLayout();
    private final Theme.Column content = new Theme.Column();
    private final JLabel headline = new JLabel();
    private final JLabel summary = new JLabel();

    public ResultsPanel() {
        setLayout(cardLayout);
        setBackground(Theme.BACKGROUND);

        add(buildEmptyView(), CARD_EMPTY);
        add(buildResultsView(), CARD_RESULTS);
        cardLayout.show(this, CARD_EMPTY);
    }

    private JPanel buildEmptyView() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);
        JLabel label = new JLabel("<html><div style='text-align:center'>"
                + "<span style='font-size:16pt'>עדיין אין תוצאות להצגה</span><br><br>"
                + "התוצאות יופיעו כאן אוטומטית מיד עם סגירת הסקר.</div></html>", SwingConstants.CENTER);
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.FONT_BODY);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildResultsView() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(Theme.padding(16, 16, 16, 16));

        JPanel header = Theme.card();
        header.setLayout(new BorderLayout(0, 6));
        headline.setFont(Theme.FONT_TITLE);
        headline.setForeground(Theme.TEXT);
        summary.setFont(Theme.FONT_BODY);
        summary.setForeground(Theme.MUTED);
        header.add(headline, BorderLayout.NORTH);
        header.add(summary, BorderLayout.CENTER);
        panel.add(header, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        scrollPane.getViewport().setBackground(Theme.BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /** Renders the results of a closed poll. Must run on the Swing thread. */
    public void showResults(Poll poll) {
        headline.setText("תוצאות הסקר: " + poll.getTitle());

        String duration = "—";
        if (poll.getStartedAt() != null && poll.getClosedAt() != null) {
            duration = Poll.formatDuration(Duration.between(poll.getStartedAt(), poll.getClosedAt()).getSeconds());
        }
        summary.setText(poll.getParticipantCount() + " משתתפים · "
                + poll.getCompletedCount() + " השלימו את כל השאלות · "
                + poll.getNotCompletedCount() + " לא השלימו · משך הסקר בפועל " + duration);

        content.removeAll();
        for (int i = 0; i < poll.getQuestionCount(); i++) {
            content.add(buildQuestionCard(i + 1, poll.getQuestion(i), poll.getParticipantCount()));
            content.add(Theme.gap(14));
        }
        // Result cards are built long after the window's RTL pass, so orient them here -
        // otherwise the Hebrew option names end up on the far left, away from their question.
        content.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        content.revalidate();
        content.repaint();
        cardLayout.show(this, CARD_RESULTS);
    }

    public void clear() {
        content.removeAll();
        cardLayout.show(this, CARD_EMPTY);
    }

    private JComponent buildQuestionCard(int index, Question question, int participantCount) {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel questionLabel = Theme.subtitle("שאלה " + index + ": " + question.getText());
        header.add(questionLabel, BorderLayout.LINE_START);
        int total = question.getTotalVotes();
        header.add(Theme.hint(total + " הצבעות מתוך " + participantCount + " משתתפים"), BorderLayout.LINE_END);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Theme.stretchRow(header));
        card.add(Theme.gap(12));

        List<Question.OptionResult> results = question.getSortedResults();
        if (total == 0) {
            // The options still have to be listed (at 0%) - the assignment asks for the
            // text, the options and a percentage for every question, votes or not.
            JLabel none = Theme.hint("לא התקבלו הצבעות על שאלה זו - כל האפשרויות ב-0%.");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
            card.add(Theme.gap(10));
        }

        for (int i = 0; i < results.size(); i++) {
            Question.OptionResult result = results.get(i);
            card.add(new ResultRow(result, BAR_COLORS[i % BAR_COLORS.length], i == 0 && total > 0));
            card.add(Theme.gap(8));
        }
        return card;
    }

    /** One option: rank, text, bar, percentage and vote count. */
    private static class ResultRow extends JPanel {

        private final Question.OptionResult result;
        private final Color color;

        ResultRow(Question.OptionResult result, Color color, boolean isWinner) {
            this.result = result;
            this.color = color;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(100, 46));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

            String label = (isWinner ? "★  " : "") + result.getOptionText();
            JLabel textLabel = new JLabel(label);
            textLabel.setFont(isWinner ? Theme.FONT_BODY_BOLD : Theme.FONT_BODY);
            textLabel.setForeground(Theme.TEXT);
            add(textLabel, BorderLayout.NORTH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String percentText = String.format("%.0f%%", result.getPercentage());
            String countText = "(" + result.getVoteCount() + ")";

            boolean rightToLeft = !getComponentOrientation().isLeftToRight();

            g2.setFont(Theme.FONT_BODY_BOLD);
            int percentWidth = g2.getFontMetrics().stringWidth(percentText) + 10;
            int countWidth = g2.getFontMetrics(Theme.FONT_SMALL).stringWidth(countText) + 10;
            int labelsWidth = percentWidth + countWidth;

            int barY = getHeight() - 16;
            int barWidth = Math.max(60, getWidth() - labelsWidth);
            // In RTL the labels sit on the right and the bar occupies the left part of the row.
            int barStart = rightToLeft ? 0 : labelsWidth;
            int labelStart = rightToLeft ? barWidth : 0;

            Theme.paintBar(g2, barStart, barY, barWidth, 10, result.getPercentage() / 100.0,
                    color, new Color(0xEDF1F7), rightToLeft);

            // Reading order: in RTL the eye meets the percentage first, so it goes outermost.
            int percentX = rightToLeft ? labelStart + countWidth + 8 : labelStart + 8;
            int countX = rightToLeft ? labelStart + 8 : labelStart + percentWidth + 8;

            g2.setColor(color);
            g2.drawString(percentText, percentX, barY + 10);
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.FONT_SMALL);
            g2.drawString(countText, countX, barY + 10);
            g2.dispose();
        }
    }
}

// ====================================================================================
// ui/MainWindow.java
// ====================================================================================

/**
 * The main management window.
 * <p>
 * Layout: a fixed community area on one side (always visible, global data) and a tabbed
 * work area on the other (create a poll / track the active poll / see the results).
 * All service callbacks are marshalled onto the Swing thread here.
 */
class MainWindow extends JFrame implements CommunityListener, PollListener {

    private static final int TAB_LIVE = 1;
    private static final int TAB_RESULTS = 2;

    private final AppConfig config;
    private final CommunityService communityService;
    private final PollService pollService;

    private final CommunityPanel communityPanel;
    private final CreatePollPanel createPollPanel;
    private final LivePollPanel livePollPanel;
    private final ResultsPanel resultsPanel;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("המערכת מוכנה");
    private final JLabel clockLabel = new JLabel();
    private final JLabel notificationBar = new JLabel();
    /** Always-visible answer to "what is the system doing right now?". */
    private final JLabel stateChip = Theme.chip("אין סקר פעיל", Color.WHITE, new Color(0x2B3A55));
    private final Timer notificationTimer;

    public MainWindow(AppConfig config,
                      CommunityService communityService,
                      PollService pollService,
                      ChatGptService chatGptService) {
        super("מערכת ניהול סקרים · Telegram + Swing");
        this.config = config;
        this.communityService = communityService;
        this.pollService = pollService;

        this.communityPanel = new CommunityPanel(communityService);
        this.createPollPanel = new CreatePollPanel(communityService, pollService, chatGptService, this::setStatus);
        this.livePollPanel = new LivePollPanel(this::confirmCloseNow, this::confirmCancelSchedule);
        this.resultsPanel = new ResultsPanel();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 720));
        // Size to fit the actual screen (minus the taskbar) and open maximized, so the
        // tracking table and the results bars get the room they need on any monitor.
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(usable.width, usable.height);
        setLocationRelativeTo(null);
        setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        // Applied only once the whole tree exists: applyComponentOrientation walks the
        // children that are attached AT THAT MOMENT, so doing it earlier would leave the
        // header, the banner and the status bar left-to-right in an otherwise RTL window.
        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        notificationTimer = new Timer(9000, e -> notificationBar.setVisible(false));
        notificationTimer.setRepeats(false);

        Timer clock = new Timer(1000, e -> clockLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        clock.start();

        communityService.addListener(this);
        pollService.addListener(this);
    }

    // ==================================================================
    // Layout
    // ==================================================================

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.HEADER_BG);
        header.setBorder(Theme.padding(14, 20, 14, 20));

        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        titleBox.setOpaque(false);
        JLabel logo = new JLabel("◆");
        logo.setFont(Theme.FONT_TITLE);
        logo.setForeground(new Color(0x4E8CF5));
        JLabel title = new JLabel("מערכת ניהול סקרים");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Color.WHITE);
        titleBox.add(logo);
        titleBox.add(title);
        header.add(titleBox, BorderLayout.LINE_START);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, 10, 0));
        right.setOpaque(false);
        right.add(stateChip);
        JLabel botChip = Theme.chip("@" + config.getBotUsername(), Color.WHITE, new Color(0x2B3A55));
        right.add(botChip);
        JButton settings = Theme.ghostButton("⚙  הגדרות");
        settings.setFont(Theme.FONT_SMALL);
        settings.addActionListener(e -> openSettings());
        right.add(settings);
        header.add(right, BorderLayout.LINE_END);

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(header, BorderLayout.NORTH);

        notificationBar.setOpaque(true);
        notificationBar.setFont(Theme.FONT_BODY_BOLD);
        notificationBar.setBorder(Theme.padding(10, 20, 10, 20));
        notificationBar.setVisible(false);
        north.add(notificationBar, BorderLayout.CENTER);
        return north;
    }

    private JSplitPane buildBody() {
        tabs.setFont(Theme.FONT_BODY_BOLD);
        tabs.setBackground(Theme.BACKGROUND);
        tabs.addTab("  יצירת סקר  ", createPollPanel);
        tabs.addTab("  סקר פעיל  ", livePollPanel);
        tabs.addTab("  תוצאות  ", resultsPanel);
        tabs.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, communityPanel, tabs);
        split.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        split.setDividerLocation(440);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createEmptyBorder());
        // The community panel keeps its width; every extra pixel goes to the work area.
        split.setResizeWeight(0.0);
        communityPanel.setMinimumSize(new Dimension(360, 0));
        tabs.setMinimumSize(new Dimension(680, 0));
        split.setContinuousLayout(true);
        return split;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.CARD);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                Theme.padding(8, 18, 8, 18)));

        statusLabel.setFont(Theme.FONT_SMALL);
        statusLabel.setForeground(Theme.MUTED);
        bar.add(statusLabel, BorderLayout.LINE_START);

        clockLabel.setFont(Theme.FONT_SMALL);
        clockLabel.setForeground(Theme.MUTED);
        bar.add(clockLabel, BorderLayout.LINE_END);
        return bar;
    }

    // ==================================================================
    // Feedback helpers
    // ==================================================================

    public void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
    }

    private void showNotification(String message, Color foreground, Color background) {
        notificationBar.setText(message);
        notificationBar.setForeground(foreground);
        notificationBar.setBackground(background);
        notificationBar.setVisible(true);
        notificationTimer.restart();
        revalidate();
        repaint();
    }

    private void openSettings() {
        SetupDialog dialog = new SetupDialog(this, config);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            JOptionPane.showMessageDialog(this,
                    "ההגדרות נשמרו. יש להפעיל מחדש את התוכנית כדי להתחבר לבוט המעודכן.",
                    "ההגדרות נשמרו", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void confirmCancelSchedule() {
        int answer = JOptionPane.showConfirmDialog(this,
                "לבטל את השליחה המתוזמנת? הסקר לא יישלח כלל וניתן יהיה ליצור סקר חדש.",
                "ביטול שליחה", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            pollService.cancelScheduledPoll();
        }
    }

    private void confirmCloseNow() {
        int answer = JOptionPane.showConfirmDialog(this,
                "לסגור את הסקר עכשיו? לא יתקבלו תשובות נוספות והתוצאות יוצגו מיד.",
                "סגירת הסקר", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            pollService.closeManually();
        }
    }

    // ==================================================================
    // CommunityListener
    // ==================================================================

    @Override
    public void onMemberJoined(Member newMember, int totalMembers) {
        SwingUtilities.invokeLater(() -> {
            communityPanel.refresh();
            communityPanel.flashNewMember();
            createPollPanel.validateForm();
            setStatus(newMember.getFullName() + " הצטרף/ה לקהילה · סה\"כ " + totalMembers + " חברים");
        });
    }

    // ==================================================================
    // PollListener
    // ==================================================================

    @Override
    public void onPollScheduled(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            createPollPanel.setPollLive(true);
            communityPanel.setPollLive(true);
            livePollPanel.showPoll(poll);
            updateStateChip(poll);
            tabs.setSelectedIndex(TAB_LIVE);
            showNotification("הסקר \"" + poll.getTitle() + "\" נקבע לשליחה. הספירה לאחור מוצגת במסך.",
                    Theme.PRIMARY_DARK, Theme.PRIMARY_SOFT);
        });
    }

    @Override
    public void onCountdownTick(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            livePollPanel.refresh();
            updateStateChip(poll);
        });
    }

    @Override
    public void onPollStarted(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            createPollPanel.setPollLive(true);
            communityPanel.setPollLive(true);
            livePollPanel.showPoll(poll);
            updateStateChip(poll);
            tabs.setSelectedIndex(TAB_LIVE);
            showNotification("✔  הסקר \"" + poll.getTitle() + "\" נשלח בהצלחה ל-" + poll.getParticipantCount()
                            + " משתתפים. הזמן למענה: " + Poll.DURATION_MINUTES + " דקות.",
                    new Color(0x0F7A44), Theme.SUCCESS_SOFT);
            setStatus("הסקר פעיל · " + poll.getParticipantCount() + " משתתפים");
        });
    }

    @Override
    public void onAnswerReceived(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            livePollPanel.refresh();
            setStatus("התקבלה תשובה · " + poll.getCompletedCount() + "/" + poll.getParticipantCount() + " השלימו");
        });
    }

    @Override
    public void onRemindersSent(Poll poll, int remindedCount) {
        SwingUtilities.invokeLater(() -> {
            livePollPanel.noteReminders(remindedCount);
            livePollPanel.refresh();
            if (remindedCount > 0) {
                showNotification("⏱  נשלחה תזכורת ל-" + remindedCount + " משתתפים שטרם השלימו את הסקר.",
                        new Color(0x9A6410), Theme.WARNING_SOFT);
            }
        });
    }

    @Override
    public void onPollClosed(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            livePollPanel.showPoll(poll);
            resultsPanel.showResults(poll);
            updateStateChip(poll);
            createPollPanel.setPollLive(false);
            communityPanel.setPollLive(false);
            createPollPanel.resetForm();
            tabs.setSelectedIndex(TAB_RESULTS);
            showNotification("■  " + poll.getCloseReason() + " · התוצאות מוצגות כעת. ניתן להתחיל סקר חדש.",
                    Theme.TEXT, new Color(0xE7ECF3));
            setStatus("הסקר הסתיים · " + poll.getCompletedCount() + " מתוך "
                    + poll.getParticipantCount() + " השלימו");
        });
    }

    @Override
    public void onScheduledPollCancelled(Poll poll) {
        SwingUtilities.invokeLater(() -> {
            livePollPanel.clear();
            updateStateChip(null);
            createPollPanel.setPollLive(false);
            communityPanel.setPollLive(false);
            tabs.setSelectedIndex(0);
            showNotification("השליחה המתוזמנת של \"" + poll.getTitle() + "\" בוטלה. הסקר לא נשלח לאיש.",
                    new Color(0x8E2C2C), Theme.DANGER_SOFT);
            setStatus("השליחה המתוזמנת בוטלה");
        });
    }

    /**
     * Keeps the header chip telling the truth at every moment: no poll / counting down to
     * the send / running with the time left / closed. The operator can read the system
     * state without looking at any particular tab.
     */
    private void updateStateChip(Poll poll) {
        if (poll == null) {
            paintChip("אין סקר פעיל", new Color(0x2B3A55));
            return;
        }
        switch (poll.getStatus()) {
            case SCHEDULED:
                paintChip("⏳ ממתין לשליחה · " + Poll.formatDuration(poll.getSecondsUntilStart()),
                        new Color(0x1D4F9E));
                break;
            case ACTIVE:
                paintChip("● סקר פעיל · נותרו " + Poll.formatDuration(poll.getSecondsRemaining()),
                        new Color(0x0F7A44));
                break;
            case CLOSED:
                paintChip("■ הסקר נסגר · התוצאות מוכנות", new Color(0x4A5568));
                break;
            default:
                paintChip("אין סקר פעיל", new Color(0x2B3A55));
        }
    }

    private void paintChip(String text, Color background) {
        stateChip.setText(text);
        stateChip.setBackground(background);
        stateChip.repaint();
    }

    /** Shown when the bot could not connect, so the operator is not left with a dead UI. */
    public void showConnectionError(String message) {
        SwingUtilities.invokeLater(() -> {
            showNotification("⚠  " + message, new Color(0x8E2C2C), Theme.DANGER_SOFT);
            setStatus("הבוט אינו מחובר");
        });
    }

    /** Marks the bot as connected in the status bar. */
    public void markConnected() {
        setStatus("הבוט מחובר · ממתין להצטרפות משתמשים");
    }
}

// ====================================================================================
// Main.java
// ====================================================================================

/**
 * Entry point: wires the services, opens the management window and connects the bot.
 *
 * <p>Run with: {@code mvn exec:java}  (or {@code java -jar target/telegram-poll-system.jar})
 */
public class Main {

    public static void main(String[] args) {
        Locale.setDefault(new Locale("he", "IL"));
        installLookAndFeel();

        AppConfig config = AppConfig.load();

        CommunityService communityService = new CommunityService();
        PollService pollService = new PollService(communityService);
        ChatGptService chatGptService = new ChatGptService(config);

        SwingUtilities.invokeLater(() -> {
            // Ask for the token on first run instead of failing with a stack trace.
            if (!SetupDialog.promptIfNeeded(null, config)) {
                JOptionPane.showMessageDialog(null,
                        "לא הוזנו פרטי הבוט. לא ניתן להפעיל את המערכת.",
                        "הפעלה בוטלה", JOptionPane.WARNING_MESSAGE);
                System.exit(0);
            }

            MainWindow window = new MainWindow(config, communityService, pollService, chatGptService);
            window.setVisible(true);

            // Connect to Telegram off the UI thread so the window never freezes.
            new Thread(() -> connectBot(config, communityService, pollService, window), "telegram-connect").start();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(pollService::shutdown));
    }

    private static void connectBot(AppConfig config,
                                   CommunityService communityService,
                                   PollService pollService,
                                   MainWindow window) {
        try {
            SurveyBot bot = new SurveyBot(config, communityService, pollService);
            pollService.setBotGateway(bot);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            window.markConnected();
            System.out.println("הבוט " + bot.describeConnection() + " מחובר ומאזין להודעות.");
        } catch (Exception e) {
            window.showConnectionError("החיבור לטלגרם נכשל: " + e.getMessage()
                    + " · בדקו את הטוקן בהגדרות ואת חיבור האינטרנט.");
            System.err.println("החיבור לטלגרם נכשל: " + e.getMessage());
        }
    }

    private static void installLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("OptionPane.messageFont", Theme.FONT_BODY);
            UIManager.put("OptionPane.buttonFont", Theme.FONT_BODY);
            UIManager.put("TabbedPane.contentBorderInsets", new java.awt.Insets(0, 0, 0, 0));
            UIManager.put("ToolTip.font", Theme.FONT_SMALL);
        } catch (Exception ignored) {
            // The default look and feel is perfectly usable - never fail startup over this.
        }
    }
}
