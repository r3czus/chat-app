package com.chatapp;

import com.chatapp.client.ui.LoginFrame;
import com.chatapp.server.ChatServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Sprawdź argumenty wiersza poleceń
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            switch (mode) {
                case "server":
                    SwingUtilities.invokeLater(() -> startServer());
                    break;
                case "client":
                    SwingUtilities.invokeLater(() -> startClient());
                    break;
                default:
                    System.out.println("Nieznany tryb. Użyj: server lub client");
                    showModeSelectionDialog();
                    break;
            }
        } else {
            // Bez argumentów - pokaż okno wyboru trybu
            showModeSelectionDialog();
        }
    }
    private static void showModeSelectionDialog() {
        // Uruchomienie interfejsu w wątku EDT (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            try {
                // Ustawienie wyglądu systemu operacyjnego
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

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
        serverFrame.setSize(400, 300);
        serverFrame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        JButton stopButton = new JButton("Zatrzymaj Serwer");

        panel.add(new JLabel("Logi serwera:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(stopButton, BorderLayout.SOUTH);

        serverFrame.getContentPane().add(panel);
        serverFrame.setVisible(true);


        // Przekierowanie wyjścia standardowego do obszaru tekstowego
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.valueOf((char) b));
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
        }));

        // Przekierowanie wyjścia błędów do obszaru tekstowego
        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(String.valueOf((char) b));
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
        }));

        // Uruchomienie serwera w osobnym wątku
        ChatServer server = new ChatServer();
        Thread serverThread = new Thread(server::start);
        serverThread.setDaemon(true);
        serverThread.start();

        // Obsługa przycisku zatrzymania
        stopButton.addActionListener((ActionEvent e) -> {
            server.stop();
            serverFrame.dispose();
            System.exit(0);
        });
            JButton viewDataButton = new JButton("Pokaż dane");
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(stopButton);
            buttonPanel.add(viewDataButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);  // Zamiast panel.add(stopButton, BorderLayout.SOUTH);

    // Obsługa przycisku
            viewDataButton.addActionListener((ActionEvent e) -> {
                server.getDbManager().showDatabaseContentUI();
            });
    }


    private static void startClient() {
        // Uruchomienie okna logowania klienta
        LoginFrame loginFrame = new LoginFrame();
        loginFrame.setVisible(true);
    }
}