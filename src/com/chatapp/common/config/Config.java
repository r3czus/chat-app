package com.chatapp.common.config;

public class Config {
    // Ustawienia serwera
    public static final int SERVER_PORT = 8888;
    public static final int MAX_CLIENTS = 50;

    // Ustawienia bazy danych
    public static final String DB_URL = "jdbc:h2:./chatdb;AUTO_SERVER=TRUE";
    public static final String DB_USER = "sa";
    public static final String DB_PASSWORD = "";

    // Ustawienia połączenia klienta
    public static final String SERVER_ADDRESS = "localhost";

    // Ustawienia wiadomości
    public static final int MESSAGE_HISTORY_LIMIT = 200;

    // Specjalne komendy
    public static final String CMD_GET_USER_LIST = "__GET_USERLIST__";
    public static final String CMD_GET_PRIVATE_HISTORY = "GET_PRIVATE_HISTORY:";
    public static final String CMD_REGISTER = "REGISTER";

    public static final String USER_LIST_PREFIX = "USER_LIST:";
}