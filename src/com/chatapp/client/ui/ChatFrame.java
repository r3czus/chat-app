package com.chatapp.client.ui;

import com.chatapp.client.network.ChatClient;
import com.chatapp.common.config.Config;
import com.chatapp.common.model.Message;
import com.chatapp.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChatFrame extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton logoutButton;
    private JButton returnToPublicButton;
    private JLabel statusLabel;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // Przechowywanie historii wiadomości
    private final List<Message> publicMessageHistory = new ArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ChatClient client;
    private String currentChatPartner = null;
    private boolean isLoggingOut = false;

    public ChatFrame(ChatClient client) {
        this.client = client;

        // Konfiguracja okna
        setupWindow();

        // Utworzenie komponentów
        createComponents();

        // Konfiguracja obsługi zdarzeń
        setupEventHandlers();

        // Natychmiastowe pobranie listy użytkowników
        refreshUserList();
    }

    private void setupWindow() {
        setTitle("Chat App - " + client.getUser().getUsername());
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Obsługa zamykania okna
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.disconnect();
            }
        });
    }

    private void createComponents() {
        // Panel główny
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel statusu z przyciskiem wylogowania
        JPanel statusPanel = new JPanel(new BorderLayout());
        JPanel statusLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Połączono");
        statusLeftPanel.add(statusLabel);

        JPanel statusRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logoutButton = new JButton("Wyloguj");
        returnToPublicButton = new JButton("Powrót do czatu ogólnego");
        statusRightPanel.add(returnToPublicButton);
        statusRightPanel.add(logoutButton);

        statusPanel.add(statusLeftPanel, BorderLayout.WEST);
        statusPanel.add(statusRightPanel, BorderLayout.EAST);

        // Panel czatu z listą użytkowników
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.7);

        // Panel czatu
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Panel wiadomości
        JPanel messagePanel = new JPanel(new BorderLayout(5, 0));
        messageField = new JTextField();
        sendButton = new JButton("Wyślij");
        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);
        chatPanel.add(messagePanel, BorderLayout.SOUTH);

        // Panel listy użytkowników
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setBorder(BorderFactory.createTitledBorder("Użytkownicy"));

        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userScrollPane = new JScrollPane(userList);
        usersPanel.add(userScrollPane, BorderLayout.CENTER);

        // Dodanie etykiety na górze listy użytkowników
        JLabel usersLabel = new JLabel("Kliknij dwukrotnie, aby rozpocząć prywatną rozmowę");
        usersLabel.setHorizontalAlignment(SwingConstants.CENTER);
        usersPanel.add(usersLabel, BorderLayout.NORTH);

        // Dodaj przycisk odświeżania listy użytkowników
        JButton refreshButton = new JButton("Odśwież listę");
        usersPanel.add(refreshButton, BorderLayout.SOUTH);

        // Konfiguracja Split Pane
        splitPane.setLeftComponent(chatPanel);
        splitPane.setRightComponent(usersPanel);

        // Dodanie paneli do głównego panelu
        mainPanel.add(statusPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Dodanie panelu głównego do okna
        setContentPane(mainPanel);

        // Konfiguracja przycisków
        refreshButton.addActionListener(e -> refreshUserList());
        returnToPublicButton.addActionListener(e -> returnToPublicChat());
    }

    private void setupEventHandlers() {
        // Obsługa przycisku wysyłania
        sendButton.addActionListener(this::handleSendMessage);

        // Obsługa klawisza Enter w polu wiadomości
        messageField.addActionListener(this::handleSendMessage);

        // Obsługa przycisku wylogowania
        logoutButton.addActionListener(this::handleLogout);

        // Obsługa dwukliku na liście użytkowników
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (selectedUser != null) {
                        handleUserSelection(selectedUser);
                    }
                }
            }
        });

        // Nasłuchiwanie nowych wiadomości
        client.setOnMessageReceived(this::handleMessageReceived);

        // Nasłuchiwanie zmian statusu połączenia
        client.setOnConnectionStatusChanged(this::handleConnectionStatusChanged);

        // Nasłuchiwanie aktualizacji listy użytkowników
        client.setOnUserListUpdated(this::updateUserList);
    }

    private void refreshUserList() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Aktualizowanie listy użytkowników...");
            client.refreshUserList();
        });
    }

    private void returnToPublicChat() {
        currentChatPartner = null;
        setTitle("Chat App - " + client.getUser().getUsername());

        chatArea.setText("");
        chatArea.append("Powrócono do czatu ogólnego\n\n");

        // Wyświetl wszystkie zapisane publiczne wiadomości
        displayPublicMessageHistory();
    }

    private void handleSendMessage(ActionEvent e) {
        String content = messageField.getText().trim();

        if (!content.isEmpty()) {
            boolean sent;

            if (currentChatPartner == null) {
                sent = client.sendMessage(content);
            } else {
                sent = client.sendPrivateMessage(content, currentChatPartner);
            }

            if (sent) {
                messageField.setText("");
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Nie można wysłać wiadomości.",
                        "Błąd",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void handleLogout(ActionEvent e) {
        isLoggingOut = true;
        client.logout();
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void handleUserSelection(String username) {
        if (username.equals(client.getUser().getUsername())) {
            return;
        }

        currentChatPartner = username;
        setTitle("Chat App - " + client.getUser().getUsername() + " (Rozmowa z: " + username + ")");

        chatArea.setText("");
        chatArea.append("Rozpoczęto prywatną konwersację z użytkownikiem: " + username + "\n");

        client.requestPrivateMessageHistory(username);
    }

    private void handleConnectionStatusChanged(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                statusLabel.setText("Połączono");
                messageField.setEnabled(true);
                sendButton.setEnabled(true);
                logoutButton.setEnabled(true);
            } else {
                statusLabel.setText("Rozłączono");
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                logoutButton.setEnabled(false);

                if (!isLoggingOut) {
                    showConnectionLostDialog();
                }
            }
        });
    }

    private void showConnectionLostDialog() {
        JOptionPane.showMessageDialog(
                this,
                "Utracono połączenie z serwerem.",
                "Rozłączono",
                JOptionPane.ERROR_MESSAGE
        );

        dispose();
        new LoginFrame().setVisible(true);
    }

    private void updateUserList(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Połączono");
            userListModel.clear();

            if (users != null && !users.isEmpty()) {
                users.forEach(userListModel::addElement);
                Logger.debug("Zaktualizowano listę użytkowników: " + users.size() + " użytkowników");
            } else {
                Logger.warn("Otrzymano pustą listę użytkowników!");
            }
        });
    }

    private void handleMessageReceived(Message message) {
        // Sprawdź czy to wiadomość publiczna i zapisz ją w historii
        if (message.getSender() != null && !message.isPrivate()) {
            publicMessageHistory.add(message);
        }

        // Wyświetl wiadomość w UI
        displayMessage(message);
    }

    private void displayPublicMessageHistory() {
        if (publicMessageHistory.isEmpty()) {
            chatArea.append("Nie znaleziono historii wiadomości dla czatu głównego.\n");
            return;
        }

        chatArea.append("--- Historia wiadomości ---\n");

        for (Message message : publicMessageHistory) {
            if (message.getSender() != null) {
                String time = message.getTimestamp().format(timeFormatter);
                String sender = message.getSender().getUsername();
                String content = message.getContent();

                chatArea.append(String.format("[%s] %s: %s\n", time, sender, content));
            }
        }

        chatArea.append("--- Koniec historii ---\n\n");

        // Przewiń na dół
        scrollToBottom();
    }

    private void displayMessage(Message message) {
        SwingUtilities.invokeLater(() -> {
            // Obsługa wiadomości systemowych
            if (message.isSystemMessage()) {
                // Ignorujemy wiadomości końca historii

                return;
            }

            // Sprawdź, czy wiadomość ma być wyświetlona w bieżącym widoku czatu
            if (shouldDisplayMessageInCurrentView(message)) {
                formatAndDisplayMessage(message);
            }
        });
    }

    private boolean shouldDisplayMessageInCurrentView(Message message) {
        boolean isPrivateMessage = message.isPrivate();
        String senderUsername = message.getSender().getUsername();
        String receiverUsername = isPrivateMessage && message.getReceiver() != null ?
                message.getReceiver().getUsername() : null;

        if (currentChatPartner == null) {
            // Jesteśmy w czacie grupowym - pokazuj tylko wiadomości publiczne
            return !isPrivateMessage;
        } else {
            // Jesteśmy w czacie prywatnym - pokazuj tylko wiadomości z/do bieżącego partnera
            return isPrivateMessage &&
                    (senderUsername.equals(currentChatPartner) ||
                            (receiverUsername != null && receiverUsername.equals(currentChatPartner)));
        }
    }

    private void formatAndDisplayMessage(Message message) {
        String time = message.getTimestamp().format(timeFormatter);
        String sender = message.getSender().getUsername();
        String content = message.getContent();

        chatArea.append(String.format("[%s] %s: %s\n", time, sender, content));

        // Przewijanie do nowej wiadomości
        scrollToBottom();
    }

    private void scrollToBottom() {
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}