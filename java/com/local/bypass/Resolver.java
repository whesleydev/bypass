package com.local.bypass;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

final class Resolver {

    static final class Result {
        final boolean ok;
        final String url;
        final String error;
        final List<String> chain;
        final long millis;
        final boolean needsJs;

        Result(boolean ok, String url, String error, List<String> chain, long millis) {
            this(ok, url, error, chain, millis, false);
        }

        Result(boolean ok, String url, String error, List<String> chain, long millis,
               boolean needsJs) {
            this.ok = ok;
            this.url = url;
            this.error = error;
            this.chain = chain;
            this.millis = millis;
            this.needsJs = needsJs;
        }
    }

    static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 14; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36";
    static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

    static final int MAX_PARALLEL = 6;
    static final int MAX_CANDIDATES = 8;
    static final long TOTAL_BUDGET_MS = 65_000L;
    static final int MAX_TIMER_WAIT_MS = 6_000;

    private static final ExecutorService VERIFIER = Executors.newFixedThreadPool(
            MAX_PARALLEL, new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "bypass-verify");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private static final Map<String, Boolean> VERIFIED = new ConcurrentHashMap<>();
    private static final Map<String, Page> CACHE = new ConcurrentHashMap<>();

    static {
        try {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        } catch (Exception ignored) {
            // sem cookie jar: segue sem sessao
        }
    }

    private static final String[] JUNK_HOSTS = {
            "api.w.org", "w.org", "schema.org", "gmpg.org", "w3.org", "wordpress.org",
            "wp.com", "googleapis.com", "gstatic.com", "cloudflareinsights.com",
            "doubleclick.net", "googletagmanager.com", "google-analytics.com",
            "jquery.com", "fontawesome.com", "bootstrapcdn.com", "cdnjs.cloudflare.com",
            "jsdelivr.net", "unpkg.com", "gravatar.com", "yoast.com", "gmpg.org",
            "fonts.googleapis.com", "fonts.gstatic.com", "recaptcha.net", "hcaptcha.com"
    };

    private static final String[] FILE_HOSTS = {
            "mediafire.com", "mega.nz", "mega.co.nz", "drive.google.com", "docs.google.com",
            "dropbox.com", "1fichier.com", "pixeldrain.com", "gofile.io", "workupload.com",
            "krakenfiles.com", "bunkr.si", "bunkr.su", "anonfiles.com", "wetransfer.com",
            "send.cm", "usersdrive.com", "uploadhaven.com", "mixdrop.co", "streamtape.com",
            "dood.", "filemoon", "files.fm", "transfer.sh", "0x0.st", "catbox.moe"
    };

    private static final Set<String> GATES = new HashSet<>(Arrays.asList(
            "linkvertise.com", "linkvertise.net", "up-to-down.net", "file-up.org",
            "adf.ly", "sh.st", "ouo.io", "ouo.press", "shrinkme.io", "shrinkearn.com",
            "exe.io", "exeo.app", "clicksfly.com", "cpmlink.net", "gplinks.co",
            "adshnk.com", "megaurl.com", "za.gl", "zagl.xyz", "droplink.co",
            "tnlink.in", "try2link.com", "mdisk.pro", "urlshortx.com", "shortmoz.link",
            "tii.ai", "tei.ai", "sub2unlock.com", "sub2unlock.net", "boost.ink",
            "boostlink.net", "payskip.org", "skiplink.io", "cuty.io", "shortino.app",
            "ay.live", "socialwolvez.com", "ez4short.com", "indianshortner.in",
            "urlsopen.com", "srt.am", "v.gd", "is.gd", "t.co", "bit.ly", "tinyurl.com",
            "cutt.ly", "rebrand.ly", "shorturl.at", "rb.gy", "tiny.cc", "ow.ly",
            "buff.ly", "dlvr.it", "bl.ink", "s.id", "lnk.to", "t.ly", "clck.ru",
            "shorturl.gg", "linktr.ee", "mnya.mobi", "clk.sh", "megaurl.in",
            "clicks.pw", "linkpays.in", "omg10.com", "upfiles.com", "upfiles.app",
            "link1s.com", "link1s.net", "lootlabs.gg", "loot-link.com", "lootdest.org",
            "lootdest.com", "loot-link.org", "rekonise.com", "hubsocial.chat",
            "work.ink", "workink.net", "platorelay.com", "platoboost.com",
            "mboost.me", "boost.ink", "social-unlock.com", "cuty.io"));

