package com.chatapp.client.ui;

import com.chatapp.client.ChatClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JLabel statusLabel;

    private ChatClient client;

    public LoginFrame() {
        // Inicjalizacja klienta
        client = new ChatClient();

        // Konfiguracja okna
        setTitle("Chat App - Logowanie");
        setSize(300, 220); // Zwiększamy wysokość aby zmieścić przycisk rejestracji
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Utworzenie komponentów
        createComponents();

        // Nasłuchiwanie statusu połączenia
        client.setOnConnectionStatusChanged(connected -> {
            SwingUtilities.invokeLater(() -> {
                if (connected) {
                    statusLabel.setText("Połączono z serwerem");
                    loginButton.setEnabled(true);
                } else {
                    statusLabel.setText("Rozłączono z serwerem");
                    loginButton.setEnabled(false);
                }
            });
        });

        // Próba połączenia z serwerem
        new Thread(() -> {
            statusLabel.setText("Łączenie z serwerem...");
            loginButton.setEnabled(false);

            if (client.connect()) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Połączono z serwerem");
                    loginButton.setEnabled(true);
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Nie można połączyć się z serwerem");
                    loginButton.setEnabled(false);
                    JOptionPane.showMessageDialog(
                            this,
                            "Nie można połączyć się z serwerem. Sprawdź czy serwer jest uruchomiony.",
                            "Błąd połączenia",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }).start();
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
        JButton registerButton = new JButton("Rejestracja"); // Nowy przycisk

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

        // Dodanie akcji do przycisków
        loginButton.addActionListener(this::handleLogin);

        registerButton.addActionListener(e -> {
            setVisible(false);
            new RegisterFrame(client).setVisible(true);
            dispose();
        });

        // Dodanie akcji do pola hasła (logowanie po naciśnięciu Enter)
        passwordField.addActionListener(this::handleLogin);
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
                        JOptionPane.showMessageDialog(
                                this,
                                "Nie można połączyć się z serwerem. Sprawdź czy serwer jest uruchomiony.",
                                "Błąd połączenia",
                                JOptionPane.ERROR_MESSAGE
                        );
                    });
                    return;
                }
            }

            // Próba logowania
            boolean success = client.authenticate(username, password);

            SwingUtilities.invokeLater(() -> {
                if (success) {
                    // Ukrycie okna logowania
                    setVisible(false);

                    // Otwarcie okna czatu
                    ChatFrame chatFrame = new ChatFrame(client);
                    chatFrame.setVisible(true);

                    // Zamknięcie okna logowania
                    dispose();
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

    // Metoda main do uruchomienia aplikacji
    public static void main(String[] args) {
        // Uruchomienie interfejsu w wątku EDT (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            try {
                // Ustawienie wyglądu systemu operacyjnego
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}