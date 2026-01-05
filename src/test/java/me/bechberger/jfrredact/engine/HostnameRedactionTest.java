package me.bechberger.jfrredact.engine;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.config.RedactionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hostname redaction patterns added for hs_err file support.
 */
class HostnameRedactionTest {

    private RedactionEngine engine;

    @BeforeEach
    void setUp() {
        try {
            // Load default config and enable hostname redaction
            RedactionConfig config = new ConfigLoader().load("default");
            config.getStrings().setEnabled(true);

            // Enable only hostname redaction, disable all others
            config.getStrings().getPatterns().getHostnames().setEnabled(true);
            config.getStrings().getPatterns().getIpAddresses().setEnabled(false);
            config.getStrings().getPatterns().getEmails().setEnabled(false);
            config.getStrings().getPatterns().getUser().setEnabled(false);
            config.getStrings().getPatterns().getSshHosts().setEnabled(false);
            config.getStrings().getPatterns().getUuids().setEnabled(false);
            config.getStrings().getPatterns().getInternalUrls().setEnabled(false);

            engine = new RedactionEngine(config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    @ParameterizedTest
    @CsvSource({
        // hs_err "Host:" line pattern
        "Host: dev-jsmith-01.corp.example.com, Host: ***",
        "Host: server123.internal.company.net, Host: ***",
        "Host: build-machine.local, Host: ***",
        "Host: jenkins-agent-42.ci.example.org, Host: ***",

        // Standalone FQDN patterns
        "Server is running on dev-server.corp.example.com, Server is running on ***",
        "Connected to db-primary.internal.company.net, Connected to ***",
        "Hostname: web-01.prod.example.com, Hostname: ***",

        // uname -a output patterns (Linux/Darwin hostname)
        "Linux dev-jsmith-01 5.15.0-91-generic, Linux *** 5.15.0-91-generic",
        "Darwin macbook-pro.local 21.6.0, Darwin *** 21.6.0",

        // Multiple hostnames in same line
        "From host1.corp.com to host2.corp.com, From *** to ***",

        // Edge cases - should NOT redact
        "localhost should not be redacted, localhost should not be redacted",
        "127.0.0.1 is safe, 127.0.0.1 is safe",
        "Short name without domain, Short name without domain",

        // Single component hostnames should NOT match FQDN pattern
        "hostname, hostname",
        "server, server"
    })
    void testHostnameRedaction(String input, String expected) {
        String actual = engine.redact("text", input);
        assertEquals(expected, actual,
            "Hostname redaction mismatch for input: " + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Host: dev-jsmith.corp.example.com",
        "Host: build-server-01.internal.company.net",
        "Host: jenkins.ci.example.org",
        "Server: web-frontend.prod.example.com"
    })
    void testHostnameAfterLabel(String input) {
        String result = engine.redact("text", input);
        assertThat(result).doesNotContain(".corp.", ".internal.", ".example.");
        assertThat(result).contains("***");
    }

    @ParameterizedTest
    @CsvSource({
        "Linux dev-jsmith-laptop 5.15.0, Linux *** 5.15.0",
        "Darwin macbook-pro 21.6.0, Darwin *** 21.6.0",
        "Linux server.corp.example.com 4.19.0, Linux *** 4.19.0"
    })
    void testUnameHostnamePattern(String input, String expected) {
        String actual = engine.redact("text", input);
        assertEquals(expected, actual,
            "uname -a hostname pattern should be redacted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "localhost",
        "localhost.localdomain",
        "127.0.0.1"
    })
    void testSafeHostnamesNotRedacted(String safeHostname) {
        String input = "Host: " + safeHostname;
        String result = engine.redact("text", input);
        assertThat(result).contains(safeHostname);
    }

    @ParameterizedTest
    @CsvSource({
        "https://artifactory.corp.example.com/repo/pkg.jar, ***",
        "https://nexus.internal.company.net/repository/releases, ***",
        "https://git.corp.example.com/project/repo.git, ***",
        "https://gitlab.internal/group/project, ***",
        "https://jenkins.ci.example.org/job/build, ***",
        "http://build.internal.company.net/artifacts, ***"
    })
    void testInternalUrlRedaction(String input, String expected) {
        try {
            // Create new config with internal URL redaction enabled
            RedactionConfig config = new ConfigLoader().load("default");
            config.getStrings().setEnabled(true);
            config.getStrings().getPatterns().getHostnames().setEnabled(true);
            config.getStrings().getPatterns().getInternalUrls().setEnabled(true);

            RedactionEngine urlEngine = new RedactionEngine(config);

            String result = urlEngine.redact("text", input);
            assertEquals(expected, result,
                "Internal URL should be redacted. Input: " + input + ", Result: " + result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default config", e);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "dev-server-01.corp.example.com, true",
        "jenkins-agent.ci.example.org, true",
        "web-frontend.prod.example.com, true",
        "db-primary.internal.company.net, true",
        "localhost, false",
        "127.0.0.1, false",
        "singlename, false"  // Single word hostnames are NOT FQDNs, should not be redacted
    })
    void testFqdnDetection(String hostname, boolean shouldRedact) {
        String input = "Host: " + hostname;
        String result = engine.redact("text", input);

        if (shouldRedact) {
            assertThat(result).doesNotContain(hostname);
            assertThat(result).contains("***");
        } else {
            assertThat(result).contains(hostname);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Host: dev-jsmith.corp.example.com\nEmail: user@example.com",
        "Server: build.internal.net\nPath: /home/johndoe/.ssh",
        "Linux hostname.corp.com 5.15.0\nuser@example.com connected"
    })
    void testMultiplePatternTypes(String input) {
        String result = engine.redact("text", input);

        // Should redact hostnames
        assertThat(result).doesNotContain(".corp.", ".internal.", ".example.");

        // Email patterns are disabled in setUp, so emails won't be redacted in this test

        // Should contain redaction markers
        assertThat(result).contains("***");
    }
}