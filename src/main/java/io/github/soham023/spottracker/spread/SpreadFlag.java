package io.github.soham023.spottracker.spread;

public enum SpreadFlag {
    /** |spread| is larger than the configured % of the NCDEX spot price. */
    WIDE_SPREAD,
    /** The compared price moved more than the configured % since its previous observation. */
    SHARP_MOVE
}
