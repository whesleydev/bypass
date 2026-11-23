package com.local.bypass;

import java.util.Collections;

/** Modulo de navegador: sinaliza pagina que so abre com JS ou desafio real. */
final class WebProbe implements Module {

    @Override
    public String name() {
        return "web";
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
        if (Resolver.isChallenge(page)) {
            return Report.miss("web", ctx.url, "challenge real (cloudflare)",
                    Collections.singletonList(ctx.url), Resolver.millis(began), true);
        }
        if (Resolver.needsJs(page)) {
            return Report.miss("web", ctx.url, "pagina com js",
                    Collections.singletonList(ctx.url), Resolver.millis(began), true);
        }
        return null;
    }
}
