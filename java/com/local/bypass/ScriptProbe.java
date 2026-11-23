package com.local.bypass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modulo de payload: gates costumam esconder o destino em script inline.
 * Decodifica escapes hex, escapes unicode, fromCharCode, hex puro, base64 e rot13
 * antes de procurar URL.
 */
final class ScriptProbe implements Module {

    private static final Pattern HEX_ESC = Pattern.compile("((?:\\\\x[0-9a-fA-F]{2}){10,})");
    private static final Pattern UNI_ESC = Pattern.compile("((?:\\\\u[0-9a-fA-F]{4}){10,})");
    private static final Pattern FROM_CHAR = Pattern.compile(
            "fromCharCode\\s*\\(\\s*([0-9][0-9,\\s]{20,})\\)");
    private static final Pattern HEX_RUN = Pattern.compile("\\b([0-9a-fA-F]{40,})\\b");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>\\\\{}|^`\\[\\]]+");

    @Override
    public String name() {
        return "script";
    }

    @Override
    public boolean applies(String url) {
        return true;
    }

    @Override
    public Report run(Module.Ctx ctx) {
        long began = System.currentTimeMillis();
        Resolver.Page page;
        try {
            page = Resolver.get(ctx.url, 8000, 500_000, Resolver.UA_MOBILE);
        } catch (Exception exc) {
            return null;
        }
        String body = page.body;
        if (body.length() < 200) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        decodeInto(candidates, HEX_ESC, body, 0);
        decodeInto(candidates, UNI_ESC, body, 1);
        decodeInto(candidates, FROM_CHAR, body, 2);
        decodeInto(candidates, HEX_RUN, body, 3);
        if (body.contains("uggcf://")) {
            collect(candidates, rot13(body));
        }

        String startHost = Resolver.host(ctx.url);
        int tried = 0;
        for (String candidate : candidates) {
            if (tried++ >= 3 || ctx.expired()) {
                break;
            }
            String host = Resolver.host(candidate);
            if (host.isEmpty() || host.equals(startHost)
                    || Resolver.isJunk(host) || Resolver.isTracker(host)) {
                continue;
            }
            List<String> chain = new ArrayList<>();
            chain.add(ctx.url);
            chain.add(candidate);
            if (Resolver.isDestination(candidate)) {
                return Report.hit("script", candidate, chain, Resolver.millis(began));
            }
            Report deep = Resolver.crawl(candidate, 10, ctx.deadline);
            if (deep.ok) {
                List<String> merged = new ArrayList<>(chain);
                merged.addAll(deep.chain);
                return new Report("script", deep.url, true, null, merged, false,
                        deep.destination, Resolver.millis(began));
            }
        }
        return null;
    }

    private static void decodeInto(List<String> out, Pattern pattern, String body, int mode) {
        Matcher matcher = pattern.matcher(body);
        int hits = 0;
        while (matcher.find() && hits++ < 3) {
            String decoded = decode(matcher.group(1), mode);
            if (decoded != null && decoded.contains("http")) {
                collect(out, decoded);
            }
        }
    }

    private static String decode(String value, int mode) {
        try {
            switch (mode) {
                case 0:
                case 1:
                    return unescape(value);
                case 2: {
                    StringBuilder text = new StringBuilder();
                    for (String piece : value.split(",")) {
                        String trimmed = piece.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        text.append((char) Integer.parseInt(trimmed));
                    }
                    return text.toString();
                }
                case 3: {
                    StringBuilder text = new StringBuilder();
                    for (int index = 0; index + 1 < value.length(); index += 2) {
                        text.append((char) Integer.parseInt(value.substring(index, index + 2), 16));
                    }
                    return text.toString();
                }
                default:
                    return null;
            }
        } catch (Exception exc) {
            return null;
        }
    }

    private static String unescape(String value) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\\' && index + 1 < value.length()) {
                char kind = value.charAt(index + 1);
                if ((kind == 'x' || kind == 'u') && index + 5 < value.length()) {
                    int width = kind == 'x' ? 2 : 4;
                    try {
                        text.append((char) Integer.parseInt(
                                value.substring(index + 2, index + 2 + width), 16));
                        index += 1 + width;
                        continue;
                    } catch (Exception ignored) {
                        // mantem o texto cru
                    }
                }
            }
            text.append(current);
        }
        return text.toString();
    }

    private static String rot13(String value) {
        StringBuilder text = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current >= 'a' && current <= 'z') {
                text.append((char) ('a' + (current - 'a' + 13) % 26));
            } else if (current >= 'A' && current <= 'Z') {
                text.append((char) ('A' + (current - 'A' + 13) % 26));
            } else {
                text.append(current);
            }
        }
        return text.toString();
    }

    private static void collect(List<String> out, String text) {
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String url = Resolver.clean(matcher.group());
            if (url != null && url.startsWith("http") && !out.contains(url)) {
                out.add(url);
            }
        }
    }
}
