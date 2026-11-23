package com.local.bypass;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gates do tipo cuty/gplinks/shrinkme entregam o destino por POST de campos
 * ocultos. Este modulo preenche o formulario e segue a resposta.
 */
final class FormGateProbe implements Module {

    private static final Pattern FORM = Pattern.compile(
            "<form([^>]*)>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern METHOD = Pattern.compile(
            "method=[\"']?post[\"']?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION = Pattern.compile(
            "action=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT = Pattern.compile(
            "<input([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile(
            "name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile(
            "value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO_LINK = Pattern.compile(
            "href=[\"']([^\"']*/(?:go|out|redirect|link|download)[^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "form";
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
            page = Resolver.get(ctx.url, 8000, 400_000, Resolver.UA_MOBILE);
        } catch (Exception exc) {
            return null;
        }
        if (page.body.isEmpty() || page.body.length() > 300_000) {
            return null;
        }

        Matcher form = FORM.matcher(page.body);
        while (form.find()) {
            if (!METHOD.matcher(form.group(1)).find()) {
                continue;
            }
            Map<String, String> fields = fields(form.group(2));
            if (fields.isEmpty()) {
                continue;
            }
            String action = action(form.group(1), page.url);
            String payload = encode(fields);

            Resolver.Page response;
            try {
                response = Resolver.post(action, payload, 9000, 400_000);
            } catch (Exception exc) {
                continue;
            }

            List<String> chain = new ArrayList<>();
            chain.add(ctx.url);

            String next = null;
            if (response.location != null && response.location.startsWith("http")) {
                next = response.location;
            } else {
                Matcher go = GO_LINK.matcher(response.body);
                if (go.find()) {
                    next = Resolver.absolutize(action, Resolver.clean(go.group(1)));
                }
            }
            if (next == null || !next.startsWith("http")) {
                continue;
            }
            chain.add(next);
            if (Resolver.isDestination(next)) {
                return Report.hit("form", next, chain, Resolver.millis(began));
            }
            Report deep = Resolver.crawl(next, 12, ctx.deadline);
            if (deep.ok) {
                List<String> merged = new ArrayList<>(chain);
                merged.addAll(deep.chain);
                return new Report("form", deep.url, true, null, merged, false,
                        deep.destination, Resolver.millis(began));
            }
            return Report.page("form", next, chain, Resolver.millis(began));
        }
        return null;
    }

    private static Map<String, String> fields(String block) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher input = INPUT.matcher(block);
        while (input.find()) {
            String tag = input.group(1);
            if (tag.toLowerCase(Locale.ROOT).contains("type=\"submit\"")
                    || tag.toLowerCase(Locale.ROOT).contains("type=submit")) {
                continue;
            }
            Matcher name = NAME.matcher(tag);
            if (!name.find()) {
                continue;
            }
            Matcher value = VALUE.matcher(tag);
            out.put(name.group(1), value.find() ? value.group(1) : "");
        }
        return out;
    }

    private static String action(String attributes, String base) {
        Matcher matcher = ACTION.matcher(attributes);
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty()) {
                return Resolver.absolutize(base, value);
            }
        }
        return base;
    }

    private static String encode(Map<String, String> fields) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            try {
                body.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                body.append('=');
                body.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (Exception ignored) {
                // campo ignorado
            }
        }
        return body.toString();
    }
}
