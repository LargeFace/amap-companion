package com.autonavi.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Presentation;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OverlayService extends Service {
    private static final String TAG = "AmapCompanion";
    private static final String CHANNEL_ID = "amap_companion";
    private static final String ACTION_SEND = "AUTONAVI_STANDARD_BROADCAST_SEND";
    private static final String ACTION_RECV = "AUTONAVI_STANDARD_BROADCAST_RECV";
    private static final int KEY_TRAFFIC_LIGHT_COUNTDOWN = 60073;
    private static final long ALERT_TTL_MS = 5000L;
    private static final long LIGHT_TTL_MS = 4500L;
    private static final Pattern CAMERA_LIGHT_PATTERN = Pattern.compile(
            "CameraLightInfo\\{([^}]*)\\}");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout panel;
    private TextView modeText;
    private TextView turnText;
    private LinearLayout laneSection;
    private LaneBarView laneBar;
    private LinearLayout lightRow;
    private TextView etaText;
    private TextView alertText;
    private TextView detailText;
    private Presentation clusterPresentation;
    private FrameLayout clusterStage;
    private LinearLayout clusterPanel;
    private TextView clusterModeText;
    private TextView clusterTurnText;
    private LinearLayout clusterLaneSection;
    private LaneBarView clusterLaneBar;
    private LinearLayout clusterLightRow;
    private TextView clusterEtaText;
    private TextView clusterAlertText;
    private TextView clusterDetailText;
    private Display clusterDisplay;
    private boolean clusterMirrorEnabled;
    private int clusterMirrorRetryCount;
    private final HashMap<Integer, LightState> trafficLights = new HashMap<>();
    private boolean inCruiseMode;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging;
    private String lastDetailedMode;
    private long alertUpdatedAt;
    private int navigationTurnDir = -1;
    private float overlayScale = 2f;
    private float clusterScale = 2f;

    private final Runnable lanePoll = new Runnable() {
        @Override
        public void run() {
            requestLaneInfo();
            requestTrafficLightInfo();
            mainHandler.postDelayed(this, 6000L);
        }
    };

    private final Runnable alertClear = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - alertUpdatedAt >= ALERT_TTL_MS) {
                clearAlertDetails();
            }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleBroadcast(intent);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, buildNotification());
        registerAmapReceivers();
        ensureOverlay();
        ensureClusterMirror();
        stopSelfIfNoVisuals();
        requestLaneInfo();
        mainHandler.postDelayed(lanePoll, 6000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureOverlay();
        ensureClusterMirror();
        stopSelfIfNoVisuals();
        requestLaneInfo();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(lanePoll);
        mainHandler.removeCallbacks(alertClear);
        dismissClusterMirror();
        try {
            unregisterReceiver(receiver);
        } catch (Throwable ignored) {
        }
        if (windowManager != null && panel != null && panel.getParent() != null) {
            try {
                windowManager.removeView(panel);
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerAmapReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SEND);
        filter.addAction("AUTO_GUIDE_INFO_FOR_INTERNAL_WIDGET");
        filter.addAction("AUTO_STATUS_FOR_INTERNAL_WIDGET");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_ROAD_NAME_INFO");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_SILENCE_ROADNAME_INFO");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_GPS_INFO");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_CAR_DIRECTION");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_CAMERA_INFO");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_TRAFFIC_LIGHT_INFO");
        filter.addAction("com.autonavi.amapauto.AUTO_WIDGET_UPDATE_CRUISE_TRAFFIC_LIGHT_INFO");
        filter.addAction(MainActivity.ACTION_MAIN_OVERLAY_CHANGED);
        filter.addAction(MainActivity.ACTION_OVERLAY_SCALE_CHANGED);
        filter.addAction(MainActivity.ACTION_CLUSTER_MIRROR_CHANGED);
        filter.addAction(MainActivity.ACTION_OVERLAY_CONTENT_CHANGED);
        try {
            registerReceiver(receiver, filter);
        } catch (Throwable t) {
            Log.e(TAG, "register receiver failed", t);
        }
    }

    private void ensureOverlay() {
        if (panel != null) {
            syncMainOverlayAttachment();
            return;
        }

        overlayScale = MainActivity.getOverlayScale(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEA111827);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x22FFFFFF);
        panel.setBackground(bg);

        modeText = new TextView(this);
        modeText.setTextColor(0xFFE8EAED);
        modeText.setTextSize(sp(13f));
        modeText.setSingleLine(true);
        modeText.setGravity(Gravity.CENTER);
        modeText.setText("\u5f85\u63a5\u6536\u5bfc\u822a/\u5de1\u822a\u4fe1\u606f");
        panel.addView(modeText, new LinearLayout.LayoutParams(-2, -2));

        turnText = new TextView(this);
        turnText.setTextColor(Color.WHITE);
        turnText.setTextSize(sp(28f));
        turnText.setTypeface(Typeface.DEFAULT_BOLD);
        turnText.setSingleLine(false);
        turnText.setMaxLines(2);
        turnText.setEllipsize(TextUtils.TruncateAt.END);
        turnText.setGravity(Gravity.CENTER);
        turnText.setPadding(dp(18), dp(6), dp(18), dp(7));
        GradientDrawable turnBg = new GradientDrawable();
        turnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        turnBg.setColors(new int[]{0xFF1D4ED8, 0xFF0891B2});
        turnBg.setCornerRadius(dp(10));
        turnText.setBackground(turnBg);
        turnText.setVisibility(View.GONE);
        turnText.setMinHeight(dp(62));
        LinearLayout.LayoutParams turnLp = new LinearLayout.LayoutParams(-2, -2);
        turnLp.setMargins(0, dp(6), 0, dp(5));
        panel.addView(turnText, turnLp);

        laneSection = new LinearLayout(this);
        laneSection.setOrientation(LinearLayout.VERTICAL);
        laneSection.setGravity(Gravity.CENTER_HORIZONTAL);
        laneSection.setPadding(dp(8), dp(5), dp(8), dp(7));
        GradientDrawable laneBg = new GradientDrawable();
        laneBg.setColor(0xCC0F172A);
        laneBg.setCornerRadius(dp(10));
        laneBg.setStroke(dp(1), 0x1FFFFFFF);
        laneSection.setBackground(laneBg);
        laneSection.setVisibility(View.GONE);
        TextView laneTitle = new TextView(this);
        laneTitle.setText("\u8f66\u9053\u4fe1\u606f");
        laneTitle.setTextColor(0xFFBAE6FD);
        laneTitle.setTextSize(sp(11f));
        laneTitle.setTypeface(Typeface.DEFAULT_BOLD);
        laneTitle.setGravity(Gravity.CENTER);
        laneSection.addView(laneTitle, new LinearLayout.LayoutParams(-2, -2));
        laneBar = new LaneBarView(this);
        laneBar.setFrameScaleMultiplier(overlayScale);
        laneBar.setScaleMultiplier(1.5f);
        LinearLayout.LayoutParams laneLp = new LinearLayout.LayoutParams(-2, -2);
        laneLp.setMargins(0, dp(2), 0, 0);
        laneSection.addView(laneBar, laneLp);
        LinearLayout.LayoutParams laneSectionLp = new LinearLayout.LayoutParams(-2, -2);
        laneSectionLp.setMargins(0, dp(5), 0, dp(4));
        panel.addView(laneSection, laneSectionLp);

        lightRow = new LinearLayout(this);
        lightRow.setOrientation(LinearLayout.HORIZONTAL);
        lightRow.setGravity(Gravity.CENTER);
        lightRow.setVisibility(View.GONE);
        panel.addView(lightRow, new LinearLayout.LayoutParams(-2, -2));

        etaText = new TextView(this);
        etaText.setTextColor(0xFFE8EAED);
        etaText.setTextSize(sp(15f));
        etaText.setSingleLine(false);
        etaText.setMaxLines(4);
        etaText.setGravity(Gravity.CENTER);
        etaText.setVisibility(View.GONE);
        panel.addView(etaText, new LinearLayout.LayoutParams(-2, -2));

        alertText = compactText(0xFFFFF7ED, 14f);
        alertText.setVisibility(View.GONE);
        LinearLayout.LayoutParams alertLp = new LinearLayout.LayoutParams(-2, -2);
        alertLp.setMargins(0, dp(5), 0, 0);
        panel.addView(alertText, alertLp);

        detailText = compactText(0xFFC7D2FE, 12f);
        detailText.setMaxLines(4);
        detailText.setVisibility(View.GONE);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-2, -2);
        detailLp.setMargins(0, dp(3), 0, 0);
        panel.addView(detailText, detailLp);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = getSavedOverlayX();
        params.y = getSavedOverlayY();

        panel.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = params.x;
                    downY = params.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - downRawX) > dp(4)
                            || Math.abs(event.getRawY() - downRawY) > dp(4)) {
                        dragging = true;
                    }
                    params.x = downX + Math.round(event.getRawX() - downRawX);
                    params.y = downY + Math.round(event.getRawY() - downRawY);
                    updateOverlayPosition();
                    return true;
                case MotionEvent.ACTION_UP:
                    saveOverlayPosition();
                    if (!dragging) {
                        openMainActivity();
                    }
                    return true;
                default:
                    return true;
            }
        });

        syncMainOverlayAttachment();
        applyContentVisibilityPrefs();
        updateClusterPosition();
    }

    private void syncMainOverlayAttachment() {
        if (windowManager == null || panel == null || params == null) {
            return;
        }
        boolean enabled = MainActivity.isMainOverlayEnabled(this);
        boolean attached = panel.getParent() != null;
        if (enabled && !attached) {
            try {
                windowManager.addView(panel, params);
                Log.d(TAG, "overlay added");
            } catch (Throwable t) {
                Log.e(TAG, "overlay add failed", t);
            }
            return;
        }
        if (!enabled && attached) {
            try {
                windowManager.removeView(panel);
                Log.d(TAG, "overlay removed by preference");
            } catch (Throwable t) {
                Log.e(TAG, "overlay remove failed", t);
            }
        }
    }

    private void ensureClusterMirror() {
        clusterMirrorEnabled = MainActivity.isClusterMirrorEnabled(this);
        if (!clusterMirrorEnabled) {
            clusterMirrorRetryCount = 0;
            dismissClusterMirror();
            return;
        }
        float nextClusterScale = MainActivity.getClusterScale(this);
        boolean scaleChanged = Math.abs(nextClusterScale - clusterScale) > 0.001f;
        clusterScale = nextClusterScale;
        activateClusterBridge();
        Display display = findClusterDisplay();
        if (display == null) {
            dismissClusterMirror();
            Log.w(TAG, "cluster mirror enabled but no secondary display found");
            if (clusterMirrorRetryCount < 5) {
                clusterMirrorRetryCount++;
                mainHandler.postDelayed(() -> {
                    if (MainActivity.isClusterMirrorEnabled(this)) {
                        ensureClusterMirror();
                    }
                }, 2500L);
            }
            return;
        }
        clusterMirrorRetryCount = 0;
        if (clusterPresentation != null && clusterDisplay != null
                && clusterDisplay.getDisplayId() == display.getDisplayId()
                && !scaleChanged) {
            updateClusterPosition();
            return;
        }
        dismissClusterMirror();
        clusterDisplay = display;
        clusterPresentation = new Presentation(this, display);
        clusterStage = new FrameLayout(clusterPresentation.getContext());
        clusterStage.setBackgroundColor(Color.TRANSPARENT);
        clusterPanel = buildClusterPanel(clusterPresentation.getContext());
        clusterStage.addView(clusterPanel, clusterLayoutParams());
        clusterPresentation.setContentView(clusterStage);
        configureClusterWindow();
        try {
            clusterPresentation.show();
            updateClusterPosition();
            syncClusterFromMain();
            applyContentVisibilityPrefs();
            Log.d(TAG, "cluster mirror shown on display " + display.getDisplayId());
        } catch (Throwable t) {
            Log.e(TAG, "cluster mirror show failed", t);
            dismissClusterMirror();
        }
    }

    private void configureClusterWindow() {
        if (clusterPresentation == null || clusterPresentation.getWindow() == null) {
            return;
        }
        if (canUseOverlayWindowType()) {
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            clusterPresentation.getWindow().setType(type);
        }
        WindowManager.LayoutParams attrs = clusterPresentation.getWindow().getAttributes();
        attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
        attrs.height = WindowManager.LayoutParams.MATCH_PARENT;
        attrs.gravity = Gravity.TOP | Gravity.LEFT;
        attrs.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        attrs.dimAmount = 0f;
        clusterPresentation.getWindow().setAttributes(attrs);
    }

    private boolean canUseOverlayWindowType() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || android.provider.Settings.canDrawOverlays(this);
    }

    private Display findClusterDisplay() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager == null) {
            return null;
        }
        Display[] presentationDisplays = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        for (Display display : presentationDisplays) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                return display;
            }
        }
        Display[] displays = manager.getDisplays();
        for (Display display : displays) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                return display;
            }
        }
        return null;
    }

    private LinearLayout buildClusterPanel(Context context) {
        LinearLayout mirror = new LinearLayout(context);
        mirror.setOrientation(LinearLayout.VERTICAL);
        mirror.setGravity(Gravity.CENTER_HORIZONTAL);
        mirror.setPadding(clusterDp(12), clusterDp(10), clusterDp(12), clusterDp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEA111827);
        bg.setCornerRadius(clusterDp(14));
        bg.setStroke(clusterDp(1), 0x22FFFFFF);
        mirror.setBackground(bg);

        clusterModeText = new TextView(context);
        clusterModeText.setTextColor(0xFFE8EAED);
        clusterModeText.setTextSize(clusterSp(13f));
        clusterModeText.setSingleLine(true);
        clusterModeText.setGravity(Gravity.CENTER);
        clusterModeText.setText("\u5f85\u63a5\u6536\u5bfc\u822a/\u5de1\u822a\u4fe1\u606f");
        mirror.addView(clusterModeText, new LinearLayout.LayoutParams(-2, -2));

        clusterTurnText = new TextView(context);
        clusterTurnText.setTextColor(Color.WHITE);
        clusterTurnText.setTextSize(clusterSp(28f));
        clusterTurnText.setTypeface(Typeface.DEFAULT_BOLD);
        clusterTurnText.setSingleLine(false);
        clusterTurnText.setMaxLines(2);
        clusterTurnText.setEllipsize(TextUtils.TruncateAt.END);
        clusterTurnText.setGravity(Gravity.CENTER);
        clusterTurnText.setPadding(clusterDp(18), clusterDp(6), clusterDp(18), clusterDp(7));
        GradientDrawable turnBg = new GradientDrawable();
        turnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        turnBg.setColors(new int[]{0xFF1D4ED8, 0xFF0891B2});
        turnBg.setCornerRadius(clusterDp(10));
        clusterTurnText.setBackground(turnBg);
        clusterTurnText.setVisibility(View.GONE);
        clusterTurnText.setMinHeight(clusterDp(62));
        LinearLayout.LayoutParams turnLp = new LinearLayout.LayoutParams(-2, -2);
        turnLp.setMargins(0, clusterDp(6), 0, clusterDp(5));
        mirror.addView(clusterTurnText, turnLp);

        clusterLaneSection = new LinearLayout(context);
        clusterLaneSection.setOrientation(LinearLayout.VERTICAL);
        clusterLaneSection.setGravity(Gravity.CENTER_HORIZONTAL);
        clusterLaneSection.setPadding(clusterDp(8), clusterDp(5), clusterDp(8), clusterDp(7));
        GradientDrawable laneBg = new GradientDrawable();
        laneBg.setColor(0xCC0F172A);
        laneBg.setCornerRadius(clusterDp(10));
        laneBg.setStroke(clusterDp(1), 0x1FFFFFFF);
        clusterLaneSection.setBackground(laneBg);
        clusterLaneSection.setVisibility(View.GONE);
        TextView laneTitle = new TextView(context);
        laneTitle.setText("\u8f66\u9053\u4fe1\u606f");
        laneTitle.setTextColor(0xFFBAE6FD);
        laneTitle.setTextSize(clusterSp(11f));
        laneTitle.setTypeface(Typeface.DEFAULT_BOLD);
        laneTitle.setGravity(Gravity.CENTER);
        clusterLaneSection.addView(laneTitle, new LinearLayout.LayoutParams(-2, -2));
        clusterLaneBar = new LaneBarView(context);
        clusterLaneBar.setFrameScaleMultiplier(clusterScale);
        clusterLaneBar.setScaleMultiplier(1.5f);
        LinearLayout.LayoutParams laneLp = new LinearLayout.LayoutParams(-2, -2);
        laneLp.setMargins(0, clusterDp(2), 0, 0);
        clusterLaneSection.addView(clusterLaneBar, laneLp);
        LinearLayout.LayoutParams laneSectionLp = new LinearLayout.LayoutParams(-2, -2);
        laneSectionLp.setMargins(0, clusterDp(5), 0, clusterDp(4));
        mirror.addView(clusterLaneSection, laneSectionLp);

        clusterLightRow = new LinearLayout(context);
        clusterLightRow.setOrientation(LinearLayout.HORIZONTAL);
        clusterLightRow.setGravity(Gravity.CENTER);
        clusterLightRow.setVisibility(View.GONE);
        mirror.addView(clusterLightRow, new LinearLayout.LayoutParams(-2, -2));

        clusterEtaText = new TextView(context);
        clusterEtaText.setTextColor(0xFFE8EAED);
        clusterEtaText.setTextSize(clusterSp(15f));
        clusterEtaText.setSingleLine(false);
        clusterEtaText.setMaxLines(4);
        clusterEtaText.setGravity(Gravity.CENTER);
        clusterEtaText.setVisibility(View.GONE);
        mirror.addView(clusterEtaText, new LinearLayout.LayoutParams(-2, -2));

        clusterAlertText = compactText(context, 0xFFFFF7ED, 14f, clusterScale);
        clusterAlertText.setVisibility(View.GONE);
        LinearLayout.LayoutParams alertLp = new LinearLayout.LayoutParams(-2, -2);
        alertLp.setMargins(0, clusterDp(5), 0, 0);
        mirror.addView(clusterAlertText, alertLp);

        clusterDetailText = compactText(context, 0xFFC7D2FE, 12f, clusterScale);
        clusterDetailText.setMaxLines(4);
        clusterDetailText.setVisibility(View.GONE);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-2, -2);
        detailLp.setMargins(0, clusterDp(3), 0, 0);
        mirror.addView(clusterDetailText, detailLp);
        return mirror;
    }

    private FrameLayout.LayoutParams clusterLayoutParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = getSavedClusterX();
        lp.topMargin = getSavedClusterY();
        return lp;
    }

    private void updateClusterPosition() {
        if (clusterStage == null || clusterPanel == null) {
            return;
        }
        FrameLayout.LayoutParams lp = clusterLayoutParams();
        int maxX = Math.max(0, clusterStage.getWidth() - clusterPanel.getWidth());
        int maxY = Math.max(0, clusterStage.getHeight() - clusterPanel.getHeight());
        if (maxX > 0) {
            lp.leftMargin = Math.min(lp.leftMargin, maxX);
        }
        if (maxY > 0) {
            lp.topMargin = Math.min(lp.topMargin, maxY);
        }
        clusterPanel.setLayoutParams(lp);
    }

    private void dismissClusterMirror() {
        if (clusterPresentation != null) {
            try {
                clusterPresentation.dismiss();
            } catch (Throwable ignored) {
            }
        }
        clusterPresentation = null;
        clusterStage = null;
        clusterPanel = null;
        clusterModeText = null;
        clusterTurnText = null;
        clusterLaneSection = null;
        clusterLaneBar = null;
        clusterLightRow = null;
        clusterEtaText = null;
        clusterAlertText = null;
        clusterDetailText = null;
        clusterDisplay = null;
    }

    private void activateClusterBridge() {
        try {
            Intent activate = new Intent(ACTION_SEND);
            activate.putExtra("KEY_TYPE", 13014);
            activate.putExtra("EXTRA_ACTIVATE_STATE", 0);
            sendBroadcast(activate);
        } catch (Throwable t) {
            Log.e(TAG, "cluster activation broadcast failed", t);
        }
    }

    private int getSavedOverlayX() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getInt(MainActivity.KEY_OVERLAY_X, rawDp(24));
    }

    private int getSavedOverlayY() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getInt(MainActivity.KEY_OVERLAY_Y, rawDp(220));
    }

    private int getSavedClusterX() {
        return MainActivity.getClusterX(this, rawDp(24));
    }

    private int getSavedClusterY() {
        return MainActivity.getClusterY(this, rawDp(120));
    }

    private void saveOverlayPosition() {
        if (params == null) {
            return;
        }
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putInt(MainActivity.KEY_OVERLAY_X, params.x)
                .putInt(MainActivity.KEY_OVERLAY_Y, params.y)
                .apply();
    }

    private void syncClusterFromMain() {
        copyTextState(modeText, clusterModeText);
        copyTextState(turnText, clusterTurnText);
        copyTextState(etaText, clusterEtaText);
        copyTextState(alertText, clusterAlertText);
        copyTextState(detailText, clusterDetailText);
        copyVisibility(laneSection, clusterLaneSection);
        renderTrafficLights();
        applyContentVisibilityPrefs();
        updateClusterPosition();
    }

    private void copyTextState(TextView source, TextView target) {
        if (source == null || target == null) {
            return;
        }
        target.setText(source.getText());
        target.setVisibility(source.getVisibility());
    }

    private void copyVisibility(View source, View target) {
        if (source != null && target != null) {
            target.setVisibility(source.getVisibility());
        }
    }

    private void showAnyPanel() {
        refreshPanelVisibility();
    }

    private void refreshPanelVisibility() {
        if (panel != null) {
            panel.setVisibility(hasVisibleChildren(panel) ? View.VISIBLE : View.GONE);
        }
        if (clusterPanel != null) {
            clusterPanel.setVisibility(hasVisibleChildren(clusterPanel) ? View.VISIBLE : View.GONE);
        }
    }

    private boolean hasVisibleChildren(LinearLayout layout) {
        if (layout == null) {
            return false;
        }
        for (int i = 0; i < layout.getChildCount(); i++) {
            if (layout.getChildAt(i).getVisibility() == View.VISIBLE) {
                return true;
            }
        }
        return false;
    }

    private void updateOverlayPosition() {
        try {
            if (windowManager != null && panel != null && panel.getParent() != null) {
                windowManager.updateViewLayout(panel, params);
            }
        } catch (Throwable t) {
            Log.e(TAG, "drag update failed", t);
        }
        updateClusterPosition();
    }

    private void rebuildOverlay() {
        int oldX = params != null ? params.x : rawDp(24);
        int oldY = params != null ? params.y : rawDp(220);
        if (windowManager != null && panel != null && panel.getParent() != null) {
            try {
                windowManager.removeView(panel);
            } catch (Throwable t) {
                Log.e(TAG, "overlay remove for scale failed", t);
            }
        }
        panel = null;
        modeText = null;
        turnText = null;
        laneSection = null;
        laneBar = null;
        lightRow = null;
        etaText = null;
        alertText = null;
        detailText = null;
        ensureOverlay();
        if (params != null) {
            params.x = oldX;
            params.y = oldY;
            updateOverlayPosition();
        }
        requestLaneInfo();
        requestTrafficLightInfo();
    }

    private void stopSelfIfNoVisuals() {
        if (!MainActivity.isMainOverlayEnabled(this) && !MainActivity.isClusterMirrorEnabled(this)) {
            stopSelf();
        }
    }

    private void openMainActivity() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "open main activity failed", t);
        }
    }

    private void handleBroadcast(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (MainActivity.ACTION_OVERLAY_SCALE_CHANGED.equals(action)) {
            rebuildOverlay();
            return;
        }
        if (MainActivity.ACTION_MAIN_OVERLAY_CHANGED.equals(action)) {
            ensureOverlay();
            stopSelfIfNoVisuals();
            return;
        }
        if (MainActivity.ACTION_CLUSTER_MIRROR_CHANGED.equals(action)) {
            ensureClusterMirror();
            stopSelfIfNoVisuals();
            return;
        }
        if (MainActivity.ACTION_OVERLAY_CONTENT_CHANGED.equals(action)) {
            applyContentVisibilityPrefs();
            return;
        }
        Bundle extras = intent.getExtras();
        Log.d(TAG, "recv action=" + action + " extras=" + describeExtras(extras));
        if (extras == null) {
            return;
        }

        ensureOverlay();
        updateModeFromExtras(extras);
        updateTurnFromExtras(extras);
        updateEtaFromExtras(extras);
        updateLaneFromExtras(extras);
        updateProtocolDetails(extras);

        int keyType = intValue(extras, "KEY_TYPE", -1);
        boolean trafficLightAction = action != null
                && action.toLowerCase(java.util.Locale.US).contains("traffic_light");
        if (keyType == KEY_TRAFFIC_LIGHT_COUNTDOWN
                || trafficLightAction
                || extras.containsKey("trafficLightStatus")
                || extras.containsKey("TRAFFIC_LIGHT_STATUS")
                || extras.containsKey("traffic_light_status")
                || extras.containsKey("redLightCountDownSeconds")
                || extras.containsKey("RED_LIGHT_COUNT_DOWN_SECONDS")
                || extras.containsKey("red_light_count_down_seconds")
                || extras.containsKey("greenLightLastSecond")
                || extras.containsKey("GREEN_LIGHT_LAST_SECOND")
                || extras.containsKey("green_light_last_second")
                || extras.containsKey("leftRedLightCountDownSeconds")
                || extras.containsKey("straightRedLightCountDownSeconds")
                || extras.containsKey("rightRedLightCountDownSeconds")
                || extras.containsKey("trafficLights")
                || extras.containsKey("trafficLightInfo")
                || extras.containsKey("cameraLightInfo")
                || extras.containsKey("cameraLightInfos")
                || extras.containsKey("cameraLightInfoWrapper")
                || extras.containsKey("cameraLights")
                || extras.containsKey("lightInfos")
                || extras.containsKey("dir")) {
            updateTrafficLights(extras);
        }

        if (ACTION_SEND.equals(action) && intValue(extras, "KEY_TYPE", -1) == 13012) {
            updateLaneFromExtras(extras);
        }
    }

    private void applyContentVisibilityPrefs() {
        syncModeVisibility();
        syncTurnVisibility();
        syncLaneVisibility();
        syncEtaVisibility();
        syncAlertVisibility();
        syncDetailVisibility();
        syncTrafficLightVisibility();
        refreshPanelVisibility();
        updateClusterPosition();
    }

    private void syncModeVisibility() {
        boolean visible = MainActivity.isModeVisible(this) && modeText != null;
        setPairedVisibility(modeText, clusterModeText, visible);
    }

    private void syncTurnVisibility() {
        boolean visible = MainActivity.isTurnVisible(this)
                && turnText != null
                && !TextUtils.isEmpty(turnText.getText());
        setPairedVisibility(turnText, clusterTurnText, visible);
    }

    private void syncLaneVisibility() {
        boolean visible = MainActivity.isLaneVisible(this)
                && laneBar != null
                && laneBar.getVisibility() == View.VISIBLE;
        setPairedVisibility(laneSection, clusterLaneSection, visible);
    }

    private void syncTrafficLightVisibility() {
        boolean visible = MainActivity.isLightVisible(this)
                && lightRow != null
                && lightRow.getChildCount() > 0;
        setPairedVisibility(lightRow, clusterLightRow, visible);
    }

    private void syncEtaVisibility() {
        boolean visible = MainActivity.isEtaVisible(this)
                && etaText != null
                && !TextUtils.isEmpty(etaText.getText());
        setPairedVisibility(etaText, clusterEtaText, visible);
    }

    private void syncAlertVisibility() {
        boolean visible = MainActivity.isAlertVisible(this)
                && alertText != null
                && !TextUtils.isEmpty(alertText.getText());
        setPairedVisibility(alertText, clusterAlertText, visible);
    }

    private void syncDetailVisibility() {
        boolean visible = MainActivity.isDetailVisible(this)
                && detailText != null
                && !TextUtils.isEmpty(detailText.getText());
        setPairedVisibility(detailText, clusterDetailText, visible);
    }

    private void setPairedVisibility(View main, View cluster, boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        if (main != null) {
            main.setVisibility(state);
        }
        if (cluster != null) {
            cluster.setVisibility(state);
        }
    }

    private TextView compactText(int color, float size) {
        return compactText(this, color, size);
    }

    private TextView compactText(Context context, int color, float size) {
        return compactText(context, color, size, overlayScale);
    }

    private TextView compactText(Context context, int color, float size, float scale) {
        TextView view = new TextView(context);
        view.setTextColor(color);
        view.setTextSize(scaledSp(size, scale));
        view.setSingleLine(false);
        view.setMaxLines(2);
        view.setGravity(Gravity.CENTER);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(scaledDp(8, scale), scaledDp(2, scale), scaledDp(8, scale), scaledDp(2, scale));
        return view;
    }

    private void updateModeFromExtras(Bundle extras) {
        if (modeText == null) {
            return;
        }
        int keyType = intValue(extras, "KEY_TYPE", -1);
        int state = intValue(extras, "EXTRA_STATE", -1);
        if (keyType != 10001 && keyType != 10019 && keyType != 60021) {
            return;
        }
        if (keyType == 10019 && state != 8 && state != 9 && state != 24 && state != 25) {
            return;
        }
        int type = intValue(extras, "TYPE", -1);
        int speed = intValue(extras, "CUR_SPEED", intValue(extras, "SPEED", -1));
        String road = valueString(extras, "CUR_ROAD_NAME", "NEXT_ROAD_NAME", "ROAD_NAME", "roadName");
        boolean hasRoute = hasAny(extras, "ROUTE_REMAIN_DIS_AUTO", "ROUTE_REMAIN_TIME_AUTO",
                "ROUTE_REMAIN_DIS", "ROUTE_REMAIN_TIME", "ETA_TEXT");

        String mode;
        if (keyType == 10019 && state == 24) {
            mode = "\u5de1\u822a";
            inCruiseMode = true;
        } else if (keyType == 10019 && state == 25) {
            mode = "\u5de1\u822a\u5df2\u9000\u51fa";
            inCruiseMode = false;
            navigationTurnDir = -1;
            lastDetailedMode = null;
        } else if (keyType == 10019 && state == 8) {
            mode = "\u5bfc\u822a";
            inCruiseMode = false;
        } else if (keyType == 10019 && state == 9) {
            mode = "\u5bfc\u822a\u5df2\u9000\u51fa";
            inCruiseMode = false;
            navigationTurnDir = -1;
            if (etaText != null) {
                etaText.setVisibility(View.GONE);
            }
            if (clusterEtaText != null) {
                clusterEtaText.setVisibility(View.GONE);
            }
            if (lightRow != null) {
                lightRow.setVisibility(View.GONE);
            }
            if (clusterLightRow != null) {
                clusterLightRow.setVisibility(View.GONE);
            }
            trafficLights.clear();
            hideLaneData();
            if (turnText != null) {
                turnText.setVisibility(View.GONE);
            }
            if (clusterTurnText != null) {
                clusterTurnText.setVisibility(View.GONE);
            }
            if (alertText != null) {
                alertText.setVisibility(View.GONE);
            }
            if (clusterAlertText != null) {
                clusterAlertText.setVisibility(View.GONE);
            }
            if (detailText != null) {
                detailText.setVisibility(View.GONE);
            }
            if (clusterDetailText != null) {
                clusterDetailText.setVisibility(View.GONE);
            }
        } else if (type == 1) {
            mode = "\u6a21\u62df\u5bfc\u822a";
        } else if (type == 2 || (!hasRoute && (speed >= 0 || !TextUtils.isEmpty(road)))) {
            mode = "\u5de1\u822a";
            inCruiseMode = true;
        } else if (keyType == 10001 || hasRoute) {
            mode = "\u5bfc\u822a";
            inCruiseMode = false;
        } else {
            mode = "\u5df2\u8fde\u63a5";
        }

        StringBuilder sb = new StringBuilder(mode);
        if (!TextUtils.isEmpty(road)) {
            sb.append(" \u00b7 ").append(road);
        }
        if (speed >= 0) {
            sb.append(" \u00b7 ").append(speed).append(" km/h");
        }
        String text = sb.toString();
        if ("\u5df2\u8fde\u63a5".equals(mode) && !TextUtils.isEmpty(lastDetailedMode)) {
            return;
        }
        if (!"\u5df2\u8fde\u63a5".equals(mode)
                && (!TextUtils.isEmpty(road) || speed >= 0 || "\u5de1\u822a".equals(mode))) {
            lastDetailedMode = text;
        }
        modeText.setText(text);
        if (clusterModeText != null) {
            clusterModeText.setText(text);
        }
        syncModeVisibility();
        if (MainActivity.isModeVisible(this)) {
            showAnyPanel();
        }
    }

    private void updateTurnFromExtras(Bundle extras) {
        if (turnText == null) {
            return;
        }
        int keyType = intValue(extras, "KEY_TYPE", -1);
        if (keyType != 10001) {
            return;
        }
        if (inCruiseMode) {
            setPairedVisibility(turnText, clusterTurnText, false);
            return;
        }
        int icon = intValue(extras, "NEW_ICON", intValue(extras, "ICON", 0));
        if (icon <= 0) {
            setPairedVisibility(turnText, clusterTurnText, false);
            return;
        }
        navigationTurnDir = turnIconToTrafficDir(icon);
        String distance = valueString(extras, "SEG_REMAIN_DIS_AUTO", "NEXT_SEG_REMAIN_DIS_AUTO");
        if (TextUtils.isEmpty(distance)) {
            int meters = intValue(extras, "SEG_REMAIN_DIS", intValue(extras, "NEXT_SEG_REMAIN_DIS", -1));
            if (meters > 0) {
                distance = formatDistance(meters);
            }
        }
        String symbol = turnSymbol(icon, intValue(extras, "ROUNG_ABOUT_NUM", 0));
        String nextRoad = valueString(extras, "NEXT_ROAD_NAME", "nextRoadName");
        StringBuilder text = new StringBuilder(symbol);
        if (!TextUtils.isEmpty(distance)) {
            text.append("  ").append(distance);
        }
        if (!TextUtils.isEmpty(nextRoad)) {
            text.append('\n').append("\u8fdb\u5165 ").append(nextRoad);
        }
        turnText.setText(text.toString());
        if (clusterTurnText != null) {
            clusterTurnText.setText(text.toString());
        }
        syncTurnVisibility();
        if (MainActivity.isTurnVisible(this)) {
            showAnyPanel();
        }
    }

    private void updateTrafficLights(Bundle extras) {
        if (lightRow == null) {
            return;
        }
        int keyType = intValue(extras, "KEY_TYPE", -1);
        if (keyType != KEY_TRAFFIC_LIGHT_COUNTDOWN
                && intValue(extras, "TRAFFIC_LIGHT_NUM", -1) == 0
                && intValue(extras, "routeRemainTrafficLightNum", -1) == 0
                && !hasCountdownPayload(extras)) {
            trafficLights.clear();
            renderTrafficLights();
            return;
        }
        HashMap<Integer, LightState> nextLights = new HashMap<>();
        if (updateCruiseCameraTrafficLights(extras, nextLights)) {
            applyTrafficLights(nextLights);
            return;
        }
        if (updateTrafficLightsFromJson(extras, nextLights)) {
            applyTrafficLights(nextLights);
            return;
        }
        if (updateDirectionalTrafficLights(extras, nextLights)) {
            applyTrafficLights(nextLights);
            return;
        }
        int[] dirs = intArrayValue(extras, "dir", "DIR", "dirs", "DIRECTIONS", "direction",
                "directions", "trafficLightDir", "trafficLightDirs", "trafficLightDirection",
                "trafficLightDirections", "TRAFFIC_LIGHT_DIR", "TRAFFIC_LIGHT_DIRECTION");
        int[] statuses = intArrayValue(extras, "trafficLightStatus", "TRAFFIC_LIGHT_STATUS",
                "trafficLightStatuses", "traffic_light_status", "trafficLightState",
                "trafficLightStates", "TRAFFIC_LIGHT_STATE");
        int[] reds = intArrayValue(extras, "redLightCountDownSeconds", "redLightCountDownSecond",
                "redLightCountdownSeconds", "redSeconds", "redCountDown", "redCountdown",
                "RED_LIGHT_COUNT_DOWN_SECONDS", "red_light_count_down_seconds");
        int[] greens = intArrayValue(extras, "greenLightLastSecond", "greenLightCountDownSeconds",
                "greenLightCountdownSeconds", "greenSeconds", "greenCountDown", "greenCountdown",
                "GREEN_LIGHT_LAST_SECOND", "green_light_last_second");
        int count = Math.max(Math.max(lengthOf(dirs), lengthOf(statuses)), Math.max(lengthOf(reds), lengthOf(greens)));
        if (count == 0) {
            count = 1;
        }

        for (int i = 0; i < count; i++) {
            int dir = valueAt(dirs, i, intValue(extras, "dir", intValue(extras, "direction", -1)));
            int status = valueAt(statuses, i, intValue(extras, "trafficLightStatus", -1));
            int red = valueAt(reds, i, intValue(extras, "redLightCountDownSeconds", 0));
            int green = valueAt(greens, i, intValue(extras, "greenLightLastSecond", 0));
            int seconds = secondsForLight(status, red, green);
            int key = dir >= 0 ? dir : 999;
            if (seconds > 0) {
                putLightState(nextLights, key, dir, status, red, green, seconds);
            }
        }
        applyTrafficLights(nextLights);
    }

    private boolean hasCountdownPayload(Bundle extras) {
        return hasAny(extras, "trafficLightStatus", "TRAFFIC_LIGHT_STATUS", "traffic_light_status",
                "redLightCountDownSeconds", "redLightCountDownSecond", "redLightCountdownSeconds",
                "redSeconds", "redCountDown", "redCountdown", "RED_LIGHT_COUNT_DOWN_SECONDS",
                "greenLightLastSecond", "greenLightCountDownSeconds", "greenLightCountdownSeconds",
                "greenSeconds", "greenCountDown", "greenCountdown", "GREEN_LIGHT_LAST_SECOND",
                "dir", "direction", "trafficLightDir", "trafficLightDirection", "trafficLights",
                "trafficLight", "trafficLightInfo", "trafficLightsCountdownInfo", "cameraLightInfo",
                "cameraLightInfos", "cameraLightInfoWrapper", "cameraLights", "lightInfos");
    }

    private void applyTrafficLights(HashMap<Integer, LightState> nextLights) {
        if (inCruiseMode) {
            mergeTrafficLights(nextLights);
        } else {
            replaceTrafficLights(nextLights);
        }
        renderTrafficLights();
    }

    private boolean updateCruiseCameraTrafficLights(Bundle extras, HashMap<Integer, LightState> target) {
        boolean handled = false;
        for (String key : extras.keySet()) {
            Object value = safeExtra(extras, key);
            if (value == null) {
                continue;
            }
            String lowerKey = key == null ? "" : key.toLowerCase(java.util.Locale.US);
            if (lowerKey.contains("cameralight") || lowerKey.contains("camera_light")
                    || lowerKey.contains("lightinfos") || lowerKey.contains("light_infos")
                    || String.valueOf(value).contains("CameraLightInfo{")) {
                handled |= parseCameraLightValue(value, target);
            }
        }
        return handled;
    }

    private boolean parseCameraLightValue(Object value, HashMap<Integer, LightState> target) {
        if (value == null) {
            return false;
        }
        if (value instanceof Bundle) {
            return parseCameraLightBundle((Bundle) value, target);
        }
        if (value instanceof Iterable) {
            boolean handled = false;
            for (Object item : (Iterable<?>) value) {
                handled |= parseCameraLightValue(item, target);
            }
            return handled;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            boolean handled = false;
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                handled |= parseCameraLightValue(Array.get(value, i), target);
            }
            return handled;
        }
        return parseCameraLightText(String.valueOf(value), target);
    }

    private boolean parseCameraLightBundle(Bundle bundle, HashMap<Integer, LightState> target) {
        boolean handled = putCameraLightInfo(
                intValue(bundle, "direction", intValue(bundle, "dir", intValue(bundle, "c", -1))),
                intValue(bundle, "status", intValue(bundle, "trafficLightStatus", intValue(bundle, "d", -1))),
                intValue(bundle, "countDown", intValue(bundle, "countdown",
                        intValue(bundle, "redLightCountDownSeconds", intValue(bundle, "e", 0)))),
                target);
        for (String key : bundle.keySet()) {
            Object value = safeExtra(bundle, key);
            if (value != null && value != bundle) {
                handled |= parseCameraLightValue(value, target);
            }
        }
        return handled;
    }

    private boolean parseCameraLightText(String text, HashMap<Integer, LightState> target) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        boolean handled = false;
        Matcher matcher = CAMERA_LIGHT_PATTERN.matcher(text);
        while (matcher.find()) {
            handled |= parseCameraLightFields(matcher.group(1), target);
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                if (trimmed.startsWith("[")) {
                    JSONArray array = new JSONArray(trimmed);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item != null) {
                            handled |= parseCameraLightObject(item, target);
                        }
                    }
                } else {
                    handled |= parseCameraLightObject(new JSONObject(trimmed), target);
                }
            } catch (Throwable t) {
                Log.d(TAG, "camera light json parse skipped: " + text);
            }
        }
        return handled;
    }

    private boolean parseCameraLightFields(String fields, HashMap<Integer, LightState> target) {
        int dir = -1;
        int status = -1;
        int countDown = 0;
        String[] parts = fields.split(",");
        for (String part : parts) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String name = pair[0].trim();
            int value = parseInt(pair[1].trim(), Integer.MIN_VALUE);
            if (value == Integer.MIN_VALUE) {
                continue;
            }
            if ("direction".equals(name) || "dir".equals(name) || "c".equals(name)) {
                dir = value;
            } else if ("status".equals(name) || "trafficLightStatus".equals(name) || "d".equals(name)) {
                status = value;
            } else if ("countDown".equals(name) || "countdown".equals(name)
                    || "redLightCountDownSeconds".equals(name) || "e".equals(name)) {
                countDown = value;
            }
        }
        return putCameraLightInfo(dir, status, countDown, target);
    }

    private boolean parseCameraLightObject(JSONObject object, HashMap<Integer, LightState> target) {
        boolean handled = false;
        JSONArray array = object.optJSONArray("cameraLightInfos");
        if (array == null) {
            array = object.optJSONArray("cameraLights");
        }
        if (array == null) {
            array = object.optJSONArray("trafficLights");
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    handled |= parseCameraLightObject(item, target);
                }
            }
        }
        handled |= putCameraLightInfo(
                object.optInt("direction", object.optInt("dir", object.optInt("c", -1))),
                object.optInt("status", object.optInt("trafficLightStatus", object.optInt("d", -1))),
                object.optInt("countDown", object.optInt("countdown",
                        object.optInt("redLightCountDownSeconds", object.optInt("e", 0)))),
                target);
        return handled;
    }

    private boolean putCameraLightInfo(int cameraDir, int cameraStatus, int countDown,
                                       HashMap<Integer, LightState> target) {
        if (cameraDir < 0 || countDown <= 0) {
            return false;
        }
        int dir = normalizeCruiseCameraDirection(cameraDir);
        int status = cameraStatus == 1 ? 1 : 4;
        putLightState(target, dir >= 0 ? dir : 999, dir, status,
                status == 1 ? countDown : 0, status == 4 ? countDown : 0, countDown);
        return true;
    }

    private int normalizeCruiseCameraDirection(int cameraDir) {
        if (cameraDir == 0) {
            return 0;
        }
        if (cameraDir == 1) {
            return 1;
        }
        if (cameraDir == 2) {
            return 4;
        }
        if (cameraDir == 3) {
            return 2;
        }
        return cameraDir;
    }

    private boolean updateTrafficLightsFromJson(Bundle extras, HashMap<Integer, LightState> target) {
        boolean handled = false;
        for (String key : extras.keySet()) {
            Object value = safeExtra(extras, key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (TextUtils.isEmpty(text) || !looksLikeTrafficLightPayload(key, text)) {
                continue;
            }
            handled |= parseTrafficLightPayload(text, target);
        }
        return handled;
    }

    private boolean looksLikeTrafficLightPayload(String key, String text) {
        String lowerKey = key == null ? "" : key.toLowerCase(java.util.Locale.US);
        String lowerText = text.toLowerCase(java.util.Locale.US);
        return lowerKey.contains("trafficlight") || lowerKey.contains("traffic_light")
                || lowerKey.contains("redlight") || lowerKey.contains("greenlight")
                || lowerText.contains("redlightcountdownseconds")
                || lowerText.contains("greenlightlastsecond")
                || lowerText.contains("trafficlightstatus");
    }

    private boolean parseTrafficLightPayload(String text, HashMap<Integer, LightState> target) {
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                boolean handled = false;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) {
                        handled |= parseTrafficLightObject(item, target);
                    }
                }
                return handled;
            }
            if (trimmed.startsWith("{")) {
                return parseTrafficLightObject(new JSONObject(trimmed), target);
            }
        } catch (Throwable t) {
            Log.d(TAG, "traffic light payload parse skipped: " + text);
        }
        return false;
    }

    private boolean parseTrafficLightObject(JSONObject object, HashMap<Integer, LightState> target) {
        boolean handled = false;
        JSONArray lights = object.optJSONArray("trafficLights");
        if (lights == null) {
            lights = object.optJSONArray("trafficLight");
        }
        if (lights == null) {
            lights = object.optJSONArray("lights");
        }
        if (lights != null) {
            for (int i = 0; i < lights.length(); i++) {
                JSONObject item = lights.optJSONObject(i);
                if (item != null) {
                    handled |= parseTrafficLightObject(item, target);
                }
            }
        }

        int dir = object.optInt("dir", object.optInt("direction",
                object.optInt("trafficLightDir", object.optInt("trafficLightDirection", -1))));
        int status = object.optInt("trafficLightStatus", object.optInt("status",
                object.optInt("trafficLightState", -1)));
        int red = object.optInt("redLightCountDownSeconds", object.optInt("redLightCountdownSeconds",
                object.optInt("redSeconds", object.optInt("redCountDown", 0))));
        int green = object.optInt("greenLightLastSecond", object.optInt("greenLightCountDownSeconds",
                object.optInt("greenLightCountdownSeconds", object.optInt("greenSeconds", 0))));
        int seconds = secondsForLight(status, red, green);
        if (seconds > 0) {
            int key = dir >= 0 ? dir : 999;
            putLightState(target, key, dir, status, red, green, seconds);
            handled = true;
        }
        return handled;
    }

    private boolean updateDirectionalTrafficLights(Bundle extras, HashMap<Integer, LightState> target) {
        boolean handled = false;
        handled |= putDirectionalLight(target, extras, 1, "leftRedLightCountDownSeconds",
                "LEFT_RED_LIGHT_COUNT_DOWN_SECONDS", "leftRedSeconds");
        handled |= putDirectionalLight(target, extras, 4, "straightRedLightCountDownSeconds",
                "STRAIGHT_RED_LIGHT_COUNT_DOWN_SECONDS", "straightRedSeconds", "frontRedSeconds");
        handled |= putDirectionalLight(target, extras, 2, "rightRedLightCountDownSeconds",
                "RIGHT_RED_LIGHT_COUNT_DOWN_SECONDS", "rightRedSeconds");
        handled |= putDirectionalLight(target, extras, 1, "leftGreenLightLastSecond",
                "LEFT_GREEN_LIGHT_LAST_SECOND", "leftGreenSeconds");
        handled |= putDirectionalLight(target, extras, 4, "straightGreenLightLastSecond",
                "STRAIGHT_GREEN_LIGHT_LAST_SECOND", "straightGreenSeconds", "frontGreenSeconds");
        handled |= putDirectionalLight(target, extras, 2, "rightGreenLightLastSecond",
                "RIGHT_GREEN_LIGHT_LAST_SECOND", "rightGreenSeconds");
        return handled;
    }

    private boolean putDirectionalLight(HashMap<Integer, LightState> target, Bundle extras, int dir, String... keys) {
        int seconds = intValue(extras, keys[0], 0);
        for (int i = 1; i < keys.length && seconds <= 0; i++) {
            seconds = intValue(extras, keys[i], 0);
        }
        if (seconds <= 0) {
            return false;
        }
        int status = keys[0].toLowerCase(java.util.Locale.US).contains("green") ? 4 : 1;
        putLightState(target, dir >= 0 ? dir : 999, dir, status, status == 1 ? seconds : 0,
                status == 4 ? seconds : 0, seconds);
        return true;
    }

    private void putLightState(int key, int dir, int status, int red, int green, int seconds) {
        putLightState(trafficLights, key, dir, status, red, green, seconds);
    }

    private void putLightState(HashMap<Integer, LightState> target, int key, int dir,
                               int status, int red, int green, int seconds) {
        LightState state = new LightState();
        state.dir = dir;
        state.status = status;
        state.seconds = seconds;
        state.color = colorForStatus(status, red, green);
        state.updatedAt = System.currentTimeMillis();
        LightState old = target.get(key);
        if (old == null || preferLightState(state, old)) {
            target.put(key, state);
        }
    }

    private boolean preferLightState(LightState candidate, LightState old) {
        if (isRedLightStatus(old.status) && !isRedLightStatus(candidate.status)) {
            return false;
        }
        if (isRedLightStatus(candidate.status) && !isRedLightStatus(old.status)) {
            return true;
        }
        return candidate.seconds > 0;
    }

    private void replaceTrafficLights(HashMap<Integer, LightState> nextLights) {
        trafficLights.clear();
        trafficLights.putAll(nextLights);
    }

    private void mergeTrafficLights(HashMap<Integer, LightState> nextLights) {
        for (Map.Entry<Integer, LightState> entry : nextLights.entrySet()) {
            LightState old = trafficLights.get(entry.getKey());
            LightState state = entry.getValue();
            if (old == null || old.status != state.status || preferLightState(state, old)) {
                trafficLights.put(entry.getKey(), state);
            }
        }
    }

    private void renderTrafficLights() {
        if (lightRow == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, LightState>> iterator = trafficLights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, LightState> entry = iterator.next();
            if (now - entry.getValue().updatedAt > LIGHT_TTL_MS) {
                iterator.remove();
            }
        }
        if (trafficLights.isEmpty()) {
            lightRow.removeAllViews();
            lightRow.setVisibility(View.GONE);
            if (clusterLightRow != null) {
                clusterLightRow.removeAllViews();
                clusterLightRow.setVisibility(View.GONE);
            }
            return;
        }

        ArrayList<Integer> keys = new ArrayList<>(trafficLights.keySet());
        Collections.sort(keys, (a, b) -> directionPriority(a) - directionPriority(b));
        if (!inCruiseMode && keys.size() > 1) {
            Integer preferred = preferredNavigationLightKey(keys);
            keys.clear();
            if (preferred != null) {
                keys.add(preferred);
            }
        }
        lightRow.removeAllViews();
        if (clusterLightRow != null) {
            clusterLightRow.removeAllViews();
        }
        boolean showDirectionLabel = inCruiseMode && keys.size() > 1;
        for (Integer key : keys) {
            LightState state = trafficLights.get(key);
            if (state == null) {
                continue;
            }
            lightRow.addView(lightPill(this, state, showDirectionLabel));
            if (clusterLightRow != null) {
                clusterLightRow.addView(lightPill(clusterPresentation.getContext(), state, showDirectionLabel, clusterScale));
            }
        }
        syncTrafficLightVisibility();
        if (MainActivity.isLightVisible(this) && lightRow.getChildCount() > 0) {
            showAnyPanel();
        }
    }

    private Integer preferredNavigationLightKey(ArrayList<Integer> keys) {
        if (navigationTurnDir >= 0 && trafficLights.containsKey(navigationTurnDir)) {
            return navigationTurnDir;
        }
        Integer best = null;
        for (Integer key : keys) {
            LightState state = trafficLights.get(key);
            if (state == null) {
                continue;
            }
            if (best == null) {
                best = key;
                continue;
            }
            LightState old = trafficLights.get(best);
            if (old == null || state.seconds < old.seconds) {
                best = key;
            }
        }
        return best;
    }

    private TextView lightPill(LightState state, boolean showDirectionLabel) {
        return lightPill(this, state, showDirectionLabel);
    }

    private TextView lightPill(Context context, LightState state, boolean showDirectionLabel) {
        return lightPill(context, state, showDirectionLabel, overlayScale);
    }

    private TextView lightPill(Context context, LightState state, boolean showDirectionLabel, float scale) {
        TextView view = new TextView(context);
        view.setTextColor(Color.WHITE);
        view.setTextSize(scaledSp(20f, scale));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(scaledDp(inCruiseMode ? 62 : 54, scale));
        view.setMinHeight(scaledDp(34, scale));
        view.setPadding(scaledDp(12, scale), 0, scaledDp(12, scale), scaledDp(1, scale));
        String label = showDirectionLabel && state.dir >= 0 ? directionLabel(state.dir) : "";
        view.setText(label + state.seconds + "s");
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(state.color);
        bg.setCornerRadius(scaledDp(18, scale));
        view.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, scaledDp(36, scale));
        lp.setMargins(scaledDp(3, scale), scaledDp(3, scale), scaledDp(3, scale), scaledDp(3, scale));
        view.setLayoutParams(lp);
        return view;
    }

    private String turnSymbol(int icon, int roundAboutNum) {
        if (icon == 2) {
            return "\u2190";
        }
        if (icon == 3) {
            return "\u21b1";
        }
        if (icon == 4) {
            return "\u2196";
        }
        if (icon == 5) {
            return "\u2197";
        }
        if (icon == 6) {
            return "\u2199";
        }
        if (icon == 7) {
            return "\u2198";
        }
        if (icon == 8 || icon == 10 || icon == 11 || icon == 12) {
            return "\u21b6";
        }
        if (icon == 13 || icon == 14 || icon == 17 || icon == 18) {
            return roundAboutNum > 0 ? ("\u25ef" + roundAboutNum) : "\u25ef";
        }
        if (icon == 9 || icon == 1) {
            return "\u2191";
        }
        if (icon == 19) {
            return "\u21b7";
        }
        if (icon == 20) {
            return "\u2191";
        }
        return "\u2191";
    }

    private int turnIconToTrafficDir(int icon) {
        if (icon == 2 || icon == 4 || icon == 6) {
            return 1;
        }
        if (icon == 3 || icon == 5 || icon == 7 || icon == 19) {
            return 2;
        }
        if (icon == 8 || icon == 10 || icon == 11 || icon == 12) {
            return 0;
        }
        return 4;
    }

    private int colorForStatus(int status, int red, int green) {
        if (isYellowTailCountdown(red, green)) {
            return 0xFFD49A00;
        }
        if (isYellowLightStatus(status)) {
            return 0xFFD49A00;
        }
        if (isGreenLightStatus(status)) {
            return 0xFF1F8F45;
        }
        if (isRedLightStatus(status)) {
            return 0xFFC62828;
        }
        if (green > 0) {
            return 0xFF1F8F45;
        }
        return 0xFFD49A00;
    }

    private int secondsForLight(int status, int red, int green) {
        if (isGreenLightStatus(status)) {
            return red > 0 ? red : green;
        }
        if (isRedLightStatus(status)) {
            return red > 0 ? red : green;
        }
        if (isYellowLightStatus(status)) {
            return red > 0 ? red : green;
        }
        return red > 0 ? red : green;
    }

    private boolean isRedLightStatus(int status) {
        return status == 1;
    }

    private boolean isGreenLightStatus(int status) {
        return status == 4;
    }

    private boolean isYellowLightStatus(int status) {
        return status == 0 || status == 2 || status == 3 || status == 5 || status == 6;
    }

    private boolean isYellowTailCountdown(int red, int green) {
        return (green == 3 && red == 0) || (green > 0 && green < 3);
    }

    private String directionLabel(int dir) {
        if (dir == 0) {
            return "\u21b6";
        }
        if (dir == 1) {
            return "\u2190";
        }
        if (dir == 2) {
            return "\u2192";
        }
        if (dir == 3) {
            return "\u2192";
        }
        if (dir == 4) {
            return "\u2191";
        }
        if (dir == 5 || dir == 6) {
            return "\u2196";
        }
        if (dir == 7 || dir == 8) {
            return "\u2197";
        }
        return dir >= 0 ? ("D" + dir) : "\u524d";
    }

    private int directionPriority(int dir) {
        if (inCruiseMode) {
            if (dir == 4) {
                return 10;
            }
            if (dir == 1 || dir == 5 || dir == 6) {
                return 20;
            }
            if (dir == 2 || dir == 3 || dir == 7 || dir == 8) {
                return 30;
            }
            if (dir == 0) {
                return 40;
            }
            return 50 + dir;
        }
        if (dir == 1 || dir == 5 || dir == 6) {
            return 10;
        }
        if (dir == 4) {
            return 20;
        }
        if (dir == 2 || dir == 3 || dir == 7 || dir == 8) {
            return 30;
        }
        if (dir == 0) {
            return 40;
        }
        return 50 + dir;
    }

    private void updateEtaFromExtras(Bundle extras) {
        if (etaText == null) {
            return;
        }
        String distance = valueString(extras, "ROUTE_REMAIN_DIS_AUTO", "routeRemainDistanceAuto", "distance");
        String time = valueString(extras, "ROUTE_REMAIN_TIME_AUTO", "routeRemainTimeAuto", "remainTime");
        String eta = valueString(extras, "ETA_TEXT", "etaText", "eta", "arrivalTime", "arriveTime");
        String road = valueString(extras, "NEXT_ROAD_NAME", "CUR_ROAD_NAME", "roadName", "curRoadName");
        String destination = valueString(extras, "endPOIName", "END_POI_NAME", "END_POI",
                "DESTINATION_NAME", "DESTINATION", "EXTRA_DESTINATION_NAME", "POINAME");

        if (TextUtils.isEmpty(distance)) {
            int meters = intValue(extras, "ROUTE_REMAIN_DIS", -1);
            if (meters > 0) {
                distance = formatDistance(meters);
            }
        }
        if (TextUtils.isEmpty(time)) {
            int seconds = intValue(extras, "ROUTE_REMAIN_TIME", -1);
            if (seconds > 0) {
                time = formatDuration(seconds);
            }
        }

        StringBuilder text = new StringBuilder();
        if (!TextUtils.isEmpty(distance)) {
            text.append(distance);
        }
        if (!TextUtils.isEmpty(time)) {
            if (text.length() > 0) {
                text.append(" \u00b7 ");
            }
            text.append(time);
        }
        if (!TextUtils.isEmpty(eta)) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(eta);
        }
        boolean roadIsEmptyDestination = !TextUtils.isEmpty(destination)
                && ("\u76ee\u7684\u5730".equals(road) || road.equals(destination));
        if (!TextUtils.isEmpty(road) && !roadIsEmptyDestination) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(road);
        }
        if (!TextUtils.isEmpty(destination)) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append("\u76ee\u7684\u5730 ").append(destination);
        }

        if (text.length() > 0) {
            etaText.setText(text.toString());
            if (clusterEtaText != null) {
                clusterEtaText.setText(text.toString());
            }
            syncEtaVisibility();
            if (MainActivity.isEtaVisible(this)) {
                showAnyPanel();
            }
        }
    }

    private void updateProtocolDetails(Bundle extras) {
        updateAlertDetails(extras);
        updateStatusDetails(extras);
    }

    private void updateAlertDetails(Bundle extras) {
        if (alertText == null) {
            return;
        }
        ArrayList<String> parts = new ArrayList<>();
        boolean alertPayload = hasAny(extras, "LIMITED_SPEED", "CAMERA_INDEX", "CAMERA_DIST",
                "CAMERA_SPEED", "CAMERA_TYPE", "SAPA_DIST", "SAPA_NAME", "TRAFFIC_LIGHT_NUM",
                "routeRemainTrafficLightNum");

        int limitedSpeed = intValue(extras, "LIMITED_SPEED", -1);
        if (limitedSpeed > 0) {
            parts.add("\u9650\u901f " + limitedSpeed);
        }

        int cameraIndex = intValue(extras, "CAMERA_INDEX", 0);
        int cameraDist = intValue(extras, "CAMERA_DIST", -1);
        int cameraSpeed = intValue(extras, "CAMERA_SPEED", -1);
        if (cameraIndex != -1 && cameraDist >= 0) {
            StringBuilder camera = new StringBuilder(cameraTypeName(intValue(extras, "CAMERA_TYPE", -1)));
            camera.append(' ').append(formatDistance(cameraDist));
            if (cameraSpeed > 0) {
                camera.append(' ').append(cameraSpeed);
            }
            parts.add(camera.toString());
        }

        int sapaDist = intValue(extras, "SAPA_DIST", -1);
        String sapaName = valueString(extras, "SAPA_NAME");
        if (sapaDist >= 0 || !TextUtils.isEmpty(sapaName)) {
            StringBuilder sapa = new StringBuilder("\u670d\u52a1\u533a");
            if (!TextUtils.isEmpty(sapaName)) {
                sapa.append(' ').append(sapaName);
            }
            if (sapaDist >= 0) {
                sapa.append(' ').append(formatDistance(sapaDist));
            }
            parts.add(sapa.toString());
        }

        int lightNum = intValue(extras, "routeRemainTrafficLightNum",
                intValue(extras, "TRAFFIC_LIGHT_NUM", -1));
        if (lightNum > 0) {
            parts.add("\u7ea2\u7eff\u706f " + lightNum + "\u4e2a");
        }

        if (parts.isEmpty()) {
            if (alertPayload) {
                clearAlertDetails();
            }
            return;
        }
        alertText.setText(join(parts, "  \u00b7  "));
        if (clusterAlertText != null) {
            clusterAlertText.setText(alertText.getText());
        }
        alertUpdatedAt = System.currentTimeMillis();
        mainHandler.removeCallbacks(alertClear);
        mainHandler.postDelayed(alertClear, ALERT_TTL_MS + 200L);
        syncAlertVisibility();
        if (MainActivity.isAlertVisible(this)) {
            showAnyPanel();
        }
    }

    private void clearAlertDetails() {
        if (alertText != null) {
            alertText.setVisibility(View.GONE);
            alertText.setText("");
        }
        if (clusterAlertText != null) {
            clusterAlertText.setVisibility(View.GONE);
            clusterAlertText.setText("");
        }
        mainHandler.removeCallbacks(alertClear);
    }

    private void updateStatusDetails(Bundle extras) {
        if (detailText == null) {
            return;
        }
        ArrayList<String> lines = new ArrayList<>();

        String locationJson = valueString(extras, "EXTRA_LOCATION_INFO");
        if (!TextUtils.isEmpty(locationJson)) {
            String parsed = locationSummary(locationJson);
            if (!TextUtils.isEmpty(parsed)) {
                lines.add(parsed);
            }
        }

        int direction = intValue(extras, "CAR_DIRECTION", -1);
        double lat = doubleValue(extras, "CAR_LATITUDE",
                doubleValue(extras, "LAT", doubleValue(extras, "LATITUDE", Double.NaN)));
        double lon = doubleValue(extras, "CAR_LONGITUDE",
                doubleValue(extras, "LON", doubleValue(extras, "LONGITUDE", Double.NaN)));
        if (direction >= 0 || (!Double.isNaN(lat) && !Double.isNaN(lon) && !(lat == 0.0d && lon == 0.0d))) {
            StringBuilder car = new StringBuilder();
            if (direction >= 0) {
                car.append("\u8f66\u5934 ").append(direction).append('\u00b0');
            }
            if (!Double.isNaN(lat) && !Double.isNaN(lon) && !(lat == 0.0d && lon == 0.0d)) {
                if (car.length() > 0) {
                    car.append("  ");
                }
                car.append(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon));
            }
            lines.add(car.toString());
        }

        String province = valueString(extras, "PROVINCE_NAME", "provinceName");
        String city = valueString(extras, "CITY_NAME", "cityName");
        String district = valueString(extras, "DISTRICT_NAME", "districtName");
        String areaCode = valueString(extras, "AREA_CODE", "areaCode");
        if (!TextUtils.isEmpty(province) || !TextUtils.isEmpty(city) || !TextUtils.isEmpty(district)) {
            StringBuilder admin = new StringBuilder("\u884c\u653f\u533a ");
            if (!TextUtils.isEmpty(province)) {
                admin.append(province).append(' ');
            }
            if (!TextUtils.isEmpty(city)) {
                admin.append(city).append(' ');
            }
            if (!TextUtils.isEmpty(district)) {
                admin.append(district);
            }
            if (!TextUtils.isEmpty(areaCode)) {
                admin.append(" ").append(areaCode);
            }
            lines.add(admin.toString().trim());
        }

        String traffic = valueString(extras, "EXTRA_LOCATION_TRAFFIC_INFO",
                "EXTRA_TRAFFIC_CONDITION_RESULT_MESSAGE");
        if (!TextUtils.isEmpty(traffic)) {
            lines.add("\u524d\u65b9\u8def\u51b5 " + traffic);
        }

        if (extras.containsKey("EXTRA_MUTE") || extras.containsKey("EXTRA_CASUAL_MUTE")) {
            boolean mute = intValue(extras, "EXTRA_MUTE", 0) == 1;
            boolean casual = intValue(extras, "EXTRA_CASUAL_MUTE", 0) == 1;
            lines.add("\u64ad\u62a5 " + (mute ? "\u9759\u97f3" : "\u6709\u58f0")
                    + (casual ? " \u00b7 \u4e34\u65f6\u9759\u97f3" : ""));
        }

        if (extras.containsKey("EXTRA_HOME_OR_COMPANY_WHAT")
                || extras.containsKey("EXTRA_HOME_OR_COMPANY_ETA")) {
            boolean home = booleanValue(extras, "EXTRA_HOME_OR_COMPANY_WHAT", true);
            String eta = valueString(extras, "EXTRA_HOME_OR_COMPANY_ETA");
            lines.add((home ? "\u56de\u5bb6" : "\u53bb\u516c\u53f8")
                    + (TextUtils.isEmpty(eta) ? "" : " " + eta));
        }

        String favorite = valueString(extras, "EXTRA_FAVORITE_MY_LOCATION");
        if (!TextUtils.isEmpty(favorite)) {
            lines.add("\u6536\u85cf\u5f53\u524d\u70b9\u5df2\u8fd4\u56de");
        }

        int roadType = intValue(extras, "ROAD_TYPE", -1);
        if (roadType >= 0) {
            lines.add("\u9053\u8def " + roadTypeName(roadType));
        }

        if (lines.isEmpty()) {
            return;
        }
        detailText.setText(join(lines, "\n"));
        if (clusterDetailText != null) {
            clusterDetailText.setText(detailText.getText());
        }
        syncDetailVisibility();
        if (MainActivity.isDetailVisible(this)) {
            showAnyPanel();
        }
    }

    private void updateLaneFromExtras(Bundle extras) {
        if (laneBar == null) {
            return;
        }

        int keyType = intValue(extras, "KEY_TYPE", -1);
        String driveWayJson = valueString(extras, "EXTRA_DRIVE_WAY", "drive_way_info_json", "driveWayInfo");
        if (!TextUtils.isEmpty(driveWayJson) && updateLaneFromJson(driveWayJson)) {
            return;
        }
        if (keyType != 13012 && !hasAny(extras, "drive_way_lane_Back_icon", "trafficLaneType",
                "trafficLaneIcon", "laneBackInfo", "laneSelectInfo", "frontLane", "backLane",
                "FRONT_LANE", "BACK_LANE")) {
            return;
        }

        int[] lanes = intArrayValue(extras, "drive_way_lane_Back_icon", "trafficLaneType",
                "trafficLaneIcon", "laneBackInfo", "laneSelectInfo", "frontLane", "backLane",
                "FRONT_LANE", "BACK_LANE");
        boolean[] advised = booleanArrayValue(extras, "trafficLaneAdvised", "recommend",
                "laneRecommend", "LANE_RECOMMEND");
        if (lanes != null && lanes.length > 0) {
            showLaneData(lanes, advised);
            return;
        }
    }

    private void showLaneData(int[] lanes, boolean[] advised) {
        if (laneBar == null) {
            return;
        }
        laneBar.setLaneData(lanes, advised);
        if (clusterLaneBar != null) {
            clusterLaneBar.setLaneData(lanes, advised);
        }
        syncLaneVisibility();
        if (MainActivity.isLaneVisible(this)) {
            showAnyPanel();
        }
    }

    private void hideLaneData() {
        if (laneBar != null) {
            laneBar.hideLane();
        }
        if (clusterLaneBar != null) {
            clusterLaneBar.hideLane();
        }
        if (laneSection != null) {
            laneSection.setVisibility(View.GONE);
        }
        if (clusterLaneSection != null) {
            clusterLaneSection.setVisibility(View.GONE);
        }
    }

    private boolean updateLaneFromJson(String json) {
        try {
            JSONObject object = new JSONObject(json);
            boolean enabled = object.optBoolean("drive_way_enabled", true);
            if (!enabled) {
                hideLaneData();
                return true;
            }
            JSONArray info = object.optJSONArray("drive_way_info");
            if (info == null || info.length() == 0) {
                hideLaneData();
                return true;
            }
            int count = Math.min(info.length(), 8);
            int[] lanes = new int[count];
            boolean[] advised = new boolean[count];
            boolean hasAdvised = false;
            for (int i = 0; i < count; i++) {
                JSONObject item = info.optJSONObject(i);
                if (item == null) {
                    lanes[i] = 1;
                    advised[i] = true;
                    continue;
                }
                lanes[i] = item.optInt("drive_way_lane_Back_icon",
                        item.optInt("trafficLaneIcon", item.optInt("trafficLaneType", 1)));
                advised[i] = item.optBoolean("trafficLaneAdvised", true);
                hasAdvised |= item.has("trafficLaneAdvised");
            }
            showLaneData(lanes, hasAdvised ? advised : null);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "parse lane json failed: " + json, t);
            return false;
        }
    }

    private void requestLaneInfo() {
        try {
            Intent intent = new Intent(ACTION_RECV);
            intent.setPackage(MainActivity.getTargetPackage(this));
            intent.putExtra("KEY_TYPE", 10062);
            sendBroadcast(intent);
            Log.d(TAG, "request lane info KEY_TYPE=10062");
        } catch (Throwable t) {
            Log.e(TAG, "request lane info failed", t);
        }
    }

    private void requestTrafficLightInfo() {
        try {
            Intent intent = new Intent(ACTION_RECV);
            intent.setPackage(MainActivity.getTargetPackage(this));
            intent.putExtra("KEY_TYPE", KEY_TRAFFIC_LIGHT_COUNTDOWN);
            sendBroadcast(intent);
            Log.d(TAG, "request traffic light info KEY_TYPE=" + KEY_TRAFFIC_LIGHT_COUNTDOWN);
        } catch (Throwable t) {
            Log.e(TAG, "request traffic light info failed", t);
        }
    }

    private boolean hasAny(Bundle extras, String... keys) {
        for (String key : keys) {
            if (extras.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private Object safeExtra(Bundle extras, String key) {
        try {
            return extras.get(key);
        } catch (Throwable t) {
            Log.d(TAG, "skip unreadable extra " + key, t);
            return null;
        }
    }

    private int intValue(Bundle extras, String key, int fallback) {
        Object value = safeExtra(extras, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int[] intArrayValue(Bundle extras, String... keys) {
        for (String key : keys) {
            Object value = safeExtra(extras, key);
            int[] parsed = parseIntArray(value);
            if (parsed != null && parsed.length > 0) {
                return parsed;
            }
        }
        return null;
    }

    private int lengthOf(int[] values) {
        return values == null ? 0 : values.length;
    }

    private int valueAt(int[] values, int index, int fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        if (index < values.length) {
            return values[index];
        }
        return values[values.length - 1];
    }

    private boolean[] booleanArrayValue(Bundle extras, String... keys) {
        for (String key : keys) {
            Object value = safeExtra(extras, key);
            boolean[] parsed = parseBooleanArray(value);
            if (parsed != null && parsed.length > 0) {
                return parsed;
            }
        }
        return null;
    }

    private int[] parseIntArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof int[]) {
            return (int[]) value;
        }
        if (value instanceof Integer) {
            return new int[]{(Integer) value};
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            int[] out = new int[length];
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                out[i] = item instanceof Number ? ((Number) item).intValue() : parseInt(String.valueOf(item), 1);
            }
            return out;
        }
        String s = String.valueOf(value).replace('[', ' ').replace(']', ' ').trim();
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        String[] parts = s.split("[,;| ]+");
        int[] out = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                out[count++] = parseInt(part, 1);
            }
        }
        if (count == 0) {
            return null;
        }
        int[] compact = new int[count];
        System.arraycopy(out, 0, compact, 0, count);
        return compact;
    }

    private boolean[] parseBooleanArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof boolean[]) {
            return (boolean[]) value;
        }
        if (value instanceof Boolean) {
            return new boolean[]{(Boolean) value};
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            boolean[] out = new boolean[length];
            for (int i = 0; i < length; i++) {
                out[i] = parseBoolean(Array.get(value, i));
            }
            return out;
        }
        String s = String.valueOf(value).replace('[', ' ').replace(']', ' ').trim();
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        String[] parts = s.split("[,;| ]+");
        boolean[] out = new boolean[parts.length];
        int count = 0;
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                out[count++] = parseBoolean(part);
            }
        }
        if (count == 0) {
            return null;
        }
        boolean[] compact = new boolean[count];
        System.arraycopy(out, 0, compact, 0, count);
        return compact;
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String s = String.valueOf(value);
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "\u662f".equals(s);
    }

    private boolean booleanValue(Bundle extras, String key, boolean fallback) {
        Object value = safeExtra(extras, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String s = String.valueOf(value);
        if ("1".equals(s) || "true".equalsIgnoreCase(s) || "\u662f".equals(s)) {
            return true;
        }
        if ("0".equals(s) || "false".equalsIgnoreCase(s) || "\u5426".equals(s)) {
            return false;
        }
        return fallback;
    }

    private double doubleValue(Bundle extras, String key, double fallback) {
        Object value = safeExtra(extras, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String valueString(Bundle extras, String... keys) {
        for (String key : keys) {
            Object value = safeExtra(extras, key);
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value);
            if (!TextUtils.isEmpty(s) && !"0".equals(s) && !"null".equals(s)) {
                return s;
            }
        }
        return null;
    }

    private String formatDistance(int meters) {
        if (meters >= 1000) {
            float km = meters / 1000f;
            return String.format(java.util.Locale.US, "%.1f\u516c\u91cc", km);
        }
        return meters + "\u7c73";
    }

    private String formatDuration(int seconds) {
        int minutes = Math.max(1, Math.round(seconds / 60f));
        return minutes + "\u5206\u949f";
    }

    private String locationSummary(String json) {
        try {
            JSONObject object = new JSONObject(json);
            String provider = object.optString("provider", "");
            double speed = object.optDouble("speed", Double.NaN);
            int bearing = object.optInt("bearing", -1);
            int accuracy = object.optInt("accuracy", -1);
            StringBuilder sb = new StringBuilder("\u5b9a\u4f4d");
            if (!TextUtils.isEmpty(provider)) {
                sb.append(' ').append(provider.toUpperCase(java.util.Locale.US));
            }
            if (!Double.isNaN(speed)) {
                int kmh = speed < 45 ? Math.round((float) speed * 3.6f) : Math.round((float) speed);
                sb.append(' ').append(kmh).append("km/h");
            }
            if (bearing >= 0) {
                sb.append(' ').append(bearing).append('\u00b0');
            }
            if (accuracy >= 0) {
                sb.append(" \u00b1").append(accuracy).append('m');
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.e(TAG, "parse location json failed: " + json, t);
            return null;
        }
    }

    private String cameraTypeName(int type) {
        switch (type) {
            case 0:
                return "\u6d4b\u901f";
            case 1:
                return "\u76d1\u63a7";
            case 2:
                return "\u95ef\u7ea2\u706f";
            case 3:
                return "\u8fdd\u7ae0";
            case 4:
                return "\u516c\u4ea4\u9053";
            default:
                return "\u7535\u5b50\u773c";
        }
    }

    private String roadTypeName(int type) {
        switch (type) {
            case 0:
                return "\u9ad8\u901f";
            case 1:
                return "\u56fd\u9053";
            case 2:
                return "\u7701\u9053";
            case 3:
                return "\u53bf\u9053";
            case 4:
                return "\u4e61\u516c\u8def";
            case 5:
                return "\u53bf\u4e61\u6751\u5185\u90e8\u8def";
            case 6:
                return "\u57ce\u5e02\u5feb\u901f\u8def";
            case 7:
                return "\u4e3b\u8981\u9053\u8def";
            case 8:
                return "\u6b21\u8981\u9053\u8def";
            case 9:
                return "\u666e\u901a\u9053\u8def";
            case 10:
                return "\u975e\u5bfc\u822a\u9053\u8def";
            default:
                return "Type " + type;
        }
    }

    private String join(ArrayList<String> values, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private String describeExtras(Bundle extras) {
        if (extras == null) {
            return "{}";
        }
        ArrayList<String> keys = new ArrayList<>(extras.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Object value = safeExtra(extras, key);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(key).append('=');
            sb.append(value);
            if (value != null) {
                sb.append('(').append(value.getClass().getName()).append(')');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private Notification buildNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "AMap Companion", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("AMap Companion")
                .setContentText("\u76d1\u542c\u9ad8\u5fb7\u5bfc\u822a/\u5de1\u822a\u5e7f\u64ad")
                .setOngoing(true)
                .build();
    }

    private int dp(int value) {
        return dp((float) value);
    }

    private int dp(float value) {
        return scaledDp(value, overlayScale);
    }

    private int rawDp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float value) {
        return scaledSp(value, overlayScale);
    }

    private int clusterDp(float value) {
        return scaledDp(value, clusterScale);
    }

    private float clusterSp(float value) {
        return scaledSp(value, clusterScale);
    }

    private int scaledDp(float value, float scale) {
        return (int) (value * scale * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float scaledSp(float value, float scale) {
        return value * scale;
    }

    private static final class LightState {
        int dir;
        int status;
        int seconds;
        int color;
        long updatedAt;
    }
}
