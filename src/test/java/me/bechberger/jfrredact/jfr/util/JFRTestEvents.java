package me.bechberger.jfrredact.jfr.util;

import jdk.jfr.*;

/**
 * Common test event definitions for JFR testing.
 */
public class JFRTestEvents {

    // ========== Test Event Classes ==========

    @Name("test.SimpleEvent")
    @Label("Simple Test Event")
    @Description("A simple event with basic fields")
    @Category("Test")
    public static class SimpleEvent extends Event {
        @Label("Message")
        public String message;

        @Label("Count")
        public int count;

        @Label("Flag")
        public boolean flag;
    }

    @Name("test.SensitiveDataEvent")
    @Label("Sensitive Data Event")
    @Description("Event containing sensitive information")
    @Category("Test")
    public static class SensitiveDataEvent extends Event {
        @Label("Username")
        public String username;

        @Label("Password")
        public String password;

        @Label("Email")
        public String email;

        @Label("IP Address")
        public String ipAddress;
    }

    @Name("test.ComplexEvent")
    @Label("Complex Event")
    @Description("Event with various data types")
    @Category("Test")
    public static class ComplexEvent extends Event {
        @Label("String Field")
        public String stringField;

        @Label("Integer Field")
        public int intField;

        @Label("Long Field")
        public long longField;

        @Label("Float Field")
        public float floatField;

        @Label("Double Field")
        public double doubleField;

        @Label("Boolean Field")
        public boolean booleanField;

        @Label("Byte Field")
        public byte byteField;

        @Label("Short Field")
        public short shortField;

        @Label("Char Field")
        public char charField;
    }

    @Name("test.ArrayEvent")
    @Label("Array Event")
    @Description("Event with array fields")
    @Category("Test")
    public static class ArrayEvent extends Event {
        @Label("String Array")
        public String[] stringArray;

        @Label("Int Array")
        public int[] intArray;

        @Label("Long Array")
        public long[] longArray;
    }

    @Name("test.NetworkEvent")
    @Label("Network Event")
    @Description("Event with network-related fields")
    @Category("Test")
    public static class NetworkEvent extends Event {
        @Label("Source Address")
        public String sourceAddress;

        @Label("Destination Address")
        public String destinationAddress;

        @Label("Source Port")
        public int sourcePort;

        @Label("Destination Port")
        public int destinationPort;

        @Label("Protocol")
        public String protocol;
    }

    @Name("test.AnnotatedEvent")
    @Label("Fully Annotated Event")
    @Description("Event with all supported JFR annotations")
    @Category({"Test", "Annotations"})
    @StackTrace(false)
    @Threshold("100 ms")
    public static class AnnotatedEvent extends Event {
        @Label("Event Time")
        @Description("When the event occurred")
        @Timestamp
        public long eventTime;

        @Label("Duration")
        @Description("How long the operation took")
        @Timespan
        public long duration2;

        @Label("Data Size")
        @Description("Amount of data processed")
        @DataAmount
        public long dataSize;

        @Label("Frequency")
        @Description("Operation frequency")
        @Frequency
        public long frequency;

        @Label("Memory Address")
        @Description("Memory address reference")
        @MemoryAddress
        public long memoryAddress;

        @Label("Percentage")
        @Description("Completion percentage")
        @Percentage
        public float percentage;

        @Label("Unsigned Value")
        @Description("Unsigned integer value")
        @Unsigned
        public int unsignedValue;

        @Label("Thread Name")
        @Description("Thread executing the operation")
        public Thread thread;

        @Label("Class Name")
        @Description("Related class")
        public Class<?> clazz;

        @Label("Experimental Field")
        @Description("Experimental feature flag")
        @Experimental
        public boolean experimentalFlag;
    }

    @Name("test.TimestampEvent")
    @Label("Timestamp Event")
    @Description("Event with various timestamp annotations")
    @Category("Test")
    public static class TimestampEvent extends Event {
        @Label("Timestamp Start")
        @Timestamp(Timestamp.TICKS)
        public long timestampStart;

        @Label("Timestamp End")
        @Timestamp(Timestamp.TICKS)
        public long timestampEnd;

        @Label("Duration Nanos")
        @Timespan
        public long durationNanos;

        @Label("Duration Micros")
        @Timespan(Timespan.MICROSECONDS)
        public long durationMicros;

        @Label("Duration Millis")
        @Timespan(Timespan.MILLISECONDS)
        public long durationMillis;

        @Label("Duration Seconds")
        @Timespan(Timespan.SECONDS)
        public long durationSeconds;
    }

    @Name("test.DataAmountEvent")
    @Label("Data Amount Event")
    @Description("Event with various data amount annotations")
    @Category("Test")
    public static class DataAmountEvent extends Event {
        @Label("Bytes")
        @DataAmount()
        public long bytes;

        @Label("Bits")
        @DataAmount(DataAmount.BITS)
        public long bits;

        @Label("Hertz")
        @Frequency
        public long hertz;

        @Label("Memory Address")
        @MemoryAddress
        public long address;
    }

    @Name("test.RelationalEvent")
    @Label("Relational Event")
    @Description("Event with related event identifiers")
    @Category("Test")
    public static class RelationalEvent extends Event {
        @Label("Event ID")
        @Description("Unique event identifier")
        public long eventId;

