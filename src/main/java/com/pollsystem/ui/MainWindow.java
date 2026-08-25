package com.pollsystem.ui;

import com.pollsystem.config.AppConfig;
import com.pollsystem.model.Member;
import com.pollsystem.model.Poll;
import com.pollsystem.service.ChatGptService;
import com.pollsystem.service.CommunityListener;
import com.pollsystem.service.CommunityService;
import com.pollsystem.service.PollListener;
import com.pollsystem.service.PollService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The main management window.
 * <p>
 * Layout: a fixed community area on one side (always visible, global data) and a tabbed
 * work area on the other (create a poll / track the active poll / see the results).
 * All service callbacks are marshalled onto the Swing thread here.
 */
public class MainWindow extends JFrame implements CommunityListener, PollListener {

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
