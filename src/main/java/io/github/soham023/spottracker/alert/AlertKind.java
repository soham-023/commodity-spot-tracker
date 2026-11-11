package io.github.soham023.spottracker.alert;

import io.github.soham023.spottracker.spread.SpreadKind;

/** What an alert is about: one of the spread kinds, or the exchange margin on the near-month contract. */
public enum AlertKind {
    BASIS,
    DELIVERY,
    MANDI,
    /** Total margin (TM) on the near-month futures contract changed day over day. */
    MARGIN;

    public static AlertKind of(SpreadKind kind) {
        return valueOf(kind.name());
    }
}
