package com.local.bypass;

import java.util.Collections;
import java.util.List;

/**
 * Veredito de um modulo. Cada modulo devolve o proprio relatorio e o Hub escolhe
 * o melhor pelo score.
 */
final class Report {

    final String module;
    final String url;
    final boolean ok;
    final String note;
    final List<String> chain;
    final boolean needsJs;
    final boolean destination;
    final long ms;

    Report(String module, String url, boolean ok, String note, List<String> chain,
           boolean needsJs, boolean destination, long ms) {
        this.module = module;
        this.url = url;
        this.ok = ok;
        this.note = note;
        this.chain = chain == null ? Collections.<String>emptyList() : chain;
        this.needsJs = needsJs;
        this.destination = destination;
        this.ms = ms;
    }

    static Report hit(String module, String url, List<String> chain, long ms) {
        return new Report(module, url, true, null, chain, false, true, ms);
    }

    static Report page(String module, String url, List<String> chain, long ms) {
        return new Report(module, url, true, null, chain, false, false, ms);
    }

    static Report miss(String module, String url, String note, List<String> chain, long ms,
                       boolean needsJs) {
        return new Report(module, url, false, note, chain, needsJs, false, ms);
    }

    static Report none(String url, String note) {
        return new Report("hub", url, false, note, Collections.singletonList(url), false, false, 0L);
    }

    int score() {
        int value = ok ? 1000 : 0;
        if (destination) {
            value += 400;
        }
        value += Math.min(chain.size(), 40) * 8;
        value -= (int) Math.min(ms / 1000L, 60L);
        return value;
    }
}
