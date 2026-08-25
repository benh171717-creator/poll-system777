package com.pollsystem.ui;

import com.pollsystem.model.Poll;
import com.pollsystem.model.Question;
import com.pollsystem.service.ChatGptService;
import com.pollsystem.service.CommunityService;
import com.pollsystem.service.PollService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Poll creation screen: manual authoring or ChatGPT generation, plus the send schedule.
 * <p>
 * The form validates itself continuously and always explains, in one sentence, why the
 * "start poll" button is disabled - the operator is never left guessing.
 */
public class CreatePollPanel extends JPanel {

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
