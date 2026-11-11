package io.github.soham023.spottracker.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.soham023.spottracker.source.HttpFetcher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/** Posts alerts through the Telegram Bot API when TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are set. */
@Component
public class TelegramAlertChannel implements AlertChannel {

    private final AlertProperties props;
    private final HttpFetcher http;
    private final ObjectMapper mapper;

    public TelegramAlertChannel(AlertProperties props, HttpFetcher http, ObjectMapper mapper) {
        this.props = props;
        this.http = http;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean isEnabled() {
        return props.telegramConfigured();
    }

    @Override
    public void send(Alert alert) throws Exception {
        String body = mapper.writeValueAsString(Map.of(
                "chat_id", props.telegramChatId(),
                "text", "🟡 " + alert.getMessage(),
                "disable_web_page_preview", true));
        http.postJson("https://api.telegram.org/bot" + props.telegramBotToken() + "/sendMessage", body,
                Duration.ofSeconds(15));
    }
}
