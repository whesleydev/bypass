package com.local.bypass;

/** Contrato de um modulo de resolucao. */
interface Module {

    String name();

    /** Roda so quando faz sentido para a URL. */
    boolean applies(String url);

    /** Pode devolver null quando nao tem nada a dizer. */
    Report run(Ctx ctx) throws Exception;

    final class Ctx {
        final String url;
        final long deadline;

        Ctx(String url, long deadline) {
            this.url = url;
            this.deadline = deadline;
        }

        boolean expired() {
            return System.currentTimeMillis() > deadline;
        }
    }
}
