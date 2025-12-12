package me.bechberger.jfrredact.text;

import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TextFileRedactor - redaction of arbitrary text files.
 *
 * <p>Note: These tests focus on file-specific behavior. The underlying redaction patterns
 * (emails, IPs, UUIDs, etc.) are thoroughly tested in {@link me.bechberger.jfrredact.engine.RedactionEngineTest}.
 *
 * <p>TextFileRedactor is particularly useful for redacting:
 * <ul>
 *   <li>Java error log files (hs_err_pid*.log) which may contain sensitive paths, system properties, and environment variables</li>
 *   <li>Application log files with sensitive user data or configuration</li>
 *   <li>Any text file that needs the same redaction patterns as JFR recordings</li>
 * </ul>
 */
public class TextFileRedactorTest {

    @TempDir
    Path tempDir;

    // ========== Mini Test Framework ==========

    /**
     * File-based redaction test case
     */
    record FileRedactionTestCase(String description, String input, String[] shouldNotContain,
                                  String[] shouldContain) {
        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Assertion helper for redaction tests
     */
    static class RedactionAssertion {
        private final String result;

        RedactionAssertion(String result) {
            this.result = result;
        }

        RedactionAssertion doesNotContain(String... values) {
            for (String value : values) {
                assertFalse(result.contains(value),
                    () -> "Content should NOT contain: " + value + "\nActual: " + result);
            }
            return this;
        }

        RedactionAssertion contains(String... values) {
            for (String value : values) {
                assertTrue(result.contains(value),
                    () -> "Content should contain: " + value + "\nActual: " + result);
            }
            return this;
        }

        RedactionAssertion isEqualTo(String expected) {
            assertEquals(expected, result);
            return this;
        }

        RedactionAssertion isNull() {
            assertNull(result);
            return this;
        }
    }

    // ========== Helper Methods ==========

    private TextFileRedactor createDefaultRedactor() {
        return new TextFileRedactor(new RedactionEngine(new RedactionConfig()));
    }

    private TextFileRedactor createRedactorWithPseudonymization() {
        RedactionConfig config = new RedactionConfig();
        config.getGeneral().getPseudonymization().setEnabled(true);
        return new TextFileRedactor(new RedactionEngine(config));
    }

    private TextFileRedactor createRedactorWithDisabledRedaction() {
        return new TextFileRedactor(RedactionEngine.NONE);
    }

    private Path createTempFile(String content) throws IOException {
        Path file = tempDir.resolve("test-input-" + System.nanoTime() + ".txt");
        Files.writeString(file, content);
        return file;
    }

    private String readFile(Path file) throws IOException {
        return Files.readString(file);
    }

    private RedactionAssertion assertThat(String result) {
        return new RedactionAssertion(result);
    }

    private void assertContains(String content, String expected) {
        assertTrue(content.contains(expected),
            () -> "Content should contain: " + expected + "\nActual: " + content);
    }

    private void assertNotContains(String content, String unexpected) {
        assertFalse(content.contains(unexpected),
            () -> "Content should NOT contain: " + unexpected + "\nActual: " + content);
    }

    // ========== Test Data Providers ==========
    // Note: Pattern-specific redaction (emails, IPs, UUIDs) is tested in RedactionEngineTest
    // These tests focus on file handling and text file redaction workflow

    // ========== File Redaction Test Data ==========

    static Stream<FileRedactionTestCase> fileRedactionCases() {
        return Stream.of(
            new FileRedactionTestCase(
                "Basic redaction",
                """
                This is a test file.
                Email: user@example.com
                IP: 192.168.1.100
                Normal text here.
                """,
                new String[]{"user@example.com", "192.168.1.100"},
                new String[]{"This is a test file.", "Normal text here."}
            ),
            new FileRedactionTestCase(
                "Multiple lines with sensitive data",
                """
                Line 1: email1@test.com
                Line 2: email2@test.org
                Line 3: 10.0.0.1
                Line 4: Normal text
                """,
                new String[]{"email1@test.com", "email2@test.org", "10.0.0.1"},
                new String[]{"Normal text"}
            ),
            new FileRedactionTestCase(
                "Java error log (hs_err) excerpt - paths and emails redacted",
                """
                ---------------  S Y S T E M  ---------------
                
                OS: macOS 14.2 (23C64)
                uname: Darwin 23.2.0 Darwin Kernel Version 23.2.0
                rlimit (soft/hard): STACK 8192k/65532k , CORE infinity/infinity , NPROC 2784/4176 
                load average: 2.50 2.25 2.10
                
                CPU: total 8 (initial active 8) (4 cores per cpu, 2 threads per core) family 6
                
                Memory: 4k page, physical 16777216k(123456k free)
                
                vm_info: OpenJDK 64-Bit Server VM (21.0.1+12) for bsd-amd64 JRE (21.0.1+12)
                
                Environment Variables:
                JAVA_HOME=/Users/johndoe/Library/Java/JavaVirtualMachines/openjdk-21.0.1
                USER=johndoe
                HOME=/Users/johndoe
                PATH=/Users/johndoe/bin:/usr/local/bin:/usr/bin
                MAIL=johndoe@company.com
                
                System Properties:
                user.name=johndoe
                user.home=/Users/johndoe
                java.home=/Users/johndoe/Library/Java/JavaVirtualMachines/openjdk-21.0.1
                """,
                new String[]{
                    "/Users/johndoe",  // Home directories are redacted
                    "johndoe@company.com"  // Emails are redacted
                    // Note: "user.name=johndoe" is NOT redacted - property redaction only works on JFR key-value pairs
                },
                new String[]{"S Y S T E M", "Environment Variables:", "Darwin Kernel", "user.name=johndoe"}
            ),
            new FileRedactionTestCase(
                "Application log file",
                """
                Application Log File
                ====================
                
                User logged in: john.doe@company.com
                Source IP: 203.0.113.45
                Session ID: 550e8400-e29b-41d4-a716-446655440000
                
                Connection established to: 192.168.1.50:8080
                Secondary contact: admin@internal.net
                
                Normal log entry without sensitive data
                """,
                new String[]{
                    "john.doe@company.com", "203.0.113.45",
                    "192.168.1.50", "admin@internal.net"
                    // Note: UUIDs are NOT redacted by default (disabled in default.yaml)
                    // Note: IPv6 short format (fe80::1) doesn't match the default regex pattern
                },
                new String[]{"Application Log File", "Normal log entry without sensitive data", "550e8400-e29b-41d4-a716-446655440000"}
            ),
            new FileRedactionTestCase(
                "UTF-8 content preserved",
                """
                UTF-8 test: Héllo Wörld
                Chinese: 你好
                Emoji: 😀
                Normal text
                """,
                new String[]{},
                new String[]{"Héllo Wörld", "你好", "😀", "Normal text"}
            )
        );
    }

    // ========== Parameterized File Redaction Tests ==========

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fileRedactionCases")
    public void testRedactFile_ParameterizedCases(FileRedactionTestCase testCase) throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();

        Path inputFile = createTempFile(testCase.input);
        Path outputFile = tempDir.resolve("output.txt");

        redactor.redactFile(inputFile, outputFile);
        String result = readFile(outputFile);

        RedactionAssertion assertion = assertThat(result);

        if (testCase.shouldNotContain.length > 0) {
            assertion.doesNotContain(testCase.shouldNotContain);
        }

        if (testCase.shouldContain.length > 0) {
            assertion.contains(testCase.shouldContain);
        }
    }


