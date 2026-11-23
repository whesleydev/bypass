package com.local.bypass;

/** Modulo principal: segue a corrente de paginas por extracao estatica. */
final class CrawlerModule implements Module {

    @Override
    public String name() {
        return "crawler";
    }

    @Override
    public boolean applies(String url) {
        return true;
    }

    @Override
    public Report run(Module.Ctx ctx) {
        return Resolver.crawl(ctx.url, 24, ctx.deadline);
    }
}
