package com.autonavi.companion;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;

public final class PluginRepository {
    private static final int TIMEOUT_MS = 15000;

    private PluginRepository() {
    }

    public static String marketUrl(Context context) {
        String updateUrl = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_UPDATE_URL, MainActivity.DEFAULT_UPDATE_URL);
        try {
            URL url = new URL(updateUrl);
            return new URL(url, "/plugins.json").toString();
        } catch (Throwable ignored) {
            return "https://amap-companion.zuoqirun.top/plugins.json";
        }
    }

    public static ArrayList<MarketPlugin> fetchMarket(Context context) throws Exception {
        String url = marketUrl(context);
        String text = httpGetText(url);
        String trimmed = text.trim();
        JSONArray plugins;
        if (trimmed.startsWith("[")) {
            plugins = new JSONArray(text);
        } else {
            JSONObject root = new JSONObject(text);
            plugins = root.optJSONArray("plugins");
        }
        ArrayList<MarketPlugin> result = new ArrayList<>();
        if (plugins == null) {
            return result;
        }
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject object = plugins.optJSONObject(i);
            if (object == null) {
                continue;
            }
            MarketPlugin plugin = MarketPlugin.parse(object, url);
            if (plugin != null) {
                result.add(plugin);
            }
        }
        return result;
    }

    public static PluginManifest downloadAndInstall(Context context, MarketPlugin plugin) throws Exception {
        if (plugin == null || TextUtils.isEmpty(plugin.downloadUrl)) {
            throw new IllegalArgumentException("插件缺少下载地址");
        }
        File out = new File(context.getCacheDir(), plugin.id + "-" + plugin.versionCode + ".acplugin");
        downloadToFile(plugin.downloadUrl, out);
        if (!TextUtils.isEmpty(plugin.sha256)) {
            String actual = sha256(out);
            if (!plugin.sha256.equalsIgnoreCase(actual)) {
                throw new IllegalArgumentException("插件 SHA-256 校验失败");
            }
        }
        return PluginManager.installFromFile(context, out, true);
    }

    private static String httpGetText(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "AMap-Companion-Plugin-Client");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            InputStream input = conn.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            input.close();
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private static void downloadToFile(String url, File out) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "AMap-Companion-Plugin-Client");
        long total = 0L;
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            InputStream input = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buffer = new byte[8192];
            int read;
            try {
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > PluginManager.MAX_PLUGIN_BYTES) {
                        throw new IllegalArgumentException("插件大小超过 20MB");
                    }
                    fos.write(buffer, 0, read);
                }
            } finally {
                input.close();
                fos.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream input = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        byte[] bytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public static final class MarketPlugin {
        public final String id;
        public final String name;
        public final int versionCode;
        public final String versionName;
        public final String developerName;
        public final String developerHomepage;
        public final String description;
        public final String capabilitiesLabel;
        public final String downloadUrl;
        public final String sha256;
        public final long size;

        private MarketPlugin(String id, String name, int versionCode, String versionName,
                             String developerName, String developerHomepage, String description,
                             String capabilitiesLabel, String downloadUrl, String sha256, long size) {
            this.id = id;
            this.name = name;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.developerName = developerName;
            this.developerHomepage = developerHomepage;
            this.description = description;
            this.capabilitiesLabel = capabilitiesLabel;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.size = size;
        }

        static MarketPlugin parse(JSONObject object, String marketUrl) {
            try {
                String id = object.optString("id", "").trim();
                PluginManifest.validatePluginId(id);
                String name = object.optString("name", id).trim();
                int versionCode = object.optInt("versionCode", 0);
                String versionName = object.optString("versionName", String.valueOf(versionCode));
                JSONObject developer = object.optJSONObject("developer");
                String developerName = developer == null ? "" : developer.optString("name", "");
                String developerHomepage = developer == null ? "" : developer.optString("homepage", "");
                String description = object.optString("description", "");
                String download = object.optString("downloadUrl",
                        object.optString("url", object.optString("path", ""))).trim();
                if (TextUtils.isEmpty(download)) {
                    return null;
                }
                URL absolute = new URL(new URL(marketUrl), download);
                JSONArray caps = object.optJSONArray("capabilities");
                ArrayList<String> labels = new ArrayList<>();
                if (caps != null) {
                    for (int i = 0; i < caps.length(); i++) {
                        String cap = caps.optString(i);
                        if (PluginManifest.CAP_FONT.equals(cap)) labels.add("字体");
                        if (PluginManifest.CAP_ICONS.equals(cap)) labels.add("图标");
                        if (PluginManifest.CAP_UI.equals(cap)) labels.add("UI模板");
                        if (PluginManifest.CAP_OVERLAY_STYLE.equals(cap)) labels.add("悬浮窗样式");
                    }
                }
                return new MarketPlugin(id, TextUtils.isEmpty(name) ? id : name, versionCode,
                        versionName, developerName, developerHomepage, description,
                        TextUtils.join(" / ", labels), absolute.toString(),
                        object.optString("sha256", ""), object.optLong("size", 0L));
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
