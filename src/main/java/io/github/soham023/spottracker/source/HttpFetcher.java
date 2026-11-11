package io.github.soham023.spottracker.source;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Thin wrapper over {@link HttpClient} so sources can be unit-tested with a stub. */
@Component
public class HttpFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; commodity-spot-tracker/0.1; +https://github.com/soham-023/commodity-spot-tracker)";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String get(String url, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GET " + redact(url) + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    public String postJson(String url, String json, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("POST " + redact(url) + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Hides api keys and bot tokens in log and error messages. */
    public static String redact(String url) {
        return url.replaceAll("(?i)(api-key=)[^&]+", "$1***").replaceAll("/bot[^/]+/", "/bot***/");
    }
}