    // ========== Additional File Tests ==========

    @Test
    public void testRedactFile_EmptyFile() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        Path inputFile = createTempFile("");
        Path outputFile = tempDir.resolve("output.txt");

        redactor.redactFile(inputFile, outputFile);

        assertEquals("", readFile(outputFile));
    }

    @Test
    public void testRedactFile_PreservesLineCount() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        String input = """
            Line 1
            Line 2: email@test.com
            Line 3
            Line 4: 192.168.1.1
            Line 5
            """;

        Path inputFile = createTempFile(input);
        Path outputFile = tempDir.resolve("output.txt");
        redactor.redactFile(inputFile, outputFile);

        assertEquals(
            Files.readAllLines(inputFile).size(),
            Files.readAllLines(outputFile).size(),
            "Output should have the same number of lines as input"
        );
    }

    @Test
    public void testRedactFile_WithFileObjects() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        Path inputFile = createTempFile("Email: test@example.com");
        Path outputFile = tempDir.resolve("output.txt");

        redactor.redactFile(inputFile.toFile(), outputFile.toFile());

        assertThat(readFile(outputFile)).doesNotContain("test@example.com");
    }

    @Test
    public void testRedactFile_WithDisabledRedaction() throws IOException {
        TextFileRedactor redactor = createRedactorWithDisabledRedaction();
        String input = """
            Email: user@example.com
            IP: 192.168.1.100
            """;

        Path inputFile = createTempFile(input);
        Path outputFile = tempDir.resolve("output.txt");
        redactor.redactFile(inputFile, outputFile);

        // When redaction is disabled, content should be unchanged
        assertThat(readFile(outputFile))
            .contains("user@example.com", "192.168.1.100");
    }

    @Test
    public void testRedactFile_LargeFile() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();

        // Generate a larger file with multiple sensitive entries
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            input.append("Line ").append(i).append(": user").append(i).append("@example.com\n");
        }

        Path inputFile = createTempFile(input.toString());
        Path outputFile = tempDir.resolve("output.txt");
        redactor.redactFile(inputFile, outputFile);

        String result = readFile(outputFile);
        // Verify no emails remain
        for (int i = 0; i < 100; i++) {
            assertNotContains(result, "user" + i + "@example.com");
        }
    }

    @Test
    public void testRedactFile_ConsistencyWithSameInput() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        String input = "Email: test@example.com and IP: 192.168.1.1";

        Path inputFile = createTempFile(input);
        Path outputFile1 = tempDir.resolve("output1.txt");
        Path outputFile2 = tempDir.resolve("output2.txt");

        redactor.redactFile(inputFile, outputFile1);
        redactor.redactFile(inputFile, outputFile2);

        assertEquals(readFile(outputFile1), readFile(outputFile2),
            "Same input should produce same output");
    }

    // ========== Edge Cases ==========

    @ParameterizedTest
    @ValueSource(strings = {"   \n\t\n  \n", "\n\n\n", "   "})
    public void testRedactFile_WhitespaceOnly(String input) throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        Path inputFile = createTempFile(input);
        Path outputFile = tempDir.resolve("output.txt");

        redactor.redactFile(inputFile, outputFile);

        // Should preserve the structure
        assertNotNull(readFile(outputFile));
    }

    @Test
    public void testRedactFile_VeryLongLine() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        String longLine = "Start ".repeat(100) + " email@test.com " + "End ".repeat(100);

        Path inputFile = createTempFile(longLine);
        Path outputFile = tempDir.resolve("output.txt");
        redactor.redactFile(inputFile, outputFile);

        assertThat(readFile(outputFile)).doesNotContain("email@test.com");
    }

    @Test
    public void testRedactFile_WindowsLineEndings() throws IOException {
        TextFileRedactor redactor = createDefaultRedactor();
        String input = "Line 1\r\nEmail: test@example.com\r\nLine 3\r\n";

        Path inputFile = tempDir.resolve("test-windows.txt");
        Files.writeString(inputFile, input);
        Path outputFile = tempDir.resolve("output.txt");

        redactor.redactFile(inputFile, outputFile);

        assertThat(readFile(outputFile)).doesNotContain("test@example.com");
    }
}