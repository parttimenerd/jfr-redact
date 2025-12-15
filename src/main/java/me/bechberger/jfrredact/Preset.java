package me.bechberger.jfrredact;

/**
 * Predefined configuration presets for JFR redaction.
 */
public enum Preset {
    /**
     * Default preset - balanced security and utility (recommended for most cases)
     */
    DEFAULT("default", "Balanced security and utility (recommended for most cases)"),

    /**
     * Strict preset - maximum redaction for highly sensitive data
     */
    STRICT("strict", "Maximum redaction for highly sensitive data"),

    /**
     * hs_err preset - optimized for Java crash reports (hs_err_pid*.log)
     */
    HSERR("hserr", "Optimized for Java crash reports (hs_err_pid*.log)");

    private final String name;
    private final String description;

    Preset(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Get the preset name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the preset description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get preset by name (case-insensitive)
     *
     * @param name The preset name
     * @return The preset, or null if not found
     */
    public static Preset fromName(String name) {
        if (name == null) {
            return null;
        }
        for (Preset preset : values()) {
            if (preset.name.equalsIgnoreCase(name)) {
                return preset;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}