package com.autonavi.companion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
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

    enum InstallMode {
        PM_INSTALL,
        ADB_INSTALL,
        SYSTEM_INSTALLER,
        DEVICE_OWNER
    }

    static final String UPDATE_APK_NAME = "amap_companion_update.apk";

    private static final int CONNECT_TIMEOUT_MS = 12000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String[] TMP_PATHS = {
            "/data/local/tmp/" + UPDATE_APK_NAME
    };

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
                changelogText(manifest),
                manifest.optString("changelogUrl", ""));
    }

    static void install(Context context, UpdateInfo info, InstallMode mode, Listener listener) {
        try {
            if (mode == InstallMode.DEVICE_OWNER) {
                notify(listener, "设备管理员静默安装模式尚未实现");
                return;
            }
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

            if (mode == InstallMode.SYSTEM_INSTALLER) {
                openSystemInstaller(context, apk);
                notify(listener, "已打开系统安装器");
                return;
            }

            String commandName = mode == InstallMode.ADB_INSTALL ? "adb install" : "pm install";
            notify(listener, "下载完成，正在通过 " + commandName + " 安装...");
            CommandResult result = installFromTmpPath(apk, mode, listener);
            if (result.exitCode == 0) {
                notify(listener, "更新已安装，如未立即生效请重启应用");
            } else {
                notify(listener, commandName + " 失败, exit=" + result.exitCode + "\n" + result.output);
            }
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
        HttpURLConnection conn = open(urlText);
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
        HttpURLConnection conn = open(urlText);
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

    private static CommandResult installFromTmpPath(File apk, InstallMode mode, Listener listener) throws Exception {
        StringBuilder errors = new StringBuilder();
        for (String path : TMP_PATHS) {
            notify(listener, "正在写入 " + path);
            CommandResult copy = copyToShellPath(apk, path);
            if (copy.exitCode != 0) {
                appendError(errors, "写入 " + path, copy);
                continue;
            }
            String command = mode == InstallMode.ADB_INSTALL
                    ? "adb install -r -d " + shellQuote(path)
                    : "pm install -r -d " + shellQuote(path);
            CommandResult install = runShell(command);
            runShell("rm -f " + shellQuote(path));
            if (install.exitCode == 0) {
                return install;
            }
            appendError(errors, command, install);
        }
        return new CommandResult(1, errors.toString().trim());
    }

    private static CommandResult copyToShellPath(File file, String targetPath) throws Exception {
        String dir = targetPath.substring(0, targetPath.lastIndexOf('/'));
        Process process = Runtime.getRuntime().exec(new String[]{
                "sh",
                "-c",
                "mkdir -p " + shellQuote(dir) + " && cat > " + shellQuote(targetPath) + " && chmod 0644 " + shellQuote(targetPath)
        });
        try {
            streamFileToProcess(file, process.getOutputStream());
        } catch (Exception ignored) {
            try {
                process.getOutputStream().close();
            } catch (Exception ignored2) {
                // ignore
            }
        }
        int exitCode = process.waitFor();
        String output = readProcess(process);
        return new CommandResult(exitCode, output);
    }

    private static CommandResult runShell(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        int exitCode = process.waitFor();
        String output = readProcess(process);
        return new CommandResult(exitCode, output);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void appendError(StringBuilder sb, String step, CommandResult result) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(step).append("\nexit=").append(result.exitCode);
        if (!TextUtils.isEmpty(result.output)) {
            sb.append("\n").append(result.output);
        }
    }

    private static void streamFileToProcess(File file, OutputStream output) throws Exception {
        FileInputStream input = new FileInputStream(file);
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
        output.close();
        input.close();
    }

    private static void openSystemInstaller(Context context, File apk) {
        Uri uri = Uri.parse("content://" + context.getPackageName() + ".apkprovider/" + UPDATE_APK_NAME);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static String readProcess(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        readStream(process.getInputStream(), sb);
        readStream(process.getErrorStream(), sb);
        return sb.toString().trim();
    }

    private static void readStream(InputStream stream, StringBuilder sb) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        reader.close();
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

    private static final class CommandResult {
        final int exitCode;
        final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
