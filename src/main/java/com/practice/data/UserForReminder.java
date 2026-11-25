
package com.practice.data;

public class UserForReminder {

    private final String telegramId;
    private final String chatId;

    public UserForReminder(String telegramId, String chatId) {
        this.telegramId = telegramId;
        this.chatId = chatId;
    }

    public String getTelegramId() {
        return telegramId;
    }

    public String getChatId() {
        return chatId;
    }
}
