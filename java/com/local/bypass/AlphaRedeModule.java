package com.local.bypass;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gate da plataforma Alpha Rede (Next.js + tRPC + Prisma). A cadeia inteira e
 * stateless e sai por HTTP, sem WebView e sem clicar em anuncio:
 *
 * <pre>
 * POST /api/trpc/linkSession.create {linkId}      -> urlToken
 * GET  &lt;artigo&gt;?t=urlToken&amp;i=1                  -> Set-Cookie session_id/user_id
 * GET  /api/session-info                          -> sessionToken, totalStage, stageId
 * GET  /api/trpc/linkSession.nextStage?input=...  -> {url, destinationLink}
 * </pre>
 *
 * O destino aparece quando progress passa de totalStage. Sem linkId conhecido,
 * varre os primeiros registros do host e fica com a cadeia que contem a pagina.
 */
final class AlphaRedeModule implements Module {

    private static final int MAX_LINKS = 6;
    private static final int TIMEOUT_MS = 2600;
    private static final String PLACEHOLDER = "exemplo.com";

    @Override
    public String name() {
        return "alpharede";
    }

    @Override
    public boolean applies(String url) {
        return url != null && url.startsWith("http");
    }

    @Override
    public Report run(Module.Ctx ctx) throws Exception {
        long began = System.currentTimeMillis();
        String origin = origin(ctx.url);
        if (origin == null) {
            return null;
        }
        Response probe = get(origin + "/api/session-info", null, 1500);
        if (probe == null || probe.body == null || !probe.body.contains("hasSession")) {
            return null;
        }
        String direct = walk(ctx.url, origin, ctx.deadline);
        if (direct != null) {
            List<String> chain = new ArrayList<>();
            chain.add(ctx.url);
            chain.add(direct);
            return Report.hit(name(), direct, chain, System.currentTimeMillis() - began);
        }
        for (int linkId = 1; linkId <= MAX_LINKS; linkId++) {
            if (ctx.expired()) {
                break;
            }
            Session session = open(ctx.url, origin, linkId);
            if (session == null) {
                continue;
            }
            Walk walk = follow(origin, session, ctx.url, ctx.deadline, true);
            if (walk != null && walk.destination != null) {
                return Report.hit(name(), walk.destination, walk.chain,
                        System.currentTimeMillis() - began);
            }
        }
        return null;
    }

    private static String walk(String url, String origin, long deadline) {
        if (url.indexOf("?t=") < 0 && url.indexOf("&t=") < 0) {
            return null;
        }
        Response probe = get(url, null, TIMEOUT_MS);
        if (probe == null || probe.cookies.isEmpty()) {
            return null;
        }
        Session session = info(origin, join(probe.cookies));
        if (session == null) {
            return null;
        }
        Walk walk = follow(origin, session, url, deadline, false);
        return walk == null ? null : walk.destination;
    }

    private static Session open(String url, String origin, int linkId) {
        Response created = post(origin + "/api/trpc/linkSession.create",
                "{\"json\":{\"linkId\":" + linkId + "}}", null);
        if (created == null || created.body == null) {
            return null;
        }
        String token;
        try {
            token = new JSONObject(created.body)
                    .getJSONObject("result").getJSONObject("data")
                    .getJSONObject("json").optString("urlToken", null);
        } catch (Exception exc) {
            return null;
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        String entry = stripToken(url) + "?t=" + enc(token) + "&i=1";
        Response seeded = get(entry, null, TIMEOUT_MS);
        if (seeded == null || seeded.cookies.isEmpty()) {
            return null;
        }
        return info(origin, join(seeded.cookies));
    }

    private static String stripToken(String url) {
        int cut = url.indexOf('?');
        return cut < 0 ? url : url.substring(0, cut);
    }

    private static Session info(String origin, String cookie) {
        Response response = get(origin + "/api/session-info", cookie, TIMEOUT_MS);
        if (response == null || response.body == null) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(response.body);
            if (!json.optBoolean("hasSession", false)) {
                return null;
            }
            String token = json.optString("sessionToken", "");
            int total = json.optInt("totalStage", 0);
            long stageId = json.optLong("stageId", 0L);
            if (token.isEmpty() || total <= 0) {
                return null;
            }
            return new Session(cookie, token, total, stageId);
        } catch (Exception exc) {
            return null;
        }
    }

    private static Walk follow(String origin, Session session, String current, long deadline,
                               boolean mustOwnPage) {
        List<String> chain = new ArrayList<>();
        chain.add(current);
        String fallback = null;
        boolean owned = !mustOwnPage;
        for (int progress = 1; progress <= session.total + 1; progress++) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            String input = "{\"json\":{\"token\":\"" + session.token + "\",\"progress\":"
                    + progress + ",\"stageId\":" + session.stageId + "}}";
            String url = origin + "/api/trpc/linkSession.nextStage?input=" + enc(input);
            Response response = get(url, session.cookie, TIMEOUT_MS);
            if (response == null || response.body == null) {
                break;
            }
            String next = null;
            String destination = null;
            try {
                JSONObject json = new JSONObject(response.body)
                        .getJSONObject("result").getJSONObject("data").getJSONObject("json");
                next = json.optString("url", null);
                destination = json.optString("destinationLink", null);
            } catch (Exception exc) {
                break;
            }
            if (next != null && !next.isEmpty()) {
                chain.add(next);
                if (trim(next).equals(trim(current))) {
                    owned = true;
                }
            }
            if (destination != null && !destination.isEmpty() && !destination.contains(PLACEHOLDER)) {
                if (owned) {
                    return new Walk(destination, chain);
                }
                if (fallback == null) {
                    fallback = destination;
                }
            }
        }
        if (mustOwnPage) {
            return null;
        }
        return fallback == null ? null : new Walk(fallback, chain);
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exc) {
            return value;
        }
    }

    private static String trim(String url) {
        String value = url.trim();
        int cut = value.indexOf('#');
        if (cut > 0) {
            value = value.substring(0, cut);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String join(List<String> cookies) {
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

    private static String origin(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getProtocol() + "://" + parsed.getHost();
        } catch (Exception exc) {
            return null;
        }
    }

    private static final class Session {
        final String cookie;
        final String token;
        final int total;
        final long stageId;

        Session(String cookie, String token, int total, long stageId) {
            this.cookie = cookie;
            this.token = token;
            this.total = total;
            this.stageId = stageId;
        }
    }

    private static final class Walk {
        final String destination;
        final List<String> chain;

        Walk(String destination, List<String> chain) {
            this.destination = destination;
            this.chain = chain;
        }
    }

    private static final class Response {
        final int code;
        final String body;
        final List<String> cookies;

        Response(int code, String body, List<String> cookies) {
            this.code = code;
            this.body = body;
            this.cookies = cookies;
        }
    }

    private static Response get(String url, String cookie, int timeout) {
        return call(url, null, cookie, timeout);
    }

    private static Response post(String url, String payload, String cookie) {
        return call(url, payload, cookie, TIMEOUT_MS);
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
                if (buffer.size() > 400_000) {
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
                // sem stream
            }
        }
    }
}
