package com.chatapp;

import com.chatapp.client.ui.LoginFrame;
import com.chatapp.server.network.ChatServer;
import com.chatapp.util.Logger;
import com.chatapp.util.Logger.LogLevel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class Main {

    public static void main(String[] args) {
        // Ustawienie poziomu logowania
        Logger.setMinLevel(LogLevel.INFO);

        // Ustawienie wyglądu aplikacji
        setupLookAndFeel();

        // Sprawdź argumenty wiersza poleceń
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            switch (mode) {
                case "server":
                    SwingUtilities.invokeLater(Main::startServer);
                    break;
                case "client":
                    SwingUtilities.invokeLater(Main::startClient);
                    break;
                default:
                    Logger.warn("Nieznany tryb: " + mode);
                    showModeSelectionDialog();
                    break;
            }
        } else {
            // Bez argumentów - pokaż okno wyboru trybu
            showModeSelectionDialog();
        }
    }

    private static void setupLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Logger.error("Błąd podczas ustawiania wyglądu: " + e.getMessage());
        }
    }

    private static void showModeSelectionDialog() {
        // Uruchomienie interfejsu w wątku EDT (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            // Utworzenie okna wyboru trybu
            JFrame startFrame = new JFrame("Chat App - Start");
            startFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            startFrame.setSize(300, 150);
            startFrame.setLocationRelativeTo(null);

            JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            JLabel label = new JLabel("Wybierz tryb uruchomienia:", SwingConstants.CENTER);

            JButton serverButton = new JButton("Uruchom Serwer");
            serverButton.addActionListener((ActionEvent e) -> {
                startFrame.setVisible(false);
                startServer();
                startFrame.dispose();
            });

            JButton clientButton = new JButton("Uruchom Klienta");
            clientButton.addActionListener((ActionEvent e) -> {
                startFrame.setVisible(false);
                startClient();
                startFrame.dispose();
            });

            panel.add(label);
            panel.add(serverButton);
            panel.add(clientButton);

            startFrame.getContentPane().add(panel);
            startFrame.setVisible(true);
        });
    }

    private static void startServer() {
        // Utworzenie okna serwera
        JFrame serverFrame = new JFrame("Chat App - Serwer");
        serverFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        serverFrame.setSize(600, 400);
        serverFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton stopButton = new JButton("Zatrzymaj Serwer");
        JButton viewDataButton = new JButton("Pokaż dane");
        buttonPanel.add(viewDataButton);
        buttonPanel.add(stopButton);

        panel.add(new JLabel("Logi serwera:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        serverFrame.getContentPane().add(panel);
        serverFrame.setVisible(true);

        // Przekierowanie wyjścia standardowego do obszaru tekstowego
        redirectSystemOutput(logArea);

        // Uruchomienie serwera w osobnym wątku
        ChatServer server = new ChatServer();
        Thread serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();

        // Obsługa przycisków
        stopButton.addActionListener((ActionEvent e) -> {
            server.close();
            serverFrame.dispose();
            System.exit(0);
        });

        viewDataButton.addActionListener((ActionEvent e) -> {
            server.getDbManager().showDatabaseContentUI();
        });
    }

    private static void startClient() {
        // Uruchomienie okna logowania klienta
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    private static void redirectSystemOutput(JTextArea textArea) {
        // Przekierowanie wyjścia standardowego
        System.setOut(new PrintStream(new TextAreaOutputStream(textArea, false)));

        // Przekierowanie wyjścia błędów
        System.setErr(new PrintStream(new TextAreaOutputStream(textArea, true)));
    }

    private static class TextAreaOutputStream extends OutputStream {
        private final JTextArea textArea;
        private final StringBuilder buffer = new StringBuilder();
        private final boolean isError;

        public TextAreaOutputStream(JTextArea textArea, boolean isError) {
            this.textArea = textArea;
            this.isError = isError;
        }

        @Override
        public void write(int b) throws IOException {
            char c = (char) b;

            if (c == '\n') {
                final String text = buffer.toString();
                buffer.setLength(0);

                SwingUtilities.invokeLater(() -> {
                    if (isError) {
                        textArea.append("[ERROR] " + text + "\n");
                    } else {
                        textArea.append(text + "\n");
                    }
                    textArea.setCaretPosition(textArea.getDocument().getLength());
                });
            } else {
                buffer.append(c);
            }
        }
    }
}