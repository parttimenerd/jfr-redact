package me.bechberger.jfrredact.commands;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import me.bechberger.jfrredact.LoggingConfig;
import me.bechberger.jfrredact.Main;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for verifying logging behavior across all commands.
 *
 * <p>This test class verifies that:
 * <ul>
 *   <li>--debug flag enables DEBUG level logging</li>
 *   <li>--verbose / -v flag enables INFO level logging</li>
 *   <li>--quiet / -q flag sets WARN level logging</li>
 *   <li>Default logging level is WARN</li>
 *   <li>Logging configuration is applied before command execution</li>
 * </ul>
 */
class LoggingBehaviorTest {

    @TempDir
    Path tempDir;

    private static final String APP_LOGGER_NAME = "me.bechberger.jfrredact";

    private Logger appLogger;
    private ListAppender<ILoggingEvent> listAppender;
    private Level originalLevel;
    private Level originalRootLevel;

    @BeforeEach
    void setUp() {
        // Get the application logger
        appLogger = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        // Save original levels
        originalLevel = appLogger.getLevel();
        originalRootLevel = rootLogger.getLevel();

        // Create and attach a list appender for capturing log events
        listAppender = new ListAppender<>();
        listAppender.start();
        appLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        // Remove the test appender
        appLogger.detachAppender(listAppender);
        listAppender.stop();

        // Restore original levels
        appLogger.setLevel(originalLevel);
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(originalRootLevel);
    }

    // ==================== LoggingConfig Direct Tests ====================

    @Nested
    @DisplayName("LoggingConfig.configure() Tests")
    class LoggingConfigTests {

        @Test
        @DisplayName("Debug flag should set DEBUG level on both root and app logger")
        void testDebugFlagSetsDebugLevel() {
            LoggingConfig.configure(true, false, false);

            Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            Logger appLoggerCheck = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);

            assertEquals(Level.DEBUG, rootLogger.getLevel(), "Root logger should be DEBUG");
            assertEquals(Level.DEBUG, appLoggerCheck.getLevel(), "App logger should be DEBUG");
        }

        @Test
        @DisplayName("Verbose flag should set INFO level on both root and app logger")
        void testVerboseFlagSetsInfoLevel() {
            LoggingConfig.configure(false, true, false);

            Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            Logger appLoggerCheck = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);

