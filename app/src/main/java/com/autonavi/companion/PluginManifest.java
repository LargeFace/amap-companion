package com.autonavi.companion;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class PluginManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final String CAP_FONT = "font";
    public static final String CAP_ICONS = "icons";
    public static final String CAP_UI = "ui";
    public static final String CAP_OVERLAY_STYLE = "overlayStyle";

    public final int schemaVersion;
    public final String id;
    public final String name;
    public final int versionCode;
    public final String versionName;
    public final String developerName;
    public final String developerHomepage;
    public final String description;
    public final int minAppVersionCode;
    public final HashSet<String> capabilities;
    public final HashMap<String, String> entries;
    public File directory;

    private PluginManifest(int schemaVersion, String id, String name, int versionCode,
                           String versionName, String developerName, String developerHomepage,
                           String description, int minAppVersionCode,
                           HashSet<String> capabilities, HashMap<String, String> entries) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.name = name;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.developerName = developerName;
        this.developerHomepage = developerHomepage;
        this.description = description;
        this.minAppVersionCode = minAppVersionCode;
        this.capabilities = capabilities;
        this.entries = entries;
    }

    public static PluginManifest parse(String text) throws Exception {
        return parse(new JSONObject(text));
    }

    public static PluginManifest parse(JSONObject object) throws Exception {
        int schemaVersion = object.optInt("schemaVersion", 0);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("不支持的插件 schemaVersion: " + schemaVersion);
        }
        String id = object.optString("id", "").trim();
        validatePluginId(id);
        String name = object.optString("name", id).trim();
        if (TextUtils.isEmpty(name)) {
            name = id;
        }
        int versionCode = object.optInt("versionCode", 0);
        if (versionCode <= 0) {
            throw new IllegalArgumentException("插件 versionCode 必须大于 0");
        }
        String versionName = object.optString("versionName", String.valueOf(versionCode)).trim();
        JSONObject developer = object.optJSONObject("developer");
        String developerName = developer == null ? "" : developer.optString("name", "").trim();
        String developerHomepage = developer == null ? "" : developer.optString("homepage", "").trim();
        String description = object.optString("description", "").trim();
        int minAppVersionCode = Math.max(0, object.optInt("minAppVersionCode", 0));

        HashSet<String> capabilities = new HashSet<>();
        JSONArray caps = object.optJSONArray("capabilities");
        if (caps != null) {
            for (int i = 0; i < caps.length(); i++) {
                String cap = caps.optString(i, "").trim();
                if (isKnownCapability(cap)) {
                    capabilities.add(cap);
                }
            }
        }
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("插件至少需要声明一个 capability");
        }

        HashMap<String, String> entries = new HashMap<>();
        JSONObject entryObject = object.optJSONObject("entry");
        if (entryObject != null) {
            for (String cap : new String[]{CAP_FONT, CAP_ICONS, CAP_UI, CAP_OVERLAY_STYLE}) {
                String entry = entryObject.optString(cap, "").trim();
                if (!TextUtils.isEmpty(entry)) {
                    validateRelativePath(entry);
                    entries.put(cap, entry);
                }
            }
        }
        return new PluginManifest(schemaVersion, id, name, versionCode, versionName,
                developerName, developerHomepage, description, minAppVersionCode,
                capabilities, entries);
    }

    public static void validatePluginId(String id) {
        if (TextUtils.isEmpty(id) || !id.matches("[A-Za-z0-9._-]{3,80}")) {
            throw new IllegalArgumentException("非法插件 id: " + id);
        }
    }

    public static void validateRelativePath(String path) {
        if (TextUtils.isEmpty(path)
                || path.startsWith("/")
                || path.startsWith("\\")
                || path.contains("\\")
                || path.contains("..")) {
            throw new IllegalArgumentException("非法插件路径: " + path);
        }
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    public File entryFile(String capability) {
        if (directory == null) {
            return null;
        }
        String relative = entries.get(capability);
        if (TextUtils.isEmpty(relative)) {
            return null;
        }
        return new File(directory, relative);
    }

    public String capabilityLabel() {
        List<String> labels = new ArrayList<>();
        if (hasCapability(CAP_FONT)) {
            labels.add("字体");
        }
        if (hasCapability(CAP_ICONS)) {
            labels.add("图标");
        }
        if (hasCapability(CAP_UI)) {
            labels.add("全局界面");
        }
        if (hasCapability(CAP_OVERLAY_STYLE)) {
            labels.add("悬浮窗样式");
        }
        return TextUtils.join(" / ", labels);
    }

    public String displayDeveloper() {
        return TextUtils.isEmpty(developerName) ? "未知开发者" : developerName;
    }

    static boolean isKnownCapability(String capability) {
        return CAP_FONT.equals(capability)
                || CAP_ICONS.equals(capability)
                || CAP_UI.equals(capability)
                || CAP_OVERLAY_STYLE.equals(capability);
    }
}
