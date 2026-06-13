package com.autonavi.companion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;

public final class PluginAssets {
    private static String cachedFontPath;
    private static long cachedFontModified;
    private static Typeface cachedFont;
    private static String cachedIconPluginId;
    private static long cachedIconModified;
    private static HashMap<String, String> cachedIconMap;

    private PluginAssets() {
    }

    public static Typeface activeTypeface(Context context) {
        PluginManifest manifest = PluginManager.activeManifest(context, PluginManifest.CAP_FONT);
        if (manifest == null) {
            return null;
        }
        File file = manifest.entryFile(PluginManifest.CAP_FONT);
        if (file == null || !file.isFile()) {
            return null;
        }
        String path = file.getAbsolutePath();
        long modified = file.lastModified();
        if (cachedFont != null && path.equals(cachedFontPath) && modified == cachedFontModified) {
            return cachedFont;
        }
        try {
            Typeface typeface = Typeface.createFromFile(file);
            cachedFontPath = path;
            cachedFontModified = modified;
            cachedFont = typeface;
            return typeface;
        } catch (Throwable ignored) {
            cachedFontPath = null;
            cachedFontModified = 0L;
            cachedFont = null;
            return null;
        }
    }

    public static Bitmap activeIconBitmap(Context context, String... names) {
        File file = activeIconFile(context, names);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static File activeImageFile(Context context, String name) {
        PluginManifest manifest = PluginManager.activeManifest(context, PluginManifest.CAP_ICONS);
        if (manifest == null || manifest.directory == null) {
            return null;
        }
        String safe = name == null ? "" : name.trim();
        if (TextUtils.isEmpty(safe)) {
            return null;
        }
        try {
            PluginManifest.validateRelativePath(safe);
        } catch (Throwable ignored) {
            return null;
        }
        File direct = new File(manifest.directory, safe);
        if (direct.isFile()) {
            return direct;
        }
        return activeIconFile(context, safe);
    }

    public static File activeIconFile(Context context, String... names) {
        PluginManifest manifest = PluginManager.activeManifest(context, PluginManifest.CAP_ICONS);
        if (manifest == null || manifest.directory == null) {
            return null;
        }
        HashMap<String, String> map = iconMap(manifest);
        for (String raw : names) {
            String name = raw == null ? "" : raw.trim();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            String mapped = map.get(name);
            if (!TextUtils.isEmpty(mapped)) {
                File mappedFile = new File(manifest.directory, mapped);
                if (mappedFile.isFile()) {
                    return mappedFile;
                }
            }
            File conventional = conventionalIconFile(manifest.directory, name);
            if (conventional != null) {
                return conventional;
            }
        }
        return null;
    }

    private static File conventionalIconFile(File directory, String name) {
        String base = name.replace('\\', '/');
        if (base.contains("..") || base.startsWith("/")) {
            return null;
        }
        String[] candidates = {
                "icons/" + base + ".png",
                "icons/" + base + ".webp",
                "icons/" + base + ".jpg",
                "icons/" + base + ".jpeg",
                "assets/" + base + ".png",
                "assets/" + base + ".webp",
                base + ".png",
                base + ".webp",
                base + ".jpg",
                base + ".jpeg"
        };
        for (String candidate : candidates) {
            File file = new File(directory, candidate);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static HashMap<String, String> iconMap(PluginManifest manifest) {
        File file = manifest.entryFile(PluginManifest.CAP_ICONS);
        if (file == null || !file.isFile()) {
            return new HashMap<>();
        }
        long modified = file.lastModified();
        if (manifest.id.equals(cachedIconPluginId)
                && cachedIconMap != null
                && modified == cachedIconModified) {
            return cachedIconMap;
        }
        HashMap<String, String> map = new HashMap<>();
        try {
            JSONObject root = new JSONObject(readText(file));
            JSONObject icons = root.optJSONObject("icons");
            if (icons == null) {
                icons = root;
            }
            java.util.Iterator<String> keys = icons.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = icons.optString(key, "").trim();
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    PluginManifest.validateRelativePath(value);
                    map.put(key, value);
                }
            }
        } catch (Throwable ignored) {
        }
        cachedIconPluginId = manifest.id;
        cachedIconModified = modified;
        cachedIconMap = map;
        return map;
    }

    private static String readText(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return out.toString("UTF-8");
    }
}
