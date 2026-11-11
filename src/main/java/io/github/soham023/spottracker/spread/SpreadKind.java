package io.github.soham023.spottracker.spread;

public enum SpreadKind {
    /** Near-month NCDEX futures minus NCDEX polled spot. */
    BASIS,
    /** APMC mandi modal price minus NCDEX polled spot. */
    MANDI
}
