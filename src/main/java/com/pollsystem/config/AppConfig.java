package com.pollsystem.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads / stores the runtime settings (bot token, bot username, OpenAI key).
 * <p>
 * Resolution order: {@code config.properties} next to the program → environment
 * variables. If nothing is found, the UI opens a setup dialog on first run, so the
 * user never has to edit files by hand.
 */
public final class AppConfig {

    private static final Path CONFIG_FILE = Paths.get("config.properties");

    private static final String KEY_BOT_TOKEN = "telegram.bot.token";
    private static final String KEY_BOT_USERNAME = "telegram.bot.username";
    private static final String KEY_OPENAI_KEY = "openai.api.key";
    private static final String KEY_OPENAI_MODEL = "openai.model";
    private static final String KEY_OPENAI_BASE_URL = "openai.base.url";

    /** The official ChatGPT endpoint - used unless the operator points the app elsewhere. */
    public static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private String botToken = "";
    private String botUsername = "";
    private String openAiApiKey = "";
    private String openAiModel = "gpt-4o-mini";
    private String openAiBaseUrl = OPENAI_ENDPOINT;

    private AppConfig() {
    }

    public static AppConfig load() {
        AppConfig config = new AppConfig();
        Properties properties = new Properties();

        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("אזהרה: לא ניתן לקרוא את config.properties - " + e.getMessage());
            }
        }

        config.botToken = firstNonBlank(properties.getProperty(KEY_BOT_TOKEN), System.getenv("TELEGRAM_BOT_TOKEN"), "");
        config.botUsername = firstNonBlank(properties.getProperty(KEY_BOT_USERNAME), System.getenv("TELEGRAM_BOT_USERNAME"), "");
        config.openAiApiKey = firstNonBlank(properties.getProperty(KEY_OPENAI_KEY), System.getenv("OPENAI_API_KEY"), "");
        config.openAiModel = firstNonBlank(properties.getProperty(KEY_OPENAI_MODEL), System.getenv("OPENAI_MODEL"), "gpt-4o-mini");
        config.openAiBaseUrl = firstNonBlank(properties.getProperty(KEY_OPENAI_BASE_URL), System.getenv("OPENAI_BASE_URL"), OPENAI_ENDPOINT);
        return config;
    }

    public void save() {
        Properties properties = new Properties();
        properties.setProperty(KEY_BOT_TOKEN, botToken);
        properties.setProperty(KEY_BOT_USERNAME, botUsername);
        properties.setProperty(KEY_OPENAI_KEY, openAiApiKey);
        properties.setProperty(KEY_OPENAI_MODEL, openAiModel);
        properties.setProperty(KEY_OPENAI_BASE_URL, openAiBaseUrl);
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            properties.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8),
                    "Telegram Poll System settings");
        } catch (IOException e) {
            System.err.println("אזהרה: לא ניתן לשמור את config.properties - " + e.getMessage());
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate.trim();
        }
        return "";
    }

    public boolean isTelegramConfigured() {
        return !botToken.isBlank() && !botUsername.isBlank();
    }

    public boolean isOpenAiConfigured() {
        return !openAiApiKey.isBlank();
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken == null ? "" : botToken.trim();
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        String value = botUsername == null ? "" : botUsername.trim();
        this.botUsername = value.startsWith("@") ? value.substring(1) : value;
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public void setOpenAiApiKey(String openAiApiKey) {
        this.openAiApiKey = openAiApiKey == null ? "" : openAiApiKey.trim();
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public void setOpenAiModel(String openAiModel) {
        this.openAiModel = (openAiModel == null || openAiModel.isBlank()) ? "gpt-4o-mini" : openAiModel.trim();
    }

    /**
     * Chat-completions endpoint. Defaults to the official ChatGPT API; any
     * OpenAI-compatible endpoint (Google Gemini, Groq, a local server) can be used
     * instead without touching the code.
     */
    public String getOpenAiBaseUrl() {
        return openAiBaseUrl;
    }

    public void setOpenAiBaseUrl(String openAiBaseUrl) {
        this.openAiBaseUrl = (openAiBaseUrl == null || openAiBaseUrl.isBlank())
                ? OPENAI_ENDPOINT : openAiBaseUrl.trim();
    }

    /** {@code true} when talking to the official ChatGPT API rather than a compatible one. */
    public boolean isOfficialOpenAi() {
        return openAiBaseUrl.contains("api.openai.com");
    }

    /**
     * {@code true} for the college's ChatGPT proxy, which is not OpenAI-shaped: it is a
     * plain {@code GET .../api-request?token=..&text=..} returning {@code {"value": "..."}}.
     * Detected from the URL so the operator only has to pick a provider, never a protocol.
     */
    public boolean isCourseProxy() {
        return openAiBaseUrl.contains("/api-request");
    }
}
