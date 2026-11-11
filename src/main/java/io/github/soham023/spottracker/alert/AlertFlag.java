package io.github.soham023.spottracker.alert;

import io.github.soham023.spottracker.spread.SpreadFlag;

/** Why an alert was raised. */
public enum AlertFlag {
    /** See {@link SpreadFlag#WIDE_SPREAD}. */
    WIDE_SPREAD,
    /** See {@link SpreadFlag#SHARP_MOVE}. */
    SHARP_MOVE,
    /** Near-month total margin moved by more than {@code spot.contracts.margin-alert-points}. */
    MARGIN_CHANGE;

    public static AlertFlag of(SpreadFlag flag) {
        return valueOf(flag.name());
    }
}
