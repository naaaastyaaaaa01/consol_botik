
package com.practice.telegram;

import com.practice.data.DatabaseManager;
import com.practice.logic.DialogLogic;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramBot extends TelegramLongPollingBot {

    private final DialogLogic logic;
    private final String botToken;
    private final String botUsername;
    private final DatabaseManager database;

    private final Map<String, ComplaintDraft> pendingComplaints =
            new HashMap<>();

    // Находим три строки рамки карточки
    private static final Pattern CARD_FRAME_PATTERN =
            Pattern.compile("(?m)┌─+┐\\R│.*│\\R└─+┘");

    public TelegramBot(DialogLogic logic) {
        this.logic = logic;

        try {
            this.database = new DatabaseManager();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Не удалось подключиться к базе данных", e);
        }

        Properties properties = new Properties();

        // Читаем bot.properties в кодировке UTF-8
        try (InputStreamReader reader =
                     new InputStreamReader(
                             new FileInputStream("bot.properties"),
                             StandardCharsets.UTF_8)) {

            properties.load(reader);

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

        loadAdmins(properties);
    }

    // ================= АДМИНИСТРАТОРЫ =================

    private void loadAdmins(Properties properties) {
        try {
            for (int i = 1; i <= 2; i++) {

                String adminId =
                        properties.getProperty("admin.id." + i);

                String adminName =
                        properties.getProperty("admin.name." + i);

                if (adminId != null && !adminId.isBlank()) {

                    if (adminName == null || adminName.isBlank()) {
                        adminName = "Администратор " + i;
                    }

                    database.addAdmin(
                            adminId.trim(),
                            adminName.trim());

                    System.out.println(
                            "Загружен администратор: " + adminName);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Не удалось загрузить администраторов", e);
        }
    }

    private boolean isAdmin(String userId) {
        try {
            return database.isAdmin(userId);
        } catch (SQLException e) {
            System.out.println(
                    "Ошибка проверки администратора: " + e.getMessage());
            return false;
        }
    }

    // ================= ПОЛУЧЕНИЕ СООБЩЕНИЙ =================

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }

        if (update.getMessage() == null
                || update.getMessage().getText() == null) {
            return;
        }

        String chatId =
                update.getMessage().getChatId().toString();

        String userId =
                update.getMessage().getFrom().getId().toString();

        String command =
                update.getMessage().getText().trim();

        recordActivity(userId, chatId);

        // Команда просмотра жалоб
        if (command.equals("/complaints")
                || command.startsWith("/complaints@")) {

            if (!isAdmin(userId)) {
                sendText(chatId,
                        "⛔ У тебя нет доступа к этой команде.");
                return;
            }

            showComplaints(chatId);
            return;
        }

        // Если пользователь оставляет комментарий к жалобе
        if (pendingComplaints.containsKey(userId)) {

            if (command.equalsIgnoreCase("/cancel")) {
                pendingComplaints.remove(userId);
                sendText(chatId, "Создание жалобы отменено.");
                return;
            }

            saveComplaint(userId, chatId, command);
            return;
        }

        String response =
                logic.processInput(userId, command);

        sendBotResponse(chatId, userId, response);
    }

    // ================= ОТПРАВКА ОТВЕТА БОТА =================

    private void sendBotResponse(
            String chatId,
            String userId,
            String response
    ) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        // Преобразуем рамку в моноширинный HTML-блок.
        // Остальной текст экранируется, чтобы HTML-разметка
        // не ломалась из-за символов в вопросах.
        message.setText(toTelegramHtml(response));
        message.setParseMode("HTML");

        if (response.contains("КАРТОЧКА")
                && logic.isAwaitingAnswer(userId)) {

            int questionIndex =
                    logic.getCurrentQuestionIndex(userId);

            if (questionIndex >= 0) {
                message.setReplyMarkup(
                        createQuestionKeyboard(questionIndex));
            }
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(
                    "Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    private String toTelegramHtml(String text) {

        Matcher matcher = CARD_FRAME_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        int lastPosition = 0;

        while (matcher.find()) {

            // Обычный текст перед рамкой
            result.append(escapeHtml(
                    text.substring(lastPosition, matcher.start())));

            // Саму рамку помещаем в моноширинный блок
            result.append("<pre>");
            result.append(escapeHtml(matcher.group()));
            result.append("</pre>");

            lastPosition = matcher.end();
        }

        // Остаток текста после последней рамки
        result.append(escapeHtml(text.substring(lastPosition)));

        return result.toString();
    }

    private String escapeHtml(String text) {

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // ================= КНОПКИ ОТВЕТОВ И ЖАЛОБЫ =================

    private InlineKeyboardMarkup createQuestionKeyboard(
            int questionIndex
    ) {

        List<List<InlineKeyboardButton>> keyboard =
                new ArrayList<>();

        // Первый ряд: четыре варианта ответа
        List<InlineKeyboardButton> answerRow =
                new ArrayList<>();

        for (int i = 1; i <= 4; i++) {

            InlineKeyboardButton button =
                    new InlineKeyboardButton();

            button.setText(String.valueOf(i));
            button.setCallbackData(
                    "answer:" + questionIndex + ":" + i);

            answerRow.add(button);
        }

        keyboard.add(answerRow);

        // Второй ряд: кнопка жалобы
        InlineKeyboardButton complaintButton =
                new InlineKeyboardButton();

        complaintButton.setText("🚩 Пожаловаться на вопрос");
        complaintButton.setCallbackData(
                "complaint:" + questionIndex);

        List<InlineKeyboardButton> complaintRow =
                new ArrayList<>();

        complaintRow.add(complaintButton);
        keyboard.add(complaintRow);

        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup();

        markup.setKeyboard(keyboard);

        return markup;
    }

    // ================= ОБРАБОТКА КНОПОК =================

    private void handleCallback(CallbackQuery callback) {

        String userId =
                callback.getFrom().getId().toString();

        if (callback.getMessage() == null) {
            return;
        }

        String chatId =
                callback.getMessage().getChatId().toString();

        recordActivity(userId, chatId);

        AnswerCallbackQuery answer =
                new AnswerCallbackQuery();

        answer.setCallbackQueryId(callback.getId());

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            System.out.println(
                    "Ошибка обработки нажатия: " + e.getMessage());
        }

        String data = callback.getData();

        if (data == null) {
            return;
        }

        // Нажатие на вариант ответа
        if (data.startsWith("answer:")) {
            handleAnswerCallback(data, userId, chatId);
            return;
        }

        // Нажатие на жалобу
        if (data.startsWith("complaint:")) {
            handleComplaintCallback(data, userId, chatId);
            return;
        }

        // Рассмотрение жалобы администратором
        if (data.startsWith("review:")) {

            if (!isAdmin(userId)) {
                sendText(chatId,
                        "⛔ У тебя нет доступа к этой функции.");
                return;
            }

            handleReviewCallback(data, chatId);
        }
    }

    // ================= ОБРАБОТКА ОТВЕТА КНОПКОЙ =================

    private void handleAnswerCallback(
            String data,
            String userId,
            String chatId
    ) {

        try {
            String[] parts = data.split(":");

            if (parts.length != 3) {
                sendText(chatId, "Не удалось обработать ответ.");
                return;
            }

            int questionIndex = Integer.parseInt(parts[1]);
            int selectedAnswer = Integer.parseInt(parts[2]);

            if (selectedAnswer < 1 || selectedAnswer > 4) {
                sendText(chatId, "Некорректный вариант ответа.");
                return;
            }

            // Проверяем, что пользователь всё ещё отвечает
            // именно на тот вопрос, к которому относилась кнопка.
            int currentQuestionIndex =
                    logic.getCurrentQuestionIndex(userId);

            if (currentQuestionIndex != questionIndex) {
                sendText(chatId,
                        "Этот вопрос уже неактуален. "
                                + "Используй кнопки последней карточки.");
                return;
            }

            String response = logic.processInput(
                    userId,
                    String.valueOf(selectedAnswer)
            );

            sendBotResponse(chatId, userId, response);

        } catch (NumberFormatException e) {
            sendText(chatId, "Не удалось обработать выбранный ответ.");
        }
    }

    private void handleComplaintCallback(
            String data,
            String userId,
            String chatId
    ) {

        try {
            int questionIndex = Integer.parseInt(
                    data.substring("complaint:".length()));

            String questionText =
                    logic.getQuestionText(questionIndex);

            if (questionText == null) {
                sendText(chatId,
                        "Не удалось определить вопрос для жалобы.");
                return;
            }

            pendingComplaints.put(
                    userId,
                    new ComplaintDraft(questionIndex, questionText));

            sendText(chatId,
                    "🚩 Напиши, пожалуйста, что именно не так с этим вопросом.\n\n"
                            + "Можешь описать ошибку в условии, вариантах ответа "
                            + "или правильном ответе.\n\n"
                            + "Для отмены отправь /cancel.");

        } catch (NumberFormatException e) {
            sendText(chatId,
                    "Не удалось обработать эту жалобу.");
        }
    }

    private void handleReviewCallback(String data, String chatId) {

        try {
            int complaintId = Integer.parseInt(
                    data.substring("review:".length()));

            database.markComplaintReviewed(complaintId);

            sendText(chatId,
                    "✅ Жалоба №" + complaintId
                            + " отмечена как рассмотренная.");

        } catch (NumberFormatException e) {
            sendText(chatId, "Некорректный номер жалобы.");
        } catch (SQLException e) {
            System.out.println(
                    "Ошибка обновления жалобы: " + e.getMessage());

            sendText(chatId,
                    "Не удалось обновить статус жалобы.");
        }
    }

    // ================= СПИСОК ЖАЛОБ =================

    private void showComplaints(String chatId) {

        try {
            var complaints = database.getNewComplaints();

            if (complaints.isEmpty()) {
                sendText(chatId,
                        "✅ Новых жалоб нет. Всё рассмотрено!");
                return;
            }

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                            .withZone(ZoneId.systemDefault());

            sendText(chatId,
                    "📋 Новые жалобы: " + complaints.size());

            for (var complaint : complaints) {

                String date = formatter.format(
                        Instant.ofEpochMilli(
                                complaint.getCreatedAt()));

                String text =
                        "🚩 Жалоба №" + complaint.getId() + "\n\n"
                                + "Вопрос №"
                                + (complaint.getQuestionIndex() + 1)
                                + ":\n"
                                + complaint.getQuestionText()
                                + "\n\nКомментарий пользователя:\n"
                                + complaint.getComment()
                                + "\n\nДата: " + date;

                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText(escapeHtml(text));
                message.setParseMode("HTML");
                message.setReplyMarkup(
                        createReviewKeyboard(complaint.getId()));

                execute(message);
            }

        } catch (SQLException | TelegramApiException e) {
            System.out.println(
                    "Ошибка получения жалоб: " + e.getMessage());

            sendText(chatId,
                    "Не удалось загрузить список жалоб.");
        }
    }

    private InlineKeyboardMarkup createReviewKeyboard(
            int complaintId
    ) {

        InlineKeyboardButton button =
                new InlineKeyboardButton();

        button.setText("✅ Рассмотрено");
        button.setCallbackData("review:" + complaintId);

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);

        List<List<InlineKeyboardButton>> keyboard =
                new ArrayList<>();

        keyboard.add(row);

        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup();

        markup.setKeyboard(keyboard);

        return markup;
    }

    // ================= СОХРАНЕНИЕ ЖАЛОБ =================

    private void saveComplaint(
            String userId,
            String chatId,
            String comment
    ) {

        ComplaintDraft draft =
                pendingComplaints.get(userId);

        if (draft == null) {
            return;
        }

        try {
            database.addComplaint(
                    userId,
                    draft.questionIndex,
                    draft.questionText,
                    comment);

            pendingComplaints.remove(userId);

            sendText(chatId,
                    "✅ Спасибо! Твоя жалоба сохранена.\n"
                            + "Мы обязательно её рассмотрим.");

        } catch (SQLException e) {
            System.out.println(
                    "Ошибка сохранения жалобы: " + e.getMessage());

            sendText(chatId,
                    "Не удалось сохранить жалобу. "
                            + "Попробуй отправить комментарий ещё раз.");
        }
    }

    // ================= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =================

    private void recordActivity(String userId, String chatId) {

        try {
            database.recordActivity(userId, chatId);
        } catch (SQLException e) {
            System.out.println(
                    "Ошибка сохранения активности пользователя: "
                            + e.getMessage());
        }
    }

    private void sendText(String chatId, String text) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(escapeHtml(text));
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(
                    "Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    // ================= НАПОМИНАНИЯ =================

    public void sendReminderMessage(String chatId)
            throws TelegramApiException {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(
                "Привет! 😊 Давно тебя не было в нашей викторине. "
                        + "Возвращайся и продолжай проверять свои знания!");

        execute(message);
    }

    private static class ComplaintDraft {

        private final int questionIndex;
        private final String questionText;

        public ComplaintDraft(
                int questionIndex,
                String questionText
        ) {

            this.questionIndex = questionIndex;
            this.questionText = questionText;
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