    private static final Set<String> TRACKERS = new HashSet<>(Arrays.asList(
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "2mdn.net", "facebook.com", "facebook.net", "cloudflare.com",
            "cloudflareinsights.com", "cloudflare-dns.com", "gstatic.com",
            "google.com", "recaptcha.net", "hcaptcha.com", "sentry.io", "clarity.ms",
            "bing.com", "w3.org", "schema.org", "fonts.googleapis.com",
            "fonts.gstatic.com", "cdn.jsdelivr.net", "unpkg.com", "jquery.com",
            "bootstrapcdn.com", "jsdelivr.net", "hotjar.com", "hotjar.io",
            "segment.com", "segment.io", "mixpanel.com", "amplitude.com",
            "posthog.com", "fullstory.com", "heapanalytics.com", "newrelic.com",
            "nr-data.net", "bugsnag.com", "rollbar.com", "yandex.ru", "mc.yandex.ru",
            "tiktok.com", "pinterest.com", "twitter.com", "x.com", "linkedin.com",
            "licdn.com", "outbrain.com", "taboola.com", "criteo.com", "criteo.net",
            "adnxs.com", "rubiconproject.com", "pubmatic.com", "smartadserver.com",
            "moatads.com", "scorecardresearch.com", "quantserve.com", "krxd.net",
            "demdex.net", "everesttech.net", "adsrvr.org", "casalemedia.com",
            "sharethrough.com", "onetrust.com", "cookielaw.org", "iubenda.com",
            "usercentrics.eu", "gravatar.com", "wp.com", "wordpress.com",
            "instagram.com", "youtube.com", "youtu.be", "twitch.tv",
            "discord.com", "discord.gg", "t.me", "telegram.me",
            "reddit.com", "redd.it", "whatsapp.com", "wa.me", "yoast.com"));

    private static final String[] PARAM_KEYS = {
            "url", "u", "r", "target", "dest", "destination", "redirect", "link",
            "to", "out", "goto", "q", "data", "redir", "continue", "return", "next",
            "page", "href", "source", "src", "o", "dl", "download"};

    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern PART = Pattern.compile(
            "(?i)parte\\s*(\\d+)\\s*(?:/|de)\\s*(\\d+)");

    private static final Pattern SLUG_ID = Pattern.compile("/([0-9a-f]{11,16})/?");

    private static final Pattern META = Pattern.compile(
            "<meta[^>]+http-equiv=[\"']?refresh[\"']?[^>]*content=[\"'][^\"']*url=([^\"'>\\s]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JS_LOC = Pattern.compile(
            "(?:window\\.|document\\.|top\\.|self\\.)?location(?:\\.href)?\\s*(?:=|\\.replace\\(|\\.assign\\()\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JS_OPEN = Pattern.compile(
            "window\\.open\\(\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern AT_OB = Pattern.compile(
            "atob\\(\\s*[\"']([A-Za-z0-9+/=_-]{8,})[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATA_ATTR = Pattern.compile(
            "data-(?:url|href|target|link|destination|redirect|src|next|go)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JSON_URL = Pattern.compile(
            "\"(?:url|link|target|destination|redirect|final_url|finalUrl|href|next|download)\""
                    + "\\s*:\\s*\"(https?:[^\"\\\\]+)\"", Pattern.CASE_INSENSITIVE);

