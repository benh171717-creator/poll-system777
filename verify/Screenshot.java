import com.pollsystem.config.AppConfig;
import com.pollsystem.model.Poll;
import com.pollsystem.model.Question;
import com.pollsystem.service.ChatGptService;
import com.pollsystem.service.CommunityService;
import com.pollsystem.service.PollService;
import com.pollsystem.ui.MainWindow;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

/** Renders the Swing UI in a virtual display so the layout can be inspected. */
public class Screenshot {

    static MainWindow window;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        config.setBotUsername("demo_survey_bot");
        config.setBotToken("dummy");

        CommunityService community = new CommunityService();
        PollService pollService = new PollService(community);
        ChatGptService gpt = new ChatGptService(config);

        SwingUtilities.invokeAndWait(() -> {
            window = new MainWindow(config, community, pollService, gpt);
            window.setSize(1320, 840);
            window.setVisible(true);
        });
        Thread.sleep(800);

        community.join(100, "דני כהן", "danny");
        community.join(200, "יעל לוי", "yael");
        community.join(300, "אורי ישראלי", "uri");
        community.join(400, "נועה ברק", null);
        Thread.sleep(600);
        shoot("01-create.png");

        // switch the creation mode to ChatGPT to verify that sub-form
        SwingUtilities.invokeAndWait(() -> {
            try {
                Field tabsField = MainWindow.class.getDeclaredField("createPollPanel");
                tabsField.setAccessible(true);
                Object panel = tabsField.get(window);
                Field gptModeField = panel.getClass().getDeclaredField("gptMode");
                gptModeField.setAccessible(true);
                javax.swing.JRadioButton radio = (javax.swing.JRadioButton) gptModeField.get(panel);
                radio.doClick();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Thread.sleep(500);
        shoot("01b-create-gpt.png");

        // a question added at runtime must look identical to the first one (RTL check)
        SwingUtilities.invokeAndWait(() -> {
            try {
                Field f = MainWindow.class.getDeclaredField("createPollPanel");
                f.setAccessible(true);
                Object panel = f.get(window);
                Field btn = panel.getClass().getDeclaredField("addQuestionButton");
                btn.setAccessible(true);
                ((javax.swing.JButton) btn.get(panel)).doClick();
                Field manual = panel.getClass().getDeclaredField("manualMode");
                manual.setAccessible(true);
                ((javax.swing.JRadioButton) manual.get(panel)).doClick();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        Thread.sleep(500);
        shoot("01c-two-questions.png");

        // scheduled poll -> countdown screen
        Poll delayed = new Poll("העדפות טכנולוגיות", List.of(
                new Question("איזו שפת תכנות מועדפת עליך?", List.of("Java", "Python", "C#", "JavaScript"))));
        pollService.schedulePoll(delayed, 3);
        Thread.sleep(1200);
        shoot("02-countdown.png");
        pollService.closeManually();
        Thread.sleep(400);

        // active poll -> live tracking
        Poll poll = new Poll("שביעות רצון מהקורס", List.of(
                new Question("איזו שפת תכנות מועדפת עליך?", List.of("Java", "Python", "C#", "JavaScript")),
                new Question("כמה שעות בשבוע אתה מקדיש ללימודים?", List.of("עד 5", "5-10", "10-20", "מעל 20")),
                new Question("איך היית מדרג את הקורס?", List.of("מצוין", "טוב", "בינוני"))));
        pollService.schedulePoll(poll, 0);
        Thread.sleep(600);

        pollService.submitAnswer(100, poll.getId(), 0, 0);
        pollService.submitAnswer(100, poll.getId(), 1, 1);
        pollService.submitAnswer(100, poll.getId(), 2, 0);
        pollService.submitAnswer(200, poll.getId(), 0, 1);
        pollService.submitAnswer(200, poll.getId(), 1, 1);
        pollService.submitAnswer(300, poll.getId(), 0, 0);
        Thread.sleep(800);
        shoot("03-live.png");

        // finish -> results
        pollService.submitAnswer(200, poll.getId(), 2, 0);
        pollService.submitAnswer(300, poll.getId(), 1, 2);
        pollService.submitAnswer(300, poll.getId(), 2, 1);
        pollService.submitAnswer(400, poll.getId(), 0, 0);
        pollService.submitAnswer(400, poll.getId(), 1, 1);
        pollService.submitAnswer(400, poll.getId(), 2, 0);
        Thread.sleep(1000);
        shoot("04-results.png");

        // back to the live tab, now showing the closed state
        selectTab(1);
        Thread.sleep(500);
        shoot("05-live-closed.png");

        pollService.shutdown();
        System.exit(0);
    }

    static void selectTab(int index) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Field f = MainWindow.class.getDeclaredField("tabs");
                f.setAccessible(true);
                ((javax.swing.JTabbedPane) f.get(window)).setSelectedIndex(index);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    static void shoot(String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
            window.paint(image.getGraphics());
            try {
                ImageIO.write(image, "png", new File("verify/shots/" + name));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("saved " + name);
    }
}
