package com.local.bypass;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Rekonise: a API entrega o token na hora, mas o /unlock so responde depois de
 * ~20s de "permanencia". Polling resolve sem interacao.
 */
final class Rekonise {

    interface Listener {
        void onStatus(String message);

        void onResult(String url);

        void onError(String message);
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

    private static final long MIN_WAIT_MILLIS = 20_000L;
    private static final long MAX_WAIT_MILLIS = 75_000L;

    private Rekonise() {
    }

    static boolean isRekonise(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("rekonise.com") || lower.endsWith(".rekonise.com");
    }

    static void run(final String url, final Listener listener) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    execute(url, listener);
                } catch (Exception exc) {
                    listener.onError(message(exc));
                }
            }
        }, "rekonise");
        worker.setDaemon(true);
        worker.start();
    }

    private static void execute(String url, Listener listener) throws Exception {
        String path = new URL(url).getPath();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            listener.onError("link sem slug");
            return;
        }

        listener.onStatus("abrindo o gate…");
        String api = "https://api.rekonise.com/social-unlocks/" + path;
        String referer = "https://rekonise.com/" + path;

        String object = get(api, referer);
        if (object == null) {
            listener.onError("api do rekonise nao respondeu");
            return;
        }
        JSONObject json = new JSONObject(object);
        String token = json.optString("unlock_token", "");
        if (token.isEmpty()) {
            listener.onError("token nao veio no objeto");
            return;
        }

        String title = json.optString("title", "");
        if (!title.isEmpty()) {
            listener.onStatus("gate: " + title);
        }

        long started = System.currentTimeMillis();
        while (System.currentTimeMillis() - started < MAX_WAIT_MILLIS) {
            long elapsed = System.currentTimeMillis() - started;
            if (elapsed < MIN_WAIT_MILLIS) {
                listener.onStatus("aguardando liberação… " + (elapsed / 1000) + "s");
            }
            sleep(3000);

            String body = get(api + "/unlock?token=" + token, referer);
            if (body == null) {
                continue;
            }
            try {
                JSONObject unlock = new JSONObject(body);
                String destination = unlock.optString("url", "");
                if (destination.startsWith("http")) {
                    listener.onResult(destination);
                    return;
                }
            } catch (Exception ignored) {
                // 403 enquanto o gate ainda nao liberou
            }
        }
        listener.onError("gate nao liberou em " + (MAX_WAIT_MILLIS / 1000) + "s");
    }

    private static String get(String url, String referer) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Origin", "https://rekonise.com");
            conn.setRequestProperty("Referer", referer);
            conn.setRequestProperty("x-requested-with", "XMLHttpRequest");
            conn.setRequestProperty("Sec-Fetch-Dest", "empty");
            conn.setRequestProperty("Sec-Fetch-Mode", "cors");
            conn.setRequestProperty("Sec-Fetch-Site", "same-site");

            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            InputStream stream = conn.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
                total += read;
                if (total > 500_000) {
                    break;
                }
            }
            stream.close();
            return buffer.toString("UTF-8");
        } catch (Exception exc) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String message(Exception exc) {
        String text = exc.getMessage();
        return text == null ? exc.getClass().getSimpleName() : text;
    }
}
