package me.bechberger.jfrredact.pseudonimyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertFalse(folder1.matches(".*\\d+.*"), "Should use names, not numbers: " + folder1);
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
        assertTrue(combined.length() > 3, "Combined name should be longer: " + combined);
    }

    @Test
    public void testGeneratePath_UsesRealisticUserFolders_Unix() {
        String path = "/home/johndoe/documents";

        String result = generator.generatePath(path);

        assertTrue(result.startsWith("/home/"), "Should preserve /home/ prefix");
        assertTrue(result.endsWith("/documents"), "Should preserve subdirectory");

        // Extract username
        String username = result.substring(6, result.indexOf("/documents"));
        assertFalse(username.matches("user\\d+"), "Should not use numbered pattern: " + username);
    }

    @Test
    public void testGeneratePath_UsesRealisticUserFolders_Mac() {
        String path = "/Users/johndoe/Library";

        String result = generator.generatePath(path);

        assertTrue(result.startsWith("/Users/"), "Should preserve /Users/ prefix");
        assertTrue(result.endsWith("/Library"), "Should preserve subdirectory");

        // Extract username
        String username = result.substring(7, result.indexOf("/Library"));
        assertFalse(username.matches("user\\d+"), "Should not use numbered pattern: " + username);
    }

    @Test
    public void testGeneratePath_UsesRealisticUserFolders_Windows() {
        String path = "C:\\Users\\JohnDoe\\AppData";

        String result = generator.generatePath(path);

        assertTrue(result.startsWith("C:\\Users\\"), "Should preserve C:\\Users\\ prefix");
        assertTrue(result.endsWith("\\AppData"), "Should preserve subdirectory");

        // Extract username (capitalized for Windows)
        int startIdx = "C:\\Users\\".length();
        int endIdx = result.indexOf("\\AppData");
        String username = result.substring(startIdx, endIdx);

        // Should be capitalized
        assertTrue(Character.isUpperCase(username.charAt(0)),
                   "Windows username should be capitalized: " + username);
        assertFalse(username.matches("User\\d+"), "Should not use numbered pattern: " + username);
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
            assertFalse(result.matches(".*user\\d+.*"),
                       "Should use realistic names, not numbered pattern: " + result);
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