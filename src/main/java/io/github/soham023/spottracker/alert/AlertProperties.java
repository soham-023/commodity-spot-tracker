package io.github.soham023.spottracker.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param telegramBotToken token from @BotFather ({@code TELEGRAM_BOT_TOKEN})
 * @param telegramChatId   chat/channel id to post to ({@code TELEGRAM_CHAT_ID})
 * @param webhookUrl       any URL accepting a JSON POST, e.g. Slack/Discord/n8n ({@code ALERT_WEBHOOK_URL})
 */
@ConfigurationProperties(prefix = "spot.alerts")
public record AlertProperties(String telegramBotToken, String telegramChatId, String webhookUrl) {

    public boolean telegramConfigured() {
        return notBlank(telegramBotToken) && notBlank(telegramChatId);
    }

    public boolean webhookConfigured() {
        return notBlank(webhookUrl);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
