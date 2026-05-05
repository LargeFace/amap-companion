package com.autonavi.companion;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class Updater {
    interface Listener {
        void onStatus(String message);
    }

    static final String UPDATE_APK_NAME = "amap_companion_update.apk";

    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 30000;

    private Updater() {
    }

    static UpdateInfo check(Context context, String updateUrl) throws Exception {
        if (TextUtils.isEmpty(updateUrl)) {
            throw new IllegalStateException("请先设置更新地址");
        }
        JSONObject manifest = new JSONObject(readText(updateUrl));
        String packageName = manifest.optString("packageName", context.getPackageName());
        if (!context.getPackageName().equals(packageName)) {
            throw new IllegalStateException("更新包名不匹配: " + packageName);
        }

        int localVersionCode = localVersionCode(context);
        String localVersionName = localVersionName(context);
        int remoteVersionCode = manifest.optInt("versionCode", -1);
        String remoteVersionName = manifest.optString("versionName", "");
        String apkUrl = manifest.optString("apkUrl", "");
        if (!TextUtils.isEmpty(apkUrl)) {
            apkUrl = resolveUrl(updateUrl, apkUrl);
        }
        String changelogUrl = manifest.optString("changelogUrl", "");
        if (!TextUtils.isEmpty(changelogUrl)) {
            changelogUrl = resolveUrl(updateUrl, changelogUrl);
        }
        String changelog = changelogText(manifest);
        if (!TextUtils.isEmpty(changelogUrl)) {
            try {
                String remoteChangelog = readText(changelogUrl).trim();
                if (!TextUtils.isEmpty(remoteChangelog)) {
                    changelog = remoteChangelog;
                }
            } catch (Throwable ignored) {
            }
        }
        return new UpdateInfo(
                updateUrl,
                packageName,
                localVersionCode,
                localVersionName,
                remoteVersionCode,
                remoteVersionName,
                apkUrl,
                manifest.optString("sha256", ""),
                manifest.optLong("size", -1L),
                manifest.optBoolean("force", false),
                changelog,
                changelogUrl);
    }

    static void install(Context context, UpdateInfo info, Listener listener) {
        try {
            if (!info.hasUpdate()) {
                notify(listener, "已是最新版本: " + info.localVersionName + " (" + info.localVersionCode + ")");
                return;
            }
            if (TextUtils.isEmpty(info.apkUrl)) {
                notify(listener, "更新接口未提供 APK 地址");
                return;
            }
            File apk = new File(context.getCacheDir(), UPDATE_APK_NAME);
            download(info.apkUrl, apk, listener);
            verifyApk(apk, info);
            notify(listener, "下载完成，正在通过 PackageInstaller 安装...");
            installViaPackageInstaller(context, apk, listener);
        } catch (Throwable t) {
            notify(listener, "更新失败: " + t.getMessage());
        }
    }

    private static int localVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return (int) info.getLongVersionCode();
        }
        return info.versionCode;
    }

    private static String localVersionName(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionName == null ? "" : info.versionName;
    }

    private static String readText(String urlText) throws Exception {
        HttpURLConnection conn = connect(urlText);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private static void download(String urlText, File out, Listener listener) throws Exception {
        HttpURLConnection conn = connect(urlText);
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("APK HTTP " + code);
            }
            int total = conn.getContentLength();
            InputStream input = new BufferedInputStream(conn.getInputStream());
            FileOutputStream output = new FileOutputStream(out);
            byte[] buffer = new byte[64 * 1024];
            int read;
            long done = 0;
            int lastPercent = -1;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent >= lastPercent + 10) {
                        lastPercent = percent;
                        notify(listener, "正在下载 APK: " + percent + "%");
                    }
                }
            }
            output.close();
            input.close();
        } finally {
            conn.disconnect();
        }
    }

    private static void verifyApk(File apk, UpdateInfo info) throws Exception {
        if (info.size > 0 && apk.length() != info.size) {
            throw new IllegalStateException("APK 大小校验失败: " + apk.length() + " != " + info.size);
        }
        if (!TextUtils.isEmpty(info.sha256)) {
            String actual = sha256(apk);
            if (!info.sha256.equalsIgnoreCase(actual)) {
                throw new IllegalStateException("APK SHA-256 校验失败\n" + actual);
            }
        }
    }

    private static HttpURLConnection open(String urlText) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json,*/*");
        conn.setRequestProperty("User-Agent", "AMapCompanionUpdater/1.0");
        return conn;
    }

    private static HttpURLConnection connect(String urlText) throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpURLConnection conn = open(urlText);
            int code = conn.getResponseCode();
            if (code < 300 || code >= 400) {
                return conn;
            }
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location == null) {
                throw new IllegalStateException("HTTP " + code + " without Location");
            }
            urlText = new URL(new URL(urlText), location).toString();
        }
        throw new IllegalStateException("Too many redirects");
    }

    private static String resolveUrl(String baseUrl, String value) {
        Uri uri = Uri.parse(value);
        if (uri.isAbsolute()) {
            return value;
        }
        Uri base = Uri.parse(baseUrl);
        return base.buildUpon().path(value.startsWith("/") ? value : parentPath(base.getPath()) + value).build().toString();
    }

    private static String parentPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return "/";
        }
        int index = path.lastIndexOf('/');
        if (index < 0) {
            return "/";
        }
        return path.substring(0, index + 1);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream input = new FileInputStream(file);
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        input.close();
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static void installViaPackageInstaller(Context context, File apk, Listener listener) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 26
                && !context.getPackageManager().canRequestPackageInstalls()) {
            throw new IllegalStateException("请先授予“安装未知应用”权限");
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        String action = context.getPackageName() + ".INSTALL_RESULT_" + sessionId;

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    Updater.notify(listener, "安装成功，如未立即生效请重启应用");
                    safeUnregister(ctx, this);
                    return;
                }
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(confirm);
                        Updater.notify(listener, "请在系统安装确认窗口中操作");
                    } else {
                        Updater.notify(listener, "系统要求确认安装，但未提供确认窗口");
                        safeUnregister(ctx, this);
                    }
                    return;
                }
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Updater.notify(listener, "安装失败 [status=" + status + "]"
                        + (TextUtils.isEmpty(msg) ? "" : "\n" + msg));
                safeUnregister(ctx, this);
            }
        };
        context.registerReceiver(receiver, new IntentFilter(action));

        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            long sizeBytes = apk.length();
            InputStream in = new FileInputStream(apk);
            OutputStream out = session.openWrite("package", 0, sizeBytes);
            byte[] buffer = new byte[64 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            session.fsync(out);
            out.close();
            in.close();

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    new Intent(action).setPackage(context.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            IntentSender statusReceiver = pendingIntent.getIntentSender();
            session.commit(statusReceiver);
            notify(listener, "已提交安装请求，等待系统处理...");
        } finally {
            session.close();
        }
    }

    private static void safeUnregister(Context context, BroadcastReceiver receiver) {
        try {
            context.unregisterReceiver(receiver);
        } catch (Throwable ignored) {
        }
    }

    private static String changelogText(JSONObject manifest) {
        JSONArray array = manifest.optJSONArray("changelog");
        if (array == null || array.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            String item = array.optString(i, "");
            if (TextUtils.isEmpty(item)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("- ").append(item);
        }
        return sb.toString();
    }

    private static void notify(Listener listener, String message) {
        if (listener != null) {
            listener.onStatus(message);
        }
    }

    static final class UpdateInfo {
        final String updateUrl;
        final String packageName;
        final int localVersionCode;
        final String localVersionName;
        final int remoteVersionCode;
        final String remoteVersionName;
        final String apkUrl;
        final String sha256;
        final long size;
        final boolean force;
        final String changelog;
        final String changelogUrl;

        UpdateInfo(String updateUrl, String packageName, int localVersionCode, String localVersionName,
                   int remoteVersionCode, String remoteVersionName, String apkUrl, String sha256,
                   long size, boolean force, String changelog, String changelogUrl) {
            this.updateUrl = updateUrl;
            this.packageName = packageName;
            this.localVersionCode = localVersionCode;
            this.localVersionName = localVersionName;
            this.remoteVersionCode = remoteVersionCode;
            this.remoteVersionName = remoteVersionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.size = size;
            this.force = force;
            this.changelog = changelog;
            this.changelogUrl = changelogUrl;
        }

        boolean hasUpdate() {
            return remoteVersionCode > localVersionCode;
        }

        String detailText() {
            StringBuilder sb = new StringBuilder();
            sb.append("当前版本: ").append(localVersionName).append(" (").append(localVersionCode).append(")\n");
            sb.append("最新版本: ").append(remoteVersionName).append(" (").append(remoteVersionCode).append(")\n");
            if (size > 0) {
                sb.append("APK 大小: ").append(size / 1024).append(" KB\n");
            }
            if (force) {
                sb.append("强制更新: 是\n");
            }
            if (!TextUtils.isEmpty(changelog)) {
                sb.append("\n更新日志:\n").append(changelog);
            } else {
                sb.append("\n更新日志: 暂无");
            }
            return sb.toString();
        }
    }
}
