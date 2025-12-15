package me.bechberger.jfrredact;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging configuration utility.
 * This must be called early in the application lifecycle, before any loggers are used.
 */
public class LoggingConfig {

    private static final String APP_LOGGER_NAME = "me.bechberger.jfrredact";

    /**
     * Configure the root logger level based on verbosity flags.
     * This affects all loggers in the application.
     *
     * @param debug Enable debug logging
     * @param verbose Enable info logging
     * @param quiet Enable warn logging only
     */
    public static void configure(boolean debug, boolean verbose, boolean quiet) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Logger appLogger = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);

        if (debug) {
            rootLogger.setLevel(Level.DEBUG);
            appLogger.setLevel(Level.DEBUG);
        } else if (verbose) {
            rootLogger.setLevel(Level.INFO);
            appLogger.setLevel(Level.INFO);
        } else if (quiet) {
            rootLogger.setLevel(Level.WARN);
            appLogger.setLevel(Level.WARN);
        }
        // else keep default level from logback.xml
    }

    /**
     * Set log level directly.
     *
     * @param level The log level to set
     */
    public static void setLevel(Level level) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(level);
    }
}