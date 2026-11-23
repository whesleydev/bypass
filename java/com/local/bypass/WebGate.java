package com.local.bypass;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.Locale;

/**
 * Executa a pagina num WebView invisivel, clica nos botoes de continuar e devolve
 * o primeiro destino que sai do host do gate. Cobre SPA que monta o link em JS.
 */
final class WebGate {

    interface Listener {
        void onStatus(String message);

        void onResult(String url);

        void onError(String message);
    }

    private static final long HARD_TIMEOUT_MS = 38_000L;
    private static final long TICK_MS = 900L;
    private static final int MAX_TICKS = 42;

    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "taboola.com", "outbrain.com", "propellerads.com", "popads.net",
            "adsterra.com", "clickadu.com", "hilltopads.net", "monetag.com",
            "onclck.com", "pushnami.com", "zedo.com", "revcontent.com",
            "mgid.com", "adcash.com", "exoclick.com", "juicyads.com",
            "trafficjunky.net", "popcash.net", "clickaine.com", "admaven.com",
            "profitablecpmrate.com", "a-ads.com", "bitmedia.io", "cointraffic.io"
    };

    private static final String CLICK_JS =
            "(function(){"
                    + "try{"
                    + "var re=/(continuar|continue|prosseguir|prosiga|avancar|avançar|next|"
                    + "proxima|próxima|get\\s*link|obter\\s*link|pegar\\s*link|download|baixar|"
                    + "unlock|desbloquear|skip|pular|clique|click|entrar)/i;"
                    + "var nodes=document.querySelectorAll('a,button,[role=button],"
                    + "div[onclick],span[onclick],input[type=submit]');"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var el=nodes[i];"
                    + "var t=(el.innerText||el.textContent||el.value||'').trim();"
                    + "if(!t||t.length>60)continue;"
                    + "if(!re.test(t))continue;"
                    + "var box=el.getBoundingClientRect();"
                    + "if(box.width<1||box.height<1)continue;"
                    + "try{el.scrollIntoView({block:'center'});}catch(e){}"
                    + "try{el.click();}catch(e){}"
                    + "}"
                    + "}catch(e){}"
                    + "return location.href;"
                    + "})()";

    private WebGate() {
    }

    static void run(final Activity activity, final String url, final Listener listener) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Runner(activity, url, listener).start();
            }
        });
    }

    private static final class Runner implements Runnable {
        private final Activity activity;
        private final String startUrl;
        private final String startHost;
        private final Listener listener;
        private final Handler handler = new Handler(Looper.getMainLooper());

        private WebView web;
        private boolean done;
        private int ticks;
        private int errors;

        Runner(Activity activity, String url, Listener listener) {
            this.activity = activity;
            this.startUrl = url;
            this.startHost = hostOf(url);
            this.listener = listener;
        }

        void start() {
            try {
                web = new WebView(activity);
            } catch (Exception exc) {
                listener.onError("sem webview: " + exc.getClass().getSimpleName());
                return;
            }
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadsImagesAutomatically(false);
            settings.setBlockNetworkImage(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setDatabaseEnabled(true);
            try {
                android.webkit.CookieManager cookies = android.webkit.CookieManager.getInstance();
                cookies.setAcceptCookie(true);
                cookies.setAcceptThirdPartyCookies(web, true);
            } catch (Exception ignored) {
                // sem cookie manager: segue
            }
            settings.setSupportMultipleWindows(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36");
            web.setBackgroundColor(Color.TRANSPARENT);
            web.setAlpha(0.01f);

            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String target) {
                    return handle(target);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handle(request.getUrl().toString());
                }

                @Override
                public void onPageFinished(WebView view, String loaded) {
                    ticks = 0;
                    if (!done) {
                        handler.postDelayed(Runner.this, 260L);
                    }
                }

                @Override
                public void onReceivedError(WebView view, int code, String description,
                                            String failingUrl) {
                    errors++;
                    if (errors >= 3 && !done) {
                        finish(null, "pagina nao carregou");
                    }
                }
            });
            web.setWebChromeClient(new WebChromeClient());

            try {
                ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(2, 2);
                params.leftMargin = -8;
                params.topMargin = -8;
                root.addView(web, params);
            } catch (Exception ignored) {
                // sem janela: o webview ainda executa
            }

            listener.onStatus("executando a pagina (js)…");
            web.loadUrl(startUrl);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!done) {
                        finish(null, "js nao liberou o link");
                    }
                }
            }, HARD_TIMEOUT_MS);
            handler.postDelayed(this, TICK_MS);
        }

        @Override
        public void run() {
            if (done || web == null) {
                return;
            }
            ticks++;
            if (ticks > MAX_TICKS) {
                finish(null, "js nao liberou o link");
                return;
            }
            try {
                web.evaluateJavascript(CLICK_JS, value -> {
                    String current = unquote(value);
                    if (current != null && !current.isEmpty()
                            && !hostOf(current).equals(startHost)) {
                        finish(current, null);
                    }
                });
            } catch (Exception ignored) {
                // proxima volta
            }
            if (!done) {
                handler.postDelayed(this, TICK_MS);
            }
        }

        private boolean handle(String target) {
            if (target == null) {
                return false;
            }
            String lower = target.toLowerCase(Locale.ROOT);
            if (lower.startsWith("intent:") || lower.startsWith("market:")
                    || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
                return true;
            }
            if (target.startsWith("javascript:")) {
                return false;
            }
            if (!target.startsWith("http")) {
                return false;
            }
            String targetHost = hostOf(target);
            if (targetHost.isEmpty()) {
                return false;
            }
            if (isAdHost(targetHost)) {
                return true;
            }
            if (!targetHost.equals(startHost) && !isSameSite(targetHost, startHost)) {
                finish(target, null);
                return true;
            }
            return false;
        }

        private void finish(String url, String error) {
            if (done) {
                return;
            }
            done = true;
            handler.removeCallbacksAndMessages(null);
            try {
                if (web != null) {
                    web.stopLoading();
                    ViewGroup parent = (ViewGroup) web.getParent();
                    if (parent != null) {
                        parent.removeView(web);
                    }
                    web.destroy();
                    web = null;
                }
            } catch (Exception ignored) {
                // ja destruido
            }
            if (url != null) {
                listener.onResult(url);
            } else {
                listener.onError(error == null ? "js nao liberou o link" : error);
            }
        }
    }

    private static boolean isAdHost(String host) {
        for (String ad : AD_HOSTS) {
            if (host.equals(ad) || host.endsWith("." + ad)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSameSite(String first, String second) {
        String a = first.startsWith("www.") ? first.substring(4) : first;
        String b = second.startsWith("www.") ? second.substring(4) : second;
        return a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a);
    }

    private static String unquote(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.replace("\\/", "/").replace("\\u003d", "=").replace("\\u0026", "&");
    }

    private static String hostOf(String url) {
        try {
            String value = url.contains("://") ? url : "http://" + url;
            String name = new java.net.URL(value).getHost();
            return name == null ? "" : name.toLowerCase(Locale.ROOT);
        } catch (Exception exc) {
            return "";
        }
    }
}
