package com.citicore.transaction.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private final AtomicInteger sequence = new AtomicInteger(0);
    private String lastDate = "";

    public synchronized String generate() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        if (!today.equals(lastDate)) {
            sequence.set(0);
            lastDate = today;
        }

        int seq = sequence.incrementAndGet();
        return String.format("CITI-TXN-%s-%06d", today, seq);
    }
}