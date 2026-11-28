package com.local.bypass;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Gate do plugin Encurtafy (WordPress). A pagina carrega a configuracao no
 * atributo data-wpl-config, com apiBase e restBase. O destino sai em duas
 * chamadas, sem timer, sem clicar em anuncio:
 *
 * <pre>
 * GET  {restBase}/final?dest=&amp;user=   -> link.id + captcha.token/timestamp
 * POST {apiBase}/click                 -> url_destino
 * </pre>
 */
final class EncurtafyModule implements Module {

    private static final String CONFIG_ATTR = "data-wpl-config=";
    private static final int TIMEOUT_MS = 4000;

    @Override
    public String name() {
        return "encurtafy";
    }

    @Override
    public boolean applies(String url) {
        return url != null && url.startsWith("http");
    }

    @Override
    public Report run(Module.Ctx ctx) throws Exception {
        long began = System.currentTimeMillis();
        String dest = param(ctx.url, "dest");
        if (dest.isEmpty()) {
            return null;
        }
        Http.Response page = Http.get(ctx.url, null, TIMEOUT_MS);
        if (page == null || page.body == null) {
            return null;
        }
        String config = config(page.body);
        if (config == null) {
            return null;
        }
        String restBase;
        String apiBase;
        try {
            JSONObject json = new JSONObject(config);
            restBase = json.optString("restBase", "");
            apiBase = json.optString("apiBase", "");
        } catch (Exception exc) {
            return null;
        }
        if (restBase.isEmpty() && apiBase.isEmpty()) {
            return null;
        }
        String base = restBase.isEmpty() ? origin(ctx.url) + "/wp-json/wpl/v1" : restBase;
        String user = param(ctx.url, "user");
        String query = "?dest=" + Http.enc(dest) + (user.isEmpty() ? "" : "&user=" + Http.enc(user));
        Http.Response prepared = Http.get(base + "/final" + query, null, TIMEOUT_MS);
        if (prepared == null || prepared.body == null) {
            return null;
        }
        String linkId;
        String captchaToken = "";
        long captchaTimestamp = 0L;
        try {
            JSONObject json = new JSONObject(prepared.body);
            if (!json.optBoolean("success", false)) {
                return null;
            }
            linkId = json.getJSONObject("link").optString("id", "");
            JSONObject captcha = json.optJSONObject("captcha");
            if (captcha != null) {
                captchaToken = captcha.optString("token", "");
                captchaTimestamp = captcha.optLong("timestamp", 0L);
            }
        } catch (Exception exc) {
            return null;
        }
        if (linkId.isEmpty() || apiBase.isEmpty()) {
            return null;
        }
        String payload = "{\"link_id\":\"" + linkId + "\""
                + ",\"captcha_token\":" + quote(captchaToken)
                + ",\"captcha_timestamp\":" + captchaTimestamp
                + ",\"captcha_answer\":\"final\",\"expected_answer\":\"final\"}";
        Http.Response click = Http.post(apiBase + "/click", payload, null, TIMEOUT_MS + 2000);
        if (click == null || click.body == null) {
            return null;
        }
        String destination = null;
        try {
            JSONObject json = new JSONObject(click.body);
            if (!json.optBoolean("success", true)) {
                return null;
            }
            destination = json.optString("url_destino", "");
            if (destination.isEmpty()) {
                destination = json.optString("redirect_url", "");
            }
        } catch (Exception exc) {
            return null;
        }
        if (destination.isEmpty()) {
            return null;
        }
        List<String> chain = new ArrayList<>();
        chain.add(ctx.url);
        chain.add(destination);
        String deeper = LinkApi.resolve(destination, TIMEOUT_MS);
        if (deeper != null) {
            chain.add(deeper);
            destination = deeper;
        }
        return Report.hit(name(), destination, chain, System.currentTimeMillis() - began);
    }

    private static String config(String body) {
        int at = body.indexOf(CONFIG_ATTR);
        if (at < 0) {
            return null;
        }
        char quote = body.charAt(at + CONFIG_ATTR.length());
        if (quote != '"' && quote != '\'') {
            return null;
        }
        int end = body.indexOf(quote, at + CONFIG_ATTR.length() + 1);
        if (end < 0) {
            return null;
        }
        return unescape(body.substring(at + CONFIG_ATTR.length() + 1, end));
    }

    private static String unescape(String value) {
        return value.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private static String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String param(String url, String name) {
        String marker = name + "=";
        int at = url.indexOf('?');
        if (at < 0) {
            return "";
        }
        for (String pair : url.substring(at + 1).split("&")) {
            if (pair.startsWith(marker)) {
                return pair.substring(marker.length());
            }
        }
        return "";
    }

    private static String origin(String url) {
        try {
            java.net.URL parsed = new java.net.URL(url);
            return parsed.getProtocol() + "://" + parsed.getHost();
        } catch (Exception exc) {
            return "";
        }
    }
}
