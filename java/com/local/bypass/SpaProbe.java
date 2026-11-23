package com.local.bypass;

import java.util.ArrayList;
import java.util.List;

/** Modulo SPA: le o documento do Firestore ou a API embutida e segue o link real. */
final class SpaProbe implements Module {

    @Override
    public String name() {
        return "spa";
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
        List<String> links = Resolver.firestoreLinks(page);
        if (links.isEmpty()) {
            return null;
        }

        List<String> chain = new ArrayList<>();
        chain.add(ctx.url);
        int tried = 0;
        for (String link : links) {
            if (tried++ >= 4 || ctx.expired()) {
                break;
            }
            String host = Resolver.host(link);
            if (host.isEmpty() || Resolver.isJunk(host) || Resolver.isTracker(host)) {
                continue;
            }
            chain.add(link);
            if (Resolver.isDestination(link)) {
                return Report.hit("spa", link, chain, Resolver.millis(began));
            }
            Report deep = Resolver.crawl(link, 12, ctx.deadline);
            if (deep.ok) {
                List<String> merged = new ArrayList<>(chain);
                merged.addAll(deep.chain);
                return new Report("spa", deep.url, true, null, merged, false,
                        deep.destination, Resolver.millis(began));
            }
        }
        return chain.size() > 1
                ? Report.page("spa", chain.get(chain.size() - 1), chain, Resolver.millis(began))
                : null;
    }
}
