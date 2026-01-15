package me.bechberger.jfrredact.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for WordsRedactCommand - command-line interface for word redaction
 */
class WordsRedactCommandTest {

    @TempDir
    Path tempDir;

    /**
     * Helper class to hold test execution results
     */
    private static class TestResult {
        final int exitCode;
        final String output;
        final String errorOutput;

        TestResult(int exitCode, String output, String errorOutput) {
            this.exitCode = exitCode;
            this.output = output;
            this.errorOutput = errorOutput;
        }
    }

    /**
     * Execute redaction with given input, rules, and return results
     */
    private TestResult executeRedaction(String input, String rules) throws Exception {
        Path inputFile = tempDir.resolve("input.txt");
        Path outputFile = tempDir.resolve("output.txt");
        Path rulesFile = tempDir.resolve("rules.txt");

        Files.writeString(inputFile, input);
        Files.writeString(rulesFile, rules);

        WordsRedactCommand command = new WordsRedactCommand();
        CommandLine cmd = new CommandLine(command);

        StringWriter errorWriter = new StringWriter();
        cmd.setErr(new PrintWriter(errorWriter));

        int exitCode = cmd.execute(
            inputFile.toString(),
            outputFile.toString(),
            "-r", rulesFile.toString()
        );

        String output = Files.exists(outputFile) ? Files.readString(outputFile) : "";
        return new TestResult(exitCode, output, errorWriter.toString());
    }

    /**
     * Execute redaction and assert success with expected output
     */
    private void assertRedaction(String input, String rules, String expectedOutput) throws Exception {
        TestResult result = executeRedaction(input, rules);
        assertEquals(0, result.exitCode, "Command should succeed");
        // Strip trailing newline for comparison since Files.readString includes it
        String actualOutput = result.output.stripTrailing();
        assertEquals(expectedOutput, actualOutput);
    }

    /**
     * Execute redaction and assert failure
     */
    private void assertRedactionFails(String input, String rules, String expectedErrorMessage) throws Exception {
        TestResult result = executeRedaction(input, rules);
        assertNotEquals(0, result.exitCode, "Command should fail");
        assertThat(result.errorOutput).contains(expectedErrorMessage);
    }

    // ========== Basic Redaction Tests ==========

    @Test
    void testBasicRedactionWithLiteralPattern() throws Exception {
        assertRedaction(
            "User secret123 logged in\nPassword: mypassword",
            "- secret123\n- mypassword",
            "User *** logged in\nPassword: ***"
        );
    }

    // ========== Wildcard Pattern Tests ==========

    @Test
    void testWildcardPrefixPattern() throws Exception {
        assertRedaction(
            "Values: secret123, secretKey, mysecret, public",
            "- secret*",
            "Values: ***, ***, mysecret, public"
        );
    }

    @Test
    void testWildcardSuffixPattern() throws Exception {
        assertRedaction(
            "Files: test_key, api_key, mykey, other",
            "- *_key",
            "Files: ***, ***, mykey, other"
        );
    }

    @Test
    void testWildcardContainsPattern() throws Exception {
        assertRedaction(
            "Hosts: mypassword, haspasswordhere, password123, public",
            "- *password*",
            "Hosts: ***, ***, ***, public"
        );
    }

    @Test
    void testWildcardInMiddleOfPattern() throws Exception {
        assertRedaction(
            "Files: app-v1.2.jar, lib-v2.0.jar, config.xml",
            "- *-v*.jar",
            "Files: ***, ***, config.xml"
        );
    }

    // ========== Regex Pattern Tests ==========

    @Test
    void testRegexPattern() throws Exception {
        assertRedaction(
            "Users: user123, user456, userABC, admin",
            "- /user[0-9]+/",
            "Users: ***, ***, userABC, admin"
        );
    }

    @Test
    void testRegexPatternWithComplexExpression() throws Exception {
        assertRedaction(
            "IPs: 192.168.1.1, 10.0.0.1, notanip",
            "- /\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/",
            "IPs: ***, ***, notanip"
        );
    }

    // ========== Replace Rule Tests ==========

    @Test
    void testReplaceRuleWithLiteral() throws Exception {
        assertRedaction(
            "Server: production-db connected",
            "! production-db DATABASE",
            "Server: DATABASE connected"
        );
    }

    @Test
    void testReplaceRuleWithWildcard() throws Exception {
        assertRedaction(
            "Servers: prod-server-01, prod-server-02, dev-server",
            "! prod-server-* PRODUCTION",
            "Servers: PRODUCTION, PRODUCTION, dev-server"
        );
    }

