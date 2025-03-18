package com.chatapp.client.network;

import com.chatapp.common.config.Config;
import com.chatapp.common.model.Message;
import com.chatapp.util.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageReceiver extends Thread {
    private final ChatClient client;
    private final ObjectInputStream input;
    private volatile boolean running = true;

    public MessageReceiver(ChatClient client, ObjectInputStream input) {
        super("MessageReceiver");
        this.client = client;
        this.input = input;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            while (running && client.isConnected()) {
                Object obj = input.readObject();

                if (obj instanceof Message) {
                    Message message = (Message) obj;

                    if (isUserListMessage(message)) {
                        processUserListMessage(message);
                    } else if (isSpecialMessage(message)) {
                        // Ignoruj specjalne wiadomości
                        continue;
                    } else {
                        client.handleReceivedMessage(message);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (client.isConnected()) {
                Logger.error("Rozłączono z serwerem: " + e.getMessage());
                client.disconnect();
            }
        }
    }

    private boolean isUserListMessage(Message message) {
        return message.getSender() == null && message.getContent() != null &&
                message.getContent().startsWith(Config.USER_LIST_PREFIX);
    }

    private boolean isSpecialMessage(Message message) {
        return message.getContent() != null &&
                message.getContent().equals(Config.CMD_GET_USER_LIST);
    }

    private void processUserListMessage(Message message) {
        try {
            String content = message.getContent().substring(Config.USER_LIST_PREFIX.length());

            if (content.isEmpty()) {
                client.handleUserList(new ArrayList<>());
                return;
            }

            String[] usernamesArray = content.split(",");
            List<String> usernames = new ArrayList<>(Arrays.asList(usernamesArray));

            Logger.debug("Otrzymano aktualizację listy użytkowników: " + usernames);
            client.handleUserList(usernames);
        } catch (Exception e) {
            Logger.error("Błąd podczas przetwarzania listy użytkowników: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        interrupt();
    }
}