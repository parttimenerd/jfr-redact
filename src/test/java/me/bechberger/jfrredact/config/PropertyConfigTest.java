package me.bechberger.jfrredact.config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for PropertyConfig pattern matching.
 */
public class PropertyConfigTest {
    private void assertMatches(PropertyConfig config, String key) {
        assertThat(config.matches(key)).withFailMessage("Expected pattern to match key: '%s'", key).isTrue();
    }
    private void assertDoesNotMatch(PropertyConfig config, String key) {
        assertThat(config.matches(key)).withFailMessage("Expected pattern NOT to match key: '%s'", key).isFalse();
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
        child.getPatterns().add(RedactionConfig.PARENT_MARKER); // Use $PARENT to include parent patterns
        child.getPatterns().add("token");
        child.getPatterns().add("password"); // Duplicate
        child.mergeWith(parent);
        assertAll("merged config should have all unique patterns",
            () -> assertEquals(3, child.getPatterns().size()),
            () -> assertThat(child.getPatterns()).contains("password", "secret", "token")
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
        assertThat(config.matches(key)).as("Key '%s' should %smatch default patterns", key, shouldMatch ? "" : "NOT ").isEqualTo(shouldMatch);
    }

    @Test
    public void testFullMatchMode() {
        PropertyConfig config = new PropertyConfig();
        config.setFullMatch(true);
        config.getPatterns().clear();
        config.getPatterns().add("password");

        // Exact matches should work
        assertThat(config.matches("password")).as("Should match exact 'password'").isTrue();
        assertThat(config.matches("PASSWORD")).as("Should match 'PASSWORD' (case insensitive)").isTrue();

        // Partial matches should NOT work in full match mode
        assertThat(config.matches("user_password")).as("Should NOT match 'user_password' in full match mode").isFalse();
        assertThat(config.matches("myPasswordField")).as("Should NOT match 'myPasswordField' in full match mode").isFalse();
        assertThat(config.matches("PASSWORD_HASH")).as("Should NOT match 'PASSWORD_HASH' in full match mode").isFalse();
    }

    @Test
    public void testPartialMatchModeDefault() {
        PropertyConfig config = new PropertyConfig();
        config.getPatterns().clear();
        config.getPatterns().add("password");

        // fullMatch should be false by default
        assertThat(config.isFullMatch()).as("fullMatch should be false by default").isFalse();

        // Both exact and partial matches should work
        assertThat(config.matches("password")).as("Should match exact 'password'").isTrue();
        assertThat(config.matches("user_password")).as("Should match 'user_password' in partial mode").isTrue();
        assertThat(config.matches("myPasswordField")).as("Should match 'myPasswordField' in partial mode").isTrue();
    }

    @Test
    public void testFullMatchWithCaseSensitive() {
        PropertyConfig config = new PropertyConfig();
        config.setFullMatch(true);
        config.setCaseSensitive(true);
        config.getPatterns().clear();
        config.getPatterns().add("password");

        assertThat(config.matches("password")).as("Should match exact 'password'").isTrue();
        assertThat(config.matches("PASSWORD")).as("Should NOT match 'PASSWORD' (case sensitive)").isFalse();
        assertThat(config.matches("Password")).as("Should NOT match 'Password' (case sensitive)").isFalse();
        assertThat(config.matches("user_password")).as("Should NOT match partial in full match mode").isFalse();
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

    /**
     * Test that bare "key" property name is NOT matched by default patterns.
     * Properties named just "key" should not be redacted as they are too generic.
     * However, values should still undergo normal string pattern redaction (e.g., IP, email detection).
     */
    @Test
    public void testBareKeyNotMatched() {
        PropertyConfig config = new PropertyConfig();
        assertThat(config.matches("key")).as("Bare 'key' should NOT match default patterns").isFalse();
        assertThat(config.matches("KEY")).as("Bare 'KEY' should NOT match default patterns").isFalse();
        assertThat(config.matches("Key")).as("Bare 'Key' should NOT match default patterns").isFalse();
    }

    /**
     * Test that key-related patterns still match when key is part of a compound name.
     */
    @Test
    public void testKeyCompoundNamesMatched() {
        PropertyConfig config = new PropertyConfig();
        assertAll("key compound names should match",
            () -> assertThat(config.matches("api_key")).as("api_key should match").isTrue(),
            () -> assertThat(config.matches("apikey")).as("apikey should match").isTrue(),
            () -> assertThat(config.matches("api-key")).as("api-key should match").isTrue(),
            () -> assertThat(config.matches("encryption_key")).as("encryption_key should match").isTrue(),
            () -> assertThat(config.matches("signing.key")).as("signing.key should match").isTrue(),
            () -> assertThat(config.matches("access_key")).as("access_key should match").isTrue(),
            () -> assertThat(config.matches("secret_key")).as("secret_key should match").isTrue(),
            () -> assertThat(config.matches("privatekey")).as("privatekey should match").isTrue()
         );
     }
 }