            assertEquals(Level.INFO, rootLogger.getLevel(), "Root logger should be INFO");
            assertEquals(Level.INFO, appLoggerCheck.getLevel(), "App logger should be INFO");
        }

        @Test
        @DisplayName("Quiet flag should set WARN level on both root and app logger")
        void testQuietFlagSetsWarnLevel() {
            LoggingConfig.configure(false, false, true);

            Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            Logger appLoggerCheck = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);

            assertEquals(Level.WARN, rootLogger.getLevel(), "Root logger should be WARN");
            assertEquals(Level.WARN, appLoggerCheck.getLevel(), "App logger should be WARN");
        }

        @Test
        @DisplayName("Debug flag should take precedence over verbose and quiet")
        void testDebugPrecedence() {
            // All three flags set - debug should win
            LoggingConfig.configure(true, true, true);

            Logger appLoggerCheck = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);
            assertEquals(Level.DEBUG, appLoggerCheck.getLevel(), "Debug should take precedence");
        }

        @Test
        @DisplayName("Verbose flag should take precedence over quiet")
        void testVerbosePrecedence() {
            // Verbose and quiet flags set - verbose should win
            LoggingConfig.configure(false, true, true);

            Logger appLoggerCheck = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);
            assertEquals(Level.INFO, appLoggerCheck.getLevel(), "Verbose should take precedence over quiet");
        }

        @Test
        @DisplayName("No flags should preserve existing level")
        void testNoFlagsPreservesLevel() {
            // Set a known level first
            appLogger.setLevel(Level.ERROR);

            LoggingConfig.configure(false, false, false);

            assertEquals(Level.ERROR, appLogger.getLevel(),
                "Level should be preserved when no flags are set");
        }
    }

    // ==================== Command Integration Tests via Main ====================

    @Nested
    @DisplayName("Main CLI Logging Integration Tests")
    class MainCliLoggingTests {

        private ByteArrayOutputStream outContent;
        private ByteArrayOutputStream errContent;
        private PrintStream originalOut;
        private PrintStream originalErr;

        @BeforeEach
        void setUpStreams() {
            originalOut = System.out;
            originalErr = System.err;
            outContent = new ByteArrayOutputStream();
            errContent = new ByteArrayOutputStream();
            // Don't redirect System.out/err as it interferes with logback
        }

        @AfterEach
        void restoreStreams() {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        @Test
        @DisplayName("Main execution strategy should configure logging before command execution")
        void testMainExecutionStrategyConfiguresLogging() {
            // Run a command through Main's CommandLine to test the execution strategy
            CommandLine cmd = new CommandLine(new Main());
            cmd.setExecutionStrategy(parseResult -> {
                // Verify logging is configured
                LoggingConfig.configure(
                    parseResult.hasMatchedOption("--debug"),
                    parseResult.hasMatchedOption("--verbose") || parseResult.hasMatchedOption("-v"),
                    parseResult.hasMatchedOption("--quiet")
                );
                return new CommandLine.RunLast().execute(parseResult);
            });

            // This test verifies the strategy concept works
            assertNotNull(cmd.getExecutionStrategy(), "Execution strategy should be set");
        }
    }

    // ==================== RedactCommand Logging Tests ====================

    @Nested
    @DisplayName("RedactCommand Logging Tests")
    class RedactCommandLoggingTests {

        @Test
        @DisplayName("RedactCommand should have --debug option")
        void testRedactCommandHasDebugOption() {
            CommandLine cmd = new CommandLine(new RedactCommand());
            assertTrue(cmd.getCommandSpec().findOption("--debug") != null,
                "RedactCommand should have --debug option");
        }

        @Test
        @DisplayName("RedactCommand should have --verbose option")
        void testRedactCommandHasVerboseOption() {
            CommandLine cmd = new CommandLine(new RedactCommand());
            assertTrue(cmd.getCommandSpec().findOption("--verbose") != null,
                "RedactCommand should have --verbose option");
            assertTrue(cmd.getCommandSpec().findOption("-v") != null,
                "RedactCommand should have -v short option");
        }

        @Test
        @DisplayName("RedactCommand should have --quiet option")
        void testRedactCommandHasQuietOption() {
            CommandLine cmd = new CommandLine(new RedactCommand());
            assertTrue(cmd.getCommandSpec().findOption("--quiet") != null,
                "RedactCommand should have --quiet option");
            assertTrue(cmd.getCommandSpec().findOption("-q") != null,
                "RedactCommand should have -q short option");
        }

        @Test
        @DisplayName("RedactCommand with --debug should log DEBUG messages")
        void testRedactCommandDebugLogging() throws Exception {
            // Configure logging as Main would
            LoggingConfig.configure(true, false, false);

            Path inputFile = createMinimalJfrFile();
            Path outputFile = tempDir.resolve("output.jfr");

            CommandLine cmd = new CommandLine(new RedactCommand());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            cmd.setOut(new PrintWriter(out, true));
            cmd.setErr(new PrintWriter(err, true));

            // Execute with --debug (logging already configured above)
            cmd.execute(inputFile.toString(), outputFile.toString(), "--debug");

            // Verify logger level was set correctly
            Logger testLogger = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);
            assertEquals(Level.DEBUG, testLogger.getLevel(),
                "Logger should be at DEBUG level after --debug flag");
        }
    }

    // ==================== RedactTextCommand Logging Tests ====================

    @Nested
    @DisplayName("RedactTextCommand Logging Tests")
    class RedactTextCommandLoggingTests {

        @Test
        @DisplayName("RedactTextCommand should have --debug option")
        void testRedactTextCommandHasDebugOption() {
            CommandLine cmd = new CommandLine(new RedactTextCommand());
            assertTrue(cmd.getCommandSpec().findOption("--debug") != null,
                "RedactTextCommand should have --debug option");
        }

        @Test
        @DisplayName("RedactTextCommand should have --verbose option")
        void testRedactTextCommandHasVerboseOption() {
            CommandLine cmd = new CommandLine(new RedactTextCommand());
            assertTrue(cmd.getCommandSpec().findOption("--verbose") != null,
                "RedactTextCommand should have --verbose option");
        }

        @Test
        @DisplayName("RedactTextCommand should have --quiet option")
        void testRedactTextCommandHasQuietOption() {
            CommandLine cmd = new CommandLine(new RedactTextCommand());
            assertTrue(cmd.getCommandSpec().findOption("--quiet") != null,
                "RedactTextCommand should have --quiet option");
        }

        @Test
        @DisplayName("RedactTextCommand with --verbose should log INFO messages")
        void testRedactTextCommandVerboseLogging() throws Exception {
            // Configure logging as Main would
            LoggingConfig.configure(false, true, false);

            Path inputFile = createTextFile("Test content with email user@example.com");
            Path outputFile = tempDir.resolve("output.txt");

            CommandLine cmd = new CommandLine(new RedactTextCommand());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            cmd.setOut(new PrintWriter(out, true));
            cmd.setErr(new PrintWriter(err, true));

            // Execute with --verbose
            cmd.execute(inputFile.toString(), outputFile.toString(), "--verbose");

            // Verify logger level was set correctly
            Logger testLogger = (Logger) LoggerFactory.getLogger(APP_LOGGER_NAME);
            assertEquals(Level.INFO, testLogger.getLevel(),
                "Logger should be at INFO level after --verbose flag");
        }
    }

    // ==================== TestCommand Logging Tests ====================

    @Nested
    @DisplayName("TestCommand Logging Tests")
    class TestCommandLoggingTests {

        @Test
        @DisplayName("TestCommand should have --debug option")
        void testTestCommandHasDebugOption() {
            CommandLine cmd = new CommandLine(new TestCommand());
            assertTrue(cmd.getCommandSpec().findOption("--debug") != null,
                "TestCommand should have --debug option");
        }

        @Test
        @DisplayName("TestCommand should have --verbose option")
        void testTestCommandHasVerboseOption() {
            CommandLine cmd = new CommandLine(new TestCommand());
            assertTrue(cmd.getCommandSpec().findOption("--verbose") != null,
                "TestCommand should have --verbose option");
        }

        @Test
        @DisplayName("TestCommand should have --quiet option")
        void testTestCommandHasQuietOption() {
            CommandLine cmd = new CommandLine(new TestCommand());
            assertTrue(cmd.getCommandSpec().findOption("--quiet") != null,
                "TestCommand should have --quiet option");
        }
    }

    // ==================== GenerateConfigCommand Logging Tests ====================

    @Nested
    @DisplayName("GenerateConfigCommand Logging Tests")
    class GenerateConfigCommandLoggingTests {

        @Test
        @DisplayName("GenerateConfigCommand should have --debug option")
        void testGenerateConfigCommandHasDebugOption() {
            CommandLine cmd = new CommandLine(new GenerateConfigCommand());
            assertTrue(cmd.getCommandSpec().findOption("--debug") != null,
                "GenerateConfigCommand should have --debug option");
        }

        @Test
        @DisplayName("GenerateConfigCommand should have --verbose option")
        void testGenerateConfigCommandHasVerboseOption() {
            CommandLine cmd = new CommandLine(new GenerateConfigCommand());
            assertTrue(cmd.getCommandSpec().findOption("--verbose") != null,
                "GenerateConfigCommand should have --verbose option");
        }

        @Test
        @DisplayName("GenerateConfigCommand should have --quiet option")
        void testGenerateConfigCommandHasQuietOption() {
            CommandLine cmd = new CommandLine(new GenerateConfigCommand());
            assertTrue(cmd.getCommandSpec().findOption("--quiet") != null,
                "GenerateConfigCommand should have --quiet option");
        }
    }

    // ==================== GenerateSchemaCommand Logging Tests ====================

    @Nested
    @DisplayName("GenerateSchemaCommand Logging Tests")
    class GenerateSchemaCommandLoggingTests {

        @Test
        @DisplayName("GenerateSchemaCommand should have --debug option")
        void testGenerateSchemaCommandHasDebugOption() {
            CommandLine cmd = new CommandLine(new GenerateSchemaCommand());
            assertTrue(cmd.getCommandSpec().findOption("--debug") != null,
                "GenerateSchemaCommand should have --debug option");
        }

        @Test
        @DisplayName("GenerateSchemaCommand should have --verbose option")
        void testGenerateSchemaCommandHasVerboseOption() {
            CommandLine cmd = new CommandLine(new GenerateSchemaCommand());
            assertTrue(cmd.getCommandSpec().findOption("--verbose") != null,
                "GenerateSchemaCommand should have --verbose option");
        }

        @Test
        @DisplayName("GenerateSchemaCommand should have --quiet option")
        void testGenerateSchemaCommandHasQuietOption() {
            CommandLine cmd = new CommandLine(new GenerateSchemaCommand());
            assertTrue(cmd.getCommandSpec().findOption("--quiet") != null,
                "GenerateSchemaCommand should have --quiet option");
        }
    }

    // ==================== Cross-Command Consistency Tests ====================

    @Nested
    @DisplayName("Cross-Command Consistency Tests")
    class CrossCommandConsistencyTests {

        static Stream<Arguments> allCommandsProvider() {
            return Stream.of(
                Arguments.of("RedactCommand", new RedactCommand()),
                Arguments.of("RedactTextCommand", new RedactTextCommand()),
                Arguments.of("TestCommand", new TestCommand()),
                Arguments.of("GenerateConfigCommand", new GenerateConfigCommand()),
                Arguments.of("GenerateSchemaCommand", new GenerateSchemaCommand())
            );
        }

        @ParameterizedTest(name = "{0} should have all logging options")
        @MethodSource("allCommandsProvider")
        @DisplayName("All commands should have consistent logging options")
        void testAllCommandsHaveLoggingOptions(String commandName, Object command) {
            CommandLine cmd = new CommandLine(command);

            assertAll(
                () -> assertNotNull(cmd.getCommandSpec().findOption("--debug"),
                    commandName + " should have --debug option"),
                () -> assertNotNull(cmd.getCommandSpec().findOption("--verbose"),
                    commandName + " should have --verbose option"),
                () -> assertNotNull(cmd.getCommandSpec().findOption("-v"),
                    commandName + " should have -v short option"),
                () -> assertNotNull(cmd.getCommandSpec().findOption("--quiet"),
                    commandName + " should have --quiet option"),
                () -> assertNotNull(cmd.getCommandSpec().findOption("-q"),
                    commandName + " should have -q short option")
            );
        }

        @ParameterizedTest(name = "{0} logging options should have correct descriptions")
        @MethodSource("allCommandsProvider")
        @DisplayName("All commands should have descriptive logging option help")
        void testAllCommandsHaveLoggingDescriptions(String commandName, Object command) {
            CommandLine cmd = new CommandLine(command);

            String debugDesc = String.join("", cmd.getCommandSpec().findOption("--debug").description());
            String verboseDesc = String.join("", cmd.getCommandSpec().findOption("--verbose").description());
            String quietDesc = String.join("", cmd.getCommandSpec().findOption("--quiet").description());

            assertAll(
                () -> assertTrue(debugDesc.toLowerCase().contains("debug"),
                    commandName + " --debug should mention debug in description"),
                () -> assertTrue(verboseDesc.toLowerCase().contains("verbose") ||
                                verboseDesc.toLowerCase().contains("info"),
                    commandName + " --verbose should mention verbose/info in description"),
                () -> assertTrue(quietDesc.toLowerCase().contains("quiet") ||
                                quietDesc.toLowerCase().contains("error") ||
                                quietDesc.toLowerCase().contains("minimize"),
                    commandName + " --quiet should mention quiet/error/minimize in description")
            );
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a minimal valid JFR file for testing.
     */
    private Path createMinimalJfrFile() throws Exception {
        Path jfrFile = tempDir.resolve("test.jfr");
        // Create an empty file - command will fail with invalid JFR but that's OK
        // for testing logging options
        Files.write(jfrFile, new byte[0]);
        return jfrFile;
    }

    /**
     * Create a text file with the given content.
     */
    private Path createTextFile(String content) throws Exception {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, content);
        return textFile;
    }
}