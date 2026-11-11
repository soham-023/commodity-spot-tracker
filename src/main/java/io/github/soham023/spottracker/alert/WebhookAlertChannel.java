package io.github.soham023.spottracker.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** POSTs a JSON payload to ALERT_WEBHOOK_URL. {@code text} makes it work with Slack-style incoming webhooks. */
@Component
public class WebhookAlertChannel implements AlertChannel {

    private final AlertProperties props;
    private final HttpFetcher http;
    private final ObjectMapper mapper;

    public WebhookAlertChannel(AlertProperties props, HttpFetcher http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public boolean isEnabled() {
        return props.webhookConfigured();
    }

    @Override
    public void send(Alert alert) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", alert.getMessage());
        payload.put("commodity", alert.getCommodity());
        payload.put("date", alert.getAlertDate().toString());
        payload.put("kind", alert.getKind().name());
        payload.put("label", alert.getLabel());
        payload.put("flag", alert.getFlag().name());
        payload.put("spreadPct", alert.getSpreadPct());
        http.postJson(props.webhookUrl(), mapper.writeValueAsString(payload), Duration.ofSeconds(15));
    }
}
