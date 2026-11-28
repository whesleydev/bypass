package com.local.bypass;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cliente minimo sem dependencia: sem seguir redirect, devolvendo os Set-Cookie. */
final class Http {

    private static final int CAP = 600_000;

    private Http() {
    }

    static final class Response {
        final int code;
        final String body;
        final List<String> cookies;

        Response(int code, String body, List<String> cookies) {
            this.code = code;
            this.body = body;
            this.cookies = cookies;
        }

        String cookieHeader() {
            StringBuilder builder = new StringBuilder();
            for (String cookie : cookies) {
                int end = cookie.indexOf(';');
                String pair = end < 0 ? cookie : cookie.substring(0, end);
                if (pair.indexOf('=') <= 0) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(pair);
            }
            return builder.toString();
        }
    }

    static Response get(String url, String cookie, int timeout) {
        return call(url, null, cookie, timeout);
    }

    static Response post(String url, String payload, String cookie, int timeout) {
        return call(url, payload, cookie, timeout);
    }

    static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exc) {
            return value;
        }
    }

    private static Response call(String url, String payload, String cookie, int timeout) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", Resolver.UA_MOBILE);
            connection.setRequestProperty("Accept", "application/json, text/html, */*");
            connection.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9");
            if (cookie != null && !cookie.isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                OutputStream out = connection.getOutputStream();
                out.write(payload.getBytes("UTF-8"));
                out.close();
            }
            int code = connection.getResponseCode();
            List<String> cookies = new ArrayList<>();
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
                if (header.getKey() != null && header.getKey().equalsIgnoreCase("Set-Cookie")) {
                    for (String value : header.getValue()) {
                        cookies.add(value);
                    }
                }
            }
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(code, read(stream), cookies);
        } catch (Exception exc) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > CAP) {
                    break;
                }
            }
            return buffer.toString("UTF-8");
        } catch (Exception exc) {
            return "";
        } finally {
            try {
                stream.close();
            } catch (Exception ignored) {
                // stream ja fechado
            }
        }
    }
}
