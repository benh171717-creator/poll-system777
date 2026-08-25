package com.pollsystem.ui;

import com.pollsystem.model.Member;
import com.pollsystem.model.Poll;
import com.pollsystem.service.CommunityService;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Always-visible panel showing the GLOBAL community: who is registered, their Telegram
 * username, when they joined, and how many members there are.
 * <p>
 * Deliberately shows nothing about answering state - that is poll data, not community data.
 */
public class CommunityPanel extends JPanel {

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
