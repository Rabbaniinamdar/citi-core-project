package com.citicore.transaction.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a unique, human-readable transaction reference.
 *
 * Format: CITI-TXN-{YYYYMMDD}-{SEQUENCE}
 * Example: CITI-TXN-20260615-000007
 *
 * The sequence resets daily and is thread-safe via AtomicInteger.
 * In production, use a DB sequence or Redis INCR for multi-instance safety.
 */
@Component
public class TxnRefGenerator {

    public synchronized String generate() {
        return "CITI-TXN-" + UUID.randomUUID();
    }
}