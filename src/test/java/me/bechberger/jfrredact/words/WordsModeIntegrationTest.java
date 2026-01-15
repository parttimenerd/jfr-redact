package me.bechberger.jfrredact.words;

import me.bechberger.jfrredact.testutil.TextFileBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class WordsModeIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void testDiscoveryFromTextFile() throws Exception {
        Path textFile = TextFileBuilder.create()
            .outputTo(tempDir.resolve("test.log"))
            .withLine("User john_doe logged in")
            .withLine("Password: secret_pass123")
            .withLine("Server: production-server-01")
            .withLine("Admin user logged in")
            .build();
        WordDiscovery discovery = new WordDiscovery();
        List<String> lines = Files.readAllLines(textFile);
        for (String line : lines) {
            discovery.analyzeText(line);
        }
        Set<String> discovered = discovery.getDiscoveredWords();
        assertThat(discovered).contains("john_doe", "secret_pass123", "production-server-01", "Admin", "user", "logged");
    }

    @Test
    void testRedactionWithMultipleRules() throws Exception {
        Path inputFile = TextFileBuilder.create()
            .outputTo(tempDir.resolve("input.log"))
            .withLines(
                "User john_doe accessed /home/john_doe/documents",
                "User jane_smith accessed /home/jane_smith/files",
                "Admin performed system operation",
                "Secret key: secret_api_key_12345"
            )
            .build();
        // Note: paths like /home/john_doe/documents are matched as single words
        // because / is included in the word pattern. Use regex to match usernames in paths.
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("Admin", false),
            WordRedactionRule.redact("john_doe", false),
            WordRedactionRule.redact("jane_smith", false),
            WordRedactionRule.redact(".*/john_doe/.*", true),  // Match paths containing john_doe
            WordRedactionRule.redact(".*/jane_smith/.*", true), // Match paths containing jane_smith
            WordRedactionRule.parse("- secret_*")
        );
        WordRedactor redactor = new WordRedactor(rules);
        Path outputFile = tempDir.resolve("output.log");
        List<String> inputLines = Files.readAllLines(inputFile);
        List<String> outputLines = inputLines.stream()
            .map(redactor::redactText)
            .toList();
        Files.write(outputFile, outputLines);
        String output = Files.readString(outputFile);
        assertThat(output).doesNotContain("john_doe", "jane_smith", "secret_api_key_12345");
        assertThat(output).contains("Admin");
        assertThat(output).contains("***");
    }

    @Test
    void testRegexBasedRedaction() throws Exception {
        Path inputFile = TextFileBuilder.create()
            .outputTo(tempDir.resolve("input.log"))
            .withLines(
                "user123 logged in",
                "user456 logged out",
                "admin performed action",
                "user789 accessed resource"
            )
            .build();
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("admin", false),
            WordRedactionRule.redact("user[0-9]+", true)
        );
        WordRedactor redactor = new WordRedactor(rules);
        List<String> outputLines = Files.readAllLines(inputFile).stream()
            .map(redactor::redactText)
            .toList();
        assertEquals("*** logged in", outputLines.get(0));
        assertEquals("*** logged out", outputLines.get(1));
        assertThat(outputLines.get(2)).contains("admin");
        assertEquals("*** accessed resource", outputLines.get(3));
    }

    @Test
    void testReplaceRedaction() throws Exception {
        Path inputFile = TextFileBuilder.create()
            .outputTo(tempDir.resolve("servers.log"))
            .withLines(
                "Connected to production-server-01",
                "Failover to production-server-02",
                "Cache on cache-server-01"
            )
            .build();
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.replace("production-server-01", "SERVER_PROD_1", false),
            WordRedactionRule.replace("production-server-02", "SERVER_PROD_2", false),
            WordRedactionRule.replace("cache-server-01", "CACHE_1", false)
        );
        WordRedactor redactor = new WordRedactor(rules);
        List<String> outputLines = Files.readAllLines(inputFile).stream()
            .map(redactor::redactText)
            .toList();
        assertThat(outputLines.get(0)).contains("SERVER_PROD_1");
        assertThat(outputLines.get(1)).contains("SERVER_PROD_2");
        assertThat(outputLines.get(2)).contains("CACHE_1");
    }

    @Test
    void testComplexScenario() throws Exception {
        Path inputFile = TextFileBuilder.create()
            .outputTo(tempDir.resolve("application.log"))
            .withLines(
                "2024-01-01 10:00:00 User i345345 logged in",
                "2024-01-01 10:01:00 Database: db-server-01 customer_db",
                "2024-01-01 10:02:00 Cache hit on cache-01",
                "2024-01-01 10:03:00 Admin performed backup",
                "2024-01-01 10:04:00 User i345345 logged out"
            )
            .build();
        WordDiscovery discovery = new WordDiscovery();
        Files.readAllLines(inputFile).forEach(discovery::analyzeText);
        Set<String> discovered = discovery.getDiscoveredWords();
        assertThat(discovered).contains("i345345", "db-server-01");
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.keep("Admin", false),
            WordRedactionRule.keep("customer_db", false),
            WordRedactionRule.redact("i345345", false),
            WordRedactionRule.replace("db-server-01", "DB_SERVER", false),
            WordRedactionRule.replace("cache-01", "CACHE_SERVER", false)
        );
        WordRedactor redactor = new WordRedactor(rules);
        Path outputFile = tempDir.resolve("redacted.log");
        List<String> redactedLines = Files.readAllLines(inputFile).stream()
            .map(redactor::redactText)
            .toList();
        Files.write(outputFile, redactedLines);
        String redactedContent = Files.readString(outputFile);

        // Verify redactions happened
        assertThat(redactedContent).doesNotContain("i345345"); // User ID redacted
        assertThat(redactedContent).contains("***", "Admin", "customer_db", "DB_SERVER", "CACHE_SERVER");
    }

    @Test
    void testStatistics() throws Exception {
        Path inputFile = TextFileBuilder.create()
            .outputTo(tempDir.resolve("input.txt"))
            .withLines(
                "secret1 and secret2",
                "public1 and secret1",
                "secret2 appears again"
            )
            .build();
        List<WordRedactionRule> rules = List.of(
            WordRedactionRule.redact("secret1", false),
            WordRedactionRule.redact("secret2", false)
        );
        WordRedactor redactor = new WordRedactor(rules);
        Files.readAllLines(inputFile).forEach(redactor::redactText);
        String stats = redactor.getStatistics();
        assertThat(stats).contains("unique");
    }
}