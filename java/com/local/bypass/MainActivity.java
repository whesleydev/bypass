package com.local.bypass;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {

    private static final int BG_TOP = 0xFF000000;
    private static final int BG_BOTTOM = 0xFF080B10;
    private static final int CARD = 0xFF0E1117;
    private static final int STROKE = 0xFF1C2330;
    private static final int TEXT = 0xFFF3F6FA;
    private static final int MUTED = 0xFF8792A2;
    private static final int HINT = 0xFF454F5E;
    private static final int ACCENT = 0xFF2E9BFF;
    private static final int ACCENT_DEEP = 0xFF0A6BEA;
    private static final int DANGER = 0xFFFF453A;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<View> entrance = new ArrayList<>();

    private EditText input;
    private TextView status;
    private TextView resultUrl;
    private TextView resultMeta;
    private LinearLayout resultCard;
    private TextView bypassButton;
    private Shimmer shimmer;
    private String currentResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG_TOP);
        getWindow().setNavigationBarColor(BG_BOTTOM);
        setContentView(buildUi());
        playEntrance();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String shared = null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            shared = intent.getData().toString();
        }
        if (shared == null) {
            return;
        }
        String link = firstLink(shared);
        if (link == null) {
            return;
        }
        input.setText(link);
        startBypass();
    }

    private static String firstLink(String raw) {
        String text = raw.trim();
        if (text.startsWith("http://") || text.startsWith("https://")) {
            int space = text.indexOf(' ');
            return space > 0 ? text.substring(0, space) : text;
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("https?://\\S+").matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private View buildUi() {
        FrameLayout stage = new FrameLayout(this);
        GradientDrawable backdrop = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM});
        stage.setBackground(backdrop);

        Glow glow = new Glow(this);
        stage.addView(glow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
        glow.setAlpha(0.6f);
        glow.animate().alpha(1f).setDuration(2400)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        glow.animate().alpha(0.72f).setDuration(2400).start();
                    }
                })
                .start();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        stage.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(hero);

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_logo);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setElevation(dp(6));
        hero.addView(mark, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = new TextView(this);
        title.setText("bypass");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setLetterSpacing(-0.03f);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(dp(12), 0, 0, 0);
        hero.addView(title, titleParams);
        entrance.add(hero);

        TextView subtitle = new TextView(this);
        subtitle.setText("cola o link. o destino volta limpo.");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        subtitle.setPadding(0, dp(12), 0, dp(22));
        root.addView(subtitle);
        entrance.add(subtitle);

        LinearLayout inputCard = new LinearLayout(this);
        inputCard.setOrientation(LinearLayout.VERTICAL);
        inputCard.setBackground(panel());
        inputCard.setElevation(dp(6));
        inputCard.setPadding(dp(18), dp(6), dp(18), dp(6));
        root.addView(inputCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        entrance.add(inputCard);

        input = new EditText(this);
        input.setHint("https://…");
        input.setTextColor(TEXT);
        input.setHintTextColor(HINT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setBackground(null);
        input.setPadding(0, dp(12), 0, dp(12));
        input.setLineSpacing(dp(3), 1f);
        inputCard.addView(input);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.setMargins(0, dp(16), 0, 0);
        root.addView(actions, actionsParams);
        entrance.add(actions);

        TextView pasteButton = makeButton("colar", false);
        pasteButton.setOnClickListener(v -> pasteFromClipboard());
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(0, dp(52), 0.36f);
        pasteParams.setMargins(0, 0, dp(10), 0);
        actions.addView(pasteButton, pasteParams);

        bypassButton = makeButton("bypassar", true);
        bypassButton.setOnClickListener(v -> startBypass());
        actions.addView(bypassButton, new LinearLayout.LayoutParams(0, dp(52), 0.64f));

        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        status.setPadding(dp(4), dp(14), dp(4), 0);
        root.addView(status);
        entrance.add(status);

        resultCard = new LinearLayout(this);
        resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setBackground(panel());
        resultCard.setElevation(dp(10));
        resultCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        resultCard.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(20), 0, 0);
        root.addView(resultCard, cardParams);

        TextView resultLabel = new TextView(this);
        resultLabel.setText("destino");
        resultLabel.setTextColor(MUTED);
        resultLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        resultLabel.setAllCaps(true);
        resultLabel.setLetterSpacing(0.14f);
        resultLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resultCard.addView(resultLabel);

        resultUrl = new TextView(this);
        resultUrl.setTextColor(TEXT);
        resultUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        resultUrl.setTypeface(Typeface.MONOSPACE);
        resultUrl.setTextIsSelectable(true);
        resultUrl.setLineSpacing(dp(3), 1f);
        resultUrl.setPadding(0, dp(10), 0, dp(6));
        resultCard.addView(resultUrl);

        resultMeta = new TextView(this);
        resultMeta.setTextColor(MUTED);
        resultMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        resultMeta.setPadding(0, 0, 0, dp(14));
        resultCard.addView(resultMeta);

        LinearLayout resultActions = new LinearLayout(this);
        resultActions.setOrientation(LinearLayout.HORIZONTAL);
        resultCard.addView(resultActions);

        TextView copyButton = makeButton("copiar", true);
        copyButton.setOnClickListener(v -> copyResult());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        copyParams.setMargins(0, 0, dp(8), 0);
        resultActions.addView(copyButton, copyParams);

        TextView openButton = makeButton("abrir", false);
        openButton.setOnClickListener(v -> openResult());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        openParams.setMargins(0, 0, dp(8), 0);
        resultActions.addView(openButton, openParams);

        TextView shareButton = makeButton("enviar", false);
        shareButton.setOnClickListener(v -> shareResult());
        resultActions.addView(shareButton, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView footer = new TextView(this);
        footer.setText("segue redirect, meta refresh, location.href, base64 e parâmetros "
                + "de gate. captcha e JS pesado podem segurar o link.");
        footer.setTextColor(0xFF5A6473);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        footer.setLineSpacing(dp(3), 1f);
        footer.setPadding(dp(4), dp(26), dp(4), 0);
        root.addView(footer);

        return stage;
    }

    private void playEntrance() {
        for (int index = 0; index < entrance.size(); index++) {
            View view = entrance.get(index);
            view.setAlpha(0f);
            view.setTranslationY(dp(18));
            view.animate().alpha(1f).translationY(0f)
                    .setStartDelay(60L * index)
                    .setDuration(460)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }
    }

    private TextView makeButton(String label, boolean primary) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        button.setLetterSpacing(0.01f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : TEXT);
        button.setBackground(primary ? primaryBackground(18) : secondaryBackground(18));
        button.setElevation(dp(primary ? 6 : 1));
        button.setClickable(true);
        button.setFocusable(true);
        spring(button);
        return button;
    }

    private Drawable primaryBackground(int radiusDp) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ACCENT, ACCENT_DEEP});
        shape.setCornerRadius(dp(radiusDp));
        return withRipple(shape, radiusDp, 0x4DFFFFFF);
    }

    private Drawable secondaryBackground(int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(CARD);
        shape.setCornerRadius(dp(radiusDp));
        shape.setStroke(dp(1), STROKE);
        return withRipple(shape, radiusDp, 0x1FFFFFFF);
    }

    private Drawable panel() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(CARD);
        shape.setCornerRadius(dp(24));
        shape.setStroke(dp(1), STROKE);
        return shape;
    }

    private Drawable withRipple(Drawable content, int radiusDp, int rippleColor) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(radiusDp));
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    private void spring(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View target, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        target.animate().cancel();
                        target.animate().scaleX(0.955f).scaleY(0.955f)
                                .setDuration(90)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        target.animate().scaleX(1f).scaleY(1f)
                                .setDuration(340)
                                .setInterpolator(new OvershootInterpolator(2.6f))
                                .start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private void setBusy(boolean busy) {
        if (busy) {
            bypassButton.setEnabled(false);
            shimmer = new Shimmer(dp(18), ACCENT, ACCENT_DEEP);
            bypassButton.setBackground(shimmer);
            shimmer.start();
            bypassButton.setText("bypassando");
        } else {
            if (shimmer != null) {
                shimmer.stop();
                shimmer = null;
            }
            bypassButton.setEnabled(true);
            bypassButton.setAlpha(1f);
            bypassButton.setText("bypassar");
            bypassButton.setBackground(primaryBackground(18));
            bypassButton.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
        }
    }

    private void setStatus(String text, int color) {
        status.animate().cancel();
        status.setText(text);
        status.setTextColor(color);
        status.setAlpha(0.45f);
        status.animate().alpha(1f).setDuration(220).start();
    }

    private void revealCard() {
        resultCard.animate().cancel();
        resultCard.setVisibility(View.VISIBLE);
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(dp(22));
        resultCard.setScaleX(0.96f);
        resultCard.setScaleY(0.96f);
        resultCard.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(430)
                .setInterpolator(new OvershootInterpolator(1.05f))
                .start();
    }

    private void pasteFromClipboard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null || !manager.hasPrimaryClip() || manager.getPrimaryClip() == null) {
            toast("clipboard vazio");
            return;
        }
        ClipData clip = manager.getPrimaryClip();
        if (clip.getItemCount() == 0) {
            toast("clipboard vazio");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        String link = firstLink(text.toString());
        if (link == null) {
            toast("nenhum link no clipboard");
            return;
        }
        input.setText(link);
        input.setAlpha(0.35f);
        input.animate().alpha(1f).setDuration(240).start();
        setStatus("link colado — toca em bypassar", MUTED);
    }

    private void startBypass() {
        String raw = input.getText().toString().trim();
        String link = firstLink(raw);
        if (link == null) {
            setStatus("cole um link http ou https", DANGER);
            shake(input);
            return;
        }
        input.setText(link);
        setBusy(true);
        resultCard.animate().cancel();
        resultCard.setVisibility(View.INVISIBLE);
        currentResult = null;

        String host = "";
        try {
            host = new java.net.URL(link).getHost();
        } catch (Exception ignored) {
        }
        if (Lootlabs.isLootlabs(host)) {
            runLootlabs(link);
            return;
        }
        if (Rekonise.isRekonise(host)) {
            runRekonise(link);
            return;
        }

        final LinkCache.Entry cached = LinkCache.get(this, link);
        if (cached != null) {
            setStatus("do cache · " + (cached.age / 60000L) + " min atrás", MUTED);
            showResult(new Resolver.Result(true, cached.url, null, cached.chain, 0L));
            return;
        }

        setStatus("resolvendo…", MUTED);
        final String origin = link;
        pool.execute(() -> {
            Resolver.Result result = Resolver.resolve(origin, 20);
            if (result.ok) {
                LinkCache.put(getApplicationContext(), origin, result.url, result.chain);
            }
            ui.post(() -> showResult(result));
        });
    }

    private void runRekonise(final String link) {
        setStatus("abrindo o gate…", MUTED);
        Rekonise.run(link, new Rekonise.Listener() {
            @Override
            public void onStatus(final String message) {
                ui.post(() -> setStatus(message, MUTED));
            }

            @Override
            public void onResult(final String url) {
                ui.post(() -> setStatus("seguindo o rastro…", MUTED));
                pool.execute(() -> {
                    Resolver.Result deep = Resolver.resolve(url, 20);
                    final String finalUrl = deep.url;
                    ui.post(() -> {
                        setBusy(false);
                        currentResult = finalUrl;
                        setStatus("gate liberado", MUTED);
                        resultUrl.setText(finalUrl);
                        resultMeta.setText(deep.ok ? "destino final · rekonise"
                                : "parou em: " + deep.error);
                        revealCard();
                    });
                });
            }

            @Override
            public void onError(final String message) {
                ui.post(() -> {
                    setBusy(false);
                    currentResult = link;
                    setStatus("gate nao liberou: " + message, DANGER);
                    resultUrl.setText(link);
                    resultMeta.setText("nao liberou · tenta de novo");
                    revealCard();
                });
            }
        });
    }

    private void runLootlabs(final String link) {
        setStatus("abrindo o gate…", MUTED);
        Lootlabs.run(link, new Lootlabs.Listener() {
            @Override
            public void onStatus(final String message) {
                ui.post(() -> setStatus(message, MUTED));
            }

            @Override
            public void onCaptcha(final String captchaUrl) {
                ui.post(() -> {
                    Intent intent = new Intent(MainActivity.this, CaptchaActivity.class);
                    intent.putExtra("url", captchaUrl);
                    startActivity(intent);
                });
            }

            @Override
            public void onResult(final String url) {
                ui.post(() -> {
                    CaptchaActivity.closeIfOpen();
                    setStatus("seguindo o rastro…", MUTED);
                });
                pool.execute(() -> {
                    Resolver.Result deep = Resolver.resolve(url, 20);
                    final String finalUrl = deep.url;
                    ui.post(() -> {
                        setBusy(false);
                        currentResult = finalUrl;
                        setStatus("gate liberado", MUTED);
                        resultUrl.setText(finalUrl);
                        resultMeta.setText(deep.ok ? "destino final · lootlabs"
                                : "parou em: " + deep.error);
                        revealCard();
                    });
                });
            }

            @Override
            public void onError(final String message) {
                ui.post(() -> {
                    CaptchaActivity.closeIfOpen();
                    setBusy(false);
                    currentResult = link;
                    setStatus("gate nao liberou: " + message, DANGER);
                    resultUrl.setText(link);
                    resultMeta.setText("nao liberou · tenta de novo");
                    revealCard();
                });
            }
        });
    }

    private void showResult(final Resolver.Result result) {
        if (!result.ok && result.needsJs) {
            runWebGate(result);
            return;
        }
        finishResult(result);
    }

    private void runWebGate(final Resolver.Result first) {
        setStatus("abrindo com js…", MUTED);
        WebGate.run(this, first.url, new WebGate.Listener() {
            @Override
            public void onStatus(final String message) {
                ui.post(() -> setStatus(message, MUTED));
            }

            @Override
            public void onResult(final String next) {
                ui.post(() -> setStatus("seguindo o rastro…", MUTED));
                pool.execute(() -> {
                    Resolver.Result deep = Resolver.resolve(next, 24);
                    final Resolver.Result winner = deep.ok ? deep : first;
                    ui.post(() -> finishResult(winner));
                });
            }

            @Override
            public void onError(final String message) {
                ui.post(() -> finishResult(first));
            }
        });
    }

    private void finishResult(Resolver.Result result) {
        setBusy(false);
        currentResult = result.url;

        if (!result.ok) {
            setStatus("não resolvi: " + result.error, DANGER);
            resultUrl.setText(result.url);
            resultMeta.setText("link original · " + trace(result.chain));
            revealCard();
            return;
        }

        setStatus("pronto em " + result.millis + " ms · "
                + (result.chain.size() - 1) + " saltos", MUTED);
        resultUrl.setText(result.url);
        resultMeta.setText(trace(result.chain));
        revealCard();
    }

    private void shake(View view) {
        view.animate().cancel();
        view.setTranslationX(0f);
        view.animate().translationX(dp(7)).setDuration(50)
                .withEndAction(() -> view.animate().translationX(-dp(7)).setDuration(60)
                        .withEndAction(() -> view.animate().translationX(dp(4)).setDuration(55)
                                .withEndAction(() -> view.animate().translationX(0f)
                                        .setDuration(70).start())
                                .start())
                        .start())
                .start();
    }

    private String trace(List<String> chain) {
        if (chain.size() < 2) {
            return "1 passo";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < chain.size(); i++) {
            if (builder.length() > 0) {
                builder.append(" → ");
            }
            builder.append(shortHost(chain.get(i)));
        }
        return builder.toString();
    }

    private String shortHost(String url) {
        try {
            String host = new java.net.URL(url).getHost();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception exc) {
            return url;
        }
    }

    private void copyResult() {
        if (currentResult == null) {
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            return;
        }
        manager.setPrimaryClip(ClipData.newPlainText("link", currentResult));
        toast("copiado");
    }

    private void openResult() {
        if (currentResult == null) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentResult)));
        } catch (Exception exc) {
            toast("nenhum app pra abrir");
        }
    }

    private void shareResult() {
        if (currentResult == null) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, currentResult);
        startActivity(Intent.createChooser(send, "enviar link"));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class Glow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Glow(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = Math.max(getWidth(), getHeight()) * 0.62f;
            float cx = getWidth() * 0.82f;
            float cy = -getHeight() * 0.05f;
            paint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{0x3D2E9BFF, 0x12147FD8, 0x00000000},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    private static final class Shimmer extends Drawable
            implements android.graphics.drawable.Animatable {

        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF box = new RectF();
        private final float radius;
        private final int startColor;
        private final int endColor;
        private final ValueAnimator animator;
        private float phase;

        Shimmer(float radius, int startColor, int endColor) {
            this.radius = radius;
            this.startColor = startColor;
            this.endColor = endColor;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1150);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    phase = (float) animation.getAnimatedValue();
                    invalidateSelf();
                }
            });
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            box.set(bounds);
            clip.reset();
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);

            body.setShader(new LinearGradient(0f, bounds.top, 0f, bounds.bottom,
                    startColor, endColor, Shader.TileMode.CLAMP));
            canvas.drawPath(clip, body);

            canvas.save();
            canvas.clipPath(clip);
            float width = bounds.width();
            float x = bounds.left - width + phase * (width * 2.2f);
            sheen.setShader(new LinearGradient(x, 0f, x + width * 0.6f, 0f,
                    new int[]{0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(box, sheen);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void start() {
            if (!animator.isStarted()) {
                animator.start();
            }
        }

        @Override
        public void stop() {
            animator.cancel();
        }

        @Override
        public boolean isRunning() {
            return animator.isRunning();
        }
    }
}
