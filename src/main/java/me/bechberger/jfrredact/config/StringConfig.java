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
     * Base class for pattern configurations with ignore capabilities
     */
    public static abstract class BasePatternConfig {
        @JsonProperty("enabled")
        private boolean enabled = true;

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

        @JsonProperty("hostnames")
        private HostnamesConfig hostnames = new HostnamesConfig();

        @JsonProperty("internal_urls")
        private InternalUrlsConfig internalUrls = new InternalUrlsConfig();

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

        public HostnamesConfig getHostnames() { return hostnames; }
        public void setHostnames(HostnamesConfig hostnames) { this.hostnames = hostnames; }

        public InternalUrlsConfig getInternalUrls() { return internalUrls; }
        public void setInternalUrls(InternalUrlsConfig internalUrls) { this.internalUrls = internalUrls; }

        public List<CustomPatternConfig> getCustom() { return custom; }
        public void setCustom(List<CustomPatternConfig> custom) { this.custom = custom; }
    }

    /**
     * Home directory pattern configuration
     */
    public static class HomeDirectoriesConfig extends BasePatternConfig {
        @JsonProperty("regexes")
        private List<String> regexes = new ArrayList<>(List.of(
            "/Users/[^/]+",
            "C:\\\\Users\\\\[a-zA-Z0-9_\\-]+",
            "/home/[^/]+"
        ));

        public List<String> getRegexes() { return regexes; }
        public void setRegexes(List<String> regexes) { this.regexes = regexes; }
    }

    /**
     * Email pattern configuration
     */
    public static class EmailsConfig extends BasePatternConfig {
        @JsonProperty("regex")
        private String regex = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
    }

    /**
     * UUID pattern configuration
     */
    public static class UuidsConfig extends BasePatternConfig {
        @JsonProperty("regex")
        private String regex = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

        public UuidsConfig() {
            setEnabled(false);
        }

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }
    }

    /**
     * IP address pattern configuration
     */
    public static class IpAddressesConfig extends BasePatternConfig {
        // IPv6 hex group pattern: 1-4 hexadecimal digits
        private static final String H = "[0-9a-fA-F]{1,4}";

        @JsonProperty("ipv4")
        private String ipv4 = "\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b";

        /**
         * IPv6 address regex pattern with full RFC 4291 support.
         *
         * <p>Supports all valid IPv6 formats including:
         * <ul>
         *   <li><b>Full format:</b> {@code 2001:0db8:85a3:0000:0000:8a2e:0370:7334}</li>
         *   <li><b>Compressed zeros:</b> {@code 2001:db8:85a3::8a2e:370:7334}</li>
         *   <li><b>Leading compression:</b> {@code ::8a2e:370:7334}</li>
         *   <li><b>Trailing compression:</b> {@code 2001:db8::}</li>
         *   <li><b>Loopback:</b> {@code ::1}</li>
         *   <li><b>Unspecified:</b> {@code ::}</li>
         *   <li><b>Link-local:</b> {@code fe80::1}</li>
         * </ul>
         *
         * <p>The regex handles the double-colon (::) notation which represents one or more
         * consecutive groups of zeros. This notation can appear at most once in an address.
         *
         * <p>Pattern breakdown:
         * <ul>
         *   <li>{@code (H:){7}H} - Full format with 8 groups</li>
         *   <li>{@code (H:){1,7}:} - 1-7 groups followed by ::</li>
         *   <li>{@code (H:){1,6}:H} - 1-6 groups, ::, then 1 group</li>
         *   <li>{@code (H:){1,5}(:H){1,2}} - Various compressed formats</li>
         *   <li>{@code :(((:H){1,7})|:)} - Leading :: formats</li>
         * </ul>
         * where H = [0-9a-fA-F]{1,4} (one to four hex digits)
         */
        @JsonProperty("ipv6")
        private String ipv6 = buildIpv6Regex();

        private static String buildIpv6Regex() {
            // Use negative lookahead/lookbehind to ensure we don't match partial addresses
            // that are part of a longer hex string
            String boundary = "(?![0-9a-fA-F:])";

            return "(?:" +
                "(?:" + H + ":){7}" + H + boundary + "|" +              // Full format: 1:2:3:4:5:6:7:8
                "(?:" + H + ":){1,7}:" + boundary + "|" +                // Trailing :: - 1:2:3:4:5:6:7::
                "(?:" + H + ":){1,6}:" + H + boundary + "|" +            // Mid compression - 1::8
                "(?:" + H + ":){1,5}(?::" + H + "){1,2}" + boundary + "|" +   // 1::7:8, 1:2::7:8, etc.
                "(?:" + H + ":){1,4}(?::" + H + "){1,3}" + boundary + "|" +   // 1::6:7:8, 1:2::6:7:8, etc.
                "(?:" + H + ":){1,3}(?::" + H + "){1,4}" + boundary + "|" +   // 1::5:6:7:8, etc.
                "(?:" + H + ":){1,2}(?::" + H + "){1,5}" + boundary + "|" +   // 1::4:5:6:7:8, etc.
                H + ":(?:(?::" + H + "){1,6})" + boundary + "|" +             // 1::3:4:5:6:7:8
                ":(?:(?::" + H + "){1,7}|:)" + boundary +                      // ::2:3:4:5:6:7:8, ::1, ::
            ")";
        }

        public String getIpv4() { return ipv4; }
        public void setIpv4(String ipv4) { this.ipv4 = ipv4; }

        public String getIpv6() { return ipv6; }
        public void setIpv6(String ipv6) { this.ipv6 = ipv6; }
    }

    /**
     * SSH host pattern configuration
     */
    public static class SshHostsConfig extends BasePatternConfig {
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>(List.of(
            "ssh://[a-zA-Z0-9.-]+",
            "(?:ssh|sftp)://(?:[^@]+@)?[a-zA-Z0-9.-]+",
            "[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+(?::[0-9]+)?",
            "(?<=ssh\\s)[a-zA-Z0-9_-]+@[a-zA-Z0-9.-]+"
        ));

        public SshHostsConfig() {
            setEnabled(false);
        }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * Hostname pattern configuration for corporate/internal hostnames.
     * Useful for hs_err files which contain "Host: hostname" lines.
     */
    public static class HostnamesConfig extends BasePatternConfig {
        /**
         * Patterns to match hostnames. These are applied to strings that look like
         * hostnames (FQDN format). The default patterns cover common corporate naming.
         */
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>(List.of(
            // hs_err "Host:" line pattern - requires FQDN format (at least one dot)
            "(?<=Host:\\s)[a-zA-Z][a-zA-Z0-9-]*(?:\\.[a-zA-Z0-9][a-zA-Z0-9.-]*)+",
            // FQDN with multiple domain parts (e.g., dev-jsmith.corp.example.com)
            // Must start with letter, contain only letters/digits in each segment, and have 2+ dots
            // Excludes version numbers like 5.15.0 by requiring letters in first segment
            "\\b[a-zA-Z][a-zA-Z0-9-]*(?:\\.[a-zA-Z][a-zA-Z0-9-]*){2,}\\b",
            // uname -a hostname (appears after Linux/Darwin, must start with letter, be a single word)
            "(?<=Linux\\s|Darwin\\s)[a-zA-Z][a-zA-Z0-9._-]*?(?=\\s+\\d)"
        ));

        public HostnamesConfig() {
            setEnabled(false);
            // Set default ignore_exact values
            getIgnoreExact().addAll(List.of(
                "localhost",
                "localhost.localdomain",
                "127.0.0.1"
            ));
        }

        public List<String> getPatterns() { return patterns; }
        public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    }

    /**
     * Internal/corporate URL pattern configuration.
     * Redacts URLs pointing to internal resources like Artifactory, Nexus, internal Git, etc.
     */
    public static class InternalUrlsConfig extends BasePatternConfig {
        /**
         * Patterns to match internal URLs. These target common internal infrastructure.
         */
        @JsonProperty("patterns")
        private List<String> patterns = new ArrayList<>(List.of(
            // Artifactory/Nexus URLs
            "https?://[a-zA-Z0-9.-]*(?:artifactory|nexus|repo|repository)[a-zA-Z0-9.-]*/[^\\s\"']*",
            // Internal Git URLs (git.company.com, gitlab.internal, etc.)
            "https?://[a-zA-Z0-9.-]*(?:git|gitlab|github|bitbucket)[a-zA-Z0-9.-]*/[^\\s\"']*",
            // Generic internal URLs (intranet, internal, corp, etc.)
            "https?://[a-zA-Z0-9.-]*(?:intranet|internal|corp|private)[a-zA-Z0-9.-]*/[^\\s\"']*",
            // Jenkins/CI URLs
            "https?://[a-zA-Z0-9.-]*(?:jenkins|ci|build|bamboo)[a-zA-Z0-9.-]*/[^\\s\"']*"
        ));

        /**
         * Domain suffixes that are considered internal (e.g., .corp.example.com, .internal)
         */
        @JsonProperty("internal_domains")
        private List<String> internalDomains = new ArrayList<>(List.of(
            ".internal",
            ".corp",
            ".local",
            ".intranet"
        ));

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
    public static class CustomPatternConfig {
        @JsonProperty("name")
        private String name;

        @JsonProperty("regex")
        private String regex;

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

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRegex() { return regex; }
        public void setRegex(String regex) { this.regex = regex; }

        public List<String> getIgnoreExact() { return ignoreExact; }
        public void setIgnoreExact(List<String> ignoreExact) { this.ignoreExact = ignoreExact; }

        public List<String> getIgnore() { return ignore; }
        public void setIgnore(List<String> ignore) { this.ignore = ignore; }

        public List<String> getIgnoreAfter() { return ignoreAfter; }
        public void setIgnoreAfter(List<String> ignoreAfter) { this.ignoreAfter = ignoreAfter; }
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