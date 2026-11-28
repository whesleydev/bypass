package com.local.bypass;

import java.util.ArrayList;
import java.util.List;

/** Aplica o LinkApi direto quando a URL de entrada ja e /r/TOKEN ou traz ?s=TOKEN. */
final class LinkApiModule implements Module {

    private static final int TIMEOUT_MS = 4000;

    @Override
    public String name() {
        return "linkapi";
    }

    @Override
    public boolean applies(String url) {
        return url != null && (url.contains("/r/") || url.contains("?s=") || url.contains("&s="));
    }

    @Override
    public Report run(Module.Ctx ctx) throws Exception {
        long began = System.currentTimeMillis();
        String destination = LinkApi.resolve(ctx.url, TIMEOUT_MS);
        if (destination == null) {
            return null;
        }
        List<String> chain = new ArrayList<>();
        chain.add(ctx.url);
        chain.add(destination);
        return Report.hit(name(), destination, chain, System.currentTimeMillis() - began);
    }
}
