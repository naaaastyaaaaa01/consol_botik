
package com.practice.ui;

import com.practice.data.DatabaseManager;
import com.practice.logic.DialogLogic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;

public class ConsoleUI {

    private static final String LINE =
            "========================================";

    private static final String SEPARATOR =
            "----------------------------------------";

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

    private static final Path PLAYER_FILE = Path.of(
            System.getProperty("user.home"),
            ".quiz-bot",
            "console-player.properties"
    );

    private final DialogLogic logic;
    private final Scanner scanner = new Scanner(System.in);
    private final DatabaseManager database;

    private final String userId;
    private final String playerName;

    private record PlayerIdentity(String id, String name) {}

    public ConsoleUI(DialogLogic logic) {
        this.logic = logic;

        PlayerIdentity identity;

        try {
            identity = loadOrCreateIdentity();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Не удалось сохранить данные консольного игрока.",
                    e
            );
        }

        this.userId = identity.id();
        this.playerName = identity.name();

        System.out.println();
        System.out.println("Игрок: " + playerName);
        System.out.println("Пометка: консольный игрок");
        System.out.println();

        DatabaseManager db;

        try {
            db = new DatabaseManager();

            db.recordActivity(
                    userId,
                    "console",
                    getDisplayName()
            );

            System.out.println("Подключение к базе данных успешно!");

        } catch (SQLException e) {
            db = null;

            System.out.println();
            System.out.println("ОШИБКА ПОДКЛЮЧЕНИЯ К БАЗЕ ДАННЫХ:");
            System.out.println(e.getMessage());
            System.out.println();
            System.out.println(
                    "Викторина продолжит работу без профиля и рейтинга."
            );
        }

