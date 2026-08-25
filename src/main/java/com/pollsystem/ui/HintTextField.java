package com.pollsystem.ui;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Text field that paints a grey hint while it is empty - clearer than a bare box. */
public class HintTextField extends JTextField {

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
