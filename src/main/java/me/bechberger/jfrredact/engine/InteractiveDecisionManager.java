package me.bechberger.jfrredact.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages interactive decisions for redaction patterns.
 * Prompts user for decisions and stores them in a decisions file for future automatic use.
 */
public class InteractiveDecisionManager {

    private static final Logger logger = LoggerFactory.getLogger(InteractiveDecisionManager.class);

    private final BufferedReader reader;
    private final PrintWriter writer;
    private final Path decisionsFile;
    private final DecisionsStorage storage;
    private boolean interactive;

    /**
     * Storage structure for decisions
     */
    public static class DecisionsStorage {
        @JsonProperty("version")
        private String version = "1.0";

        @JsonProperty("usernames")
        private Map<String, Decision> usernames = new HashMap<>();

        @JsonProperty("hostnames")
        private Map<String, Decision> hostnames = new HashMap<>();

        @JsonProperty("folders")
        private Map<String, Decision> folders = new HashMap<>();

        @JsonProperty("custom_patterns")
        private Map<String, Map<String, Decision>> customPatterns = new HashMap<>();

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public Map<String, Decision> getUsernames() { return usernames; }
        public void setUsernames(Map<String, Decision> usernames) { this.usernames = usernames; }

        public Map<String, Decision> getHostnames() { return hostnames; }
        public void setHostnames(Map<String, Decision> hostnames) { this.hostnames = hostnames; }

        public Map<String, Decision> getFolders() { return folders; }
        public void setFolders(Map<String, Decision> folders) { this.folders = folders; }

        public Map<String, Map<String, Decision>> getCustomPatterns() { return customPatterns; }
        public void setCustomPatterns(Map<String, Map<String, Decision>> customPatterns) {
            this.customPatterns = customPatterns;
        }
    }

    /**
     * Decision for a specific value
     */
    public static class Decision {
        @JsonProperty("action")
        private DecisionAction action;

        @JsonProperty("replacement")
        private String replacement;

        @JsonProperty("timestamp")
        private String timestamp;

        public Decision() {}

        public Decision(DecisionAction action, String replacement) {
            this.action = action;
            this.replacement = replacement;
            this.timestamp = new Date().toString();
        }

        public DecisionAction getAction() { return action; }
        public void setAction(DecisionAction action) { this.action = action; }

