
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

    // Создаём таблицы, если они ещё не существуют
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
    public int recordActivity(String telegramId, String chatId)
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

            return statement.executeUpdate();
        }
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
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, telegramId);
            statement.setInt(2, questionIndex);
            statement.setString(3, questionText);
            statement.setString(4, comment);
            statement.setLong(5, System.currentTimeMillis());

            statement.executeUpdate();

            // Получаем ID добавленной жалобы
            try (Statement idStatement = connection.createStatement();
                 ResultSet result = idStatement.executeQuery(
                         "SELECT last_insert_rowid()")) {

                if (result.next()) {
                    return result.getInt(1);
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
                INSERT OR REPLACE INTO admins (telegram_id, name)
                VALUES (?, ?)
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
