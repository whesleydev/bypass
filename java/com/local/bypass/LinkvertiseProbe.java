package com.local.bypass;

import android.util.Base64;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Linkvertise entrega o destino pela API estatica: serial = base64 de
 * {timestamp, random, link_id}. Sem captcha, sem timer.
 */
final class LinkvertiseProbe implements Module {

    private static final String[] HOSTS = {
            "linkvertise.com", "linkvertise.net", "link-to.net", "up-to-down.net",
            "file-up.org", "mboost.me", "boost.ink", "linkvertise.link"
    };

    private static final Pattern ID_PATH = Pattern.compile("/(\\d{3,10})(?:/|\\?|$)");
    private static final Pattern ID_QUERY = Pattern.compile("[?&](?:id|link_id)=(\\d{3,10})");

    @Override
    public String name() {
        return "linkvertise";
    }

    @Override
    public boolean applies(String url) {
        String host = Resolver.host(url);
        for (String known : HOSTS) {
            if (host.equals(known) || host.endsWith("." + known)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Report run(Module.Ctx ctx) {
        long began = System.currentTimeMillis();
        String id = linkId(ctx.url);
        if (id == null) {
            return null;
        }
        String serial = serial(id);
        if (serial == null) {
            return null;
        }
        String endpoint = "https://publisher.linkvertise.com/api/v1/redirect/link/static/"
                + id + "?serial=" + serial;
        Resolver.Page page;
        try {
            page = Resolver.get(endpoint, 9000, 300_000, Resolver.UA_MOBILE);
        } catch (Exception exc) {
            return null;
        }
        if (page.code != 200 || page.body.isEmpty()) {
            return null;
        }
        String target = target(page.body);
        if (target == null) {
            return null;
        }
        List<String> chain = new ArrayList<>();
        chain.add(ctx.url);
        chain.add(target);
        return Report.hit("linkvertise", target, chain, Resolver.millis(began));
    }

    private static String linkId(String url) {
        String path = url;
        int hostEnd = url.indexOf('/', url.indexOf("://") + 3);
        if (hostEnd > 0) {
            path = url.substring(hostEnd);
        }
        Matcher direct = ID_PATH.matcher(path);
        if (direct.find()) {
            return direct.group(1);
        }
        Matcher query = ID_QUERY.matcher(url);
        if (query.find()) {
            return query.group(1);
        }
        return null;
    }

    private static String serial(String id) {
        try {
            JSONObject json = new JSONObject();
            json.put("timestamp", System.currentTimeMillis());
            json.put("random", randomString());
            json.put("link_id", Long.parseLong(id));
            byte[] bytes = json.toString().getBytes("UTF-8");
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception exc) {
            return null;
        }
    }

    private static String randomString() {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder text = new StringBuilder(10);
        java.util.Random random = new java.util.Random();
        for (int index = 0; index < 10; index++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }

    private static String target(String body) {
        try {
            JSONObject json = new JSONObject(body);
            if (!json.optBoolean("success", false)) {
                return null;
            }
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                return null;
            }
            JSONObject link = data.optJSONObject("link");
            if (link == null) {
                return null;
            }
            String target = link.optString("target", "");
            if (target.isEmpty()) {
                target = link.optString("target_url", "");
            }
            if (target.toLowerCase(Locale.ROOT).startsWith("http")) {
                return target;
            }
        } catch (Exception ignored) {
            // formato diferente
        }
        return null;
    }
}
