package com.pollsystem;

import com.pollsystem.bot.SurveyBot;
import com.pollsystem.config.AppConfig;
import com.pollsystem.service.ChatGptService;
import com.pollsystem.service.CommunityService;
import com.pollsystem.service.PollService;
import com.pollsystem.ui.MainWindow;
import com.pollsystem.ui.SetupDialog;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.Locale;

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
            UIManager.put("OptionPane.messageFont", com.pollsystem.ui.Theme.FONT_BODY);
            UIManager.put("OptionPane.buttonFont", com.pollsystem.ui.Theme.FONT_BODY);
            UIManager.put("TabbedPane.contentBorderInsets", new java.awt.Insets(0, 0, 0, 0));
            UIManager.put("ToolTip.font", com.pollsystem.ui.Theme.FONT_SMALL);
        } catch (Exception ignored) {
            // The default look and feel is perfectly usable - never fail startup over this.
        }
    }
}
