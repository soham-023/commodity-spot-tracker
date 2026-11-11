package io.github.soham023.spottracker.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Always-on channel: writes alerts to the application log. */
@Component
public class LogAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger("ALERTS");

    @Override
    public String name() {
        return "log";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(Alert alert) {
        log.warn("[{}] {}", alert.getFlag(), alert.getMessage());
    }
}
