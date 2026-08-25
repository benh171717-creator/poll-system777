package com.pollsystem.ui;

import com.pollsystem.model.Poll;
import com.pollsystem.model.Question;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.Duration;
import java.util.List;

/**
 * Results of the last closed poll: every question with its options sorted by popularity,
 * each showing its share of the votes as a percentage and a bar.
 */
public class ResultsPanel extends JPanel {

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