        public String getReplacement() { return replacement; }
        public void setReplacement(String replacement) { this.replacement = replacement; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    public enum DecisionAction {
        KEEP,           // Don't redact this value
        REDACT,         // Redact this value
        REPLACE,        // Replace with custom text
        KEEP_ALL,       // Keep all values of this type (set as global policy)
        REDACT_ALL      // Redact all values of this type (set as global policy)
    }

    public InteractiveDecisionManager(Path decisionsFile, boolean interactive) {
        this(decisionsFile, interactive, new BufferedReader(new InputStreamReader(System.in)),
             new PrintWriter(System.out, true));
    }

    public InteractiveDecisionManager(Path decisionsFile, boolean interactive,
                                     BufferedReader reader, PrintWriter writer) {
        this.decisionsFile = decisionsFile;
        this.interactive = interactive;
        this.reader = reader;
        this.writer = writer;
        this.storage = loadDecisions();
    }

    /**
     * Load existing decisions from file
     */
    private DecisionsStorage loadDecisions() {
        if (decisionsFile != null && Files.exists(decisionsFile)) {
            try {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                return mapper.readValue(decisionsFile.toFile(), DecisionsStorage.class);
            } catch (IOException e) {
                logger.warn("Failed to load decisions file: {}", e.getMessage());
            }
        }
        return new DecisionsStorage();
    }

    /**
     * Save decisions to file
     */
    public void saveDecisions() {
        if (decisionsFile == null) {
            return;
        }

        try {
            Files.createDirectories(decisionsFile.getParent());
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(decisionsFile.toFile(), storage);
            logger.info("Saved decisions to: {}", decisionsFile);
        } catch (IOException e) {
            logger.error("Failed to save decisions file: {}", e.getMessage());
        }
    }

    /**
     * Get decision for a discovered value, prompting user if in interactive mode
     */
    public Decision getDecision(DiscoveredPatterns.DiscoveredValue value) {
        String normalizedValue = value.getValue().toLowerCase(Locale.ROOT);
        Map<String, Decision> categoryMap = getCategoryMap(value.getType(), value.getCustomTypeName());

        // Check if we already have a decision
        Decision existing = categoryMap.get(normalizedValue);
        if (existing != null) {
            return existing;
        }

        // If not interactive, default to REDACT
        if (!interactive) {
            return new Decision(DecisionAction.REDACT, null);
        }

        // Prompt user for decision
        Decision decision = promptUser(value);
        categoryMap.put(normalizedValue, decision);

        // Handle global policies
        // This is handled at a higher level, but we store it here too

        return decision;
    }

    /**
     * Get the appropriate category map for a pattern type
     */
    private Map<String, Decision> getCategoryMap(DiscoveredPatterns.PatternType type, String customName) {
        return switch (type) {
            case USERNAME -> storage.getUsernames();
            case HOSTNAME -> storage.getHostnames();
            case EMAIL_LOCAL_PART -> storage.getUsernames(); // Treat email local parts as usernames
            case CUSTOM -> {
                String key = customName != null ? customName : "default";
                yield storage.getCustomPatterns().computeIfAbsent(key, k -> new HashMap<>());
            }
        };
    }

    /**
     * Get category map for folders (special handling for base folder extraction)
     */
    private Map<String, Decision> getFolderDecisions() {
        return storage.getFolders();
    }

    /**
     * Prompt user for a decision about a discovered value
     */
    private Decision promptUser(DiscoveredPatterns.DiscoveredValue value) {
        writer.println();
        writer.println("═══════════════════════════════════════════════════════════════");
        writer.printf("Discovered %s: '%s'%n", getTypeDescription(value), value.getValue());
        writer.printf("Occurrences: %d%n", value.getOccurrences());
        writer.println("───────────────────────────────────────────────────────────────");
        writer.println("What should be done with this value?");
        writer.println("  [k] keep      - Keep this specific value unredacted");
        writer.println("  [r] redact    - Redact this specific value (default)");
        writer.println("  [p] replace   - Replace with custom text");
        writer.println("  [K] keep all  - Keep all " + getTypeDescription(value) + "s");
        writer.println("  [R] redact all- Redact all " + getTypeDescription(value) + "s");
        writer.println("───────────────────────────────────────────────────────────────");
        writer.print("Your choice [k/r/p/K/R] (default: r): ");
        writer.flush();

        try {
            String input = reader.readLine();
            if (input == null) {
                return new Decision(DecisionAction.REDACT, null);
            }

            input = input.trim();

            // Check for uppercase global options first before lowercasing
            if (input.equals("K")) {
                writer.println("→ Will keep all " + getTypeDescription(value) + "s");
                return new Decision(DecisionAction.KEEP_ALL, null);
            } else if (input.equals("R")) {
                writer.println("→ Will redact all " + getTypeDescription(value) + "s");
                return new Decision(DecisionAction.REDACT_ALL, null);
            }

            // Handle lowercase options
            return switch (input.toLowerCase()) {
                case "k" -> new Decision(DecisionAction.KEEP, null);
                case "r", "" -> new Decision(DecisionAction.REDACT, null);
                case "p" -> promptForReplacement(value);
                default -> {
                    writer.println("Invalid choice, defaulting to redact");
                    yield new Decision(DecisionAction.REDACT, null);
                }
            };
        } catch (IOException e) {
            logger.error("Failed to read user input: {}", e.getMessage());
            return new Decision(DecisionAction.REDACT, null);
        }
    }

    /**
     * Prompt user for replacement text
     */
    private Decision promptForReplacement(DiscoveredPatterns.DiscoveredValue value) {
        writer.print("Enter replacement text for '" + value.getValue() + "': ");
        writer.flush();

        try {
            String replacement = reader.readLine();
            if (replacement == null || replacement.trim().isEmpty()) {
                writer.println("No replacement provided, defaulting to redact");
                return new Decision(DecisionAction.REDACT, null);
            }
            writer.println("→ Will replace '" + value.getValue() + "' with '" + replacement.trim() + "'");
            return new Decision(DecisionAction.REPLACE, replacement.trim());
        } catch (IOException e) {
            logger.error("Failed to read replacement text: {}", e.getMessage());
            return new Decision(DecisionAction.REDACT, null);
        }
    }

    /**
     * Get a user-friendly description of a pattern type
     */
    private String getTypeDescription(DiscoveredPatterns.DiscoveredValue value) {
        if (value.getType() == DiscoveredPatterns.PatternType.CUSTOM && value.getCustomTypeName() != null) {
            return value.getCustomTypeName();
        }
        return switch (value.getType()) {
            case USERNAME -> "username";
            case HOSTNAME -> "hostname";
            case EMAIL_LOCAL_PART -> "email";
            case CUSTOM -> "custom pattern";
        };
    }

    /**
     * Get decision for a folder path (with base folder support)
     */
    public Decision getFolderDecision(String folder) {
        String normalizedFolder = normalizePath(folder);
        Map<String, Decision> folders = getFolderDecisions();

        // Check if we have a decision for this exact folder
        Decision existing = folders.get(normalizedFolder);
        if (existing != null) {
            return existing;
        }

        // Check parent folders (base folder matching)
        String current = normalizedFolder;
        while (current.contains("/")) {
            int lastSlash = current.lastIndexOf('/');
            current = current.substring(0, lastSlash);
            existing = folders.get(current);
            if (existing != null) {
                // Parent folder has a decision, apply it to this subfolder
                return existing;
            }
        }

        // If not interactive, default to REDACT
        if (!interactive) {
            return new Decision(DecisionAction.REDACT, null);
        }

        // Prompt user (starting with base folders)
        String baseFolder = extractBaseFolder(folder);
        if (baseFolder != null && !baseFolder.equals(normalizedFolder)) {
            Decision baseDecision = promptUserForFolder(baseFolder);
            folders.put(normalizePath(baseFolder), baseDecision);

            // Apply base folder decision to this specific folder
            if (baseDecision.getAction() != DecisionAction.REPLACE) {
                return baseDecision;
            }
        }

        // Prompt for this specific folder
        Decision decision = promptUserForFolder(folder);
        folders.put(normalizedFolder, decision);
        return decision;
    }

    /**
     * Extract base folder from a path (e.g., /home/user/documents -> /home/user)
     */
    private String extractBaseFolder(String path) {
        String normalized = normalizePath(path);
        int slashCount = 0;
        int lastSlash = -1;

        for (int i = 0; i < normalized.length(); i++) {
            if (normalized.charAt(i) == '/') {
                slashCount++;
                if (slashCount == 2) { // e.g., after "/home" we get "/home/user"
                    lastSlash = i;
                } else if (slashCount == 3) {
                    return normalized.substring(0, lastSlash);
                }
            }
        }

        return null; // Path too short to extract base
    }

    /**
     * Normalize a file path
     */
    private String normalizePath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("\\\\", "/");
    }

