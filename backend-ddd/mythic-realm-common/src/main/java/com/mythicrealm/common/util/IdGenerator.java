package com.mythicrealm.common.util;

import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {
    private static final AtomicLong counter = new AtomicLong(System.currentTimeMillis());

    public static long nextId() {
        return counter.incrementAndGet();
    }
}
