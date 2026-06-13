package com.autonavi.companion;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class PluginManager {
    public static final long MAX_PLUGIN_BYTES = 20L * 1024L * 1024L;
    private static final String ROOT_DIR = "amap_companion/plugins";

    private PluginManager() {
    }

    public static File rootDir() {
        return new File(Environment.getExternalStorageDirectory(), ROOT_DIR);
    }

    public static File privateRootDir(Context context) {
        return new File(context.getFilesDir(), "plugins");
    }

    public static File installRootDir(Context context) {
        File publicRoot = rootDir();
        if (ensureWritableDirectory(publicRoot)) {
            return publicRoot;
        }
        File privateRoot = privateRootDir(context);
        if (ensureWritableDirectory(privateRoot)) {
            return privateRoot;
        }
        throw new IllegalStateException("无法创建公共或私有插件目录");
    }

    private static ArrayList<File> pluginRoots(Context context) {
        ArrayList<File> roots = new ArrayList<>();
        File publicRoot = rootDir();
        File privateRoot = privateRootDir(context);
        if (ensureWritableDirectory(publicRoot)) {
            roots.add(publicRoot);
            roots.add(privateRoot);
        } else {
            roots.add(privateRoot);
            roots.add(publicRoot);
        }
        return roots;
    }

    public static ArrayList<PluginManifest> installedPlugins(Context context) {
        ArrayList<PluginManifest> plugins = new ArrayList<>();
        HashSet<String> seenIds = new HashSet<>();
        for (File root : pluginRoots(context)) {
            File[] dirs = root.listFiles();
            if (dirs == null) {
                continue;
            }
            for (File dir : dirs) {
                if (!dir.isDirectory() || dir.getName().startsWith(".")) {
                    continue;
                }
                try {
                    PluginManifest manifest = readManifest(dir);
                    if (manifest != null && seenIds.add(manifest.id)) {
                        plugins.add(manifest);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        Collections.sort(plugins, Comparator
                .comparing((PluginManifest p) -> p.name.toLowerCase(java.util.Locale.CHINA))
                .thenComparing(p -> p.id));
        return plugins;
    }

    public static PluginManifest readManifest(File pluginDir) throws Exception {
        File manifestFile = new File(pluginDir, "plugin.json");
        if (!manifestFile.isFile()) {
            return null;
        }
        String text = readText(manifestFile);
        PluginManifest manifest = PluginManifest.parse(text);
        manifest.directory = pluginDir;
        return manifest;
    }

    public static PluginManifest activeManifest(Context context, String capability) {
        String id = getEnabledPluginId(context, capability);
        if (TextUtils.isEmpty(id)) {
            return null;
        }
        try {
            PluginManifest.validatePluginId(id);
            PluginManifest manifest = installedPlugin(context, id);
            if (manifest != null && manifest.hasCapability(capability)) {
                return manifest;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static PluginManifest installedPlugin(Context context, String pluginId) {
        PluginManifest.validatePluginId(pluginId);
        for (File root : pluginRoots(context)) {
            try {
                PluginManifest manifest = readManifest(new File(root, pluginId));
                if (manifest != null) {
                    return manifest;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static String getEnabledPluginId(Context context, String capability) {
        return context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
                .getString(keyForCapability(capability), "");
    }

    public static void setEnabledPluginId(Context context, String capability, String pluginId) {
        SharedPreferences.Editor editor = context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE).edit();
        String key = keyForCapability(capability);
        if (TextUtils.isEmpty(pluginId)) {
            editor.remove(key);
        } else {
            PluginManifest.validatePluginId(pluginId);
            editor.putString(key, pluginId);
        }
        editor.apply();
    }

    public static String keyForCapability(String capability) {
        if (PluginManifest.CAP_FONT.equals(capability)) {
            return AppPrefs.KEY_PLUGIN_FONT_ID;
        }
        if (PluginManifest.CAP_ICONS.equals(capability)) {
            return AppPrefs.KEY_PLUGIN_ICONS_ID;
        }
        if (PluginManifest.CAP_UI.equals(capability)) {
            return AppPrefs.KEY_PLUGIN_UI_ID;
        }
        throw new IllegalArgumentException("unknown capability: " + capability);
    }

    public static PluginManifest installFromUri(Context context, Uri uri, boolean replaceExisting) throws Exception {
        File temp = new File(context.getCacheDir(), "plugin-import.acplugin");
        copyUriToFile(context, uri, temp);
        return installFromFile(context, temp, replaceExisting);
    }

    public static PluginManifest installFromFile(Context context, File pluginFile, boolean replaceExisting) throws Exception {
        if (pluginFile == null || !pluginFile.isFile()) {
            throw new IllegalArgumentException("插件文件不存在");
        }
        if (pluginFile.length() <= 0 || pluginFile.length() > MAX_PLUGIN_BYTES) {
            throw new IllegalArgumentException("插件大小必须在 1B 到 20MB 之间");
        }
        PluginManifest manifest = inspectZipManifest(pluginFile);
        int appVersion = currentAppVersionCode(context);
        if (manifest.minAppVersionCode > appVersion) {
            throw new IllegalArgumentException("插件需要更高版本 AMap Companion: " + manifest.minAppVersionCode);
        }
        PluginManifest installedAnywhere = installedPlugin(context, manifest.id);
        if (installedAnywhere != null && !replaceExisting) {
            throw new IllegalArgumentException("插件已安装: " + manifest.id);
        }
        File root = installRootDir(context);
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("无法创建插件目录: " + root.getAbsolutePath());
        }
        File existing = new File(root, manifest.id);
        if (existing.exists() && !replaceExisting) {
            throw new IllegalArgumentException("插件已安装: " + manifest.id);
        }
        File tempDir = new File(root, ".installing_" + manifest.id + "_" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new IllegalStateException("无法创建临时安装目录");
        }
        try {
            extractZip(pluginFile, tempDir);
            PluginManifest installed = readManifest(tempDir);
            if (installed == null || !manifest.id.equals(installed.id)) {
                throw new IllegalArgumentException("插件 manifest 不一致");
            }
            validateEntryFiles(installed);
            if (existing.exists()) {
                deleteRecursively(existing);
            }
            if (!tempDir.renameTo(existing)) {
                throw new IllegalStateException("无法移动插件到安装目录");
            }
            installed.directory = existing;
            for (File otherRoot : pluginRoots(context)) {
                if (!sameFile(root, otherRoot)) {
                    deleteRecursively(new File(otherRoot, manifest.id));
                }
            }
            return installed;
        } catch (Throwable t) {
            deleteRecursively(tempDir);
            throw t;
        }
    }

    public static void deletePlugin(Context context, String pluginId) {
        PluginManifest.validatePluginId(pluginId);
        for (String capability : new String[]{PluginManifest.CAP_FONT, PluginManifest.CAP_ICONS, PluginManifest.CAP_UI}) {
            if (pluginId.equals(getEnabledPluginId(context, capability))) {
                setEnabledPluginId(context, capability, "");
            }
        }
        String selectedStyle = AppPrefs.getOverlayUiStyle(context);
        if (OverlayUiStyles.isPluginStyle(selectedStyle)
                && pluginId.equals(OverlayUiStyles.pluginIdFromStyle(selectedStyle))) {
            context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(AppPrefs.KEY_OVERLAY_UI_STYLE, OverlayUiStyles.OLD)
                    .apply();
        }
        for (File root : pluginRoots(context)) {
            deleteRecursively(new File(root, pluginId));
        }
    }

    private static PluginManifest inspectZipManifest(File file) throws Exception {
        ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
        try {
            ZipEntry entry;
            boolean hasManifest = false;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = normalizedZipEntryName(entry);
                if ("plugin.json".equals(entryName)) {
                    hasManifest = true;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    return PluginManifest.parse(out.toString("UTF-8"));
                }
            }
            if (!hasManifest) {
                throw new IllegalArgumentException("插件缺少 plugin.json");
            }
            throw new IllegalArgumentException("无法读取 plugin.json");
        } finally {
            zip.close();
        }
    }

    private static void validateEntryFiles(PluginManifest manifest) {
        for (String cap : manifest.capabilities) {
            File entry = manifest.entryFile(cap);
            if (entry == null || !entry.isFile()) {
                throw new IllegalArgumentException("插件缺少 " + cap + " 入口文件");
            }
        }
    }

    private static void extractZip(File file, File dest) throws Exception {
        String destPath = dest.getCanonicalPath() + File.separator;
        ZipInputStream zip = new ZipInputStream(new FileInputStream(file));
        long total = 0L;
        try {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = normalizedZipEntryName(entry);
                File out = new File(dest, entryName);
                String outPath = out.getCanonicalPath();
                if (!outPath.startsWith(destPath)) {
                    throw new IllegalArgumentException("插件压缩包包含非法路径");
                }
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) {
                        throw new IllegalStateException("无法创建目录: " + out.getAbsolutePath());
                    }
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("无法创建目录: " + parent.getAbsolutePath());
                }
                FileOutputStream fos = new FileOutputStream(out);
                try {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_PLUGIN_BYTES) {
                            throw new IllegalArgumentException("插件解压后超过 20MB");
                        }
                        fos.write(buffer, 0, read);
                    }
                } finally {
                    fos.close();
                }
            }
        } finally {
            zip.close();
        }
    }

    private static String normalizedZipEntryName(ZipEntry entry) {
        String rawName = entry.getName();
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        while (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        if (TextUtils.isEmpty(name) || name.startsWith("/") || name.contains(":")) {
            throw new IllegalArgumentException("插件压缩包包含非法路径: " + rawName);
        }
        String[] segments = name.split("/");
        for (String segment : segments) {
            if (TextUtils.isEmpty(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("插件压缩包包含非法路径: " + rawName);
            }
        }
        return name;
    }

    private static void copyUriToFile(Context context, Uri uri, File out) throws Exception {
        InputStream input = context.getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IllegalArgumentException("无法打开插件文件");
        }
        FileOutputStream fos = new FileOutputStream(out);
        long total = 0L;
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_PLUGIN_BYTES) {
                    throw new IllegalArgumentException("插件大小超过 20MB");
                }
                fos.write(buffer, 0, read);
            }
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
            }
            fos.close();
        }
    }

    private static String readText(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return out.toString("UTF-8");
    }

    private static int currentAppVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return (int) info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private static boolean ensureWritableDirectory(File directory) {
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return false;
            }
            File probe = new File(directory, ".write_probe_" + System.nanoTime());
            if (!probe.createNewFile()) {
                return false;
            }
            return probe.delete() || !probe.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sameFile(File first, File second) {
        try {
            return first.getCanonicalFile().equals(second.getCanonicalFile());
        } catch (Throwable ignored) {
            return first.getAbsolutePath().equals(second.getAbsolutePath());
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
