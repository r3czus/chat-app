package com.chatapp.db;

import com.chatapp.model.Message;
import com.chatapp.model.User;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:h2:./chatdb;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private Connection connection;

    public DatabaseManager() {
        try {
            // Inicjalizacja połączenia
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // Wyczyść i zainicjuj bazę danych, jeśli potrzeba
            initializeDatabase();
        } catch (SQLException e) {
            System.err.println("Błąd podczas łączenia z bazą danych: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        try {
            // Sprawdź czy tabela messages istnieje i czy ma odpowiednią strukturę
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "MESSAGES", null);

            boolean messagesTableExists = tables.next();
            tables.close();

            // Jeśli tabela messages istnieje, sprawdź czy ma kolumnę receiver_id
            boolean receiverColumnExists = false;
            if (messagesTableExists) {
                ResultSet columns = metaData.getColumns(null, null, "MESSAGES", "RECEIVER_ID");
                receiverColumnExists = columns.next();
                columns.close();

                // Jeśli tabela istnieje, ale nie ma odpowiedniej kolumny, zrzuć ją i utwórz na nowo
                if (!receiverColumnExists) {
                    System.out.println("Tabela MESSAGES istnieje, ale brakuje kolumny RECEIVER_ID. Ponowne tworzenie tabeli...");
                    Statement dropStmt = connection.createStatement();
                    dropStmt.execute("DROP TABLE messages");
                    dropStmt.close();
                    messagesTableExists = false;
                }
            }

            // Utworzenie tablic jeśli nie istnieją
            Statement stmt = connection.createStatement();

            // Tabela użytkowników
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "password VARCHAR(100) NOT NULL" +
                    ")");

            // Tabela wiadomości z obsługą prywatnych wiadomości (jeśli nie istnieje lub została zrzucona)
            if (!messagesTableExists) {
                stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "sender_id INT NOT NULL, " +
                        "receiver_id INT, " +  // Może być NULL dla publicznych wiadomości
                        "content TEXT NOT NULL, " +
                        "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "FOREIGN KEY (sender_id) REFERENCES users(id), " +
                        "FOREIGN KEY (receiver_id) REFERENCES users(id)" +
                        ")");
                System.out.println("Utworzono/odtworzono tabelę MESSAGES z kolumną RECEIVER_ID");
            }

            stmt.close();

            // Sprawdź tabelę wiadomości
            checkMessagesTable();

            // Dodaj testowego użytkownika, jeśli żaden nie istnieje
            PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM users");
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                // Brak użytkowników, dodaj testowych
                PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO users (username, password) VALUES (?, ?)");

                // Dodaj admin/admin
                insertStmt.setString(1, "admin");
                insertStmt.setString(2, "admin");
                insertStmt.executeUpdate();

                // Dodaj user/user
                insertStmt.setString(1, "user");
                insertStmt.setString(2, "user");
                insertStmt.executeUpdate();

                insertStmt.close();
                System.out.println("Dodano testowych użytkowników: admin/admin, user/user");
            }

            rs.close();
            checkStmt.close();

        } catch (SQLException e) {
            System.err.println("Błąd podczas inicjalizacji bazy danych: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Metoda sprawdzająca tabelę messages
    private void checkMessagesTable() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            System.out.println("Sprawdzanie struktury tabeli MESSAGES:");
            ResultSet columns = metaData.getColumns(null, null, "MESSAGES", null);

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                String dataType = columns.getString("TYPE_NAME");
                System.out.println("  - Kolumna: " + columnName + " (" + dataType + ")");
            }

            columns.close();
        } catch (SQLException e) {
            System.err.println("Błąd podczas sprawdzania tabeli messages: " + e.getMessage());
        }
    }

    // Metody dla użytkowników

    public User registerUser(String username, String password) {
        try {
            // Sprawdzenie czy użytkownik już istnieje
            PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT id FROM users WHERE username = ?");
            checkStmt.setString(1, username);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Użytkownik istnieje
                rs.close();
                checkStmt.close();
                return null;
            }

            // Dodanie użytkownika
            PreparedStatement insertStmt = connection.prepareStatement(
                    "INSERT INTO users (username, password) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, username);
            insertStmt.setString(2, password); // W praktyce trzeba zahashować

            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                insertStmt.close();
                return null;
            }

            // Pobranie ID
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int userId = -1;
            if (generatedKeys.next()) {
                userId = generatedKeys.getInt(1);
            }

            generatedKeys.close();
            insertStmt.close();

            User user = new User(userId, username);
            user.setPassword(password);
            return user;

        } catch (SQLException e) {
            System.err.println("Błąd podczas rejestracji użytkownika: " + e.getMessage());
            return null;
        }
    }

    public User authenticateUser(String username, String password) {
        try {
            System.out.println("Próba logowania - Użytkownik: '" + username + "', Hasło: '" + password + "'");

            // Najpierw sprawdź, czy użytkownik istnieje
            PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT id, username, password FROM users WHERE username = ?");
            checkStmt.setString(1, username);
            ResultSet checkRs = checkStmt.executeQuery();

            if (checkRs.next()) {
                String storedPassword = checkRs.getString("password");
                System.out.println("Znaleziono użytkownika. Zapisane hasło: '" + storedPassword + "'");

                // Porównaj hasła
                if (password.equals(storedPassword)) {
                    User user = new User(checkRs.getInt("id"), checkRs.getString("username"));
                    System.out.println("Hasła zgodne - autoryzacja udana dla: " + user.getUsername());
                    checkRs.close();
                    checkStmt.close();
                    return user;
                } else {
                    System.out.println("Hasła nie zgadzają się - autoryzacja nieudana");
                }
            } else {
                System.out.println("Nie znaleziono użytkownika o nazwie: " + username);
            }

            checkRs.close();
            checkStmt.close();
            return null;

        } catch (SQLException e) {
            System.err.println("Błąd podczas autoryzacji: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Metody dla wiadomości

    public boolean saveMessage(Message message) {
        try {
            String sql;
            PreparedStatement stmt;

            if (message.isPrivate()) {
                // Dla wiadomości prywatnych
                sql = "INSERT INTO messages (sender_id, receiver_id, content, timestamp) VALUES (?, ?, ?, ?)";
                stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, message.getSender().getId());
                stmt.setInt(2, message.getReceiver().getId());
                stmt.setString(3, message.getContent());
                stmt.setTimestamp(4, Timestamp.valueOf(message.getTimestamp()));
                System.out.println("Zapisywanie prywatnej wiadomości od " + message.getSender().getUsername() +
                        " do " + message.getReceiver().getUsername() + ": " + message.getContent());
            } else {
                // Dla wiadomości publicznych
                sql = "INSERT INTO messages (sender_id, content, timestamp) VALUES (?, ?, ?)";
                stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                stmt.setInt(1, message.getSender().getId());
                stmt.setString(2, message.getContent());
                stmt.setTimestamp(3, Timestamp.valueOf(message.getTimestamp()));
                System.out.println("Zapisywanie publicznej wiadomości od " + message.getSender().getUsername() + ": " + message.getContent());
            }

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                stmt.close();
                System.out.println("Nie udało się zapisać wiadomości - brak zmodyfikowanych wierszy");
                return false;
            }

            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                message.setId(generatedKeys.getInt(1));
                System.out.println("Wiadomość zapisana z ID: " + message.getId());
            }

            generatedKeys.close();
            stmt.close();
            return true;

        } catch (SQLException e) {
            System.err.println("Błąd podczas zapisywania wiadomości: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public List<Message> getPrivateMessages(int user1Id, int user2Id, int limit) {
        List<Message> messages = new ArrayList<>();

        try {
            System.out.println("Pobieranie prywatnych wiadomości między użytkownikami ID: " + user1Id + " i " + user2Id);

            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT m.id, m.content, m.timestamp, " +
                            "sender.id AS sender_id, sender.username AS sender_username, " +
                            "receiver.id AS receiver_id, receiver.username AS receiver_username " +
                            "FROM messages m " +
                            "JOIN users sender ON m.sender_id = sender.id " +
                            "JOIN users receiver ON m.receiver_id = receiver.id " +
                            "WHERE (m.sender_id = ? AND m.receiver_id = ?) OR (m.sender_id = ? AND m.receiver_id = ?) " +
                            "ORDER BY m.timestamp ASC " +
                            "LIMIT ?");

            stmt.setInt(1, user1Id);
            stmt.setInt(2, user2Id);
            stmt.setInt(3, user2Id);
            stmt.setInt(4, user1Id);
            stmt.setInt(5, limit);

            ResultSet rs = stmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                User sender = new User(rs.getInt("sender_id"), rs.getString("sender_username"));
                User receiver = new User(rs.getInt("receiver_id"), rs.getString("receiver_username"));

                Message message = new Message();
                message.setId(rs.getInt("id"));
                message.setSender(sender);
                message.setReceiver(receiver);
                message.setContent(rs.getString("content"));
                message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());

                messages.add(message);
                count++;
            }

            System.out.println("Pobrano łącznie " + count + " prywatnych wiadomości");

            rs.close();
            stmt.close();

        } catch (SQLException e) {
            System.err.println("Błąd podczas pobierania prywatnych wiadomości: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public List<Message> getRecentMessages(int limit) {
        List<Message> messages = new ArrayList<>();

        try {
            System.out.println("Pobieranie ostatnich " + limit + " publicznych wiadomości");

            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT m.id, m.content, m.timestamp, " +
                            "u.id AS user_id, u.username " +
                            "FROM messages m " +
                            "JOIN users u ON m.sender_id = u.id " +
                            "WHERE m.receiver_id IS NULL " +  // Tylko publiczne wiadomości
                            "ORDER BY m.timestamp DESC " +
                            "LIMIT ?");

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                User sender = new User(rs.getInt("user_id"), rs.getString("username"));

                Message message = new Message();
                message.setId(rs.getInt("id"));
                message.setSender(sender);
                message.setContent(rs.getString("content"));
                message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());

                messages.add(0, message); // Dodaj na początek listy, żeby zachować chronologię
            }

            rs.close();
            stmt.close();

            System.out.println("Pobrano łącznie " + messages.size() + " wiadomości");

        } catch (SQLException e) {
            System.err.println("Błąd podczas pobierania wiadomości: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();

        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, username FROM users");

            while (rs.next()) {
                User user = new User(rs.getInt("id"), rs.getString("username"));
                users.add(user);
            }

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Błąd podczas pobierania użytkowników: " + e.getMessage());
        }

        return users;
    }

    public List<Object[]> getAllMessagesWithUsernames() {
        List<Object[]> messages = new ArrayList<>();

        try {
            PreparedStatement stmt = connection.prepareStatement(
                    "SELECT m.id, sender.username AS sender_username, " +
                            "receiver.username AS receiver_username, m.content, m.timestamp " +
                            "FROM MESSAGES m " +
                            "JOIN USERS sender ON m.sender_id = sender.id " +
                            "LEFT JOIN USERS receiver ON m.receiver_id = receiver.id " +
                            "ORDER BY m.timestamp");

            ResultSet rs = stmt.executeQuery();

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

            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Błąd podczas pobierania wiadomości: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    public void showDatabaseContentUI() {
        JFrame dataFrame = new JFrame("Zawartość bazy danych");
        dataFrame.setSize(800, 500);
        dataFrame.setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        // Panel użytkowników
        JPanel usersPanel = new JPanel(new BorderLayout());
        String[] userColumns = {"ID", "Nazwa użytkownika", "Hasło"};
        DefaultTableModel userModel = new DefaultTableModel(userColumns, 0);
        JTable userTable = new JTable(userModel);
        JScrollPane userScrollPane = new JScrollPane(userTable);
        usersPanel.add(userScrollPane, BorderLayout.CENTER);

        // Panel wiadomości
        JPanel messagesPanel = new JPanel(new BorderLayout());
        String[] msgColumns = {"ID", "Nadawca", "Typ", "Treść", "Czas"};
        DefaultTableModel msgModel = new DefaultTableModel(msgColumns, 0);
        JTable msgTable = new JTable(msgModel);
        JScrollPane msgScrollPane = new JScrollPane(msgTable);
        messagesPanel.add(msgScrollPane, BorderLayout.CENTER);

        tabbedPane.addTab("Użytkownicy", usersPanel);
        tabbedPane.addTab("Wiadomości", messagesPanel);

        dataFrame.add(tabbedPane);
        dataFrame.setVisible(true);

        // Wczytanie danych
        try {
            // Pobierz użytkowników z hasłami (normalnie to powinno być zabezpieczone)
            Statement userStmt = connection.createStatement();
            ResultSet userRs = userStmt.executeQuery("SELECT id, username, password FROM users");

            while (userRs.next()) {
                Object[] row = {
                        userRs.getInt("id"),
                        userRs.getString("username"),
                        userRs.getString("password")
                };
                userModel.addRow(row);
            }

            userRs.close();
            userStmt.close();

            // Pobierz wszystkie wiadomości
            List<Object[]> messages = getAllMessagesWithUsernames();
            for (Object[] message : messages) {
                msgModel.addRow(message);
            }

        } catch (SQLException e) {
            System.err.println("Błąd podczas pobierania danych do UI: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(dataFrame,
                    "Wystąpił błąd podczas ładowania danych: " + e.getMessage(),
                    "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void checkDatabase() {
        try {
            DatabaseMetaData metaData = connection.getMetaData();

            // Sprawdź tabele
            System.out.println("---- Tabele w bazie danych ----");
            ResultSet tables = metaData.getTables(null, null, null, new String[] {"TABLE"});
            boolean hasTables = false;

            while (tables.next()) {
                hasTables = true;
                String tableName = tables.getString("TABLE_NAME");
                System.out.println("Tabela: " + tableName);

                // Sprawdź kolumny tabeli
                ResultSet columns = metaData.getColumns(null, null, tableName, null);
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String dataType = columns.getString("TYPE_NAME");
                    System.out.println("  - Kolumna: " + columnName + " (" + dataType + ")");
                }
                columns.close();
            }

            if (!hasTables) {
                System.out.println("Brak tabel w bazie danych!");
            }
            System.out.println("----------------------------");

            tables.close();
        } catch (SQLException e) {
            System.err.println("Błąd podczas sprawdzania struktury bazy danych: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Błąd podczas zamykania połączenia: " + e.getMessage());
        }
    }
}