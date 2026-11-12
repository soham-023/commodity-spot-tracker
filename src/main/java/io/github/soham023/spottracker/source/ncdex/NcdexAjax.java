package io.github.soham023.spottracker.source.ncdex;

import io.github.soham023.spottracker.source.HttpFetcher;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NCDEX's data endpoints are Laravel routes: each POST needs the session cookie from a GET of the page
 * that hosts the widget, plus that page's {@code csrf-token} meta value as {@code _token}.
 */
class NcdexAjax {

    private static final Pattern CSRF = Pattern.compile("<meta\\s+name=\"csrf-token\"\\s+content=\"([^\"]+)\"");

    private final HttpFetcher http;
    private final String baseUrl;
    private final Duration timeout;
    private final String pagePath;
    private String token;

    NcdexAjax(HttpFetcher http, String baseUrl, Duration timeout, String pagePath) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.pagePath = pagePath;
    }

    String post(String path, Map<String, String> params) throws IOException, InterruptedException {
        if (token == null) {
            token = csrfToken(http.get(baseUrl + pagePath, timeout));
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("_token", token);
        form.putAll(params);
        return http.postForm(baseUrl + path, form, baseUrl + pagePath, timeout);
    }

    static String csrfToken(String html) {
        Matcher m = CSRF.matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("NCDEX page has no csrf-token meta tag");
        }
        return m.group(1);
    }
}
