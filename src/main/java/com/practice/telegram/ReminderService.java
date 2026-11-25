
package com.practice.telegram;

import com.practice.data.DatabaseManager;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReminderService {

    private final DatabaseManager database;
    private final TelegramBot bot;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public ReminderService(TelegramBot bot) {
        this.bot = bot;

        try {
            this.database = new DatabaseManager();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Не удалось подключить базу для напоминаний", e);
        }
    }

    // Запускаем автоматическую проверку пользователей
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::sendReminders,
                0,
                1,
                TimeUnit.HOURS
        );

        System.out.println("Сервис напоминаний запущен!");
    }

    // Проверяем, кому пора отправить напоминание
    private void sendReminders() {

        try {
            var users = database.getUsersDueForReminder();

            for (var user : users) {
                try {
                    bot.sendReminderMessage(user.getChatId());

                    database.markReminderSent(user.getTelegramId());

                    System.out.println(
                            "Отправлено напоминание пользователю: "
                                    + user.getTelegramId());

                } catch (TelegramApiException e) {
                    System.out.println(
                            "Не удалось отправить напоминание пользователю "
                                    + user.getTelegramId() + ": "
                                    + e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.out.println(
                    "Ошибка при проверке напоминаний: "
                            + e.getMessage());
        }
    }

    // Останавливаем сервис
    public void stop() {
        scheduler.shutdown();
    }
}
