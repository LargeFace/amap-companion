package com.autonavi.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {
    static final String PREFS = "amap_companion";
    static final String KEY_TARGET_PACKAGE = "target_package";
    static final String KEY_UPDATE_URL = "update_url";
    static final String KEY_UPDATE_CHANNEL = "update_channel";
    static final String KEY_OVERLAY_SCALE_PERCENT = "overlay_scale_percent";
    static final String ACTION_OVERLAY_SCALE_CHANGED = "com.autonavi.companion.OVERLAY_SCALE_CHANGED";
    static final String DEFAULT_TARGET_PACKAGE = "com.autonavi.amapClone";
    static final String UPDATE_CHANNEL_SERVER = "server";
    static final String UPDATE_CHANNEL_GITHUB = "github";
    static final String DEFAULT_UPDATE_CHANNEL = UPDATE_CHANNEL_SERVER;
    static final String SERVER_UPDATE_URL = "https://amap-companion.zuoqirun.top/update.json";
    static final String GITHUB_UPDATE_URL = "https://amap-companion.zuoqirun.top/update-github.json";
    static final String DEFAULT_UPDATE_URL = SERVER_UPDATE_URL;
    static final int MIN_OVERLAY_SCALE_PERCENT = 80;
    static final int MAX_OVERLAY_SCALE_PERCENT = 300;
    static final int DEFAULT_OVERLAY_SCALE_PERCENT = 200;
    private static final String TARGET_PACKAGE_PREFIX = "com.autonavi.";

    private TextView targetText;
    private TextView updateText;
    private TextView overlayScaleText;
    private FrameLayout overlayPreviewStage;
    private LinearLayout overlayPreviewPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        persistDefaultUpdateUrl();
        setContentView(buildContent());
        startOverlayService();
        targetText.postDelayed(() -> {
            checkForUpdates(false);
        }, 2000L);
    }

    private ScrollView buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFF3F6FA);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout hero = card(0xFF111827);
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("AMap Companion");
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        hero.addView(title, new LinearLayout.LayoutParams(-1, -2));

        targetText = new TextView(this);
        targetText.setTextSize(14f);
        targetText.setTextColor(0xFFD1D5DB);
        targetText.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams targetLp = new LinearLayout.LayoutParams(-1, -2);
        targetLp.setMargins(0, dp(8), 0, 0);
        hero.addView(targetText, targetLp);
        updateTargetText();

        updateText = new TextView(this);
        updateText.setTextSize(13f);
        updateText.setTextColor(0xFFA7F3D0);
        updateText.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(-1, -2);
        updateLp.setMargins(0, dp(8), 0, 0);
        hero.addView(updateText, updateLp);
        updateUpdateText("\u66f4\u65b0\u6e20\u9053\n" + displayUpdateUrl());

        LinearLayout controls = card(Color.WHITE);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.setMargins(0, dp(14), 0, 0);
        root.addView(controls, controlsLp);

        controls.addView(button("\u9009\u62e9\u76ee\u6807\u5e94\u7528", v -> chooseTargetApp(), 0xFF2563EB));
        controls.addView(button("\u6388\u6743\u60ac\u6d6e\u7a97", v -> requestOverlayPermission(), 0xFF475569));
        controls.addView(button("\u542f\u52a8\u60ac\u6d6e\u7a97", v -> startOverlayService(), 0xFF0F766E));
        controls.addView(button("\u5173\u95ed\u60ac\u6d6e\u7a97", v -> stopOverlayService(), 0xFFB45309));
        controls.addView(button("\u6253\u5f00\u76ee\u6807\u5e94\u7528", v -> openTargetApp(), 0xFF111827));
        controls.addView(button("\u9009\u62e9\u4e0b\u8f7d\u6e20\u9053", v -> chooseUpdateChannel(), 0xFF334155));
        controls.addView(button("\u68c0\u67e5\u66f4\u65b0", v -> checkForUpdates(true), 0xFF059669));
        addOverlayScaleControls(controls);

        return scroll;
    }

    private void addOverlayScaleControls(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(10), dp(2), 0);

        overlayScaleText = new TextView(this);
        overlayScaleText.setTextSize(14f);
        overlayScaleText.setTextColor(0xFF111827);
        overlayScaleText.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(overlayScaleText, new LinearLayout.LayoutParams(-1, -2));
        addOverlayPreview(box);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(MAX_OVERLAY_SCALE_PERCENT - MIN_OVERLAY_SCALE_PERCENT);
        seekBar.setProgress(getOverlayScalePercent(this) - MIN_OVERLAY_SCALE_PERCENT);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = MIN_OVERLAY_SCALE_PERCENT + progress;
                updateOverlayScaleText(percent);
                if (fromUser) {
                    saveOverlayScalePercent(percent);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                int percent = MIN_OVERLAY_SCALE_PERCENT + bar.getProgress();
                saveOverlayScalePercent(percent);
                updateOverlayScaleText(percent);
            }
        });
        box.addView(seekBar, new LinearLayout.LayoutParams(-1, -2));
        updateOverlayScaleText(getOverlayScalePercent(this));
        box.addView(button("\u5e94\u7528\u5f53\u524d\u5927\u5c0f\u5230\u60ac\u6d6e\u7a97", v -> notifyOverlayScaleChanged(), 0xFF334155));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        parent.addView(box, lp);
    }

    private void addOverlayPreview(LinearLayout parent) {
        overlayPreviewStage = new FrameLayout(this);
        overlayPreviewStage.setPadding(dp(10), dp(10), dp(10), dp(10));
        overlayPreviewStage.setBackground(navigationPreviewBackground());
        addPreviewRoads(overlayPreviewStage);

        LinearLayout topGuide = buildPreviewTopGuide();
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        topLp.setMargins(dp(6), dp(6), dp(6), 0);
        overlayPreviewStage.addView(topGuide, topLp);

        overlayPreviewPanel = buildOverlayPreviewPanel();
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER | Gravity.BOTTOM);
        panelLp.setMargins(0, dp(66), 0, dp(12));
        overlayPreviewStage.addView(overlayPreviewPanel, panelLp);

        LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(-1, dp(260));
        stageLp.setMargins(0, dp(8), 0, dp(2));
        parent.addView(overlayPreviewStage, stageLp);
    }

    private GradientDrawable navigationPreviewBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bg.setColors(new int[]{0xFF182436, 0xFF0B1320});
        bg.setCornerRadius(dp(8));
        bg.setStroke(dp(1), 0xFF1F2A3A);
        return bg;
    }

    private void addPreviewRoads(FrameLayout stage) {
        stage.addView(previewRoad(dp(260), dp(16), -18f, 0xFF0F8F6D),
                roadLayout(dp(-28), dp(184), dp(360), dp(18)));
        stage.addView(previewRoad(dp(190), dp(11), -18f, 0xFF14B88A),
                roadLayout(dp(190), dp(204), dp(250), dp(13)));
        stage.addView(previewRoad(dp(210), dp(9), 28f, 0xFF24364C),
                roadLayout(dp(10), dp(116), dp(260), dp(12)));
        stage.addView(previewRoad(dp(180), dp(8), 28f, 0xFF24364C),
                roadLayout(dp(210), dp(98), dp(240), dp(10)));
    }

    private FrameLayout.LayoutParams roadLayout(int left, int top, int width, int height) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = left;
        lp.topMargin = top;
        return lp;
    }

    private android.view.View previewRoad(int width, int height, float rotation, int color) {
        android.view.View road = new android.view.View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(height / 2f);
        road.setBackground(bg);
        road.setRotation(rotation);
        road.setAlpha(0.92f);
        return road;
    }

    private LinearLayout buildPreviewTopGuide() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF0000000);
        bg.setCornerRadius(dp(8));
        top.setBackground(bg);

        TextView main = new TextView(this);
        main.setText("\u2190 669 \u7c73  \u8fdb\u5165 \u6986\u4e61\u8def\u8f85\u8def");
        main.setTextSize(15f);
        main.setTextColor(Color.WHITE);
        main.setTypeface(Typeface.DEFAULT_BOLD);
        main.setSingleLine(true);
        top.addView(main, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("5.3\u516c\u91cc \u00b7 10\u5206\u949f                                      05:42\u5230");
        sub.setTextSize(8.5f);
        sub.setTextColor(0xFFD1D5DB);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(5), 0, 0);
        top.addView(sub, subLp);
        return top;
    }

    private LinearLayout buildOverlayPreviewPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(6), dp(5), dp(6), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEA111827);
        bg.setCornerRadius(dp(7));
        bg.setStroke(dp(1), 0x22FFFFFF);
        panel.setBackground(bg);

        TextView mode = new TextView(this);
        mode.setText("\u5bfc\u822a \u00b7 \u5357\u56db\u73af\u4e1c\u8def\u8f85\u8def \u00b7 39 km/h");
        mode.setTextSize(6.5f);
        mode.setTextColor(0xFFE8EAED);
        mode.setSingleLine(true);
        panel.addView(mode, new LinearLayout.LayoutParams(-2, -2));

        TextView turn = new TextView(this);
        turn.setText("\u2190  669\u7c73\n\u8fdb\u5165 \u6986\u4e61\u8def\u8f85\u8def");
        turn.setTextSize(15f);
        turn.setTypeface(Typeface.DEFAULT_BOLD);
        turn.setGravity(Gravity.CENTER);
        turn.setTextColor(Color.WHITE);
        turn.setPadding(dp(12), dp(4), dp(12), dp(5));
        GradientDrawable turnBg = new GradientDrawable();
        turnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        turnBg.setColors(new int[]{0xFF1D4ED8, 0xFF0891B2});
        turnBg.setCornerRadius(dp(5));
        turn.setBackground(turnBg);
        LinearLayout.LayoutParams turnLp = new LinearLayout.LayoutParams(-2, -2);
        turnLp.setMargins(0, dp(3), 0, dp(3));
        panel.addView(turn, turnLp);

        LinearLayout lights = new LinearLayout(this);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setGravity(Gravity.CENTER);
        lights.addView(previewLight("\u2190 51s", 0xFFC62828));
        lights.addView(previewLight("\u2191 18s", 0xFFC62828));
        panel.addView(lights, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout laneSection = new LinearLayout(this);
        laneSection.setOrientation(LinearLayout.VERTICAL);
        laneSection.setGravity(Gravity.CENTER_HORIZONTAL);
        laneSection.setPadding(dp(4), dp(3), dp(4), dp(4));
        GradientDrawable laneBg = new GradientDrawable();
        laneBg.setColor(0xCC0F172A);
        laneBg.setCornerRadius(dp(5));
        laneBg.setStroke(dp(1), 0x1FFFFFFF);
        laneSection.setBackground(laneBg);

        TextView laneTitle = new TextView(this);
        laneTitle.setText("\u8f66\u9053\u4fe1\u606f");
        laneTitle.setTextSize(5.5f);
        laneTitle.setTextColor(0xFFBAE6FD);
        laneTitle.setTypeface(Typeface.DEFAULT_BOLD);
        laneSection.addView(laneTitle, new LinearLayout.LayoutParams(-2, -2));

        LaneBarView laneBar = new LaneBarView(this);
        laneBar.setFrameScaleMultiplier(1f);
        laneBar.setScaleMultiplier(1.5f);
        laneBar.setLaneData(new int[]{15, 31, 18}, new boolean[]{true, false, true});
        laneSection.addView(laneBar, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams laneLp = new LinearLayout.LayoutParams(-2, -2);
        laneLp.setMargins(0, dp(3), 0, dp(2));
        panel.addView(laneSection, laneLp);

        TextView eta = new TextView(this);
        eta.setText("5.3\u516c\u91cc \u00b7 10\u5206\u949f\n\u9884\u8ba105:42\u5230\u8fbe\n\u76ee\u7684\u5730 \u5c0f\u7ea2\u95e8\u4e61\u515a\u7fa4\u670d\u52a1\u4e2d\u5fc3");
        eta.setTextSize(7.5f);
        eta.setTextColor(0xFFE8EAED);
        eta.setGravity(Gravity.CENTER);
        panel.addView(eta, new LinearLayout.LayoutParams(-2, -2));
        return panel;
    }

    private TextView previewLight(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(10f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(31));
        view.setMinHeight(dp(18));
        view.setPadding(dp(5), 0, dp(5), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(9));
        view.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(18));
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        view.setLayoutParams(lp);
        return view;
    }

    private LinearLayout card(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        if (color == Color.WHITE) {
            bg.setStroke(dp(1), 0xFFE5E7EB);
        }
        layout.setBackground(bg);
        return layout;
    }

    private Button button(String text, android.view.View.OnClickListener listener, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15f);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(9), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void chooseTargetApp() {
        PackageManager pm = getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PackageManager.MATCH_ALL : 0;
        HashSet<String> launcherPackages = new HashSet<>();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, flags);
        HashSet<String> seen = new HashSet<>();
        ArrayList<AppChoice> choices = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (!isAmapPackage(pkg)) {
                continue;
            }
            launcherPackages.add(pkg);
            if (pkg.equals(getPackageName())) {
                continue;
            }
            if (!seen.add(pkg)) {
                continue;
            }
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String label = String.valueOf(appInfo.loadLabel(pm));
            choices.add(new AppChoice(label, pkg, isSystemApp(appInfo), true));
        }
        for (ApplicationInfo appInfo : pm.getInstalledApplications(flags)) {
            String pkg = appInfo.packageName;
            if (pkg == null || !isAmapPackage(pkg) || pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            String label = String.valueOf(appInfo.loadLabel(pm));
            choices.add(new AppChoice(label, pkg, isSystemApp(appInfo), launcherPackages.contains(pkg)));
        }
        Collections.sort(choices, Comparator
                .comparing((AppChoice a) -> a.system)
                .thenComparing(a -> a.label.toLowerCase(java.util.Locale.CHINA))
                .thenComparing(a -> a.packageName));
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            AppChoice choice = choices.get(i);
            String type = choice.system ? "\u7cfb\u7edf" : "\u7528\u6237";
            String launch = choice.launchable ? "\u53ef\u6253\u5f00" : "\u65e0\u684c\u9762\u56fe\u6807";
            labels[i] = choice.label + "  \u00b7  " + type + "  \u00b7  " + launch + "\n" + choice.packageName;
        }
        if (choices.isEmpty()) {
            choices.add(new AppChoice(DEFAULT_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE, false, false));
            labels = new String[]{DEFAULT_TARGET_PACKAGE
                    + "\n\u672a\u626b\u63cf\u5230 com.autonavi.* \u5e94\u7528\uff0c\u4f7f\u7528\u9ed8\u8ba4\u5305\u540d"};
        }
        new AlertDialog.Builder(this)
                .setTitle("\u9009\u62e9\u76ee\u6807\u5e94\u7528")
                .setItems(labels, (dialog, which) -> {
                    saveTargetPackage(choices.get(which).packageName);
                    updateTargetText();
                    startOverlayService();
                })
                .show();
    }

    private boolean isSystemApp(ApplicationInfo appInfo) {
        return (appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private boolean isAmapPackage(String packageName) {
        return packageName != null && packageName.startsWith(TARGET_PACKAGE_PREFIX);
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopOverlayService() {
        stopService(new Intent(this, OverlayService.class));
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void openTargetApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getTargetPackage(this));
        if (launch != null) {
            startActivity(launch);
        }
    }

    private void chooseUpdateChannel() {
        String[] labels = {
                "\u670d\u52a1\u5668\u5206\u53d1\uff08\u63a8\u8350\uff09\n" + SERVER_UPDATE_URL,
                "GitHub \u76f4\u8fde\n" + GITHUB_UPDATE_URL
        };
        int checked = UPDATE_CHANNEL_GITHUB.equals(getUpdateChannel()) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("\u9009\u62e9\u4e0b\u8f7d\u6e20\u9053")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveUpdateChannel(which == 1 ? UPDATE_CHANNEL_GITHUB : UPDATE_CHANNEL_SERVER);
                    updateUpdateText("\u66f4\u65b0\u6e20\u9053\n" + displayUpdateUrl());
                    dialog.dismiss();
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private void updateTargetText() {
        if (targetText != null) {
            targetText.setText("\u76ee\u6807\u5e94\u7528\n" + getTargetPackage(this));
        }
    }

    private void checkForUpdates(boolean manual) {
        String url = getUpdateUrl();
        if (TextUtils.isEmpty(url)) {
            if (manual) {
                Toast.makeText(this, "\u66f4\u65b0\u5730\u5740\u672a\u914d\u7f6e", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        updateUpdateText("\u6b63\u5728\u68c0\u67e5\u66f4\u65b0...\n" + url);
        new Thread(() -> {
            try {
                Updater.UpdateInfo info = Updater.check(this, url);
                runOnUiThread(() -> handleUpdateInfo(info, manual));
            } catch (Throwable t) {
                runOnUiThread(() -> updateUpdateText("\u66f4\u65b0\u5931\u8d25: " + t.getMessage()));
            }
        }).start();
    }

    private void handleUpdateInfo(Updater.UpdateInfo info, boolean manual) {
        if (!info.hasUpdate()) {
            updateUpdateText("\u5df2\u662f\u6700\u65b0\u7248\n" + info.localVersionName + " (" + info.localVersionCode + ")");
            if (manual) {
                Toast.makeText(this, "\u5df2\u662f\u6700\u65b0\u7248", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        updateUpdateText("\u53d1\u73b0\u65b0\u7248\n" + info.remoteVersionName + " (" + info.remoteVersionCode + ")");
        showUpdateDetail(info);
    }

    private void showUpdateDetail(Updater.UpdateInfo info) {
        new AlertDialog.Builder(this)
                .setTitle("\u53d1\u73b0\u65b0\u7248")
                .setMessage(info.detailText())
                .setPositiveButton("\u66f4\u65b0", (dialog, which) -> installUpdate(info))
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private void installUpdate(Updater.UpdateInfo info) {
        updateUpdateText("\u51c6\u5907\u66f4\u65b0...\n" + info.remoteVersionName + " (" + info.remoteVersionCode + ")");
        new Thread(() -> Updater.install(this, info,
                message -> runOnUiThread(() -> updateUpdateText(message)))).start();
    }

    private void updateUpdateText(String text) {
        if (updateText != null) {
            updateText.setText(text);
        }
    }

    private void saveTargetPackage(String packageName) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_TARGET_PACKAGE, packageName)
                .apply();
    }

    private void saveUpdateUrl(String url) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_UPDATE_URL, TextUtils.isEmpty(url) ? DEFAULT_UPDATE_URL : url)
                .apply();
    }

    private void saveUpdateChannel(String channel) {
        String normalized = UPDATE_CHANNEL_GITHUB.equals(channel) ? UPDATE_CHANNEL_GITHUB : UPDATE_CHANNEL_SERVER;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_UPDATE_CHANNEL, normalized)
                .putString(KEY_UPDATE_URL, channelToUpdateUrl(normalized))
                .apply();
    }

    private void persistDefaultUpdateUrl() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String channel = prefs.getString(KEY_UPDATE_CHANNEL, DEFAULT_UPDATE_CHANNEL);
        if (!UPDATE_CHANNEL_GITHUB.equals(channel)) {
            channel = UPDATE_CHANNEL_SERVER;
        }
        prefs.edit()
                .putString(KEY_UPDATE_CHANNEL, channel)
                .putString(KEY_UPDATE_URL, channelToUpdateUrl(channel))
                .apply();
    }

    private String getUpdateUrl() {
        return channelToUpdateUrl(getUpdateChannel());
    }

    private String getUpdateChannel() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String channel = prefs.getString(KEY_UPDATE_CHANNEL, DEFAULT_UPDATE_CHANNEL);
        if (UPDATE_CHANNEL_GITHUB.equals(channel)) {
            return UPDATE_CHANNEL_GITHUB;
        }
        String legacyUrl = prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL);
        if (GITHUB_UPDATE_URL.equals(legacyUrl)) {
            return UPDATE_CHANNEL_GITHUB;
        }
        return UPDATE_CHANNEL_SERVER;
    }

    private String channelToUpdateUrl(String channel) {
        return UPDATE_CHANNEL_GITHUB.equals(channel) ? GITHUB_UPDATE_URL : SERVER_UPDATE_URL;
    }

    private String displayUpdateUrl() {
        String url = getUpdateUrl();
        if (TextUtils.isEmpty(url)) {
            return "\u672a\u8bbe\u7f6e";
        }
        String channelName = UPDATE_CHANNEL_GITHUB.equals(getUpdateChannel()) ? "GitHub \u76f4\u8fde" : "\u670d\u52a1\u5668\u5206\u53d1";
        return channelName + "\n" + url;
    }

    private void saveOverlayScalePercent(int percent) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_OVERLAY_SCALE_PERCENT, clampOverlayScalePercent(percent))
                .apply();
    }

    private void updateOverlayScaleText(int percent) {
        if (overlayScaleText != null) {
            overlayScaleText.setText("\u60ac\u6d6e\u7a97\u5927\u5c0f " + clampOverlayScalePercent(percent) + "%");
        }
        updateOverlayPreviewScale(percent);
    }

    private void updateOverlayPreviewScale(int percent) {
        if (overlayPreviewPanel == null || overlayPreviewStage == null) {
            return;
        }
        float scale = clampOverlayScalePercent(percent) / 100f;
        overlayPreviewPanel.setScaleX(scale);
        overlayPreviewPanel.setScaleY(scale);
        FrameLayout.LayoutParams panelLp = (FrameLayout.LayoutParams) overlayPreviewPanel.getLayoutParams();
        panelLp.gravity = Gravity.CENTER;
        overlayPreviewPanel.setLayoutParams(panelLp);

        LinearLayout.LayoutParams stageLp = (LinearLayout.LayoutParams) overlayPreviewStage.getLayoutParams();
        stageLp.height = Math.max(dp(210), Math.round(dp(260) * scale));
        overlayPreviewStage.setLayoutParams(stageLp);
    }

    private void notifyOverlayScaleChanged() {
        startOverlayService();
        Intent intent = new Intent(ACTION_OVERLAY_SCALE_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    static int getOverlayScalePercent(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        return clampOverlayScalePercent(prefs.getInt(KEY_OVERLAY_SCALE_PERCENT, DEFAULT_OVERLAY_SCALE_PERCENT));
    }

    static float getOverlayScale(android.content.Context context) {
        return getOverlayScalePercent(context) / 100f;
    }

    private static int clampOverlayScalePercent(int percent) {
        return Math.max(MIN_OVERLAY_SCALE_PERCENT, Math.min(MAX_OVERLAY_SCALE_PERCENT, percent));
    }

    static String getTargetPackage(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        String value = prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE);
        return value == null || value.length() == 0 ? DEFAULT_TARGET_PACKAGE : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppChoice {
        final String label;
        final String packageName;
        final boolean system;
        final boolean launchable;

        AppChoice(String label, String packageName, boolean system, boolean launchable) {
            this.label = label;
            this.packageName = packageName;
            this.system = system;
            this.launchable = launchable;
        }
    }
}
