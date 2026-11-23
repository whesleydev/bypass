package com.local.bypass;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Guarda o resultado de cada link por 6h para reabrir instantaneo. */
final class LinkCache {

    private static final String STORE = "bypass_links";
    private static final long TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_ENTRIES = 80;

    static final class Entry {
        final String url;
        final List<String> chain;
        final long age;

        Entry(String url, List<String> chain, long age) {
            this.url = url;
            this.chain = chain;
            this.age = age;
        }
    }

    private LinkCache() {
    }

    static Entry get(Context context, String from) {
        try {
            SharedPreferences store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
            String raw = store.getString(key(from), null);
            if (raw == null) {
                return null;
            }
            JSONObject json = new JSONObject(raw);
            long age = System.currentTimeMillis() - json.optLong("ts", 0L);
            if (age < 0 || age > TTL_MS) {
                store.edit().remove(key(from)).apply();
                return null;
            }
            List<String> chain = new ArrayList<>();
            JSONArray items = json.optJSONArray("chain");
            if (items != null) {
                for (int index = 0; index < items.length(); index++) {
                    chain.add(items.optString(index, ""));
                }
            }
            String url = json.optString("to", "");
            if (url.isEmpty()) {
                return null;
            }
            return new Entry(url, chain, age);
        } catch (Exception exc) {
            return null;
        }
    }

    static void put(Context context, String from, String to, List<String> chain) {
        if (from == null || to == null || to.isEmpty()) {
            return;
        }
        try {
            SharedPreferences store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            json.put("to", to);
            json.put("ts", System.currentTimeMillis());
            JSONArray items = new JSONArray();
            if (chain != null) {
                for (String step : chain) {
                    items.put(step);
                }
            }
            json.put("chain", items);
            SharedPreferences.Editor editor = store.edit();
            if (store.getAll().size() > MAX_ENTRIES) {
                editor.clear();
            }
            editor.putString(key(from), json.toString()).apply();
        } catch (Exception ignored) {
            // cache e luxo
        }
    }

    private static String key(String url) {
        return "u_" + Integer.toHexString(url.hashCode()) + "_" + url.length();
    }
}
