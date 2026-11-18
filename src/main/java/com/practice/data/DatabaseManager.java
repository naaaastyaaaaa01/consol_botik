
package com.practice.data;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static final String DATABASE_URL = "jdbc:sqlite:bot.db";

    private static final long THREE_DAYS =
            3L * 24 * 60 * 60 * 1000;

    public DatabaseManager() throws SQLException {
        createTables();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    private void createTables() throws SQLException {
        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    telegram_id TEXT PRIMARY KEY,
                    chat_id TEXT NOT NULL,
                    last_activity INTEGER NOT NULL,
                    last_reminder INTEGER
                )
                """;

        String complaintsTable = """
                CREATE TABLE IF NOT EXISTS complaints (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    telegram_id TEXT NOT NULL,
                    question_index INTEGER,
                    question_text TEXT NOT NULL,
                    comment TEXT,
                    created_at INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'NEW'
                )
                """;

        String adminsTable = """
                CREATE TABLE IF NOT EXISTS admins (
                    telegram_id TEXT PRIMARY KEY,
                    name TEXT
                )
                """;

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {

            statement.execute(usersTable);
            statement.execute(complaintsTable);
            statement.execute(adminsTable);
        }
    }

    // Сохраняем активность пользователя
    public void recordActivity(String telegramId, String chatId)
            throws SQLException {

        String sql = """
                INSERT INTO users
                (telegram_id, chat_id, last_activity, last_reminder)
                VALUES (?, ?, ?, NULL)
                ON CONFLICT(telegram_id) DO UPDATE SET
                    chat_id = excluded.chat_id,
                    last_activity = excluded.last_activity,
                    last_reminder = NULL
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setString(2, chatId);
            statement.setLong(3, System.currentTimeMillis());

            statement.executeUpdate();
        }
    }

    // Получаем пользователей, которым пора отправить напоминание
    public List<UserForReminder> getUsersDueForReminder()
            throws SQLException {

        List<UserForReminder> users = new ArrayList<>();

        String sql = """
                SELECT telegram_id, chat_id
                FROM users
                WHERE
                    last_activity <= ?
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

    // Запоминаем время отправки напоминания
    public void markReminderSent(String telegramId)
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

            statement.executeUpdate();
        }
    }

    // Добавляем жалобу
    public void addComplaint(
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
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setInt(2, questionIndex);
            statement.setString(3, questionText);
            statement.setString(4, comment);
            statement.setLong(5, System.currentTimeMillis());

            statement.executeUpdate();
        }
    }

    // Получаем все ещё не рассмотренные жалобы
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
    public void markComplaintReviewed(int complaintId)
            throws SQLException {

        String sql = """
                UPDATE complaints
                SET status = 'REVIEWED'
                WHERE id = ?
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, complaintId);
            statement.executeUpdate();
        }
    }

    // Добавляем администратора
    public void addAdmin(String telegramId, String name)
            throws SQLException {

        String sql = """
                INSERT OR REPLACE INTO admins (telegram_id, name)
                VALUES (?, ?)
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setString(2, name);
            statement.executeUpdate();
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

    // Данные пользователя для отправки напоминания
    public static class UserForReminder {
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

    // Данные жалобы
    public static class Complaint {
        private final int id;
        private final String telegramId;
        private final int questionIndex;
        private final String questionText;
        private final String comment;
        private final long createdAt;

        public Complaint(
                int id,
                String telegramId,
                int questionIndex,
                String questionText,
                String comment,
                long createdAt
        ) {
            this.id = id;
            this.telegramId = telegramId;
            this.questionIndex = questionIndex;
            this.questionText = questionText;
            this.comment = comment;
            this.createdAt = createdAt;
        }

        public int getId() {
            return id;
        }

        public String getTelegramId() {
            return telegramId;
        }

        public int getQuestionIndex() {
            return questionIndex;
        }

        public String getQuestionText() {
            return questionText;
        }

        public String getComment() {
            return comment;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }
}
