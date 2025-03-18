package com.chatapp.server.network;

import com.chatapp.common.config.Config;
import com.chatapp.common.model.Message;
import com.chatapp.common.model.User;
import com.chatapp.server.storage.DatabaseManager;
import com.chatapp.util.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer implements AutoCloseable {
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private List<ClientHandler> clients;
    private DatabaseManager dbManager;
    private volatile boolean running;

    public ChatServer() {
        clients = new CopyOnWriteArrayList<>();
        threadPool = Executors.newFixedThreadPool(Config.MAX_CLIENTS);
        dbManager = new DatabaseManager();
        running = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(Config.SERVER_PORT);
            running = true;

            Logger.info("Serwer uruchomiony na porcie " + Config.SERVER_PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    Logger.info("Nowe połączenie: " + clientSocket.getInetAddress().getHostAddress());

                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    threadPool.execute(clientHandler);
                } catch (IOException e) {
                    if (!running) break;
                    Logger.error("Błąd podczas akceptowania połączenia: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Logger.error("Błąd podczas uruchamiania serwera: " + e.getMessage());
        } finally {
            close();
        }
    }

    public void broadcastMessage(Message message) {
        if (message == null || message.getSender() == null) return;

        // Zapisanie wiadomości w bazie danych
        dbManager.saveMessage(message);

        // Wysłanie wiadomości do wszystkich klientów oprócz nadawcy
        int senderId = message.getSender().getId();

        for (ClientHandler client : clients) {
            if (client.getUser() != null && client.getUser().getId() != senderId) {
                client.sendMessage(message);
            }
        }
    }

    public List<String> getUsernameList(ClientHandler excludeClient) {
        List<String> usernames = new ArrayList<>();

        // Dodaj wszystkich aktualnie zalogowanych użytkowników
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client != excludeClient) {
                usernames.add(client.getUser().getUsername());
            }
        }

        // Dodaj także użytkowników z bazy danych
        for (User user : dbManager.getAllUsers()) {
            if (excludeClient == null ||
                    (excludeClient.getUser() != null && !user.getUsername().equals(excludeClient.getUser().getUsername()))) {
                if (!usernames.contains(user.getUsername())) {
                    usernames.add(user.getUsername());
                }
            }
        }

        return usernames;
    }

    public void sendUserListToAll() {
        try {
            Logger.debug("Wysyłanie listy użytkowników do wszystkich klientów");

            for (ClientHandler client : clients) {
                if (client.getUser() != null) {
                    // Filtruj listę dla każdego klienta, aby nie widział siebie
                    List<String> filteredUsernames = getUsernameList(client);
                    client.sendUserList(filteredUsernames);
                }
            }
        } catch (Exception e) {
            Logger.error("Błąd podczas wysyłania listy użytkowników: " + e.getMessage());
        }
    }

    public void addClient(ClientHandler client) {
        clients.add(client);
        Logger.info("Nowy klient połączony. Aktywnych klientów: " + clients.size());
        sendUserListToAll();
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        Logger.info("Klient rozłączony. Aktywnych klientów: " + clients.size());
        sendUserListToAll();
    }

    @Override
    public void close() {
        running = false;

        // Zamknięcie wszystkich połączeń
        for (ClientHandler client : clients) {
            client.close();
        }
        clients.clear();

        // Zamknięcie puli wątków
        threadPool.shutdown();

        // Zamknięcie gniazda serwera
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Logger.error("Błąd podczas zamykania serwera: " + e.getMessage());
        }

        // Zamknięcie połączenia z bazą danych
        if (dbManager != null) {
            dbManager.close();
        }

        Logger.info("Serwer zatrzymany");
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public List<ClientHandler> getClients() {
        return new ArrayList<>(clients);
    }
    public static void main(String[] args) {
        Logger.setMinLevel(Logger.LogLevel.INFO);
        System.out.println("Uruchamianie serwera czatu...");
        ChatServer server = new ChatServer();
        server.start();
    }
}