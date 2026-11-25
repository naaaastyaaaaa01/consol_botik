
package com.practice.data;

import java.sql.*;

public class DatabaseMigration {

    private static final String SQLITE_URL = "jdbc:sqlite:bot.db";

    private static final String POSTGRES_URL =
            System.getenv().getOrDefault(
                    "DB_URL",
                    "jdbc:postgresql://localhost:5432/quiz_bot_db"
            );

    private static final String POSTGRES_USER =
            System.getenv().getOrDefault("DB_USER", "postgres");

    public static void main(String[] args) {

        String password = System.getenv("DB_PASSWORD");

        if (password == null || password.isBlank()) {
            System.out.println("╨Э╨╡ ╨╖╨░╨┤╨░╨╜╨░ ╨┐╨╡╤А╨╡╨╝╨╡╨╜╨╜╨░╤П DB_PASSWORD");
            return;
        }

        try {
            // ╨б╨╛╨╖╨┤╨░╤С╨╝ ╤В╨░╨▒╨╗╨╕╤Ж╤Л PostgreSQL, ╨╡╤Б╨╗╨╕ ╨╕╤Е ╨╡╤Й╤С ╨╜╨╡╤В
            new DatabaseManager();

            try (Connection sqlite = DriverManager.getConnection(SQLITE_URL);
                 Connection postgres = DriverManager.getConnection(
                         POSTGRES_URL, POSTGRES_USER, password
                 )) {

                postgres.setAutoCommit(false);

                try {
                    int users = migrateUsers(sqlite, postgres);
                    int complaints = migrateComplaints(sqlite, postgres);
                    int admins = migrateAdmins(sqlite, postgres);

                    updateComplaintSequence(postgres);

                    postgres.commit();

                    System.out.println("╨Я╨╡╤А╨╡╨╜╨╛╤Б ╤Г╤Б╨┐╨╡╤И╨╜╨╛ ╨╖╨░╨▓╨╡╤А╤И╤С╨╜!");
                    System.out.println("╨Я╨╛╨╗╤М╨╖╨╛╨▓╨░╤В╨╡╨╗╨╡╨╣: " + users);
                    System.out.println("╨Ц╨░╨╗╨╛╨▒: " + complaints);
                    System.out.println("╨Р╨┤╨╝╨╕╨╜╨╕╤Б╤В╤А╨░╤В╨╛╤А╨╛╨▓: " + admins);

                } catch (SQLException e) {
                    postgres.rollback();
                    throw e;
                }
            }

        } catch (SQLException e) {
            System.out.println("╨Ю╤И╨╕╨▒╨║╨░ ╨┐╨╡╤А╨╡╨╜╨╛╤Б╨░ ╨┤╨░╨╜╨╜╤Л╤Е:");
            e.printStackTrace();
        }
    }

    private static int migrateUsers(
            Connection sqlite,
            Connection postgres
    ) throws SQLException {

        String selectSql = """
                SELECT telegram_id, chat_id, last_activity, last_reminder
                FROM users
                """;

        String insertSql = """
                INSERT INTO users
                (telegram_id, chat_id, last_activity, last_reminder)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (telegram_id) DO UPDATE SET
                    chat_id = EXCLUDED.chat_id,
                    last_activity = EXCLUDED.last_activity,
                    last_reminder = EXCLUDED.last_reminder
                """;

        int count = 0;

        try (Statement select = sqlite.createStatement();
             ResultSet result = select.executeQuery(selectSql);
             PreparedStatement insert = postgres.prepareStatement(insertSql)) {

            while (result.next()) {
                insert.setString(1, result.getString("telegram_id"));
                insert.setString(2, result.getString("chat_id"));
                insert.setLong(3, result.getLong("last_activity"));

                Object reminder = result.getObject("last_reminder");

                if (reminder == null) {
                    insert.setNull(4, Types.BIGINT);
                } else {
                    insert.setLong(4, ((Number) reminder).longValue());
                }

                insert.executeUpdate();
                count++;
            }
        }

        return count;
    }

    private static int migrateComplaints(
            Connection sqlite,
            Connection postgres
    ) throws SQLException {

        String selectSql = """
                SELECT id, telegram_id, question_index, question_text,
                       comment, created_at, status
                FROM complaints
                """;

        String insertSql = """
                INSERT INTO complaints
                (id, telegram_id, question_index, question_text,
                 comment, created_at, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """;

        int count = 0;

        try (Statement select = sqlite.createStatement();
             ResultSet result = select.executeQuery(selectSql);
             PreparedStatement insert = postgres.prepareStatement(insertSql)) {

            while (result.next()) {
                insert.setInt(1, result.getInt("id"));
                insert.setString(2, result.getString("telegram_id"));

                Object questionIndex = result.getObject("question_index");

                if (questionIndex == null) {
                    insert.setNull(3, Types.INTEGER);
                } else {
                    insert.setInt(3, ((Number) questionIndex).intValue());
                }

                insert.setString(4, result.getString("question_text"));
                insert.setString(5, result.getString("comment"));
                insert.setLong(6, result.getLong("created_at"));
                insert.setString(7, result.getString("status"));

                count += insert.executeUpdate();
            }
        }

        return count;
    }

    private static int migrateAdmins(
            Connection sqlite,
            Connection postgres
    ) throws SQLException {

        String selectSql = """
                SELECT telegram_id, name
                FROM admins
                """;

        String insertSql = """
                INSERT INTO admins (telegram_id, name)
                VALUES (?, ?)
                ON CONFLICT (telegram_id) DO UPDATE SET
                    name = EXCLUDED.name
                """;

        int count = 0;

        try (Statement select = sqlite.createStatement();
             ResultSet result = select.executeQuery(selectSql);
             PreparedStatement insert = postgres.prepareStatement(insertSql)) {

            while (result.next()) {
                insert.setString(1, result.getString("telegram_id"));
                insert.setString(2, result.getString("name"));

                insert.executeUpdate();
                count++;
            }
        }

        return count;
    }

    private static void updateComplaintSequence(
            Connection postgres
    ) throws SQLException {

        String sql = """
                SELECT setval(
                    pg_get_serial_sequence('complaints', 'id'),
                    COALESCE(MAX(id), 1),
                    MAX(id) IS NOT NULL
                )
                FROM complaints
                """;

        try (Statement statement = postgres.createStatement()) {
            statement.executeQuery(sql);
        }
    }
}
