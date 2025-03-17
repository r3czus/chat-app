package com.chatapp.client;

import com.chatapp.model.Message;
import com.chatapp.model.User;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 8888;

    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private User user;
    private Thread receiverThread;
    private boolean connected;
    private boolean intentionalDisconnect = false;
    private Consumer<List<String>> onUserListUpdated;

    // Callback na otrzymanie wiadomości
    private Consumer<Message> onMessageReceived;

    // Callback na zmianę stanu połączenia
    private Consumer<Boolean> onConnectionStatusChanged;

    public ChatClient() {
        connected = false;
    }

    // Metoda do łączenia z serwerem
    public boolean connect() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);

            // Inicjalizacja strumieni
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            // Oznaczenie jako połączony
            connected = true;

            // Powiadomienie o zmianie statusu
            if (onConnectionStatusChanged != null) {
                onConnectionStatusChanged.accept(true);
            }

            return true;
        } catch (IOException e) {
            System.err.println("Błąd podczas łączenia z serwerem: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    // Metoda do autoryzacji użytkownika
    public boolean authenticate(String username, String password) {
        if (!connected || output == null || input == null) {
            System.out.println("Próba autoryzacji bez połączenia");
            return false;
        }

        try {
            System.out.println("ChatClient: Wysyłanie danych logowania - Użytkownik: '" + username + "', Hasło: '" + password + "'");

            // Utworzenie obiektu użytkownika z danymi do logowania
            User credentials = new User();
            credentials.setUsername(username);
            credentials.setPassword(password);

            // Wysłanie danych logowania
            output.writeObject(credentials);
            output.flush();
            System.out.println("ChatClient: Dane logowania wysłane, oczekiwanie na odpowiedź");

            // Oczekiwanie na odpowiedź serwera
            Object response = input.readObject();

            if (response instanceof User) {
                // Autoryzacja powiodła się
                user = (User) response;
                System.out.println("ChatClient: Autoryzacja udana dla użytkownika: " + user.getUsername());

                // Rozpoczęcie wątku odbierającego wiadomości
                startReceiver();

                return true;
            } else {
                // Autoryzacja nie powiodła się
                System.out.println("ChatClient: Autoryzacja nieudana, odpowiedź: " + (response == null ? "null" : response.toString()));
                disconnect();
                return false;
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Błąd podczas autoryzacji: " + e.getMessage());
            e.printStackTrace();
            disconnect();
            return false;
        }
    }

    // Wysłanie pustej wiadomości, aby wymusić aktualizację listy użytkowników
    public void refreshUserList() {
        if (!connected || user == null) {
            return;
        }

        try {
            // Wysyłanie pustej wiadomości do serwera, aby zainicjować odpowiedź
            Message dummyMessage = new Message();
            dummyMessage.setSender(user);
            dummyMessage.setContent("__GET_USERLIST__");
            dummyMessage.setTimestamp(LocalDateTime.now());

            output.writeObject(dummyMessage);
            output.flush();
            System.out.println("Wysłano żądanie o zaktualizowanie listy użytkowników");
        } catch (IOException e) {
            System.err.println("Błąd podczas wysyłania żądania listy użytkowników: " + e.getMessage());
        }
    }

    // Metoda do rejestracji użytkownika
    public boolean register(String username, String password) {
        if (!connected || output == null || input == null) {
            return false;
        }

        try {
            // Utworzenie obiektu użytkownika z danymi do rejestracji
            User credentials = new User();
            credentials.setUsername(username);
            credentials.setPassword(password);

            // Dodanie flagi oznaczającej rejestrację
            Message registerMsg = new Message(null, "REGISTER");
            registerMsg.setSender(credentials);

            // Wysłanie danych rejestracji
            output.writeObject(registerMsg);
            output.flush();

            // Oczekiwanie na odpowiedź serwera
            Object response = input.readObject();

            if (response instanceof User) {
                // Rejestracja powiodła się
                return true;
            } else {
                // Rejestracja nie powiodła się
                return false;
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Błąd podczas rejestracji: " + e.getMessage());
            return false;
        }
    }

    // Metoda do rozpoczęcia wątku odbierającego wiadomości
    private void startReceiver() {
        receiverThread = new Thread(() -> {
            try {
                while (connected) {
                    Object obj = input.readObject();

                    if (obj instanceof Message) {
                        Message message = (Message) obj;

                        // Sprawdź czy to wiadomość z listą użytkowników
                        if (message.getSender() == null && message.getContent() != null && message.getContent().startsWith("USER_LIST:")) {
                            // Przetwórz listę użytkowników
                            String content = message.getContent().substring(10); // Usuń "USER_LIST:"
                            String[] usernamesArray = content.split(",");
                            List<String> usernames = new ArrayList<>(Arrays.asList(usernamesArray));

                            System.out.println("Otrzymano aktualizację listy użytkowników: " + usernames);

                            // Powiadomienie o nowej liście użytkowników
                            if (onUserListUpdated != null) {
                                onUserListUpdated.accept(usernames);
                            }
                        } else if (message.getContent() != null && message.getContent().equals("__GET_USERLIST__")) {
                            // Ignoruj specjalne wiadomości do odświeżania listy
                            continue;
                        } else {
                            // Standardowa wiadomość
                            if (onMessageReceived != null) {
                                onMessageReceived.accept(message);
                            }
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    System.err.println("Rozłączono z serwerem: " + e.getMessage());
                    disconnect();
                }
            }
        });

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public boolean sendPrivateMessage(String content, String receiverUsername) {
        if (!connected || user == null) {
            return false;
        }

        try {
            // Znajdź odbiorcę po nazwie użytkownika
            User receiver = new User();
            receiver.setUsername(receiverUsername);

            // Utwórz wiadomość prywatną
            Message message = new Message(user, receiver, content);

            // Wyślij wiadomość
            output.writeObject(message);
            output.flush();
            return true;
        } catch (IOException e) {
            System.err.println("Błąd podczas wysyłania prywatnej wiadomości: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public void requestPrivateMessageHistory(String otherUsername) {
        if (!connected || user == null) {
            return;
        }

        try {
            // Wyślij specjalną wiadomość systemową z żądaniem historii prywatnych wiadomości
            Message request = new Message();
            request.setContent("GET_PRIVATE_HISTORY:" + otherUsername);
            request.setSender(user);

            output.writeObject(request);
            output.flush();
        } catch (IOException e) {
            System.err.println("Błąd podczas żądania historii prywatnych wiadomości: " + e.getMessage());
        }
    }

    // Metoda do wysyłania wiadomości
    public boolean sendMessage(String content) {
        if (!connected || user == null) {
            return false;
        }

        try {
            Message message = new Message(user, content);
            output.writeObject(message);
            output.flush();
            return true;
        } catch (IOException e) {
            System.err.println("Błąd podczas wysyłania wiadomości: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    // Metoda do rozłączania
    public void disconnect() {
        connected = false;

        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Błąd podczas rozłączania: " + e.getMessage());
        }

        // Powiadomienie o zmianie statusu
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(false);
        }
    }

    public void logout() {
        intentionalDisconnect = true;
        disconnect();
    }

    // Gettery i settery

    public User getUser() {
        return user;
    }

    public boolean wasIntentionalDisconnect() {
        return intentionalDisconnect;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setOnMessageReceived(Consumer<Message> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;
    }

    public void setOnConnectionStatusChanged(Consumer<Boolean> onConnectionStatusChanged) {
        this.onConnectionStatusChanged = onConnectionStatusChanged;
    }

    public void setOnUserListUpdated(Consumer<List<String>> onUserListUpdated) {
        this.onUserListUpdated = onUserListUpdated;
    }
}