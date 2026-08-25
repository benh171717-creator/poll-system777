package com.pollsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pollsystem.config.AppConfig;
import com.pollsystem.model.Poll;
import com.pollsystem.model.Question;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates poll questions from a free-text topic using the OpenAI (ChatGPT) API.
 * The generated questions are returned to the UI for review before the poll is sent.
 * <p>
 * The endpoint is read from {@link AppConfig}, so the same client also works against any
 * OpenAI-compatible service (Google Gemini, Groq, a local model) without a code change.
 */
public class ChatGptService {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    private final AppConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public ChatGptService(AppConfig config) {
        this.config = config;
    }

    /**
     * Asks ChatGPT for {@code questionCount} questions about {@code topic}, each with
     * {@code optionsPerQuestion} answer options.
     *
     * @throws ChatGptException with a clear Hebrew message when the call fails
     */
    public List<Question> generateQuestions(String topic, int questionCount, int optionsPerQuestion) {
        if (!config.isOpenAiConfigured()) {
            throw new ChatGptException("לא הוגדר מפתח OpenAI API. ניתן להגדיר אותו דרך תפריט ההגדרות.");
        }
        if (topic == null || topic.isBlank()) {
            throw new ChatGptException("יש להזין נושא לסקר לפני יצירה אוטומטית.");
        }
        questionCount = Math.max(Poll.MIN_QUESTIONS, Math.min(Poll.MAX_QUESTIONS, questionCount));
        optionsPerQuestion = Math.max(Question.MIN_OPTIONS, Math.min(Question.MAX_OPTIONS, optionsPerQuestion));

        String prompt = buildPrompt(topic, questionCount, optionsPerQuestion);

        try {
            if (config.isCourseProxy()) {
                return parseQuestions(stripCodeFences(callCourseProxy(prompt)));
            }

            HttpResponse<String> response = call(prompt, true);

            // Some OpenAI-compatible services reject "response_format" - retry plainly.
            if (response.statusCode() == 400) {
                response = call(prompt, false);
            }

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ChatGptException("מפתח ה-API אינו תקין (" + response.statusCode()
                        + "). יש לבדוק אותו בחלון ההגדרות.");
            }
            if (response.statusCode() == 404) {
                throw new ChatGptException("שם המודל או כתובת השרת שגויים (404). יש לבדוק אותם בחלון ההגדרות.");
            }
            if (response.statusCode() == 429) {
                throw new ChatGptException("חריגה ממכסת השימוש ב-API (429). נסה שוב בעוד מספר רגעים.");
            }
            if (response.statusCode() / 100 != 2) {
                throw new ChatGptException("שגיאה בפנייה לשירות ה-AI (קוד " + response.statusCode() + "): "
                        + shorten(response.body()));
            }

            JsonNode root = mapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new ChatGptException("ChatGPT החזיר תשובה ריקה. נסה לנסח את הנושא מחדש.");
            }
            return parseQuestions(stripCodeFences(content));

        } catch (ChatGptException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ChatGptException("הפנייה ל-ChatGPT ארכה זמן רב מדי. נסה שוב.");
        } catch (java.io.IOException e) {
            throw new ChatGptException("שגיאת רשת בפנייה ל-ChatGPT: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatGptException("הפנייה ל-ChatGPT בוטלה.");
        } catch (Exception e) {
            throw new ChatGptException("שגיאה בעיבוד התשובה מ-ChatGPT: " + e.getMessage());
        }
    }

    /**
     * The college's ChatGPT proxy. Unlike the OpenAI protocol it takes the whole prompt as
     * a query parameter and answers with a small envelope:
     * <pre>{"error":false,"code":null,"value":"...the model's reply..."}</pre>
     *
     * @return the model's raw reply text
     */
    private String callCourseProxy(String prompt) throws Exception {
        String instruction = "אתה עוזר שיוצר סקרים בעברית. החזר JSON תקין בלבד, "
                + "ללא טקסט נוסף וללא סימוני קוד.\n\n" + prompt;

        String url = config.getOpenAiBaseUrl()
                + (config.getOpenAiBaseUrl().contains("?") ? "&" : "?")
                + "token=" + URLEncoder.encode(config.getOpenAiApiKey(), StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(instruction, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                // The proxy rejects requests without a browser-like agent.
                .header("User-Agent", BROWSER_USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() / 100 != 2) {
            throw new ChatGptException("שרת הקורס החזיר שגיאה (קוד " + response.statusCode() + "): "
                    + shorten(response.body()));
        }

        JsonNode root = mapper.readTree(response.body());
        if (root.path("error").asBoolean(false)) {
            int code = root.path("code").asInt(0);
            throw new ChatGptException("שרת הקורס דחה את הבקשה (code " + code + "). "
                    + "בדקו שהטוקן הועתק במלואו ושנותרה יתרה בחשבון הסטודנט.");
        }

        String value = root.path("value").asText("");
        if (value.isBlank()) {
            throw new ChatGptException("שרת הקורס החזיר תשובה ריקה. נסו לנסח את הנושא מחדש.");
        }
        return value;
    }

    /** Builds and sends one chat-completions request. */
    private HttpResponse<String> call(String prompt, boolean requestJsonFormat) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getOpenAiModel());
        body.put("temperature", 0.8);

        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", "אתה עוזר שיוצר סקרים בעברית. אתה מחזיר JSON תקין בלבד, ללא טקסט נוסף וללא סימוני קוד.");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        if (requestJsonFormat) {
            body.putObject("response_format").put("type", "json_object");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getOpenAiBaseUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Authorization", "Bearer " + config.getOpenAiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() > 160 ? single.substring(0, 160) + "..." : single;
    }

    private String buildPrompt(String topic, int questionCount, int optionsPerQuestion) {
        return "צור סקר בעברית בנושא: \"" + topic + "\".\n"
                + "הסקר יכלול בדיוק " + questionCount + " שאלות, ולכל שאלה בדיוק "
                + optionsPerQuestion + " אפשרויות תשובה קצרות וברורות.\n"
                + "השאלות צריכות להיות שאלות דעה או העדפה, כך שלכל אפשרות יש סיכוי להיבחר.\n"
                + "החזר JSON בפורמט הבא בלבד:\n"
                + "{\"title\": \"כותרת קצרה לסקר\", \"questions\": ["
                + "{\"text\": \"נוסח השאלה\", \"options\": [\"אפשרות 1\", \"אפשרות 2\"]}]}";
    }

    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            if (firstNewLine > 0) trimmed = trimmed.substring(firstNewLine + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    private List<Question> parseQuestions(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode questionsNode = root.path("questions");
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new ChatGptException("לא התקבלו שאלות מ-ChatGPT. נסה נושא אחר.");
        }
        List<Question> questions = new ArrayList<>();
        for (JsonNode questionNode : questionsNode) {
            if (questions.size() >= Poll.MAX_QUESTIONS) break;
            String text = questionNode.path("text").asText("").trim();
            List<String> options = new ArrayList<>();
            for (JsonNode optionNode : questionNode.path("options")) {
                String option = optionNode.asText("").trim();
                if (!option.isEmpty() && options.size() < Question.MAX_OPTIONS) {
                    options.add(option);
                }
            }
            if (text.isEmpty() || options.size() < Question.MIN_OPTIONS) continue;
            questions.add(new Question(text, options));
        }
        if (questions.isEmpty()) {
            throw new ChatGptException("התשובה מ-ChatGPT לא הכילה שאלות תקינות. נסה שוב.");
        }
        return questions;
    }

    /** Title suggested by ChatGPT, extracted best-effort (never fails the flow). */
    public String suggestTitle(String topic) {
        return topic == null || topic.isBlank() ? "סקר" : topic.trim();
    }

    /** A failure the UI can present to the user as-is. */
    public static class ChatGptException extends RuntimeException {
        public ChatGptException(String message) {
            super(message);
        }
    }
}
