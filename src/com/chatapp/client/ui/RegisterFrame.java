package com.chatapp.client.ui;

import com.chatapp.client.network.ChatClient;
import com.chatapp.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class RegisterFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JButton registerButton;
    private JButton backButton;
    private JLabel statusLabel;

    private ChatClient client;

    public RegisterFrame(ChatClient client) {
        this.client = client;

        // Konfiguracja okna
        setupWindow();

        // Utworzenie komponentów
        createComponents();

        // Konfiguracja obsługi zdarzeń
        setupEventHandlers();
    }

    private void setupWindow() {
        setTitle("Chat App - Rejestracja");
        setSize(350, 250);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void createComponents() {
        // Panel główny
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Panel formularza
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        formPanel.add(new JLabel("Nazwa użytkownika:"));
        usernameField = new JTextField();
        formPanel.add(usernameField);

        formPanel.add(new JLabel("Hasło:"));
        passwordField = new JPasswordField();
        formPanel.add(passwordField);

        formPanel.add(new JLabel("Potwierdź hasło:"));
        confirmPasswordField = new JPasswordField();
        formPanel.add(confirmPasswordField);

        // Panel przycisków
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        registerButton = new JButton("Zarejestruj");
        backButton = new JButton("Powrót");
        buttonPanel.add(registerButton);
        buttonPanel.add(backButton);

        // Panel statusu
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Wprowadź dane do rejestracji");
        statusPanel.add(statusLabel);

        // Dodanie paneli do głównego panelu
        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Dodanie panelu głównego do okna
        setContentPane(mainPanel);
    }

    private void setupEventHandlers() {
        registerButton.addActionListener(this::handleRegister);

        backButton.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });
    }

    private void handleRegister(ActionEvent e) {
        if (!validateInput()) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Wyłączenie przycisku podczas rejestracji
        registerButton.setEnabled(false);
        statusLabel.setText("Rejestracja...");

        // Rejestracja w osobnym wątku
        new Thread(() -> {
            // Połączenie z serwerem jeśli nie połączono
            if (!ensureConnected()) {
                return;
            }

            // Rejestracja użytkownika
            boolean success = client.register(username, password);

            SwingUtilities.invokeLater(() -> {
                if (success) {
                    showRegistrationSuccess();
                } else {
                    showRegistrationFailure();
                }
            });
        }).start();
    }

    private boolean validateInput() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Podaj nazwę użytkownika", "Błąd", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Podaj hasło", "Błąd", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, "Hasła nie są zgodne", "Błąd", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean ensureConnected() {
        if (!client.isConnected()) {
            if (!client.connect()) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Nie można połączyć z serwerem");
                    registerButton.setEnabled(true);
                    JOptionPane.showMessageDialog(
                            this,
                            "Nie można połączyć się z serwerem. Sprawdź czy serwer jest uruchomiony.",
                            "Błąd połączenia",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
                return false;
            }
        }
        return true;
    }

    private void showRegistrationSuccess() {
        JOptionPane.showMessageDialog(
                this,
                "Rejestracja zakończona pomyślnie. Możesz się teraz zalogować.",
                "Rejestracja udana",
                JOptionPane.INFORMATION_MESSAGE
        );
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void showRegistrationFailure() {
        statusLabel.setText("Błąd rejestracji");
        registerButton.setEnabled(true);
        JOptionPane.showMessageDialog(
                this,
                "Nie można zarejestrować użytkownika. Nazwa użytkownika może być już zajęta.",
                "Błąd rejestracji",
                JOptionPane.ERROR_MESSAGE
        );
    }
}