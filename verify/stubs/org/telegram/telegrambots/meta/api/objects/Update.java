package org.telegram.telegrambots.meta.api.objects;
public class Update {
    public boolean hasMessage() { return false; }
    public boolean hasCallbackQuery() { return false; }
    public Message getMessage() { return null; }
    public CallbackQuery getCallbackQuery() { return null; }
}
