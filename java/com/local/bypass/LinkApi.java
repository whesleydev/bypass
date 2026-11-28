package com.local.bypass;

import org.json.JSONObject;

import java.net.URL;
import java.util.Locale;

/**
 * Gates SPA que servem /p?s=TOKEN e leem o destino em /api/links/TOKEN
 * (campo originalUrl). Tambem aceita o atalho /r/TOKEN.
 */
final class LinkApi {

    private static final String[] PATHS = {"/api/links/", "/api/link/"};

    private LinkApi() {
    }

    static String resolve(String url, int timeout) {
        String origin = origin(url);
        String token = token(url);
        if (origin.isEmpty() || token == null) {
            return null;
        }
        for (String path : PATHS) {
            Http.Response response = Http.get(origin + path + Http.enc(token), null, timeout);
            if (response == null || response.body == null
                    || !response.body.contains("originalUrl")) {
                continue;
            }
            try {
                String target = new JSONObject(response.body).optString("originalUrl", "");
                if (!target.isEmpty()) {
                    return target;
                }
            } catch (Exception exc) {
                // formato diferente, tenta o proximo caminho
            }
        }
        return null;
    }

    private static String token(String url) {
        try {
            URL parsed = new URL(url);
            String query = parsed.getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    if (pair.startsWith("s=") && pair.length() > 2) {
                        return pair.substring(2);
                    }
                }
            }
            String path = parsed.getPath();
            if (path != null && path.startsWith("/r/")) {
                String slug = path.substring(3);
                int cut = slug.indexOf('/');
                return (cut < 0 ? slug : slug.substring(0, cut)).trim();
            }
            return null;
        } catch (Exception exc) {
            return null;
        }
    }

    static String origin(String url) {
        try {
            URL parsed = new URL(url);
            String host = parsed.getHost().toLowerCase(Locale.ROOT);
            int port = parsed.getPort();
            return parsed.getProtocol() + "://" + host + (port > 0 && port != 443 ? ":" + port : "");
        } catch (Exception exc) {
            return "";
        }
    }
}
