package com.chatapp.client.ui;

import com.chatapp.client.ChatClient;
import com.chatapp.model.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
    private JLabel statusLabel;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // Przechowujemy historyczne wiadomości
    private List<Message> publicMessageHistory = new ArrayList<>();

    private ChatClient client;
    private DateTimeFormatter timeFormatter;
    private String currentChatPartner = null;
    private boolean isLoggingOut = false;

    public ChatFrame(ChatClient client) {
        this.client = client;
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Konfiguracja okna
        setTitle("Chat App - " + client.getUser().getUsername());
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Utworzenie komponentów
        createComponents();

        // Nasłuchiwanie nowych wiadomości
        client.setOnMessageReceived(this::handleMessageReceived);

        // Nasłuchiwanie zmian statusu połączenia
        client.setOnConnectionStatusChanged(connected -> {
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
                        JOptionPane.showMessageDialog(
                                this,
                                "Utracono połączenie z serwerem.",
                                "Rozłączono",
                                JOptionPane.ERROR_MESSAGE
                        );

                        dispose();
                        LoginFrame loginFrame = new LoginFrame();
                        loginFrame.setVisible(true);
                    }
                }
            });
        });

        // Obsługa zamykania okna
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.disconnect();
            }
        });

        // Nasłuchiwanie aktualizacji listy użytkowników
        client.setOnUserListUpdated(this::updateUserList);

        // Wymuś natychmiastowe pobranie listy użytkowników zaraz po otwarciu okna
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Pobieranie listy użytkowników...");
            client.refreshUserList();
        });
    }

    private void createComponents() {
        // Panel główny
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel statusu z przyciskiem wylogowania
        JPanel statusPanel = new JPanel(new BorderLayout());
        JPanel statusLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Łączenie...");
        statusLeftPanel.add(statusLabel);

        JPanel statusRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logoutButton = new JButton("Wyloguj");
        statusRightPanel.add(logoutButton);

        JButton returnToPublicButton = new JButton("Powrót do czatu ogólnego");
        statusRightPanel.add(returnToPublicButton, 0);

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
        refreshButton.addActionListener(e -> {
            statusLabel.setText("Aktualizowanie listy użytkowników...");
            client.refreshUserList();
        });
        usersPanel.add(refreshButton, BorderLayout.SOUTH);

        // Konfiguracja Split Pane
        splitPane.setLeftComponent(chatPanel);
        splitPane.setRightComponent(usersPanel);

        // Dodanie paneli do głównego panelu
        mainPanel.add(statusPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Dodanie panelu głównego do okna
        setContentPane(mainPanel);

        // Dodanie akcji do przycisku wysyłania
        sendButton.addActionListener(this::handleSendMessage);

        // Dodanie akcji do pola wiadomości (wysyłanie po naciśnięciu Enter)
        messageField.addActionListener(this::handleSendMessage);

        // Dodanie akcji do przycisku wylogowania
        logoutButton.addActionListener(this::handleLogout);

        // Dodanie akcji do listy użytkowników (podwójne kliknięcie)
        userList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (selectedUser != null) {
                        handleUserSelection(selectedUser);
                    }
                }
            }
        });

        // Obsługa przycisku powrotu do czatu ogólnego
        returnToPublicButton.addActionListener(e -> {
            currentChatPartner = null;
            setTitle("Chat App - " + client.getUser().getUsername());
            chatArea.setText("");
            chatArea.append("Powrócono do czatu ogólnego\n\n");

            // Wyświetl wszystkie zapisane publiczne wiadomości
            displayPublicMessageHistory();
        });
    }

    // Wyświetla zapisaną historię wiadomości publicznych
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
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
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
        LoginFrame loginFrame = new LoginFrame();
        loginFrame.setVisible(true);
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

    public void updateUserList(java.util.List<String> users) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Połączono");
            userListModel.clear();
            if (users != null && !users.isEmpty()) {
                for (String user : users) {
                    userListModel.addElement(user);
                }
                System.out.println("Zaktualizowano listę użytkowników: " + users.size() + " użytkowników");
            } else {
                System.out.println("Otrzymano pustą listę użytkowników!");
            }
        });
    }

    // Nowa metoda obsługi wiadomości - rozdziela obsługę i wyświetlanie
    private void handleMessageReceived(Message message) {
        // Sprawdź czy to wiadomość publiczna i zapisz ją w historii
        if (message.getSender() != null && !message.isPrivate()) {
            // Dodaj do historii publicznych wiadomości
            publicMessageHistory.add(message);
        }

        // Wyświetl wiadomość w UI
        displayMessage(message);
    }

    private void displayMessage(Message message) {
        SwingUtilities.invokeLater(() -> {
            // Obsługa wiadomości systemowych
            if (message.getSender() == null) {
                // Ignorujemy wiadomości końca historii
                if (!message.getContent().contains("KONIEC HISTORII")) {
                    chatArea.append(message.getContent() + "\n");
                }
                return;
            }

            // Sprawdź, czy wiadomość ma być wyświetlona w bieżącym widoku czatu
            boolean isPrivateMessage = message.isPrivate();
            String senderUsername = message.getSender().getUsername();
            String receiverUsername = isPrivateMessage && message.getReceiver() != null ?
                    message.getReceiver().getUsername() : null;

            boolean showInCurrentChat = false;

            if (currentChatPartner == null) {
                // Jesteśmy w czacie grupowym - pokazuj tylko wiadomości publiczne
                showInCurrentChat = !isPrivateMessage;
            } else {
                // Jesteśmy w czacie prywatnym - pokazuj tylko wiadomości z/do bieżącego partnera
                showInCurrentChat = isPrivateMessage &&
                        (senderUsername.equals(currentChatPartner) ||
                                (receiverUsername != null && receiverUsername.equals(currentChatPartner)));
            }

            if (showInCurrentChat) {
                // Formatowanie i wyświetlanie wiadomości
                String time = message.getTimestamp().format(timeFormatter);
                String sender = message.getSender().getUsername();
                String content = message.getContent();

                chatArea.append(String.format("[%s] %s: %s\n", time, sender, content));

                // Przewijanie do nowej wiadomości
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
        });
    }
}