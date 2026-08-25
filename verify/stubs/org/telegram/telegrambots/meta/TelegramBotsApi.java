package org.telegram.telegrambots.meta;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
public class TelegramBotsApi {
    public TelegramBotsApi(Class<? extends BotSession> c) throws TelegramApiException {}
    public BotSession registerBot(LongPollingBot bot) throws TelegramApiException { return null; }
}