    private static final Pattern RAW_URL = Pattern.compile(
            "https?://[^\\s\"'<>\\\\{}|^`\\[\\]]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern ANCHOR = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern IFRAME = Pattern.compile(
            "<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern FORM = Pattern.compile(
            "<form[^>]*>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern FORM_ACTION = Pattern.compile(
            "action=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern FORM_METHOD = Pattern.compile(
            "method=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_TAG = Pattern.compile(
            "<input[^>]+>", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_NAME = Pattern.compile(
            "name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern INPUT_VALUE = Pattern.compile(
            "value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIMER = Pattern.compile(
            "setTimeout\\s*\\(\\s*[^,]{0,90},\\s*(\\d{3,6})\\s*\\)");

    private static final Pattern STEP_LABEL = Pattern.compile(
            "(?i)(proxim|continuar|continue|next|avancar|seguir|parte\\s*\\d|step|"
                    + "weiter|siguiente|suivant|go\\s*to|get\\s*link|download|baixar|"
                    + "desbloquear|unlock|clique\\s*aqui|click\\s*here|aqui|acessar|abrir|"
                    + "pegar\\s*link|gerar\\s*link|link\\s*liberado|prosseguir|avancar)");

    private static final Pattern NAV_LABEL = Pattern.compile(
            "(?i)(^\\s*(home|inicio)\\s*$|categoria|categorias|sobre|contato|politica|"
                    + "termos|privacidade|login|entrar|registr|minha\\s*conta|menu|facebook|"
                    + "instagram|twitter|whatsapp|telegram|youtube|tiktok|pesquis|busca|"
                    + "search|autor|tag|coment|arquivo|feed|rss|cookies|dmca)");

    private static final Pattern NAV_PATH = Pattern.compile(
            "(?i)(/category/|/categoria/|/tag/|/author/|/autor/|/page/\\d|/sobre|/contato|"
                    + "/contact|/termos|/terms|/politica|/privacy|/privacidade|/feed|"
                    + "/wp-login|/wp-admin|/login|/register|/carrinho|/cart|\\?s=)");

    private static final Pattern ASSET_EXT = Pattern.compile(
            "(?i)\\.(js|mjs|css|map|woff2?|ttf|otf|eot|svg|png|jpe?g|gif|ico|webp|"
                    + "avif|bmp|mp3|wav|ogg|m4a|webmanifest|xml)(\\?|#|$)");

    private static final Pattern ASSET_PATH = Pattern.compile(
            "(?i)(/cdn-cgi/|beacon|/rum|analytics|/collect|/pixel|/track|impression|"
                    + "prebid|/gtm|gtag|/ads?/|/static/|/assets/|/img/|/fonts?/|/cdn/|"
                    + "/wp-json/|/wp-content/|/feed/|xmlrpc)");

    private static final Pattern FILE_EXT = Pattern.compile(
            "(?i)\\.(zip|apk|exe|dmg|rar|7z|pdf|mp4|mkv|iso|msi|txt|epub|docx?|xlsx?|"
                    + "pptx?|tar|gz|ipa|obb)(\\?|#|$)");

    private static final Pattern GATE_MARKER = Pattern.compile(
            "(?i)(aguarde|please wait|countdown|timer|anuncio|advertisement|"
                    + "clique\\s*para\\s*continuar|click\\s*to\\s*continue|unlock|"
                    + "desbloquear|pular\\s*anuncio|skip\\s*ad|verificando|verifying)");

    private static final Pattern CHALLENGE_TITLE = Pattern.compile(
            "(?i)(just a moment|just a second|attention required|checking your browser|"
                    + "ddos-guard|um momento|acesso negado|access denied|solve the challenge)");

    private static final Pattern CHALLENGE_BODY = Pattern.compile(
            "(?i)(__cf_chl|_cf_chl_opt|cf-browser-verification|cf-challenge|challenge-platform|"
                    + "cf-chl-|ddos-guard|checking your browser|verify you are human|"
                    + "solve the challenge)");

    private Resolver() {
    }

    static final class Page {
        final String url;
        final int code;
        final String location;
        final String type;
        final String body;
        final String title;

        Page(String url, int code, String location, String type, String body) {
            this.url = url;
            this.code = code;
            this.location = location;
            this.type = type;
            this.body = body == null ? "" : body;
            this.title = titleOf(this.body);
        }
    }

    static final class Candidate {
        final String url;
        final int score;
        final String label;

        Candidate(String url, int score, String label) {
            this.url = url;
            this.score = score;
            this.label = label == null ? "" : label;
        }
    }

    static final class Pick {
        final String url;

        Pick(String url) {
            this.url = url;
        }
    }

    static String lastRejectedHost;

    static Report crawl(String start, int maxHops, long deadline) {
        long began = System.currentTimeMillis();
        List<String> chain = new ArrayList<>();
        String current = start;
        chain.add(current);
        Set<String> seen = new HashSet<>();
        seen.add(current);
        String note = "nenhum link de destino nessa pagina";
        Page lastPage = null;
        boolean challengeHit = false;

        for (int hop = 0; hop < maxHops; hop++) {
            if (System.currentTimeMillis() > deadline) {
                note = "tempo esgotado";
                break;
            }

            Page page;
            try {
                page = get(current, 12000, 500_000, UA_MOBILE);
            } catch (Exception exc) {
                note = "falha de rede: " + readable(exc);
                break;
            }

            if (page.code >= 300 && page.code < 400 && page.location != null) {
                String next = absolutize(current, page.location);
                if (next.equals(current) || !seen.add(next)) {
                    note = "loop de redirect";
                    break;
                }
                chain.add(next);
                current = next;
                continue;
            }

            if (isChallenge(page)) {
                page = retryDesktop(current, page);
                if (isChallenge(page)) {
                    note = "challenge real (cloudflare)";
                    challengeHit = true;
                    lastPage = page;
                    break;
                }
            }

            lastPage = page;
            if (isDestination(current)) {
                return Report.hit("crawler", current, chain, millis(began));
            }

            List<Candidate> pool = extract(page);
            Pick pick = choose(page, pool, seen);
            if (pick == null) {
                int wait = timerWait(page.body);
                if (wait > 0) {
                    sleep(wait);
                    try {
                        page = get(current, 12000, 500_000, UA_MOBILE);
                        pool = extract(page);
                        pick = choose(page, pool, seen);
                    } catch (Exception ignored) {
                        pick = null;
                    }
                }
            }
            if (pick == null) {
                String rescue = wpNext(lastRejectedHost, page.title);
                if (rescue != null && seen.add(rescue)) {
                    chain.add(rescue);
                    current = rescue;
                    continue;
                }
                note = "nenhum link de destino nessa pagina";
                break;
            }

            seen.add(pick.url);
            chain.add(pick.url);
            current = pick.url;
            if (isDestination(current)) {
                return Report.hit("crawler", current, chain, millis(began));
            }
        }

        long spent = millis(began);
        boolean needsJs = challengeHit || needsJs(lastPage);
        if (chain.size() > 1) {
            if (looksLikeContentPage(lastPage)) {
                return Report.page("crawler", current, chain, spent);
            }
            return Report.miss("crawler", current, note, chain, spent, needsJs);
        }
        if (!isGate(host(start)) && !needsJs) {
            return Report.hit("crawler", start, chain, spent);
        }
        return Report.miss("crawler", start, note, chain, spent, needsJs);
    }

    private static List<Candidate> extract(Page page) {
        String text = page.body.replace("\\/", "/").replaceAll("rel=\"https?://[^\"]*\"", "");
        List<Candidate> pool = new ArrayList<>();

        addAll(pool, META, text, 100);
        addAll(pool, JS_LOC, text, 96);
        addAll(pool, JS_OPEN, text, 94);
        addAll(pool, DATA_ATTR, text, 92);
        addAll(pool, JSON_URL, text, 88);

        Matcher anchor = ANCHOR.matcher(text);
        while (anchor.find()) {
            String target = clean(anchor.group(1));
            if (target == null || target.startsWith("#") || target.startsWith("javascript:")) {
                continue;
            }
            String label = anchor.group(2).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            String flat = fold(label);
            String url = absolutize(page.url, target);
            int score = 12;
            if (STEP_LABEL.matcher(flat).find()) {
                score = 72;
            }
            if (NAV_LABEL.matcher(flat).find() || NAV_PATH.matcher(url).find()) {
                score = -60;
            }
            pool.add(new Candidate(url, score, label));
        }

        Matcher frame = IFRAME.matcher(text);
        while (frame.find()) {
            pool.add(new Candidate(absolutize(page.url, clean(frame.group(1))), 84, "iframe"));
        }

        Matcher ob = AT_OB.matcher(text);
        while (ob.find()) {
            for (String decoded : decodeAll(ob.group(1))) {
                if (decoded.startsWith("http")) {
                    pool.add(new Candidate(clean(decoded), 78, "atob"));
                }
            }
        }

        for (String raw : fromQuery(page.url)) {
            for (String decoded : decodeAll(raw)) {
                pool.add(new Candidate(decoded, 90, "query"));
            }
        }

        boolean strong = false;
        for (Candidate candidate : pool) {
            if (candidate.score >= 60) {
                strong = true;
                break;
            }
        }
        if (!strong) {
            Matcher raw = RAW_URL.matcher(text);
            while (raw.find()) {
                pool.add(new Candidate(clean(raw.group()), 6, "raw"));
            }
        }

        Collections.sort(pool, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                return Integer.compare(right.score, left.score);
            }
        });
        return pool;
    }

    private static final Pattern PROJECT_ID = Pattern.compile(
            "projectId\\s*:\\s*[\"']([a-z0-9-]+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern COLLECTION_PREFIX = Pattern.compile(
            "COLLECTION_PREFIX\\s*=\\s*[\"']([A-Za-z0-9_]+)[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern API_KEY = Pattern.compile(
            "apiKey\\s*:\\s*[\"']([A-Za-z0-9_\\-]{20,})[\"']", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUERY_VALUE = Pattern.compile(
            "[?&](?:game|script|id|doc|item|page)=([A-Za-z0-9_\\-]{6,})", Pattern.CASE_INSENSITIVE);

    /**
     * Paginas SPA guardam o link final no Firestore. Lendo o doc pela API REST
     * o link aparece sem precisar executar JavaScript.
     */
    static List<String> firestoreLinks(Page page) {
        List<String> out = new ArrayList<>();
        if (page.body == null || !page.body.contains("firebaseConfig")) {
            return out;
        }
        Matcher project = PROJECT_ID.matcher(page.body);
        Matcher key = API_KEY.matcher(page.body);
        if (!project.find() || !key.find()) {
            return out;
        }
        Matcher id = QUERY_VALUE.matcher(page.url);
        if (!id.find()) {
            return out;
        }
        String docId = id.group(1);
        Matcher prefix = COLLECTION_PREFIX.matcher(page.body);
        String base = prefix.find() ? prefix.group(1) : "";
        String[] paths = base.isEmpty()
                ? new String[]{"games/" + docId, "scripts/" + docId, "links/" + docId}
                : new String[]{base + "/main/games/" + docId, base + "/main/scripts/" + docId,
                base + "/main/links/" + docId};
        for (String path : paths) {
            String url = "https://firestore.googleapis.com/v1/projects/" + project.group(1)
                    + "/databases/(default)/documents/" + path + "?key=" + key.group(1);
            try {
                Page doc = get(url, 8000, 400_000, UA_MOBILE);
                if (doc.code != 200 || doc.body.isEmpty()) {
                    continue;
                }
                collectUrls(doc.body, out);
            } catch (Exception ignored) {
                // sem doc: segue
            }
        }
        return out;
    }

    static void collectUrls(String json, List<String> out) {
        Matcher matcher = Pattern.compile("https?://[^\"'\\s\\\\]+").matcher(json);
        while (matcher.find()) {
            String url = clean(matcher.group().replace("\\u0026", "&"));
            if (url != null && !out.contains(url) && !ASSET_EXT.matcher(url).find()) {
                out.add(url);
            }
        }
    }

    static Pick choose(Page page, List<Candidate> pool, Set<String> seen) {
        String currentHost = host(page.url);
        List<Candidate> filtered = new ArrayList<>();
        Set<String> local = new HashSet<>();

        boolean hasStep = false;
        for (Candidate candidate : pool) {
            if (candidate.score >= 60) {
                hasStep = true;
                break;
            }
        }
        boolean gatePage = page.body.length() < 15_000;
        if (!hasStep && !gatePage) {
            return null;
        }

        for (Candidate candidate : pool) {
            String url = candidate.url;
            if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                continue;
            }
            if (hasStep && candidate.score < 60) {
                continue;
            }
            if (NAV_PATH.matcher(url).find()) {
                continue;
            }
            if (url.contains("${") || url.contains("{{") || url.contains("%s")
                    || url.contains("<") || url.contains(">")) {
                continue;
            }
            String candidateHost = host(url);
            if (candidateHost.isEmpty() || isTracker(candidateHost) || isJunk(candidateHost)) {
                continue;
            }
            if (candidateHost.equals(currentHost) && isHome(url)) {
                continue;
            }
            if (seen.contains(url) || !local.add(url)) {
                continue;
            }
            if (candidate.score <= 0) {
                continue;
            }
            if (ASSET_EXT.matcher(url).find() || ASSET_PATH.matcher(url).find()) {
                continue;
            }
            filtered.add(candidate);
            if (filtered.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        if (filtered.isEmpty()) {
            return null;
        }

        List<Future<Pick>> futures = new ArrayList<>();
        for (final Candidate candidate : filtered) {
            futures.add(VERIFIER.submit(new Callable<Pick>() {
                @Override
                public Pick call() {
                    return verify(candidate);
                }
            }));
        }

        for (int index = 0; index < futures.size(); index++) {
            try {
                Pick pick = futures.get(index).get(9, TimeUnit.SECONDS);
                if (pick != null) {
                    for (int rest = index + 1; rest < futures.size(); rest++) {
                        futures.get(rest).cancel(true);
                    }
                    return pick;
                }
            } catch (Exception ignored) {
                // proximo candidato
            }
        }
        return null;
    }

    static Pick verify(Candidate candidate) {
        Boolean cached = VERIFIED.get(candidate.url);
        if (cached != null) {
            return cached ? new Pick(candidate.url) : null;
        }
        try {
            Page page = get(candidate.url, 8000, 220_000, UA_MOBILE);
            if (page.code >= 400) {
                VERIFIED.put(candidate.url, false);
                lastRejectedHost = host(candidate.url);
                return null;
            }
            String type = page.type == null ? "" : page.type.toLowerCase(Locale.ROOT);
            boolean asset = type.contains("javascript") || type.contains("ecmascript")
                    || type.contains("text/css") || type.startsWith("image/")
                    || type.startsWith("font/");
            if (asset) {
                VERIFIED.put(candidate.url, false);
                return null;
            }
            VERIFIED.put(candidate.url, true);
            return new Pick(candidate.url);
        } catch (Exception exc) {
            return null;
        }
    }

    static boolean isChallenge(Page page) {
        if (page.body.isEmpty()) {
            return false;
        }
        String title = page.title == null ? "" : page.title;
        if (CHALLENGE_TITLE.matcher(title).find()) {
            return true;
        }
        if (page.body.contains("_cf_chl_opt") || page.body.contains("__cf_chl")) {
            return true;
        }
        boolean small = page.body.length() < 24_000;
        if (!small) {
            return false;
        }
        if (page.code == 503 || page.code == 403) {
            return true;
        }
        return CHALLENGE_BODY.matcher(page.body).find();
    }

    static Page retryDesktop(String url, Page first) {
        try {
            warmCookies(url);
            Page second = get(url, 12000, 500_000, UA_DESKTOP);
            if (!second.body.isEmpty() && second.body.length() > first.body.length()) {
                return second;
            }
            return second;
        } catch (Exception exc) {
            return first;
        }
    }

    static void warmCookies(String url) {
        try {
            String host = host(url);
            if (host.isEmpty()) {
                return;
            }
            get("https://" + host + "/", 6000, 120_000, UA_DESKTOP);
        } catch (Exception ignored) {
            // sem cookies: segue
        }
    }

    static int timerWait(String body) {
        if (body == null || !GATE_MARKER.matcher(body).find()) {
            return 0;
        }
        Matcher matcher = TIMER.matcher(body);
        if (!matcher.find()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(matcher.group(1));
            return Math.min(value, MAX_TIMER_WAIT_MS);
        } catch (Exception exc) {
            return 0;
        }
    }

    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();

    static Page get(String url, int timeout, int limit, String ua) throws Exception {
        String key = ua.hashCode() + "|" + url;
        Page cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Object lock = LOCKS.get(key);
        if (lock == null) {
            Object fresh = new Object();
            Object previous = LOCKS.putIfAbsent(key, fresh);
            lock = previous == null ? fresh : previous;
        }
        synchronized (lock) {
            cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            return fetch(url, timeout, limit, ua, key);
        }
    }

    static final java.util.concurrent.atomic.AtomicInteger REQUESTS =
            new java.util.concurrent.atomic.AtomicInteger();

    static Page post(String url, String form, int timeout, int limit) throws Exception {
        REQUESTS.incrementAndGet();
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", UA_MOBILE);
        conn.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Referer", url);
        try {
            byte[] payload = form.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(payload.length);
            java.io.OutputStream out = conn.getOutputStream();
            out.write(payload);
            out.close();
        } catch (Exception ignored) {
            // corpo recusado: ainda tenta ler resposta
        }
        int code = conn.getResponseCode();
        String location = conn.getHeaderField("Location");
        String type = conn.getContentType();
        String body = "";
        InputStream stream = null;
        try {
            stream = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (stream != null) {
                body = readAll(stream, limit);
            }
        } catch (Exception ignored) {
            // sem corpo
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
        return new Page(url, code, location, type, body);
    }

    private static Page fetch(String url, int timeout, int limit, String ua, String key)
            throws Exception {
        REQUESTS.incrementAndGet();
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("User-Agent", ua);
        conn.setRequestProperty("Accept",
                "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");

        int code = conn.getResponseCode();
        String location = conn.getHeaderField("Location");
        String type = conn.getContentType();
        String encoding = conn.getHeaderField("Content-Encoding");

        String body = "";
        InputStream stream = null;
        try {
            stream = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (stream != null) {
                if (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip")) {
                    stream = new GZIPInputStream(stream);
                }
                body = readAll(stream, limit);
            }
        } catch (Exception ignored) {
            // corpo vazio: header ja basta
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }

        Page page = new Page(url, code, location, type, body);
        if (code > 0 && code < 500) {
            if (CACHE.size() > 160) {
                CACHE.clear();
            }
            CACHE.put(key, page);
        }
        return page;
    }

    private static final Map<String, String> WP_CACHE = new ConcurrentHashMap<>();

    static String wpNext(String host, String pageTitle) {
        if (host == null || pageTitle == null) {
            return null;
        }
        Matcher part = PART.matcher(pageTitle);
        if (!part.find()) {
            return null;
        }
        int current;
        int total;
        try {
            current = Integer.parseInt(part.group(1));
            total = Integer.parseInt(part.group(2));
        } catch (Exception exc) {
            return null;
        }
        int want = current + 1;
        if (want > total) {
            return null;
        }
        String base = pageTitle
                .replaceAll("(?i)\\(?\\s*parte\\s*\\d+\\s*/\\s*\\d+\\s*\\)?", " ")
                .replaceAll("\\s+[-|]\\s+.*$", "")
                .replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (base.length() < 3) {
            return null;
        }
        try {
            String query = "https://" + host + "/wp-json/wp/v2/search?per_page=50&search="
                    + URLEncoder.encode(base, "UTF-8");
            String cached = WP_CACHE.get(query);
            Page page;
            if (cached != null) {
                page = new Page(query, 200, null, "application/json", cached);
            } else {
                page = get(query, 8000, 400_000, UA_MOBILE);
            }
            if (page.code != 200 || page.body.isEmpty()) {
                return null;
            }
            if (cached == null) {
                if (WP_CACHE.size() > 40) {
                    WP_CACHE.clear();
                }
                WP_CACHE.put(query, page.body);
            }
            JSONArray items = new JSONArray(page.body);
            List<String> urls = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String title = item.optString("title", "").toLowerCase(Locale.ROOT);
                String url = item.optString("url", "");
                if (!title.contains("parte " + want) || !url.startsWith("http")) {
                    continue;
                }
                long id = 0L;
                Matcher slug = SLUG_ID.matcher(url);
                if (slug.find()) {
                    try {
                        id = Long.parseLong(slug.group(1), 16);
                    } catch (Exception ignored) {
                        id = 0L;
                    }
                }
                urls.add(url);
                ids.add(id);
            }
            for (int a = 0; a < urls.size(); a++) {
                for (int b = a + 1; b < urls.size(); b++) {
                    if (ids.get(b) > ids.get(a)) {
                        String tmpUrl = urls.get(a);
                        urls.set(a, urls.get(b));
                        urls.set(b, tmpUrl);
                        Long tmpId = ids.get(a);
                        ids.set(a, ids.get(b));
                        ids.set(b, tmpId);
                    }
                }
            }
            for (String url : urls) {
                try {
                    Page probe = get(url, 8000, 200_000, UA_MOBILE);
                    if (probe.code < 400) {
                        return url;
                    }
                } catch (Exception ignored) {
                    // proximo
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    static boolean isDestination(String url) {
        if (url == null) {
            return false;
        }
        if (FILE_EXT.matcher(url).find()) {
            return true;
        }
        String host = host(url);
        for (String known : FILE_HOSTS) {
            if (host.equals(known) || host.endsWith("." + known) || host.contains(known)) {
                return true;
            }
        }
        return false;
    }

    static boolean needsJs(Page page) {
        if (page == null || page.body == null) {
            return false;
        }
        String body = page.body;
        if (body.length() > 30_000) {
            return false;
        }
        boolean script = body.contains("<script") || body.contains("type=\"module\"");
        boolean appShell = body.contains("id=\"app\"") || body.contains("id=\"root\"")
                || body.contains("__NEXT_DATA__") || body.contains("nuxt");
        return script || appShell;
    }

    static boolean looksLikeContentPage(Page page) {
        if (page == null || page.body == null || page.body.length() < 20_000) {
            return false;
        }
        if (GATE_MARKER.matcher(page.body).find()) {
            return false;
        }
        return page.body.contains("<article") || page.body.contains("<main")
                || page.body.contains("firebaseConfig");
    }

    static boolean isHome(String url) {
        try {
            URL parsed = new URL(url);
            String query = parsed.getQuery();
            if (query != null && !query.isEmpty()) {
                return false;
            }
            String path = parsed.getPath();
            return path == null || path.isEmpty() || path.equals("/");
        } catch (Exception exc) {
            return false;
        }
    }

    static int partOf(String title) {
        if (title == null) {
            return 0;
        }
        Matcher matcher = PART.matcher(title);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception exc) {
            return 0;
        }
    }

    static String titleOf(String html) {
        if (html == null) {
            return null;
        }
        Matcher matcher = TITLE.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replaceAll("<[^>]+>", "").trim();
    }

    static void addAll(List<Candidate> pool, Pattern pattern, String text, int score) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            for (String decoded : decodeAll(clean(matcher.group(1)))) {
                if (decoded.startsWith("http")) {
                    pool.add(new Candidate(clean(decoded), score, ""));
                }
            }
        }
    }

    private static List<String> decodeAll(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return out;
        }
        out.add(value);
        String decoded = urlDecode(value);
        if (!decoded.equals(value)) {
            out.add(decoded);
        }
        for (String candidate : new ArrayList<>(out)) {
            String b64 = base64(candidate);
            if (b64 != null) {
                out.add(b64);
            }
        }
        return out;
    }

    static String urlDecode(String value) {
        try {
            String plus = value.replace("+", "%2B");
            return java.net.URLDecoder.decode(plus, "UTF-8");
        } catch (Exception exc) {
            return value;
        }
    }

    static String base64(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 16 || trimmed.contains("://")) {
            return null;
        }
        try {
            String normalized = trimmed.replace('-', '+').replace('_', '/');
            int pad = normalized.length() % 4;
            if (pad > 0) {
                normalized = normalized + "====".substring(0, 4 - pad);
            }
            byte[] bytes = Base64.decode(normalized, Base64.DEFAULT);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            String text = new String(bytes, "UTF-8");
            return text.startsWith("http") ? text : null;
        } catch (Exception exc) {
            return null;
        }
    }

    static String fold(String value) {
        if (value == null) {
            return "";
        }
        try {
            String decomposed = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
            return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        } catch (Exception exc) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim().replace("&amp;", "&");
        while (out.endsWith(".") || out.endsWith(",") || out.endsWith(";")
                || out.endsWith(")") || out.endsWith("\"") || out.endsWith("'")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static List<String> fromQuery(String url) {
        List<String> out = new ArrayList<>();
        int mark = url.indexOf('?');
        if (mark < 0) {
            return out;
        }
        String query = url.substring(mark + 1);
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).toLowerCase(Locale.ROOT);
            for (String candidateKey : PARAM_KEYS) {
                if (key.equals(candidateKey)) {
                    out.add(pair.substring(eq + 1));
                    break;
                }
            }
        }
        return out;
    }

    static String absolutize(String base, String location) {
        try {
            return new URL(new URL(base), location).toString();
        } catch (Exception exc) {
            return location;
        }
    }

    static String host(String url) {
        try {
            String value = url.contains("://") ? url : "http://" + url;
            String name = new URL(value).getHost();
            return name == null ? "" : name.toLowerCase(Locale.ROOT);
        } catch (Exception exc) {
            return "";
        }
    }

    static String baseDomain(String host) {
        if (host == null) {
            return "";
        }
        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    static boolean isTracker(String host) {
        return TRACKERS.contains(baseDomain(host));
    }

    static boolean isGate(String host) {
        return GATES.contains(baseDomain(host));
    }

    static boolean isJunk(String host) {
        if (host == null) {
            return true;
        }
        for (String junk : JUNK_HOSTS) {
            if (host.equals(junk) || host.endsWith("." + junk)) {
                return true;
            }
        }
        return false;
    }

    static String readAll(InputStream stream, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = stream.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            total += read;
            if (total >= limit) {
                break;
            }
        }
        return out.toString("UTF-8");
    }

    static String readable(Exception exc) {
        String message = exc.getMessage();
        if (message == null || message.isEmpty()) {
            return exc.getClass().getSimpleName();
        }
        return message;
    }

    static long millis(long began) {
        return System.currentTimeMillis() - began;
    }

    static Result resolve(String start, int maxHops) {
        long began = System.currentTimeMillis();
        REQUESTS.set(0);
        Report best = Hub.race(start, HUB_BUDGET_MS);
        return new Result(best.ok, best.url, best.note, best.chain,
                millis(began), best.needsJs);
    }

    static final long HUB_BUDGET_MS = 9_000L;

    static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        }
    }
}
