package com.pollsystem.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Single place for every colour, font and reusable widget in the UI, so the whole
 * application looks consistent. Also provides Hebrew-friendly font selection.
 */
public final class Theme {

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
