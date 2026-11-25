
package com.practice.telegram;

import com.practice.data.DatabaseManager;
import com.practice.logic.DialogLogic;
import com.practice.logic.Topic;
import com.practice.logic.Difficulty;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
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

    private static final Pattern CARD_FRAME_PATTERN =
            Pattern.compile("(?m)┌─+┐\\R│.*│\\R└─+┘");

    private static final Pattern BOLD_TAG_PATTERN =
            Pattern.compile("</?b>");

    // ================= ТИТУЛЫ =================

    private static final int[] TITLE_THRESHOLDS = {
            0, 20, 50, 100, 200, 500
    };

    private static final String[] TITLES = {
            "🌱 Новичок",
            "📚 Знаток",
            "🎓 Исследователь",
            "🥇 Эрудит",
            "🏅 Мастер знаний",
            "👑 Гений математики"
    };

    public TelegramBot(DialogLogic logic) {
        this.logic = logic;

        try {
            this.database = new DatabaseManager();
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Не удалось подключиться к базе данных", e);
        }

        Properties properties = new Properties();

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
        registerCommands();
    }

    // ================= РЕГИСТРАЦИЯ КОМАНД TELEGRAM =================

    private void registerCommands() {

        List<BotCommand> commands = new ArrayList<>();

        commands.add(new BotCommand("help", "Показать справку"));
        commands.add(new BotCommand("hint", "Подсказка к текущему вопросу"));
        commands.add(new BotCommand("review", "Повторить вопросы с ошибками"));
        commands.add(new BotCommand("restart", "Начать заново"));
        commands.add(new BotCommand("exit", "Завершить игру"));
        commands.add(new BotCommand("profile", "Моя статистика"));

        SetMyCommands setMyCommands = new SetMyCommands();
        setMyCommands.setCommands(commands);
        setMyCommands.setScope(new BotCommandScopeDefault());
        setMyCommands.setLanguageCode("ru");

        try {
            execute(setMyCommands);
            System.out.println("Команды Telegram успешно зарегистрированы!");
        } catch (TelegramApiException e) {
            System.out.println(
                    "Не удалось зарегистрировать команды Telegram: "
                            + e.getMessage());
        }
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

        String displayName = getDisplayName(update);

        recordActivity(userId, chatId, displayName);

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

        // Команда просмотра профиля
        if (command.equals("/profile")
                || command.startsWith("/profile@")) {

            showProfile(chatId, userId);
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

        // Проверяем, завершается ли обычная викторина
        boolean finishingRegularQuiz =
                isRegularQuizAnswer(userId, command);

        String response =
                logic.processInput(userId, command);

        // Сохраняем завершённую обычную викторину
        if (finishingRegularQuiz && logic.isFinished(userId)) {
            recordCompletedQuiz(userId, chatId);
        }

        sendBotResponse(chatId, userId, response);
    }

    // ================= ПРОФИЛЬ =================

    private void showProfile(String chatId, String userId) {

        try {
            DatabaseManager.ProfileData profile =
                    database.getProfile(userId);

            if (profile == null) {
                sendText(chatId,
                        "Профиль пока не найден. Напиши /start.");
                return;
            }

            String displayName = profile.displayName();

            if (displayName == null || displayName.isBlank()) {
                displayName = "Пользователь";
            }

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                            .withZone(ZoneId.systemDefault());

            String registrationDate =
                    formatter.format(
                            Instant.ofEpochMilli(profile.registeredAt()));

            int questionsAnswered = profile.questionsAnswered();
            int correctAnswers = profile.correctAnswers();
            int quizzesCompleted = profile.quizzesCompleted();

            double accuracy = questionsAnswered == 0
                    ? 0.0
                    : correctAnswers * 100.0 / questionsAnswered;

            String currentTitle = getTitle(correctAnswers);
            int nextThreshold = getNextThreshold(correctAnswers);
            String nextTitle = getNextTitle(correctAnswers);

            StringBuilder text = new StringBuilder();

            text.append("🏆 <b>МОИ ДОСТИЖЕНИЯ</b>\n\n");

            text.append("👤 <b>")
                    .append(escapeHtml(displayName))
                    .append("</b>\n");

            text.append("📅 В викторине с ")
                    .append(registrationDate)
                    .append("\n\n");

            text.append(currentTitle)
                    .append("\n");

            text.append("<i>")
                    .append(correctAnswers)
                    .append(" правильных ответов</i>\n");

            text.append("\n");

            text.append("🎮 Викторин пройдено: <b>")
                    .append(quizzesCompleted)
                    .append("</b>\n");

            text.append("📖 Вопросов отвечено: <b>")
                    .append(questionsAnswered)
                    .append("</b>\n");

            text.append("✅ Правильных ответов: <b>")
                    .append(correctAnswers)
                    .append("</b>\n");

            text.append("🎯 Точность: <b>")
                    .append(String.format(
                            java.util.Locale.US,
                            "%.1f%%",
                            accuracy))
                    .append("</b>\n");

            text.append("\n");

            if (nextThreshold == -1) {

                text.append("👑 <b>Максимальный титул достигнут!</b>\n\n");

                text.append(createProgressBar(1.0))
                        .append("\n");

                text.append("<i>Ты достиг вершины математических знаний!</i>");

            } else {

                int previousThreshold =
                        getCurrentThreshold(correctAnswers);

                double progress =
                        (correctAnswers - previousThreshold)
                                / (double) (nextThreshold - previousThreshold);

                text.append("✨ <b>Следующая цель</b>\n");

                text.append(nextTitle)
                        .append(" — ")
                        .append(nextThreshold)
                        .append(" правильных ответов\n\n");

                text.append(createProgressBar(progress))
                        .append("\n");

                text.append("<b>")
                        .append(correctAnswers)
                        .append(" / ")
                        .append(nextThreshold)
                        .append("</b>\n");

                text.append("Осталось ")
                        .append(nextThreshold - correctAnswers)
                        .append(" правильных ответов!\n\n");

                text.append("<i>Каждый вопрос приближает тебя к вершине!</i>");
            }

            sendHtmlText(chatId, text.toString());

        } catch (SQLException e) {

            System.out.println(
                    "Ошибка получения профиля: " + e.getMessage());

            sendText(chatId,
                    "Не удалось загрузить профиль. Попробуй позже.");
        }
    }

    // ================= СИСТЕМА ТИТУЛОВ =================

    private String getTitle(int correctAnswers) {

        int index = 0;

        for (int i = 0; i < TITLE_THRESHOLDS.length; i++) {
            if (correctAnswers >= TITLE_THRESHOLDS[i]) {
                index = i;
            }
        }

        return TITLES[index];
    }

    private int getCurrentThreshold(int correctAnswers) {

        int threshold = 0;

        for (int value : TITLE_THRESHOLDS) {
            if (correctAnswers >= value) {
                threshold = value;
            }
        }

        return threshold;
    }

    private int getNextThreshold(int correctAnswers) {

        for (int threshold : TITLE_THRESHOLDS) {
            if (correctAnswers < threshold) {
                return threshold;
            }
        }

        return -1;
    }

    private String getNextTitle(int correctAnswers) {

        for (int i = 0; i < TITLE_THRESHOLDS.length; i++) {

            if (correctAnswers < TITLE_THRESHOLDS[i]) {
                return TITLES[i];
            }
        }

        return null;
    }

    private String createProgressBar(double progress) {

        int totalBlocks = 10;

        progress = Math.max(0.0, Math.min(1.0, progress));

        int filledBlocks =
                (int) Math.floor(progress * totalBlocks);

        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < totalBlocks; i++) {

            if (i < filledBlocks) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        return bar.toString();
    }

    // ================= УЧЁТ ВИКТОРИН =================

    private boolean isRegularQuizAnswer(
            String userId,
            String input
    ) {

        if (!logic.isAwaitingAnswer(userId)) {
            return false;
        }

        if (logic.isReviewMode(userId)) {
            return false;
        }

        return input.matches("[1-4]");
    }

    private void recordCompletedQuiz(
            String userId,
            String chatId
    ) {

        try {

            DatabaseManager.ProfileData before =
                    database.getProfile(userId);

            int oldCorrectAnswers =
                    before == null ? 0 : before.correctAnswers();

            String oldTitle =
                    getTitle(oldCorrectAnswers);

            int questionsCount =
                    logic.getRoundQuestionCount(userId);

            int correctCount =
                    logic.getRoundCorrectCount(userId);

            if (questionsCount <= 0) {
                return;
            }

            database.recordQuizAttempt(
                    userId,
                    questionsCount,
                    correctCount);

            System.out.println(
                    "Сохранена завершённая викторина пользователя "
                            + userId);

            // Проверяем повышение титула
            DatabaseManager.ProfileData after =
                    database.getProfile(userId);

            if (after != null) {

                String newTitle =
                        getTitle(after.correctAnswers());

                if (!oldTitle.equals(newTitle)) {

                    sendHtmlText(
                            chatId,
                            "🎉 <b>НОВОЕ ДОСТИЖЕНИЕ!</b>\n\n"
                                    + "Ты получила новый титул:\n"
                                    + newTitle
                                    + "\n\n"
                                    + "✨ Продолжай в том же духе!"
                    );
                }
            }

        } catch (SQLException e) {

            System.out.println(
                    "Ошибка сохранения результата викторины: "
                            + e.getMessage());
        }
    }

    // ================= ОТПРАВКА ОТВЕТА БОТА =================

    private void sendBotResponse(
            String chatId,
            String userId,
            String response
    ) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode("HTML");

        // Меню выбора темы
        if (logic.isChoosingTopic(userId)) {

            message.setText(
                    "👋 <b>Привет! Добро пожаловать в математическую викторину!</b>\n\n"
                            + "🧠 Проверь свои знания по линейной алгебре!\n\n"
                            + "📚 <b>Выбери тему, по которой хочешь пройти тест:</b>"
            );

            message.setReplyMarkup(createTopicKeyboard());

        }

        // Меню выбора сложности
        else if (logic.isChoosingDifficulty(userId)) {

            message.setText(
                    "✨ <b>Отличный выбор!</b>\n\n"
                            + "🎯 Теперь выбери уровень сложности:\n\n"
                            + "💡 После каждого ответа ты увидишь правильный ответ и объяснение."
            );

            message.setReplyMarkup(createDifficultyKeyboard());

        }

        // Обычный ответ или карточка вопроса
        else {

            message.setText(toTelegramHtml(response));

            if (response.contains("КАРТОЧКА")
                    && logic.isAwaitingAnswer(userId)) {

                int questionIndex =
                        logic.getCurrentQuestionIndex(userId);

                if (questionIndex >= 0) {
                    message.setReplyMarkup(
                            createQuestionKeyboard(questionIndex, userId));
                }
            }
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(
                    "Ошибка отправки сообщения: " + e.getMessage());
        }
    }

    // ================= ФОРМАТИРОВАНИЕ HTML =================

    private String toTelegramHtml(String text) {

        Matcher matcher = CARD_FRAME_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        int lastPosition = 0;

        while (matcher.find()) {

            result.append(escapeHtmlPreservingBold(
                    text.substring(lastPosition, matcher.start())));

            result.append("<pre>");
            result.append(escapeHtml(matcher.group()));
            result.append("</pre>");

            lastPosition = matcher.end();
        }

        result.append(escapeHtmlPreservingBold(
                text.substring(lastPosition)));

        return result.toString();
    }

    private String escapeHtmlPreservingBold(String text) {

        Matcher matcher = BOLD_TAG_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        int lastPosition = 0;

        while (matcher.find()) {

            result.append(escapeHtml(
                    text.substring(lastPosition, matcher.start())));

            result.append(matcher.group());

            lastPosition = matcher.end();
        }

        result.append(escapeHtml(text.substring(lastPosition)));

        return result.toString();
    }

    private String escapeHtml(String text) {

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // ================= КНОПКИ ВЫБОРА ТЕМЫ =================

    private InlineKeyboardMarkup createTopicKeyboard() {

        List<List<InlineKeyboardButton>> keyboard =
                new ArrayList<>();

        addButtonRow(
                keyboard,
                "🌍 Все темы",
                "topic:0"
        );

        Topic[] topics = Topic.values();

        String[] emojis = {
                "📐", "🔢", "🔄", "📏",
                "✏️", "🔷", "🌐"
        };

        for (int i = 0; i < topics.length; i++) {

            String buttonText =
                    emojis[i] + " " + topics[i].getTitle();

            addButtonRow(
                    keyboard,
                    buttonText,
                    "topic:" + (i + 1)
            );
        }

        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup();

        markup.setKeyboard(keyboard);

        return markup;
    }

    // ================= КНОПКИ ВЫБОРА СЛОЖНОСТИ =================

    private InlineKeyboardMarkup createDifficultyKeyboard() {

        List<List<InlineKeyboardButton>> keyboard =
                new ArrayList<>();

        addButtonRow(
                keyboard,
                "🎲 Любая сложность",
                "difficulty:0"
        );

        Difficulty[] levels = Difficulty.values();

        String[] emojis = {
                "🟢", "🟡", "🔴"
        };

        for (int i = 0; i < levels.length; i++) {

            String buttonText =
                    emojis[i] + " " + levels[i].getTitle();

            addButtonRow(
                    keyboard,
                    buttonText,
                    "difficulty:" + (i + 1)
            );
        }

        InlineKeyboardMarkup markup =
                new InlineKeyboardMarkup();

        markup.setKeyboard(keyboard);

        return markup;
    }

    // ================= ВСПОМОГАТЕЛЬНЫЙ МЕТОД КНОПОК =================

    private void addButtonRow(
            List<List<InlineKeyboardButton>> keyboard,
            String text,
            String callbackData
    ) {

        InlineKeyboardButton button =
                new InlineKeyboardButton();

        button.setText(text);
        button.setCallbackData(callbackData);

        List<InlineKeyboardButton> row =
                new ArrayList<>();

        row.add(button);
        keyboard.add(row);
    }

    // ================= КНОПКИ ОТВЕТОВ И ЖАЛОБЫ =================

    private InlineKeyboardMarkup createQuestionKeyboard(
            int questionIndex,
            String userId
    ) {

        List<List<InlineKeyboardButton>> keyboard =
                new ArrayList<>();

        String[] options = logic.getCurrentOptions(userId);

        for (int i = 0; i < options.length; i++) {

            InlineKeyboardButton button =
                    new InlineKeyboardButton();

            String buttonText = options[i];

            if (buttonText.length() > 64) {
                buttonText = buttonText.substring(0, 61) + "...";
            }

            button.setText(buttonText);

            button.setCallbackData(
                    "answer:" + questionIndex + ":" + (i + 1));

            List<InlineKeyboardButton> answerRow =
                    new ArrayList<>();

            answerRow.add(button);
            keyboard.add(answerRow);
        }

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

        recordActivity(
                userId,
                chatId,
                getDisplayName(callback));

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

        if (data.startsWith("topic:")) {
            handleTopicCallback(data, userId, chatId);
            return;
        }

        if (data.startsWith("difficulty:")) {
            handleDifficultyCallback(data, userId, chatId);
            return;
        }

        if (data.startsWith("answer:")) {
            handleAnswerCallback(data, userId, chatId);
            return;
        }

        if (data.startsWith("complaint:")) {
            handleComplaintCallback(data, userId, chatId);
            return;
        }

        if (data.startsWith("review:")) {

            if (!isAdmin(userId)) {
                sendText(chatId,
                        "⛔ У тебя нет доступа к этой функции.");
                return;
            }

            handleReviewCallback(data, chatId);
        }
    }

    // ================= ОБРАБОТКА ВЫБОРА ТЕМЫ =================

    private void handleTopicCallback(
            String data,
            String userId,
            String chatId
    ) {

        if (!logic.isChoosingTopic(userId)) {
            sendText(chatId,
                    "Это меню уже неактуально. Используй последнее сообщение бота.");
            return;
        }

        try {
            int choice = Integer.parseInt(
                    data.substring("topic:".length()));

            if (choice < 0 || choice > Topic.values().length) {
                sendText(chatId, "Некорректный выбор темы.");
                return;
            }

            String response = logic.processInput(
                    userId,
                    String.valueOf(choice)
            );

            sendBotResponse(chatId, userId, response);

        } catch (NumberFormatException e) {
            sendText(chatId, "Не удалось обработать выбор темы.");
        }
    }

    // ================= ОБРАБОТКА ВЫБОРА СЛОЖНОСТИ =================

    private void handleDifficultyCallback(
            String data,
            String userId,
            String chatId
    ) {

        if (!logic.isChoosingDifficulty(userId)) {
            sendText(chatId,
                    "Это меню уже неактуально. Используй последнее сообщение бота.");
            return;
        }

        try {
            int choice = Integer.parseInt(
                    data.substring("difficulty:".length()));

            if (choice < 0 || choice > Difficulty.values().length) {
                sendText(chatId, "Некорректный выбор сложности.");
                return;
            }

            String response = logic.processInput(
                    userId,
                    String.valueOf(choice)
            );

            sendBotResponse(chatId, userId, response);

        } catch (NumberFormatException e) {
            sendText(chatId, "Не удалось обработать выбор сложности.");
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

            int currentQuestionIndex =
                    logic.getCurrentQuestionIndex(userId);

            if (currentQuestionIndex != questionIndex) {
                sendText(chatId,
                        "Этот вопрос уже неактуален. "
                                + "Используй кнопки последней карточки.");
                return;
            }

            boolean finishingRegularQuiz =
                    isRegularQuizAnswer(
                            userId,
                            String.valueOf(selectedAnswer));

            String response = logic.processInput(
                    userId,
                    String.valueOf(selectedAnswer)
            );

            if (finishingRegularQuiz && logic.isFinished(userId)) {
                recordCompletedQuiz(userId, chatId);
            }

            sendBotResponse(chatId, userId, response);

        } catch (NumberFormatException e) {
            sendText(chatId, "Не удалось обработать выбранный ответ.");
        }
    }

    // ================= ОБРАБОТКА ЖАЛОБ =================

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

    private void recordActivity(
            String userId,
            String chatId,
            String displayName
    ) {

        try {
            database.recordActivity(
                    userId,
                    chatId,
                    displayName);
        } catch (SQLException e) {
            System.out.println(
                    "Ошибка сохранения активности пользователя: "
                            + e.getMessage());
        }
    }

    private String getDisplayName(Update update) {

        if (update.getMessage() == null
                || update.getMessage().getFrom() == null) {
            return null;
        }

        var user = update.getMessage().getFrom();

        String firstName = user.getFirstName();
        String lastName = user.getLastName();

        String displayName = "";

        if (firstName != null && !firstName.isBlank()) {
            displayName = firstName;
        }

        if (lastName != null && !lastName.isBlank()) {
            if (!displayName.isEmpty()) {
                displayName += " ";
            }
            displayName += lastName;
        }

        if (displayName.isBlank()) {
            displayName = user.getUserName();
        }

        return displayName;
    }

    private String getDisplayName(CallbackQuery callback) {

        if (callback.getFrom() == null) {
            return null;
        }

        var user = callback.getFrom();

        String firstName = user.getFirstName();
        String lastName = user.getLastName();

        String displayName = "";

        if (firstName != null && !firstName.isBlank()) {
            displayName = firstName;
        }

        if (lastName != null && !lastName.isBlank()) {
            if (!displayName.isEmpty()) {
                displayName += " ";
            }
            displayName += lastName;
        }

        if (displayName.isBlank()) {
            displayName = user.getUserName();
        }

        return displayName;
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

    // Отправка уже подготовленного HTML
    private void sendHtmlText(String chatId, String text) {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println(
                    "Ошибка отправки HTML-сообщения: " + e.getMessage());
        }
    }

    // ================= НАПОМИНАНИЯ =================

    public void sendReminderMessage(String chatId)
            throws TelegramApiException {

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(
                "👋 Привет! 😊 Давно тебя не было в нашей викторине.\n\n"
                        + "📚 Возвращайся и продолжай проверять свои знания!\n"
                        + "🧠 Новые вопросы уже ждут тебя."
        );

        execute(message);
    }

    // ================= ЧЕРНОВИК ЖАЛОБЫ =================

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

    // ================= ДАННЫЕ БОТА =================

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
