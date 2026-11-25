
package com.practice.data;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DATABASE_URL =
            System.getenv().getOrDefault(
                    "DB_URL",
                    "jdbc:postgresql://localhost:5432/quiz_bot_db"
            );

    private static final String DATABASE_USER =
            System.getenv().getOrDefault("DB_USER", "postgres");

    private static final String DATABASE_PASSWORD =
            System.getenv("DB_PASSWORD");

    private static final long THREE_DAYS =
            3L * 24 * 60 * 60 * 1000;

    public DatabaseManager() throws SQLException {
        createTables();
    }

    private Connection connect() throws SQLException {
        if (DATABASE_PASSWORD == null || DATABASE_PASSWORD.isBlank()) {
            throw new SQLException(
                    "Не задана переменная окружения DB_PASSWORD"
            );
        }

        return DriverManager.getConnection(
                DATABASE_URL,
                DATABASE_USER,
                DATABASE_PASSWORD
        );
    }

    // Создаём и обновляем таблицы
    private void createTables() throws SQLException {

        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    telegram_id TEXT PRIMARY KEY,
                    chat_id TEXT NOT NULL,
                    last_activity BIGINT NOT NULL,
                    last_reminder BIGINT
                )
                """;

        String complaintsTable = """
                CREATE TABLE IF NOT EXISTS complaints (
                    id BIGSERIAL PRIMARY KEY,
                    telegram_id TEXT NOT NULL,
                    question_index INTEGER,
                    question_text TEXT NOT NULL,
                    comment TEXT,
                    created_at BIGINT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'NEW'
                )
                """;

        String adminsTable = """
                CREATE TABLE IF NOT EXISTS admins (
                    telegram_id TEXT PRIMARY KEY,
                    name TEXT
                )
                """;

        String quizAttemptsTable = """
                CREATE TABLE IF NOT EXISTS quiz_attempts (
                    id BIGSERIAL PRIMARY KEY,
                    telegram_id TEXT NOT NULL,
                    completed_at BIGINT NOT NULL,
                    questions_count INTEGER NOT NULL,
                    correct_count INTEGER NOT NULL
                )
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {

            statement.execute(usersTable);
            statement.execute(complaintsTable);
            statement.execute(adminsTable);
            statement.execute(quizAttemptsTable);

            // Добавляем новые столбцы в существующую таблицу
            statement.execute("""
                    ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS display_name TEXT
                    """);

            statement.execute("""
                    ALTER TABLE users
                    ADD COLUMN IF NOT EXISTS registered_at BIGINT
                    """);

            // Заполняем дату регистрации старых пользователей
            statement.execute("""
                    UPDATE users
                    SET registered_at = last_activity
                    WHERE registered_at IS NULL
                    """);
        }
    }

    // Сохраняем активность пользователя
    public int recordActivity(String telegramId, String chatId)
            throws SQLException {
        return recordActivity(telegramId, chatId, null);
    }

    // Сохраняем активность и имя пользователя
    public int recordActivity(
            String telegramId,
            String chatId,
            String displayName
    ) throws SQLException {

        String sql = """
                INSERT INTO users
                (telegram_id, chat_id, last_activity,
                 last_reminder, display_name, registered_at)
                VALUES (?, ?, ?, NULL, ?, ?)
                ON CONFLICT (telegram_id) DO UPDATE SET
                    chat_id = EXCLUDED.chat_id,
                    last_activity = EXCLUDED.last_activity,
                    last_reminder = NULL,
                    display_name = COALESCE(
                        EXCLUDED.display_name,
                        users.display_name
                    )
                """;

        long now = System.currentTimeMillis();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setString(2, chatId);
            statement.setLong(3, now);
            statement.setString(4, displayName);
            statement.setLong(5, now);

            return statement.executeUpdate();
        }
    }

    // Сохраняем завершённую викторину
    public int recordQuizAttempt(
            String telegramId,
            int questionsCount,
            int correctCount
    ) throws SQLException {

        String sql = """
                INSERT INTO quiz_attempts
                (telegram_id, completed_at, questions_count, correct_count)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setLong(2, System.currentTimeMillis());
            statement.setInt(3, questionsCount);
            statement.setInt(4, correctCount);

            return statement.executeUpdate();
        }
    }

    // Получаем профиль пользователя
    public ProfileData getProfile(String telegramId)
            throws SQLException {

        String sql = """
                SELECT
                    u.display_name,
                    u.registered_at,
                    COALESCE(COUNT(q.id), 0) AS quizzes_completed,
                    COALESCE(SUM(q.questions_count), 0) AS questions_answered,
                    COALESCE(SUM(q.correct_count), 0) AS correct_answers
                FROM users u
                LEFT JOIN quiz_attempts q
                    ON u.telegram_id = q.telegram_id
                WHERE u.telegram_id = ?
                GROUP BY u.telegram_id, u.display_name, u.registered_at
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return new ProfileData(
                            result.getString("display_name"),
                            result.getLong("registered_at"),
                            result.getInt("quizzes_completed"),
                            result.getInt("questions_answered"),
                            result.getInt("correct_answers")
                    );
                }
            }
        }

        return null;
    }

    // Данные профиля
    public record ProfileData(
            String displayName,
            long registeredAt,
            int quizzesCompleted,
            int questionsAnswered,
            int correctAnswers
    ) {
    }

    // Получаем топ-10 пользователей рейтинга
    public List<RankingEntry> getRanking() throws SQLException {

        List<RankingEntry> ranking = new ArrayList<>();

        String sql = """
                SELECT
                    u.telegram_id,
                    COALESCE(
                        NULLIF(u.display_name, ''),
                        'Игрок'
                    ) AS display_name,
                    SUM(q.correct_count) AS total_correct
                FROM users u
                JOIN quiz_attempts q
                    ON u.telegram_id = q.telegram_id
                GROUP BY u.telegram_id, u.display_name
                ORDER BY
                    total_correct DESC,
                    COUNT(q.id) DESC,
                    display_name ASC
                LIMIT 10
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                ranking.add(new RankingEntry(
                        result.getString("telegram_id"),
                        result.getString("display_name"),
                        result.getInt("total_correct")
                ));
            }
        }

        return ranking;
    }

    // Получаем место конкретного пользователя в рейтинге
    public UserRanking getUserRanking(String telegramId)
            throws SQLException {

        String sql = """
                WITH player_totals AS (
                    SELECT
                        u.telegram_id,
                        COALESCE(
                            NULLIF(u.display_name, ''),
                            'Игрок'
                        ) AS display_name,
                        SUM(q.correct_count) AS total_correct,
                        COUNT(q.id) AS quizzes_completed
                    FROM users u
                    JOIN quiz_attempts q
                        ON u.telegram_id = q.telegram_id
                    GROUP BY u.telegram_id, u.display_name
                ),
                ranked_players AS (
                    SELECT
                        telegram_id,
                        display_name,
                        total_correct,
                        ROW_NUMBER() OVER (
                            ORDER BY
                                total_correct DESC,
                                quizzes_completed DESC,
                                display_name ASC
                        ) AS player_rank
                    FROM player_totals
                )
                SELECT
                    player_rank,
                    display_name,
                    total_correct
                FROM ranked_players
                WHERE telegram_id = ?
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);

            try (ResultSet result = statement.executeQuery()) {

                if (result.next()) {
                    return new UserRanking(
                            result.getInt("player_rank"),
                            result.getString("display_name"),
                            result.getInt("total_correct")
                    );
                }
            }
        }

        return null;
    }

    // Данные одного участника рейтинга
    public record RankingEntry(
            String telegramId,
            String displayName,
            int correctAnswers
    ) {
    }

    // Место пользователя в общем рейтинге
    public record UserRanking(
            int rank,
            String displayName,
            int correctAnswers
    ) {
    }

    // Получаем пользователей, которым пора отправить напоминание
    public List<UserForReminder> getUsersDueForReminder()
            throws SQLException {

        List<UserForReminder> users = new ArrayList<>();

        String sql = """
                SELECT telegram_id, chat_id
                FROM users
                WHERE last_activity <= ?
                AND (
                    last_reminder IS NULL
                    OR last_reminder <= ?
                )
                """;

        long now = System.currentTimeMillis();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, now - THREE_DAYS);
            statement.setLong(2, now - THREE_DAYS);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    users.add(new UserForReminder(
                            result.getString("telegram_id"),
                            result.getString("chat_id")
                    ));
                }
            }
        }

        return users;
    }

    // Сохраняем время отправки напоминания
    public int markReminderSent(String telegramId)
            throws SQLException {

        String sql = """
                UPDATE users
                SET last_reminder = ?
                WHERE telegram_id = ?
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, telegramId);

            return statement.executeUpdate();
        }
    }

    // Добавляем жалобу и возвращаем её ID
    public int addComplaint(
            String telegramId,
            int questionIndex,
            String questionText,
            String comment
    ) throws SQLException {

        String sql = """
                INSERT INTO complaints
                (telegram_id, question_index, question_text,
                 comment, created_at, status)
                VALUES (?, ?, ?, ?, ?, 'NEW')
                RETURNING id
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setInt(2, questionIndex);
            statement.setString(3, questionText);
            statement.setString(4, comment);
            statement.setLong(5, System.currentTimeMillis());

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("id");
                }
            }
        }

        throw new SQLException("Не удалось получить ID созданной жалобы");
    }

    // Получаем нерассмотренные жалобы
    public List<Complaint> getNewComplaints()
            throws SQLException {

        List<Complaint> complaints = new ArrayList<>();

        String sql = """
                SELECT *
                FROM complaints
                WHERE status = 'NEW'
                ORDER BY created_at
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {

            while (result.next()) {
                complaints.add(new Complaint(
                        result.getInt("id"),
                        result.getString("telegram_id"),
                        result.getInt("question_index"),
                        result.getString("question_text"),
                        result.getString("comment"),
                        result.getLong("created_at")
                ));
            }
        }

        return complaints;
    }

    // Отмечаем жалобу как рассмотренную
    public int markComplaintReviewed(int complaintId)
            throws SQLException {

        String sql = """
                UPDATE complaints
                SET status = 'REVIEWED'
                WHERE id = ?
                AND status = 'NEW'
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, complaintId);

            return statement.executeUpdate();
        }
    }

    // Добавляем администратора
    public int addAdmin(String telegramId, String name)
            throws SQLException {

        String sql = """
                INSERT INTO admins (telegram_id, name)
                VALUES (?, ?)
                ON CONFLICT (telegram_id) DO UPDATE SET
                    name = EXCLUDED.name
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setString(2, name);

            return statement.executeUpdate();
        }
    }

    // Проверяем, является ли пользователь администратором
    public boolean isAdmin(String telegramId)
            throws SQLException {

        String sql = """
                SELECT 1
                FROM admins
                WHERE telegram_id = ?
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
