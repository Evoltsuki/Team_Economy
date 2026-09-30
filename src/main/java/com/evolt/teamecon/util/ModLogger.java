package com.evolt.teamecon.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModLogger {
    public static final Logger LOGGER = LoggerFactory.getLogger("TeamEconomy");

    private ModLogger() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }
}
