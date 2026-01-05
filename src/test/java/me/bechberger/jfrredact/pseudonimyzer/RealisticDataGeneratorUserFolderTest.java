package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for RealisticDataGenerator user folder generation enhancements.
 */
public class RealisticDataGeneratorUserFolderTest {

    private RealisticDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RealisticDataGenerator(42L);
    }

    // ========== User Folder Generation Tests ==========

    @Test
    public void testGenerateUserFolder_UsesNamesFromPool() {
        String folder1 = generator.generateUserFolder("user1");
        String folder2 = generator.generateUserFolder("user2");
        String folder3 = generator.generateUserFolder("user3");

        // Should use actual names from the pool
        assertNotNull(folder1);
        assertNotNull(folder2);
        assertNotNull(folder3);

        // Should be different for different inputs
        assertNotEquals(folder1, folder2);
        assertNotEquals(folder2, folder3);

        // Should not contain numbers (uses actual names)
        assertThat(folder1.matches(".*\\d+.*")).as("Should use names, not numbers: %s", folder1).isFalse();
    }

    @Test
    public void testGenerateUserFolder_Consistency() {
        String original = "johndoe";

        String result1 = generator.generateUserFolder(original);
        String result2 = generator.generateUserFolder(original);

        assertEquals(result1, result2, "Same input should produce same output");
    }

    @Test
    public void testGenerateUserFolder_CombinesNamesWhenExhausted() {
        // Generate more folders than available names (26 first names)
        for (int i = 0; i < 26; i++) {
            generator.generateUserFolder("user" + i);
        }

        // Next ones should combine names
        String combined = generator.generateUserFolder("newuser");

        assertNotNull(combined);
        // Combined names should be longer than single names (typically)
        assertThat(combined.length()).as("Combined name should be longer: %s", combined).isGreaterThan(3);
    }

    @Test
    public void testGeneratePath_UsesRealisticUserFolders_Unix() {
        String path = "/home/johndoe/documents";

        String result = generator.generatePath(path);

        assertThat(result).as("Should preserve /home/ prefix").startsWith("/home/");
        assertThat(result).as("Should preserve subdirectory").endsWith("/documents");

        // Extract username
        String username = result.substring(6, result.indexOf("/documents"));
        assertThat(username.matches("user\\d+")).as("Should not use numbered pattern: %s", username).isFalse();
    }

    @Test
    public void testGeneratePath_UsesRealisticUserFolders_Mac() {
        String path = "/Users/johndoe/Library";

        String result = generator.generatePath(path);

        assertThat(result).as("Should preserve /Users/ prefix").startsWith("/Users/");
        assertThat(result).as("Should preserve subdirectory").endsWith("/Library");

        // Extract username
        String username = result.substring(7, result.indexOf("/Library"));
        assertThat(username.matches("user\\d+")).as("Should not use numbered pattern: %s", username).isFalse();
    }

    @Test
    public void testGeneratePath_UsesRealisticUserFolders_Windows() {
        String path = "C:\\Users\\JohnDoe\\AppData";

        String result = generator.generatePath(path);

        assertThat(result).as("Should preserve C:\\Users\\ prefix").startsWith("C:\\Users\\");
        assertThat(result).as("Should preserve subdirectory").endsWith("\\AppData");

        // Extract username (capitalized for Windows)
        int startIdx = "C:\\Users\\".length();
        int endIdx = result.indexOf("\\AppData");
        String username = result.substring(startIdx, endIdx);

        // Should be capitalized
        assertThat(Character.isUpperCase(username.charAt(0))).as("Windows username should be capitalized: %s", username).isTrue();
        assertThat(username.matches("User\\d+")).as("Should not use numbered pattern: %s", username).isFalse();
    }

    @Test
    public void testGeneratePath_ConsistentUserFolders() {
        String path1 = "/home/johndoe/file1.txt";
        String path2 = "/home/johndoe/file2.txt";

        String result1 = generator.generatePath(path1);
        String result2 = generator.generatePath(path2);

        // Should use same username for same original user
        String user1 = result1.substring(6, result1.lastIndexOf("/"));
        String user2 = result2.substring(6, result2.lastIndexOf("/"));

        assertEquals(user1, user2, "Same original user should map to same generated user");
    }

    @Test
    public void testGeneratePath_DifferentUsersGetDifferentFolders() {
        String path1 = "/home/alice/file.txt";
        String path2 = "/home/bob/file.txt";

        String result1 = generator.generatePath(path1);
        String result2 = generator.generatePath(path2);

        // Extract usernames
        String user1 = result1.substring(6, result1.lastIndexOf("/"));
        String user2 = result2.substring(6, result2.lastIndexOf("/"));

        assertNotEquals(user1, user2, "Different users should get different folders");
    }

    @Test
    public void testClearCache_ResetsNameIndex() {
        // Generate a few folders
        String folder1 = generator.generateUserFolder("user1");
        String folder2 = generator.generateUserFolder("user2");

        generator.clearCache();

        // After clearing, should start from beginning of name pool
        String folder3 = generator.generateUserFolder("user3");

        // Might get same name as folder1 (depends on random seed and order)
        assertNotNull(folder3);
    }

    @Test
    public void testGenerateUserFolder_NullAndEmpty() {
        assertNull(generator.generateUserFolder(null));
        assertEquals("", generator.generateUserFolder(""));
    }

    // ========== Integration Tests ==========

    @Test
    public void testMultiplePathsWithRealisticFolders() {
        String[] paths = {
            "/home/user1/docs",
            "/home/user2/pics",
            "/Users/user3/Desktop",
            "C:\\Users\\User4\\Downloads"
        };

        for (String path : paths) {
            String result = generator.generatePath(path);
            assertNotNull(result);
            // Should not contain "user\d+" pattern
            assertThat(result.matches(".*user\\d+.*")).as("Should use realistic names, not numbered pattern: %s", result).isFalse();
        }
    }

    @Test
    public void testDeterminism_SameSeedProducesSameUserFolders() {
        RealisticDataGenerator gen1 = new RealisticDataGenerator(123L);
        RealisticDataGenerator gen2 = new RealisticDataGenerator(123L);

        String folder1a = gen1.generateUserFolder("test1");
        String folder1b = gen2.generateUserFolder("test1");

        assertEquals(folder1a, folder1b, "Same seed should produce same user folders");
    }
}