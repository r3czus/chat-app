package com.chatapp.server;

import com.chatapp.model.Message;
import com.chatapp.model.User;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private User user;
    private boolean running;

    private static final int MESSAGE_HISTORY_LIMIT = 200;

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
            System.err.println("Błąd podczas inicjalizacji strumieni: " + e.getMessage());
            close();
        }
    }

    @Override
    public void run() {
        try {
            // Oczekiwanie na autoryzację lub rejestrację
            Object obj = input.readObject();

            if (obj instanceof Message && ((Message) obj).getContent().equals("REGISTER")) {
                // Obsługa rejestracji
                Message registerMsg = (Message) obj;
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
                return;
            } else if (obj instanceof User) {
                // Standardowa obsługa logowania
                handleAuthentication((User) obj);

                // Jeśli autoryzacja przebiegła pomyślnie, wysyłamy historię wiadomości
                if (user != null) {
                    // Wyślij historię wiadomości
                    sendMessageHistory();

                    // Wyślij także listę użytkowników
                    sendUserList(server.getUsernameList(this));

                    // Główna pętla obsługująca wiadomości od klienta
                    while (running) {
                        obj = input.readObject();

                        if (obj instanceof Message) {
                            Message message = (Message) obj;
                            String content = message.getContent();

                            // Sprawdź special case'y
                            if (content != null) {
                                // Specjalne wiadomości do odświeżenia listy użytkowników
                                if (content.equals("__GET_USERLIST__")) {
                                    System.out.println("Otrzymano żądanie listy użytkowników od " + user.getUsername());
                                    sendUserList(server.getUsernameList(this));
                                    continue;
                                }

                                // Żądanie historii prywatnych wiadomości
                                if (content.startsWith("GET_PRIVATE_HISTORY:")) {
                                    String otherUsername = content.substring("GET_PRIVATE_HISTORY:".length());
                                    handlePrivateHistoryRequest(otherUsername);
                                    continue;
                                }
                            }

                            // Standardowa obsługa wiadomości
                            if (message.isPrivate()) {
                                // Prywatna wiadomość
                                handlePrivateMessage(message);
                            } else {
                                // Publiczna wiadomość
                                // Wyślij kopię wiadomości z powrotem tylko do nadawcy
                                sendMessage(message);

                                // Wyślij wiadomość do wszystkich innych przez broadcastMessage
                                server.broadcastMessage(message);
                            }
                        }
                    }
                }
            } else {
                // Nieoczekiwany obiekt
                output.writeObject(null);
                output.flush();
                running = false;
            }

        } catch (IOException | ClassNotFoundException e) {
            // Klient się rozłączył lub wystąpił błąd
            System.out.println("Klient rozłączony: " + e.getMessage());
        } finally {
            close();
        }
    }

    // Metoda obsługująca proces autoryzacji
    private void handleAuthentication(User credentials) throws IOException {
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

            System.out.println("Użytkownik zalogowany: " + user.getUsername());

            // Wyślij zaktualizowaną listę użytkowników wszystkim klientom
            server.sendUserListToAll();
        } else {
            // Autoryzacja nie powiodła się
            output.writeObject(null);
            output.flush();
            running = false;
            System.out.println("Nieudana próba logowania: " + credentials.getUsername());
        }
    }

    public void sendUserList(List<String> usernames) {
        try {
            // Utwórz specjalną wiadomość z listą użytkowników
            Message userListMessage = new Message();
            userListMessage.setContent("USER_LIST:" + String.join(",", usernames));
            userListMessage.setSender(null); // Wiadomość systemowa

            System.out.println("Wysyłanie listy użytkowników do " + user.getUsername() + ": " + usernames);

            output.writeObject(userListMessage);
            output.flush();
        } catch (IOException e) {
            System.err.println("Błąd podczas wysyłania listy użytkowników: " + e.getMessage());
        }
    }

    // Wysyłanie historii wiadomości
    private void sendMessageHistory() {
        try {
            // Pobierz zwiększoną ilość wiadomości (200 zamiast 100)
            List<Message> recentMessages = server.getDbManager().getRecentMessages(MESSAGE_HISTORY_LIMIT);

            System.out.println("Wysyłanie historii " + recentMessages.size() + " wiadomości do użytkownika: " + user.getUsername());

            for (Message message : recentMessages) {
                output.writeObject(message);
                output.flush();
                // Dodaj małe opóźnienie, aby uniknąć problemów z przesyłaniem dużej ilości danych
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Oznaczenie końca historii
            Message endMessage = new Message(null, "--- KONIEC HISTORII ---");
            output.writeObject(endMessage);
            output.flush();

        } catch (IOException e) {
            System.err.println("Błąd podczas wysyłania historii: " + e.getMessage());
        }
    }

    // Obsługa prywatnych wiadomości
    private void handlePrivateMessage(Message message) {
        try {
            System.out.println("Obsługa prywatnej wiadomości od " + message.getSender().getUsername() +
                    " do " + message.getReceiver().getUsername() + ": " + message.getContent());

            // Najpierw znajdźmy pełne dane odbiorcy z bazy danych
            User receiver = null;
            List<User> users = server.getDbManager().getAllUsers();
            for (User u : users) {
                if (u.getUsername().equals(message.getReceiver().getUsername())) {
                    receiver = u;
                    break;
                }
            }

            if (receiver != null) {
                // Zaktualizuj obiekt wiadomości z pełnym obiektem odbiorcy
                message.setReceiver(receiver);

                // Zapisz wiadomość w bazie danych
                boolean saved = server.getDbManager().saveMessage(message);

                if (saved) {
                    System.out.println("Prywatna wiadomość zapisana w bazie danych");

                    // Znajdź ClientHandler odbiorcy i wyślij mu wiadomość
                    boolean delivered = false;
                    for (ClientHandler client : server.getClients()) {
                        if (client.getUser() != null && client.getUser().getUsername().equals(receiver.getUsername())) {
                            client.sendMessage(message);
                            System.out.println("Prywatna wiadomość dostarczona do " + receiver.getUsername());
                            delivered = true;
                            break;
                        }
                    }

                    // Zawsze wyślij kopię wiadomości z powrotem do nadawcy
                    sendMessage(message);
                    System.out.println("Kopia prywatnej wiadomości wysłana do nadawcy: " + message.getSender().getUsername());
                } else {
                    System.out.println("Nie udało się zapisać prywatnej wiadomości w bazie danych");
                }
            } else {
                System.out.println("Nie znaleziono użytkownika: " + message.getReceiver().getUsername());
                // Nawet jeśli nie znaleźliśmy odbiorcy, wyślij kopię wiadomości do nadawcy
                sendMessage(message);
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas obsługi prywatnej wiadomości: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Obsługa żądania historii prywatnych wiadomości
    private void handlePrivateHistoryRequest(String otherUsername) {
        try {
            // Znajdź ID drugiego użytkownika
            User otherUser = null;
            List<User> users = server.getDbManager().getAllUsers();
            for (User u : users) {
                if (u.getUsername().equals(otherUsername)) {
                    otherUser = u;
                    break;
                }
            }

            if (otherUser == null) {
                return; // Po prostu przerwij, nie wysyłaj komunikatu o błędzie
            }

            // Pobierz historię prywatnych wiadomości
            List<Message> privateMessages = server.getDbManager().getPrivateMessages(
                    user.getId(), otherUser.getId(), 100);

            // Wyślij historię wiadomości
            for (Message message : privateMessages) {
                sendMessage(message);
                // Małe opóźnienie dla stabilności
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Oznaczenie końca historii prywatnych wiadomości
            Message endMessage = new Message(null, "--- KONIEC HISTORII ---");
            output.writeObject(endMessage);
            output.flush();

        } catch (Exception e) {
            System.err.println("Błąd podczas obsługi żądania historii prywatnych wiadomości: " + e.getMessage());
        }
    }

    // Wysyłanie wiadomości do klienta
    public void sendMessage(Message message) {
        try {
            output.writeObject(message);
            output.flush();
        } catch (IOException e) {
            System.err.println("Błąd podczas wysyłania wiadomości: " + e.getMessage());
            close();
        }
    }

    public User getUser() {
        return user;
    }

    // Zamknięcie połączenia
    public void close() {
        try {
            running = false;

            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();

            server.removeClient(this);

        } catch (IOException e) {
            System.err.println("Błąd podczas zamykania połączenia: " + e.getMessage());
        }
    }
}