        this.database = db;
    }

    // ================= ДАННЫЕ ИГРОКА =================

    private PlayerIdentity loadOrCreateIdentity() throws IOException {

        if (Files.exists(PLAYER_FILE)) {

            Properties properties = new Properties();

            try (var input = Files.newInputStream(PLAYER_FILE)) {
                properties.load(input);
            }

            String savedId = properties.getProperty("id");
            String savedName = properties.getProperty("name");

            if (savedId != null && !savedId.isBlank()
                    && savedName != null && !savedName.isBlank()) {

                System.out.println("Найден сохранённый профиль игрока.");

                return new PlayerIdentity(
                        savedId.trim(),
                        savedName.trim()
                );
            }
        }

        System.out.println();
        System.out.println(LINE);
        System.out.println("       СОЗДАНИЕ ПРОФИЛЯ ИГРОКА");
        System.out.println(LINE);
        System.out.println();
        System.out.print("Введи своё имя: ");

        String name;

        while (true) {
            name = scanner.nextLine().trim();

            if (!name.isBlank()) {
                break;
            }

            System.out.print("Имя не может быть пустым. Введи имя: ");
        }

        String id = "console_" + UUID.randomUUID();

        Properties properties = new Properties();
        properties.setProperty("id", id);
        properties.setProperty("name", name);

        Files.createDirectories(PLAYER_FILE.getParent());

        try (var output = Files.newOutputStream(PLAYER_FILE)) {
            properties.store(
                    output,
                    "Console quiz player identity"
            );
        }

        System.out.println();
        System.out.println("Профиль успешно создан!");
        System.out.println("Твой ID сохранён автоматически.");
        System.out.println("При следующем запуске вводить имя не придётся.");
        System.out.println();

        return new PlayerIdentity(id, name);
    }

    private String getDisplayName() {
        return playerName + " (консольный игрок)";
    }

    // ================= ПРИВЕТСТВИЕ =================

    private void printWelcomeMessage() {

        System.out.println();
        System.out.println(LINE);
        System.out.println("       🧠 ВИКТОРИНА ПО ЛИНЕЙНОЙ АЛГЕБРЕ");
        System.out.println(LINE);
        System.out.println();

        System.out.println("Привет, " + playerName + "!");
        System.out.println("Добро пожаловать в математическую викторину.");
        System.out.println();

        System.out.println("У каждого вопроса есть 4 варианта ответа.");
        System.out.println("Введи номер правильного варианта: 1, 2, 3 или 4.");
        System.out.println();

        printCommands();

        System.out.println(LINE);
        System.out.println();
    }

    // ================= КОМАНДЫ =================

    private void printCommands() {

        System.out.println("ДОСТУПНЫЕ КОМАНДЫ:");
        System.out.println("/hint    — подсказка к вопросу");
        System.out.println("/review  — повторить ошибки");
        System.out.println("/restart — начать заново");
        System.out.println("/profile — мой профиль и достижения");
        System.out.println("/rating  — рейтинг игроков");
        System.out.println("/help    — помощь");
        System.out.println("/exit    — выход");
        System.out.println();
    }

    private void printHelp() {

        System.out.println();
        System.out.println(LINE);
        System.out.println("                  ПОМОЩЬ");
        System.out.println(LINE);
        System.out.println();

        System.out.println("Сначала выбери тему и сложность.");
        System.out.println("Затем отвечай на вопросы, вводя номер варианта.");
        System.out.println();

        printCommands();

        System.out.println(LINE);
        System.out.println();
    }

    // ================= ФОРМАТИРОВАНИЕ ОТВЕТОВ =================

    private String formatResponse(String response) {

        if (response == null) {
            return "";
        }

        return response
                .replaceAll("([1-4])\\uFE0F?\\u20E3", "[$1]")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</?b>", "")
                .replaceAll("(?i)</?strong>", "")
                .replaceAll("(?i)</?i>", "")
                .replaceAll("(?i)</?em>", "")
                .replaceAll("(?i)</?u>", "")
                .replaceAll("(?i)</?code>", "")
                .replaceAll("(?i)</?pre>", "")
                .replace("━━━━━━━━━━━━━━━━━━━", SEPARATOR)
                .replace("━━━━━━━━━━━━━━━━━━━━", SEPARATOR)
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .trim();
    }

    // ================= ВЫВОД СООБЩЕНИЯ БОТА =================

    private void printBotMessage(String response) {

        String formatted = formatResponse(response);

        System.out.println();
        System.out.println("БОТ:");
        System.out.println(formatted);

        if (response != null
                && response.contains("КАРТОЧКА")
                && logic.isAwaitingAnswer(userId)) {

            String[] options = logic.getCurrentOptions(userId);

            if (options != null
                    && options.length > 0
                    && !response.contains(options[0])) {

                System.out.println();
                System.out.println("ВАРИАНТЫ ОТВЕТА:");

                for (int i = 0; i < options.length; i++) {
                    System.out.println(
                            "[" + (i + 1) + "] " + options[i]
                    );
                }
            }
        }

        System.out.println();
        System.out.println(SEPARATOR);
        System.out.println();
    }

    // ================= ПРОФИЛЬ =================

    private void showProfile() {

        if (database == null) {
            printDatabaseUnavailable();
            return;
        }

        try {
            DatabaseManager.ProfileData profile =
                    database.getProfile(userId);

            if (profile == null) {
                System.out.println("Профиль пока не найден.");
                return;
            }

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                            .withZone(ZoneId.systemDefault());

            String registrationDate =
                    formatter.format(
                            Instant.ofEpochMilli(profile.registeredAt())
                    );

            int questionsAnswered = profile.questionsAnswered();
            int correctAnswers = profile.correctAnswers();
            int quizzesCompleted = profile.quizzesCompleted();

            double accuracy = questionsAnswered == 0
                    ? 0.0
                    : correctAnswers * 100.0 / questionsAnswered;

            String currentTitle = getTitle(correctAnswers);
            int nextThreshold = getNextThreshold(correctAnswers);
            String nextTitle = getNextTitle(correctAnswers);

            System.out.println();
            System.out.println(LINE);
            System.out.println("              🏆 МОЙ ПРОФИЛЬ");
            System.out.println(LINE);
            System.out.println();

            System.out.println("👤 " + getDisplayName());
            System.out.println("📅 В викторине с " + registrationDate);
            System.out.println();

            System.out.println("ТВОЙ ТИТУЛ:");
            System.out.println(currentTitle);
            System.out.println(correctAnswers + " правильных ответов");
            System.out.println();

            System.out.println("СТАТИСТИКА:");
            System.out.println("🎮 Викторин пройдено: " + quizzesCompleted);
            System.out.println("📖 Вопросов отвечено: " + questionsAnswered);
            System.out.println("✅ Правильных ответов: " + correctAnswers);

            System.out.printf(
                    Locale.US,
                    "🎯 Точность: %.1f%%%n",
                    accuracy
            );

            System.out.println();

            if (nextThreshold == -1) {

                System.out.println("👑 Максимальный титул достигнут!");
                System.out.println(createProgressBar(1.0));
                System.out.println(
                        "Ты достиг вершины математических знаний!"
                );

            } else {

                int previousThreshold =
                        getCurrentThreshold(correctAnswers);

                double progress =
                        (correctAnswers - previousThreshold)
                                / (double) (nextThreshold - previousThreshold);

                System.out.println("✨ СЛЕДУЮЩАЯ ЦЕЛЬ:");
                System.out.println(nextTitle);
                System.out.println(nextThreshold + " правильных ответов");

                System.out.println();
                System.out.println(createProgressBar(progress));

                System.out.println(
                        correctAnswers + " / " + nextThreshold
                );

                System.out.println(
                        "Осталось "
                                + (nextThreshold - correctAnswers)
                                + " правильных ответов!"
                );
            }

            System.out.println();
            System.out.println(LINE);
            System.out.println();

        } catch (SQLException e) {

            System.out.println(
                    "Не удалось загрузить профиль: " + e.getMessage()
            );
        }
    }

    // ================= РЕЙТИНГ =================

    private void showRating() {

        if (database == null) {
            printDatabaseUnavailable();
            return;
        }

        try {
            List<DatabaseManager.RankingEntry> ranking =
                    database.getRanking();

            DatabaseManager.UserRanking userRanking =
                    database.getUserRanking(userId);

            System.out.println();
            System.out.println(LINE);
            System.out.println("             🏆 РЕЙТИНГ ИГРОКОВ");
            System.out.println(LINE);
            System.out.println();

            if (ranking == null || ranking.isEmpty()) {

                System.out.println("Пока рейтинг пуст.");
                System.out.println(
                        "Пройди первую викторину и займи своё место!"
                );

            } else {

                for (int i = 0; i < ranking.size(); i++) {

                    DatabaseManager.RankingEntry entry =
                            ranking.get(i);

                    String medal;

                    switch (i) {
                        case 0 -> medal = "🥇";
                        case 1 -> medal = "🥈";
                        case 2 -> medal = "🥉";
                        default -> medal = (i + 1) + ".";
                    }

                    String name = entry.displayName();

                    if (name == null || name.isBlank()) {
                        name = "Игрок";
                    }

                    System.out.println(
                            medal + " " + name
                                    + " — " + entry.correctAnswers()
                    );
                }
            }

            System.out.println();
            System.out.println(SEPARATOR);
            System.out.println();
            System.out.println("ТВОЙ РЕЗУЛЬТАТ:");

            if (userRanking == null) {

                System.out.println(
                        "Ты пока не участвуешь в рейтинге."
                );

                System.out.println(
                        "Пройди обычную викторину, чтобы получить место!"
                );

            } else {

                System.out.println(
                        "Место: №" + userRanking.rank()
                );

                System.out.println(
                        "Правильных ответов: "
                                + userRanking.correctAnswers()
                );

                if (userRanking.rank() == 1) {
                    System.out.println("👑 Ты возглавляешь рейтинг!");

                } else if (userRanking.rank() <= 3) {
                    System.out.println(
                            "✨ Ты в тройке лучших! Так держать!"
                    );

                } else {
                    System.out.println(
                            "Продолжай играть и поднимайся выше!"
                    );
                }
            }

            System.out.println();
            System.out.println(LINE);
            System.out.println();

        } catch (SQLException e) {

            System.out.println(
                    "Не удалось загрузить рейтинг: " + e.getMessage()
            );
        }
    }

    // ================= СОХРАНЕНИЕ РЕЗУЛЬТАТА =================

    private void recordCompletedQuiz() {

        if (database == null) {
            return;
        }

        int questionsCount =
                logic.getRoundQuestionCount(userId);

        int correctCount =
                logic.getRoundCorrectCount(userId);

        if (questionsCount <= 0) {
            return;
        }

        try {

            DatabaseManager.ProfileData before =
                    database.getProfile(userId);

            int oldCorrectAnswers =
                    before == null ? 0 : before.correctAnswers();

            String oldTitle = getTitle(oldCorrectAnswers);

            database.recordQuizAttempt(
                    userId,
                    questionsCount,
                    correctCount
            );

            System.out.println(
                    "Результат викторины сохранён в базе данных."
            );

            DatabaseManager.ProfileData after =
                    database.getProfile(userId);

            if (after != null) {

                String newTitle =
                        getTitle(after.correctAnswers());

                if (!oldTitle.equals(newTitle)) {

                    System.out.println();
                    System.out.println("🎉 НОВОЕ ДОСТИЖЕНИЕ!");
                    System.out.println("Ты получила новый титул:");
                    System.out.println(newTitle);
                    System.out.println();
                }
            }

        } catch (SQLException e) {

            System.out.println(
                    "Не удалось сохранить результат: " + e.getMessage()
            );
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

        StringBuilder bar = new StringBuilder("[");

        for (int i = 0; i < totalBlocks; i++) {

            if (i < filledBlocks) {
                bar.append("#");
            } else {
                bar.append("-");
            }
        }

        bar.append("]");

        return bar.toString();
    }

    // ================= ОШИБКА БАЗЫ ДАННЫХ =================

    private void printDatabaseUnavailable() {

        System.out.println(
                "Функция недоступна: нет подключения к базе данных."
        );
    }

    // ================= ЗАПУСК =================

    public void start() {

        printWelcomeMessage();

        String response =
                logic.processInput(userId, "");

        printBotMessage(response);

        while (true) {

            System.out.print("Твой ответ: ");

            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")
                    || input.equalsIgnoreCase("/exit")) {

                System.out.println();
                System.out.println(SEPARATOR);
                System.out.println("Ты завершил викторину. До встречи!");
                System.out.println(SEPARATOR);

                return;
            }

            if (input.equalsIgnoreCase("/profile")) {
                showProfile();
                continue;
            }

            if (input.equalsIgnoreCase("/rating")) {
                showRating();
                continue;
            }

            if (input.equalsIgnoreCase("/help")) {
                printHelp();
                continue;
            }

            boolean regularQuizAnswer =
                    logic.isAwaitingAnswer(userId)
                            && !logic.isReviewMode(userId)
                            && input.matches("[1-4]");

            response = logic.processInput(userId, input);

            if (regularQuizAnswer && logic.isFinished(userId)) {
                recordCompletedQuiz();
            }

            printBotMessage(response);
        }
    }
}
