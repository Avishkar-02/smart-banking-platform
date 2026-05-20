package com.smartbanking.common.util;

import java.util.UUID;

// Simple utility — just a constant and a generator.
// No filter, no Spring dependency, no complexity.
// Services use the constant to read/write the header consistently.

public class CorrelationIdUtils {

    // One constant used everywhere means one typo doesn't break tracing.
    // If you hardcode "X-Correlation-ID" in 8 services,
    // one "X-Corelation-ID" typo and tracing silently breaks.
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    // Prevent instantiation — this is a utility class
    private CorrelationIdUtils() {}
}