        @Label("Parent ID")
        @Description("Parent event identifier")
        public long parentId;

        @Label("Operation")
        public String operation;

        @Label("Result")
        public int result;
    }

    @Name("test.ThreadEvent")
    @Label("Thread Event")
    @Description("Event with thread and class information")
    @Category("Test")
    public static class ThreadEvent extends Event {
        @Label("Thread")
        @Description("Thread that triggered the event")
        public Thread thread;

        @Label("Class")
        @Description("Class associated with the event")
        public Class<?> clazz;

        @Label("Thread Name")
        public String threadName;

        @Label("Thread Priority")
        public int threadPriority;

        @Label("Is Daemon")
        public boolean isDaemon;
    }

    @Name("test.PerformanceEvent")
    @Label("Performance Event")
    @Description("Event with performance metrics")
    @Category({"Test", "Performance"})
    @StackTrace()
    @Threshold("1 ms")
    public static class PerformanceEvent extends Event {
        @Label("Operation")
        public String operation;

        @Label("Duration")
        @Timespan(Timespan.MILLISECONDS)
        public long duration2;

        @Label("CPU Time")
        @Timespan
        public long cpuTime;

        @Label("Bytes Allocated")
        @DataAmount
        public long bytesAllocated;

        @Label("Success Rate")
        @Percentage
        public float successRate;

        @Label("Operations Per Second")
        @Frequency
        public long opsPerSecond;
    }

    @Name("test.EnabledEvent")
    @Label("Enabled Event")
    @Description("Event that is enabled by default")
    @Category("Test")
    @Enabled()
    public static class EnabledEvent extends Event {
        @Label("Message")
        public String message;

        @Label("Counter")
        public int counter;
    }

    @Name("test.DisabledEvent")
    @Label("Disabled Event")
    @Description("Event that is disabled by default")
    @Category("Test")
    @Enabled(false)
    public static class DisabledEvent extends Event {
        @Label("Message")
        public String message;

        @Label("Should Not Appear")
        public String shouldNotAppear;
    }

    @Name("test.PeriodEvent")
    @Label("Period Event")
    @Description("Event with a specific period")
    @Category("Test")
    @Period("1 s")
    public static class PeriodEvent extends Event {
        @Label("Timestamp")
        @Timestamp
        public long timestamp;

        @Label("Value")
        public int value;
    }

    @Name("test.ContentTypeEvent")
    @Label("Content Type Event")
    @Description("Event with various content type annotations")
    @Category("Test")
    public static class ContentTypeEvent extends Event {
        @Label("Bytes")
        @DataAmount
        public long bytes;

        @Label("Percentage")
        @Percentage
        public float percentage;

        @Label("Memory Address")
        @MemoryAddress
        public long memoryAddress;

        @Label("Unsigned")
        @Unsigned
        public long unsigned;
    }


    @Name("test.ComprehensiveDataAmountEvent")
    @Label("Comprehensive Data Amount Event")
    @Description("Event testing all DataAmount variations")
    @Category("Test")
    public static class ComprehensiveDataAmountEvent extends Event {
        @Label("Bytes Value")
        @DataAmount()
        public long bytesValue;

        @Label("Bits Value")
        @DataAmount(DataAmount.BITS)
        public long bitsValue;

        @Label("Kilobytes")
        @DataAmount()
        public long kilobytes;  // Will store as bytes

        @Label("Megabytes")
        @DataAmount()
        public long megabytes;  // Will store as bytes

        @Label("Gigabytes")
        @DataAmount()
        public long gigabytes;  // Will store as bytes

        @Label("Transfer Rate")
        @Frequency
        public long transferRate;  // bytes per second

        @Label("Bandwidth Usage")
        @Percentage
        public float bandwidthUsage;
    }

    @Name("test.AllContentTypesEvent")
    @Label("All Content Types Event")
    @Description("Event with every available content type annotation")
    @Category("Test")
    public static class AllContentTypesEvent extends Event {
        // Data amounts
        @Label("Bytes")
        @DataAmount()
        public long bytes;

        @Label("Bits")
        @DataAmount(DataAmount.BITS)
        public long bits;

        // Time-related
        @Label("Timestamp")
        @Timestamp
        public long timestamp;

        @Label("Timespan Nanos")
        @Timespan
        public long timespanNanos;

        @Label("Timespan Micros")
        @Timespan(Timespan.MICROSECONDS)
        public long timespanMicros;

        @Label("Timespan Millis")
        @Timespan(Timespan.MILLISECONDS)
        public long timespanMillis;

        @Label("Timespan Seconds")
        @Timespan(Timespan.SECONDS)
        public long timespanSeconds;

        // Numeric content types
        @Label("Frequency Hz")
        @Frequency
        public long frequency;

        @Label("Memory Address")
        @MemoryAddress
        public long memoryAddress;

        @Label("Percentage Value")
        @Percentage
        public float percentage;

        @Label("Unsigned Value")
        @Unsigned
        public long unsigned;

        // Reference types
        @Label("Thread Reference")
        public Thread thread;

        @Label("Class Reference")
        public Class<?> clazz;
    }
}