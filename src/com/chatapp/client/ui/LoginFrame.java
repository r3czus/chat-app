package com.chatapp.client.ui;

import com.chatapp.client.network.ChatClient;
import com.chatapp.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;

    private ChatClient client;

    public LoginFrame() {
        // Inicjalizacja klienta
        client = new ChatClient();

        // Konfiguracja okna
        setupWindow();

        // Utworzenie komponentów
        createComponents();

        // Konfiguracja obsługi zdarzeń
        setupEventHandlers();

        // Próba połączenia z serwerem
        connectToServer();
    }

    private void setupWindow() {
        setTitle("Chat App - Logowanie");
        setSize(300, 220);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void createComponents() {
        // Panel główny
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel formularza
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        formPanel.add(new JLabel("Nazwa użytkownika:"));
        usernameField = new JTextField();
        formPanel.add(usernameField);

        formPanel.add(new JLabel("Hasło:"));
        passwordField = new JPasswordField();
        formPanel.add(passwordField);

        // Panel przycisków
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        loginButton = new JButton("Zaloguj");
        registerButton = new JButton("Rejestracja");
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        // Panel statusu
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Gotowy");
        statusPanel.add(statusLabel);

        // Dodanie paneli do głównego panelu
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Dodanie panelu głównego do okna
        setContentPane(mainPanel);
    }

    private void setupEventHandlers() {
        // Obsługa przycisku logowania
        loginButton.addActionListener(this::handleLogin);

        // Obsługa przycisku rejestracji
        registerButton.addActionListener(e -> {
            setVisible(false);
            new RegisterFrame(client).setVisible(true);
            dispose();
        });

        // Logowanie po naciśnięciu Enter w polu hasła
        passwordField.addActionListener(this::handleLogin);

        // Nasłuchiwanie statusu połączenia
        client.setOnConnectionStatusChanged(this::updateConnectionStatus);
    }

    private void updateConnectionStatus(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            if (connected) {
                statusLabel.setText("Połączono z serwerem");
                loginButton.setEnabled(true);
            } else {
                statusLabel.setText("Rozłączono z serwerem");
                loginButton.setEnabled(false);
            }
        });
    }

    private void connectToServer() {
        new Thread(() -> {
            statusLabel.setText("Łączenie z serwerem...");
            loginButton.setEnabled(false);

            if (!client.connect()) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Nie można połączyć się z serwerem");
                    showConnectionError();
                });
            }
        }).start();
    }

    private void showConnectionError() {
        JOptionPane.showMessageDialog(
                this,
                "Nie można połączyć się z serwerem. Sprawdź czy serwer jest uruchomiony.",
                "Błąd połączenia",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private void handleLogin(ActionEvent e) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Podaj nazwę użytkownika", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Wyłączenie przycisku podczas autoryzacji
        loginButton.setEnabled(false);
        statusLabel.setText("Autoryzacja...");

        // Autoryzacja w osobnym wątku
        new Thread(() -> {
            // Sprawdź czy jest połączenie, jeśli nie - połącz
            if (!client.isConnected()) {
                statusLabel.setText("Łączenie z serwerem...");
                if (!client.connect()) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Nie można połączyć się z serwerem");
                        loginButton.setEnabled(true);
                        showConnectionError();
                    });
                    return;
                }
            }

            // Próba logowania
            boolean success = client.authenticate(username, password);

            SwingUtilities.invokeLater(() -> {
                if (success) {
                    openChatWindow();
                } else {
                    statusLabel.setText("Błąd autoryzacji");
                    loginButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            "Nie można zalogować. Sprawdź dane logowania.",
                            "Błąd autoryzacji",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        }).start();
    }

    private void openChatWindow() {
        // Ukrycie okna logowania
        setVisible(false);

        // Otwarcie okna czatu
        ChatFrame chatFrame = new ChatFrame(client);
        chatFrame.setVisible(true);

        // Zamknięcie okna logowania
        dispose();
    }

    // Metoda pomocnicza do ustawiania wyglądu
    public static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Logger.error("Błąd podczas ustawiania wyglądu: " + e.getMessage());
        }
    }
    public static void main(String[] args) {
        // Ustawienie poziomu logowania
        Logger.setMinLevel(Logger.LogLevel.INFO);

        // Uruchomienie interfejsu w wątku EDT
        SwingUtilities.invokeLater(() -> {
            setSystemLookAndFeel();
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}