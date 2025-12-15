package me.bechberger.jfrredact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.bechberger.jfrredact.config.RedactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads and resolves RedactionConfig with parent inheritance support.
 *
 * Supports:
 * - Loading from preset names (default, strict)
 * - Loading from file paths
 * - Loading from URLs (http://, https://, file://)
 * - Resolving parent configurations recursively (including URL parents)
 * - Preventing circular dependencies
 */
public class ConfigLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, RedactionConfig> loadedConfigs = new HashMap<>();
    private final Map<String, Boolean> loadingStack = new HashMap<>();

    public ConfigLoader() {
        // Configure mapper to provide better error messages
        yamlMapper.findAndRegisterModules();
    }

    /**
     * Load configuration from a preset name, file path, or URL.
     * Resolves parent configurations recursively.
     *
     * @param source Preset name (default, strict), file path, or URL (http://, https://, file://)
     * @return Fully resolved configuration
     * @throws IOException If file/URL cannot be read
     * @throws IllegalArgumentException If circular dependency detected
     */
    public RedactionConfig load(String source) throws IOException {
        if (source == null || source.equals("none")) {
            logger.debug("Loading empty configuration (no parent)");
            return new RedactionConfig();
        }

        logger.debug("Loading configuration from: {}", source);

        // Check if already loaded
        if (loadedConfigs.containsKey(source)) {
            logger.debug("Using cached configuration for: {}", source);
            return loadedConfigs.get(source);
        }

        // Detect circular dependencies
        if (loadingStack.getOrDefault(source, false)) {
            String chain = loadingStack.keySet().stream()
                .filter(loadingStack::get)
                .collect(Collectors.joining(" -> "));
            throw new IllegalArgumentException(
                "Circular dependency detected in configuration inheritance.\n" +
                "Loading chain: " + chain + " -> " + source + "\n" +
                "Please check your 'parent' fields to ensure no circular references."
            );
        }

        loadingStack.put(source, true);

        try {
            RedactionConfig config = loadRaw(source);

            // Resolve parent if specified
            String parent = config.getParent();
            if (parent != null && !parent.equals("none")) {
                logger.debug("Resolving parent configuration: {}", parent);
                try {
                    RedactionConfig parentConfig = load(parent);
                    config.mergeWith(parentConfig);
                } catch (IOException e) {
                    throw new ConfigurationException(
                        "Failed to load parent configuration: " + parent + "\n" +
                        "Referenced from: " + source + "\n" +
                        "Error: " + e.getMessage(),
                        e
                    );
                }
            }

            loadedConfigs.put(source, config);
            logger.info("Successfully loaded configuration from: {}", source);
            return config;
        } finally {
            loadingStack.put(source, false);
        }
    }

    /**
     * Load raw configuration without resolving parent.
     */
    private RedactionConfig loadRaw(String source) throws IOException {
        // Check if it's a preset
        Preset preset = Preset.fromName(source);
        if (preset != null) {
            return loadPreset(preset);
        }

        // Check if it's a URL
        if (isUrl(source)) {
            return loadFromUrl(source);
        }

        // Otherwise, treat as file path
        return loadFromFile(new File(source));
    }

    /**
     * Check if a source string is a URL.
     */
    private boolean isUrl(String source) {
        if (source == null) {
            return false;
        }
        String lower = source.toLowerCase();
        return lower.startsWith("http://") ||
               lower.startsWith("https://") ||
               lower.startsWith("file://");
    }

    /**
     * Load configuration from a URL.
     */
    private RedactionConfig loadFromUrl(String urlString) throws IOException {
        logger.debug("Loading configuration from URL: {}", urlString);

        try {
            URI uri = new URI(urlString);
            URL url = uri.toURL();
            URLConnection connection = url.openConnection();

            // Set reasonable timeouts (10 seconds)
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (InputStream is = connection.getInputStream()) {
                RedactionConfig config = yamlMapper.readValue(is, RedactionConfig.class);
                logger.debug("Successfully loaded configuration from URL");
                return config;
            }
        } catch (UnrecognizedPropertyException e) {
            throw new ConfigurationException(
                "Invalid configuration property in URL: " + urlString + "\n" +
                "Unknown property: '" + e.getPropertyName() + "'\n" +
                "At: " + e.getLocation() + "\n" +
                "Please check the configuration file for typos or refer to config-template.yaml for valid properties."
            );
        } catch (java.net.MalformedURLException e) {
            throw new ConfigurationException(
                "Invalid URL format: " + urlString + "\n" +
                "Error: " + e.getMessage() + "\n" +
                "Supported URL formats: http://, https://, file://",
                e
            );
        } catch (java.net.UnknownHostException e) {
            throw new ConfigurationException(
                "Cannot resolve hostname in URL: " + urlString + "\n" +
                "Host: " + e.getMessage() + "\n" +
                "Please check your internet connection and verify the URL is correct.",
                e
            );
        } catch (java.net.SocketTimeoutException e) {
            throw new ConfigurationException(
                "Timeout while loading configuration from URL: " + urlString + "\n" +
                "The server took too long to respond (timeout: 10 seconds).\n" +
                "Please check your internet connection or try again later.",
                e
            );
        } catch (Exception e) {
            throw new ConfigurationException(
                "Failed to load configuration from URL: " + urlString + "\n" +
                "Error: " + e.getMessage() + "\n" +
                "Please check that the URL is accessible and contains valid YAML configuration.",
                e
            );
        }
    }

    /**
     * Load configuration from a preset.
     */
    private RedactionConfig loadPreset(Preset preset) throws IOException {
        String presetPath = String.format("/presets/%s.yaml", preset.getName());
        logger.debug("Loading preset from resource: {}", presetPath);

        try (InputStream is = getClass().getResourceAsStream(presetPath)) {
            if (is == null) {
                throw new ConfigurationException(
                    "Preset not found: " + preset.getName() + "\n" +
                    "Available presets: default, strict\n" +
                    "Please check the preset name or create a custom configuration file."
                );
            }
            RedactionConfig config = yamlMapper.readValue(is, RedactionConfig.class);
            logger.debug("Successfully loaded preset: {}", preset.getName());
            return config;
        } catch (UnrecognizedPropertyException e) {
            throw new ConfigurationException(
                "Invalid property in preset '" + preset.getName() + "': " + e.getPropertyName() + "\n" +
                "This is likely a bug in the preset definition. Please report this issue."
            );
        } catch (IOException e) {
            if (e instanceof ConfigurationException) {
                throw e;
            }
            throw new ConfigurationException(
                "Failed to parse preset: " + preset.getName() + "\n" +
                "Error: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Load configuration from a file.
     */
    private RedactionConfig loadFromFile(File file) throws IOException {
        logger.debug("Loading configuration from file: {}", file.getAbsolutePath());

        if (!file.exists()) {
            throw new ConfigurationException(
                "Configuration file not found: " + file.getAbsolutePath() + "\n" +
                "Please check the file path and ensure the file exists.\n" +
                "You can create a configuration file using config-template.yaml as a starting point."
            );
        }

        if (!file.canRead()) {
            throw new ConfigurationException(
                "Configuration file is not readable: " + file.getAbsolutePath() + "\n" +
                "Please check file permissions."
            );
        }

        if (file.length() == 0) {
            throw new ConfigurationException(
                "Configuration file is empty: " + file.getAbsolutePath() + "\n" +
                "Please add configuration content or use a preset instead."
            );
        }

        try {
            RedactionConfig config = yamlMapper.readValue(file, RedactionConfig.class);
            logger.debug("Successfully loaded configuration from file");
            return config;
        } catch (UnrecognizedPropertyException e) {
            String nearbyText = extractNearbyText(file, e.getLocation().getLineNr());
            throw new ConfigurationException(
                "Invalid configuration property in file: " + file.getAbsolutePath() + "\n" +
                "Unknown property: '" + e.getPropertyName() + "' at line " + e.getLocation().getLineNr() + "\n" +
                nearbyText +
                "Please refer to config-template.yaml for valid properties and check for typos."
            );
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            throw new ConfigurationException(
                "YAML syntax error in file: " + file.getAbsolutePath() + "\n" +
                "Line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + "\n" +
                "Error: " + e.getOriginalMessage() + "\n" +
                "Common issues:\n" +
                "  - Incorrect indentation (YAML requires consistent spacing)\n" +
                "  - Missing colon after property name\n" +
                "  - Unquoted special characters\n" +
                "  - Tabs instead of spaces (use spaces for indentation)"
            );
        } catch (IOException e) {
            if (e instanceof ConfigurationException) {
                throw e;
            }
            throw new ConfigurationException(
                "Failed to load configuration from file: " + file.getAbsolutePath() + "\n" +
                "Error: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * Extract nearby text from file for better error context.
     */
    private String extractNearbyText(File file, int errorLine) {
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            if (errorLine > 0 && errorLine <= lines.size()) {
                int start = Math.max(0, errorLine - 3);
                int end = Math.min(lines.size(), errorLine + 2);
                StringBuilder context = new StringBuilder("\nNear line " + errorLine + ":\n");
                for (int i = start; i < end; i++) {
                    String prefix = (i == errorLine - 1) ? ">>> " : "    ";
                    context.append(String.format("%s%4d: %s\n", prefix, i + 1, lines.get(i)));
                }
                return context.toString();
            }
        } catch (Exception ignored) {
            // If we can't read the file, just skip the context
        }
        return "";
    }

    /**
     * Custom exception for configuration errors with helpful messages.
     */
    public static class ConfigurationException extends IOException {
        public ConfigurationException(String message) {
            super(message);
        }

        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Clear the loaded configuration cache.
     */
    public void clearCache() {
        loadedConfigs.clear();
        loadingStack.clear();
    }

    /**
     * Load raw YAML string from a preset or file.
     * Useful for generating configuration templates.
     *
     * @param source Preset name or file path
     * @return Raw YAML string
     */
    public String loadRawYaml(String source) throws IOException {
        // Check if it's a preset
        Preset preset = Preset.fromName(source);
        if (preset != null) {
            String presetPath = String.format("/presets/%s.yaml", preset.getName());
            try (InputStream is = getClass().getResourceAsStream(presetPath)) {
                if (is == null) {
                    throw new ConfigurationException(
                        "Preset not found: " + preset.getName()
                    );
                }
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        // Otherwise, treat as file path
        File file = new File(source);
        if (!file.exists()) {
            throw new ConfigurationException("File not found: " + file.getAbsolutePath());
        }
        return java.nio.file.Files.readString(file.toPath());
    }
}