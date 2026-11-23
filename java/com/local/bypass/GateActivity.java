package com.local.bypass;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class GateActivity extends Activity {

    private static final int BG = 0xFF0E1116;
    private static final int CARD = 0xFF161B22;
    private static final int STROKE = 0xFF232B36;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = 0xFF8B949E;
    private static final int ACCENT = 0xFF3B82F6;

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final Set<String> ADS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "facebook.com", "facebook.net", "adnxs.com",
            "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
            "popads.net", "popcash.net", "propellerads.com", "onclickads.net",
            "adsterra.com", "hilltopads.net", "exoclick.com", "juicyads.com",
            "trafficjunky.net", "clickadu.com", "adcash.com", "mgid.com",
            "revcontent.com", "zergnet.com", "smartadserver.com", "rubiconproject.com",
            "pubmatic.com", "openx.net", "casalemedia.com", "sharethrough.com",
            "cloudflareinsights.com", "hotjar.com", "clarity.ms",
            "instagram.com", "facebook.com", "youtube.com", "youtu.be",
            "tiktok.com", "twitch.tv", "twitter.com", "x.com", "discord.gg",
            "discord.com", "t.me", "telegram.me", "pinterest.com", "linkedin.com"));

    private WebView web;
    private LinearLayout captureBar;
    private TextView captureText;
    private String gateHost;
    private String destination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        String url = getIntent().getStringExtra("url");
        if (url == null || !url.startsWith("http")) {
            finish();
            return;
        }
        gateHost = Uri.parse(url).getHost();

        setContentView(buildUi());
        configureWeb();
        web.loadUrl(url);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(8), dp(12));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("modo gate");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reload = makeButton("recarregar", false);
        reload.setOnClickListener(v -> web.reload());
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        reloadParams.setMargins(0, 0, dp(8), 0);
        header.addView(reload, reloadParams);

        TextView close = makeButton("fechar", false);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        captureBar = new LinearLayout(this);
        captureBar.setOrientation(LinearLayout.VERTICAL);
        captureBar.setBackground(rounded(CARD, STROKE, 16));
        captureBar.setPadding(dp(14), dp(12), dp(14), dp(12));
        captureBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.setMargins(dp(10), dp(10), dp(10), dp(10));
        root.addView(captureBar, barParams);

        TextView label = new TextView(this);
        label.setText("destino capturado");
        label.setTextColor(MUTED);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setAllCaps(true);
        label.setLetterSpacing(0.12f);
        captureBar.addView(label);

        captureText = new TextView(this);
        captureText.setTextColor(TEXT);
        captureText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        captureText.setTypeface(Typeface.MONOSPACE);
        captureText.setTextIsSelectable(true);
        captureText.setPadding(0, dp(6), 0, dp(10));
        captureBar.addView(captureText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        captureBar.addView(actions);

        TextView copy = makeButton("copiar", true);
        copy.setOnClickListener(v -> {
            if (destination == null) {
                return;
            }
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("link", destination));
                toast("copiado");
            }
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        copyParams.setMargins(0, 0, dp(8), 0);
        actions.addView(copy, copyParams);

        TextView open = makeButton("abrir", false);
        open.setOnClickListener(v -> {
            if (destination == null) {
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(destination)));
            } catch (Exception exc) {
                toast("nenhum app pra abrir");
            }
        });
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(40), 1f));

        return root;
    }

    private void configureWeb() {
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(UA);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handle(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handle(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(HELPER_JS, null);
            }
        });
    }

    private boolean handle(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return true;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.equals(gateHost) || isAd(host)) {
            return false;
        }
        capture(uri.toString());
        return true;
    }

    private boolean isAd(String host) {
        String[] parts = host.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        return ADS.contains(parts[parts.length - 2] + "." + parts[parts.length - 1]);
    }

    private void capture(String url) {
        if (destination != null && destination.equals(url)) {
            return;
        }
        destination = url;
        captureText.setText(url);
        captureBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "destino capturado", Toast.LENGTH_SHORT).show();
    }

    private TextView makeButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? rounded(ACCENT, ACCENT, 12) : rounded(CARD, STROKE, 12));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != fill) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private static final String HELPER_JS =
            "(function(){"
            + "if(window.__btHooked){return;}window.__btHooked=1;"
            + "var last=location.href;"
            + "setInterval(function(){"
            + "  if(location.href!==last){last=location.href;}"
            + "},400);"
            + "var wo=window.open;"
            + "window.open=function(u){try{if(u&&/^https?:/.test(String(u))){"
            + "  var a=document.createElement('a');a.href=u;document.body.appendChild(a);"
            + "  a.click();a.remove();}}catch(e){}return wo.apply(window,arguments);};"
            + "})();";
}
