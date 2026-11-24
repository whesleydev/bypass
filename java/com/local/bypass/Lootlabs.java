package com.local.bypass;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Fluxo nativo do lootlabs: sessao -> params -> /tc -> ws.
 * O destino chega por websocket depois que o captcha (turnstile) e validado.
 */
final class Lootlabs {

    interface Listener {
        void onStatus(String message);

        void onCaptcha(String captchaUrl);

        void onResult(String url);

        void onError(String message);
    }

    static final class Task {
        final String urid;
        final int taskId;
        final String sessionId;
        final String adUrl;
        final String title;

        Task(String urid, int taskId, String sessionId, String adUrl, String title) {
            this.urid = urid;
            this.taskId = taskId;
            this.sessionId = sessionId;
            this.adUrl = adUrl;
            this.title = title;
        }
    }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

    private static final Pattern SESSION_RE =
            Pattern.compile("<script>document\\.session = '([^']*)'</script>");

    private static final Pattern CDN_RE =
            Pattern.compile("p\\['CDN_DOMAIN'\\]\\s*=\\s*'([^']*)'");

    private static final Pattern TID_RE =
            Pattern.compile("p\\['TID'\\]\\s*=\\s*(\\d+)");

    private static final Pattern KEY_RE =
            Pattern.compile("p\\['KEY'\\]\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern PVAR_RE =
            Pattern.compile("p\\['(\\w+)'\\]\\s*=\\s*([^\\n;]+)");

    private static final String[] LOOT_HOSTS = {
            "lootlabs.gg", "links.lootlabs.gg", "lootlinks.co", "lootlinks.com",
            "loot-links.com", "loot-link.com", "lootlink.org", "linksloot.net",
            "lootdest.com", "lootdest.info", "lootdest.org", "links-loot.com",
            "lootlabs.com", "lootlink.io", "loot-links.net"};

    private static final class Handle {
        private volatile String token;
        volatile boolean resolved;

        void submit(String value) {
            token = value;
        }

        String take() {
            String value = token;
            token = null;
            return value;
        }
    }

    private static volatile Handle active;

    static void submitCaptchaToken(String token) {
        Handle handle = active;
        if (handle != null && token != null && token.length() > 10) {
            handle.submit(token);
        }
    }

    private Lootlabs() {
    }

    static boolean isLootlabs(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (String candidate : LOOT_HOSTS) {
            if (lower.equals(candidate) || lower.endsWith("." + candidate)) {
                return true;
            }
        }
        return false;
    }

    static void run(final String url, final Listener listener) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    execute(url, listener);
                } catch (Exception exc) {
                    listener.onError(describe(exc));
                }
            }
        }, "lootlabs");
        worker.setDaemon(true);
        worker.start();
    }

    private static void execute(String url, Listener listener) throws Exception {
        String host = new URL(url).getHost();
        String origin = "https://" + host;
        CookieJar jar = new CookieJar();

        listener.onStatus("abrindo o gate…");
        Response page = get(url, headers(jar, origin, null), null);
        if (page.code != 200) {
            listener.onError("gate respondeu " + page.code);
            return;
        }

        Matcher sessionMatch = SESSION_RE.matcher(page.body);
        if (!sessionMatch.find()) {
            listener.onError("sessao do gate nao encontrada");
            return;
        }
        String sessionUuid = sessionMatch.group(1);

        post(origin + "/verify", "{\"session\":\"" + sessionUuid + "\"}",
                headers(jar, origin, "text/plain;charset=UTF-8"), null);

        Matcher cdnMatch = CDN_RE.matcher(page.body);
        Matcher tidMatch = TID_RE.matcher(page.body);
        Matcher keyMatch = KEY_RE.matcher(page.body);
        if (!cdnMatch.find() || !tidMatch.find() || !keyMatch.find()) {
            listener.onError("parametros do gate nao encontrados");
            return;
        }
        String cdn = cdnMatch.group(1);
        String tid = tidMatch.group(1);
        String key = keyMatch.group(1);
        Map<String, String> pvars = pvars(page.body);

        listener.onStatus("lendo configuracao…");
        Response paramsResp = get("https://" + cdn + "/?tid=" + tid + "&params_only=1",
                headers(jar, origin, null), null);
        JSONArray params = parseParams(paramsResp.body);
        if (params == null || params.length() < 31) {
            listener.onError("configuracao do gate invalida");
            return;
        }
        String wsDomain = params.optString(9, "onsultingco.com");
        String tcDomain = params.optString(29, "nerventualken.com");
        boolean allowUnlocker = params.optBoolean(30, true);

        listener.onStatus("pedindo tarefas…");
        JSONObject payload = new JSONObject();
        payload.put("tid", Integer.parseInt(tid));
        payload.put("bl", new JSONArray(new int[]{18, 2, 33, 7, 21, 49}));
        payload.put("session", randomDigits(18));
        payload.put("max_tasks", 1);
        payload.put("design_id", 135);
        payload.put("cur_url", url);
        payload.put("doc_ref", "");
        payload.put("tier_id", "0");
        payload.put("num_of_tasks", "1");
        payload.put("is_loot", true);
        payload.put("rkey", key);
        payload.put("cookie_id", randomDigits(9));
        payload.put("botd", botd(sessionUuid));
        payload.put("botds", sessionUuid);
        payload.put("offer", pvars.containsKey("OFFER") ? pvars.get("OFFER") : "0");
        payload.put("ver", pvars.containsKey("WIDGET_VERSION") ? pvars.get("WIDGET_VERSION") : "v1");
        payload.put("tasks_timer", pvars.containsKey("TASKS_TIMER") ? pvars.get("TASKS_TIMER") : "-1");
        payload.put("test_unlocker_app", parseInt(pvars.get("TEST_UNLOCKER_APP"), -1));
        payload.put("allow_unlocker", allowUnlocker);
        payload.put("show_unlocker", true);
        payload.put("desktop_design", 0);
        payload.put("unlocker_only", 0);
        payload.put("additional_info", new JSONObject());
        payload.put("taboola_user_sync", "0");

        Response tcResp = post("https://" + tcDomain + "/tc", payload.toString(),
                headers(jar, origin, "application/json"), null);
        if (tcResp.code != 200) {
            listener.onError("/tc respondeu " + tcResp.code);
            return;
        }

        List<Task> tasks = parseTasks(tcResp.body);
        if (tasks.isEmpty()) {
            listener.onError("nenhuma tarefa retornada");
            return;
        }

        Task captcha = null;
        for (Task task : tasks) {
            if (task.taskId == 41 || (task.title != null
                    && task.title.toLowerCase(Locale.ROOT).contains("human"))) {
                captcha = task;
                break;
            }
        }
        Task bind = captcha != null ? captcha : tasks.get(0);

        listener.onStatus("conectando…");
        String wsUrl = "wss://" + shardOf(bind.urid)
                + "." + wsDomain + "/c?uid=" + bind.urid + "&cat=" + bind.taskId
                + "&key=" + key + "&session_id="
                + (bind.sessionId == null ? "undefined" : bind.sessionId)
                + "&is_loot=1&tid=" + tid;

        WsClient socket = WsClient.open(wsUrl, origin);
        if (socket == null) {
            listener.onError("nao consegui abrir o websocket");
            return;
        }

        final Handle handle = new Handle();
        active = handle;
        final String captchaUrid = bind.urid;
        final int captchaCat = bind.taskId;
        final String syncer = tcDomain;
        final String captchaTid = tid;

        for (Task task : tasks) {
            if (captcha != null && task == captcha) {
                continue;
            }
            get("https://" + tcDomain + "/td?ac=auto_complete&urid=" + task.urid
                    + "&cat=" + task.taskId + "&tid=" + tid, headers(jar, origin, null), null);
        }

        if (captcha != null && captcha.adUrl != null) {
            listener.onStatus("captcha necessario");
            listener.onCaptcha(captcha.adUrl);
        } else {
            listener.onStatus("aguardando liberacao…");
        }

        long deadline = System.currentTimeMillis() + 180_000L;
        long nextPing = 0;
        while (System.currentTimeMillis() < deadline) {
            if (System.currentTimeMillis() >= nextPing) {
                socket.sendText("0");
                nextPing = System.currentTimeMillis() + 10_000L;
            }
            String token = handle.take();
            if (token != null) {
                listener.onStatus("validando captcha…");
                verifyAsync(handle, listener, syncer, captchaUrid, captchaCat, captchaTid,
                        token, jar, origin);
            }

            String message = socket.read(1000);
            if (message == null) {
                continue;
            }
            if ("Refresh Page".equals(message)) {
                listener.onError("gate pediu refresh");
                socket.close();
                return;
            }
            if (message.startsWith("r:")) {
                handle.resolved = true;
                String payloadText = filterPrintable(message.substring(2).trim());
                String result = payloadText.startsWith("http")
                        ? payloadText : xorDecode(payloadText);
                socket.close();
                if (result != null && result.startsWith("http")) {
                    listener.onResult(result);
                } else {
                    listener.onError("resposta do gate ilegivel");
                }
                return;
            }
        }
        socket.close();
        listener.onError("tempo esgotado esperando o destino");
    }

    private static void verifyAsync(final Handle handle, final Listener listener,
                                    final String syncer, final String urid, final int cat,
                                    final String tid, final String token,
                                    final CookieJar jar, final String origin) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                String outcome = verifyCaptcha(syncer, urid, cat, tid, token, jar, origin);
                listener.onStatus("verify " + outcome);
                if (outcome.startsWith("200") && !handle.resolved) {
                    try {
                        Thread.sleep(7000L);
                    } catch (InterruptedException ignored) {
                    }
                    if (!handle.resolved) {
                        String retry = verifyCaptcha(syncer, urid, cat, tid, token, jar, origin);
                        listener.onStatus("verify " + retry + " (retry)");
                    }
                }
            }
        }, "captcha-verify");
        worker.setDaemon(true);
        worker.start();
    }

    private static String verifyCaptcha(String syncer, String urid, int cat, String tid,
                                        String token, CookieJar jar, String origin) {
        try {
            JSONObject body = new JSONObject();
            body.put("urid", urid);
            body.put("token", token);

            Map<String, String> verifyHeaders = headers(jar, origin, "application/json");
            verifyHeaders.put("Accept", "*/*");
            verifyHeaders.put("Referer", origin + "/captcha?urid=" + urid);
            Response verify = post(origin + "/captcha/verify", body.toString(), verifyHeaders, jar);

            Map<String, String> doneHeaders = headers(jar, origin, null);
            doneHeaders.put("Referer", origin + "/captcha?urid=" + urid);
            get(origin + "/td?ac=captcha&urid=" + urid + "&=&cat=" + cat + "&tid=" + tid,
                    doneHeaders, jar);
            String snippet = verify.body == null ? "" : verify.body.trim();
            if (snippet.length() > 90) {
                snippet = snippet.substring(0, 90);
            }
            return verify.code + (snippet.isEmpty() ? "" : " " + snippet.replace('\n', ' '));
        } catch (Exception error) {
            return "-1 " + error.getClass().getSimpleName();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static final class CookieJar {
        final Map<String, String> cookies = new HashMap<>();

        synchronized void store(Response response) {
            List<String> raw = response.setCookies;
            if (raw == null) {
                return;
            }
            for (String item : raw) {
                int semicolon = item.indexOf(';');
                String pair = semicolon > 0 ? item.substring(0, semicolon) : item;
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    cookies.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
                }
            }
        }

        synchronized String header() {
            if (cookies.isEmpty()) {
                return null;
            }
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return builder.toString();
        }
    }

    private static Map<String, String> headers(CookieJar jar, String origin, String contentType) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", UA);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Accept-Encoding", "identity");
        headers.put("Origin", origin);
        headers.put("Referer", origin + "/");
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
        String cookie = jar.header();
        if (cookie != null) {
            headers.put("Cookie", cookie);
        }
        return headers;
    }

    private static final class Response {
        final int code;
        final String body;
        final List<String> setCookies;

        Response(int code, String body, List<String> setCookies) {
            this.code = code;
            this.body = body;
            this.setCookies = setCookies;
        }
    }

    private static Response get(String url, Map<String, String> headers, CookieJar jar)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        int code = conn.getResponseCode();
        String body = readBody(conn, code);
        List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
        Response response = new Response(code, body, cookies);
        if (jar != null) {
            jar.store(response);
        }
        conn.disconnect();
        return response;
    }

    private static Response post(String url, String payload, Map<String, String> headers,
                                 CookieJar jar) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        OutputStream out = conn.getOutputStream();
        out.write(payload.getBytes("UTF-8"));
        out.close();
        int code = conn.getResponseCode();
        String body = readBody(conn, code);
        List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
        Response response = new Response(code, body, cookies);
        if (jar != null) {
            jar.store(response);
        }
        conn.disconnect();
        return response;
    }

    private static String readBody(HttpURLConnection conn, int code) {
        try {
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
                total += read;
                if (total > 900_000) {
                    break;
                }
            }
            stream.close();
            return buffer.toString("UTF-8");
        } catch (Exception exc) {
            return "";
        }
    }

    private static JSONArray parseParams(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("(")) {
            text = text.substring(1);
        }
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        if (text.endsWith(")")) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            return new JSONArray("[" + text + "]");
        } catch (Exception exc) {
            return null;
        }
    }

    private static Map<String, String> pvars(String html) {
        Map<String, String> values = new HashMap<>();
        Matcher matcher = PVAR_RE.matcher(html);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2).trim();
            if ((value.startsWith("'") && value.endsWith("'"))
                    || (value.startsWith("\"") && value.endsWith("\""))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    private static List<Task> parseTasks(String raw) {
        List<Task> tasks = new ArrayList<>();
        try {
            String text = raw.trim();
            JSONArray array = text.startsWith("[") ? new JSONArray(text)
                    : new JSONArray("[" + text + "]");
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                tasks.add(new Task(item.optString("urid", ""),
                        item.optInt("task_id", 0),
                        item.optString("session_id", null),
                        item.optString("ad_url", null),
                        item.optString("title", "")));
            }
        } catch (Exception ignored) {
        }
        return tasks;
    }

    private static String botd(String uuid) throws Exception {
        int nonce = solveNonce(uuid);
        JSONObject bot = new JSONObject();
        bot.put("bot", false);
        bot.put("timestamp", System.currentTimeMillis());
        JSONObject gl = new JSONObject();
        gl.put("uuid", uuid);
        gl.put("nonce", nonce);
        gl.put("time", (int) (nonce * (1.15 + Math.random() * 0.25)) + 20
                + (int) (Math.random() * 60));
        bot.put("webGLSolution", gl);

        byte[] keyBytes = sha256(transformUuid(uuid).getBytes("UTF-8"));
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(bot.toString().getBytes("UTF-8"));
        byte[] joined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, joined, 0, iv.length);
        System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
        bot.put("encrypted", Base64.encodeToString(joined, Base64.NO_WRAP));
        return bot.toString();
    }

    private static int solveNonce(String uuid) {
        String hex = uuid.replace("-", "");
        double seed = Long.parseLong(hex.substring(0, 8), 16) / 4294967295.0;
        for (int nonce = 0; nonce < 100000; nonce++) {
            double value = fract(seed, nonce);
            value = fract(value, nonce + 1);
            value = fract(value, nonce + 2);
            if (value < 0.001) {
                return nonce;
            }
        }
        return 99999;
    }

    private static double fract(double a, double b) {
        double value = 43758.5453 * Math.sin(12.9898 * a + 78.233 * b);
        return value - Math.floor(value);
    }

    private static String transformUuid(String uuid) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < uuid.length() && key.length() < 4; i++) {
            char ch = uuid.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                key.append(ch);
            }
        }
        if (key.length() == 0) {
            key.append("KEY1");
        }
        byte[] uuidBytes = uuid.getBytes();
        byte[] keyBytes = key.toString().getBytes();
        byte[] out = new byte[uuidBytes.length];
        for (int i = 0; i < uuidBytes.length; i++) {
            out[i] = (byte) (uuidBytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static String xorDecode(String encoded) {
        try {
            byte[] raw = Base64.decode(encoded, Base64.DEFAULT);
            if (raw.length < 6) {
                return null;
            }
            byte[] key = new byte[5];
            System.arraycopy(raw, 0, key, 0, 5);
            byte[] out = new byte[raw.length - 5];
            for (int i = 5; i < raw.length; i++) {
                out[i - 5] = (byte) (raw[i] ^ key[(i - 5) % 5]);
            }
            return new String(out, "UTF-8").trim();
        } catch (Exception exc) {
            return null;
        }
    }

    private static String filterPrintable(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 32 && ch <= 126) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String randomDigits(int count) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder();
        builder.append(1 + random.nextInt(9));
        for (int i = 0; i < count - 1; i++) {
            builder.append(random.nextInt(10));
        }
        return builder.toString();
    }

    private static int shardOf(String urid) {
        try {
            String digits = urid.length() > 5 ? urid.substring(urid.length() - 5) : urid;
            return Integer.parseInt(digits) % 3;
        } catch (Exception exc) {
            return Math.abs(urid.hashCode()) % 3;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exc) {
            return fallback;
        }
    }

    private static String describe(Exception exc) {
        String message = exc.getMessage();
        return message == null ? exc.getClass().getSimpleName() : message;
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exc) {
            return value;
        }
    }

    // ------------------------------------------------------------- websocket

    static final class WsClient {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        private WsClient(Socket socket) throws Exception {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        static WsClient open(String url, String origin) {
            try {
                URI uri = new URI(url);
                boolean tls = "wss".equalsIgnoreCase(uri.getScheme());
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : (tls ? 443 : 80);
                Socket raw = new Socket();
                raw.connect(new InetSocketAddress(host, port), 12000);
                raw.setSoTimeout(15000);
                if (tls) {
                    SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                            .createSocket(raw, host, port, true);
                    ssl.startHandshake();
                    raw = ssl;
                }
                WsClient client = new WsClient(raw);
                byte[] nonce = new byte[16];
                new SecureRandom().nextBytes(nonce);
                String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
                String path = uri.getRawPath();
                if (uri.getRawQuery() != null) {
                    path += "?" + uri.getRawQuery();
                }
                String request = "GET " + path + " HTTP/1.1\r\n"
                        + "Host: " + host + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + key + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n"
                        + "Origin: " + origin + "\r\n"
                        + "User-Agent: " + UA + "\r\n"
                        + "Accept-Language: en-US,en;q=0.9\r\n"
                        + "\r\n";
                client.out.write(request.getBytes("UTF-8"));
                client.out.flush();

                ByteArrayOutputStream head = new ByteArrayOutputStream();
                int matched = 0;
                while (head.size() < 8192) {
                    int ch = client.in.read();
                    if (ch < 0) {
                        return null;
                    }
                    head.write(ch);
                    if ((matched == 0 || matched == 2) && ch == '\r') {
                        matched++;
                    } else if ((matched == 1 || matched == 3) && ch == '\n') {
                        matched++;
                    } else {
                        matched = 0;
                    }
                    if (matched == 4) {
                        break;
                    }
                }
                String response = head.toString("ISO-8859-1");
                if (!response.startsWith("HTTP/1.1 101") && !response.startsWith("HTTP/1.0 101")) {
                    return null;
                }
                return client;
            } catch (Exception exc) {
                return null;
            }
        }

        void sendText(String text) {
            try {
                byte[] payload = text.getBytes("UTF-8");
                byte[] mask = new byte[4];
                new SecureRandom().nextBytes(mask);
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x81);
                if (payload.length < 126) {
                    frame.write(0x80 | payload.length);
                } else if (payload.length < 65536) {
                    frame.write(0x80 | 126);
                    frame.write((payload.length >> 8) & 0xff);
                    frame.write(payload.length & 0xff);
                } else {
                    frame.write(0x80 | 127);
                    for (int i = 7; i >= 0; i--) {
                        frame.write((int) (((long) payload.length >> (8 * i)) & 0xff));
                    }
                }
                frame.write(mask);
                for (int i = 0; i < payload.length; i++) {
                    frame.write(payload[i] ^ mask[i % 4]);
                }
                out.write(frame.toByteArray());
                out.flush();
            } catch (Exception ignored) {
            }
        }

        String read(int timeoutMillis) {
            try {
                socket.setSoTimeout(timeoutMillis);
                int first = in.read();
                if (first < 0) {
                    return null;
                }
                int second = in.read();
                if (second < 0) {
                    return null;
                }
                int opcode = first & 0x0f;
                long length = second & 0x7f;
                if (length == 126) {
                    length = ((long) in.read() << 8) | in.read();
                } else if (length == 127) {
                    length = 0;
                    for (int i = 0; i < 8; i++) {
                        length = (length << 8) | in.read();
                    }
                }
                byte[] payload = new byte[(int) length];
                int offset = 0;
                while (offset < length) {
                    int read = in.read(payload, offset, (int) length - offset);
                    if (read < 0) {
                        return null;
                    }
                    offset += read;
                }
                if (opcode == 0x8) {
                    return null;
                }
                if (opcode == 0x9) {
                    sendPong(payload);
                    return "";
                }
                if (opcode == 0x1 || opcode == 0x0) {
                    return new String(payload, "UTF-8");
                }
                return "";
            } catch (java.net.SocketTimeoutException timeout) {
                return "";
            } catch (Exception exc) {
                return null;
            }
        }

        private void sendPong(byte[] payload) {
            try {
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x8a);
                frame.write(0x80 | (payload.length & 0x7f));
                frame.write(new byte[]{0, 0, 0, 0});
                frame.write(payload);
                out.write(frame.toByteArray());
                out.flush();
            } catch (Exception ignored) {
            }
        }

        void close() {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
