package io.github.soham023.spottracker.spread;

public enum SpreadKind {
    /** Near-month NCDEX futures minus NCDEX polled spot of the basis quality. */
    BASIS,
    /**
     * Near-month NCDEX futures minus another deliverable grade's NCDEX polled spot (e.g. Rajapore at Sangli),
     * delivery-adjusted with the exchange premium/discount matrix.
     */
    DELIVERY,
    /** APMC mandi modal price minus NCDEX polled spot. */
    MANDI
}
