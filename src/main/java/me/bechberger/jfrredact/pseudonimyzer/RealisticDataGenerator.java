package me.bechberger.jfrredact.pseudonimyzer;

import java.util.*;

/**
 * Generates realistic-looking alternatives for sensitive data.
 *
 * This generator creates plausible replacements that maintain the format and structure
 * of the original data while protecting sensitive information.
 *
 * Examples:
 * - "john.doe@example.com" -> "alice.smith@example.com"
 * - "/home/johndoe" -> "/home/user01"
 * - "C:\Users\JohnDoe" -> "C:\Users\User01"
 * - "johndoe" -> "user01"
 */
public class RealisticDataGenerator {

    private static final String[] FIRST_NAMES = {
        "alice", "bob", "charlie", "diana", "eve", "frank", "grace", "henry",
        "iris", "jack", "kate", "leo", "mary", "nathan", "olivia", "peter",
        "quinn", "rachel", "sam", "tina", "uma", "victor", "wendy", "xavier",
        "yara", "zoe"
    };

    private static final String[] LAST_NAMES = {
        "smith", "johnson", "williams", "brown", "jones", "garcia", "miller", "davis",
        "rodriguez", "martinez", "hernandez", "lopez", "gonzalez", "wilson", "anderson",
        "thomas", "taylor", "moore", "jackson", "martin", "lee", "perez", "thompson"
    };

    private static final String[] COMPANIES = {
        "example", "test", "demo", "sample", "acme", "techcorp", "datagroup",
        "systems", "solutions", "industries", "services", "global"
    };

    private static final String[] DOMAINS = {
        "com", "org", "net", "io"
    };

    private final Random random;
    private final Map<String, String> nameCache = new HashMap<>();
    private final Map<String, String> emailCache = new HashMap<>();
    private final Map<String, String> pathCache = new HashMap<>();
    private final Map<String, String> userFolderCache = new HashMap<>();
    private int userCounter = 1;
    private int nameIndex = 0;

    public RealisticDataGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generate a realistic user folder name.
     * Uses actual names from the pool, combining them when exhausted.
     *
     * Examples:
     * - First calls: "alice", "bob", "charlie"
     * - When pool exhausted: "alicebob", "charliealice", etc.
     */
    public String generateUserFolder(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        // Check cache
        String cached = userFolderCache.get(original);
        if (cached != null) {
            return cached;
        }

        String generated;

        // Use names from pool first
        if (nameIndex < FIRST_NAMES.length) {
            generated = FIRST_NAMES[nameIndex++];
        } else {
            // Combine names when pool exhausted
            int firstIdx = random.nextInt(FIRST_NAMES.length);
            int secondIdx = random.nextInt(FIRST_NAMES.length);
            generated = FIRST_NAMES[firstIdx] + FIRST_NAMES[secondIdx];
        }

        userFolderCache.put(original, generated);
        return generated;
    }

    /**
     * Generate a realistic username based on the original.
     * Maintains consistency for the same input.
     */
    public String generateUsername(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        // Check cache
        String cached = nameCache.get(original);
        if (cached != null) {
            return cached;
        }

        String generated;

        // If original contains dots or underscores, maintain that pattern
        if (original.contains(".")) {
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            generated = firstName + "." + lastName;
        } else if (original.contains("_")) {
            generated = "user_" + String.format("%02d", userCounter++);
        } else {
            // Simple username
            generated = "user" + String.format("%02d", userCounter++);
        }

        nameCache.put(original, generated);
        return generated;
    }

    /**
     * Generate a realistic email address.
     * Tries to maintain the domain structure if possible.
     */
    public String generateEmail(String original) {
        if (original == null || !original.contains("@")) {
            return original;
        }

        // Check cache
        String cached = emailCache.get(original);
        if (cached != null) {
            return cached;
        }

        String[] parts = original.split("@");
        String localPart = parts[0];
        String domain = parts.length > 1 ? parts[1] : "example.com";

        // Generate new local part
        String newLocal;
        if (localPart.contains(".")) {
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            newLocal = firstName + "." + lastName;
        } else {
            newLocal = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] +
                      String.format("%02d", userCounter++);
        }

        // Try to preserve domain structure or use generic one
        String newDomain;
        if (domain.contains(".")) {
            String[] domainParts = domain.split("\\.");
            String tld = domainParts[domainParts.length - 1];
            String company = COMPANIES[random.nextInt(COMPANIES.length)];
            newDomain = company + "." + (DOMAINS[0].equals(tld) || DOMAINS[1].equals(tld) ||
                                        DOMAINS[2].equals(tld) || DOMAINS[3].equals(tld) ? tld : "com");
        } else {
            newDomain = COMPANIES[random.nextInt(COMPANIES.length)] + ".com";
        }

        String generated = newLocal + "@" + newDomain;
        emailCache.put(original, generated);
        return generated;
    }

    /**
     * Generate a realistic file path.
     * Preserves path structure and format.
     * Uses realistic user folder names from the name pool.
     */
    public String generatePath(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        // Check cache
        String cached = pathCache.get(original);
        if (cached != null) {
            return cached;
        }

        String generated;

        // Detect path type
        if (original.startsWith("/Users/") || original.startsWith("/home/")) {
            // Unix/Mac home directory
            String prefix = original.startsWith("/Users/") ? "/Users/" : "/home/";
            String remainder = original.substring(prefix.length());
            String[] parts = remainder.split("/", 2);
            String username = parts[0];
            String restOfPath = parts.length > 1 ? "/" + parts[1] : "";

            String newUsername = generateUserFolder(username);
            generated = prefix + newUsername + restOfPath;
        } else if (original.matches("(?i)C:\\\\Users\\\\.*")) {
            // Windows home directory
            String remainder = original.substring("C:\\Users\\".length());
            String[] parts = remainder.split("\\\\", 2);
            String username = parts[0];
            String restOfPath = parts.length > 1 ? "\\" + parts[1] : "";

            // Capitalize first letter for Windows
            String newUsername = generateUserFolder(username);
            newUsername = newUsername.substring(0, 1).toUpperCase() + newUsername.substring(1);
            generated = "C:\\Users\\" + newUsername + restOfPath;
        } else {
            // Generic path - just replace what looks like usernames
            generated = original;
            for (String name : FIRST_NAMES) {
                if (original.toLowerCase().contains(name)) {
                    String replacement = generateUserFolder(name);
                    generated = generated.replaceAll("(?i)" + name, replacement);
                    break;
                }
            }
        }

        pathCache.put(original, generated);
        return generated;
    }

    /**
     * Generate a realistic replacement based on the type of data detected.
     * Automatically detects emails, paths, and usernames.
     */
    public String generateReplacement(String original) {
        if (original == null || original.isEmpty()) {
            return original;
        }

        // Email
        if (original.contains("@") && original.contains(".")) {
            return generateEmail(original);
        }

        // File path
        if (original.contains("/") || original.contains("\\")) {
            return generatePath(original);
        }

        // Username
        return generateUsername(original);
    }

    /**
     * Clear all caches (useful for testing)
     */
    public void clearCache() {
        nameCache.clear();
        emailCache.clear();
        pathCache.clear();
        userFolderCache.clear();
        userCounter = 1;
        nameIndex = 0;
    }
}