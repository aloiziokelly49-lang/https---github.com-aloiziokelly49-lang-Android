package com.cloudink.app.util;

import java.util.UUID;

public final class UuidGenerator {

    private UuidGenerator() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}
