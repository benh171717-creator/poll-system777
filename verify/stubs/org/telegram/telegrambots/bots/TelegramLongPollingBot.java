package org.telegram.telegrambots.bots;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
public abstract class TelegramLongPollingBot implements LongPollingBot {
    protected TelegramLongPollingBot(String botToken) {}
    public abstract String getBotUsername();
    public abstract void onUpdateReceived(Update update);
    public Message execute(SendMessage m) throws TelegramApiException { return null; }
    public java.io.Serializable execute(EditMessageText m) throws TelegramApiException { return null; }
    public Boolean execute(AnswerCallbackQuery m) throws TelegramApiException { return null; }
}
