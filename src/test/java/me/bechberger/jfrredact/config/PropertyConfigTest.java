package me.bechberger.jfrredact.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
/**
 * Tests for PropertyConfig pattern matching.
 */
public class PropertyConfigTest {
    private void assertMatches(PropertyConfig config, String key) {
        assertTrue(config.matches(key),
            () -> "Expected pattern to match key: '" + key + "'");
    }
    private void assertDoesNotMatch(PropertyConfig config, String key) {
        assertFalse(config.matches(key),
            () -> "Expected pattern NOT to match key: '" + key + "'");
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "password",
        "user_password",
        "PASSWORD_HASH",
        "MyPasswordField",
        "db.password.encrypted",
        "spring.datasource.password"
    })
    public void testPasswordPatternMatchesInMiddle(String key) {
        PropertyConfig config = new PropertyConfig();
        assertMatches(config, key);
    }
    @ParameterizedTest
    @CsvSource({
        "password, password",
        "password, user_password",
        "password, PASSWORD",
        "password, myPasswordField",
        "secret, api_secret_key",
        "secret, SECRET_TOKEN",
        "token, auth_token",
        "token, refresh_token_ttl",
        "key, api_key",
        "key, encryption_key_path"
    })
    public void testPatternMatchesAnywhere(String pattern, String key) {
        PropertyConfig config = new PropertyConfig();
        config.getPatterns().clear();
        config.getPatterns().add(pattern);
        assertMatches(config, key);
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "username",
        "email",
        "database_url",
        "port",
        "timeout"
    })
    public void testNonMatchingKeys(String key) {
        PropertyConfig config = new PropertyConfig();
        config.getPatterns().clear();
        config.getPatterns().add("password");
        assertDoesNotMatch(config, key);
    }
    @Test
    public void testCaseSensitiveMatching() {
        PropertyConfig config = new PropertyConfig();
        config.setCaseSensitive(true);
        config.getPatterns().clear();
        config.getPatterns().add("password");
        assertMatches(config, "user_password");
        assertDoesNotMatch(config, "user_PASSWORD");
        assertDoesNotMatch(config, "USER_PASSWORD");
    }
    @Test
    public void testCaseInsensitiveMatching() {
        PropertyConfig config = new PropertyConfig();
        config.setCaseSensitive(false);
        config.getPatterns().clear();
        config.getPatterns().add("password");
        assertAll("case insensitive matching",
            () -> assertMatches(config, "user_password"),
            () -> assertMatches(config, "user_PASSWORD"),
            () -> assertMatches(config, "USER_PASSWORD"),
            () -> assertMatches(config, "Password123")
        );
    }
    @Test
    public void testDisabledConfig() {
        PropertyConfig config = new PropertyConfig();
        config.setEnabled(false);
        assertAll("disabled config should not match anything",
            () -> assertDoesNotMatch(config, "password"),
            () -> assertDoesNotMatch(config, "user_password")
        );
    }
    @Test
    public void testNullKey() {
        PropertyConfig config = new PropertyConfig();
        assertDoesNotMatch(config, null);
    }
    @Test
    public void testMultiplePatterns() {
        PropertyConfig config = new PropertyConfig();
        // Default patterns include: password, secret, token, key, etc.
        assertAll("default patterns should match common sensitive keys",
            () -> assertMatches(config, "api_password"),
            () -> assertMatches(config, "client_secret"),
            () -> assertMatches(config, "auth_token"),
            () -> assertMatches(config, "encryption_key")
        );
    }
    @Test
    public void testMergeWithParent() {
        PropertyConfig parent = new PropertyConfig();
        parent.getPatterns().clear();
        parent.getPatterns().add("password");
        parent.getPatterns().add("secret");
        PropertyConfig child = new PropertyConfig();
        child.getPatterns().clear();
        child.getPatterns().add("token");
        child.getPatterns().add("password"); // Duplicate
        child.mergeWith(parent);
        assertAll("merged config should have all unique patterns",
            () -> assertEquals(3, child.getPatterns().size()),
            () -> assertTrue(child.getPatterns().contains("password")),
            () -> assertTrue(child.getPatterns().contains("secret")),
            () -> assertTrue(child.getPatterns().contains("token"))
        );
    }
    @ParameterizedTest
    @CsvSource({
        "db.user.password, true",
        "spring.datasource.password, true",
        "oauth.client.secret, true",
        "jwt.signing.key, true",
        "api.auth.token, true",
        "database.url, false",
        "server.port, false",
        "app.name, false"
    })
    public void testRealWorldPropertyNames(String key, boolean shouldMatch) {
        PropertyConfig config = new PropertyConfig();
        assertEquals(shouldMatch, config.matches(key),
            () -> "Key '" + key + "' should " + (shouldMatch ? "" : "NOT ") + "match default patterns");
    }

    @Test
    public void testFullMatchMode() {
        PropertyConfig config = new PropertyConfig();
        config.setFullMatch(true);
        config.getPatterns().clear();
        config.getPatterns().add("password");

        // Exact matches should work
        assertTrue(config.matches("password"), "Should match exact 'password'");
        assertTrue(config.matches("PASSWORD"), "Should match 'PASSWORD' (case insensitive)");

        // Partial matches should NOT work in full match mode
        assertFalse(config.matches("user_password"), "Should NOT match 'user_password' in full match mode");
        assertFalse(config.matches("myPasswordField"), "Should NOT match 'myPasswordField' in full match mode");
        assertFalse(config.matches("PASSWORD_HASH"), "Should NOT match 'PASSWORD_HASH' in full match mode");
    }

    @Test
    public void testPartialMatchModeDefault() {
        PropertyConfig config = new PropertyConfig();
        config.getPatterns().clear();
        config.getPatterns().add("password");

        // fullMatch should be false by default
        assertFalse(config.isFullMatch(), "fullMatch should be false by default");

        // Both exact and partial matches should work
        assertTrue(config.matches("password"), "Should match exact 'password'");
        assertTrue(config.matches("user_password"), "Should match 'user_password' in partial mode");
        assertTrue(config.matches("myPasswordField"), "Should match 'myPasswordField' in partial mode");
    }

    @Test
    public void testFullMatchWithCaseSensitive() {
        PropertyConfig config = new PropertyConfig();
        config.setFullMatch(true);
        config.setCaseSensitive(true);
        config.getPatterns().clear();
        config.getPatterns().add("password");

        assertTrue(config.matches("password"), "Should match exact 'password'");
        assertFalse(config.matches("PASSWORD"), "Should NOT match 'PASSWORD' (case sensitive)");
        assertFalse(config.matches("Password"), "Should NOT match 'Password' (case sensitive)");
        assertFalse(config.matches("user_password"), "Should NOT match partial in full match mode");
    }

    @ParameterizedTest
    @CsvSource({
        "api_key, api_key, true",
        "api_key, API_KEY, true",
        "api_key, my_api_key, false",
        "api_key, api_key_prod, false",
        "token, token, true",
        "token, auth_token, false"
    })
    public void testFullMatchBehavior(String pattern, String key, boolean shouldMatch) {
        PropertyConfig config = new PropertyConfig();
        config.setFullMatch(true);
        config.getPatterns().clear();
        config.getPatterns().add(pattern);

        assertEquals(shouldMatch, config.matches(key),
            () -> "Pattern '" + pattern + "' with key '" + key + "' should " +
                  (shouldMatch ? "" : "NOT ") + "match in full match mode");
    }
}