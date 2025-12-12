package me.bechberger.jfrredact.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for string pattern redaction.
 */
public class StringConfig {

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("redact_in_method_names")
    private boolean redactInMethodNames = false;

    @JsonProperty("redact_in_class_names")
    private boolean redactInClassNames = false;

    @JsonProperty("redact_in_thread_names")
    private boolean redactInThreadNames = false;

    @JsonProperty("patterns")
    private PatternsConfig patterns = new PatternsConfig();

    /**
     * Configuration for various string patterns to redact
     */
    public static class PatternsConfig {
        @JsonProperty("home_directories")
        private HomeDirectoriesConfig homeDirectories = new HomeDirectoriesConfig();

        @JsonProperty("emails")
        private EmailsConfig emails = new EmailsConfig();

        @JsonProperty("uuids")
        private UuidsConfig uuids = new UuidsConfig();

        @JsonProperty("ip_addresses")
        private IpAddressesConfig ipAddresses = new IpAddressesConfig();

        @JsonProperty("ssh_hosts")
        private SshHostsConfig sshHosts = new SshHostsConfig();

        @JsonProperty("custom")
        private List<CustomPatternConfig> custom = new ArrayList<>();

        // Getters and setters
        public HomeDirectoriesConfig getHomeDirectories() { return homeDirectories; }
        public void setHomeDirectories(HomeDirectoriesConfig homeDirectories) {
            this.homeDirectories = homeDirectories;
        }

        public EmailsConfig getEmails() { return emails; }
        public void setEmails(EmailsConfig emails) { this.emails = emails; }

        public UuidsConfig getUuids() { return uuids; }
        public void setUuids(UuidsConfig uuids) { this.uuids = uuids; }

        public IpAddressesConfig getIpAddresses() { return ipAddresses; }
        public void setIpAddresses(IpAddressesConfig ipAddresses) { this.ipAddresses = ipAddresses; }

        public SshHostsConfig getSshHosts() { return sshHosts; }
        public void setSshHosts(SshHostsConfig sshHosts) { this.sshHosts = sshHosts; }

        public List<CustomPatternConfig> getCustom() { return custom; }
        public void setCustom(List<CustomPatternConfig> custom) { this.custom = custom; }
    }

    /**
     * Home directory pattern configuration
     */
    public static class HomeDirectoriesConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

        @JsonProperty("regexes")
        private List<String> regexes = new ArrayList<>(List.of(
            "/Users/[^/]+",
            "C:\\\\Users\\\\[a-zA-Z0-9_\\-]+",
            "/home/[^/]+"
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getRegexes() { return regexes; }
        public void setRegexes(List<String> regexes) { this.regexes = regexes; }
    }

    /**
     * Email pattern configuration
     */
    public static class EmailsConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

        @JsonProperty("regex")
        private String regex = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
    }

    /**
     * UUID pattern configuration
     */
    public static class UuidsConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;

        @JsonProperty("regex")
        private String regex = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
    }

    /**
     * IP address pattern configuration
     */
    public static class IpAddressesConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

        @JsonProperty("ipv4")
        private String ipv4 = "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b";

        @JsonProperty("ipv6")
        private String ipv6 = "\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getIpv4() { return ipv4; }
        public void setIpv4(String ipv4) { this.ipv4 = ipv4; }

        public String getIpv6() { return ipv6; }
        public void setIpv6(String ipv6) { this.ipv6 = ipv6; }
    }

    /**
     * SSH host pattern configuration
     */
    public static class SshHostsConfig {
        @JsonProperty("enabled")
        private boolean enabled = false;

        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>(List.of(
            "ssh://[a-zA-Z0-9.-]+",
            "(?:ssh|sftp)://(?:[^@]+@)?[a-zA-Z0-9.-]+",
            "[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+(?::[0-9]+)?",
            "(?<=ssh\\s)[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+"
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * Custom pattern configuration
     */
    public static class CustomPatternConfig {
        @JsonProperty("name")
        private String name;

        @JsonProperty("regex")
        private String regex;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
    }

    // Getters and setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isRedactInMethodNames() { return redactInMethodNames; }
    public void setRedactInMethodNames(boolean redactInMethodNames) {
        this.redactInMethodNames = redactInMethodNames;
    }

    public boolean isRedactInClassNames() { return redactInClassNames; }
    public void setRedactInClassNames(boolean redactInClassNames) {
        this.redactInClassNames = redactInClassNames;
    }

    public boolean isRedactInThreadNames() { return redactInThreadNames; }
    public void setRedactInThreadNames(boolean redactInThreadNames) {
        this.redactInThreadNames = redactInThreadNames;
    }

    public PatternsConfig getPatterns() { return patterns; }
    public void setPatterns(PatternsConfig patterns) { this.patterns = patterns; }

    /**
     * Merge with parent configuration
     */
    public void mergeWith(StringConfig parent) {
        if (parent == null) return;

        // Merge home directory regexes
        for (String regex : parent.getPatterns().getHomeDirectories().getRegexes()) {
            if (!patterns.getHomeDirectories().getRegexes().contains(regex)) {
                patterns.getHomeDirectories().getRegexes().add(regex);
            }
        }

        // Merge SSH host patterns
        for (String pattern : parent.getPatterns().getSshHosts().getPatterns()) {
            if (!patterns.getSshHosts().getPatterns().contains(pattern)) {
                patterns.getSshHosts().getPatterns().add(pattern);
            }
        }

        // Merge custom patterns
        for (CustomPatternConfig customPattern : parent.getPatterns().getCustom()) {
            // Check if pattern with same name exists
            boolean exists = patterns.getCustom().stream()
                .anyMatch(p -> p.getName() != null && p.getName().equals(customPattern.getName()));
            if (!exists) {
                patterns.getCustom().add(customPattern);
            }
        }
    }
}