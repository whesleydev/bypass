package com.local.bypass;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CaptchaActivity extends Activity {

    private static final int BG = 0xFF0E1116;
    private static final int CARD = 0xFF161B22;
    private static final int STROKE = 0xFF232B36;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int MUTED = 0xFF8B949E;

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static CaptchaActivity current;

    private WebView web;

    static void closeIfOpen() {
        CaptchaActivity activity = current;
        if (activity != null && !activity.isFinishing()) {
            activity.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        String url = getIntent().getStringExtra("url");
        if (url == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(8), dp(12));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("confirme que é humano");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("fechar");
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(14), 0, dp(14), 0);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        close.setTextColor(TEXT);
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), STROKE);
        close.setBackground(background);
        close.setClickable(true);
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        TextView hint = new TextView(this);
        hint.setText("marca a caixa e volta — o link aparece sozinho");
        hint.setTextColor(MUTED);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(dp(16), 0, dp(16), dp(10));
        root.addView(hint);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setUserAgentString(UA);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                startPolling();
            }
        });
        web.setBackgroundColor(Color.TRANSPARENT);
        web.loadUrl(url);

        setContentView(root);
    }

    private void startPolling() {
        if (web == null) {
            return;
        }
        web.postDelayed(() -> {
            if (web == null) {
                return;
            }
            web.evaluateJavascript(TOKEN_JS, value -> {
                String token = unquote(value);
                if (token != null && token.length() > 10) {
                    Lootlabs.submitCaptchaToken(token);
                    android.widget.Toast.makeText(this, "captcha resolvido",
                            android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                startPolling();
            });
        }, 1200);
    }

    private String unquote(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1);
        }
        value = value.replace("\\\"", "\"").replace("\\\\", "\\");
        if (value.equals("null") || value.isEmpty()) {
            return null;
        }
        return value;
    }

    @Override
    protected void onDestroy() {
        if (current == this) {
            current = null;
        }
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private static final String TOKEN_JS =
            "(function(){var t='';try{if(window.TOKEN){t=String(window.TOKEN);}"
            + "if(!t){var el=document.querySelector('input[name=\"cf-turnstile-response\"]');"
            + "if(el&&el.value){t=el.value;}}"
            + "if(!t){var el2=document.querySelector('[name=\"cf-turnstile-response\"]');"
            + "if(el2&&el2.value){t=el2.value;}}}catch(e){}return t;})()";

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
