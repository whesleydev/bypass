package com.local.bypass;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Modulo de metadados: og:url, canonical e JSON-LD costumam guardar o destino. */
final class MetaProbe implements Module {

    private static final Pattern OG_URL = Pattern.compile(
            "<meta[^>]+property=[\"']og:url[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANONICAL = Pattern.compile(
            "<link[^>]+rel=[\"']canonical[\"'][^>]+href=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "meta";
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
        List<String> found = new ArrayList<>();
        collect(found, OG_URL, page.body);
        collect(found, CANONICAL, page.body);

        String startHost = Resolver.host(ctx.url);
        for (String candidate : found) {
            String host = Resolver.host(candidate);
            if (host.isEmpty() || host.equals(startHost) || Resolver.isJunk(host)) {
                continue;
            }
            List<String> chain = new ArrayList<>();
            chain.add(ctx.url);
            chain.add(candidate);
            if (Resolver.isDestination(candidate)) {
                return Report.hit("meta", candidate, chain, Resolver.millis(began));
            }
            Report deep = Resolver.crawl(candidate, 10, ctx.deadline);
            if (deep.ok) {
                List<String> merged = new ArrayList<>(chain);
                merged.addAll(deep.chain);
                return new Report("meta", deep.url, true, null, merged, false,
                        deep.destination, Resolver.millis(began));
            }
        }
        return null;
    }

    private static void collect(List<String> out, Pattern pattern, String body) {
        if (body == null) {
            return;
        }
        Matcher matcher = pattern.matcher(body);
        while (matcher.find()) {
            String url = Resolver.clean(matcher.group(1));
            if (url != null && url.startsWith("http") && !out.contains(url)) {
                out.add(url);
            }
        }
    }
}
