package io.github.soham023.spottracker.alert;

/** Somewhere an alert can be delivered. Disabled channels are skipped silently. */
public interface AlertChannel {

    String name();

    boolean isEnabled();

    void send(Alert alert) throws Exception;
}
