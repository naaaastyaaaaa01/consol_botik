
package com.practice.telegram;

import com.practice.logic.DialogLogic;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class TelegramBot extends TelegramLongPollingBot {

    private final DialogLogic logic;
    private final String botToken;
    private final String botUsername;

    public TelegramBot(DialogLogic logic) {
        this.logic = logic;

        Properties properties = new Properties();

        try (FileInputStream file =
                     new FileInputStream("bot.properties")) {

            properties.load(file);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Не удалось загрузить bot.properties", e);
        }

        botToken = properties.getProperty("bot.token");
        botUsername = properties.getProperty("bot.username");

        if (botToken == null || botUsername == null) {
            throw new RuntimeException(
                    "В bot.properties не указаны токен или имя бота");
        }
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.getMessage() == null ||
                update.getMessage().getText() == null) {
            return;
        }

        String chatId = update.getMessage()
                .getChatId().toString();

        String userId = update.getMessage()
                .getFrom().getId().toString();

        String command = update.getMessage().getText();

        String response = logic.processInput(userId, command);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(response);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(
                    "Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
