package com.aibridge.dto.lineage;

/** How one link of the failover chain ended. */
public enum AttemptOutcome {
    /** Provider answered with usable content. Terminal. */
    SUCCESS,
    /** Provider answered 200 but with no assistant content. Terminal — not a failover trigger. */
    EMPTY_RESPONSE,
    /** No pacing slot became available within the config's queue timeout. Advances the chain. */
    QUEUE_TIMEOUT,
    /** Provider returned 429 despite local pacing. Advances the chain. */
    RATE_LIMITED,
    /** Provider returned 5xx, or the call timed out. Advances the chain. */
    UNAVAILABLE,
    /** No adapter is registered for the config's provider. Advances the chain. */
    NO_ADAPTER,

    /**
     * An unexpected failure — a misconfiguration or a bug, not a provider outage. Recorded and
     * rethrown rather than failed over: trying the next provider would hide the cause.
     */
    ERROR
}
