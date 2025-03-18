package com.chatapp.server.storage;

import com.chatapp.util.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class DatabaseUI {

    private final DatabaseManager dbManager;

    public DatabaseUI(DatabaseManager dbManager) {
        this.dbManager = dbManager;
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
            loadUsers(userModel);
            loadMessages(msgModel);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(dataFrame,
                    "Wystąpił błąd podczas ładowania danych: " + e.getMessage(),
                    "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadUsers(DefaultTableModel model) {
        List<Object[]> users = dbManager.getAllUsersForDisplay();
        for (Object[] user : users) {
            model.addRow(user);
        }
    }

    private void loadMessages(DefaultTableModel model) {
        List<Object[]> messages = dbManager.getAllMessagesWithUsernames();
        for (Object[] message : messages) {
            model.addRow(message);
        }
    }
}