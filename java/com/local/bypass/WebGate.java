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

    private static final long HARD_TIMEOUT_MS = 80_000L;
    private static final long TICK_MS = 800L;
    private static final int MAX_TICKS = 95;

    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "taboola.com", "outbrain.com", "propellerads.com", "popads.net",
            "adsterra.com", "clickadu.com", "hilltopads.net", "monetag.com",
            "onclck.com", "pushnami.com", "zedo.com", "revcontent.com",
            "mgid.com", "adcash.com", "exoclick.com", "juicyads.com",
            "trafficjunky.net", "popcash.net", "clickaine.com", "admaven.com",
            "profitablecpmrate.com", "a-ads.com", "bitmedia.io", "cointraffic.io"
    };

    private static final String NET_JS = String.join("\n", new String[]{
            "(function(){",
            "if(window.__bpyNet)return;window.__bpyNet=1;window.__bpyUrls=[];",
            "function scan(t){try{var m=String(t).match(/https?:\\/\\/[^\\s\"'\\\\<>)\\]}]+/g)||[];",
            "for(var i=0;i<m.length;i++){var u=m[i].replace(/[.,;]+$/,'');",
            "if(u.length<12)continue;",
            "if(window.__bpyUrls.indexOf(u)<0&&window.__bpyUrls.length<200)window.__bpyUrls.push(u);}}catch(e){}}",
            "try{var of=window.fetch;if(of){window.fetch=function(){var p=of.apply(this,arguments);",
            "try{p.then(function(r){try{r.clone().text().then(scan);}catch(e){}});}catch(e){}return p;};}}catch(e){}",
            "try{var XO=XMLHttpRequest.prototype.open,XS=XMLHttpRequest.prototype.send;",
            "XMLHttpRequest.prototype.open=function(m,u){this.__bpyU=u;return XO.apply(this,arguments);};",
            "XMLHttpRequest.prototype.send=function(){var x=this;",
            "x.addEventListener('load',function(){try{scan(x.responseText);}catch(e){}});",
            "return XS.apply(this,arguments);};}catch(e){}",
            "try{var OW=window.WebSocket;if(OW){window.WebSocket=function(u,p){var w=p?new OW(u,p):new OW(u);",
            "w.addEventListener('message',function(e){scan(e.data);});return w;};",
            "window.WebSocket.prototype=OW.prototype;}}catch(e){}",
            "try{var op=history.pushState;history.pushState=function(){scan(location.href);",
            "return op.apply(this,arguments);};}catch(e){}",
            "})()"});

    private static final String DRIVER_JS = String.join("\n", new String[]{
            "(function(){",
            "var out={clicked:0,dialog:0};",
            "try{",
            "  try{",
            "    localStorage.setItem('xx_t','2');",
            "    localStorage.setItem('fs_countdown','1');",
            "    localStorage.removeItem('ttl');",
            "  }catch(e){}",
            "  function flip(){",
            "    try{",
            "      var proto=Object.getPrototypeOf(document);",
            "      Object.defineProperty(proto,'visibilityState',{configurable:true,",
            "        get:function(){return 'hidden';}});",
            "      document.dispatchEvent(new Event('visibilitychange'));",
            "      setTimeout(function(){",
            "        Object.defineProperty(proto,'visibilityState',{configurable:true,",
            "          get:function(){return 'visible';}});",
            "        document.dispatchEvent(new Event('visibilitychange'));",
            "      },120);",
            "    }catch(e){}",
            "  }",
            "  flip();",
            "  function fold(text){",
            "    var raw='';",
            "    for(var i=0;i<text.length;i++){",
            "      var c=text.charCodeAt(i);",
            "      if(c>=0x1D400&&c<=0x1D6A3){var k=(c-0x1D400)%52;",
            "        raw+=String.fromCharCode(k<26?65+k:65+k-26);continue;}",
            "      if(c>=0x0400&&c<=0x04FF){",
            "        var m={0x410:'A',0x411:'B',0x412:'B',0x415:'E',0x41A:'K',0x41C:'M',",
            "          0x41D:'H',0x41E:'O',0x420:'P',0x421:'C',0x422:'T',0x425:'X',",
            "          0x406:'I',0x408:'J',0x40D:'D'};",
            "        if(m[c])raw+=m[c];continue;}",
            "      if(c===0x200B||c===0x200C||c===0x200D||c===0xFEFF||c===0x00A0)continue;",
            "      raw+=text.charAt(i);",
            "    }",
            "    try{raw=raw.normalize('NFD').replace(/[\u0300-\u036f]/g,'');}catch(e){}",
            "    return raw.toUpperCase()",
            "      .replace(/0/g,'O').replace(/[1!|]/g,'I').replace(/3/g,'E')",
            "      .replace(/[4@]/g,'A').replace(/[5$]/g,'S').replace(/7/g,'T')",
            "      .replace(/9/g,'G').replace(/8/g,'B').replace(/&/g,'E')",
            "      .replace(/[^A-Z]/g,'');",
            "  }",
            "  var words=['CONTINUAR','CONTINUE','PROSSEGUIR','PROSSIGA','AVANCAR','NEXT',",
            "    'FINALIZAR','CLIQUE','CLICK','ETAPA','AQUI','IRPARA','DESBLOQUEAR',",
            "    'UNLOCK','GETLINK','OBTERLINK','PEGARLINK','DOWNLOAD','BAIXAR','SKIP',",
            "    'PULAR','FECHAR','CLOSE','GERARLINK','ACESSAR','ENTRAR'];",
            "  var nodes=document.querySelectorAll('a,button,[role=button],div[onclick],span[onclick],input[type=submit]');",
            "  var now=Date.now();",
            "  for(var n=0;n<nodes.length&&out.clicked<2;n++){",
            "    var el=nodes[n];",
            "    var label=el.innerText||el.textContent||el.value||'';",
            "    if(!label||label.length>80)continue;",
            "    var folded=fold(label);",
            "    if(!folded)continue;",
            "    var hit=false;",
            "    for(var w=0;w<words.length;w++){if(folded.indexOf(words[w])>=0){hit=true;break;}}",
            "    if(!hit)continue;",
            "    if(el.disabled)continue;",
            "    if(el.__bpy&&now-el.__bpy<1200)continue;",
            "    var box=el.getBoundingClientRect();",
            "    if(box.width<1||box.height<1)continue;",
            "    el.__bpy=now;",
            "    try{el.scrollIntoView({block:'center'});}catch(e){}",
            "    try{el.click();out.clicked++;}catch(e){}",
            "  }",
            "  out.dialog=document.querySelector('[data-slot=\"dialog-content\"],[role=\"dialog\"]')?1:0;",
            "  out.net=(window.__bpyUrls||[]).slice(-40);",
            "  out.url=location.href;",
            "}catch(e){out.err=String(e);}",
            "return JSON.stringify(out);",
            "})()"});

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
        private int clickedTotal;
        private boolean sawGate;

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
                public void onPageStarted(WebView view, String target, android.graphics.Bitmap icon) {
                    try {
                        view.evaluateJavascript(NET_JS, null);
                    } catch (Exception ignored) {
                        // proxima pagina
                    }
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
                web.evaluateJavascript(DRIVER_JS, value -> {
                    String raw = unquote(value);
                    if (raw == null || raw.isEmpty()) {
                        return;
                    }
                    String current = null;
                    String captured = null;
                    try {
                        org.json.JSONObject info = new org.json.JSONObject(raw);
                        current = info.optString("url", null);
                        if (info.optInt("clicked", 0) > 0) {
                            sawGate = true;
                            clickedTotal += info.optInt("clicked", 0);
                        }
                        org.json.JSONArray net = info.optJSONArray("net");
                        if (net != null && net.length() > 0) {
                            sawGate = true;
                        }
                        captured = bestCaptured(net);
                    } catch (Exception parse) {
                        current = raw;
                    }
                    if (captured != null) {
                        finish(captured, null);
                        return;
                    }
                    if (current != null && !current.isEmpty()
                            && !hostOf(current).equals(startHost)) {
                        finish(current, null);
                        return;
                    }
                    if (!sawGate && ticks > 16) {
                        finish(null, "sem gate nesta pagina");
                    }
                });
            } catch (Exception ignored) {
                // proxima volta
            }
            if (!done) {
                handler.postDelayed(this, TICK_MS);
            }
        }

        private String bestCaptured(org.json.JSONArray net) {
            if (net == null) {
                return null;
            }
            for (int index = net.length() - 1; index >= 0; index--) {
                String candidate = net.optString(index, null);
                if (candidate == null || !candidate.startsWith("http")) {
                    continue;
                }
                String candidateHost = hostOf(candidate);
                if (candidateHost.isEmpty() || isAdHost(candidateHost)) {
                    continue;
                }
                if (candidateHost.equals(startHost) || isSameSite(candidateHost, startHost)) {
                    continue;
                }
                if (candidate.contains("/favicon") || candidate.contains(".png")
                        || candidate.contains(".jpg") || candidate.contains(".css")) {
                    continue;
                }
                return candidate;
            }
            return null;
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
