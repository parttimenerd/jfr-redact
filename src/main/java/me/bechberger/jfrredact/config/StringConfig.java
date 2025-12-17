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

    @JsonProperty("no_redact")
    private List<String> noRedact = new ArrayList<>();

    /**
     * Discovery settings for a pattern type.
     * Controls how values are extracted and redacted everywhere they appear.
     */
    public static class DiscoverySettings {
        /**
         * Enable pattern discovery for this pattern type.
         * When true, values matching this pattern will be discovered and then redacted everywhere.
         */
        @JsonProperty("enabled")
        private boolean enabled = true;

        /**
         * Which capture group to extract for pattern discovery.
         * 0 = entire match, 1 = first capture group, 2 = second capture group, etc.
         */
        @JsonProperty("capture_group")
        private int captureGroup = 1;

        /**
         * Minimum occurrences required before a discovered value is redacted.
         * Helps prevent false positives from generic/common values.
         */
        @JsonProperty("min_occurrences")
        private int minOccurrences = 1;

        /**
         * Case sensitivity for discovered value matching.
         * If false, "Bob", "bob", and "BOB" are treated as the same value.
         */
        @JsonProperty("case_sensitive")
        private boolean caseSensitive = false;

        /**
         * Whitelist of values that should never be discovered/redacted by this pattern.
         * Useful for common/generic values like "root", "admin", "test".
         */
        @JsonProperty("whitelist")
        private List<String> whitelist = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getCaptureGroup() { return captureGroup; }
        public void setCaptureGroup(int captureGroup) { this.captureGroup = captureGroup; }

        public int getMinOccurrences() { return minOccurrences; }
        public void setMinOccurrences(int minOccurrences) {
            this.minOccurrences = Math.max(1, minOccurrences);
        }

        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

        public List<String> getWhitelist() { return whitelist; }
        public void setWhitelist(List<String> whitelist) { this.whitelist = whitelist; }
    }

    /**
     * Base class for pattern configurations with ignore capabilities
     */
    public static abstract class BasePatternConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

        /**
         * Discovery settings block. Presence enables discovery.
         */
        @JsonProperty("discovery")
        private DiscoverySettings discovery = new DiscoverySettings();


        /**
         * Specific verbatim values that should not be redacted
         */
        @JsonProperty("ignore_exact")
        private List<String> ignoreExact = new ArrayList<>();

        /**
         * Patterns that match values that should not be redacted
         */
        @JsonProperty("ignore")
        private List<String> ignore = new ArrayList<>();

        /**
         * Prefix expressions that come directly before - if matched, don't redact
         */
        @JsonProperty("ignore_after")
        private List<String> ignoreAfter = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public DiscoverySettings getDiscovery() { return discovery; }
        public void setDiscovery(DiscoverySettings discovery) { this.discovery = discovery; }

        // Convenience methods that delegate to discovery block
        public boolean isEnableDiscovery() { return discovery.isEnabled(); }
        public void setEnableDiscovery(boolean enableDiscovery) { discovery.setEnabled(enableDiscovery); }

        public int getDiscoveryCaptureGroup() { return discovery.getCaptureGroup(); }
        public void setDiscoveryCaptureGroup(int captureGroup) { discovery.setCaptureGroup(captureGroup); }

        public int getDiscoveryMinOccurrences() { return discovery.getMinOccurrences(); }
        public void setDiscoveryMinOccurrences(int minOccurrences) { discovery.setMinOccurrences(minOccurrences); }

        public boolean isDiscoveryCaseSensitive() { return discovery.isCaseSensitive(); }
        public void setDiscoveryCaseSensitive(boolean caseSensitive) { discovery.setCaseSensitive(caseSensitive); }

        public List<String> getDiscoveryWhitelist() { return discovery.getWhitelist(); }
        public void setDiscoveryWhitelist(List<String> whitelist) { discovery.setWhitelist(whitelist); }

        public List<String> getIgnoreExact() { return ignoreExact; }
        public void setIgnoreExact(List<String> ignoreExact) { this.ignoreExact = ignoreExact; }

        public List<String> getIgnore() { return ignore; }
        public void setIgnore(List<String> ignore) { this.ignore = ignore; }

        public List<String> getIgnoreAfter() { return ignoreAfter; }
        public void setIgnoreAfter(List<String> ignoreAfter) { this.ignoreAfter = ignoreAfter; }
    }

    /**
     * Configuration for various string patterns to redact
     */
    public static class PatternsConfig {
        @JsonProperty("user")
        private UserConfig user = new UserConfig();

        @JsonProperty("emails")
        private EmailsConfig emails = new EmailsConfig();

        @JsonProperty("uuids")
        private UuidsConfig uuids = new UuidsConfig();

        @JsonProperty("ip_addresses")
        private IpAddressesConfig ipAddresses = new IpAddressesConfig();

        @JsonProperty("ssh_hosts")
        private SshHostsConfig sshHosts = new SshHostsConfig();

        @JsonProperty("hostnames")
        private HostnamesConfig hostnames = new HostnamesConfig();

        @JsonProperty("internal_urls")
        private InternalUrlsConfig internalUrls = new InternalUrlsConfig();

        @JsonProperty("custom")
        private List<CustomPatternConfig> custom = new ArrayList<>();

        // Getters and setters
        public UserConfig getUser() {
            return user;
        }

        public void setUser(UserConfig user) {
            this.user = user;
        }

        public EmailsConfig getEmails() { return emails; }
        public void setEmails(EmailsConfig emails) { this.emails = emails; }

        public UuidsConfig getUuids() { return uuids; }
        public void setUuids(UuidsConfig uuids) { this.uuids = uuids; }

        public IpAddressesConfig getIpAddresses() { return ipAddresses; }
        public void setIpAddresses(IpAddressesConfig ipAddresses) { this.ipAddresses = ipAddresses; }

        public SshHostsConfig getSshHosts() { return sshHosts; }
        public void setSshHosts(SshHostsConfig sshHosts) { this.sshHosts = sshHosts; }

        public HostnamesConfig getHostnames() { return hostnames; }
        public void setHostnames(HostnamesConfig hostnames) { this.hostnames = hostnames; }

        public InternalUrlsConfig getInternalUrls() { return internalUrls; }
        public void setInternalUrls(InternalUrlsConfig internalUrls) { this.internalUrls = internalUrls; }

        public List<CustomPatternConfig> getCustom() { return custom; }
        public void setCustom(List<CustomPatternConfig> custom) { this.custom = custom; }
    }

    /**
     * User name pattern configuration (from user directories).
     * Default patterns are defined in default.yaml preset.
     */
    public static class UserConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * Email pattern configuration.
     * Default patterns are defined in default.yaml preset.
     */
    public static class EmailsConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * UUID pattern configuration.
     * Default patterns are defined in default.yaml preset.
     */
    public static class UuidsConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public UuidsConfig() {
            setEnabled(false);
        }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * IP address pattern configuration.
     * Default patterns are defined in default.yaml preset.
     */
    public static class IpAddressesConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * SSH host pattern configuration.
     * Default patterns are defined in default.yaml preset.
     */
    public static class SshHostsConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public SshHostsConfig() {
            setEnabled(false);
        }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * Hostname pattern configuration.
     * Default patterns are defined in default.yaml preset.
     */
    public static class HostnamesConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public HostnamesConfig() {
            setEnabled(false);
            getDiscovery().setCaptureGroup(0);  // Use entire match
        }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * Internal/corporate URL pattern configuration.
     * Default patterns are defined in default.yaml preset.
     */
    public static class InternalUrlsConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        @JsonProperty("internal_domains")
        private List<String> internalDomains = new ArrayList<>();

        public InternalUrlsConfig() {
            setEnabled(false);
        }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }

        public List<String> getInternalDomains() { return internalDomains; }
        public void setInternalDomains(List<String> internalDomains) { this.internalDomains = internalDomains; }
    }

    /**
     * Custom pattern configuration
     */
    public static class CustomPatternConfig extends BasePatternConfig {
        @JsonProperty("name")
        private String name;

        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
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

    public List<String> getNoRedact() { return noRedact; }
    public void setNoRedact(List<String> noRedact) { this.noRedact = noRedact; }

    /**
     * Merge with parent configuration
     */
    public void mergeWith(StringConfig parent) {
        if (parent == null) return;

        // Merge no_redact list
        for (String str : parent.getNoRedact()) {
            if (!noRedact.contains(str)) {
                noRedact.add(str);
            }
        }

        // Merge user patterns
        for (String regex : parent.getPatterns().getUser().getPatterns()) {
            if (!patterns.getUser().getPatterns().contains(regex)) {
                patterns.getUser().getPatterns().add(regex);
            }
        }

        // Merge email patterns
        for (String pattern : parent.getPatterns().getEmails().getPatterns()) {
            if (!patterns.getEmails().getPatterns().contains(pattern)) {
                patterns.getEmails().getPatterns().add(pattern);
            }
        }

        // Merge UUID patterns
        for (String pattern : parent.getPatterns().getUuids().getPatterns()) {
            if (!patterns.getUuids().getPatterns().contains(pattern)) {
                patterns.getUuids().getPatterns().add(pattern);
            }
        }

        // Merge IP address patterns
        for (String pattern : parent.getPatterns().getIpAddresses().getPatterns()) {
            if (!patterns.getIpAddresses().getPatterns().contains(pattern)) {
                patterns.getIpAddresses().getPatterns().add(pattern);
            }
        }

        // Merge SSH host patterns
        for (String pattern : parent.getPatterns().getSshHosts().getPatterns()) {
            if (!patterns.getSshHosts().getPatterns().contains(pattern)) {
                patterns.getSshHosts().getPatterns().add(pattern);
            }
        }

        // Merge hostname patterns
        for (String pattern : parent.getPatterns().getHostnames().getPatterns()) {
            if (!patterns.getHostnames().getPatterns().contains(pattern)) {
                patterns.getHostnames().getPatterns().add(pattern);
            }
        }

        // Merge internal URL patterns
        for (String pattern : parent.getPatterns().getInternalUrls().getPatterns()) {
            if (!patterns.getInternalUrls().getPatterns().contains(pattern)) {
                patterns.getInternalUrls().getPatterns().add(pattern);
            }
        }

        // Merge internal domains
        for (String domain : parent.getPatterns().getInternalUrls().getInternalDomains()) {
            if (!patterns.getInternalUrls().getInternalDomains().contains(domain)) {
                patterns.getInternalUrls().getInternalDomains().add(domain);
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