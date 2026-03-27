package me.bechberger.jfrredact.text;

import me.bechberger.jfrredact.ConfigLoader;
import me.bechberger.jfrredact.config.RedactionConfig;
import me.bechberger.jfrredact.engine.RedactionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests verifying the intent of hs_err redaction on realistic snippets.
 *
 * <p>All sensitive data (hostnames, usernames, paths) has been replaced with
 * pseudonyms that are structurally equivalent to the original values.
 *
 * <p>Path redaction relies on discovery of home-directory usernames (e.g.
 * {@code /home/USER/...} or {@code /Users/USER/...}). Arbitrary internal paths
 * like {@code /data/ci/runs/...} are only redacted if they contain a discovered
 * username segment. Tests that exercise path redaction use paths containing a
 * home directory prefix so that the discovery engine can find and redact
 * the sensitive portion.
 */
public class HsErrRedactionTest {

    // Pseudonymized sensitive values (replacing real data from hserr_example)
    private static final String HOSTNAME = "buildnode42";
    private static final String BUILD_USER = "devbot";
    // Username discovered from /home/ciuser/... paths
    private static final String CI_USER = "ciuser";
    private static final String HOME_PREFIX = "/home/" + CI_USER;

    @TempDir
    Path tempDir;

    private RedactionConfig config;

    @BeforeEach
    void setUp() throws IOException {
        config = new ConfigLoader().load("hserr");
    }

    private TextFileRedactor createHsErrRedactor() {
        return new TextFileRedactor(new RedactionEngine(config), config);
    }

    /**
     * Redact multi-line text using the hserr preset with file-based discovery.
     * The text is written to a temp file and processed through the full
     * TextFileRedactor pipeline (discovery + redaction).
     */
    private String redactText(String input) throws IOException {
        TextFileRedactor redactor = createHsErrRedactor();
        Path inputFile = tempDir.resolve("input-" + System.nanoTime() + ".txt");
        Path outputFile = tempDir.resolve("output-" + System.nanoTime() + ".txt");
        Files.writeString(inputFile, input);
        redactor.redactFile(inputFile, outputFile);
        return Files.readString(outputFile);
    }

    /**
     * Redact a single line using the RedactionEngine directly (no discovery).
     * This reflects what happens per-line without discovery contributing patterns.
     */
    private String redactLine(String input) {
        RedactionEngine engine = new RedactionEngine(config);
        return engine.redact("text", input);
    }

    // ========== Header Section ==========

    @Nested
    class HeaderSection {

