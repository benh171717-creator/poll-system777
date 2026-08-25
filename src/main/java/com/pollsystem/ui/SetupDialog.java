package com.pollsystem.ui;

import com.pollsystem.config.AppConfig;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;

/**
 * First-run / settings dialog. Lets the operator paste the bot token and the AI key
 * without editing any file, so the program is runnable straight after download.
 */
public class SetupDialog extends JDialog {

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
