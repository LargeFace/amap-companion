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
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {
    static final String PREFS = "amap_companion";
    static final String KEY_TARGET_PACKAGE = "target_package";
    static final String DEFAULT_TARGET_PACKAGE = "com.autonavi.amapClone";
    private static final String TARGET_PACKAGE_PREFIX = "com.autonavi.";

    private TextView targetText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        startOverlayService();
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

        LinearLayout controls = card(Color.WHITE);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(-1, -2);
        controlsLp.setMargins(0, dp(14), 0, 0);
        root.addView(controls, controlsLp);

        controls.addView(button("\u9009\u62e9\u76ee\u6807\u5e94\u7528", v -> chooseTargetApp(), 0xFF2563EB));
        controls.addView(button("\u6388\u6743\u60ac\u6d6e\u7a97", v -> requestOverlayPermission(), 0xFF475569));
        controls.addView(button("\u542f\u52a8\u60ac\u6d6e\u7a97", v -> startOverlayService(), 0xFF0F766E));
        controls.addView(button("\u5173\u95ed\u60ac\u6d6e\u7a97", v -> stopOverlayService(), 0xFFB45309));
        controls.addView(button("\u6253\u5f00\u76ee\u6807\u5e94\u7528", v -> openTargetApp(), 0xFF111827));

        return scroll;
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

    private void updateTargetText() {
        if (targetText != null) {
            targetText.setText("\u76ee\u6807\u5e94\u7528\n" + getTargetPackage(this));
        }
    }

    private void saveTargetPackage(String packageName) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_TARGET_PACKAGE, packageName)
                .apply();
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
