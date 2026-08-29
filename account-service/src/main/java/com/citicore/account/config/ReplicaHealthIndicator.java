package com.citicore.account.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ReplicaHealthIndicator {

    private final AtomicBoolean replicaAvailable =
            new AtomicBoolean(true);

    public boolean isReplicaAvailable() {
        return replicaAvailable.get();
    }

    public void markReplicaUnavailable() {
        if (replicaAvailable.compareAndSet(true, false)) {
            System.out.println(
                    "⚠️ [REPLICA] Replica marked UNAVAILABLE"
            );
        }
    }

    public void markReplicaAvailable() {
        if (replicaAvailable.compareAndSet(false, true)) {
            System.out.println(
                    "✅ [REPLICA] Replica marked AVAILABLE"
            );
        }
    }
}