package com.autonavi.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
    static final String KEY_MAIN_OVERLAY_ENABLED = "main_overlay_enabled";
    static final String KEY_CLUSTER_MIRROR_ENABLED = "cluster_mirror_enabled";
    static final String KEY_OVERLAY_X = "overlay_x";
    static final String KEY_OVERLAY_Y = "overlay_y";
    static final String KEY_CLUSTER_X = "cluster_x";
    static final String KEY_CLUSTER_Y = "cluster_y";
    static final String KEY_CLUSTER_SCALE_PERCENT = "cluster_scale_percent";
    static final String KEY_CLUSTER_DISPLAY_ID = "cluster_display_id";
    static final String KEY_SHOW_MODE = "show_mode";
    static final String KEY_SHOW_TURN = "show_turn";
    static final String KEY_SHOW_LANE = "show_lane";
    static final String KEY_SHOW_LIGHT = "show_light";
    static final String KEY_SHOW_ETA = "show_eta";
    static final String KEY_SHOW_ALERT = "show_alert";
    static final String KEY_SHOW_DETAIL = "show_detail";
    static final String KEY_TRANSPARENT_BACKGROUND = "transparent_background";
    static final String KEY_BACKGROUND_OPACITY_PERCENT = "background_opacity_percent";
    static final String KEY_TEXT_MODE = "text_mode";
    static final String KEY_OVERLAY_UI_STYLE = "overlay_ui_style";
    static final String ACTION_MAIN_OVERLAY_CHANGED = "com.autonavi.companion.MAIN_OVERLAY_CHANGED";
    static final String ACTION_OVERLAY_SCALE_CHANGED = "com.autonavi.companion.OVERLAY_SCALE_CHANGED";
    static final String ACTION_CLUSTER_MIRROR_CHANGED = "com.autonavi.companion.CLUSTER_MIRROR_CHANGED";
    static final String ACTION_OVERLAY_CONTENT_CHANGED = "com.autonavi.companion.OVERLAY_CONTENT_CHANGED";
    static final String ACTION_OVERLAY_STYLE_CHANGED = "com.autonavi.companion.OVERLAY_STYLE_CHANGED";
    static final String DEFAULT_TARGET_PACKAGE = "com.autonavi.amapClone";
    static final String UPDATE_CHANNEL_SERVER = "server";
    static final String UPDATE_CHANNEL_GITHUB = "github";
    static final String DEFAULT_UPDATE_CHANNEL = UPDATE_CHANNEL_SERVER;
    static final String SERVER_UPDATE_URL = "https://amap-companion.zuoqirun.top/update.json";
    static final String GITHUB_UPDATE_URL = "https://amap-companion.zuoqirun.top/update-github.json";
    static final String REPOSITORY_URL = "https://github.com/zuo-qirun/amap-companion";
    static final String LICENSE_URL = "https://github.com/zuo-qirun/amap-companion/blob/master/LICENSE";
    static final String DEFAULT_UPDATE_URL = SERVER_UPDATE_URL;
    static final String TEXT_MODE_LIGHT = "light";
    static final String TEXT_MODE_AUTO = "auto";
    static final String OVERLAY_UI_OLD = "old";
    static final String OVERLAY_UI_NEW = "new";
    static final int MIN_BACKGROUND_OPACITY_PERCENT = 15;
    static final int MAX_BACKGROUND_OPACITY_PERCENT = 90;
    static final int DEFAULT_BACKGROUND_OPACITY_PERCENT = 90;
    static final int MIN_OVERLAY_SCALE_PERCENT = 80;
    static final int MAX_OVERLAY_SCALE_PERCENT = 300;
    static final int DEFAULT_OVERLAY_SCALE_PERCENT = 200;
    private static final String TARGET_PACKAGE_PREFIX = "com.autonavi.";

    private TextView targetText;
    private TextView updateText;
    private TextView overlayScaleText;
    private TextView clusterScaleText;
    private TextView clusterDisplayText;
    private TextView overlayBackgroundOpacityText;
    private FrameLayout overlayPreviewStage;
    private LinearLayout overlayPreviewPanel;
    private Button overlayTextModeButton;
    private Button overlayUiStyleButton;
    private TextView previewModeText;
    private TextView previewTurnText;
    private LinearLayout previewLightRow;
    private LinearLayout previewLaneSection;
    private TextView previewEtaText;
    private TextView previewAlertText;
    private TextView previewDetailText;
    private TextView previewLaneTitleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        persistDefaultUpdateUrl();
        migrateOverlayStylePrefs();
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
        boolean wideLayout = isWideLayout();

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

        LinearLayout contentArea = new LinearLayout(this);
        contentArea.setOrientation(wideLayout ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(-1, -2);
        contentLp.setMargins(0, dp(14), 0, 0);
        root.addView(contentArea, contentLp);

        LinearLayout leftColumn = new LinearLayout(this);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(-1, -2);
        if (wideLayout) {
            leftLp = new LinearLayout.LayoutParams(0, -2, 1f);
            leftLp.setMargins(0, 0, dp(7), 0);
        }
        contentArea.addView(leftColumn, leftLp);

        LinearLayout rightColumn = new LinearLayout(this);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(-1, -2);
        if (wideLayout) {
            rightLp = new LinearLayout.LayoutParams(0, -2, 1f);
            rightLp.setMargins(dp(7), 0, 0, 0);
        } else {
            rightLp.setMargins(0, dp(14), 0, 0);
        }
        contentArea.addView(rightColumn, rightLp);

        LinearLayout actions = card(Color.WHITE);
        leftColumn.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        addActionButtons(actions, wideLayout);

        LinearLayout settings = card(Color.WHITE);
        rightColumn.addView(settings, new LinearLayout.LayoutParams(-1, -2));
        addOverlayScaleControls(settings);
        addClusterMirrorControls(settings);
        addOverlayContentControls(settings);
        addOpenSourceSection(wideLayout ? leftColumn : rightColumn, wideLayout);

        return scroll;
    }

    private void addActionButtons(LinearLayout parent, boolean wideLayout) {
        if (wideLayout) {
            addButtonPair(parent,
                    button("\u9009\u62e9\u76ee\u6807\u5e94\u7528", v -> chooseTargetApp(), 0xFF2563EB),
                    button("\u6388\u6743\u60ac\u6d6e\u7a97", v -> requestOverlayPermission(), 0xFF475569));
            addButtonPair(parent,
                    button("\u542f\u52a8\u60ac\u6d6e\u7a97", v -> enableMainOverlay(), 0xFF0F766E),
                    button("\u5173\u95ed\u60ac\u6d6e\u7a97", v -> stopOverlayService(), 0xFFB45309));
            addButtonPair(parent,
                    button(clusterMirrorButtonText(), v -> toggleClusterMirror((Button) v), 0xFF7C3AED),
                    button("\u6253\u5f00\u76ee\u6807\u5e94\u7528", v -> openTargetApp(), 0xFF111827));
            addButtonPair(parent,
                    button("\u9009\u62e9\u4e0b\u8f7d\u6e20\u9053", v -> chooseUpdateChannel(), 0xFF334155),
                    button("\u68c0\u67e5\u66f4\u65b0", v -> checkForUpdates(true), 0xFF059669));
            return;
        }
        parent.addView(button("\u9009\u62e9\u76ee\u6807\u5e94\u7528", v -> chooseTargetApp(), 0xFF2563EB));
        parent.addView(button("\u6388\u6743\u60ac\u6d6e\u7a97", v -> requestOverlayPermission(), 0xFF475569));
        parent.addView(button("\u542f\u52a8\u60ac\u6d6e\u7a97", v -> enableMainOverlay(), 0xFF0F766E));
        parent.addView(button("\u5173\u95ed\u60ac\u6d6e\u7a97", v -> stopOverlayService(), 0xFFB45309));
        parent.addView(button(clusterMirrorButtonText(), v -> toggleClusterMirror((Button) v), 0xFF7C3AED));
        parent.addView(button("\u6253\u5f00\u76ee\u6807\u5e94\u7528", v -> openTargetApp(), 0xFF111827));
        parent.addView(button("\u9009\u62e9\u4e0b\u8f7d\u6e20\u9053", v -> chooseUpdateChannel(), 0xFF334155));
        parent.addView(button("\u68c0\u67e5\u66f4\u65b0", v -> checkForUpdates(true), 0xFF059669));
    }

    private void addOpenSourceSection(LinearLayout root, boolean compactTopMargin) {
        LinearLayout section = card(Color.WHITE);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(-1, -2);
        sectionLp.setMargins(0, compactTopMargin ? dp(10) : dp(14), 0, 0);
        root.addView(section, sectionLp);

        TextView title = new TextView(this);
        title.setText("\u5f00\u6e90\u4fe1\u606f");
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        section.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView repo = new TextView(this);
        repo.setText("\u5f00\u6e90\u5730\u5740\n" + REPOSITORY_URL);
        repo.setTextSize(13f);
        repo.setTextColor(0xFF334155);
        repo.setLineSpacing(dp(2), 1.0f);
        repo.setTextIsSelectable(true);
        LinearLayout.LayoutParams repoLp = new LinearLayout.LayoutParams(-1, -2);
        repoLp.setMargins(0, dp(8), 0, 0);
        section.addView(repo, repoLp);

        TextView license = new TextView(this);
        license.setText("\u5f00\u6e90\u8bb8\u53ef\u8bc1\nGNU GPL v3.0\n\u672c\u9879\u76ee\u6309 GPL v3.0 \u5f00\u6e90\u53d1\u5e03\uff0c\u53ef\u4ee5\u4f7f\u7528\u3001\u4fee\u6539\u548c\u5206\u53d1\uff0c\u4f46\u5206\u53d1\u4fee\u6539\u7248\u65f6\u9700\u7ee7\u7eed\u4ee5\u76f8\u540c\u8bb8\u53ef\u8bc1\u5f00\u6e90\uff0c\u5e76\u9644\u4e0a\u539f\u59cb\u8bb8\u53ef\u8bc1\u6587\u672c\u3002");
        license.setTextSize(13f);
        license.setTextColor(0xFF334155);
        license.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams licenseLp = new LinearLayout.LayoutParams(-1, -2);
        licenseLp.setMargins(0, dp(10), 0, 0);
        section.addView(license, licenseLp);

        if (isWideLayout()) {
            addButtonPair(section,
                    button("\u6253\u5f00\u5f00\u6e90\u4ed3\u5e93", v -> openUrl(REPOSITORY_URL), 0xFF1D4ED8),
                    button("\u67e5\u770b\u8bb8\u53ef\u8bc1", v -> openUrl(LICENSE_URL), 0xFF475569));
        } else {
            section.addView(button("\u6253\u5f00\u5f00\u6e90\u4ed3\u5e93", v -> openUrl(REPOSITORY_URL), 0xFF1D4ED8));
            section.addView(button("\u67e5\u770b\u8bb8\u53ef\u8bc1", v -> openUrl(LICENSE_URL), 0xFF475569));
        }
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

    private void addClusterMirrorControls(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(12), dp(2), 0);

        TextView title = new TextView(this);
        title.setText("\u526f\u5c4f\u60ac\u6d6e\u7a97");
        title.setTextSize(14f);
        title.setTextColor(0xFF111827);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        clusterDisplayText = new TextView(this);
        clusterDisplayText.setTextSize(13f);
        clusterDisplayText.setTextColor(0xFF334155);
        LinearLayout.LayoutParams displayTextLp = new LinearLayout.LayoutParams(-1, -2);
        displayTextLp.setMargins(0, dp(8), 0, 0);
        box.addView(clusterDisplayText, displayTextLp);
        updateClusterDisplayText();

        box.addView(button("\u9009\u62e9\u6295\u5c4f\u5c4f\u5e55", v -> chooseClusterDisplay(), 0xFF334155));

        clusterScaleText = new TextView(this);
        clusterScaleText.setTextSize(13f);
        clusterScaleText.setTextColor(0xFF334155);
        LinearLayout.LayoutParams scaleTextLp = new LinearLayout.LayoutParams(-1, -2);
        scaleTextLp.setMargins(0, dp(8), 0, 0);
        box.addView(clusterScaleText, scaleTextLp);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(MAX_OVERLAY_SCALE_PERCENT - MIN_OVERLAY_SCALE_PERCENT);
        seekBar.setProgress(getClusterScalePercent(this) - MIN_OVERLAY_SCALE_PERCENT);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = MIN_OVERLAY_SCALE_PERCENT + progress;
                updateClusterScaleText(percent);
                if (fromUser) {
                    saveClusterScalePercent(percent);
                    notifyClusterMirrorChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                int percent = MIN_OVERLAY_SCALE_PERCENT + bar.getProgress();
                saveClusterScalePercent(percent);
                updateClusterScaleText(percent);
                notifyClusterMirrorChanged();
            }
        });
        box.addView(seekBar, new LinearLayout.LayoutParams(-1, -2));
        updateClusterScaleText(getClusterScalePercent(this));

        LinearLayout upRow = new LinearLayout(this);
        upRow.setGravity(Gravity.CENTER);
        upRow.addView(directionButton("\u4e0a", v -> moveClusterBy(0, -dp(16))));
        box.addView(upRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setGravity(Gravity.CENTER);
        middleRow.addView(directionButton("\u5de6", v -> moveClusterBy(-dp(16), 0)));
        middleRow.addView(directionButton("\u53f3", v -> moveClusterBy(dp(16), 0)));
        box.addView(middleRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout downRow = new LinearLayout(this);
        downRow.setGravity(Gravity.CENTER);
        downRow.addView(directionButton("\u4e0b", v -> moveClusterBy(0, dp(16))));
        box.addView(downRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        parent.addView(box, lp);
    }

    private void addOverlayContentControls(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(2), dp(12), dp(2), 0);

        TextView title = new TextView(this);
        title.setText("\u81ea\u5b9a\u4e49\u60ac\u6d6e\u7a97\u5185\u5bb9");
        title.setTextSize(14f);
        title.setTextColor(0xFF111827);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("\u4e3b\u60ac\u6d6e\u7a97\u548c\u526f\u5c4f\u955c\u50cf\u4f1a\u540c\u6b65\u4f7f\u7528\u8fd9\u7ec4\u663e\u793a\u8bbe\u7f6e");
        hint.setTextSize(12f);
        hint.setTextColor(0xFF64748B);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(6), 0, 0);
        box.addView(hint, hintLp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.setMargins(0, dp(6), 0, 0);
        box.addView(grid, gridLp);

        if (isWideLayout()) {
            addTogglePair(grid,
                    contentToggle("\u9876\u90e8\u72b6\u6001", KEY_SHOW_MODE),
                    contentToggle("\u8def\u7ebf\u6307\u5f15", KEY_SHOW_TURN));
            addTogglePair(grid,
                    contentToggle("\u7ea2\u7eff\u706f\u5012\u8ba1\u65f6", KEY_SHOW_LIGHT),
                    contentToggle("\u8f66\u9053\u4fe1\u606f", KEY_SHOW_LANE));
            addTogglePair(grid,
                    contentToggle("\u5269\u4f59\u91cc\u7a0b\u4e0e\u76ee\u7684\u5730", KEY_SHOW_ETA),
                    contentToggle("\u9650\u901f/\u7535\u5b50\u773c/\u7ea2\u7eff\u706f\u4e2a\u6570", KEY_SHOW_ALERT));
            addTogglePair(grid,
                    contentToggle("\u8be6\u7ec6\u72b6\u6001", KEY_SHOW_DETAIL),
                    null);
        } else {
            grid.addView(contentToggle("\u9876\u90e8\u72b6\u6001", KEY_SHOW_MODE));
            grid.addView(contentToggle("\u8def\u7ebf\u6307\u5f15", KEY_SHOW_TURN));
            grid.addView(contentToggle("\u7ea2\u7eff\u706f\u5012\u8ba1\u65f6", KEY_SHOW_LIGHT));
            grid.addView(contentToggle("\u8f66\u9053\u4fe1\u606f", KEY_SHOW_LANE));
            grid.addView(contentToggle("\u5269\u4f59\u91cc\u7a0b\u4e0e\u76ee\u7684\u5730", KEY_SHOW_ETA));
            grid.addView(contentToggle("\u9650\u901f/\u7535\u5b50\u773c/\u7ea2\u7eff\u706f\u4e2a\u6570", KEY_SHOW_ALERT));
            grid.addView(contentToggle("\u8be6\u7ec6\u72b6\u6001", KEY_SHOW_DETAIL));
        }
        addBackgroundOpacityControls(box);
        overlayUiStyleButton = button(overlayUiStyleButtonText(), v -> chooseOverlayUiStyle(), 0xFF334155);
        LinearLayout.LayoutParams uiStyleLp = new LinearLayout.LayoutParams(-1, dp(42));
        uiStyleLp.setMargins(0, dp(8), 0, 0);
        overlayUiStyleButton.setLayoutParams(uiStyleLp);
        box.addView(overlayUiStyleButton);
        overlayTextModeButton = button(textModeButtonText(), v -> chooseTextMode(), 0xFF475569);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, dp(42));
        buttonLp.setMargins(0, dp(8), 0, 0);
        overlayTextModeButton.setLayoutParams(buttonLp);
        box.addView(overlayTextModeButton);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        parent.addView(box, lp);
        updateOverlayPreviewContentVisibility();
        applyOverlayPreviewStyle();
    }

    private void addBackgroundOpacityControls(LinearLayout parent) {
        overlayBackgroundOpacityText = new TextView(this);
        overlayBackgroundOpacityText.setTextSize(13f);
        overlayBackgroundOpacityText.setTextColor(0xFF334155);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(-1, -2);
        textLp.setMargins(0, dp(8), 0, 0);
        parent.addView(overlayBackgroundOpacityText, textLp);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(MAX_BACKGROUND_OPACITY_PERCENT - MIN_BACKGROUND_OPACITY_PERCENT);
        seekBar.setProgress(getBackgroundOpacityPercent(this) - MIN_BACKGROUND_OPACITY_PERCENT);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = MIN_BACKGROUND_OPACITY_PERCENT + progress;
                updateBackgroundOpacityText(percent);
                if (fromUser) {
                    saveBackgroundOpacityPercent(percent);
                    applyOverlayPreviewStyle();
                    notifyOverlayStyleChanged();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                int percent = MIN_BACKGROUND_OPACITY_PERCENT + bar.getProgress();
                saveBackgroundOpacityPercent(percent);
                updateBackgroundOpacityText(percent);
                applyOverlayPreviewStyle();
                notifyOverlayStyleChanged();
            }
        });
        parent.addView(seekBar, new LinearLayout.LayoutParams(-1, -2));
        updateBackgroundOpacityText(getBackgroundOpacityPercent(this));
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
        panel.setBackground(createPreviewPanelBackground());

        previewModeText = new TextView(this);
        previewModeText.setText("\u5bfc\u822a \u00b7 \u5357\u56db\u73af\u4e1c\u8def\u8f85\u8def \u00b7 39 km/h");
        previewModeText.setTextSize(6.5f);
        previewModeText.setTextColor(0xFFE8EAED);
        previewModeText.setSingleLine(true);
        panel.addView(previewModeText, new LinearLayout.LayoutParams(-2, -2));

        previewTurnText = new TextView(this);
        previewTurnText.setText("\u2190  669\u7c73\n\u8fdb\u5165 \u6986\u4e61\u8def\u8f85\u8def");
        previewTurnText.setTextSize(15f);
        previewTurnText.setTypeface(Typeface.DEFAULT_BOLD);
        previewTurnText.setGravity(Gravity.CENTER);
        previewTurnText.setTextColor(Color.WHITE);
        previewTurnText.setPadding(dp(12), dp(4), dp(12), dp(5));
        GradientDrawable turnBg = new GradientDrawable();
        turnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        turnBg.setColors(new int[]{0xFF1D4ED8, 0xFF0891B2});
        turnBg.setCornerRadius(dp(5));
        previewTurnText.setBackground(turnBg);
        LinearLayout.LayoutParams turnLp = new LinearLayout.LayoutParams(-2, -2);
        turnLp.setMargins(0, dp(3), 0, dp(3));
        panel.addView(previewTurnText, turnLp);

        previewLightRow = new LinearLayout(this);
        previewLightRow.setOrientation(LinearLayout.HORIZONTAL);
        previewLightRow.setGravity(Gravity.CENTER);
        previewLightRow.addView(previewLight("\u2190 51s", 0xFFC62828));
        previewLightRow.addView(previewLight("\u2191 18s", 0xFFC62828));
        panel.addView(previewLightRow, new LinearLayout.LayoutParams(-2, -2));

        previewLaneSection = new LinearLayout(this);
        previewLaneSection.setOrientation(LinearLayout.VERTICAL);
        previewLaneSection.setGravity(Gravity.CENTER_HORIZONTAL);
        previewLaneSection.setPadding(dp(4), dp(3), dp(4), dp(4));
        GradientDrawable laneBg = new GradientDrawable();
        laneBg.setColor(0xCC0F172A);
        laneBg.setCornerRadius(dp(5));
        laneBg.setStroke(dp(1), 0x1FFFFFFF);
        previewLaneSection.setBackground(laneBg);

        previewLaneTitleText = new TextView(this);
        previewLaneTitleText.setText("\u8f66\u9053\u4fe1\u606f");
        previewLaneTitleText.setTextSize(5.5f);
        previewLaneTitleText.setTextColor(0xFFBAE6FD);
        previewLaneTitleText.setTypeface(Typeface.DEFAULT_BOLD);
        previewLaneSection.addView(previewLaneTitleText, new LinearLayout.LayoutParams(-2, -2));

        LaneBarView laneBar = new LaneBarView(this);
        laneBar.setFrameScaleMultiplier(1f);
        laneBar.setScaleMultiplier(1.5f);
        laneBar.setLaneData(new int[]{15, 31, 18}, new boolean[]{true, false, true});
        previewLaneSection.addView(laneBar, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams laneLp = new LinearLayout.LayoutParams(-2, -2);
        laneLp.setMargins(0, dp(3), 0, dp(2));
        panel.addView(previewLaneSection, laneLp);

        previewEtaText = new TextView(this);
        previewEtaText.setText("5.3\u516c\u91cc \u00b7 10\u5206\u949f\n\u9884\u8ba105:42\u5230\u8fbe\n\u76ee\u7684\u5730 \u5c0f\u7ea2\u95e8\u4e61\u515a\u7fa4\u670d\u52a1\u4e2d\u5fc3");
        previewEtaText.setTextSize(7.5f);
        previewEtaText.setTextColor(0xFFE8EAED);
        previewEtaText.setGravity(Gravity.CENTER);
        panel.addView(previewEtaText, new LinearLayout.LayoutParams(-2, -2));

        previewAlertText = new TextView(this);
        previewAlertText.setText("\u9650\u901f 50  \u00b7  \u7ea2\u7eff\u706f 2\u4e2a");
        previewAlertText.setTextSize(6.5f);
        previewAlertText.setTextColor(0xFFFFF7ED);
        previewAlertText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alertLp = new LinearLayout.LayoutParams(-2, -2);
        alertLp.setMargins(0, dp(3), 0, 0);
        panel.addView(previewAlertText, alertLp);

        previewDetailText = new TextView(this);
        previewDetailText.setText("\u8f66\u5934 90\u00b0\n\u9053\u8def \u4e3b\u8981\u9053\u8def");
        previewDetailText.setTextSize(5.8f);
        previewDetailText.setTextColor(0xFFC7D2FE);
        previewDetailText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-2, -2);
        detailLp.setMargins(0, dp(2), 0, 0);
        panel.addView(previewDetailText, detailLp);
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

    private Button directionButton(String text, android.view.View.OnClickListener listener) {
        Button b = button(text, listener, 0xFF475569);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(86), dp(42));
        lp.setMargins(dp(5), dp(6), dp(5), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void addButtonPair(LinearLayout parent, Button left, Button right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, dp(9), 0, 0);
        row.setLayoutParams(rowLp);

        row.addView(wideButton(left, 0, dp(5)));
        if (right != null) {
            row.addView(wideButton(right, dp(5), 0));
        } else {
            android.view.View spacer = new android.view.View(this);
            LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 0, 1f);
            spacerLp.setMargins(dp(5), 0, 0, 0);
            row.addView(spacer, spacerLp);
        }
        parent.addView(row);
    }

    private Button wideButton(Button button, int leftMargin, int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(leftMargin, 0, rightMargin, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private void addTogglePair(LinearLayout parent, CheckBox left, CheckBox right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, dp(2), 0, 0);
        row.setLayoutParams(rowLp);

        row.addView(wideToggle(left, 0, dp(8)));
        if (right != null) {
            row.addView(wideToggle(right, dp(8), 0));
        } else {
            android.view.View spacer = new android.view.View(this);
            LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 0, 1f);
            spacerLp.setMargins(dp(8), 0, 0, 0);
            row.addView(spacer, spacerLp);
        }
        parent.addView(row);
    }

    private CheckBox wideToggle(CheckBox checkBox, int leftMargin, int rightMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(leftMargin, 0, rightMargin, 0);
        checkBox.setLayoutParams(lp);
        return checkBox;
    }

    private boolean isWideLayout() {
        return getResources().getDisplayMetrics().widthPixels >= getResources().getDisplayMetrics().heightPixels;
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
            try {
                Context.class.getMethod("startForegroundService", Intent.class).invoke(this, intent);
            } catch (Throwable ignored) {
                startService(intent);
            }
        } else {
            startService(intent);
        }
    }

    private void stopOverlayService() {
        saveMainOverlayEnabled(false);
        notifyMainOverlayChanged();
        stopServiceIfNoVisuals();
    }

    private void enableMainOverlay() {
        saveMainOverlayEnabled(true);
        startOverlayService();
        notifyMainOverlayChanged();
    }

    private String clusterMirrorButtonText() {
        return isClusterMirrorEnabled(this) ? "\u5173\u95ed\u4eea\u8868\u76d8\u955c\u50cf" : "\u5f00\u542f\u4eea\u8868\u76d8\u955c\u50cf";
    }

    private void toggleClusterMirror(Button button) {
        boolean enabled = !isClusterMirrorEnabled(this);
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CLUSTER_MIRROR_ENABLED, enabled)
                .apply();
        button.setText(clusterMirrorButtonText());
        startOverlayService();
        notifyClusterMirrorChanged();
        stopServiceIfNoVisuals();
        Toast.makeText(this,
                enabled ? "\u5df2\u5f00\u542f\u4eea\u8868\u76d8\u955c\u50cf" : "\u5df2\u5173\u95ed\u4eea\u8868\u76d8\u955c\u50cf",
                Toast.LENGTH_SHORT).show();
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

    private void chooseClusterDisplay() {
        ArrayList<DisplayChoice> choices = getClusterDisplayChoices();
        String[] labels = new String[choices.size() + 1];
        labels[0] = "\u81ea\u52a8\u9009\u62e9\n\u4f18\u5148\u4f7f\u7528\u7cfb\u7edf\u8ba4\u5b9a\u7684\u526f\u5c4f";
        for (int i = 0; i < choices.size(); i++) {
            DisplayChoice choice = choices.get(i);
            labels[i + 1] = choice.label + "\nID " + choice.displayId;
        }
        int currentId = getClusterDisplayId(this);
        int checked = 0;
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).displayId == currentId) {
                checked = i + 1;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("\u9009\u62e9\u6295\u5c4f\u5c4f\u5e55")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveClusterDisplayId(which == 0 ? -1 : choices.get(which - 1).displayId);
                    updateClusterDisplayText();
                    startOverlayService();
                    notifyClusterMirrorChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "\u65e0\u6cd5\u6253\u5f00\u94fe\u63a5", Toast.LENGTH_SHORT).show();
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

    private CheckBox contentToggle(String text, String key) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(text);
        checkBox.setChecked(isOverlayContentEnabled(this, key));
        checkBox.setTextSize(14f);
        checkBox.setTextColor(0xFF0F172A);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF2563EB));
        }
        checkBox.setPadding(0, dp(2), 0, dp(2));
        checkBox.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            saveOverlayContentEnabled(key, isChecked);
            updateOverlayPreviewContentVisibility();
            notifyOverlayContentChanged();
        });
        return checkBox;
    }

    private void saveOverlayContentEnabled(String key, boolean enabled) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(key, enabled)
                .apply();
    }

    private void updateOverlayPreviewContentVisibility() {
        setPreviewVisibility(previewModeText, isOverlayContentEnabled(this, KEY_SHOW_MODE));
        setPreviewVisibility(previewTurnText, isOverlayContentEnabled(this, KEY_SHOW_TURN));
        setPreviewVisibility(previewLightRow, isOverlayContentEnabled(this, KEY_SHOW_LIGHT));
        setPreviewVisibility(previewLaneSection, isOverlayContentEnabled(this, KEY_SHOW_LANE));
        setPreviewVisibility(previewEtaText, isOverlayContentEnabled(this, KEY_SHOW_ETA));
        setPreviewVisibility(previewAlertText, isOverlayContentEnabled(this, KEY_SHOW_ALERT));
        setPreviewVisibility(previewDetailText, isOverlayContentEnabled(this, KEY_SHOW_DETAIL));
    }

    private void applyOverlayPreviewStyle() {
        applyOverlayPreviewPanelStyle();
        applyOverlayPreviewTextStyle();
        if (overlayUiStyleButton != null) {
            overlayUiStyleButton.setText(overlayUiStyleButtonText());
        }
        if (overlayTextModeButton != null) {
            overlayTextModeButton.setText(textModeButtonText());
        }
        updateBackgroundOpacityText(getBackgroundOpacityPercent(this));
    }

    private void applyOverlayPreviewPanelStyle() {
        if (overlayPreviewPanel != null) {
            overlayPreviewPanel.setBackground(createPreviewPanelBackground());
        }
    }

    private void applyOverlayPreviewTextStyle() {
        int primary = previewPrimaryTextColor();
        int alert = previewAlertTextColor();
        int detail = previewDetailTextColor();
        if (previewModeText != null) {
            previewModeText.setTextColor(primary);
        }
        if (previewEtaText != null) {
            previewEtaText.setTextColor(primary);
        }
        if (previewAlertText != null) {
            previewAlertText.setTextColor(alert);
        }
        if (previewDetailText != null) {
            previewDetailText.setTextColor(detail);
        }
    }

    private GradientDrawable createPreviewPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(7));
        int opacity = getBackgroundOpacityPercent(this);
        bg.setColor(withAlpha(0xFF111827, opacity));
        bg.setStroke(dp(1), withAlpha(0xFFFFFFFF, Math.max(8, Math.round(opacity * 0.18f))));
        return bg;
    }

    private String textModeButtonText() {
        return isAutoTextMode(this)
                ? "\u6587\u5b57\u6a21\u5f0f\uff1a\u81ea\u52a8\uff08\u6839\u636e\u900f\u660e\u5ea6\u81ea\u52a8\u5207\u6362\uff09"
                : "\u6587\u5b57\u6a21\u5f0f\uff1a\u6d45\u8272";
    }

    private String overlayUiStyleButtonText() {
        return isNewOverlayUiEnabled(this)
                ? "\u60ac\u6d6e\u7a97\u6837\u5f0f\uff1a\u65b0 UI\uff08\u6d4b\u8bd5\u4e2d\uff09"
                : "\u60ac\u6d6e\u7a97\u6837\u5f0f\uff1a\u65e7 UI";
    }

    private void chooseOverlayUiStyle() {
        String[] labels = {
                "\u65e7 UI\uff08\u9ed8\u8ba4\uff09",
                "\u65b0 UI\uff08\u5361\u7247\u6837\u5f0f\uff0c\u6d4b\u8bd5\u4e2d\uff09"
        };
        int checked = isNewOverlayUiEnabled(this) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("\u9009\u62e9\u60ac\u6d6e\u7a97\u6837\u5f0f")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveOverlayUiStyle(which == 1 ? OVERLAY_UI_NEW : OVERLAY_UI_OLD);
                    applyOverlayPreviewStyle();
                    notifyOverlayStyleChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private void chooseTextMode() {
        String[] labels = {
                "\u81ea\u52a8\u6a21\u5f0f\uff08\u6839\u636e\u80cc\u666f\u900f\u660e\u5ea6\u81ea\u52a8\u66f4\u6539\u6587\u5b57\u989c\u8272\uff09",
                "\u6d45\u8272\u6a21\u5f0f\uff08\u59cb\u7ec8\u4f7f\u7528\u6d45\u8272\u6587\u5b57\uff09"
        };
        int checked = isAutoTextMode(this) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("\u9009\u62e9\u6587\u5b57\u6a21\u5f0f")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveOverlayTextMode(which == 0 ? TEXT_MODE_AUTO : TEXT_MODE_LIGHT);
                    applyOverlayPreviewStyle();
                    notifyOverlayStyleChanged();
                    dialog.dismiss();
                })
                .setNegativeButton("\u53d6\u6d88", null)
                .show();
    }

    private void saveOverlayTextMode(String mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_TEXT_MODE, TEXT_MODE_AUTO.equals(mode) ? TEXT_MODE_AUTO : TEXT_MODE_LIGHT)
                .apply();
    }

    private void saveOverlayUiStyle(String style) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_OVERLAY_UI_STYLE, OVERLAY_UI_NEW.equals(style) ? OVERLAY_UI_NEW : OVERLAY_UI_OLD)
                .apply();
    }

    private int previewPrimaryTextColor() {
        return usesDarkTextPalette(this) ? 0xFF0F172A : 0xFFE8EAED;
    }

    private int previewAlertTextColor() {
        return usesDarkTextPalette(this) ? 0xFF7C2D12 : 0xFFFFF7ED;
    }

    private int previewDetailTextColor() {
        return usesDarkTextPalette(this) ? 0xFF1E3A8A : 0xFFC7D2FE;
    }

    private void updateBackgroundOpacityText(int percent) {
        if (overlayBackgroundOpacityText != null) {
            overlayBackgroundOpacityText.setText("\u4e3b\u80cc\u666f\u900f\u660e\u5ea6 " + clampBackgroundOpacityPercent(percent) + "%");
        }
    }

    private void saveBackgroundOpacityPercent(int percent) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_BACKGROUND_OPACITY_PERCENT, clampBackgroundOpacityPercent(percent))
                .apply();
    }

    private void migrateOverlayStylePrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.contains(KEY_BACKGROUND_OPACITY_PERCENT)) {
            return;
        }
        int opacity = prefs.getBoolean(KEY_TRANSPARENT_BACKGROUND, false)
                ? MIN_BACKGROUND_OPACITY_PERCENT
                : DEFAULT_BACKGROUND_OPACITY_PERCENT;
        prefs.edit().putInt(KEY_BACKGROUND_OPACITY_PERCENT, opacity).apply();
    }

    private void setPreviewVisibility(android.view.View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void notifyOverlayScaleChanged() {
        startOverlayService();
        Intent intent = new Intent(ACTION_OVERLAY_SCALE_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void saveMainOverlayEnabled(boolean enabled) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MAIN_OVERLAY_ENABLED, enabled)
                .apply();
    }

    private void notifyMainOverlayChanged() {
        Intent intent = new Intent(ACTION_MAIN_OVERLAY_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifyClusterMirrorChanged() {
        Intent intent = new Intent(ACTION_CLUSTER_MIRROR_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifyOverlayContentChanged() {
        Intent intent = new Intent(ACTION_OVERLAY_CONTENT_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifyOverlayStyleChanged() {
        Intent intent = new Intent(ACTION_OVERLAY_STYLE_CHANGED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void stopServiceIfNoVisuals() {
        if (!isMainOverlayEnabled(this) && !isClusterMirrorEnabled(this)) {
            stopService(new Intent(this, OverlayService.class));
        }
    }

    private void saveClusterScalePercent(int percent) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_CLUSTER_SCALE_PERCENT, clampOverlayScalePercent(percent))
                .apply();
    }

    private void updateClusterScaleText(int percent) {
        if (clusterScaleText != null) {
            clusterScaleText.setText("\u526f\u5c4f\u5927\u5c0f " + clampOverlayScalePercent(percent) + "%");
        }
    }

    private void updateClusterDisplayText() {
        if (clusterDisplayText == null) {
            return;
        }
        int selectedId = getClusterDisplayId(this);
        if (selectedId < 0) {
            clusterDisplayText.setText("\u6295\u5c4f\u5c4f\u5e55 \u00b7 \u81ea\u52a8\u9009\u62e9");
            return;
        }
        DisplayChoice selected = null;
        ArrayList<DisplayChoice> choices = getClusterDisplayChoices();
        for (DisplayChoice choice : choices) {
            if (choice.displayId == selectedId) {
                selected = choice;
                break;
            }
        }
        if (selected != null) {
            clusterDisplayText.setText("\u6295\u5c4f\u5c4f\u5e55 \u00b7 " + selected.label + " (ID " + selected.displayId + ")");
        } else {
            clusterDisplayText.setText("\u6295\u5c4f\u5c4f\u5e55 \u00b7 \u5df2\u6307\u5b9a ID " + selectedId + "\uff08\u5f53\u524d\u672a\u68c0\u6d4b\u5230\uff09");
        }
    }

    private void saveClusterDisplayId(int displayId) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_CLUSTER_DISPLAY_ID, displayId)
                .apply();
    }

    private ArrayList<DisplayChoice> getClusterDisplayChoices() {
        ArrayList<DisplayChoice> choices = new ArrayList<>();
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager == null) {
            return choices;
        }
        Display[] displays = manager.getDisplays();
        for (Display display : displays) {
            if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                continue;
            }
            String name = display.getName();
            if (TextUtils.isEmpty(name)) {
                name = "\u526f\u5c4f";
            }
            choices.add(new DisplayChoice(display.getDisplayId(), name));
        }
        Collections.sort(choices, Comparator.comparingInt(choice -> choice.displayId));
        return choices;
    }

    private void moveClusterBy(int dx, int dy) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int x = Math.max(0, prefs.getInt(KEY_CLUSTER_X, dp(24)) + dx);
        int y = Math.max(0, prefs.getInt(KEY_CLUSTER_Y, dp(120)) + dy);
        prefs.edit()
                .putInt(KEY_CLUSTER_X, x)
                .putInt(KEY_CLUSTER_Y, y)
                .apply();
        startOverlayService();
        notifyClusterMirrorChanged();
    }

    static int getOverlayScalePercent(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        return clampOverlayScalePercent(prefs.getInt(KEY_OVERLAY_SCALE_PERCENT, DEFAULT_OVERLAY_SCALE_PERCENT));
    }

    static float getOverlayScale(android.content.Context context) {
        return getOverlayScalePercent(context) / 100f;
    }

    static boolean isMainOverlayEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_MAIN_OVERLAY_ENABLED, true);
    }

    static boolean isClusterMirrorEnabled(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_CLUSTER_MIRROR_ENABLED, false);
    }

    static int getClusterScalePercent(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        return clampOverlayScalePercent(prefs.getInt(KEY_CLUSTER_SCALE_PERCENT, DEFAULT_OVERLAY_SCALE_PERCENT));
    }

    static float getClusterScale(android.content.Context context) {
        return getClusterScalePercent(context) / 100f;
    }

    static int getClusterDisplayId(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_CLUSTER_DISPLAY_ID, -1);
    }

    static int getClusterX(android.content.Context context, int defaultValue) {
        return Math.max(0, context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_CLUSTER_X, defaultValue));
    }

    static int getClusterY(android.content.Context context, int defaultValue) {
        return Math.max(0, context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_CLUSTER_Y, defaultValue));
    }

    static boolean isModeVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_MODE);
    }

    static boolean isTurnVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_TURN);
    }

    static boolean isLaneVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_LANE);
    }

    static boolean isLightVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_LIGHT);
    }

    static boolean isEtaVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_ETA);
    }

    static boolean isAlertVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_ALERT);
    }

    static boolean isDetailVisible(android.content.Context context) {
        return isOverlayContentEnabled(context, KEY_SHOW_DETAIL);
    }

    static boolean isTransparentBackground(android.content.Context context) {
        return getBackgroundOpacityPercent(context) <= MIN_BACKGROUND_OPACITY_PERCENT;
    }

    static boolean isAutoTextMode(android.content.Context context) {
        return TEXT_MODE_AUTO.equals(getOverlayTextMode(context));
    }

    static boolean isNewOverlayUiEnabled(android.content.Context context) {
        return OVERLAY_UI_NEW.equals(getOverlayUiStyle(context));
    }

    static boolean usesDarkTextPalette(android.content.Context context) {
        return getBackgroundOpacityPercent(context) <= 55 && isAutoTextMode(context);
    }

    static int getBackgroundOpacityPercent(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.contains(KEY_BACKGROUND_OPACITY_PERCENT)) {
            return clampBackgroundOpacityPercent(
                    prefs.getInt(KEY_BACKGROUND_OPACITY_PERCENT, DEFAULT_BACKGROUND_OPACITY_PERCENT));
        }
        return prefs.getBoolean(KEY_TRANSPARENT_BACKGROUND, false)
                ? MIN_BACKGROUND_OPACITY_PERCENT
                : DEFAULT_BACKGROUND_OPACITY_PERCENT;
    }

    static String getOverlayTextMode(android.content.Context context) {
        String mode = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_TEXT_MODE, TEXT_MODE_AUTO);
        return TEXT_MODE_LIGHT.equals(mode) ? TEXT_MODE_LIGHT : TEXT_MODE_AUTO;
    }

    static String getOverlayUiStyle(android.content.Context context) {
        String style = context.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_OVERLAY_UI_STYLE, OVERLAY_UI_OLD);
        return OVERLAY_UI_NEW.equals(style) ? OVERLAY_UI_NEW : OVERLAY_UI_OLD;
    }

    static boolean isOverlayContentEnabled(android.content.Context context, String key) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(key, true);
    }

    private static int clampOverlayScalePercent(int percent) {
        return Math.max(MIN_OVERLAY_SCALE_PERCENT, Math.min(MAX_OVERLAY_SCALE_PERCENT, percent));
    }

    private static int clampBackgroundOpacityPercent(int percent) {
        return Math.max(MIN_BACKGROUND_OPACITY_PERCENT, Math.min(MAX_BACKGROUND_OPACITY_PERCENT, percent));
    }

    private static int withAlpha(int color, int alphaPercent) {
        int alpha = Math.max(0, Math.min(255, Math.round(clampBackgroundOpacityPercent(alphaPercent) * 255f / 100f)));
        return (alpha << 24) | (color & 0x00FFFFFF);
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

    private static final class DisplayChoice {
        final int displayId;
        final String label;

        DisplayChoice(int displayId, String label) {
            this.displayId = displayId;
            this.label = label;
        }
    }
}
