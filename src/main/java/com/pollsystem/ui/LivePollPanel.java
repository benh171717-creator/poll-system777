package com.pollsystem.ui;

import com.pollsystem.model.ParticipantProgress;
import com.pollsystem.model.Poll;
import com.pollsystem.model.PollStatus;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Live view of the CURRENT poll: the countdown before a delayed send, the running
 * statistics while the poll is open, and the per-participant answering progress.
 * <p>
 * This area is about ONE poll only - the global community list lives in its own panel.
 */
public class LivePollPanel extends JPanel {

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
