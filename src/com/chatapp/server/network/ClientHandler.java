package com.chatapp.server.network;

import com.chatapp.common.config.Config;
import com.chatapp.common.model.Message;
import com.chatapp.common.model.User;
import com.chatapp.util.Logger;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable, AutoCloseable {
    private final Socket socket;
    private final ChatServer server;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private User user;
    private boolean running;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.running = true;

        try {
            // Inicjalizacja strumieni we/wy
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            // Dodaj klienta do serwera
            server.addClient(this);
        } catch (IOException e) {
            Logger.error("Błąd podczas inicjalizacji strumieni: " + e.getMessage());
            close();
        }
    }

    @Override
    public void run() {
        try {
            // Autoryzacja/rejestracja
            Object obj = input.readObject();

            if (isRegistrationRequest(obj)) {
                handleRegistration((Message) obj);
                return;
            } else if (obj instanceof User) {
                // Logowanie
                if (!handleAuthentication((User) obj)) {
                    return;
                }

                // Wysyłanie historii i listy użytkowników
                sendMessageHistory();
                sendUserList(server.getUsernameList(this));

                // Główna pętla obsługi wiadomości
                processMessages();
            } else {
                // Nieznany typ obiektu
                sendAuthenticationFailure();
            }
        } catch (IOException | ClassNotFoundException e) {
            Logger.error("Klient rozłączony: " + e.getMessage());
        } finally {
            close();
        }
    }

    private boolean isRegistrationRequest(Object obj) {
        return obj instanceof Message &&
                ((Message) obj).getContent() != null &&
                ((Message) obj).getContent().equals(Config.CMD_REGISTER);
    }

    private void handleRegistration(Message registerMsg) throws IOException {
        User credentials = registerMsg.getSender();

        // Próba rejestracji
        User registeredUser = server.getDbManager().registerUser(
                credentials.getUsername(),
                credentials.getPassword()
        );

        // Wyślij odpowiedź
        output.writeObject(registeredUser);
        output.flush();

        // Zamknij połączenie po rejestracji
        close();
    }

    private boolean handleAuthentication(User credentials) throws IOException {
        // Próba autoryzacji
        User authenticatedUser = server.getDbManager().authenticateUser(
                credentials.getUsername(),
                credentials.getPassword()
        );

        if (authenticatedUser != null) {
            // Autoryzacja powiodła się
            this.user = authenticatedUser;

            // Wyślij potwierdzenie do klienta
            output.writeObject(authenticatedUser);
            output.flush();

            Logger.info("Użytkownik zalogowany: " + user.getUsername());

            // Wyślij zaktualizowaną listę użytkowników
            server.sendUserListToAll();
            return true;
        } else {
            // Autoryzacja nie powiodła się
            sendAuthenticationFailure();
            return false;
        }
    }

    private void sendAuthenticationFailure() throws IOException {
        output.writeObject(null);
        output.flush();
        running = false;
    }

    private void processMessages() throws IOException, ClassNotFoundException {
        while (running) {
            Object obj = input.readObject();

            if (obj instanceof Message) {
                Message message = (Message) obj;
                String content = message.getContent();

                if (content != null) {
                    if (content.equals(Config.CMD_GET_USER_LIST)) {
                        handleUserListRequest();
                    } else if (content.startsWith(Config.CMD_GET_PRIVATE_HISTORY)) {
                        handlePrivateHistoryRequest(content);
                    } else if (message.isPrivate()) {
                        handlePrivateMessage(message);
                    } else {
                        // Standardowa wiadomość publiczna
                        sendMessage(message);  // Wyślij kopię do nadawcy
                        server.broadcastMessage(message);  // Broadcast do innych
                    }
                }
            }
        }
    }

    private void handleUserListRequest() {
        Logger.debug("Otrzymano żądanie listy użytkowników od " + user.getUsername());
        sendUserList(server.getUsernameList(this));
    }

    private void handlePrivateHistoryRequest(String content) {
        String otherUsername = content.substring(Config.CMD_GET_PRIVATE_HISTORY.length());

        try {
            // Znajdź ID drugiego użytkownika
            User otherUser = findUserByUsername(otherUsername);

            if (otherUser == null) {
                return;
            }

            // Pobierz historię prywatnych wiadomości
            List<Message> privateMessages = server.getDbManager().getPrivateMessages(
                    user.getId(), otherUser.getId(), 100);

            // Wyślij historię wiadomości
            for (Message message : privateMessages) {
                sendMessage(message);
                Thread.sleep(10);  // Małe opóźnienie dla stabilności
            }

            // Oznaczenie końca historii

        } catch (Exception e) {
            Logger.error("Błąd podczas obsługi żądania historii prywatnych wiadomości: " + e.getMessage());
        }
    }

    private void handlePrivateMessage(Message message) {
        try {
            Logger.debug("Obsługa prywatnej wiadomości od " + message.getSender().getUsername() +
                    " do " + message.getReceiver().getUsername());

            // Znajdź pełne dane odbiorcy
            User receiver = findUserByUsername(message.getReceiver().getUsername());

            if (receiver != null) {
                // Zaktualizuj obiekt wiadomości z pełnym obiektem odbiorcy
                message.setReceiver(receiver);

                // Zapisz wiadomość w bazie danych
                boolean saved = server.getDbManager().saveMessage(message);

                if (saved) {
                    // Znajdź ClientHandler odbiorcy i wyślij mu wiadomość
                    deliverPrivateMessage(message, receiver);

                    // Wyślij kopię wiadomości z powrotem do nadawcy
                    sendMessage(message);
                }
            } else {
                Logger.warn("Nie znaleziono użytkownika: " + message.getReceiver().getUsername());
                // Nawet jeśli nie znaleźliśmy odbiorcy, wyślij kopię wiadomości do nadawcy
                sendMessage(message);
            }
        } catch (Exception e) {
            Logger.error("Błąd podczas obsługi prywatnej wiadomości: " + e.getMessage());
        }
    }

    private User findUserByUsername(String username) {
        List<User> users = server.getDbManager().getAllUsers();

        for (User u : users) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }

        return null;
    }

    private void deliverPrivateMessage(Message message, User receiver) {
        boolean delivered = false;

        for (ClientHandler client : server.getClients()) {
            if (client.getUser() != null && client.getUser().getUsername().equals(receiver.getUsername())) {
                client.sendMessage(message);
                Logger.debug("Prywatna wiadomość dostarczona do " + receiver.getUsername());
                delivered = true;
                break;
            }
        }

        if (!delivered) {
            Logger.debug("Odbiorca offline - wiadomość zapisana tylko w bazie danych");
        }
    }

    private void sendMessageHistory() {
        try {
            // Pobierz wiadomości
            List<Message> recentMessages = server.getDbManager().getRecentMessages(Config.MESSAGE_HISTORY_LIMIT);

            Logger.debug("Wysyłanie historii " + recentMessages.size() + " wiadomości do użytkownika: " + user.getUsername());

            for (Message message : recentMessages) {
                output.writeObject(message);
                output.flush();
                Thread.sleep(10);  // Małe opóźnienie dla stabilności
            }

            // Oznaczenie końca historii

        } catch (Exception e) {
            Logger.error("Błąd podczas wysyłania historii: " + e.getMessage());
        }
    }

    private void sendSystemMessage(String content) throws IOException {
        Message message = new Message(null, content);
        output.writeObject(message);
        output.flush();
    }

    public void sendUserList(List<String> usernames) {
        try {
            // Utwórz specjalną wiadomość systemową z listą użytkowników
            String content = Config.USER_LIST_PREFIX + String.join(",", usernames);
            Message userListMessage = new Message(null, content);

            output.writeObject(userListMessage);
            output.flush();
        } catch (IOException e) {
            Logger.error("Błąd podczas wysyłania listy użytkowników: " + e.getMessage());
        }
    }

    public void sendMessage(Message message) {
        try {
            output.writeObject(message);
            output.flush();
        } catch (IOException e) {
            Logger.error("Błąd podczas wysyłania wiadomości: " + e.getMessage());
            close();
        }
    }

    public User getUser() {
        return user;
    }

    @Override
    public void close() {
        try {
            running = false;

            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();

            server.removeClient(this);
        } catch (IOException e) {
            Logger.error("Błąd podczas zamykania połączenia: " + e.getMessage());
        }
    }
}