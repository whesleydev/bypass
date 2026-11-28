package com.local.bypass;

import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        Http.Response probe = Http.get(origin + "/api/session-info", null, 1500);
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
        Http.Response probe = Http.get(url, null, TIMEOUT_MS);
        if (probe == null || probe.cookies.isEmpty()) {
            return null;
        }
        Session session = info(origin, probe.cookieHeader());
        if (session == null) {
            return null;
        }
        Walk walk = follow(origin, session, url, deadline, false);
        return walk == null ? null : walk.destination;
    }

    private static Session open(String url, String origin, int linkId) {
        Http.Response created = Http.post(origin + "/api/trpc/linkSession.create",
                "{\"json\":{\"linkId\":" + linkId + "}}", null, TIMEOUT_MS);
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
        String entry = stripToken(url) + "?t=" + Http.enc(token) + "&i=1";
        Http.Response seeded = Http.get(entry, null, TIMEOUT_MS);
        if (seeded == null || seeded.cookies.isEmpty()) {
            return null;
        }
        return info(origin, seeded.cookieHeader());
    }

    private static String stripToken(String url) {
        int cut = url.indexOf('?');
        return cut < 0 ? url : url.substring(0, cut);
    }

    private static Session info(String origin, String cookie) {
        Http.Response response = Http.get(origin + "/api/session-info", cookie, TIMEOUT_MS);
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
            String url = origin + "/api/trpc/linkSession.nextStage?input=" + Http.enc(input);
            Http.Response response = Http.get(url, session.cookie, TIMEOUT_MS);
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





}
