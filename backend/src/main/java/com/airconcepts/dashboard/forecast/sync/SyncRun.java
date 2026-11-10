package com.airconcepts.dashboard.forecast.sync;
import java.time.Instant;
import java.util.UUID;

/** Safe diagnostics only, never provider payloads or tokens. */
public record SyncRun(UUID id, String trigger, Instant startedAt, Instant finishedAt, String status,
    int documentsSeen, int documentsInserted, int documentsUpdated, int documentsSettled, int conflictsFlagged,
    String errorCode, Integer upstreamStatus, Integer dayLimitRemaining, Integer minuteLimitRemaining,
    Integer appMinuteLimitRemaining) {}
