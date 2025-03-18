package com.chatapp.server.storage;

import com.chatapp.common.config.Config;
import com.chatapp.common.model.Message;
import com.chatapp.common.model.User;
import com.chatapp.util.Logger;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager implements AutoCloseable {
    private Connection connection;

    public DatabaseManager() {
        try {
            connection = DriverManager.getConnection(
                    Config.DB_URL,
                    Config.DB_USER,
                    Config.DB_PASSWORD
            );
            initializeDatabase();
        } catch (SQLException e) {
            Logger.error("Błąd podczas łączenia z bazą danych: " + e.getMessage());
        }
    }

    private void initializeDatabase() {
        try {
            createTablesIfNotExist();
            addTestUsersIfNeeded();
        } catch (SQLException e) {
            Logger.error("Błąd podczas inicjalizacji bazy danych: " + e.getMessage());
        }
    }

    private void createTablesIfNotExist() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Tabela użytkowników
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(100) NOT NULL" +
                    ")");

            // Tabela wiadomości
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "sender_id INT NOT NULL, " +
                    "receiver_id INT, " +
                    "content TEXT NOT NULL, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (sender_id) REFERENCES users(id), " +
                    "FOREIGN KEY (receiver_id) REFERENCES users(id)" +
                    ")");
        }
    }

    private void addTestUsersIfNeeded() throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM users")) {
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO users (username, password) VALUES (?, ?)")) {

                    // Dodaj admin/admin
                    insertStmt.setString(1, "admin");
                    insertStmt.setString(2, "admin");
                    insertStmt.executeUpdate();

                    // Dodaj user/user
                    insertStmt.setString(1, "user");
                    insertStmt.setString(2, "user");
                    insertStmt.executeUpdate();

                    Logger.info("Dodano testowych użytkowników: admin/admin, user/user");
                }
            }
        }
    }

    public User registerUser(String username, String password) {
        try {
            // Sprawdzenie czy użytkownik już istnieje
            try (PreparedStatement checkStmt = connection.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                checkStmt.setString(1, username);

                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Użytkownik istnieje
                        return null;
                    }
                }
            }

            // Dodanie użytkownika
            try (PreparedStatement insertStmt = connection.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                insertStmt.setString(1, username);
                insertStmt.setString(2, password); // W produkcji powinno być hashowane!

                int affectedRows = insertStmt.executeUpdate();
                if (affectedRows == 0) {
                    return null;
                }

                // Pobranie ID
                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int userId = generatedKeys.getInt(1);
                        User user = new User(userId, username);
                        user.setPassword(password);
                        return user;
                    }
                }
            }

            return null;
        } catch (SQLException e) {
            Logger.error("Błąd podczas rejestracji użytkownika: " + e.getMessage());
            return null;
        }
    }

    public User authenticateUser(String username, String password) {
        try {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT id, username, password FROM users WHERE username = ?")) {

                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("password");

                        if (password.equals(storedPassword)) {
                            User user = new User(rs.getInt("id"), rs.getString("username"));
                            Logger.debug("Autoryzacja udana dla: " + user.getUsername());
                            return user;
                        }
                    }
                }
            }

            return null;
        } catch (SQLException e) {
            Logger.error("Błąd podczas autoryzacji: " + e.getMessage());
            return null;
        }
    }

    public boolean saveMessage(Message message) {
        try {
            String sql;
            PreparedStatement stmt;

            if (message.isPrivate()) {
                sql = "INSERT INTO messages (sender_id, receiver_id, content, timestamp) VALUES (?, ?, ?, ?)";
                stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, message.getSender().getId());
                stmt.setInt(2, message.getReceiver().getId());
                stmt.setString(3, message.getContent());
                stmt.setTimestamp(4, Timestamp.valueOf(message.getTimestamp()));
            } else {
                sql = "INSERT INTO messages (sender_id, content, timestamp) VALUES (?, ?, ?)";
                stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, message.getSender().getId());
                stmt.setString(2, message.getContent());
                stmt.setTimestamp(3, Timestamp.valueOf(message.getTimestamp()));
            }

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        message.setId(generatedKeys.getInt(1));
                    }
                }
                stmt.close();
                return true;
            }

            stmt.close();
            return false;
        } catch (SQLException e) {
            Logger.error("Błąd podczas zapisywania wiadomości: " + e.getMessage());
            return false;
        }
    }

    public List<Message> getPrivateMessages(int user1Id, int user2Id, int limit) {
        List<Message> messages = new ArrayList<>();

        try {
            String sql = "SELECT m.id, m.content, m.timestamp, " +
                    "sender.id AS sender_id, sender.username AS sender_username, " +
                    "receiver.id AS receiver_id, receiver.username AS receiver_username " +
                    "FROM messages m " +
                    "JOIN users sender ON m.sender_id = sender.id " +
                    "JOIN users receiver ON m.receiver_id = receiver.id " +
                    "WHERE (m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?) " +
                    "ORDER BY m.timestamp ASC LIMIT ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, user1Id);
                stmt.setInt(2, user2Id);
                stmt.setInt(3, user2Id);
                stmt.setInt(4, user1Id);
                stmt.setInt(5, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(createMessageFromResultSet(rs, true));
                    }
                }
            }
        } catch (SQLException e) {
            Logger.error("Błąd podczas pobierania prywatnych wiadomości: " + e.getMessage());
        }

        return messages;
    }

    public List<Message> getRecentMessages(int limit) {
        List<Message> messages = new ArrayList<>();

        try {
            String sql = "SELECT m.id, m.content, m.timestamp, " +
                    "u.id AS user_id, u.username " +
                    "FROM messages m " +
                    "JOIN users u ON m.sender_id = u.id " +
                    "WHERE m.receiver_id IS NULL " +
                    "ORDER BY m.timestamp DESC LIMIT ?";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(0, createMessageFromResultSet(rs, false));
                    }
                }
            }
        } catch (SQLException e) {
            Logger.error("Błąd podczas pobierania wiadomości: " + e.getMessage());
        }

        return messages;
    }

    private Message createMessageFromResultSet(ResultSet rs, boolean isPrivate) throws SQLException {
        Message message = new Message();
        message.setId(rs.getInt("id"));
        message.setContent(rs.getString("content"));
        message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());

        if (isPrivate) {
            User sender = new User(rs.getInt("sender_id"), rs.getString("sender_username"));
            User receiver = new User(rs.getInt("receiver_id"), rs.getString("receiver_username"));
            message.setSender(sender);
            message.setReceiver(receiver);
        } else {
            User sender = new User(rs.getInt("user_id"), rs.getString("username"));
            message.setSender(sender);
        }

        return message;
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();

        try {
            try (Statement stmt = connection.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, username FROM users")) {
                    while (rs.next()) {
                        User user = new User(rs.getInt("id"), rs.getString("username"));
                        users.add(user);
                    }
                }
            }
        } catch (SQLException e) {
            Logger.error("Błąd podczas pobierania użytkowników: " + e.getMessage());
        }

        return users;
    }

    public List<Object[]> getAllUsersForDisplay() {
        List<Object[]> users = new ArrayList<>();

        try {
            try (Statement stmt = connection.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT id, username, password FROM users")) {
                    while (rs.next()) {
                        Object[] row = {
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("password")
                        };
                        users.add(row);
                    }
                }
            }
        } catch (SQLException e) {
            Logger.error("Błąd podczas pobierania użytkowników do wyświetlenia: " + e.getMessage());
        }

        return users;
    }

    public List<Object[]> getAllMessagesWithUsernames() {
        List<Object[]> messages = new ArrayList<>();

        try {
            String sql = "SELECT m.id, sender.username AS sender_username, " +
                    "receiver.username AS receiver_username, m.content, m.timestamp " +
                    "FROM MESSAGES m " +
                    "JOIN USERS sender ON m.sender_id = sender.id " +
                    "LEFT JOIN USERS receiver ON m.receiver_id = receiver.id " +
                    "ORDER BY m.timestamp";

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String receiverUsername = rs.getString("receiver_username");
                        String messageType = receiverUsername == null ? "Publiczna" : "Prywatna do " + receiverUsername;

                        Object[] message = {
                                rs.getInt("id"),
                                rs.getString("sender_username"),
                                messageType,
                                rs.getString("content"),
                                rs.getTimestamp("timestamp")
                        };
                        messages.add(message);
                    }
                }
            }
        } catch (SQLException e) {
            Logger.error("Błąd podczas pobierania wiadomości: " + e.getMessage());
        }

        return messages;
    }

    public void showDatabaseContentUI() {
        new DatabaseUI(this).showDatabaseContentUI();
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                Logger.debug("Połączenie z bazą danych zamknięte");
            }
        } catch (SQLException e) {
            Logger.error("Błąd podczas zamykania połączenia: " + e.getMessage());
        }
    }
}