    @Test
    void testReplaceRuleWithRegex() throws Exception {
        assertRedaction(
            "Users: user123, user456, userABC",
            "! /user[0-9]+/ USER",
            "Users: USER, USER, userABC"
        );
    }

    // ========== Keep Rule Tests ==========

    @Test
    void testKeepRuleWithWildcard() throws Exception {
        assertRedaction(
            "Users: admin, adminUser, Administrator, user123",
            "+ admin*\n- user*\n- Administrator",
            "Users: admin, adminUser, ***, ***"
        );
    }

    @Test
    void testKeepRuleWithRegex() throws Exception {
        assertRedaction(
            "Values: Admin, Administrator, admin, user",
            "+ /Admin.*/\n- admin\n- user",
            "Values: Admin, Administrator, ***, ***"
        );
    }

    // ========== Mixed Rules Tests ==========

    @Test
    void testMixedRulesRedactReplaceKeep() throws Exception {
        assertRedaction(
            "Data: admin, secret123, prod-server, publicValue",
            "+ admin\n- secret*\n! prod-* PRODUCTION\n",
            "Data: admin, ***, PRODUCTION, publicValue"
        );
    }

    @Test
    void testCommentsAndEmptyLinesIgnored() throws Exception {
        assertRedaction(
            "Values: secret, public",
            "# This is a comment\n\n- secret\n  \n# Another comment\n",
            "Values: ***, public"
        );
    }

    @Test
    void testMultipleRedactionsOnSameLine() throws Exception {
        assertRedaction(
            "Multiple values: secret1, secret2, secret3 here",
            "- secret*",
            "Multiple values: ***, ***, *** here"
        );
    }

    @Test
    void testCaseSensitiveMatching() throws Exception {
        assertRedaction(
            "Values: Secret, secret, SECRET",
            "- secret",
            "Values: Secret, ***, SECRET"
        );
    }

    // ========== Complex Real-World Scenario ==========

    @Test
    void testComplexRealWorldScenario() throws Exception {
        String input =
            "2024-01-15 10:00:00 INFO User user123 logged in from 192.168.1.50\n" +
            "2024-01-15 10:00:01 INFO Connected to prod-db-primary\n" +
            "2024-01-15 10:00:02 WARN API key api_key_abc123xyz used\n" +
            "2024-01-15 10:00:03 INFO Admin reviewed configuration\n" +
            "2024-01-15 10:00:04 ERROR Failed to connect to staging-server-01";

        String rules =
            "# Keep safe values\n" +
            "+ Admin\n" +
            "+ INFO\n" +
            "+ WARN\n" +
            "+ ERROR\n" +
            "\n" +
            "# Redact sensitive patterns\n" +
            "- /user[0-9]+/\n" +
            "- /\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\n" +
            "- api_key_*\n" +
            "\n" +
            "# Replace server names\n" +
            "! prod-* PRODUCTION\n" +
            "! staging-* STAGING\n";

        TestResult result = executeRedaction(input, rules);

        assertEquals(0, result.exitCode);

        // Verify redactions
        assertThat(result.output).doesNotContain("user123", "192.168.1.50", "api_key_abc123xyz");

        // Verify replacements
        assertThat(result.output).contains("PRODUCTION", "STAGING");

        // Verify kept values
        assertThat(result.output).contains("Admin", "INFO", "WARN", "ERROR");
    }

    // ========== Error Handling Tests ==========

    @Test
    void testFileNotFound() throws Exception {
        Path nonExistentFile = tempDir.resolve("nonexistent.txt");
        Path outputFile = tempDir.resolve("output.txt");
        Path rulesFile = tempDir.resolve("rules.txt");

        Files.writeString(rulesFile, "- secret");

        WordsRedactCommand command = new WordsRedactCommand();
        CommandLine cmd = new CommandLine(command);

        StringWriter sw = new StringWriter();
        cmd.setErr(new PrintWriter(sw));

        int exitCode = cmd.execute(
            nonExistentFile.toString(),
            outputFile.toString(),
            "-r", rulesFile.toString()
        );

        assertEquals(1, exitCode);
        assertThat(sw.toString()).contains("does not exist");
    }

    @Test
    void testEmptyRulesWarning() throws Exception {
        TestResult result = executeRedaction("Some content", "# Only comments\n\n");

        assertEquals(0, result.exitCode);
        assertThat(result.errorOutput).contains("No redaction rules");
    }
}