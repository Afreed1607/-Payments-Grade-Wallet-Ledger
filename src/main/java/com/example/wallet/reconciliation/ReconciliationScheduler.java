package com.example.wallet.reconciliation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationScheduler {
    private final ReconciliationService service;
    public ReconciliationScheduler(ReconciliationService service) { this.service = service; }

    /** Runs nightly; the database job lock makes this safe across API replicas. */
    @Scheduled(cron = "${wallet.reconciliation.cron:0 0 2 * * *}")
    public void nightlyRun() { service.run(); }
}
