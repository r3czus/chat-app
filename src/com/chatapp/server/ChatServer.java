package com.chatapp.server;

import com.chatapp.db.DatabaseManager;
import com.chatapp.model.Message;
import com.chatapp.model.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private static final int PORT = 8888;
    private static final int MAX_CLIENTS = 50;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private List<ClientHandler> clients;
    private DatabaseManager dbManager;
    private boolean running;
    public List<ClientHandler> getClients() {
        return new ArrayList<>(clients);
    }
    public ChatServer() {
        clients = new ArrayList<>();
        threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
        dbManager = new DatabaseManager();
        dbManager.checkDatabase();
        running = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;

            System.out.println("Serwer uruchomiony na porcie " + PORT);

            // Główna pętla akceptująca nowe połączenia
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nowe połączenie: " + clientSocket.getInetAddress().getHostAddress());

                    // Utworzenie obsługi dla klienta
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);

                    // Uruchomienie obsługi klienta w osobnym wątku
                    threadPool.execute(clientHandler);

                } catch (IOException e) {
                    if (!running) break;
                    System.err.println("Błąd podczas akceptowania połączenia: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Błąd podczas uruchamiania serwera: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        running = false;

        // Zamknięcie wszystkich połączeń
        clients.forEach(ClientHandler::close);
        clients.clear();

        // Zamknięcie puli wątków
        threadPool.shutdown();

        // Zamknięcie gniazda serwera
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Błąd podczas zamykania serwera: " + e.getMessage());
        }

        // Zamknięcie połączenia z bazą danych
        if (dbManager != null) {
            dbManager.close();
        }

        System.out.println("Serwer zatrzymany");
    }

    // Metoda wywoływana przez ClientHandler po otrzymaniu wiadomości od klienta
    public void broadcastMessage(Message message) {
        // Zapisanie wiadomości w bazie danych
        dbManager.saveMessage(message);

        // Wysłanie wiadomości do wszystkich klientów OPRÓCZ nadawcy
        int senderId = message.getSender().getId();
        for (ClientHandler client : new ArrayList<>(clients)) {
            // Pomijamy wysyłanie wiadomości do nadawcy
            if (client.getUser() != null && client.getUser().getId() != senderId) {
                client.sendMessage(message);
            }
        }

        // Wyślij także zaktualizowaną listę użytkowników
        sendUserListToAll();
    }

    // Nowa metoda do pobierania listy nazw użytkowników dla konkretnego klienta
    public List<String> getUsernameList(ClientHandler excludeClient) {
        List<String> usernames = new ArrayList<>();

        // Dodaj wszystkich aktualnie zalogowanych użytkowników
        for (ClientHandler client : clients) {
            if (client.getUser() != null && client != excludeClient) {
                usernames.add(client.getUser().getUsername());
            }
        }

        // Dodaj także użytkowników z bazy danych, którzy mogą nie być obecnie zalogowani
        List<User> dbUsers = dbManager.getAllUsers();
        for (User user : dbUsers) {
            if (excludeClient == null || !user.getUsername().equals(excludeClient.getUser().getUsername())) {
                if (!usernames.contains(user.getUsername())) {
                    usernames.add(user.getUsername());
                }
            }
        }

        return usernames;
    }

    public void addClient(ClientHandler client) {
        clients.add(client);
        System.out.println("Nowy klient połączony. Aktywnych klientów: " + clients.size());
        // Wyślij zaktualizowaną listę użytkowników
        sendUserListToAll();
    }

    public void sendUserListToAll() {
        try {
            System.out.println("Wysyłanie listy użytkowników do wszystkich klientów...");

            // Pobierz listę użytkowników z bazy danych
            List<User> dbUsers = dbManager.getAllUsers();
            List<String> usernames = new ArrayList<>();

            // Dodaj wszystkich użytkowników z bazy danych
            for (User user : dbUsers) {
                usernames.add(user.getUsername());
            }

            // Dodaj aktualnie zalogowanych użytkowników, którzy mogą nie być w bazie
            for (ClientHandler client : clients) {
                User user = client.getUser();
                if (user != null && !usernames.contains(user.getUsername())) {
                    usernames.add(user.getUsername());
                }
            }

            System.out.println("Lista użytkowników: " + String.join(", ", usernames));

            // Wyślij wiadomość do wszystkich klientów
            for (ClientHandler client : clients) {
                if (client.getUser() != null) {
                    // Filtruj listę dla każdego klienta, aby nie widział siebie
                    List<String> filteredUsernames = new ArrayList<>(usernames);
                    filteredUsernames.remove(client.getUser().getUsername());

                    client.sendUserList(filteredUsernames);
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas wysyłania listy użytkowników: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // Metoda do usuwania klienta z listy gdy się rozłączy
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Klient rozłączony. Aktywnych klientów: " + clients.size());
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    // Metoda główna do uruchomienia serwera
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }
}