    /**
     * Prompt user for a decision about a folder
     */
    private Decision promptUserForFolder(String folder) {
        writer.println();
        writer.println("═══════════════════════��═══════════════════════════════════════");
        writer.printf("Discovered folder: '%s'%n", folder);
        writer.println("───────────────────────────────────────────────────────────────");
        writer.println("What should be done with this folder path?");
        writer.println("  [k] keep      - Keep this folder path unredacted");
        writer.println("  [r] redact    - Redact this folder path (default)");
        writer.println("  [p] replace   - Replace with custom path");
        writer.println("───────────────────────────────────────────────────────────────");
        writer.print("Your choice [k/r/p] (default: r): ");
        writer.flush();

        try {
            String input = reader.readLine();
            if (input == null) {
                return new Decision(DecisionAction.REDACT, null);
            }

            input = input.trim().toLowerCase();

            return switch (input) {
                case "k" -> new Decision(DecisionAction.KEEP, null);
                case "r", "" -> new Decision(DecisionAction.REDACT, null);
                case "p" -> {
                    writer.print("Enter replacement path for '" + folder + "': ");
                    writer.flush();
                    String replacement = reader.readLine();
                    if (replacement == null || replacement.trim().isEmpty()) {
                        writer.println("No replacement provided, defaulting to redact");
                        yield new Decision(DecisionAction.REDACT, null);
                    }
                    writer.println("→ Will replace '" + folder + "' with '" + replacement.trim() + "'");
                    yield new Decision(DecisionAction.REPLACE, replacement.trim());
                }
                default -> {
                    writer.println("Invalid choice, defaulting to redact");
                    yield new Decision(DecisionAction.REDACT, null);
                }
            };
        } catch (IOException e) {
            logger.error("Failed to read user input: {}", e.getMessage());
            return new Decision(DecisionAction.REDACT, null);
        }
    }

    public DecisionsStorage getStorage() {
        return storage;
    }

    public boolean isInteractive() {
        return interactive;
    }
}