        @Test
        void headerInternalErrorPath_usernamePartIsRedacted() throws IOException {
            // The workspace path contains a home directory segment so discovery finds the username.
            String input = """
                    #
                    # A fatal error has been detected by the Java Runtime Environment:
                    #
                    #  Internal Error (%s/workspace/openjdk-jdk-dev/jdk/src/hotspot/share/gc/shenandoah/shenandoahHeapRegion.cpp:870), pid=47559, tid=47741
                    #""".formatted(HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in workspace path should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("Error structure should be preserved")
                    .contains("Internal Error")
                    .contains("shenandoahHeapRegion.cpp:870")
                    .contains("pid=47559");
        }

        @Test
        void headerAdhocBuildUser_isRedacted() throws IOException {
            String input = """
                    # JRE version: OpenJDK Runtime Environment (27.0) (fastdebug build 27-internal-adhoc.%s.jdk)
                    # Java VM: OpenJDK 64-Bit Server VM (fastdebug 27-internal-adhoc.%s.jdk, mixed mode, sharing, tiered, compressed oops, compact obj headers, shenandoah gc, linux-ppc64le)
                    """.formatted(BUILD_USER, BUILD_USER);

            String result = redactText(input);

            assertThat(result)
                    .as("Build username in adhoc build string should be redacted")
                    .doesNotContain("adhoc." + BUILD_USER + ".jdk");
            assertThat(result)
                    .as("JRE/VM type info should be preserved")
                    .contains("OpenJDK Runtime Environment")
                    .contains("OpenJDK 64-Bit Server VM");
        }

        @Test
        void headerCoreDumpPath_usernamePartIsRedacted() throws IOException {
            String input = "# Core dump will be written. Default location: %s/jtreg_work/JTwork/scratch/7/core.47559".formatted(HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in core dump path should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("Core dump message structure should be preserved")
                    .contains("Core dump will be written");
        }
    }

    // ========== Summary / Host Line ==========

    @Nested
    class SummarySection {

        @Test
        void hostLine_hostnameIsRedacted() throws IOException {
            String input = "Host: %s, POWER10 (architected), altivec supported, 64 cores, 127G, SUSE Linux Enterprise Server 15 SP6".formatted(HOSTNAME);

            String result = redactText(input);

            assertThat(result)
                    .as("Hostname should be redacted from Host: line")
                    .doesNotContain(HOSTNAME);
            assertThat(result)
                    .as("Hardware/OS info should be preserved")
                    .contains("POWER10")
                    .contains("64 cores")
                    .contains("SUSE Linux Enterprise Server");
        }

        @Test
        void timeLine_isPreserved() throws IOException {
            String input = "Time: Wed Mar 18 06:10:13 2026 CET elapsed time: 19.234801 seconds (0d 0h 0m 19s)";

            String result = redactText(input);

            assertThat(result)
                    .as("Time information should be preserved")
                    .contains("Wed Mar 18 06:10:13 2026 CET")
                    .contains("elapsed time:");
        }
    }

    // ========== Command Line ==========

    @Nested
    class CommandLineSection {

        @Test
        void commandLine_homeDirectoryPaths_usernameIsRedacted() throws IOException {
            String input = """
                    Command Line: -Dtest.vm.opts=-Xmx768m -Djava.util.prefs.userRoot=%s/jtreg_work/tmp -Dtest.jdk=%s/testee-vm -XX:+UseShenandoahGC com.sun.javatest.regtest.agent.MainWrapper
                    """.formatted(HOME_PREFIX, HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in command line paths should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("JVM flags and class names should be preserved")
                    .contains("Command Line:")
                    .contains("-Xmx768m")
                    .contains("-XX:+UseShenandoahGC");
        }
    }

    // ========== Stack Traces (single-line, no discovery) ==========

    @Nested
    class StackTraceSection {

        @ParameterizedTest
        @ValueSource(strings = {
                "j  gc.stress.gcold.TestGCOld.main([Ljava/lang/String;)V+205",
                "J 313 c2 gc.stress.gcold.TestGCOld.doYoungGenAlloc(JI)V (41 bytes) @ 0x00007fff7bc488cc [0x00007fff7bc486c0+0x000000000000020c]"
        })
        void stackFrames_withoutScopeOperator_areNotRedacted(String line) {
            String result = redactLine(line);

            assertThat(result)
                    .as("Stack frame should not be redacted: %s", line)
                    .isEqualTo(line);
        }

        @Test
        void cLibraryStackFrame_libraryNamePreserved() {
            // libc.so.6 may match IP-like patterns, but the overall frame is recognizable
            String line = "C  [libc.so.6+0xb2014]  start_thread+0x184";
            String result = redactLine(line);

            assertThat(result)
                    .as("C library stack frame method should be preserved")
                    .contains("start_thread")
                    .contains("0xb2014");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "j  java.lang.invoke.LambdaForm$DMH+0x0000013c01042400.invokeStatic(Ljava/lang/Object;Ljava/lang/Object;)V+10 java.base@27-internal",
                "j  jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;+23 java.base@27-internal",
                "j  java.lang.reflect.Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;+102 java.base@27-internal",
                "j  java.lang.Thread.run()V+19 java.base@27-internal",
                "j  java.lang.Thread.run()V+19 java.base@27",
                "v  ~RuntimeStub::new_array_blob (C2 runtime) 0x00007fff7b44fb38"
        })
        void javaStackFrames_moduleVersionNotation_isNotRedacted(String line) {
            // The hserr ssh_hosts ignore pattern should prevent module@version from being matched
            String result = redactLine(line);

            assertThat(result)
                    .as("Java stack frame with module@version should not be redacted: %s", line)
                    .isEqualTo(line);
        }

        @Test
        void nativeStackFrame_classAndMethodNamesPreserved() {
            // Even though :: might be matched, the key identifiers must survive
            String line = "V  [libjvm.so+0x1c1a4f0]  ShenandoahHeapRegion::set_affiliation(ShenandoahAffiliation)+0x2f0  (shenandoahHeapRegion.cpp:870)";
            String result = redactLine(line);

            assertThat(result)
                    .as("Source file reference should be preserved")
                    .contains("shenandoahHeapRegion.cpp:870");
            assertThat(result)
                    .as("Class name should be preserved")
                    .contains("ShenandoahHeapRegion");
            assertThat(result)
                    .as("Method name should be preserved")
                    .contains("set_affiliation");
        }
    }

    // ========== Register Dumps ==========

    @Nested
    class RegisterSection {

        @Test
        void registerDump_isPreserved() throws IOException {
            String input = """
                    Registers:
                    pc =0x00007fff92c1a4f0  lr =0x00007fff92c1a4a0  ctr=0x00007fff9129c700
                    r0 =0x00007fff92c1a4a0  r1 =0x00007fff60ffccf0  r2 =0x00007fff939a7d00
                    r3 =0x00007fff9342e250  r4 =0x0000000000000366  r5 =0x00007fff9342ef08
                    """;

            String result = redactText(input);

            assertThat(result)
                    .as("Register values should be preserved as-is")
                    .contains("pc =0x00007fff92c1a4f0")
                    .contains("r0 =0x00007fff92c1a4a0")
                    .contains("r4 =0x0000000000000366");
        }

        @Test
        void registerToMemoryMapping_usernameInPathIsRedacted() throws IOException {
            String input = "pc =0x00007fff92c1a4f0: <offset 0x0000000001c1a4f0> in %s/testee-vm/lib/server/libjvm.so at 0x00007fff91000000".formatted(HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in register-to-memory mapping path should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("Offset and address info should be preserved")
                    .contains("<offset 0x0000000001c1a4f0>")
                    .contains("libjvm.so");
        }
    }

    // ========== Assembly / Disassembly ==========

    @Nested
    class AssemblySection {

        @Test
        void assemblyInstructions_contentIsPreserved() throws IOException {
            String input = """
                      0x00007fff7bc486c0:   mflr    r20                         ;   {other}
                      0x00007fff7bc488c8:   bl      0x00007fff7b44fb00
                      0x00007fff7bc488cc:   cmplwi  r2,209
                      0x00007fff7bc487f8:   dcbz    0,r4
                    =>0x00007fff92c1a4f0:   00 00 49 99 10 00 3e e9 01 e3 f3 4a 00 00 00 60
                    """;

            String result = redactText(input);

            assertThat(result)
                    .as("Assembly instruction content should be preserved")
                    .contains("mflr    r20")
                    .contains("bl      0x00007fff7b44fb00")
                    .contains("cmplwi  r2,209")
                    .contains("dcbz    0,r4")
                    .contains("00 00 49 99 10 00 3e e9");
        }

        @Test
        void hexDump_isPreserved() throws IOException {
            String input = """
                    0x00007fff60ffccf0:   00007fff60ffcdb0 0000000000000000   ...`............
                    0x00007fff60ffcd00:   00007fff92c1a4fc 00007fff939a7d00   .........}......
                    """;

            String result = redactText(input);

            assertThat(result)
                    .as("Hex dumps should be preserved")
                    .contains("00007fff60ffcdb0")
                    .contains("00007fff92c1a4fc");
        }
    }

    // ========== Dynamic Libraries ==========

    @Nested
    class DynamicLibrariesSection {

        @Test
        void dynamicLibraryEntry_usernameInPathIsRedacted() throws IOException {
            String input = "7fff602c0000-7fff60430000 r-xp 00000000 fe:06 3400620   %s/testee-vm/lib/server/hsdis-ppc64le.so".formatted(HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in dynamic library path should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("Memory mapping info should be preserved")
                    .contains("r-xp");
        }

        @Test
        void systemLibraryEntry_isPreserved() throws IOException {
            String input = "7fff62c00000-7fff62e80000 r--p 00000000 fe:05 67396661   /usr/lib/locale/en_US.utf8/LC_COLLATE";

            String result = redactText(input);

            assertThat(result)
                    .as("System library entries should be preserved")
                    .contains("/usr/lib/locale/en_US.utf8/LC_COLLATE");
        }
    }

    // ========== Environment Variables ==========

    @Nested
    class EnvironmentVariablesSection {

        @Test
        void classpathVariable_usernameInPathIsRedacted() throws IOException {
            String input = "CLASSPATH=%s/jtreg_work/JTwork/classes/6/gc/stress/gcold/TestGCOldWithShenandoah_aggressive.d".formatted(HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in CLASSPATH path should be redacted")
                    .doesNotContain(CI_USER);
        }

        @Test
        void langVariable_isPreserved() throws IOException {
            String input = "LANG=en_US.UTF-8";

            String result = redactText(input);

            assertThat(result)
                    .as("Non-sensitive env vars like LANG should be preserved")
                    .contains("LANG=en_US.UTF-8");
        }
    }

    // ========== VM Info ==========

    @Nested
    class VmInfoSection {

        @Test
        void vmInfoLine_buildUserIsRedacted() throws IOException {
            String input = "vm_info: OpenJDK 64-Bit Server VM (fastdebug 27-internal-adhoc.%s.jdk) for linux-ppc64le JRE (27-internal-adhoc.%s.jdk), built on 2026-03-17T21:10:20Z with gcc 14.2.0".formatted(BUILD_USER, BUILD_USER);

            String result = redactText(input);

            assertThat(result)
                    .as("Build user in vm_info should be redacted")
                    .doesNotContain("adhoc." + BUILD_USER + ".jdk");
            assertThat(result)
                    .as("VM type and build date should be preserved")
                    .contains("OpenJDK 64-Bit Server VM")
                    .contains("gcc 14.2.0");
        }

        @Test
        void releaseVersionLine_buildUserIsRedacted() throws IOException {
            String input = "JAVA_RUNTIME_VERSION=\"27-internal-adhoc.%s.jdk\"".formatted(BUILD_USER);

            String result = redactText(input);

            assertThat(result)
                    .as("Build user in JAVA_RUNTIME_VERSION should be redacted")
                    .doesNotContain(BUILD_USER);
        }
    }

    // ========== Event Log Entries ==========

    @Nested
    class EventLogSection {

        @Test
        void sharedLibraryLoadEvent_usernameInPathIsRedacted() throws IOException {
            String input = """
                    Event: 0.005 Attempting to load shared library %s/testee-vm/lib/libjava.so
                    Event: 0.005 Loaded shared library %s/testee-vm/lib/libjava.so
                    """.formatted(HOME_PREFIX, HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in event log paths should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("Event structure and library names should be preserved")
                    .contains("Event:")
                    .contains("libjava.so");
        }

        @Test
        void exceptionThrownEvent_usernameInPathIsRedacted() throws IOException {
            String input = "thrown [%s/workspace/jdk/src/hotspot/share/interpreter/linkResolver.cpp, line 803]".formatted(HOME_PREFIX);

            String result = redactText(input);

            assertThat(result)
                    .as("Username in thrown event path should be redacted")
                    .doesNotContain(CI_USER);
            assertThat(result)
                    .as("Source reference and line number should be preserved")
                    .contains("linkResolver.cpp")
                    .contains("line 803");
        }
    }

    // ========== CPU / Hardware Info ==========

    @Nested
    class HardwareSection {

        @Test
        void cpuInfo_isPreserved() throws IOException {
            String input = """
                    processor\t: 0
                    cpu\t\t: POWER10 (architected), altivec supported
                    clock\t\t: 3550.000000MHz
                    revision\t: 2.0 (pvr 0080 0200)
                    """;

            String result = redactText(input);

            assertThat(result)
                    .as("CPU info should be fully preserved")
                    .contains("POWER10 (architected)")
                    .contains("3550.000000MHz")
                    .contains("pvr 0080 0200");
        }

        @Test
        void machineModel_isPreserved() throws IOException {
            String input = """
                    platform\t: pSeries
                    model\t\t: IBM,9080-HEX
                    machine\t\t: CHRP IBM,9080-HEX
                    """;

            String result = redactText(input);

            assertThat(result)
                    .as("Machine model info should be preserved")
                    .contains("IBM,9080-HEX")
                    .contains("pSeries");
        }

        @Test
        void memoryInfo_isPreserved() throws IOException {
            String input = "Memory: 64k page, physical 133594752k(115506176k free), swap 20971456k(20743616k free)";

            String result = redactText(input);

            assertThat(result)
                    .as("Memory statistics should be preserved")
                    .contains("physical 133594752k")
                    .contains("swap 20971456k");
        }
    }

    // ========== Full Snippet Integration Test ==========

    @Test
    void fullHeaderSnippet_sensitiveDataRedacted_structurePreserved() throws IOException {
        String input = """
                #
                # A fatal error has been detected by the Java Runtime Environment:
                #
                #  Internal Error (%s/workspace/jdk/src/hotspot/share/gc/shenandoah/shenandoahHeapRegion.cpp:870), pid=47559, tid=47741
                #  assert(ctx->is_bitmap_range_within_region_clear(top_bitmap, _end)) failed: Region 1364, bitmap should be clear between top_bitmap: 0x00000000fd500000 and end: 0x00000000fd540000
                #
                # JRE version: OpenJDK Runtime Environment (27.0) (fastdebug build 27-internal-adhoc.%s.jdk)
                # Java VM: OpenJDK 64-Bit Server VM (fastdebug 27-internal-adhoc.%s.jdk, mixed mode, sharing, tiered, compressed oops, compact obj headers, shenandoah gc, linux-ppc64le)
                # Problematic frame:
                # V  [libjvm.so+0x1c1a4f0]  ShenandoahHeapRegion::set_affiliation(ShenandoahAffiliation)+0x2f0
                #
                # Core dump will be written. Default location: %s/jtreg_work/JTwork/scratch/7/core.47559
                #
                # If you would like to submit a bug report, please visit:
                #   https://bugreport.java.com/bugreport/crash.jsp
                #

                ---------------  S U M M A R Y ------------

                %s

                Host: %s, POWER10 (architected), altivec supported, 64 cores, 127G, SUSE Linux Enterprise Server 15 SP6
                Time: Wed Mar 18 06:10:13 2026 CET elapsed time: 19.234801 seconds (0d 0h 0m 19s)
                """.formatted(
                HOME_PREFIX,
                BUILD_USER,
                BUILD_USER,
                HOME_PREFIX,
                "Command Line: -Dtest.jdk=" + HOME_PREFIX + "/testee-vm -XX:+UseShenandoahGC",
                HOSTNAME
        );

        String result = redactText(input);

        // Sensitive data should be redacted
        assertThat(result)
                .doesNotContain(CI_USER)
                .doesNotContain(HOSTNAME)
                .doesNotContain("adhoc." + BUILD_USER + ".jdk");

        // Structural elements should be preserved
        assertThat(result)
                .contains("A fatal error has been detected")
                .contains("shenandoahHeapRegion.cpp:870")
                .contains("OpenJDK Runtime Environment")
                .contains("S U M M A R Y")
                .contains("POWER10")
                .contains("SUSE Linux Enterprise Server")
                .contains("bugreport")  // URL domain may be partially redacted
                .contains("crash.jsp");
    }

    @Test
    void fullStackTraceSnippet_classAndMethodNamesPreserved() throws IOException {
        String input = """
                Current thread (0x00007fff8c481fc0):  JavaThread "MainThread"        [_thread_in_vm, id=47741, stack(0x00007fff60e00000,0x00007fff61000000) (2048K)]

                Stack: [0x00007fff60e00000,0x00007fff61000000],  sp=0x00007fff60ffccf0,  free space=2035k
                Native frames: (J=compiled Java code, j=interpreted, Vv=VM code, C=native code)
                V  [libjvm.so+0x1c1a4f0]  ShenandoahHeapRegion::set_affiliation(ShenandoahAffiliation)+0x2f0  (shenandoahHeapRegion.cpp:870)
                V  [libjvm.so+0x1b70ae4]  ShenandoahFreeSet::try_allocate_in(ShenandoahHeapRegion*, ShenandoahAllocRequest&, bool&)+0x3a4  (shenandoahFreeSet.cpp:1579)
                V  [libjvm.so+0x1697250]  MemAllocator::mem_allocate_inside_tlab_slow(MemAllocator::Allocation&) const+0x2e0  (memAllocator.cpp:298)
                V  [libjvm.so+0x1f6e7dc]  TypeArrayKlass::allocate_common(int, bool, JavaThread*)+0x1ec  (collectedHeap.inline.hpp:43)
                j  gc.stress.gcold.TestGCOld.main([Ljava/lang/String;)V+205
                j  java.lang.invoke.LambdaForm$DMH+0x0000013c01042400.invokeStatic(Ljava/lang/Object;Ljava/lang/Object;)V+10 java.base@27-internal
                j  jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;+23 java.base@27-internal
                j  java.lang.reflect.Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;+102 java.base@27-internal
                j  java.lang.Thread.run()V+19 java.base@27-internal
                v  ~StubRoutines::call_stub_stub (stub gen) 0x00007fff7b3c0b44
                """;

        String result = redactText(input);

        // Class names and method names should still be identifiable in the output
        assertThat(result)
                .contains("ShenandoahHeapRegion")
                .contains("set_affiliation")
                .contains("ShenandoahFreeSet")
                .contains("try_allocate_in")
                .contains("shenandoahHeapRegion.cpp:870")
                .contains("gc.stress.gcold.TestGCOld.main");
    }

    // ========== Email Redaction in hs_err Context ==========

    @Nested
    class EmailSection {

        @Test
        void emailInEnvironmentVariable_isRedacted() throws IOException {
            String input = "MAIL=developer@internal-corp.example.com";

            String result = redactText(input);

            assertThat(result)
                    .as("Email addresses in env vars should be redacted")
                    .doesNotContain("developer@internal-corp.example.com");
        }
    }
}
