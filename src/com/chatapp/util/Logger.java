package com.chatapp.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static LogLevel minLevel = LogLevel.INFO;

    public enum LogLevel {
        DEBUG(0), INFO(1), WARN(2), ERROR(3);

        private final int value;

        LogLevel(int value) {
            this.value = value;
        }

        public boolean isEnabled(LogLevel minLevel) {
            return this.value >= minLevel.value;
        }
    }

    public static void setMinLevel(LogLevel level) {
        minLevel = level;
    }

    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    public static void warn(String message) {
        log(LogLevel.WARN, message);
    }

    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    private static void log(LogLevel level, String message) {
        if (!level.isEnabled(minLevel)) {
            return;
        }

        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String threadName = Thread.currentThread().getName();
        String output = String.format("[%s] [%s] [%s] %s", timestamp, threadName, level, message);

        if (level == LogLevel.ERROR) {
            System.err.println(output);
        } else {
            System.out.println(output);
        }
    }
}