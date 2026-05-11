package com.smartbanking.common.util;

import java.util.UUID;

public class CorrelationIdUtils {

    private static final String CORRELATION_ID_HEADER="X-CORRELATION-ID";

    private static String generate(){
        return UUID.randomUUID().toString();
    }
}
