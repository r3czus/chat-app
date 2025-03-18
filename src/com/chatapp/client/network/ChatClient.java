

package com.chatapp.client.network;

import com.chatapp.common.config.Config;
import com.chatapp.common.model.Message;
import com.chatapp.common.model.User;
import com.chatapp.util.Logger;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ChatClient implements AutoCloseable {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private User user;
    private MessageReceiver messageReceiver;
    private boolean connected;
    private boolean intentionalDisconnect = false;

    // Callbacki
    private Consumer<List<String>> onUserListUpdated;
    private Consumer<Message> onMessageReceived;
    private Consumer<Boolean> onConnectionStatusChanged;

    public ChatClient() {
        this.connected = false;
    }

    public boolean connect() {
        try {
            socket = new Socket(Config.SERVER_ADDRESS, Config.SERVER_PORT);

            // Inicjalizacja strumieni
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            connected = true;
            notifyConnectionStatusChanged(true);

            return true;
        } catch (IOException e) {
            Logger.error("Błąd podczas łączenia z serwerem: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public boolean authenticate(String username, String password) {
        if (!isConnected()) {
            Logger.warn("Próba autoryzacji bez połączenia");
            return false;
        }

        try {
            Logger.debug("Wysyłanie danych logowania: " + username);

            User credentials = new User();
            credentials.setUsername(username);
            credentials.setPassword(password);

            output.writeObject(credentials);
            output.flush();

            Object response = input.readObject();

            if (response instanceof User) {
                user = (User) response;
                Logger.info("Autoryzacja udana dla: " + user.getUsername());

                startMessageReceiver();
                return true;
            } else {
                Logger.warn("Autoryzacja nieudana");
                disconnect();
                return false;
            }
        } catch (IOException | ClassNotFoundException e) {
            Logger.error("Błąd podczas autoryzacji: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public boolean register(String username, String password) {
        if (!isConnected()) {
            return false;
        }

        try {
            User credentials = new User();
            credentials.setUsername(username);
            credentials.setPassword(password);

            Message registerMsg = new Message();
            registerMsg.setSender(credentials);
            registerMsg.setContent(Config.CMD_REGISTER);

            output.writeObject(registerMsg);
            output.flush();

            Object response = input.readObject();

            return response instanceof User;
        } catch (IOException | ClassNotFoundException e) {
            Logger.error("Błąd podczas rejestracji: " + e.getMessage());
            return false;
        }
    }

    public void refreshUserList() {
        if (!isConnected() || user == null) {
            return;
        }

        try {
            Message message = new Message();
            message.setSender(user);
            message.setContent(Config.CMD_GET_USER_LIST);
            message.setTimestamp(LocalDateTime.now());

            output.writeObject(message);
            output.flush();
            Logger.debug("Wysłano żądanie aktualizacji listy użytkowników");
        } catch (IOException e) {
            Logger.error("Błąd podczas wysyłania żądania listy użytkowników: " + e.getMessage());
        }
    }

    public boolean sendPrivateMessage(String content, String receiverUsername) {
        if (!isConnected() || user == null) {
            return false;
        }

        try {
            User receiver = new User();
            receiver.setUsername(receiverUsername);

            Message message = new Message(user, receiver, content);

            output.writeObject(message);
            output.flush();
            return true;
        } catch (IOException e) {
            Logger.error("Błąd podczas wysyłania prywatnej wiadomości: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public void requestPrivateMessageHistory(String otherUsername) {
        if (!isConnected() || user == null) {
            return;
        }

        try {
            Message request = new Message();
            request.setContent(Config.CMD_GET_PRIVATE_HISTORY + otherUsername);
            request.setSender(user);

            output.writeObject(request);
            output.flush();
        } catch (IOException e) {
            Logger.error("Błąd podczas żądania historii prywatnych wiadomości: " + e.getMessage());
        }
    }

    public boolean sendMessage(String content) {
        if (!isConnected() || user == null) {
            return false;
        }

        try {
            Message message = new Message(user, content);
            output.writeObject(message);
            output.flush();
            return true;
        } catch (IOException e) {
            Logger.error("Błąd podczas wysyłania wiadomości: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    private void startMessageReceiver() {
        messageReceiver = new MessageReceiver(this, input);
        messageReceiver.start();
    }

    public void disconnect() {
        connected = false;

        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            Logger.error("Błąd podczas rozłączania: " + e.getMessage());
        }

        notifyConnectionStatusChanged(false);
    }

    public void logout() {
        intentionalDisconnect = true;
        disconnect();
    }

    void handleReceivedMessage(Message message) {
        if (onMessageReceived != null) {
            onMessageReceived.accept(message);
        }
    }

    void handleUserList(List<String> usernames) {
        if (onUserListUpdated != null) {
            onUserListUpdated.accept(usernames);
        }
    }

    private void notifyConnectionStatusChanged(boolean status) {
        if (onConnectionStatusChanged != null) {
            onConnectionStatusChanged.accept(status);
        }
    }

    @Override
    public void close() {
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