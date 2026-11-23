package com.local.bypass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Corre todos os modulos aplicaveis ao mesmo tempo e devolve o melhor relatorio
 * dentro do orcamento. O primeiro acerto definitivo encerra a corrida.
 */
final class Hub {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4,
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "bypass-module");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static final List<Module> MODULES = Arrays.<Module>asList(
            new CrawlerModule(),
            new WpProbe(),
            new SpaProbe(),
            new MetaProbe(),
            new ScriptProbe(),
            new LinkvertiseProbe(),
            new FormGateProbe(),
            new WebProbe());

    private Hub() {
    }

    static Report race(String url, long budgetMs) {
        long deadline = System.currentTimeMillis() + budgetMs;
        List<Module> active = new ArrayList<>();
        for (Module module : MODULES) {
            if (module.applies(url)) {
                active.add(module);
            }
        }
        if (active.isEmpty()) {
            return Report.none(url, "nenhum modulo aplicavel");
        }

        ExecutorCompletionService<Report> queue = new ExecutorCompletionService<>(POOL);
        for (final Module module : active) {
            final Module.Ctx ctx = new Module.Ctx(url, deadline);
            queue.submit(new java.util.concurrent.Callable<Report>() {
                @Override
                public Report call() {
                    try {
                        return module.run(ctx);
                    } catch (Exception exc) {
                        return null;
                    }
                }
            });
        }

        Report best = null;
        for (int index = 0; index < active.size(); index++) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                break;
            }
            Future<Report> future;
            try {
                future = queue.poll(left, TimeUnit.MILLISECONDS);
                if (future == null) {
                    break;
                }
                Report report = future.get();
                if (report == null) {
                    continue;
                }
                if (best == null || report.score() > best.score()) {
                    best = report;
                }
                if (report.ok && report.destination) {
                    break;
                }
            } catch (Exception ignored) {
                // proximo
            }
        }
        if (best == null) {
            return Report.none(url, "nenhum modulo respondeu");
        }
        return best;
    }
}
