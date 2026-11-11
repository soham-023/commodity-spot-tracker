package io.github.soham023.spottracker.domain;

/** Whether a price is a physical (spot / mandi) quote or an exchange futures quote. */
public enum PriceType {
    SPOT,
    FUTURES
}
