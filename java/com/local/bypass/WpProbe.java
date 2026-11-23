package com.local.bypass;

import java.util.ArrayList;
import java.util.List;

/** Modulo WordPress: usa o indice REST do site para achar a proxima parte. */
final class WpProbe implements Module {

    @Override
    public String name() {
        return "wp";
    }

    @Override
    public boolean applies(String url) {
        String host = Resolver.host(url);
        if (host.isEmpty()) {
            return false;
        }
        return url.contains("?p=") || url.contains("/parte-") || url.contains("/part/");
    }

    @Override
    public Report run(Module.Ctx ctx) {
        long began = System.currentTimeMillis();
        String host = Resolver.host(ctx.url);
        List<String> chain = new ArrayList<>();
        chain.add(ctx.url);
        String current = ctx.url;
        String title;
        try {
            title = Resolver.get(ctx.url, 8000, 300_000, Resolver.UA_MOBILE).title;
        } catch (Exception exc) {
            return null;
        }

        for (int hop = 0; hop < 14 && !ctx.expired(); hop++) {
            String next = Resolver.wpNext(host, title);
            if (next == null || chain.contains(next)) {
                break;
            }
            chain.add(next);
            current = next;
            if (Resolver.isDestination(current)) {
                return Report.hit("wp", current, chain, Resolver.millis(began));
            }
            try {
                title = Resolver.get(next, 8000, 300_000, Resolver.UA_MOBILE).title;
            } catch (Exception exc) {
                break;
            }
        }

        if (chain.size() > 1) {
            return Report.page("wp", current, chain, Resolver.millis(began));
        }
        return null;
    }
}
