package com.autonavi.companion;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
    private static final String DIY_DIR_NAME = "amap_companion/diy";
    private static final long LIGHT_TTL_MS = 4500L;
    private static final long LIGHT_TICK_MS = 1000L;
    private static final long DISPLAY_POLICY_POLL_MS = 1500L;
    private static final long NAVIGATION_ACTIVE_TTL_MS = 12000L;
    private static final long TARGET_BROADCAST_ACTIVE_TTL_MS = 300000L;
    private static final long PANEL_WIDTH_SHRINK_DELAY_MS = 2500L;
    private static final long FULL_MODE_TURN_MS = 10000L;
    private static final long FULL_MODE_ETA_MS = 5000L;
    private static final Pattern CAMERA_LIGHT_PATTERN = Pattern.compile(
            "CameraLightInfo\\{([^}]*)\\}");

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout panel;
    private LinearLayout modeRow;
    private TextView modeText;
    private TextView titleText;
    private View summaryDivider;
    private LinearLayout summaryRow;
    private TextView headingInfoText;
    private TextView roadInfoText;
    private LinearLayout turnCard;
    private TextView turnLeadText;
    private ImageView turnLeadIconView;
    private TextView turnText;
    private TextView turnDistanceText;
    private ImageView turnIconView;
    private TextView turnDistBadge;
    private LinearLayout turnRowLayout;
    private LinearLayout laneSection;
    private LaneBarView laneBar;
    private LinearLayout lightRow;
    private TextView etaText;
    private LinearLayout alertCard;
    private TextView limitBadgeText;
    private TextView alertCaptionText;
    private TextView alertText;
    private LinearLayout alertRow;
    private LinearLayout navTurnBox;
    private ImageView navTurnIconView;
    private TextView navTurnDistText;
    private TextView detailText;
    private Context clusterContext;
    private WindowManager clusterWindowManager;
    private WindowManager.LayoutParams clusterParams;
    private LinearLayout clusterPanel;
    private LinearLayout clusterModeRow;
    private TextView clusterModeText;
    private TextView clusterTitleText;
    private View clusterSummaryDivider;
    private LinearLayout clusterSummaryRow;
    private TextView clusterHeadingInfoText;
    private TextView clusterRoadInfoText;
    private LinearLayout clusterTurnCard;
    private TextView clusterTurnLeadText;
    private ImageView clusterTurnLeadIconView;
    private TextView clusterTurnText;
    private TextView clusterTurnDistanceText;
    private ImageView clusterTurnIconView;
    private TextView clusterTurnDistBadge;
    private LinearLayout clusterTurnRowLayout;
    private LinearLayout clusterLaneSection;
    private LaneBarView clusterLaneBar;
    private LinearLayout clusterLightRow;
    private TextView clusterEtaText;
    private LinearLayout clusterAlertCard;
    private TextView clusterLimitBadgeText;
    private TextView clusterAlertCaptionText;
    private TextView clusterAlertText;
    private LinearLayout clusterAlertRow;
    private LinearLayout clusterNavTurnBox;
    private ImageView clusterNavTurnIconView;
    private TextView clusterNavTurnDistText;
    private TextView clusterDetailText;
    private Display clusterDisplay;
    private boolean clusterMirrorEnabled;
    private int clusterMirrorRetryCount;
    // Dynamic island fields
    private LinearLayout compactWidgetRow;
    private TextView compactNavTurnRoadText;
    private TextView compactCruiseRoadText;
    private TextView compactCruiseDirText;
    private LinearLayout clusterCompactWidgetRow;
    private TextView clusterCompactNavTurnRoadText;
    private TextView clusterCompactCruiseRoadText;
    private TextView clusterCompactCruiseDirText;
    private LinearLayout compactLaneBox;
    private LinearLayout clusterCompactLaneBox;
    // Card UI fields
    private LinearLayout cardCruiseRow1, cardCruiseRow2, cardNavArea;
    private LinearLayout clusterCardCruiseRow1, clusterCardCruiseRow2, clusterCardNavArea;
    private LaneBarView cardCruiseLaneBar, cardNavLaneBar;
    private LinearLayout cardCruiseLightRow, cardNavLightRow;
    private LinearLayout cardCruiseEdogRow, cardNavEdogRow;
    private LaneBarView clusterCardCruiseLaneBar, clusterCardNavLaneBar;
    private LinearLayout clusterCardCruiseLightRow, clusterCardNavLightRow;
    private LinearLayout clusterCardCruiseEdogRow, clusterCardNavEdogRow;
    // Dynamic island alternator fields
    private LinearLayout fullModeTurnInfoCol;
    private LinearLayout fullModeEtaInfoCol;
    private LinearLayout fullModeClusterTurnInfoCol;
    private LinearLayout fullModeClusterEtaInfoCol;
    private TextView fullModeEtaRemainDist;
    private TextView fullModeEtaArriveTime;
    private TextView fullModeClusterEtaRemainDist;
    private TextView fullModeClusterEtaArriveTime;
    private Runnable fullModeAlternator;
    private boolean fullModeShowEta = false;
    private float fullModeScale = 2f;
    private final HashMap<Integer, LightState> trafficLights = new HashMap<>();
    private final HashMap<String, Bitmap> diyArrowCache = new HashMap<>();
    private final HashMap<String, Long> diyArrowModified = new HashMap<>();
    private boolean inCruiseMode;
    private float downRawX;
    private float downRawY;
    private int downX;
    private int downY;
    private boolean dragging;
    private float clusterDownRawX;
    private float clusterDownRawY;
    private int clusterDownX;
    private int clusterDownY;
    private boolean clusterDragging;
    private String lastDetailedMode;
    private String currentModeLabel = "";
    private String currentRoadName = "";
    private String currentHeadingSummary = "";
    private String currentRoadTypeSummary = "";
    private String currentTurnLead = "";
    private String currentTurnRoad = "";
    private String currentTurnDistance = "";
    private int currentTurnIcon = 0;
    private int currentLimitSpeed = -1;
    // Overspeed warning (all UI styles)
    private int currentVehicleSpeed = -1;
    private Runnable overspeedBlinks;
    private boolean overspeedBlinkOn;
    private int overspeedColor;
    private GradientDrawable panelBackground;
    private GradientDrawable clusterPanelBackground;
    private long alertUpdatedAt;
    private int navigationTurnDir = -1;
    private Runnable mainPanelWidthUnlock;
    private Runnable clusterPanelWidthUnlock;
    private int mainPanelBaseMinWidth = -1;
    private int clusterPanelBaseMinWidth = -1;
    private int mainPanelBaseMinHeight = -1;
    private int clusterPanelBaseMinHeight = -1;
    private int mainPanelHeldMinWidth;
    private int clusterPanelHeldMinWidth;
    private int mainPanelHeldMinHeight;
    private int clusterPanelHeldMinHeight;
    private int[] lastLaneData;
    private boolean[] lastLaneAdvised;
    private float overlayScale = 2f;
    private float clusterScale = 2f;
    private float activeDensity = -1f;
    private boolean onCreateDelayed;
    private boolean targetAppForeground;
    private boolean targetBroadcastActive;
    private boolean navigationOrCruiseActive;
    private long lastNavigationSignalAt;
    private long lastTargetBroadcastAt;
    private final View.OnLayoutChangeListener clusterBoundsListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateClusterPosition();

    private final Runnable lanePoll = new Runnable() {
        @Override
        public void run() {
            if (shouldRequestAmapData()) {
                requestLaneInfo();
                requestTrafficLightInfo();
            }
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

    private final Runnable trafficLightTicker = new Runnable() {
        @Override
        public void run() {
            renderTrafficLights();
        }
    };

    private final Runnable displayPolicyPoll = new Runnable() {
        @Override
        public void run() {
            refreshDisplayPolicies();
            mainHandler.postDelayed(this, DISPLAY_POLICY_POLL_MS);
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
        stopSelfIfNoVisuals();
        if (shouldRequestAmapData()) {
            requestLaneInfo();
        }
        mainHandler.postDelayed(lanePoll, 6000L);
        mainHandler.post(displayPolicyPoll);
        onCreateDelayed = true;
        mainHandler.postDelayed(() -> {
            onCreateDelayed = false;
            ensureOverlay();
            ensureClusterMirror();
            stopSelfIfNoVisuals();
        }, 800);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!onCreateDelayed) {
            ensureOverlay();
            ensureClusterMirror();
            stopSelfIfNoVisuals();
        }
        if (shouldRequestAmapData()) {
            requestLaneInfo();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(lanePoll);
        mainHandler.removeCallbacks(alertClear);
        mainHandler.removeCallbacks(trafficLightTicker);
        mainHandler.removeCallbacks(displayPolicyPoll);
        if (mainPanelWidthUnlock != null) {
            mainHandler.removeCallbacks(mainPanelWidthUnlock);
        }
        if (clusterPanelWidthUnlock != null) {
            mainHandler.removeCallbacks(clusterPanelWidthUnlock);
        }
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
        stopFullModeAlternator();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerAmapReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SEND);
        filter.addAction(ACTION_RECV);
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
        filter.addAction(MainActivity.ACTION_CLUSTER_POSITION_CHANGED);
        filter.addAction(MainActivity.ACTION_OVERLAY_CONTENT_CHANGED);
        filter.addAction(MainActivity.ACTION_OVERLAY_STYLE_CHANGED);
        filter.addAction(MainActivity.ACTION_DISPLAY_POLICY_CHANGED);
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
        panel = buildPanelForContext(this, overlayScale, false);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = getSavedOverlayX();
        params.y = getSavedOverlayY();

        android.graphics.Point screenSize = new android.graphics.Point();
        windowManager.getDefaultDisplay().getRealSize(screenSize);
        if (screenSize.x > 0) {
            params.x = Math.max(0, Math.min(params.x, Math.max(0, screenSize.x - 100)));
        }
        if (screenSize.y > 0) {
            params.y = Math.max(0, Math.min(params.y, Math.max(0, screenSize.y - 100)));
        }

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
            Log.d(TAG, "syncMainOverlayAttachment: null check failed wm=" + (windowManager != null) + " panel=" + (panel != null) + " params=" + (params != null));
            return;
        }
        boolean enabled = (MainActivity.isMainOverlayEnabled(this)
                || shouldShowMainOverlayForTargetBroadcast())
                && !shouldHideMainOverlayForTargetForeground();
        boolean attached = panel.getParent() != null;
        Log.d(TAG, "syncMainOverlayAttachment: enabled=" + enabled + " attached=" + attached);
        if (enabled && !attached) {
            try {
                windowManager.addView(panel, params);
                Log.d(TAG, "overlay added");
                mainHandler.postDelayed(() -> {
                    if (params != null && panel != null && panel.getParent() != null) {
                        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        try {
                            windowManager.updateViewLayout(panel, params);
                        } catch (Throwable ignored) {}
                    }
                }, 4000);
            } catch (Throwable t) {
                Log.e(TAG, "overlay add failed", t);
            }
            return;
        }
        if (!enabled && attached) {
            try {
                windowManager.removeView(panel);
                Log.d(TAG, "overlay removed by preference or display policy");
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
        if (shouldHideClusterMirrorForInactiveNavigation()) {
            clusterMirrorRetryCount = 0;
            dismissClusterMirror();
            return;
        }
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
        float requestedClusterScale = MainActivity.getClusterScale(this);
        float nextClusterScale = requestedClusterScale;
        boolean scaleChanged = Math.abs(nextClusterScale - clusterScale) > 0.001f;
        clusterMirrorRetryCount = 0;
        if (clusterPanel != null && clusterDisplay != null
                && clusterDisplay.getDisplayId() == display.getDisplayId()
                && !scaleChanged) {
            updateClusterPosition();
            return;
        }
        dismissClusterMirror();
        clusterScale = nextClusterScale;
        clusterDisplay = display;
        try {
            clusterContext = createDisplayContext(display);
        } catch (Throwable t) {
            Log.e(TAG, "createDisplayContext failed", t);
            clusterContext = this;
        }
        if (clusterContext == null) {
            clusterContext = this;
        }
        clusterWindowManager = (WindowManager) clusterContext.getSystemService(WINDOW_SERVICE);
        if (clusterWindowManager == null) {
            Log.e(TAG, "cluster WindowManager is null");
            return;
        }
        clusterPanel = buildPanelForContext(clusterContext, clusterScale, true);
        clusterPanel.addOnLayoutChangeListener(clusterBoundsListener);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        clusterParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        clusterParams.gravity = Gravity.TOP | Gravity.LEFT;
        clusterParams.x = getSavedClusterX();
        clusterParams.y = getSavedClusterY();

        clusterPanel.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    clusterDownRawX = event.getRawX();
                    clusterDownRawY = event.getRawY();
                    clusterDownX = clusterParams.x;
                    clusterDownY = clusterParams.y;
                    clusterDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - clusterDownRawX) > dp(4)
                            || Math.abs(event.getRawY() - clusterDownRawY) > dp(4)) {
                        clusterDragging = true;
                    }
                    clusterParams.x = clusterDownX + Math.round(event.getRawX() - clusterDownRawX);
                    clusterParams.y = clusterDownY + Math.round(event.getRawY() - clusterDownRawY);
                    updateClusterPosition();
                    return true;
                case MotionEvent.ACTION_UP:
                    saveClusterPosition();
                    return true;
                default:
                    return true;
            }
        });

        try {
            clusterWindowManager.addView(clusterPanel, clusterParams);
            clusterPanel.post(this::updateClusterPosition);
            syncClusterFromMain();
            applyContentVisibilityPrefs();
            mainHandler.postDelayed(() -> {
                if (clusterParams != null && clusterPanel != null && clusterPanel.getParent() != null) {
                    clusterParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    try {
                        clusterWindowManager.updateViewLayout(clusterPanel, clusterParams);
                    } catch (Throwable ignored) {}
                }
            }, 4000);
            Log.d(TAG, "cluster mirror shown on display " + display.getDisplayId()
                    + ", requestedScale=" + requestedClusterScale
                    + ", appliedScale=" + clusterScale);
        } catch (Throwable t) {
            Log.e(TAG, "cluster mirror add failed", t);
            dismissClusterMirror();
        }
    }

    private boolean canUseOverlayWindowType() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        try {
            Method method = android.provider.Settings.class
                    .getMethod("canDrawOverlays", Context.class);
            Object result = method.invoke(null, this);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Display findClusterDisplay() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (manager == null) {
            return null;
        }
        int preferredDisplayId = MainActivity.getClusterDisplayId(this);
        if (preferredDisplayId >= 0) {
            Display[] displays = manager.getDisplays();
            for (Display display : displays) {
                if (display != null && display.getDisplayId() == preferredDisplayId) {
                    return display;
                }
            }
            Log.w(TAG, "preferred cluster display missing: " + preferredDisplayId);
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

    private LinearLayout buildCardPanel(Context context, float scale, boolean cluster) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padH = scaledDp(5, scale);
        int padTop = scaledDp(4, scale);
        int padBottom = scaledDp(2, scale);
        card.setPadding(padH, padTop, padH, padBottom);
        GradientDrawable bg = new GradientDrawable();
        int opacity = MainActivity.getBackgroundOpacityPercent(this);
        bg.setColor(withAlpha(0xFF111827, opacity));
        bg.setCornerRadius(scaledDp(12, scale));
        bg.setStroke(scaledDp(1, scale), withAlpha(0xFFFFFFFF, MainActivity.strokeOpacityForBackground(opacity)));
        card.setBackground(bg);

        TextView mode = new TextView(context);
        mode.setVisibility(View.GONE);
        card.addView(mode, new LinearLayout.LayoutParams(0, 0));

        // ===== CRUISE ROW 1: road + dir + edog =====
        LinearLayout cruiseRow1 = new LinearLayout(context);
        cruiseRow1.setOrientation(LinearLayout.HORIZONTAL);
        cruiseRow1.setGravity(Gravity.CENTER_VERTICAL);
        TextView roadText = new TextView(context);
        roadText.setTextColor(primaryTextColor());
        roadText.setTextSize(scaledSp(14f, scale));
        roadText.setTypeface(Typeface.DEFAULT_BOLD);
        roadText.setSingleLine(true);
        roadText.setEllipsize(TextUtils.TruncateAt.END);
        cruiseRow1.addView(roadText, new LinearLayout.LayoutParams(-2, -2));
        TextView dirText = new TextView(context);
        dirText.setTextColor(primaryTextColor());
        dirText.setTextSize(scaledSp(14f, scale));
        dirText.setTypeface(Typeface.DEFAULT_BOLD);
        dirText.setPadding(scaledDp(4, scale), 0, 0, 0);
        cruiseRow1.addView(dirText, new LinearLayout.LayoutParams(-2, -2));
        // Edog: children toggle VISIBLE/GONE based on data (TTL clearing skipped for card UI)
        LinearLayout cruiseEdog = buildEdogAlertRow(context, scale, 19);
        cruiseEdog.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams edogLp = new LinearLayout.LayoutParams(-2, -2);
        cruiseRow1.addView(cruiseEdog, edogLp);
        card.addView(cruiseRow1, new LinearLayout.LayoutParams(-2, scaledDp(32, scale)));

        // ===== CRUISE ROW 2: lanes + lights (height ratio 2) =====
        LinearLayout cruiseRow2 = new LinearLayout(context);
        cruiseRow2.setOrientation(LinearLayout.HORIZONTAL);
        cruiseRow2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout cruiseLaneBox = new LinearLayout(context);
        cruiseLaneBox.setOrientation(LinearLayout.VERTICAL);
        cruiseLaneBox.setGravity(Gravity.CENTER_HORIZONTAL);
        LaneBarView cruiseLane = new LaneBarView(context);
        cruiseLane.setFrameScaleMultiplier(scale);
        cruiseLane.setScaleMultiplier(0.9f);
        cruiseLane.setCustomHeightDp(32);
        cruiseLane.setLaneSpacingDp(2);
        cruiseLane.setShowBackground(false);
        cruiseLane.setShowDividers(false);
        cruiseLane.setCompactSpacing(true);
        cruiseLane.setUseCommonBitmapCrop(true);
        cruiseLane.setMinCellCount(1);
        cruiseLaneBox.addView(cruiseLane, new LinearLayout.LayoutParams(-2, -2));
        cruiseRow2.addView(cruiseLaneBox, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout cruiseLights = new LinearLayout(context);
        cruiseLights.setOrientation(LinearLayout.HORIZONTAL);
        cruiseLights.setGravity(Gravity.CENTER);
        cruiseRow2.addView(cruiseLights, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(-2, scaledDp(35, scale));
        r2lp.setMargins(0, scaledDp(3, scale), 0, 0);
        card.addView(cruiseRow2, r2lp);

        // ===== NAV AREA: left=icon+dist, right=2 rows =====
        LinearLayout navArea = new LinearLayout(context);
        navArea.setOrientation(LinearLayout.HORIZONTAL);
        navArea.setGravity(Gravity.CENTER_VERTICAL);
        // Left: turn icon + distance
        LinearLayout navLeft = new LinearLayout(context);
        navLeft.setOrientation(LinearLayout.VERTICAL);
        navLeft.setGravity(Gravity.CENTER);
        ImageView navIcon = new ImageView(context);
        navIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = scaledDp(44, scale);
        navLeft.addView(navIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView navDist = new TextView(context);
        navDist.setTextColor(primaryTextColor());
        navDist.setTextSize(scaledSp(16f, scale));
        navDist.setTypeface(Typeface.DEFAULT_BOLD);
        navDist.setGravity(Gravity.END);
        navLeft.addView(navDist, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(-2, -2);
        leftLp.setMargins(0, 0, scaledDp(8, scale), 0);
        navArea.addView(navLeft, leftLp);
        // Right: 2 rows vertically
        LinearLayout navRight = new LinearLayout(context);
        navRight.setOrientation(LinearLayout.VERTICAL);
        // Nav row 1: road name (left) + spacer + ETA (right-aligned with row2)
        LinearLayout navRow1 = new LinearLayout(context);
        navRow1.setOrientation(LinearLayout.HORIZONTAL);
        navRow1.setGravity(Gravity.CENTER_VERTICAL);
        TextView navRoad = new TextView(context);
        navRoad.setTextColor(primaryTextColor());
        navRoad.setTextSize(scaledSp(15f, scale));
        navRoad.setTypeface(Typeface.DEFAULT_BOLD);
        navRoad.setSingleLine(true);
        navRoad.setEllipsize(TextUtils.TruncateAt.END);
        navRow1.addView(navRoad, new LinearLayout.LayoutParams(-2, -2));
        TextView navEta = new TextView(context);
        navEta.setTextColor(primaryTextColor());
        navEta.setTextSize(scaledSp(15f, scale));
        navEta.setSingleLine(true);
        navEta.setEllipsize(TextUtils.TruncateAt.END);
        navEta.setPadding(scaledDp(6, scale), 0, 0, 0);
        navRow1.addView(navEta, new LinearLayout.LayoutParams(-2, -2));
        navRight.addView(navRow1, new LinearLayout.LayoutParams(-2, scaledDp(30, scale)));
        // Nav row 2: lanes + lights + edog
        LinearLayout navRow2 = new LinearLayout(context);
        navRow2.setOrientation(LinearLayout.HORIZONTAL);
        navRow2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout navLaneBox = new LinearLayout(context);
        navLaneBox.setOrientation(LinearLayout.VERTICAL);
        navLaneBox.setGravity(Gravity.CENTER);
        LaneBarView navLane = new LaneBarView(context);
        navLane.setFrameScaleMultiplier(scale);
        navLane.setScaleMultiplier(0.9f);
        navLane.setCustomHeightDp(36);
        navLane.setLaneSpacingDp(2);
        navLane.setShowBackground(false);
        navLane.setShowDividers(false);
        navLane.setCompactSpacing(true);
        navLane.setUseCommonBitmapCrop(true);
        navLane.setMinCellCount(1);
        navLaneBox.addView(navLane, new LinearLayout.LayoutParams(-2, -2));
        navRow2.addView(navLaneBox, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout navLights = new LinearLayout(context);
        navLights.setOrientation(LinearLayout.HORIZONTAL);
        navLights.setGravity(Gravity.CENTER);
        navRow2.addView(navLights, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout navEdog = buildEdogAlertRow(context, scale, 19);
        navEdog.setVisibility(View.VISIBLE);
        navRow2.addView(navEdog, new LinearLayout.LayoutParams(-2, -2));
        navRight.addView(navRow2, new LinearLayout.LayoutParams(-2, scaledDp(40, scale)));
        navArea.addView(navRight, new LinearLayout.LayoutParams(-2, -2));
        card.addView(navArea, new LinearLayout.LayoutParams(-2, -2));

        // Assign field references
        if (cluster) {
            clusterPanel = card;
            clusterCardCruiseRow1 = cruiseRow1;
            clusterCardCruiseRow2 = cruiseRow2;
            clusterCardNavArea = navArea;
            clusterModeText = mode;
            clusterNavTurnBox = navLeft;
            clusterNavTurnIconView = navIcon;
            clusterNavTurnDistText = navDist;
            clusterCompactCruiseDirText = dirText;
            clusterCompactCruiseRoadText = roadText;
            clusterCompactNavTurnRoadText = navRoad;
            clusterEtaText = navEta;
            clusterLaneSection = navLaneBox;
            clusterLaneBar = navLane;
            clusterLightRow = navLights;
            clusterAlertRow = navEdog;
            clusterCardCruiseLaneBar = cruiseLane;
            clusterCardCruiseLightRow = cruiseLights;
            clusterCardCruiseEdogRow = cruiseEdog;
            clusterCardNavLaneBar = navLane;
            clusterCardNavLightRow = navLights;
            clusterCardNavEdogRow = navEdog;
        } else {
            panel = card;
            cardCruiseRow1 = cruiseRow1;
            cardCruiseRow2 = cruiseRow2;
            cardNavArea = navArea;
            modeText = mode;
            navTurnBox = navLeft;
            navTurnIconView = navIcon;
            navTurnDistText = navDist;
            compactCruiseDirText = dirText;
            compactCruiseRoadText = roadText;
            compactNavTurnRoadText = navRoad;
            etaText = navEta;
            laneSection = navLaneBox;
            laneBar = navLane;
            lightRow = navLights;
            alertRow = navEdog;
            cardCruiseLaneBar = cruiseLane;
            cardCruiseLightRow = cruiseLights;
            cardCruiseEdogRow = cruiseEdog;
            cardNavLaneBar = navLane;
            cardNavLightRow = navLights;
            cardNavEdogRow = navEdog;
        }
        applyTextPalette();
        updateCardLayout();
        return card;
    }

    private LinearLayout buildClassicPanel(Context context, float scale, boolean cluster) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(scaledDp(12, scale), scaledDp(10, scale), scaledDp(12, scale), scaledDp(10, scale));
        root.setBackground(cluster ? createClusterPanelBackground() : createMainPanelBackground());

        TextView mode = new TextView(context);
        mode.setTextSize(scaledSp(13f, scale));
        mode.setSingleLine(true);
        mode.setGravity(Gravity.CENTER);
        mode.setText("待接收导航/巡航信息");
        root.addView(mode, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout turnRow = new LinearLayout(context);
        turnRow.setOrientation(LinearLayout.VERTICAL);
        turnRow.setGravity(Gravity.CENTER);
        turnRow.setPadding(scaledDp(14, scale), scaledDp(8, scale), scaledDp(16, scale), scaledDp(9, scale));
        GradientDrawable turnBg = new GradientDrawable();
        turnBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        turnBg.setColors(new int[]{0xFF1D4ED8, 0xFF0891B2});
        turnBg.setCornerRadius(scaledDp(10, scale));
        turnRow.setBackground(turnBg);
        turnRow.setVisibility(View.GONE);
        turnRow.setMinimumHeight(scaledDp(62, scale));

        TextView turn = new TextView(context);
        turn.setTextColor(Color.WHITE);
        turn.setTextSize(scaledSp(22f, scale));
        turn.setTypeface(Typeface.DEFAULT_BOLD);
        turn.setSingleLine(true);
        turn.setMaxLines(1);
        turn.setEllipsize(TextUtils.TruncateAt.END);
        turn.setGravity(Gravity.CENTER);
        turnRow.addView(turn, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout turnDetailRow = new LinearLayout(context);
        turnDetailRow.setOrientation(LinearLayout.HORIZONTAL);
        turnDetailRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailRowLp = new LinearLayout.LayoutParams(-2, -2);
        detailRowLp.setMargins(0, scaledDp(4, scale), 0, 0);
        turnRow.addView(turnDetailRow, detailRowLp);

        ImageView turnIcon = new ImageView(context);
        turnIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        turnIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams turnIconLp = new LinearLayout.LayoutParams(scaledDp(30, scale), scaledDp(30, scale));
        turnIconLp.setMargins(0, 0, scaledDp(7, scale), 0);
        turnDetailRow.addView(turnIcon, turnIconLp);

        TextView turnDistance = new TextView(context);
        turnDistance.setTextColor(Color.WHITE);
        turnDistance.setTextSize(scaledSp(22f, scale));
        turnDistance.setTypeface(Typeface.DEFAULT_BOLD);
        turnDistance.setGravity(Gravity.CENTER);
        turnDetailRow.addView(turnDistance, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams turnRowLp = new LinearLayout.LayoutParams(-2, -2);
        turnRowLp.setMargins(0, scaledDp(6, scale), 0, scaledDp(5, scale));
        root.addView(turnRow, turnRowLp);

        LinearLayout laneBox = new LinearLayout(context);
        laneBox.setOrientation(LinearLayout.VERTICAL);
        laneBox.setGravity(Gravity.CENTER_HORIZONTAL);
        laneBox.setPadding(scaledDp(8, scale), scaledDp(5, scale), scaledDp(8, scale), scaledDp(7, scale));
        laneBox.setVisibility(View.GONE);

        LaneBarView lane = new LaneBarView(context);
        lane.setFrameScaleMultiplier(scale);
        lane.setScaleMultiplier(1.5f);
        LinearLayout.LayoutParams laneLp = new LinearLayout.LayoutParams(-2, -2);
        laneLp.setMargins(0, 0, 0, 0);
        laneBox.addView(lane, laneLp);
        LinearLayout.LayoutParams laneSectionLp = new LinearLayout.LayoutParams(-2, -2);
        laneSectionLp.setMargins(0, scaledDp(5, scale), 0, scaledDp(4, scale));
        root.addView(laneBox, laneSectionLp);

        LinearLayout lights = new LinearLayout(context);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setGravity(Gravity.CENTER);
        lights.setVisibility(View.GONE);
        root.addView(lights, new LinearLayout.LayoutParams(-2, -2));

        TextView eta = new TextView(context);
        eta.setTextSize(scaledSp(15f, scale));
        eta.setSingleLine(false);
        eta.setMaxLines(4);
        eta.setGravity(Gravity.CENTER);
        eta.setVisibility(View.GONE);
        root.addView(eta, new LinearLayout.LayoutParams(-2, -2));

        TextView alert = compactText(context, 14f, false, scale);
        alert.setVisibility(View.GONE);
        root.addView(alert, new LinearLayout.LayoutParams(-2, 0));

        LinearLayout edogAlertRow = buildEdogAlertRow(context, scale);
        edogAlertRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams alertLp = new LinearLayout.LayoutParams(-2, -2);
        alertLp.setMargins(0, scaledDp(5, scale), 0, 0);
        root.addView(edogAlertRow, alertLp);

        TextView detail = compactText(context, 12f, true, scale);
        detail.setMaxLines(4);
        detail.setVisibility(View.GONE);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-2, -2);
        detailLp.setMargins(0, scaledDp(3, scale), 0, 0);
        root.addView(detail, detailLp);

        if (cluster) {
            clusterPanel = root;
            clusterModeRow = null;
            clusterModeText = mode;
            clusterTitleText = null;
            clusterSummaryDivider = null;
            clusterSummaryRow = null;
            clusterHeadingInfoText = null;
            clusterRoadInfoText = null;
            clusterTurnCard = null;
            clusterTurnLeadText = null;
            clusterTurnText = turn;
            clusterTurnDistanceText = turnDistance;
            clusterTurnIconView = turnIcon;
            clusterTurnRowLayout = turnRow;
            clusterLaneSection = laneBox;
            clusterLaneBar = lane;
            clusterLightRow = lights;
            clusterEtaText = eta;
            clusterAlertCard = null;
            clusterLimitBadgeText = null;
            clusterAlertCaptionText = null;
            clusterAlertText = alert;
            clusterAlertRow = edogAlertRow;
            clusterDetailText = detail;
        } else {
            panel = root;
            modeRow = null;
            modeText = mode;
            titleText = null;
            summaryDivider = null;
            summaryRow = null;
            headingInfoText = null;
            roadInfoText = null;
            turnCard = null;
            turnLeadText = null;
            turnText = turn;
            turnDistanceText = turnDistance;
            turnIconView = turnIcon;
            turnRowLayout = turnRow;
            laneSection = laneBox;
            laneBar = lane;
            lightRow = lights;
            etaText = eta;
            alertCard = null;
            limitBadgeText = null;
            alertCaptionText = null;
            alertText = alert;
            alertRow = edogAlertRow;
            detailText = detail;
        }

        applyTextPalette();
        return root;
    }

    private LinearLayout buildDashboardPanel(Context context, float scale, boolean cluster) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(scaledDp(cluster ? 12 : 14, scale), scaledDp(cluster ? 8 : 10, scale),
                scaledDp(cluster ? 12 : 14, scale), scaledDp(cluster ? 8 : 10, scale));
        root.setMinimumWidth(scaledDp(cluster ? 300 : 314, scale));
        root.setBackground(cluster ? createClusterPanelBackground() : createMainPanelBackground());

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-2, -2));

        TextView badge = circleBadge(context, "↗", scale, 0xFF3B82F6, 0xCC0A1422);
        header.addView(badge, new LinearLayout.LayoutParams(scaledDp(32, scale), scaledDp(32, scale)));

        TextView mode = new TextView(context);
        mode.setText("待接收导航/巡航信息");
        mode.setTextSize(scaledSp(13f, scale));
        mode.setSingleLine(true);
        mode.setEllipsize(TextUtils.TruncateAt.END);
        mode.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(-2, -2);
        modeLp.setMargins(scaledDp(12, scale), 0, 0, 0);
        header.addView(mode, modeLp);

        TextView title = new TextView(context);
        title.setText("待命");
        title.setTextSize(scaledSp(cluster ? 24f : 26f, scale));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.setMargins(0, scaledDp(cluster ? 10 : 12, scale), 0, scaledDp(cluster ? 6 : 8, scale));
        root.addView(title, titleLp);


        View divider = new View(context);
        divider.setBackgroundColor(withAlpha(0xFFFFFFFF, 12));
        divider.setVisibility(View.GONE);
        root.addView(divider, new LinearLayout.LayoutParams(scaledDp(220, scale), scaledDp(1, scale)));

        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setBackground(createSectionBackground(scale));
        summary.setPadding(scaledDp(14, scale), scaledDp(12, scale), scaledDp(14, scale), scaledDp(12, scale));
        summary.setVisibility(View.GONE);
        root.addView(summary, sectionLp(scale, cluster ? 5f : 9f));

        TextView heading = infoBlockText(context, "车头\n--", scale);
        summary.addView(heading, new LinearLayout.LayoutParams(-2, -2));

        View summaryMid = new View(context);
        summaryMid.setBackgroundColor(withAlpha(0xFFFFFFFF, 10));
        LinearLayout.LayoutParams summaryMidLp = new LinearLayout.LayoutParams(scaledDp(1, scale), scaledDp(42, scale));
        summaryMidLp.setMargins(scaledDp(12, scale), 0, scaledDp(12, scale), 0);
        summary.addView(summaryMid, summaryMidLp);

        TextView roadInfo = infoBlockText(context, "道路\n未透出", scale);
        summary.addView(roadInfo, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout turnBox = new LinearLayout(context);
        turnBox.setOrientation(LinearLayout.HORIZONTAL);
        turnBox.setGravity(Gravity.CENTER_VERTICAL);
        turnBox.setBackground(createSectionBackground(scale));
        turnBox.setPadding(scaledDp(cluster ? 14 : 16, scale), scaledDp(cluster ? 10 : 12, scale),
                scaledDp(cluster ? 14 : 16, scale), scaledDp(cluster ? 10 : 12, scale));
        turnBox.setVisibility(View.GONE);
        root.addView(turnBox, sectionLp(scale, cluster ? 5f : 9f));

        LinearLayout turnLeft = new LinearLayout(context);
        turnLeft.setOrientation(LinearLayout.VERTICAL);
        turnBox.addView(turnLeft, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout turnLeadRow = new LinearLayout(context);
        turnLeadRow.setOrientation(LinearLayout.HORIZONTAL);
        turnLeadRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView turnLeadIcon = new ImageView(context);
        turnLeadIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        turnLeadIcon.setVisibility(View.GONE);
        int turnLeadIconSize = scaledDp(24, scale);
        turnLeadRow.addView(turnLeadIcon, new LinearLayout.LayoutParams(turnLeadIconSize, turnLeadIconSize));
        TextView turnLead = new TextView(context);
        turnLead.setText("下一路口");
        turnLead.setTextSize(scaledSp(13f, scale));
        turnLead.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams turnLeadTextLp = new LinearLayout.LayoutParams(-2, -2);
        turnLeadTextLp.setMargins(scaledDp(3, scale), 0, 0, 0);
        turnLeadRow.addView(turnLead, turnLeadTextLp);
        LinearLayout.LayoutParams turnLeadRowLp = new LinearLayout.LayoutParams(-2, -2);
        turnLeadRowLp.setMargins(0, 0, 0, scaledDp(1, scale));
        turnLeft.addView(turnLeadRow, turnLeadRowLp);

        TextView turnRoad = new TextView(context);
        turnRoad.setTextSize(scaledSp(cluster ? 22f : 24f, scale));
        turnRoad.setTypeface(Typeface.DEFAULT_BOLD);
        turnRoad.setMaxLines(2);
        LinearLayout.LayoutParams turnRoadLp = new LinearLayout.LayoutParams(-2, -2);
        turnRoadLp.setMargins(0, scaledDp(6, scale), 0, 0);
        turnLeft.addView(turnRoad, turnRoadLp);

        TextView turnDistance = new TextView(context);
        turnDistance.setTextSize(scaledSp(cluster ? 22f : 24f, scale));
        turnDistance.setTypeface(Typeface.DEFAULT_BOLD);
        turnDistance.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams turnDistanceLp = new LinearLayout.LayoutParams(-2, -2);
        turnDistanceLp.setMargins(scaledDp(18, scale), 0, 0, 0);
        turnBox.addView(turnDistance, turnDistanceLp);

        LinearLayout laneBox = new LinearLayout(context);
        laneBox.setOrientation(LinearLayout.VERTICAL);
        laneBox.setGravity(Gravity.CENTER_HORIZONTAL);
        laneBox.setPadding(scaledDp(10, scale), scaledDp(7, scale), scaledDp(10, scale), scaledDp(8, scale));
        laneBox.setVisibility(View.GONE);
        root.addView(laneBox, sectionLp(scale, cluster ? 5f : 8f));

        LaneBarView lane = new LaneBarView(context);
        lane.setFrameScaleMultiplier(scale);
        lane.setScaleMultiplier(1.5f);
        LinearLayout.LayoutParams laneLp = new LinearLayout.LayoutParams(-2, -2);
        laneLp.setMargins(0, 0, 0, 0);
        laneBox.addView(lane, laneLp);

        LinearLayout lights = new LinearLayout(context);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setGravity(Gravity.CENTER);
        lights.setVisibility(View.GONE);
        root.addView(lights, sectionLp(scale, cluster ? 5f : 6f));

        TextView eta = new TextView(context);
        eta.setTextSize(scaledSp(14f, scale));
        eta.setSingleLine(false);
        eta.setMaxLines(4);
        eta.setGravity(Gravity.CENTER);
        eta.setPadding(scaledDp(12, scale), scaledDp(8, scale), scaledDp(12, scale), scaledDp(8, scale));
        eta.setBackground(createSectionBackground(scale));
        eta.setVisibility(View.GONE);
        root.addView(eta, sectionLp(scale, cluster ? 5f : 8f));

        LinearLayout alertBox = new LinearLayout(context);
        alertBox.setOrientation(LinearLayout.HORIZONTAL);
        alertBox.setGravity(Gravity.CENTER_VERTICAL);
        alertBox.setPadding(scaledDp(14, scale), scaledDp(10, scale), scaledDp(14, scale), scaledDp(10, scale));
        alertBox.setBackground(createSectionBackground(scale));
        alertBox.setVisibility(View.GONE);
        root.addView(alertBox, sectionLp(scale, cluster ? 5f : 8f));

        LinearLayout edogAlertRow = buildEdogAlertRow(context, scale);
        alertBox.addView(edogAlertRow, new LinearLayout.LayoutParams(-2, -2));

        TextView limitBadge = speedBadge(context, "--", scale);
        limitBadge.setVisibility(View.GONE);
        alertBox.addView(limitBadge, new LinearLayout.LayoutParams(0, 0));

        LinearLayout alertRight = new LinearLayout(context);
        alertRight.setOrientation(LinearLayout.VERTICAL);
        alertBox.addView(alertRight, new LinearLayout.LayoutParams(0, 0));

        TextView alertCaption = new TextView(context);
        alertCaption.setText("道路提醒");
        alertCaption.setTextSize(scaledSp(12f, scale));
        alertCaption.setTypeface(Typeface.DEFAULT_BOLD);
        alertCaption.setTextColor(0xFFCBD5E1);
        alertCaption.setVisibility(View.GONE);
        alertRight.addView(alertCaption, new LinearLayout.LayoutParams(-2, -2));

        TextView alert = compactText(context, 14f, false, scale);
        alert.setGravity(Gravity.START);
        alert.setPadding(0, scaledDp(4, scale), 0, 0);
        alert.setMaxLines(3);
        alert.setVisibility(View.GONE);
        alertRight.addView(alert, new LinearLayout.LayoutParams(-2, -2));

        TextView detail = compactText(context, 12f, true, scale);
        detail.setMaxLines(4);
        detail.setPadding(scaledDp(12, scale), scaledDp(8, scale), scaledDp(12, scale), scaledDp(8, scale));
        detail.setBackground(createSectionBackground(scale));
        detail.setVisibility(View.GONE);
        root.addView(detail, sectionLp(scale, cluster ? 5f : 6f));

        if (cluster) {
            clusterPanel = root;
            clusterModeRow = header;
            clusterModeText = mode;
            clusterTitleText = title;
            clusterSummaryDivider = divider;
            clusterSummaryRow = summary;
            clusterHeadingInfoText = heading;
            clusterRoadInfoText = roadInfo;
            clusterTurnCard = turnBox;
            clusterTurnLeadText = turnLead;
            clusterTurnLeadIconView = turnLeadIcon;
            clusterTurnText = turnRoad;
            clusterTurnDistanceText = turnDistance;
            clusterLaneSection = laneBox;
            clusterLaneBar = lane;
            clusterLightRow = lights;
            clusterEtaText = eta;
            clusterAlertCard = alertBox;
            clusterLimitBadgeText = limitBadge;
            clusterAlertCaptionText = alertCaption;
            clusterAlertText = alert;
            clusterAlertRow = edogAlertRow;
            clusterDetailText = detail;
        } else {
            panel = root;
            modeRow = header;
            modeText = mode;
            titleText = title;
            summaryDivider = divider;
            summaryRow = summary;
            headingInfoText = heading;
            roadInfoText = roadInfo;
            turnCard = turnBox;
            turnLeadText = turnLead;
            turnLeadIconView = turnLeadIcon;
            turnText = turnRoad;
            turnDistanceText = turnDistance;
            laneSection = laneBox;
            laneBar = lane;
            lightRow = lights;
            etaText = eta;
            alertCard = alertBox;
            limitBadgeText = limitBadge;
            alertCaptionText = alertCaption;
            alertText = alert;
            alertRow = edogAlertRow;
            detailText = detail;
        }

        applyTextPalette();
        refreshRoadTitle();
        refreshStatusSummary();
        refreshTurnCard();
        refreshAlertCard();
        return root;
    }

    private LinearLayout buildDynamicIslandPanel(Context context, float scale, boolean cluster) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(scaledDp(10, scale), scaledDp(7, scale), scaledDp(12, scale), scaledDp(7, scale));
        root.setMinimumHeight(scaledDp(48, scale));
        root.setBackground(createDynamicIslandBackground(scale));

        TextView mode = new TextView(context);
        mode.setVisibility(View.GONE);
        root.addView(mode, new LinearLayout.LayoutParams(0, 0));

        LinearLayout navTurn = new LinearLayout(context);
        navTurn.setOrientation(LinearLayout.HORIZONTAL);
        navTurn.setGravity(Gravity.CENTER_VERTICAL);
        navTurn.setVisibility(View.GONE);
        ImageView navIcon = new ImageView(context);
        navIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int navIconSize = scaledDp(28, scale);
        navTurn.addView(navIcon, new LinearLayout.LayoutParams(navIconSize, navIconSize));
        TextView navDist = new TextView(context);
        navDist.setTextColor(primaryTextColor());
        navDist.setTextSize(scaledSp(14f, scale));
        navDist.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams navDistLp = new LinearLayout.LayoutParams(-2, -2);
        navDistLp.setMargins(scaledDp(4, scale), 0, scaledDp(8, scale), 0);
        navTurn.addView(navDist, navDistLp);
        root.addView(navTurn, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout laneBox = new LinearLayout(context);
        laneBox.setOrientation(LinearLayout.VERTICAL);
        laneBox.setGravity(Gravity.CENTER_HORIZONTAL);
        laneBox.setPadding(scaledDp(3, scale), scaledDp(1, scale), scaledDp(3, scale), scaledDp(1, scale));
        laneBox.setVisibility(View.GONE);
        LaneBarView lane = new LaneBarView(context);
        lane.setFrameScaleMultiplier(scale);
        lane.setScaleMultiplier(0.82f);
        lane.setCompactSpacing(true);
        laneBox.addView(lane, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams laneBoxLp = new LinearLayout.LayoutParams(-2, -2);
        laneBoxLp.setMargins(0, 0, scaledDp(6, scale), 0);
        root.addView(laneBox, laneBoxLp);

        LinearLayout lights = new LinearLayout(context);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setGravity(Gravity.CENTER);
        lights.setVisibility(View.GONE);
        root.addView(lights, new LinearLayout.LayoutParams(-2, -2));

        TextView turn = new TextView(context);
        turn.setVisibility(View.GONE);
        root.addView(turn, new LinearLayout.LayoutParams(0, 0));

        TextView alert = new TextView(context);
        alert.setVisibility(View.GONE);
        root.addView(alert, new LinearLayout.LayoutParams(0, 0));

        if (cluster) {
            clusterPanel = root;
            clusterModeRow = null;
            clusterModeText = mode;
            clusterTitleText = null;
            clusterSummaryDivider = null;
            clusterSummaryRow = null;
            clusterHeadingInfoText = null;
            clusterRoadInfoText = null;
            clusterTurnCard = null;
            clusterTurnLeadText = null;
            clusterTurnText = turn;
            clusterTurnDistanceText = null;
            clusterTurnIconView = null;
            clusterTurnDistBadge = null;
            clusterTurnRowLayout = null;
            clusterNavTurnBox = navTurn;
            clusterNavTurnIconView = navIcon;
            clusterNavTurnDistText = navDist;
            clusterLaneSection = laneBox;
            clusterLaneBar = lane;
            clusterLightRow = lights;
            clusterEtaText = null;
            clusterAlertCard = null;
            clusterLimitBadgeText = null;
            clusterAlertCaptionText = null;
            clusterAlertText = alert;
            clusterAlertRow = null;
            clusterDetailText = null;
        } else {
            panel = root;
            modeRow = null;
            modeText = mode;
            titleText = null;
            summaryDivider = null;
            summaryRow = null;
            headingInfoText = null;
            roadInfoText = null;
            turnCard = null;
            turnLeadText = null;
            turnText = turn;
            turnDistanceText = null;
            turnIconView = null;
            turnDistBadge = null;
            turnRowLayout = null;
            navTurnBox = navTurn;
            navTurnIconView = navIcon;
            navTurnDistText = navDist;
            laneSection = laneBox;
            laneBar = lane;
            lightRow = lights;
            etaText = null;
            alertCard = null;
            limitBadgeText = null;
            alertCaptionText = null;
            alertText = alert;
            alertRow = null;
            detailText = null;
        }

        applyTextPalette();
        refreshTurnCard();
        return root;
    }

    private GradientDrawable createDynamicIslandBackground(float scale) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(scaledDp(999, scale));
        int opacity = MainActivity.getBackgroundOpacityPercent(this);
        bg.setColor(withAlpha(0xFF111827, opacity));
        bg.setStroke(scaledDp(1, scale), withAlpha(0xFFFFFFFF, MainActivity.strokeOpacityForBackground(opacity)));
        return bg;
    }

    private LinearLayout buildDynamicIslandFullPanel(Context context, float scale, boolean cluster) {
        if (cluster) {
            return buildDynamicIslandFullClusterPanel(context, scale);
        }
        fullModeScale = scale;
        int fullInfoWidth = scaledDp(56, scale);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createDynamicIslandBackground(scale));

        // Content row
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(scaledDp(6, scale), scaledDp(3, scale), scaledDp(6, scale), scaledDp(3, scale));
        content.setMinimumHeight(scaledDp(42, scale));

        // Mode tag (hidden)
        TextView mode = new TextView(context);
        mode.setVisibility(View.GONE);
        content.addView(mode, new LinearLayout.LayoutParams(0, 0));

        // --- Nav left section (direction + distance + road name + ETA info) ---
        LinearLayout navTurn = new LinearLayout(context);
        navTurn.setOrientation(LinearLayout.HORIZONTAL);
        navTurn.setGravity(Gravity.CENTER_VERTICAL);
        navTurn.setVisibility(View.GONE);

        ImageView navIcon = new ImageView(context);
        navIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int navIconSize = scaledDp(28, scale);
        LinearLayout.LayoutParams navIconLp = new LinearLayout.LayoutParams(navIconSize, navIconSize);
        navIconLp.setMargins(scaledDp(10, scale), 0, 0, 0);
        navTurn.addView(navIcon, navIconLp);

        // Turn info column (distance + road name)
        LinearLayout distRoadCol = new LinearLayout(context);
        distRoadCol.setOrientation(LinearLayout.VERTICAL);
        distRoadCol.setGravity(Gravity.CENTER);
        TextView navDist = new TextView(context);
        navDist.setTextColor(primaryTextColor());
        navDist.setTextSize(scaledSp(14f, scale));
        navDist.setTypeface(Typeface.DEFAULT_BOLD);
        navDist.setGravity(Gravity.CENTER);
        distRoadCol.addView(navDist, new LinearLayout.LayoutParams(-1, -2));

        TextView navRoad = new TextView(context);
        navRoad.setTextColor(primaryTextColor());
        navRoad.setTextSize(scaledSp(10f, scale));
        navRoad.setSingleLine(true);
        navRoad.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        navRoad.setMarqueeRepeatLimit(-1);
        navRoad.setHorizontallyScrolling(true);
        navRoad.setFocusable(true);
        navRoad.setFocusableInTouchMode(true);
        navRoad.setSelected(true);
        navRoad.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams navRoadLp = new LinearLayout.LayoutParams(-1, -2);
        navRoadLp.setMargins(0, scaledDp(1, scale), 0, 0);
        distRoadCol.addView(navRoad, navRoadLp);

        LinearLayout.LayoutParams distRoadColLp = new LinearLayout.LayoutParams(fullInfoWidth, -2);
        distRoadColLp.setMargins(scaledDp(3, scale), 0, scaledDp(3, scale), 0);
        navTurn.addView(distRoadCol, distRoadColLp);

        // ETA info column (remaining distance + arrival time)
        LinearLayout etaInfoCol = new LinearLayout(context);
        etaInfoCol.setOrientation(LinearLayout.VERTICAL);
        etaInfoCol.setGravity(Gravity.CENTER);
        etaInfoCol.setVisibility(View.GONE);

        TextView etaRemainDist = new TextView(context);
        etaRemainDist.setTextColor(primaryTextColor());
        etaRemainDist.setTextSize(scaledSp(14f, scale));
        etaRemainDist.setTypeface(Typeface.DEFAULT_BOLD);
        etaRemainDist.setGravity(Gravity.CENTER);
        etaInfoCol.addView(etaRemainDist, new LinearLayout.LayoutParams(-1, -2));

        TextView etaArriveTime = new TextView(context);
        etaArriveTime.setTextColor(primaryTextColor());
        etaArriveTime.setTextSize(scaledSp(10f, scale));
        etaArriveTime.setTypeface(Typeface.DEFAULT_BOLD);
        etaArriveTime.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etaArriveLp = new LinearLayout.LayoutParams(-1, -2);
        etaArriveLp.setMargins(0, scaledDp(1, scale), 0, 0);
        etaInfoCol.addView(etaArriveTime, etaArriveLp);

        LinearLayout.LayoutParams etaInfoColLp = new LinearLayout.LayoutParams(fullInfoWidth, -2);
        etaInfoColLp.setMargins(scaledDp(3, scale), 0, scaledDp(3, scale), 0);
        navTurn.addView(etaInfoCol, etaInfoColLp);

        content.addView(navTurn, new LinearLayout.LayoutParams(-2, -2));

        // --- Cruise left section (road name + direction) ---
        LinearLayout cruiseLeft = new LinearLayout(context);
        cruiseLeft.setOrientation(LinearLayout.VERTICAL);
        cruiseLeft.setGravity(Gravity.CENTER);
        cruiseLeft.setVisibility(View.GONE);
        TextView cruiseRoad = new TextView(context);
        cruiseRoad.setTextColor(primaryTextColor());
        cruiseRoad.setTextSize(scaledSp(10f, scale));
        cruiseRoad.setTypeface(Typeface.DEFAULT_BOLD);
        cruiseRoad.setSingleLine(true);
        cruiseRoad.setEllipsize(TextUtils.TruncateAt.END);
        cruiseRoad.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cruiseRoadLp = new LinearLayout.LayoutParams(-1, -2);
        cruiseLeft.addView(cruiseRoad, cruiseRoadLp);
        TextView cruiseDir = new TextView(context);
        cruiseDir.setTextColor(primaryTextColor());
        cruiseDir.setTextSize(scaledSp(10f, scale));
        cruiseDir.setTypeface(Typeface.DEFAULT_BOLD);
        cruiseDir.setSingleLine(true);
        cruiseDir.setGravity(Gravity.CENTER);
        cruiseLeft.addView(cruiseDir, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams cruiseLeftLp = new LinearLayout.LayoutParams(fullInfoWidth, -2);
        cruiseLeftLp.setMargins(0, 0, scaledDp(4, scale), 0);
        content.addView(cruiseLeft, cruiseLeftLp);

        // --- Lane section ---
        LinearLayout laneBox = new LinearLayout(context);
        laneBox.setOrientation(LinearLayout.VERTICAL);
        laneBox.setGravity(Gravity.CENTER_HORIZONTAL);
        laneBox.setVisibility(View.GONE);
        LaneBarView lane = new LaneBarView(context);
        float compactLaneScale = scale;
        lane.setFrameScaleMultiplier(compactLaneScale);
        lane.setScaleMultiplier(0.9f);
        lane.setCustomHeightDp(36);
        lane.setLaneSpacingDp(2);
        lane.setShowBackground(false);
        lane.setShowDividers(false);
        lane.setCompactSpacing(true);
        lane.setUseCommonBitmapCrop(true);
        lane.setMinCellCount(1);
        laneBox.addView(lane, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams laneBoxLp = new LinearLayout.LayoutParams(-2, -2);
        laneBoxLp.setMargins(0, 0, scaledDp(2, scale), 0);
        content.addView(laneBox, laneBoxLp);

        // --- Light row ---
        LinearLayout lights = new LinearLayout(context);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setGravity(Gravity.CENTER);
        lights.setVisibility(View.GONE);
        content.addView(lights, new LinearLayout.LayoutParams(-2, -2));

        // --- Widget row ---
        LinearLayout widgetRow = new LinearLayout(context);
        widgetRow.setOrientation(LinearLayout.HORIZONTAL);
        widgetRow.setGravity(Gravity.CENTER_VERTICAL);
        widgetRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams widgetRowLp = new LinearLayout.LayoutParams(-2, -2);
        widgetRowLp.setMargins(scaledDp(3, scale), 0, 0, 0);
        content.addView(widgetRow, widgetRowLp);

        root.addView(content);

        // --- Field assignments ---
        panel = root;
        modeText = mode;
        compactWidgetRow = widgetRow;
        compactNavTurnRoadText = navRoad;
        compactCruiseRoadText = cruiseRoad;
        compactCruiseDirText = cruiseDir;
        compactLaneBox = laneBox;
        fullModeTurnInfoCol = distRoadCol;
        fullModeEtaInfoCol = etaInfoCol;
        fullModeEtaRemainDist = etaRemainDist;
        fullModeEtaArriveTime = etaArriveTime;

        navTurnBox = navTurn;
        navTurnIconView = navIcon;
        navTurnDistText = navDist;
        laneSection = laneBox;
        laneBar = lane;
        lightRow = lights;

        // Null out unused fields
        turnCard = null;
        turnText = null;
        turnDistanceText = null;
        turnIconView = null;
        turnDistBadge = null;
        turnRowLayout = null;
        modeRow = null;
        titleText = null;
        summaryDivider = null;
        summaryRow = null;
        headingInfoText = null;
        roadInfoText = null;
        etaText = null;
        alertCard = null;
        limitBadgeText = null;
        alertCaptionText = null;
        alertText = null;
        alertRow = null;
        detailText = null;
        turnLeadText = null;
        turnLeadIconView = null;

        applyTextPalette();
        refreshTurnCard();
        return root;
    }

    private LinearLayout buildDynamicIslandFullClusterPanel(Context context, float scale) {
        int fullInfoWidth = scaledDp(56, scale);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(createDynamicIslandBackground(scale));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(scaledDp(6, scale), scaledDp(3, scale), scaledDp(6, scale), scaledDp(3, scale));
        content.setMinimumHeight(scaledDp(42, scale));

        TextView mode = new TextView(context);
        mode.setVisibility(View.GONE);
        content.addView(mode, new LinearLayout.LayoutParams(0, 0));

        LinearLayout navTurn = new LinearLayout(context);
        navTurn.setOrientation(LinearLayout.HORIZONTAL);
        navTurn.setGravity(Gravity.CENTER_VERTICAL);
        navTurn.setVisibility(View.GONE);

        ImageView navIcon = new ImageView(context);
        navIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int navIconSize = scaledDp(28, scale);
        LinearLayout.LayoutParams navIconLp = new LinearLayout.LayoutParams(navIconSize, navIconSize);
        navIconLp.setMargins(scaledDp(10, scale), 0, 0, 0);
        navTurn.addView(navIcon, navIconLp);

        // Turn info column
        LinearLayout distRoadCol = new LinearLayout(context);
        distRoadCol.setOrientation(LinearLayout.VERTICAL);
        distRoadCol.setGravity(Gravity.CENTER);
        TextView navDist = new TextView(context);
        navDist.setTextColor(primaryTextColor());
        navDist.setTextSize(scaledSp(14f, scale));
        navDist.setTypeface(Typeface.DEFAULT_BOLD);
        navDist.setGravity(Gravity.CENTER);
        distRoadCol.addView(navDist, new LinearLayout.LayoutParams(-1, -2));

        TextView navRoad = new TextView(context);
        navRoad.setTextColor(primaryTextColor());
        navRoad.setTextSize(scaledSp(10f, scale));
        navRoad.setSingleLine(true);
        navRoad.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        navRoad.setMarqueeRepeatLimit(-1);
        navRoad.setHorizontallyScrolling(true);
        navRoad.setFocusable(true);
        navRoad.setFocusableInTouchMode(true);
        navRoad.setSelected(true);
        navRoad.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams navRoadLp = new LinearLayout.LayoutParams(-1, -2);
        navRoadLp.setMargins(0, scaledDp(1, scale), 0, 0);
        distRoadCol.addView(navRoad, navRoadLp);

        LinearLayout.LayoutParams distRoadColLp = new LinearLayout.LayoutParams(fullInfoWidth, -2);
        distRoadColLp.setMargins(scaledDp(3, scale), 0, scaledDp(3, scale), 0);
        navTurn.addView(distRoadCol, distRoadColLp);

        // ETA info column
        LinearLayout etaInfoCol = new LinearLayout(context);
        etaInfoCol.setOrientation(LinearLayout.VERTICAL);
        etaInfoCol.setGravity(Gravity.CENTER);
        etaInfoCol.setVisibility(View.GONE);

        TextView etaRemainDist = new TextView(context);
        etaRemainDist.setTextColor(primaryTextColor());
        etaRemainDist.setTextSize(scaledSp(14f, scale));
        etaRemainDist.setTypeface(Typeface.DEFAULT_BOLD);
        etaRemainDist.setGravity(Gravity.CENTER);
        etaInfoCol.addView(etaRemainDist, new LinearLayout.LayoutParams(-1, -2));

        TextView etaArriveTime = new TextView(context);
        etaArriveTime.setTextColor(primaryTextColor());
        etaArriveTime.setTextSize(scaledSp(10f, scale));
        etaArriveTime.setTypeface(Typeface.DEFAULT_BOLD);
        etaArriveTime.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etaArriveLp = new LinearLayout.LayoutParams(-1, -2);
        etaArriveLp.setMargins(0, scaledDp(1, scale), 0, 0);
        etaInfoCol.addView(etaArriveTime, etaArriveLp);

        LinearLayout.LayoutParams etaInfoColLp = new LinearLayout.LayoutParams(fullInfoWidth, -2);
        etaInfoColLp.setMargins(scaledDp(3, scale), 0, scaledDp(3, scale), 0);
        navTurn.addView(etaInfoCol, etaInfoColLp);

        content.addView(navTurn, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout cruiseLeft = new LinearLayout(context);
        cruiseLeft.setOrientation(LinearLayout.VERTICAL);
        cruiseLeft.setGravity(Gravity.CENTER);
        cruiseLeft.setVisibility(View.GONE);
        TextView cruiseRoad = new TextView(context);
        cruiseRoad.setTextColor(primaryTextColor());
        cruiseRoad.setTextSize(scaledSp(10f, scale));
        cruiseRoad.setTypeface(Typeface.DEFAULT_BOLD);
        cruiseRoad.setSingleLine(true);
        cruiseRoad.setEllipsize(TextUtils.TruncateAt.END);
        cruiseRoad.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cruiseRoadLp = new LinearLayout.LayoutParams(-1, -2);
        cruiseLeft.addView(cruiseRoad, cruiseRoadLp);
        TextView cruiseDir = new TextView(context);
        cruiseDir.setTextColor(primaryTextColor());
        cruiseDir.setTextSize(scaledSp(10f, scale));
        cruiseDir.setTypeface(Typeface.DEFAULT_BOLD);
        cruiseDir.setSingleLine(true);
        cruiseDir.setGravity(Gravity.CENTER);
        cruiseLeft.addView(cruiseDir, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams cruiseLeftLp = new LinearLayout.LayoutParams(fullInfoWidth, -2);
        cruiseLeftLp.setMargins(0, 0, scaledDp(4, scale), 0);
        content.addView(cruiseLeft, cruiseLeftLp);

        LinearLayout laneBox = new LinearLayout(context);
        laneBox.setOrientation(LinearLayout.VERTICAL);
        laneBox.setGravity(Gravity.CENTER_HORIZONTAL);
        laneBox.setVisibility(View.GONE);
        LaneBarView lane = new LaneBarView(context);
        lane.setFrameScaleMultiplier(scale);
        lane.setScaleMultiplier(0.9f);
        lane.setCustomHeightDp(36);
        lane.setLaneSpacingDp(2);
        lane.setShowBackground(false);
        lane.setShowDividers(false);
        lane.setCompactSpacing(true);
        lane.setUseCommonBitmapCrop(true);
        lane.setMinCellCount(1);
        laneBox.addView(lane, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams laneBoxLp = new LinearLayout.LayoutParams(-2, -2);
        laneBoxLp.setMargins(0, 0, scaledDp(2, scale), 0);
        content.addView(laneBox, laneBoxLp);

        LinearLayout lights = new LinearLayout(context);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setGravity(Gravity.CENTER);
        lights.setVisibility(View.GONE);
        content.addView(lights, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout widgetRow = new LinearLayout(context);
        widgetRow.setOrientation(LinearLayout.HORIZONTAL);
        widgetRow.setGravity(Gravity.CENTER_VERTICAL);
        widgetRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams widgetRowLp = new LinearLayout.LayoutParams(-2, -2);
        widgetRowLp.setMargins(scaledDp(3, scale), 0, 0, 0);
        content.addView(widgetRow, widgetRowLp);

        root.addView(content);

        clusterPanel = root;
        clusterModeText = mode;
        clusterCompactWidgetRow = widgetRow;
        clusterCompactNavTurnRoadText = navRoad;
        clusterCompactCruiseRoadText = cruiseRoad;
        clusterCompactCruiseDirText = cruiseDir;
        clusterCompactLaneBox = laneBox;
        fullModeClusterTurnInfoCol = distRoadCol;
        fullModeClusterEtaInfoCol = etaInfoCol;
        fullModeClusterEtaRemainDist = etaRemainDist;
        fullModeClusterEtaArriveTime = etaArriveTime;
        clusterNavTurnBox = navTurn;
        clusterNavTurnIconView = navIcon;
        clusterNavTurnDistText = navDist;
        clusterLaneSection = laneBox;
        clusterLaneBar = lane;
        clusterLightRow = lights;
        clusterTurnCard = null;
        clusterTurnText = null;
        clusterTurnDistanceText = null;
        clusterTurnIconView = null;
        clusterTurnDistBadge = null;
        clusterTurnRowLayout = null;
        clusterModeRow = null;
        clusterTitleText = null;
        clusterSummaryDivider = null;
        clusterSummaryRow = null;
        clusterHeadingInfoText = null;
        clusterRoadInfoText = null;
        clusterEtaText = null;
        clusterAlertCard = null;
        clusterLimitBadgeText = null;
        clusterAlertCaptionText = null;
        clusterAlertText = null;
        clusterAlertRow = null;
        clusterDetailText = null;

        updateDynamicIslandCruiseDirectionText(cruiseDir);
        applyTextPalette();
        refreshTurnCard();
        return root;
    }

    private boolean shouldShowStandbyStatusDetails() {
        if (TextUtils.isEmpty(currentModeLabel)) {
            return false;
        }
        return currentModeLabel.startsWith("\u5bfc\u822a")
                || currentModeLabel.startsWith("\u5de1\u822a")
                || currentModeLabel.startsWith("\u6a21\u62df\u5bfc\u822a");
    }

    private LinearLayout.LayoutParams sectionLp(float scale, float topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, scaledDp(topMarginDp, scale), 0, 0);
        return lp;
    }

    private TextView infoBlockText(Context context, String text, float scale) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(scaledSp(13.5f, scale));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private TextView circleBadge(Context context, String text, float scale, int strokeColor, int fillColor) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(strokeColor);
        view.setTextSize(scaledSp(16f, scale));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(fillColor);
        bg.setStroke(scaledDp(2, scale), strokeColor);
        view.setBackground(bg);
        return view;
    }

    private TextView speedBadge(Context context, String text, float scale) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(Color.WHITE);
        view.setTextSize(scaledSp(20f, scale));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x12000000);
        bg.setStroke(scaledDp(3, scale), 0xFFEF4444);
        view.setBackground(bg);
        return view;
    }

    private GradientDrawable createSectionBackground(float scale) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(withAlpha(0xFF132131, 86));
        bg.setCornerRadius(scaledDp(14, scale));
        bg.setStroke(scaledDp(1, scale), withAlpha(0xFFFFFFFF, 10));
        return bg;
    }

    private LinearLayout buildPanelForContext(Context context, float scale, boolean cluster) {
        float oldDensity = activeDensity;
        activeDensity = context.getResources().getDisplayMetrics().density;
        try {
            LinearLayout panel;
            if (MainActivity.isCardUiEnabled(this)) {
                panel = buildCardPanel(context, scale, cluster);
            } else if (MainActivity.isDynamicIslandUiEnabled(this)) {
                panel = buildDynamicIslandFullPanel(context, scale, cluster);
            } else if (MainActivity.isDynamicIslandTestUiEnabled(this)) {
                panel = buildDynamicIslandPanel(context, scale, cluster);
            } else if (MainActivity.isNewOverlayUiEnabled(this)) {
                panel = buildDashboardPanel(context, scale, cluster);
            } else {
                panel = buildClassicPanel(context, scale, cluster);
            }
            FontManager.applyToViewTree(context, panel);
            // Store panel background for overspeed warning border
            if (panel.getBackground() instanceof GradientDrawable) {
                if (cluster) {
                    clusterPanelBackground = (GradientDrawable) panel.getBackground();
                } else {
                    panelBackground = (GradientDrawable) panel.getBackground();
                }
            }
            return panel;
        } finally {
            activeDensity = oldDensity;
        }
    }

    private void updateClusterPosition() {
        if (clusterWindowManager == null || clusterPanel == null || clusterParams == null) {
            return;
        }
        int x = clusterParams.x;
        int y = clusterParams.y;
        int displayWidth = 0;
        int displayHeight = 0;
        if (clusterDisplay != null) {
            Point size = new Point();
            clusterDisplay.getRealSize(size);
            displayWidth = size.x;
            displayHeight = size.y;
        }
        int panelWidth = clusterPanel.getWidth();
        int panelHeight = clusterPanel.getHeight();
        if (panelWidth <= 0 || panelHeight <= 0) {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            clusterPanel.measure(widthSpec, heightSpec);
            panelWidth = Math.max(panelWidth, clusterPanel.getMeasuredWidth());
            panelHeight = Math.max(panelHeight, clusterPanel.getMeasuredHeight());
        }
        if (displayWidth > 0 && panelWidth > 0) {
            // Keep the user's saved anchor stable. Wide transient content such as
            // 8-lane guidance should not push the instrument overlay left.
            x = Math.max(0, Math.min(x, displayWidth - 1));
        }
        if (displayHeight > 0 && panelHeight > 0) {
            y = Math.max(0, Math.min(y, displayHeight - panelHeight));
        }
        clusterParams.x = x;
        clusterParams.y = y;
        try {
            if (clusterPanel.getParent() != null) {
                clusterWindowManager.updateViewLayout(clusterPanel, clusterParams);
            }
        } catch (Throwable t) {
            Log.e(TAG, "cluster position update failed", t);
        }
    }

    private void applySavedClusterPosition() {
        if (clusterWindowManager == null || clusterPanel == null || clusterParams == null) {
            ensureClusterMirror();
            return;
        }
        clusterParams.x = getSavedClusterX();
        clusterParams.y = getSavedClusterY();
        updateClusterPosition();
        saveClusterPosition();
    }

    private void dismissClusterMirror() {
        if (clusterPanel != null) {
            clusterPanel.removeOnLayoutChangeListener(clusterBoundsListener);
            clusterPanel.setVisibility(View.GONE);
            clusterPanel.invalidate();
        }
        if (clusterWindowManager != null && clusterPanel != null && clusterPanel.getParent() != null) {
            try {
                if (clusterParams != null) {
                    clusterWindowManager.updateViewLayout(clusterPanel, clusterParams);
                }
            } catch (Throwable ignored) {
            }
            try {
                clusterWindowManager.removeView(clusterPanel);
            } catch (Throwable ignored) {
            }
        }
        fullModeClusterTurnInfoCol = null;
        fullModeClusterEtaInfoCol = null;
        fullModeClusterEtaRemainDist = null;
        fullModeClusterEtaArriveTime = null;
        clusterContext = null;
        clusterWindowManager = null;
        clusterParams = null;
        clusterPanel = null;
        resetClusterPanelWidthStabilizer();
        clusterModeRow = null;
        clusterModeText = null;
        clusterTitleText = null;
        clusterSummaryDivider = null;
        clusterSummaryRow = null;
        clusterHeadingInfoText = null;
        clusterRoadInfoText = null;
        clusterTurnCard = null;
        clusterTurnLeadText = null;
        clusterTurnText = null;
        clusterTurnDistanceText = null;
        clusterLaneSection = null;
        clusterLaneBar = null;
        clusterLightRow = null;
        clusterEtaText = null;
        clusterAlertCard = null;
        clusterLimitBadgeText = null;
        clusterAlertCaptionText = null;
        clusterAlertText = null;
        clusterDetailText = null;
        clusterCompactWidgetRow = null;
        clusterCompactNavTurnRoadText = null;
        clusterCompactCruiseRoadText = null;
        clusterCompactCruiseDirText = null;
        clusterCompactLaneBox = null;
        clusterDisplay = null;
        clusterScale = -1f;
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

    private void refreshDisplayPolicies() {
        boolean foregroundChanged = false;
        if (MainActivity.isHideMainWhenTargetForegroundEnabled(this)) {
            boolean foreground = isTargetAppForeground();
            foregroundChanged = targetAppForeground != foreground;
            targetAppForeground = foreground;
        } else if (targetAppForeground) {
            targetAppForeground = false;
            foregroundChanged = true;
        }

        boolean targetBroadcastChanged = expireTargetBroadcastActivityIfNeeded();
        boolean navigationChanged = expireNavigationActivityIfNeeded();
        if (foregroundChanged || targetBroadcastChanged) {
            syncMainOverlayAttachment();
        }
        if (navigationChanged) {
            ensureClusterMirror();
        }
        if (foregroundChanged || navigationChanged) {
            refreshPanelVisibility();
        }
    }

    private boolean shouldHideMainOverlayForTargetForeground() {
        return MainActivity.isHideMainWhenTargetForegroundEnabled(this) && targetAppForeground;
    }

    private boolean shouldShowMainOverlayForTargetBroadcast() {
        return MainActivity.isShowMainWhenTargetForegroundEnabled(this) && targetBroadcastActive;
    }

    private boolean shouldHideClusterMirrorForInactiveNavigation() {
        return MainActivity.isHideClusterWhenInactiveEnabled(this)
                && !navigationOrCruiseActive
                && !shouldShowMainOverlayForTargetBroadcast();
    }

    private boolean updateNavigationActivityFromExtras(Bundle extras) {
        int keyType = intValue(extras, "KEY_TYPE", -1);
        int state = intValue(extras, "EXTRA_STATE", -1);
        boolean explicitExit = keyType == 10019 && (state == 9 || state == 12 || state == 25);
        boolean activeSignal = isNavigationActivitySignal(extras, keyType, state);
        boolean before = navigationOrCruiseActive;
        if (explicitExit) {
            navigationOrCruiseActive = false;
            lastNavigationSignalAt = 0L;
        } else if (activeSignal) {
            navigationOrCruiseActive = true;
            lastNavigationSignalAt = System.currentTimeMillis();
        }
        return before != navigationOrCruiseActive;
    }

    private boolean expireNavigationActivityIfNeeded() {
        if (!navigationOrCruiseActive || lastNavigationSignalAt <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() - lastNavigationSignalAt < NAVIGATION_ACTIVE_TTL_MS) {
            return false;
        }
        navigationOrCruiseActive = false;
        lastNavigationSignalAt = 0L;
        return true;
    }

    private boolean updateTargetBroadcastActivity(String action) {
        if (!isAmapRuntimeBroadcastAction(action)) {
            return false;
        }
        boolean before = targetBroadcastActive;
        targetBroadcastActive = true;
        lastTargetBroadcastAt = System.currentTimeMillis();
        return before != targetBroadcastActive;
    }

    private boolean expireTargetBroadcastActivityIfNeeded() {
        if (!targetBroadcastActive || lastTargetBroadcastAt <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() - lastTargetBroadcastAt < TARGET_BROADCAST_ACTIVE_TTL_MS) {
            return false;
        }
        targetBroadcastActive = false;
        lastTargetBroadcastAt = 0L;
        return true;
    }

    private boolean isAmapRuntimeBroadcastAction(String action) {
        return ACTION_SEND.equals(action)
                || ACTION_RECV.equals(action)
                || "AUTO_GUIDE_INFO_FOR_INTERNAL_WIDGET".equals(action)
                || "AUTO_STATUS_FOR_INTERNAL_WIDGET".equals(action)
                || (action != null && action.startsWith("com.autonavi.amapauto."));
    }

    private boolean shouldRequestAmapData() {
        return MainActivity.isMainOverlayEnabled(this)
                || MainActivity.isClusterMirrorEnabled(this)
                || targetBroadcastActive
                || navigationOrCruiseActive;
    }

    private boolean isNavigationActivitySignal(Bundle extras, int keyType, int state) {
        if (keyType == 10019) {
            return state == 5 || state == 6 || state == 8 || state == 10 || state == 11 || state == 24;
        }
        if (keyType == 10001 || keyType == 60021 || keyType == 13012) {
            return true;
        }
        if (keyType == KEY_TRAFFIC_LIGHT_COUNTDOWN && hasCountdownPayload(extras)) {
            return true;
        }
        return hasAny(extras,
                "ROUTE_REMAIN_DIS_AUTO", "ROUTE_REMAIN_TIME_AUTO",
                "ROUTE_REMAIN_DIS", "ROUTE_REMAIN_TIME",
                "SEG_REMAIN_DIS", "NEXT_SEG_REMAIN_DIS",
                "trafficLightStatus", "redLightCountDownSeconds", "greenLightLastSecond");
    }

    private boolean isTargetAppForeground() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        String targetPackage = MainActivity.getTargetPackage(this);
        try {
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()
                    && tasks.get(0).topActivity != null
                    && targetPackage.equals(tasks.get(0).topActivity.getPackageName())) {
                return true;
            }
        } catch (Throwable t) {
            Log.d(TAG, "read running task failed", t);
        }
        if (!MainActivity.hasUsageStatsAccess(this)) {
            Log.d(TAG, "usage stats access not granted; cannot detect target foreground");
            return false;
        }
        try {
            UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            if (usageStatsManager == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            UsageEvents events = usageStatsManager.queryEvents(now - 10000L, now);
            if (events != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                String latestForegroundPackage = null;
                long latestForegroundAt = 0L;
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    int type = event.getEventType();
                    if ((type == UsageEvents.Event.MOVE_TO_FOREGROUND
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            && type == UsageEvents.Event.ACTIVITY_RESUMED))
                            && event.getTimeStamp() >= latestForegroundAt) {
                        latestForegroundAt = event.getTimeStamp();
                        latestForegroundPackage = event.getPackageName();
                    }
                }
                if (!TextUtils.isEmpty(latestForegroundPackage)) {
                    return targetPackage.equals(latestForegroundPackage);
                }
            }
            List<UsageStats> stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 10000L, now);
            UsageStats latest = null;
            for (UsageStats stat : stats) {
                if (stat == null || TextUtils.isEmpty(stat.getPackageName())) {
                    continue;
                }
                if (latest == null || stat.getLastTimeUsed() > latest.getLastTimeUsed()) {
                    latest = stat;
                }
            }
            return latest != null && targetPackage.equals(latest.getPackageName());
        } catch (Throwable t) {
            Log.d(TAG, "read usage stats failed", t);
        }
        return false;
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

    private void saveClusterPosition() {
        if (clusterParams == null) {
            return;
        }
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .edit()
                .putInt(MainActivity.KEY_CLUSTER_X, clusterParams.x)
                .putInt(MainActivity.KEY_CLUSTER_Y, clusterParams.y)
                .apply();
    }

    private void syncClusterFromMain() {
        copyTextState(modeText, clusterModeText);
        copyTextState(turnText, clusterTurnText);
        copyTextState(etaText, clusterEtaText);
        copyTextState(alertText, clusterAlertText);
        copyTextState(detailText, clusterDetailText);
        copyVisibility(laneSection, clusterLaneSection);
        applyCachedLaneData();
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

    private void updateCompactMarqueeText(TextView view, String text) {
        if (view == null) {
            return;
        }
        String next = text == null ? "" : text;
        if (TextUtils.isEmpty(next)) {
            view.setVisibility(View.GONE);
            return;
        }
        if (!TextUtils.equals(view.getText(), next)) {
            view.setText(next);
            view.setSelected(false);
            view.post(() -> view.setSelected(true));
        }
        view.setVisibility(View.VISIBLE);
    }


	private void updateCompactCruiseDirectionText(TextView view) {
        if (view == null) {
            return;
        }
        if (MainActivity.isCardUiEnabled(this)) {
            String heading = !TextUtils.isEmpty(currentHeadingSummary) ? currentHeadingSummary : "--";
            view.setText("【" + heading + "】 ");
            view.setVisibility(View.VISIBLE);
            return;
        }
        if (!TextUtils.isEmpty(currentHeadingSummary)) {
            view.setText("\u8f66\u5934\uff1a" + currentHeadingSummary);
        } else if (TextUtils.isEmpty(view.getText())) {
            view.setText("\u8f66\u5934\uff1a--");
        }
        view.setVisibility(View.VISIBLE);
    }
    private void updateClusterCompactTurnText() {
        if (clusterCompactNavTurnRoadText != null) {
            String roadName = currentTurnRoad;
            if (TextUtils.isEmpty(roadName)) {
                roadName = currentRoadName;
            }
            updateCompactMarqueeText(clusterCompactNavTurnRoadText, roadName);
        }
        if (clusterCompactCruiseRoadText != null && inCruiseMode) {
            String road = !TextUtils.isEmpty(currentRoadName) ? currentRoadName : "";
            if (clusterCompactCruiseRoadText != null) clusterCompactCruiseRoadText.setText(road);
        }
        updateCompactCruiseDirectionText(clusterCompactCruiseDirText);
    }


    private boolean isDynamicIslandOrCard() {
        return MainActivity.isDynamicIslandUiEnabled(this)
                || MainActivity.isCardUiEnabled(this);
    }

        private void showAnyPanel() {
        refreshPanelVisibility();
    }

    private void refreshPanelVisibility() {
        if (panel != null) {
            panel.setVisibility(hasVisibleChildren(panel) ? View.VISIBLE : View.GONE);
            schedulePanelSizeStabilizer(panel, false);
        }
        if (clusterPanel != null) {
            clusterPanel.setVisibility(hasVisibleChildren(clusterPanel) ? View.VISIBLE : View.GONE);
            schedulePanelSizeStabilizer(clusterPanel, true);
        }
    }

    private void schedulePanelSizeStabilizer(LinearLayout target, boolean cluster) {
        if (target == null) {
            return;
        }
        target.post(() -> stabilizePanelSize(target, cluster));
    }

    private void stabilizePanelSize(LinearLayout target, boolean cluster) {
        if (target == null) {
            return;
        }
        int baseMin = cluster ? clusterPanelBaseMinWidth : mainPanelBaseMinWidth;
        if (baseMin < 0) {
            baseMin = Math.max(0, target.getMinimumWidth());
            if (cluster) {
                clusterPanelBaseMinWidth = baseMin;
            } else {
                mainPanelBaseMinWidth = baseMin;
            }
        }
        int baseMinHeight = cluster ? clusterPanelBaseMinHeight : mainPanelBaseMinHeight;
        if (baseMinHeight < 0) {
            baseMinHeight = Math.max(0, target.getMinimumHeight());
            if (cluster) {
                clusterPanelBaseMinHeight = baseMinHeight;
            } else {
                mainPanelBaseMinHeight = baseMinHeight;
            }
        }

        int width = target.getWidth();
        int height = target.getHeight();
        if (width <= 0 || height <= 0) {
            int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            target.measure(wSpec, hSpec);
            width = target.getMeasuredWidth();
            height = target.getMeasuredHeight();
        }

        int held = cluster ? clusterPanelHeldMinWidth : mainPanelHeldMinWidth;
        int heldHeight = cluster ? clusterPanelHeldMinHeight : mainPanelHeldMinHeight;
        int nextMin = Math.max(baseMin, Math.max(held, width));
        int nextMinHeight = Math.max(baseMinHeight, Math.max(heldHeight, height));
        boolean expanded = nextMin > held || nextMinHeight > heldHeight;
        if (cluster) {
            clusterPanelHeldMinWidth = nextMin;
            clusterPanelHeldMinHeight = nextMinHeight;
        } else {
            mainPanelHeldMinWidth = nextMin;
            mainPanelHeldMinHeight = nextMinHeight;
        }
        if (target.getMinimumWidth() != nextMin) {
            target.setMinimumWidth(nextMin);
            target.requestLayout();
        }
        if (target.getMinimumHeight() != nextMinHeight) {
            target.setMinimumHeight(nextMinHeight);
            target.requestLayout();
        }

        // Push expanded size to WindowManager immediately
        if (expanded) {
            if (cluster) {
                clusterWindowManager.updateViewLayout(clusterPanel, clusterParams);
            } else {
                windowManager.updateViewLayout(panel, params);
            }
        }

        Runnable oldUnlock = cluster ? clusterPanelWidthUnlock : mainPanelWidthUnlock;
        if (!expanded && oldUnlock != null) {
            return;
        }
        if (oldUnlock != null) {
            mainHandler.removeCallbacks(oldUnlock);
        }
        Runnable unlock = () -> unlockPanelWidth(target, cluster);
        if (cluster) {
            clusterPanelWidthUnlock = unlock;
        } else {
            mainPanelWidthUnlock = unlock;
        }
        mainHandler.postDelayed(unlock, PANEL_WIDTH_SHRINK_DELAY_MS);
    }

    private void unlockPanelWidth(LinearLayout target, boolean cluster) {
        if (target == null || target != (cluster ? clusterPanel : panel)) {
            return;
        }
        int baseMin = Math.max(0, cluster ? clusterPanelBaseMinWidth : mainPanelBaseMinWidth);
        int baseMinHeight = Math.max(0, cluster ? clusterPanelBaseMinHeight : mainPanelBaseMinHeight);
        if (cluster) {
            clusterPanelHeldMinWidth = 0;
            clusterPanelHeldMinHeight = 0;
            clusterPanelWidthUnlock = null;
        } else {
            mainPanelHeldMinWidth = 0;
            mainPanelHeldMinHeight = 0;
            mainPanelWidthUnlock = null;
        }
        boolean changed = false;
        if (target.getMinimumWidth() != baseMin) {
            target.setMinimumWidth(baseMin);
            changed = true;
        }
        if (target.getMinimumHeight() != baseMinHeight) {
            target.setMinimumHeight(baseMinHeight);
            changed = true;
        }
        if (changed) target.requestLayout();
        if (cluster) {
            updateClusterPosition();
        } else {
            updateOverlayPosition();
        }
    }

    private void resetMainPanelWidthStabilizer() {
        if (mainPanelWidthUnlock != null) {
            mainHandler.removeCallbacks(mainPanelWidthUnlock);
            mainPanelWidthUnlock = null;
        }
        mainPanelBaseMinWidth = -1;
        mainPanelBaseMinHeight = -1;
        mainPanelHeldMinWidth = 0;
        mainPanelHeldMinHeight = 0;
    }

    private void resetClusterPanelWidthStabilizer() {
        if (clusterPanelWidthUnlock != null) {
            mainHandler.removeCallbacks(clusterPanelWidthUnlock);
            clusterPanelWidthUnlock = null;
        }
        clusterPanelBaseMinWidth = -1;
        clusterPanelBaseMinHeight = -1;
        clusterPanelHeldMinWidth = 0;
        clusterPanelHeldMinHeight = 0;
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
        if (params != null) {
            android.graphics.Point screenSize = new android.graphics.Point();
            windowManager.getDefaultDisplay().getRealSize(screenSize);
            int panelWidth = panel != null ? panel.getWidth() : 0;
            int panelHeight = panel != null ? panel.getHeight() : 0;
            if (panelWidth <= 0 || panelHeight <= 0) {
                if (panel != null) {
                    int wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                    panel.measure(wSpec, hSpec);
                    panelWidth = Math.max(panelWidth, panel.getMeasuredWidth());
                    panelHeight = Math.max(panelHeight, panel.getMeasuredHeight());
                }
            }
            if (screenSize.x > 0 && panelWidth > 0) {
                params.x = Math.max(0, Math.min(params.x, screenSize.x - panelWidth));
            }
            if (screenSize.y > 0 && panelHeight > 0) {
                params.y = Math.max(0, Math.min(params.y, screenSize.y - panelHeight));
            }
        }
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
        stopFullModeAlternator();
        if (windowManager != null && panel != null && panel.getParent() != null) {
            try {
                windowManager.removeView(panel);
            } catch (Throwable t) {
                Log.e(TAG, "overlay remove for scale failed", t);
            }
        }
        fullModeTurnInfoCol = null;
        fullModeEtaInfoCol = null;
        fullModeEtaRemainDist = null;
        fullModeEtaArriveTime = null;
        fullModeClusterEtaRemainDist = null;
        fullModeClusterEtaArriveTime = null;
        panel = null;
        resetMainPanelWidthStabilizer();
        modeRow = null;
        modeText = null;
        titleText = null;
        summaryDivider = null;
        summaryRow = null;
        headingInfoText = null;
        roadInfoText = null;
        turnCard = null;
        turnLeadText = null;
        turnLeadIconView = null;
        turnText = null;
        turnDistanceText = null;
        turnIconView = null;
        turnDistBadge = null;
        turnRowLayout = null;
        navTurnBox = null;
        navTurnIconView = null;
        navTurnDistText = null;
        laneSection = null;
        laneBar = null;
        lightRow = null;
        etaText = null;
        alertCard = null;
        limitBadgeText = null;
        alertCaptionText = null;
        alertText = null;
        alertRow = null;
        detailText = null;
        compactWidgetRow = null;
        compactNavTurnRoadText = null;
        compactCruiseRoadText = null;
        compactCruiseDirText = null;
        compactLaneBox = null;
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
        if (!MainActivity.isMainOverlayEnabled(this)
                && !MainActivity.isClusterMirrorEnabled(this)
                && !MainActivity.isAutoStartEnabled(this)
                && !MainActivity.isShowMainWhenTargetForegroundEnabled(this)) {
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
            clusterScale = -1f;
            ensureClusterMirror();
            stopSelfIfNoVisuals();
            return;
        }
        if (MainActivity.ACTION_CLUSTER_POSITION_CHANGED.equals(action)) {
            applySavedClusterPosition();
            return;
        }
        if (MainActivity.ACTION_OVERLAY_STYLE_CHANGED.equals(action)) {
            rebuildOverlay();
            dismissClusterMirror();
            ensureClusterMirror();
            applyContentVisibilityPrefs();
            return;
        }
        if (MainActivity.ACTION_OVERLAY_CONTENT_CHANGED.equals(action)) {
            applyContentVisibilityPrefs();
            return;
        }
        if (MainActivity.ACTION_DISPLAY_POLICY_CHANGED.equals(action)) {
            refreshDisplayPolicies();
            stopSelfIfNoVisuals();
            return;
        }
        boolean targetBroadcastChanged = updateTargetBroadcastActivity(action);
        if (targetBroadcastChanged) {
            ensureOverlay();
            syncMainOverlayAttachment();
            ensureClusterMirror();
        }
        Bundle extras = intent.getExtras();
        Log.d(TAG, "recv action=" + action + " extras=" + describeExtras(extras));
        if (extras == null) {
            return;
        }

        ensureOverlay();
        boolean displayPolicyChanged = targetBroadcastChanged || updateNavigationActivityFromExtras(extras);
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
        // Capture vehicle speed from any broadcast (nav 10001, cruise 10019/60021, etc.)
        int speed = intValue(extras, "CUR_SPEED", intValue(extras, "SPEED", -1));
        if (speed >= 0) {
            currentVehicleSpeed = speed;
        }
        updateOverspeedWarning();
        if (displayPolicyChanged) {
            syncMainOverlayAttachment();
            ensureClusterMirror();
        }
    }

    private void applyContentVisibilityPrefs() {
        syncModeVisibility();
        syncTurnVisibility();
        syncLaneVisibility();
        syncEtaVisibility();
        syncAlertVisibility();
        if (!MainActivity.isAlertVisible(this)) {
            clearCompactWidgetRow();
        }
        syncDetailVisibility();
        syncTrafficLightVisibility();
        updateDynamicIslandLayout();
        refreshPanelVisibility();
        updateClusterPosition();
    }

    private void applyPanelStyle() {
        if (panel != null) {
            panel.setBackground(createMainPanelBackground());
        }
        if (clusterPanel != null) {
            clusterPanel.setBackground(createClusterPanelBackground());
        }
        applyTextPalette();
    }

    private GradientDrawable createMainPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        int opacity = MainActivity.getBackgroundOpacityPercent(this);
        bg.setColor(withAlpha(0xFF111827, opacity));
        bg.setStroke(dp(1), withAlpha(0xFFFFFFFF, MainActivity.strokeOpacityForBackground(opacity)));
        return bg;
    }

    private GradientDrawable createClusterPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(clusterDp(14));
        int opacity = MainActivity.getBackgroundOpacityPercent(this);
        bg.setColor(withAlpha(0xFF111827, opacity));
        bg.setStroke(clusterDp(1), withAlpha(0xFFFFFFFF, MainActivity.strokeOpacityForBackground(opacity)));
        return bg;
    }

    private void applyTextPalette() {
        int primary = primaryTextColor();
        int alert = alertTextColor();
        int detail = detailTextColor();
        if (modeText != null) {
            modeText.setTextColor(primary);
        }
        if (titleText != null) {
            titleText.setTextColor(primary);
        }
        if (headingInfoText != null) {
            headingInfoText.setTextColor(primary);
        }
        if (roadInfoText != null) {
            roadInfoText.setTextColor(primary);
        }
        if (turnLeadText != null) {
            turnLeadText.setTextColor(0xFF60A5FA);
        }
        if (turnText != null) {
            turnText.setTextColor(primary);
        }
        if (turnDistanceText != null) {
            turnDistanceText.setTextColor(primary);
        }
        if (etaText != null) {
            etaText.setTextColor(primary);
        }
        if (alertCaptionText != null) {
            alertCaptionText.setTextColor(0xFFCBD5E1);
        }
        if (limitBadgeText != null) {
            limitBadgeText.setTextColor(Color.WHITE);
        }
        if (alertText != null) {
            alertText.setTextColor(primary);
        }
        if (navTurnDistText != null) {
            navTurnDistText.setTextColor(primary);
        }
        applyEdogAlertTextColor(alertRow, primary);
        if (detailText != null) {
            detailText.setTextColor(detail);
        }
        if (clusterModeText != null) {
            clusterModeText.setTextColor(primary);
        }
        if (clusterTitleText != null) {
            clusterTitleText.setTextColor(primary);
        }
        if (clusterHeadingInfoText != null) {
            clusterHeadingInfoText.setTextColor(primary);
        }
        if (clusterRoadInfoText != null) {
            clusterRoadInfoText.setTextColor(primary);
        }
        if (clusterTurnLeadText != null) {
            clusterTurnLeadText.setTextColor(0xFF60A5FA);
        }
        if (clusterTurnText != null) {
            clusterTurnText.setTextColor(primary);
        }
        if (clusterTurnDistanceText != null) {
            clusterTurnDistanceText.setTextColor(primary);
        }
        if (clusterEtaText != null) {
            clusterEtaText.setTextColor(primary);
        }
        if (clusterAlertCaptionText != null) {
            clusterAlertCaptionText.setTextColor(0xFFCBD5E1);
        }
        if (clusterLimitBadgeText != null) {
            clusterLimitBadgeText.setTextColor(Color.WHITE);
        }
        if (clusterAlertText != null) {
            clusterAlertText.setTextColor(primary);
        }
        if (clusterNavTurnDistText != null) {
            clusterNavTurnDistText.setTextColor(primary);
        }
        applyEdogAlertTextColor(clusterAlertRow, primary);
        if (clusterDetailText != null) {
            clusterDetailText.setTextColor(detail);
        }
    }

    private void applyEdogAlertTextColor(LinearLayout row, int primary) {
        if (row == null) {
            return;
        }
        View cameraBox = row.findViewWithTag("camera_box");
        if (cameraBox instanceof LinearLayout) {
            LinearLayout box = (LinearLayout) cameraBox;
            if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                ((TextView) box.getChildAt(1)).setTextColor(primary);
            }
        }
        View lightBox = row.findViewWithTag("light_box");
        if (lightBox instanceof LinearLayout) {
            LinearLayout box = (LinearLayout) lightBox;
            if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                ((TextView) box.getChildAt(1)).setTextColor(primary);
            }
        }
    }

    private int primaryTextColor() {
        return MainActivity.usesDarkTextPalette(this) ? 0xFF0F172A : 0xFFE8EAED;
    }

    private int alertTextColor() {
        return MainActivity.usesDarkTextPalette(this) ? 0xFF7C2D12 : 0xFFFFF7ED;
    }

    private int detailTextColor() {
        return MainActivity.usesDarkTextPalette(this) ? 0xFF1E3A8A : 0xFFC7D2FE;
    }

    private int withAlpha(int color, int alphaPercent) {
        int alpha = Math.max(0, Math.min(255, Math.round(alphaPercent * 255f / 100f)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private void refreshRoadTitle() {
        String road = TextUtils.isEmpty(currentRoadName) ? "待命" : currentRoadName;
        if (titleText != null) {
            titleText.setText(road);
        }
        if (clusterTitleText != null) {
            clusterTitleText.setText(road);
        }
    }

    private void refreshStatusSummary() {
        String heading = TextUtils.isEmpty(currentHeadingSummary) ? "车头\n--" : "车头\n" + currentHeadingSummary;
        String roadType = TextUtils.isEmpty(currentRoadTypeSummary) ? "道路\n未透出" : "道路\n" + currentRoadTypeSummary;
        if (headingInfoText != null) {
            headingInfoText.setText(heading);
        }
        if (clusterHeadingInfoText != null) {
            clusterHeadingInfoText.setText(heading);
        }
        if (roadInfoText != null) {
            roadInfoText.setText(roadType);
        }
        if (clusterRoadInfoText != null) {
            clusterRoadInfoText.setText(roadType);
        }
    }

    private void refreshTurnCard() {
        String lead = TextUtils.isEmpty(currentTurnLead) ? "下一路口" : currentTurnLead;
        if (turnLeadText != null) {
            turnLeadText.setText(lead);
        }
        if (clusterTurnLeadText != null) {
            clusterTurnLeadText.setText(lead);
        }
        if (turnLeadIconView != null) {
            applyTurnIcon(turnLeadIconView, currentTurnIcon);
            turnLeadIconView.setVisibility(currentTurnIcon > 0 ? View.VISIBLE : View.GONE);
        }
        if (clusterTurnLeadIconView != null) {
            applyTurnIcon(clusterTurnLeadIconView, currentTurnIcon);
            clusterTurnLeadIconView.setVisibility(currentTurnIcon > 0 ? View.VISIBLE : View.GONE);
        }
        String turnRoadText = currentTurnRoad;
        if (turnDistanceText == null && !TextUtils.isEmpty(currentTurnDistance)) {
            turnRoadText = TextUtils.isEmpty(turnRoadText)
                    ? currentTurnDistance
                    : turnRoadText + "  " + currentTurnDistance;
        }
        String clusterTurnRoadText = currentTurnRoad;
        if (clusterTurnDistanceText == null && !TextUtils.isEmpty(currentTurnDistance)) {
            clusterTurnRoadText = TextUtils.isEmpty(clusterTurnRoadText)
                    ? currentTurnDistance
                    : clusterTurnRoadText + "  " + currentTurnDistance;
        }
        if (turnText != null) {
            turnText.setText(turnRoadText);
            turnText.setVisibility(TextUtils.isEmpty(turnRoadText) ? View.GONE : View.VISIBLE);
        }
        if (clusterTurnText != null) {
            clusterTurnText.setText(clusterTurnRoadText);
            clusterTurnText.setVisibility(TextUtils.isEmpty(clusterTurnRoadText) ? View.GONE : View.VISIBLE);
        }
        if (turnDistanceText != null) {
            turnDistanceText.setText(currentTurnDistance);
            turnDistanceText.setVisibility(TextUtils.isEmpty(currentTurnDistance) ? View.GONE : View.VISIBLE);
        }
        if (clusterTurnDistanceText != null) {
            clusterTurnDistanceText.setText(currentTurnDistance);
            clusterTurnDistanceText.setVisibility(TextUtils.isEmpty(currentTurnDistance) ? View.GONE : View.VISIBLE);
        }
        if (turnIconView != null) {
            applyTurnIcon(turnIconView, currentTurnIcon);
            turnIconView.setVisibility(currentTurnIcon > 0 ? View.VISIBLE : View.GONE);
        }
        if (clusterTurnIconView != null) {
            applyTurnIcon(clusterTurnIconView, currentTurnIcon);
            clusterTurnIconView.setVisibility(currentTurnIcon > 0 ? View.VISIBLE : View.GONE);
        }
        updateDistanceBadge(turnDistBadge, currentTurnDistance);
        updateDistanceBadge(clusterTurnDistBadge, currentTurnDistance);
        updateNavTurn(navTurnBox, navTurnIconView, navTurnDistText);
        updateNavTurn(clusterNavTurnBox, clusterNavTurnIconView, clusterNavTurnDistText);
        // Dynamic island: update nav turn road name
        if (compactNavTurnRoadText != null) {
            String roadName = currentTurnRoad;
            if ("下一路口".equals(roadName) && !TextUtils.isEmpty(currentRoadName)) {
                roadName = currentRoadName;
            }
            if (TextUtils.isEmpty(roadName) || "下一路口".equals(roadName)) {
                compactNavTurnRoadText.setVisibility(View.GONE);
            } else {
                updateCompactMarqueeText(compactNavTurnRoadText, roadName);
            }
        }
        if (compactCruiseRoadText != null && inCruiseMode) {
            if (!MainActivity.isCardUiEnabled(this)) {
                String road = !TextUtils.isEmpty(currentRoadName) ? currentRoadName : "";
                compactCruiseRoadText.setText(road);
            }
        }
        updateClusterCompactTurnText();
        if (isDynamicIslandOrCard()) {
            ensureFullModeAlternator();
            updateDynamicIslandLayout();
            updateCardLayout();
        }
        syncLaneVisibility();
    }

    private void clearTurnState() {
        currentTurnLead = "";
        currentTurnRoad = "";
        currentTurnDistance = "";
        currentTurnIcon = 0;
        navigationTurnDir = -1;
        refreshTurnCard();
        if (compactNavTurnRoadText != null) {
            compactNavTurnRoadText.setVisibility(View.GONE);
        }
        if (clusterCompactNavTurnRoadText != null) {
            clusterCompactNavTurnRoadText.setVisibility(View.GONE);
        }
        stopCompactBreathing();
        stopFullModeAlternator();
        setPairedVisibility(turnCard, clusterTurnCard, false);
        setPairedVisibility(turnRowLayout, clusterTurnRowLayout, false);
        setPairedVisibility(turnText, clusterTurnText, false);
        setPairedVisibility(turnDistanceText, clusterTurnDistanceText, false);
        setPairedVisibility(turnIconView, clusterTurnIconView, false);
        if (turnIconView != null) {
            turnIconView.setImageDrawable(null);
        }
        if (clusterTurnIconView != null) {
            clusterTurnIconView.setImageDrawable(null);
        }
        if (turnLeadIconView != null) {
            turnLeadIconView.setImageDrawable(null);
            turnLeadIconView.setVisibility(View.GONE);
        }
        if (clusterTurnLeadIconView != null) {
            clusterTurnLeadIconView.setImageDrawable(null);
            clusterTurnLeadIconView.setVisibility(View.GONE);
        }
        if (navTurnBox != null) {
            navTurnBox.setVisibility(View.GONE);
        }
        if (clusterNavTurnBox != null) {
            clusterNavTurnBox.setVisibility(View.GONE);
        }
        if (navTurnIconView != null) {
            navTurnIconView.setImageDrawable(null);
        }
        if (clusterNavTurnIconView != null) {
            clusterNavTurnIconView.setImageDrawable(null);
        }
        if (navTurnDistText != null) {
            navTurnDistText.setText("");
        }
        if (clusterNavTurnDistText != null) {
            clusterNavTurnDistText.setText("");
        }
        syncLaneVisibility();
    }

    private void refreshAlertCard() {
        String badge = currentLimitSpeed > 0 ? String.valueOf(currentLimitSpeed) : "--";
        if (limitBadgeText != null) {
            limitBadgeText.setText(badge);
        }
        if (clusterLimitBadgeText != null) {
            clusterLimitBadgeText.setText(badge);
        }
    }

    private void syncModeVisibility() {
        boolean visible = MainActivity.isModeVisible(this) && modeText != null;
        if (modeRow != null || clusterModeRow != null) {
            setPairedVisibility(modeRow, clusterModeRow, visible);
            setPairedVisibility(titleText, clusterTitleText, visible);
        } else {
            setPairedVisibility(modeText, clusterModeText, visible);
        }
    }

    private void syncTurnVisibility() {
        boolean visible = MainActivity.isTurnVisible(this)
                && ((turnText != null && !TextUtils.isEmpty(turnText.getText()))
                || (turnDistanceText != null && !TextUtils.isEmpty(turnDistanceText.getText()))
                || currentTurnIcon > 0);
        if (turnRowLayout != null || clusterTurnRowLayout != null) {
            setPairedVisibility(turnRowLayout, clusterTurnRowLayout, visible);
        } else if (turnCard != null || clusterTurnCard != null) {
            setPairedVisibility(turnCard, clusterTurnCard, visible);
        } else {
            setPairedVisibility(turnText, clusterTurnText, visible);
        }
    }

    private void syncLaneVisibility() {
        boolean turnPriority = (navTurnBox != null && navTurnBox.getVisibility() == View.VISIBLE)
                || (clusterNavTurnBox != null && clusterNavTurnBox.getVisibility() == View.VISIBLE);
        // Dynamic island mode: lanes always visible alongside nav turn info
        if (isDynamicIslandOrCard()) {
            turnPriority = false;
        }
        boolean visible = MainActivity.isLaneVisible(this)
                && laneBar != null
                && laneBar.getVisibility() == View.VISIBLE
                && !turnPriority;
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
        boolean visible;
        if (MainActivity.isCardUiEnabled(this)) {
            visible = MainActivity.isAlertVisible(this)
                    && alertRow != null
                    && alertRow.getVisibility() == View.VISIBLE;
        } else {
            visible = MainActivity.isAlertVisible(this)
                    && alertText != null
                    && !TextUtils.isEmpty(alertText.getText());
        }
        if (alertCard != null || clusterAlertCard != null) {
            setPairedVisibility(alertCard, clusterAlertCard, visible);
        } else if (alertRow != null || clusterAlertRow != null) {
            setPairedVisibility(alertRow, clusterAlertRow, visible);
        } else {
            setPairedVisibility(alertText, clusterAlertText, visible);
        }
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

    private TextView compactText(float size, boolean detailStyle) {
        return compactText(this, size, detailStyle);
    }

    private TextView compactText(Context context, float size, boolean detailStyle) {
        return compactText(context, size, detailStyle, overlayScale);
    }

    private TextView compactText(Context context, float size, boolean detailStyle, float scale) {
        TextView view = new TextView(context);
        view.setTextColor(detailStyle ? detailTextColor() : alertTextColor());
        view.setTextSize(scaledSp(size, scale));
        view.setSingleLine(false);
        view.setMaxLines(2);
        view.setGravity(Gravity.CENTER);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(scaledDp(8, scale), scaledDp(2, scale), scaledDp(8, scale), scaledDp(2, scale));
        return view;
    }

    private LinearLayout buildEdogAlertRow(Context context, float scale) {
        return buildEdogAlertRow(context, scale, 24);
    }

    private LinearLayout buildEdogAlertRow(Context context, float scale, int iconSizeDp) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int iconSize = scaledDp(iconSizeDp, scale);

        FrameLayout speedBox = new FrameLayout(context);
        speedBox.setTag("speed_box");
        ImageView speedIcon = new ImageView(context);
        speedIcon.setImageResource(R.drawable.widget_drawable_auto_ic_edog_limit_speed_loading);
        speedIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        speedBox.addView(speedIcon, new FrameLayout.LayoutParams(iconSize, iconSize));
        TextView speedText = new TextView(context);
        speedText.setTextColor(0xFFDC2626);
        speedText.setTextSize(scaledSp(10.5f, scale));
        speedText.setTypeface(Typeface.DEFAULT_BOLD);
        speedText.setGravity(Gravity.CENTER);
        speedBox.addView(speedText, new FrameLayout.LayoutParams(iconSize, iconSize));
        speedBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(-2, -2);
        speedLp.setMargins(0, 0, scaledDp(6, scale), 0);
        row.addView(speedBox, speedLp);

        LinearLayout cameraBox = new LinearLayout(context);
        cameraBox.setTag("camera_box");
        cameraBox.setOrientation(LinearLayout.HORIZONTAL);
        cameraBox.setGravity(Gravity.CENTER_VERTICAL);
        cameraBox.setVisibility(View.GONE);
        FrameLayout cameraIconFrame = new FrameLayout(context);
        cameraIconFrame.setTag("camera_icon_frame");
        ImageView cameraIcon = new ImageView(context);
        cameraIcon.setImageResource(R.drawable.widget_drawable_auto_ic_edog_camera_loading);
        cameraIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cameraIconFrame.addView(cameraIcon, new FrameLayout.LayoutParams(iconSize, iconSize));
        TextView cameraSpeedOverlay = new TextView(context);
        cameraSpeedOverlay.setTextColor(0xFFDC2626);
        cameraSpeedOverlay.setTextSize(scaledSp(iconSizeDp * 0.38f, scale));
        cameraSpeedOverlay.setTypeface(Typeface.DEFAULT_BOLD);
        cameraSpeedOverlay.setGravity(Gravity.CENTER);
        cameraSpeedOverlay.setVisibility(View.GONE);
        cameraIconFrame.addView(cameraSpeedOverlay, new FrameLayout.LayoutParams(iconSize, iconSize));
        cameraBox.addView(cameraIconFrame, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView cameraText = new TextView(context);
        cameraText.setTextColor(primaryTextColor());
        cameraText.setTextSize(scaledSp(12f, scale));
        cameraText.setTypeface(Typeface.DEFAULT_BOLD);
        cameraBox.addView(cameraText, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams cameraLp = new LinearLayout.LayoutParams(-2, -2);
        cameraLp.setMargins(0, 0, scaledDp(6, scale), 0);
        row.addView(cameraBox, cameraLp);

        LinearLayout lightBox = new LinearLayout(context);
        lightBox.setTag("light_box");
        lightBox.setOrientation(LinearLayout.HORIZONTAL);
        lightBox.setGravity(Gravity.CENTER_VERTICAL);
        lightBox.setVisibility(View.GONE);
        ImageView lightIcon = new ImageView(context);
        lightIcon.setImageResource(R.drawable.widget_drawable_auto_ic_edog_traffic_loading);
        lightIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lightBox.addView(lightIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView lightCount = new TextView(context);
        lightCount.setTextColor(primaryTextColor());
        lightCount.setTextSize(scaledSp(13f, scale));
        lightCount.setTypeface(Typeface.DEFAULT_BOLD);
        lightBox.addView(lightCount, new LinearLayout.LayoutParams(-2, -2));
        row.addView(lightBox, new LinearLayout.LayoutParams(-2, -2));

        FontManager.applyToViewTree(context, row);
        return row;
    }

    private void updateDistanceBadge(TextView badge, String distance) {
        if (badge == null) {
            return;
        }
        if (TextUtils.isEmpty(distance)) {
            badge.setText("");
            badge.setVisibility(View.GONE);
            return;
        }
        badge.setText(distance);
        badge.setVisibility(View.VISIBLE);
    }

    private void updateNavTurn(LinearLayout box, ImageView iconView, TextView distanceText) {
        if (box == null) {
            return;
        }
        if (currentTurnIcon <= 0) {
            box.setVisibility(View.GONE);
            return;
        }
        applyTurnIcon(iconView, currentTurnIcon);
        if (distanceText != null) {
            distanceText.setText(TextUtils.isEmpty(currentTurnDistance) ? "" : currentTurnDistance);
        }
        box.setVisibility(View.VISIBLE);
    }

    private void applyTurnIcon(ImageView view, int icon) {
        if (view == null) {
            return;
        }
        int resId = turnIconResource(icon);
        float rotation = 0f;
        float scaleX = 1f;
        if (resId == 0) {
            resId = fallbackTurnIconResource(icon);
        }
        view.setImageResource(resId);
        view.setRotation(rotation);
        view.setScaleX(scaleX);
        view.setVisibility(View.VISIBLE);
    }

    private int turnIconResource(int icon) {
        icon = compatibleTurnIcon(icon);
        if (icon <= 0) {
            return 0;
        }
        // The broadcast's 50+ action codes are not the same namespace as sou50+ resources:
        // sou50-sou69 are roundabout-exit artwork, while values like NEW_ICON=65 can mean
        // "enter/continue on main road". Only direct-map the base AutoNavi turn icons here.
        if (icon > 28) {
            return 0;
        }
        return souTurnIconResource(icon);
    }

    private int souTurnIconResource(int icon) {
        int id = getResources().getIdentifier("sou" + icon + "_night_a530", "drawable", getPackageName());
        if (id != 0) {
            return id;
        }
        return getResources().getIdentifier("sou" + icon + "_night", "drawable", getPackageName());
    }

    private int fallbackTurnIconResource(int icon) {
        icon = compatibleTurnIcon(icon);
        switch (icon) {
            case 2:
                return souTurnIconResource(2);
            case 3:
            case 7:
                return souTurnIconResource(3);
            case 4:
            case 6:
                return souTurnIconResource(4);
            case 5:
                return souTurnIconResource(5);
            case 8:
            case 10:
            case 11:
            case 12:
                return souTurnIconResource(8);
            case 19:
                return souTurnIconResource(19);
            case 1:
            case 9:
            case 20:
            default:
                return souTurnIconResource(9);
        }
    }

    private int compatibleTurnIcon(int icon) {
        // AutoNavi broadcasts action codes, not drawable ids. Its own GuideInfoProtocolData
        // switchIcon() maps these extension actions back to base turn icons on supported builds.
        if (icon == 65) {
            return 4;
        }
        if (icon == 66) {
            return 5;
        }
        return icon;
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
            currentRoadName = "";
            currentHeadingSummary = "";
            currentRoadTypeSummary = "";
            clearTurnState();
            updateDynamicIslandLayout();
        } else if (keyType == 10019 && state == 8) {
            mode = "\u5bfc\u822a";
            inCruiseMode = false;
        } else if (keyType == 10019 && state == 9) {
            mode = "\u5bfc\u822a\u5df2\u9000\u51fa";
            inCruiseMode = false;
            navigationTurnDir = -1;
            currentRoadName = "";
            currentHeadingSummary = "";
            currentRoadTypeSummary = "";
            updateDynamicIslandLayout();
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
            clearTurnState();
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
            currentLimitSpeed = -1;
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
            currentRoadName = "";
            currentRoadTypeSummary = "";
            clearTurnState();
        }

        StringBuilder sb = new StringBuilder(mode);
        if (!TextUtils.isEmpty(road)) {
            sb.append(" \u00b7 ").append(road);
        }
        if (speed >= 0) {
            sb.append(" \u00b7 ").append(speed).append(" km/h");
        }
        String text = sb.toString();
        currentModeLabel = text;
        if ("\u5df2\u8fde\u63a5".equals(mode) && !TextUtils.isEmpty(lastDetailedMode)) {
            return;
        }
        if (!"\u5df2\u8fde\u63a5".equals(mode)
                && (!TextUtils.isEmpty(road) || speed >= 0 || "\u5de1\u822a".equals(mode))) {
            lastDetailedMode = text;
        }
        if (!TextUtils.isEmpty(road)) {
            currentRoadName = road;
        }
        modeText.setText(text);
        if (clusterModeText != null) {
            clusterModeText.setText(text);
        }
        refreshRoadTitle();
        refreshAlertCard();
        updateDynamicIslandLayout();
        syncModeVisibility();
        if (MainActivity.isModeVisible(this)) {
            showAnyPanel();
        }
    }

    private void updateTurnFromExtras(Bundle extras) {
        boolean compactMode = MainActivity.isDynamicIslandUiEnabled(this);
        if (turnText == null && (compactNavTurnRoadText == null || !compactMode) && !MainActivity.isCardUiEnabled(this)) {
            return;
        }
        int keyType = intValue(extras, "KEY_TYPE", -1);
        if (keyType != 10001) {
            return;
        }
        if (inCruiseMode) {
            clearTurnState();
            return;
        }
        int icon = intValue(extras, "NEW_ICON", intValue(extras, "ICON", 0));
        if (icon <= 0) {
            clearTurnState();
            return;
        }
        navigationTurnDir = turnIconToTrafficDir(icon);
        currentTurnIcon = icon;
        String distance = valueString(extras, "SEG_REMAIN_DIS_AUTO", "NEXT_SEG_REMAIN_DIS_AUTO");
        if (TextUtils.isEmpty(distance)) {
            int meters = intValue(extras, "SEG_REMAIN_DIS", intValue(extras, "NEXT_SEG_REMAIN_DIS", -1));
            if (meters > 0) {
                distance = (compactMode || MainActivity.isCardUiEnabled(this)) ? formatDistanceCompact(meters) : formatDistance(meters);
            }
        } else if (compactMode || MainActivity.isCardUiEnabled(this)) {
            distance = distance.replace("\u516c\u91cc", "km").replace("\u7c73", "m");
        }
        String nextRoad = valueString(extras, "NEXT_ROAD_NAME", "nextRoadName", "NEXT_ROAD",
                "NEXT_ROAD_NAME_AUTO", "SEG_ROAD_NAME", "NEXT_SEG_ROAD_NAME", "ROAD_NAME", "roadName");
        currentTurnLead = "\u4e0b\u4e00\u8def\u53e3";
        currentTurnRoad = !TextUtils.isEmpty(nextRoad) ? nextRoad
                : !TextUtils.isEmpty(currentRoadName) ? currentRoadName : "\u4e0b\u4e00\u8def\u53e3";
        currentTurnDistance = TextUtils.isEmpty(distance) ? "" : distance;
        refreshTurnCard();
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
        if (booleanValue(extras, "clearLights", false)
                || booleanValue(extras, "EXTRA_CLEAR_LIGHTS", false)) {
            trafficLights.clear();
            renderTrafficLights();
            Log.d(TAG, "clear traffic lights by wrapper broadcast");
            return;
        }
        if (keyType != KEY_TRAFFIC_LIGHT_COUNTDOWN
                && intValue(extras, "TRAFFIC_LIGHT_NUM", -1) == 0
                && intValue(extras, "routeRemainTrafficLightNum", -1) == 0
                && !hasCountdownPayload(extras)) {
            trafficLights.clear();
            renderTrafficLights();
            return;
        }
        HashMap<Integer, LightState> nextLights = new HashMap<>();
        if (hasLightsDataPayload(extras)) {
            if (updateLightsDataTrafficLights(extras, nextLights)) {
                replaceTrafficLights(nextLights);
                renderTrafficLights();
                return;
            } else if (!hasSingleLightPayload(extras)) {
                trafficLights.clear();
                renderTrafficLights();
                Log.d(TAG, "lightsData present but empty, cleared stale cruise lights");
                return;
            } else {
                Log.d(TAG, "lightsData present but no valid light parsed, falling back to single-field payload");
            }
        }
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
                "trafficLight", "trafficLightInfo", "trafficLightsCountdownInfo", "lightsData",
                "LIGHTS_DATA", "cameraLightInfo",
                "cameraLightInfos", "cameraLightInfoWrapper", "cameraLights", "lightInfos");
    }

    private boolean hasLightsDataPayload(Bundle extras) {
        return extras.containsKey("lightsData") || extras.containsKey("LIGHTS_DATA");
    }

    private boolean hasSingleLightPayload(Bundle extras) {
        return hasAny(extras, "trafficLightStatus", "TRAFFIC_LIGHT_STATUS", "traffic_light_status",
                "redLightCountDownSeconds", "redLightCountDownSecond", "redLightCountdownSeconds",
                "greenLightLastSecond", "greenLightCountDownSeconds", "greenLightCountdownSeconds",
                "dir", "direction", "trafficLightDir", "trafficLightDirection");
    }

    private void applyTrafficLights(HashMap<Integer, LightState> nextLights) {
        if (inCruiseMode) {
            mergeTrafficLights(nextLights);
        } else {
            replaceTrafficLights(nextLights);
        }
        renderTrafficLights();
    }

    private boolean updateLightsDataTrafficLights(Bundle extras, HashMap<Integer, LightState> target) {
        boolean handled = false;
        Object value = safeExtra(extras, "lightsData");
        if (value == null) {
            value = safeExtra(extras, "LIGHTS_DATA");
        }
        inCruiseMode = true;
        handled |= parseLightsDataValue(value, target);
        return handled;
    }

    private boolean parseLightsDataValue(Object value, HashMap<Integer, LightState> target) {
        if (value == null) {
            return false;
        }
        if (value instanceof Bundle) {
            return parseLightsDataBundle((Bundle) value, target);
        }
        if (value instanceof Iterable) {
            boolean handled = false;
            for (Object item : (Iterable<?>) value) {
                handled |= parseLightsDataValue(item, target);
            }
            return handled;
        }
        Class<?> valueClass = value.getClass();
        if (valueClass.isArray()) {
            boolean handled = false;
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                handled |= parseLightsDataValue(Array.get(value, i), target);
            }
            return handled;
        }
        return parseLightsDataText(String.valueOf(value), target);
    }

    private boolean parseLightsDataBundle(Bundle bundle, HashMap<Integer, LightState> target) {
        return putLightsDataInfo(
                intValue(bundle, "dir", intValue(bundle, "direction", intValue(bundle, "c", -1))),
                intValue(bundle, "status", intValue(bundle, "trafficLightStatus", intValue(bundle, "d", -1))),
                intValue(bundle, "countdown", intValue(bundle, "countDown",
                        intValue(bundle, "redLightCountDownSeconds", intValue(bundle, "e", 0)))),
                intValue(bundle, "redLightCountDownSeconds", intValue(bundle, "redLightCountdownSeconds",
                        intValue(bundle, "redSeconds", intValue(bundle, "redCountDown", 0)))),
                intValue(bundle, "greenLightLastSecond", intValue(bundle, "greenLightCountDownSeconds",
                        intValue(bundle, "greenLightCountdownSeconds", intValue(bundle, "greenSeconds", 0)))),
                target);
    }

    private boolean parseLightsDataText(String text, HashMap<Integer, LightState> target) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        try {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                boolean handled = false;
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) {
                        handled |= parseLightsDataObject(item, target);
                    }
                }
                return handled;
            }
            if (trimmed.startsWith("{")) {
                return parseLightsDataObject(new JSONObject(trimmed), target);
            }
        } catch (Throwable t) {
            Log.d(TAG, "lightsData parse skipped: " + text);
        }
        return false;
    }

    private boolean parseLightsDataObject(JSONObject object, HashMap<Integer, LightState> target) {
        boolean handled = false;
        JSONArray array = object.optJSONArray("lightsData");
        if (array == null) {
            array = object.optJSONArray("lights");
        }
        if (array == null) {
            array = object.optJSONArray("trafficLights");
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    handled |= parseLightsDataObject(item, target);
                }
            }
        }
        handled |= putLightsDataInfo(
                object.optInt("dir", object.optInt("direction", object.optInt("c", -1))),
                object.optInt("status", object.optInt("trafficLightStatus", object.optInt("d", -1))),
                object.optInt("countdown", object.optInt("countDown",
                        object.optInt("redLightCountDownSeconds", object.optInt("e", 0)))),
                object.optInt("redLightCountDownSeconds", object.optInt("redLightCountdownSeconds",
                        object.optInt("redSeconds", object.optInt("redCountDown", 0)))),
                object.optInt("greenLightLastSecond", object.optInt("greenLightCountDownSeconds",
                        object.optInt("greenLightCountdownSeconds", object.optInt("greenSeconds", 0)))),
                target);
        return handled;
    }

    private boolean putLightsDataInfo(int rawDir, int rawStatus, int countDown, int red, int green,
                                      HashMap<Integer, LightState> target) {
        if (rawDir < 0) {
            return false;
        }
        if (countDown <= 0) {
            countDown = Math.max(red, green);
        }
        int dir = normalizeCruiseCameraDirection(rawDir);
        int status = normalizeCruiseCameraStatus(rawStatus);
        if (red <= 0 && green <= 0) {
            if (isGreenLightStatus(status)) {
                green = countDown;
            } else {
                red = countDown;
            }
        }
        int seconds = secondsForLight(status, red, green);
        if (seconds <= 0) {
            return false;
        }
        putLightState(target, dir >= 0 ? dir : 900 + target.size(), dir, status,
                red, green, seconds);
        Log.d(TAG, "lightsData light rawDir=" + rawDir + " rawStatus=" + rawStatus
                + " countdown=" + countDown + " red=" + red + " green=" + green
                + " => dir=" + dir + " status=" + status + " seconds=" + seconds);
        return true;
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
        int status = normalizeCruiseCameraStatus(cameraStatus);
        putLightState(target, dir >= 0 ? dir : 999, dir, status,
                isRedLightStatus(status) ? countDown : 0,
                isGreenLightStatus(status) ? countDown : 0, countDown);
        return true;
    }

    private int normalizeCruiseCameraStatus(int cameraStatus) {
        if (cameraStatus == 0) {
            return 1;
        }
        if (cameraStatus == 1 || cameraStatus == 2 || cameraStatus == 4) {
            return 4;
        }
        return cameraStatus;
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
                || lowerKey.contains("lightsdata")
                || lowerText.contains("redlightcountdownseconds")
                || lowerText.contains("greenlightlastsecond")
                || lowerText.contains("trafficlightstatus")
                || lowerText.contains("\"countdown\"")
                || lowerText.contains("\"showtype\"");
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
        state.dir = normalizeLightDirectionForDisplay(dir, status, red, green);
        state.status = status;
        state.seconds = seconds;
        state.color = colorForStatus(status, red, green);
        state.updatedAt = System.currentTimeMillis();
        state.ttlMs = inCruiseMode ? seconds * 1000L + 2000L : LIGHT_TTL_MS;
        LightState old = target.get(key);
        if (old == null || preferLightState(state, old)) {
            target.put(key, state);
        }
    }

    private int normalizeLightDirectionForDisplay(int dir, int status, int red, int green) {
        if (!inCruiseMode && isDisplayedYellowLight(status, red, green)) {
            return -1;
        }
        if (!inCruiseMode && dir == 3 && effectiveNavigationTurnDir() == 0) {
            return 0;
        }
        if (!inCruiseMode && dir == 0) {
            return 4;
        }
        return dir;
    }

    private int effectiveNavigationTurnDir() {
        if (navigationTurnDir >= 0) {
            return navigationTurnDir;
        }
        if (currentTurnIcon > 0) {
            return turnIconToTrafficDir(currentTurnIcon);
        }
        return -1;
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
            LightState state = entry.getValue();
            if (now - state.updatedAt > state.ttlMs || currentLightSeconds(state, now) <= 0) {
                iterator.remove();
            }
        }
        if (trafficLights.isEmpty()) {
            mainHandler.removeCallbacks(trafficLightTicker);
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
        boolean showMainDirectionLabel = true;
        boolean showClusterDirectionLabel = true;
        for (Integer key : keys) {
            LightState state = trafficLights.get(key);
            if (state == null) {
                continue;
            }
            int seconds = currentLightSeconds(state, now);
            if (seconds <= 0) {
                continue;
            }
            lightRow.addView(lightPill(this, state, showMainDirectionLabel, overlayScale, seconds));
            if (clusterLightRow != null && clusterContext != null) {
                clusterLightRow.addView(lightPill(clusterContext, state,
                        showClusterDirectionLabel, clusterScale, seconds));
            }
        }
        syncTrafficLightVisibility();
        if (MainActivity.isLightVisible(this) && lightRow.getChildCount() > 0) {
            showAnyPanel();
        }
        mainHandler.removeCallbacks(trafficLightTicker);
        if (!trafficLights.isEmpty()) {
            mainHandler.postDelayed(trafficLightTicker, LIGHT_TICK_MS);
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
            if (old == null || currentLightSeconds(state, System.currentTimeMillis())
                    < currentLightSeconds(old, System.currentTimeMillis())) {
                best = key;
            }
        }
        return best;
    }

    private View lightPill(LightState state, boolean showDirectionLabel) {
        return lightPill(this, state, showDirectionLabel);
    }

    private View lightPill(Context context, LightState state, boolean showDirectionLabel) {
        return lightPill(context, state, showDirectionLabel, overlayScale);
    }

    private View lightPill(Context context, LightState state, boolean showDirectionLabel, float scale) {
        return lightPill(context, state, showDirectionLabel, scale,
                currentLightSeconds(state, System.currentTimeMillis()));
    }

    private View lightPill(Context context, LightState state, boolean showDirectionLabel,
                           float scale, int seconds) {
        float oldDensity = activeDensity;
        activeDensity = context.getResources().getDisplayMetrics().density;
        try {
            boolean showArrowBadge = showDirectionLabel && state.dir >= 0;

            // Dynamic island (main mode) cruise: use classic capsule style matching lane size
            boolean useClassicCruisePill = MainActivity.isCardUiEnabled(this)
                    || (MainActivity.isDynamicIslandUiEnabled(this) && inCruiseMode);
            // Dynamic island (test mode) keeps its own compact style
            if (!useClassicCruisePill && MainActivity.isDynamicIslandUiEnabled(this)) {
                return buildCompactLightPill(context, state, showArrowBadge, scale, seconds, oldDensity);
            }

            boolean dynamicIsland = MainActivity.isDynamicIslandUiEnabled(this);
            // Cruise mode in main dynamic island uses classic sizing matched to lane
            if (useClassicCruisePill) {
                dynamicIsland = true;
            }
            int pillHeight = useClassicCruisePill ? 24 : (dynamicIsland ? 36 : 44);
            int arrowSize = useClassicCruisePill ? 14 : (dynamicIsland ? 17 : 25);
            int minW = showArrowBadge ? (useClassicCruisePill ? 46 : (dynamicIsland ? 76 : 92))
                    : (useClassicCruisePill ? 44 : (dynamicIsland ? 62 : 76));

            LinearLayout view = new LinearLayout(context);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER);
            view.setMinimumWidth(scaledDp(minW, scale));
            view.setMinimumHeight(scaledDp(pillHeight, scale));
            int padH = useClassicCruisePill ? 6 : (showArrowBadge ? (dynamicIsland ? 5 : 6) : (dynamicIsland ? 7 : 8));
            int padV = useClassicCruisePill ? 1 : (dynamicIsland ? 3 : 4);
            int padR = useClassicCruisePill ? 6 : (showArrowBadge ? (dynamicIsland ? 8 : 10) : (dynamicIsland ? 8 : 10));
            view.setPadding(scaledDp(padH, scale), scaledDp(padV, scale),
                    scaledDp(padR, scale), scaledDp(padV, scale));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(withAlpha(state.color, 92));
            bg.setCornerRadius(scaledDp(pillHeight / 2, scale));
            bg.setStroke(scaledDp(1, scale), withAlpha(0xFFFFFFFF, 72));
            view.setBackground(bg);

            if (showArrowBadge) {
                View arrow;
                if (useClassicCruisePill) {
                    // Create arrow without inner padding for tighter cruise capsule
                    Bitmap diyBitmap = loadDiyArrowBitmap(state.dir);
                    if (diyBitmap != null) {
                        ImageView img = new ImageView(context);
                        img.setImageBitmap(diyBitmap);
                        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        arrow = img;
                    } else {
                        ImageView img = new ImageView(context);
                        img.setImageResource(defaultCruiseArrowResource(state.dir));
                        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        arrow = img;
                    }
                } else {
                    arrow = diyArrowBadge(context, state, scale);
                }
                LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                        scaledDp(arrowSize, scale), scaledDp(arrowSize, scale));
                arrowLp.setMargins(0, 0, scaledDp(useClassicCruisePill ? 1 : (dynamicIsland ? 5 : 6), scale), 0);
                view.addView(arrow, arrowLp);
            } else if (!useClassicCruisePill) {
                TextView dot = new TextView(context);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(state.color);
                dotBg.setStroke(scaledDp(3, scale), withAlpha(0xFFFFFFFF, 76));
                dot.setBackground(dotBg);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                        scaledDp(arrowSize, scale), scaledDp(arrowSize, scale));
                dotLp.setMargins(0, 0, scaledDp(dynamicIsland ? 5 : 7, scale), 0);
                view.addView(dot, dotLp);
            }

            LinearLayout textColumn = new LinearLayout(context);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            textColumn.setGravity(Gravity.CENTER);

            TextView time = new TextView(context);
            time.setText(String.valueOf(seconds));
            time.setTextColor(Color.WHITE);
            time.setTextSize(scaledSp(useClassicCruisePill ? 14f : (dynamicIsland ? 17f : 21f), scale));
            time.setTypeface(Typeface.DEFAULT_BOLD);
            time.setGravity(Gravity.CENTER);
            textColumn.addView(time, new LinearLayout.LayoutParams(-2, -2));

            view.addView(textColumn, new LinearLayout.LayoutParams(-2, -2));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, scaledDp(pillHeight, scale));
            lp.setMargins(scaledDp(useClassicCruisePill ? 2 : (dynamicIsland ? 2 : 3), scale), scaledDp(1, scale),
                    scaledDp(useClassicCruisePill ? 2 : (dynamicIsland ? 2 : 3), scale), scaledDp(1, scale));
            view.setLayoutParams(lp);
            FontManager.applyToViewTree(context, view);
            return view;
        } finally {
            activeDensity = oldDensity;
        }
    }

    private View buildCompactLightPill(Context context, LightState state, boolean showArrowBadge,
                                        float scale, int seconds, float oldDensity) {
        int circleSize = scaledDp(28, scale);

        FrameLayout circle = new FrameLayout(context);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        circleBg.setColor(withAlpha(state.color, 92));
        circleBg.setStroke(scaledDp(2, scale), state.color);
        circle.setBackground(circleBg);

        boolean cruiseArrow = inCruiseMode && showArrowBadge;
        if (cruiseArrow) {
            View arrow = diyArrowBadge(context, state, scale);
            int arrowSize = scaledDp(20, scale);
            FrameLayout.LayoutParams arrowLp = new FrameLayout.LayoutParams(arrowSize, arrowSize);
            arrowLp.gravity = Gravity.CENTER;
            circle.addView(arrow, arrowLp);
        } else {
            TextView time = new TextView(context);
            time.setText(String.valueOf(seconds));
            time.setTextColor(Color.WHITE);
            time.setTextSize(scaledSp(seconds >= 100 ? 10.5f : 12.5f, scale));
            time.setTypeface(Typeface.DEFAULT_BOLD);
            time.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams timeLp = new FrameLayout.LayoutParams(-1, -1);
            timeLp.gravity = Gravity.CENTER;
            circle.addView(time, timeLp);
        }

        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        view.addView(circle, new LinearLayout.LayoutParams(circleSize, circleSize));
        if (cruiseArrow) {
            TextView time = new TextView(context);
            time.setText(String.valueOf(seconds));
            time.setTextColor(Color.WHITE);
            time.setTextSize(scaledSp(seconds >= 100 ? 10.5f : 12f, scale));
            time.setTypeface(Typeface.DEFAULT_BOLD);
            time.setGravity(Gravity.CENTER);
            time.setIncludeFontPadding(false);
            LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(-2, -2);
            timeLp.setMargins(0, scaledDp(1, scale), 0, 0);
            view.addView(time, timeLp);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(scaledDp(2, scale), scaledDp(1, scale), scaledDp(2, scale), scaledDp(1, scale));
        view.setLayoutParams(lp);
        FontManager.applyToViewTree(context, view);
        activeDensity = oldDensity;
        return view;
    }

    private View diyArrowBadge(Context context, LightState state, float scale) {
        Bitmap bitmap = loadDiyArrowBitmap(state.dir);
        if (bitmap != null) {
            ImageView image = new ImageView(context);
            image.setImageBitmap(bitmap);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            int padding = arrowImagePaddingDp(state.dir);
            image.setPadding(scaledDp(padding, scale), scaledDp(padding, scale),
                    scaledDp(padding, scale), scaledDp(padding, scale));
            return image;
        }

        ImageView image = new ImageView(context);
        image.setImageResource(defaultCruiseArrowResource(state.dir));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        int padding = arrowImagePaddingDp(state.dir);
        image.setPadding(scaledDp(padding, scale), scaledDp(padding, scale),
                scaledDp(padding, scale), scaledDp(padding, scale));
        return image;
    }

    private int defaultCruiseArrowResource(int dir) {
        int souIcon = trafficDirToTurnIcon(dir);
        int souRes = turnIconResource(souIcon);
        if (souRes != 0) {
            return souRes;
        }
        if (dir == 0) {
            return souTurnIconResource(8);
        }
        if (dir == 1 || dir == 5 || dir == 6) {
            return souTurnIconResource(2);
        }
        if (dir == 2 || dir == 3 || dir == 7 || dir == 8) {
            return souTurnIconResource(3);
        }
        return souTurnIconResource(9);
    }

    private int arrowImagePaddingDp(int dir) {
        return dir == 0 ? 3 : 1;
    }

    private int trafficDirToTurnIcon(int dir) {
        if (dir == 0) {
            return 8;
        }
        if (dir == 1 || dir == 5 || dir == 6) {
            return 2;
        }
        if (dir == 2 || dir == 3 || dir == 7 || dir == 8) {
            return 3;
        }
        return 9;
    }

    private Bitmap loadDiyArrowBitmap(int dir) {
        String[] fileNames = diyArrowFileNames(dir);
        if (fileNames.length == 0) {
            return null;
        }
        File diyDir = new File(Environment.getExternalStorageDirectory(), DIY_DIR_NAME);
        try {
            if (!diyDir.isDirectory()) {
                diyDir.mkdirs();
            }
        } catch (Throwable ignored) {
        }
        for (String fileName : fileNames) {
            try {
                File file = new File(diyDir, fileName);
                if (!file.isFile()) {
                    diyArrowCache.remove(fileName);
                    diyArrowModified.remove(fileName);
                    continue;
                }
                long modified = file.lastModified();
                Long cachedModified = diyArrowModified.get(fileName);
                Bitmap cached = diyArrowCache.get(fileName);
                if (cached != null && cachedModified != null && cachedModified == modified && !cached.isRecycled()) {
                    return cached;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) {
                    diyArrowCache.remove(fileName);
                    diyArrowModified.remove(fileName);
                    continue;
                }
                diyArrowCache.put(fileName, bitmap);
                diyArrowModified.put(fileName, modified);
                return bitmap;
            } catch (Throwable t) {
                Log.d(TAG, "load diy arrow failed: " + fileName, t);
            }
        }
        return null;
    }

    private String[] diyArrowFileNames(int dir) {
        String base;
        if (dir == 1 || dir == 5 || dir == 6) {
            base = "cruise_arrow_left";
        } else if (dir == 2 || dir == 3 || dir == 7 || dir == 8) {
            base = "cruise_arrow_right";
        } else if (dir == 4) {
            base = "cruise_arrow_straight";
        } else if (dir == 0) {
            base = "cruise_arrow_uturn";
        } else {
            base = "cruise_arrow_default";
        }
        return new String[]{base + ".png", base + ".webp", base + ".jpg", base + ".jpeg"};
    }

    private int currentLightSeconds(LightState state, long now) {
        if (state == null) {
            return 0;
        }
        long elapsedSeconds = Math.max(0L, (now - state.updatedAt) / 1000L);
        return Math.max(0, state.seconds - (int) elapsedSeconds);
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
        if (icon == 8) {
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

    private boolean isDisplayedYellowLight(int status, int red, int green) {
        return colorForStatus(status, red, green) == 0xFFD49A00;
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

    private String bearingToCompass(int bearing) {
        int normalized = ((bearing % 360) + 360) % 360;
        String[] labels = {"\u5317", "\u4e1c\u5317", "\u4e1c", "\u4e1c\u5357", "\u5357", "\u897f\u5357", "\u897f", "\u897f\u5317"};
        int index = Math.round(normalized / 45f) % labels.length;
        return labels[index];
    }

    private int directionPriority(int dir) {
        if (inCruiseMode) {
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
        boolean compactMode = MainActivity.isDynamicIslandUiEnabled(this);
        if (etaText == null && !compactMode) {
            return;
        }
        String distance = valueString(extras, "ROUTE_REMAIN_DIS_AUTO", "routeRemainDistanceAuto", "distance");
        String time = valueString(extras, "ROUTE_REMAIN_TIME_AUTO", "routeRemainTimeAuto", "remainTime");
        String eta = valueString(extras, "ETA_TEXT", "etaText", "eta", "arrivalTime", "arriveTime");
        String road = valueString(extras, "NEXT_ROAD_NAME", "CUR_ROAD_NAME", "roadName", "curRoadName");
        String destination = valueString(extras, "endPOIName", "END_POI_NAME", "END_POI",
                "DESTINATION_NAME", "DESTINATION", "EXTRA_DESTINATION_NAME", "POINAME");

        int remainMeters = -1;
        if (TextUtils.isEmpty(distance)) {
            int meters = intValue(extras, "ROUTE_REMAIN_DIS", -1);
            if (meters > 0) {
                remainMeters = meters;
                distance = (compactMode || MainActivity.isCardUiEnabled(this)) ? formatDistanceCompact(meters) : formatDistance(meters);
            }
        }
        if (TextUtils.isEmpty(time)) {
            int seconds = intValue(extras, "ROUTE_REMAIN_TIME", -1);
            if (seconds > 0) {
                time = formatDuration(seconds);
            }
        }

        // Card UI: normalize distance/eta text
        if (!TextUtils.isEmpty(distance) && MainActivity.isCardUiEnabled(this)) {
            distance = distance.replace("公里", "km").replace("米", "m");
        }

        StringBuilder text = new StringBuilder();
        if (!TextUtils.isEmpty(distance)) {
            text.append(distance);
        }
        if (!TextUtils.isEmpty(time) && !MainActivity.isCardUiEnabled(this)) {
            if (text.length() > 0) {
                text.append(" \u00b7 ");
            }
            text.append(time);
        }
        if (!TextUtils.isEmpty(eta)) {
            String cleanEta = eta.replace("\u9884\u8ba1", "").replace("\u5230\u8fbe", "\u5230").trim();
            if (!TextUtils.isEmpty(cleanEta)) {
                if (text.length() > 0) {
                    text.append(" \u00b7 ");
                }
                text.append(cleanEta);
            }
        }
        if (!TextUtils.isEmpty(destination) && !MainActivity.isCardUiEnabled(this)) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(destination);
        }

        if (text.length() > 0 || compactMode) {
            if (etaText != null) {
                etaText.setText(text.toString());
            }
            if (clusterEtaText != null) {
                clusterEtaText.setText(text.toString());
            }
            boolean roadIsEmptyDestination = !TextUtils.isEmpty(destination)
                    && ("\u76ee\u7684\u5730".equals(road) || road.equals(destination));
            if (!TextUtils.isEmpty(road) && !roadIsEmptyDestination) {
                currentRoadName = road;
            }
            if (!compactMode && !MainActivity.isDynamicIslandUiEnabled(this)) {
                refreshRoadTitle();
                syncEtaVisibility();
                if (MainActivity.isEtaVisible(this)) {
                    showAnyPanel();
                }
            } else if (MainActivity.isDynamicIslandUiEnabled(this)) {
                // Store ETA info for alternating display
                int remainSec = intValue(extras, "ROUTE_REMAIN_TIME", -1);
                if (remainSec <= 0) {
                    String timeStr = valueString(extras, "ROUTE_REMAIN_TIME_AUTO", "routeRemainTimeAuto", "remainTime");
                    if (!TextUtils.isEmpty(timeStr)) {
                        try {
                            remainSec = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                updateFullModeEtaInfo(distance, remainSec);
                if (!TextUtils.isEmpty(road) && !roadIsEmptyDestination) {
                    if (!inCruiseMode) {
                        updateCompactMarqueeText(compactNavTurnRoadText, road);
                        updateCompactMarqueeText(clusterCompactNavTurnRoadText, road);
                    }
                    if (compactCruiseRoadText != null && inCruiseMode) {
                        if (compactCruiseRoadText != null) compactCruiseRoadText.setText(road);
                    }
                }
            } else if (!TextUtils.isEmpty(road) && !roadIsEmptyDestination) {
                if (!MainActivity.isCardUiEnabled(this) && compactCruiseRoadText != null && inCruiseMode) {
                    compactCruiseRoadText.setText(road);
                }
                updateDynamicIslandLayout();
            }
        }
    }

    private void updateProtocolDetails(Bundle extras) {
        updateAlertDetails(extras);
        updateStatusDetails(extras);
    }

    private void updateAlertDetails(Bundle extras) {
        boolean compactMode = MainActivity.isDynamicIslandUiEnabled(this);
        if (alertText == null && !compactMode && !MainActivity.isCardUiEnabled(this)) {
            return;
        }
        ArrayList<String> parts = new ArrayList<>();
        boolean alertPayload = hasAny(extras, "LIMITED_SPEED", "CAMERA_INDEX", "CAMERA_DIST",
                "CAMERA_SPEED", "CAMERA_TYPE", "SAPA_DIST", "SAPA_NAME", "TRAFFIC_LIGHT_NUM",
                "routeRemainTrafficLightNum");

        int cameraIndex = intValue(extras, "CAMERA_INDEX", 0);
        int cameraDist = intValue(extras, "CAMERA_DIST", -1);
        int cameraType = intValue(extras, "CAMERA_TYPE", -1);
        int cameraSpeed = intValue(extras, "CAMERA_SPEED", -1);
        int limitedSpeed = intValue(extras, "LIMITED_SPEED", -1);
        int displaySpeed = limitedSpeed > 0 ? limitedSpeed : cameraSpeed;
        if (displaySpeed > 0) {
            parts.add("\u9650\u901f " + displaySpeed);
        }

        if (cameraIndex != -1 && cameraDist >= 0) {
            StringBuilder camera = new StringBuilder(cameraTypeName(cameraType));
            camera.append(' ').append(formatDistance(cameraDist));
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
                currentLimitSpeed = -1;
                // Some AMap builds emit sparse edog broadcasts between full payloads.
                // Keep the previous compact widgets until the normal TTL clears them
                // instead of flickering visible/gone on every sparse packet.
                mainHandler.removeCallbacks(alertClear);
                mainHandler.postDelayed(alertClear, ALERT_TTL_MS + 200L);
            }
            return;
        }
        currentLimitSpeed = displaySpeed;
        if (alertText != null) {
            alertText.setText(join(parts, "  \u00b7  "));
        }
        if (clusterAlertText != null && alertText != null) {
            clusterAlertText.setText(alertText.getText());
        }
        populateEdogAlertRow(alertRow, displaySpeed, cameraIndex, cameraDist, cameraType, lightNum);
        populateEdogAlertRow(clusterAlertRow, displaySpeed, cameraIndex, cameraDist, cameraType, lightNum);
        populateCompactWidgetRow(displaySpeed, cameraIndex, cameraDist, cameraType, lightNum);
        refreshAlertCard();
        alertUpdatedAt = System.currentTimeMillis();
        mainHandler.removeCallbacks(alertClear);
        mainHandler.postDelayed(alertClear, ALERT_TTL_MS + 200L);
        syncAlertVisibility();
        if (MainActivity.isAlertVisible(this)) {
            showAnyPanel();
        }
    }

    private void clearAlertDetails() {
        currentLimitSpeed = -1;
        if (alertText != null) {
            alertText.setVisibility(View.GONE);
            alertText.setText("");
        }
        if (clusterAlertText != null) {
            clusterAlertText.setVisibility(View.GONE);
            clusterAlertText.setText("");
        }
        if (!MainActivity.isCardUiEnabled(this)) {
            clearEdogAlertRow(alertRow);
            clearEdogAlertRow(clusterAlertRow);
        }
        clearCompactWidgetRow();
        refreshAlertCard();
        syncAlertVisibility();
        mainHandler.removeCallbacks(alertClear);
    }

    private void populateEdogAlertRow(LinearLayout row, int speed, int cameraIndex,
                                      int cameraDist, int cameraType, int lightNum) {
        if (row == null) {
            return;
        }
        boolean anyVisible = false;
        View speedBox = row.findViewWithTag("speed_box");
        if (speedBox instanceof FrameLayout) {
            FrameLayout frame = (FrameLayout) speedBox;
            if (speed > 0) {
                speedBox.setVisibility(View.VISIBLE);
                if (frame.getChildCount() > 1 && frame.getChildAt(1) instanceof TextView) {
                    ((TextView) frame.getChildAt(1)).setText(String.valueOf(speed));
                }
                anyVisible = true;
            } else {
                speedBox.setVisibility(View.GONE);
            }
        }

        View cameraBox = row.findViewWithTag("camera_box");
        if (cameraBox instanceof LinearLayout) {
            boolean hasCamera = cameraIndex != -1 && cameraDist >= 0;
            if (hasCamera) {
                cameraBox.setVisibility(View.VISIBLE);
                LinearLayout box = (LinearLayout) cameraBox;
                // First child is a FrameLayout (tag="camera_icon_frame") with icon + speed overlay
                View iconFrame = box.findViewWithTag("camera_icon_frame");
                if (iconFrame instanceof FrameLayout) {
                    FrameLayout frame = (FrameLayout) iconFrame;
                    if (frame.getChildCount() > 0 && frame.getChildAt(0) instanceof ImageView) {
                        ((ImageView) frame.getChildAt(0)).setImageResource(edogIconResource(cameraType));
                    }
                    if (frame.getChildCount() > 1 && frame.getChildAt(1) instanceof TextView) {
                        TextView limit = (TextView) frame.getChildAt(1);
                        boolean speedCamera = isSpeedCameraType(cameraType) && speed > 0;
                        limit.setText(speedCamera ? String.valueOf(speed) : "");
                        limit.setVisibility(speedCamera ? View.VISIBLE : View.GONE);
                    }
                } else if (box.getChildCount() > 0 && box.getChildAt(0) instanceof ImageView) {
                    // Old-style direct ImageView (backward compat)
                    ((ImageView) box.getChildAt(0)).setImageResource(edogIconResource(cameraType));
                }
                if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                    ((TextView) box.getChildAt(1)).setText(formatDistance(cameraDist));
                }
                anyVisible = true;
            } else {
                cameraBox.setVisibility(View.GONE);
            }
        }

        View lightBox = row.findViewWithTag("light_box");
        if (lightBox instanceof LinearLayout) {
            if (lightNum > 0) {
                lightBox.setVisibility(View.VISIBLE);
                LinearLayout box = (LinearLayout) lightBox;
                if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                    ((TextView) box.getChildAt(1)).setText(lightNum + "\u4e2a");
                }
                anyVisible = true;
            } else {
                lightBox.setVisibility(View.GONE);
            }
        }
        row.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
    }

    private void clearEdogAlertRow(LinearLayout row) {
        if (row == null) {
            return;
        }
        row.setVisibility(View.GONE);
        View speedBox = row.findViewWithTag("speed_box");
        if (speedBox != null) speedBox.setVisibility(View.GONE);
        View cameraBox = row.findViewWithTag("camera_box");
        if (cameraBox != null) cameraBox.setVisibility(View.GONE);
        View lightBox = row.findViewWithTag("light_box");
        if (lightBox != null) lightBox.setVisibility(View.GONE);
    }

    private void updateStatusDetails(Bundle extras) {
        boolean hasCompactHeadingTarget = compactCruiseDirText != null || clusterCompactCruiseDirText != null;
        if (detailText == null && !hasCompactHeadingTarget) {
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
        if (direction < 0 && !TextUtils.isEmpty(locationJson)) {
            try {
                direction = new JSONObject(locationJson).optInt("bearing", -1);
            } catch (Throwable ignored) {}
        }
        double lat = doubleValue(extras, "CAR_LATITUDE",
                doubleValue(extras, "LAT", doubleValue(extras, "LATITUDE", Double.NaN)));
        double lon = doubleValue(extras, "CAR_LONGITUDE",
                doubleValue(extras, "LON", doubleValue(extras, "LONGITUDE", Double.NaN)));
        boolean showStatusDetails = shouldShowStandbyStatusDetails();
        int roadType = intValue(extras, "ROAD_TYPE", -1);
        if (showStatusDetails && roadType >= 0) {
            currentRoadTypeSummary = roadTypeName(roadType);
        } else {
            currentRoadTypeSummary = "";
        }
        if (showStatusDetails && (direction >= 0 || (!Double.isNaN(lat) && !Double.isNaN(lon) && !(lat == 0.0d && lon == 0.0d)))) {
            StringBuilder car = new StringBuilder();
            if (direction >= 0) {
                car.append("\u8f66\u5934 ").append(bearingToCompass(direction));
            }
            if (!TextUtils.isEmpty(currentRoadTypeSummary)) {
                if (car.length() > 0) {
                    car.append(" \u00b7 ");
                }
                car.append(currentRoadTypeSummary);
            }
            if (!Double.isNaN(lat) && !Double.isNaN(lon) && !(lat == 0.0d && lon == 0.0d)) {
                if (car.length() > 0) {
                    car.append("  ");
                }
                car.append(String.format(java.util.Locale.US, "%.5f, %.5f", lat, lon));
            }
            lines.add(car.toString());
        }
        if (direction >= 0) {
            currentHeadingSummary = bearingToCompass(direction);
        }
        if (MainActivity.isDynamicIslandUiEnabled(this)) {
            updateDynamicIslandCruiseDirectionText(compactCruiseDirText);
            updateDynamicIslandCruiseDirectionText(clusterCompactCruiseDirText);
        } else {
            updateCompactCruiseDirectionText(compactCruiseDirText);
            updateCompactCruiseDirectionText(clusterCompactCruiseDirText);
        }

        String province = valueString(extras, "PROVINCE_NAME", "provinceName");
        String city = valueString(extras, "CITY_NAME", "cityName");
        String district = valueString(extras, "DISTRICT_NAME", "districtName");
        String areaCode = valueString(extras, "AREA_CODE", "areaCode");
        if (showStatusDetails && (!TextUtils.isEmpty(province) || !TextUtils.isEmpty(city) || !TextUtils.isEmpty(district))) {
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
        if (showStatusDetails && !TextUtils.isEmpty(traffic)) {
            lines.add("\u524d\u65b9\u8def\u51b5 " + traffic);
        }

        if (showStatusDetails && (extras.containsKey("EXTRA_MUTE") || extras.containsKey("EXTRA_CASUAL_MUTE"))) {
            boolean mute = intValue(extras, "EXTRA_MUTE", 0) == 1;
            boolean casual = intValue(extras, "EXTRA_CASUAL_MUTE", 0) == 1;
            lines.add("\u64ad\u62a5 " + (mute ? "\u9759\u97f3" : "\u6709\u58f0")
                    + (casual ? " \u00b7 \u4e34\u65f6\u9759\u97f3" : ""));
        }

        if (showStatusDetails && (extras.containsKey("EXTRA_HOME_OR_COMPANY_WHAT")
                || extras.containsKey("EXTRA_HOME_OR_COMPANY_ETA"))) {
            boolean home = booleanValue(extras, "EXTRA_HOME_OR_COMPANY_WHAT", false);
            String eta = valueString(extras, "EXTRA_HOME_OR_COMPANY_ETA");
            lines.add((home ? "\u56de\u5bb6" : "\u53bb\u516c\u53f8")
                    + (TextUtils.isEmpty(eta) ? "" : " " + eta));
        }

        String favorite = valueString(extras, "EXTRA_FAVORITE_MY_LOCATION");
        if (showStatusDetails && !TextUtils.isEmpty(favorite)) {
            lines.add("\u6536\u85cf\u5f53\u524d\u70b9\u5df2\u8fd4\u56de");
        }

        refreshStatusSummary();

        if (lines.isEmpty() || detailText == null) {
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
        cacheLaneData(lanes, advised);
        if (laneBar == null && clusterLaneBar == null) {
            return;
        }
        applyCachedLaneData();
        syncLaneVisibility();
        if (MainActivity.isLaneVisible(this)) {
            showAnyPanel();
        }
    }

    private void hideLaneData() {
        lastLaneData = null;
        lastLaneAdvised = null;
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

    private void cacheLaneData(int[] lanes, boolean[] advised) {
        if (lanes == null || lanes.length == 0) {
            lastLaneData = null;
            lastLaneAdvised = null;
            return;
        }
        lastLaneData = Arrays.copyOf(lanes, lanes.length);
        lastLaneAdvised = advised == null ? null : Arrays.copyOf(advised, advised.length);
    }

    private void applyCachedLaneData() {
        if (lastLaneData == null || lastLaneData.length == 0) {
            return;
        }
        if (laneBar != null) {
            laneBar.setLaneData(lastLaneData, lastLaneAdvised);
        }
        if (clusterLaneBar != null) {
            clusterLaneBar.setLaneData(lastLaneData, lastLaneAdvised);
        }
    }

    private boolean updateLaneFromJson(String json) {
        try {
            JSONObject object = new JSONObject(json);
            boolean enabled = object.optBoolean("drive_way_enabled");
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
                advised[i] = item.optBoolean("trafficLaneAdvised");
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

    // --- Dynamic island mode helpers ---


    private void updateCardLayout() {
        if (!MainActivity.isCardUiEnabled(this)) return;
        boolean isNav = !inCruiseMode && currentTurnIcon > 0;
        boolean isCruise = inCruiseMode;
        boolean isEmpty = !isNav && !isCruise;

        // Toggle main panel containers
        if (cardCruiseRow1 != null) cardCruiseRow1.setVisibility(isCruise ? View.VISIBLE : View.GONE);
        if (cardCruiseRow2 != null) cardCruiseRow2.setVisibility(isCruise ? View.VISIBLE : View.GONE);
        if (cardNavArea != null) cardNavArea.setVisibility(isNav ? View.VISIBLE : View.GONE);

        // Swap shared field references based on active mode
        if (isCruise) {
            if (cardCruiseLaneBar != null) laneBar = cardCruiseLaneBar;
            if (cardCruiseLightRow != null) lightRow = cardCruiseLightRow;
            if (cardCruiseEdogRow != null) alertRow = cardCruiseEdogRow;
            // Update cruise direction and road text
            if (compactCruiseRoadText != null) {
                String road = currentRoadName;
                if (!TextUtils.isEmpty(road)) {
                    compactCruiseRoadText.setText("  " + road);
                }
            }
            if (compactCruiseDirText != null) {
                updateCompactCruiseDirectionText(compactCruiseDirText);
            }
        } else {
            if (cardNavLaneBar != null) laneBar = cardNavLaneBar;
            if (cardNavLightRow != null) lightRow = cardNavLightRow;
            if (cardNavEdogRow != null) alertRow = cardNavEdogRow;
        }

        // Handle empty state: collapse to small circle
        if (panel != null) {
            if (isEmpty) {
                int circlePx = scaledDp(18, overlayScale);
                panel.setMinimumWidth(circlePx);
                panel.setMinimumHeight(circlePx);
                int smallPad = scaledDp(2, overlayScale);
                panel.setPadding(smallPad, smallPad, smallPad, smallPad);
            } else {
                int normalPadH = scaledDp(5, overlayScale);
                int normalPadTop = scaledDp(4, overlayScale);
                int normalPadBottom = scaledDp(2, overlayScale);
                panel.setPadding(normalPadH, normalPadTop, normalPadH, normalPadBottom);
                panel.setMinimumWidth(0);
                if (isCruise) {
                    if (mainPanelWidthUnlock != null) {
                        mainHandler.removeCallbacks(mainPanelWidthUnlock);
                        mainPanelWidthUnlock = null;
                    }
                    mainPanelBaseMinWidth = -1;
                    mainPanelHeldMinWidth = 0;
                    // Height: full=76dp (4+32+3+35+2), single-row=38dp (4+32+2)
                    boolean row2HasContent = (cardCruiseLaneBar != null && cardCruiseLaneBar.getVisibility() == View.VISIBLE)
                            || (cardCruiseLightRow != null && cardCruiseLightRow.getChildCount() > 0);
                    if (cardCruiseRow2 != null) {
                        cardCruiseRow2.setVisibility(row2HasContent ? View.VISIBLE : View.GONE);
                    }
                    int minH = row2HasContent ? scaledDp(76, overlayScale) : scaledDp(38, overlayScale);
                    panel.setMinimumHeight(minH);
                    panel.setVisibility(View.VISIBLE);
                    panel.setMinimumWidth(0);
                    panel.requestLayout();
                    // Force WindowManager to immediately resize the window
                    windowManager.updateViewLayout(panel, params);
                } else {
                    // Nav mode: fixed 76dp height matching cruise
                    resetMainPanelWidthStabilizer();
                    mainPanelBaseMinWidth = -1;
                    mainPanelHeldMinWidth = 0;
                    panel.setMinimumHeight(scaledDp(76, overlayScale));
                    panel.setMinimumWidth(0);
                    panel.setVisibility(View.VISIBLE);
                    panel.requestLayout();
                    windowManager.updateViewLayout(panel, params);
                }
            }
        }

        // Toggle cluster containers
        if (clusterCardCruiseRow1 != null) clusterCardCruiseRow1.setVisibility(isCruise ? View.VISIBLE : View.GONE);
        if (clusterCardCruiseRow2 != null) clusterCardCruiseRow2.setVisibility(isCruise ? View.VISIBLE : View.GONE);
        if (clusterCardNavArea != null) clusterCardNavArea.setVisibility(isNav ? View.VISIBLE : View.GONE);

        // Swap cluster shared field references
        if (isCruise) {
            if (clusterCardCruiseLaneBar != null) clusterLaneBar = clusterCardCruiseLaneBar;
            if (clusterCardCruiseLightRow != null) clusterLightRow = clusterCardCruiseLightRow;
            if (clusterCardCruiseEdogRow != null) clusterAlertRow = clusterCardCruiseEdogRow;
        } else {
            if (clusterCardNavLaneBar != null) clusterLaneBar = clusterCardNavLaneBar;
            if (clusterCardNavLightRow != null) clusterLightRow = clusterCardNavLightRow;
            if (clusterCardNavEdogRow != null) clusterAlertRow = clusterCardNavEdogRow;
        }

        // Handle cluster empty state
        if (clusterPanel != null) {
            if (isEmpty) {
                int circlePx = scaledDp(18, clusterScale > 0 ? clusterScale : overlayScale);
                clusterPanel.setMinimumWidth(circlePx);
                clusterPanel.setMinimumHeight(circlePx);
                int smallPad = scaledDp(2, clusterScale > 0 ? clusterScale : overlayScale);
                clusterPanel.setPadding(smallPad, smallPad, smallPad, smallPad);
            } else {
                clusterPanel.setMinimumWidth(0);
                int normalPadH = scaledDp(5, clusterScale > 0 ? clusterScale : overlayScale);
                int normalPadTop = scaledDp(4, clusterScale > 0 ? clusterScale : overlayScale);
                int normalPadBottom = scaledDp(2, clusterScale > 0 ? clusterScale : overlayScale);
                clusterPanel.setPadding(normalPadH, normalPadTop, normalPadH, normalPadBottom);
                if (isCruise) {
                    float cs = clusterScale > 0 ? clusterScale : overlayScale;
                    boolean cRow2HasContent = (clusterCardCruiseLaneBar != null && clusterCardCruiseLaneBar.getVisibility() == View.VISIBLE)
                            || (clusterCardCruiseLightRow != null && clusterCardCruiseLightRow.getChildCount() > 0);
                    if (clusterCardCruiseRow2 != null) {
                        clusterCardCruiseRow2.setVisibility(cRow2HasContent ? View.VISIBLE : View.GONE);
                    }
                    clusterPanel.setMinimumHeight(scaledDp(cRow2HasContent ? 76 : 38, cs));
                    clusterPanel.setMinimumWidth(0);
                    clusterPanel.requestLayout();
                    clusterWindowManager.updateViewLayout(clusterPanel, clusterParams);
                } else {
                    float cs = clusterScale > 0 ? clusterScale : overlayScale;
                    clusterPanel.setMinimumHeight(scaledDp(76, cs));
                    clusterPanel.setMinimumWidth(0);
                    clusterPanel.requestLayout();
                    clusterWindowManager.updateViewLayout(clusterPanel, clusterParams);
                }
            }
        }
    }

    // --- Overspeed warning (all UI styles) ---

    private void updateOverspeedWarning() {
        int v = currentVehicleSpeed;
        int l = currentLimitSpeed;
        if (l <= 0 || v < 0 || v <= l) {
            stopOverspeedBlink();
            return;
        }
        float ratio = (float) v / l;
        int color;
        if (ratio > 1.20f) {
            color = 0xFFB71C1C; // deep red
        } else if (ratio > 1.10f) {
            color = 0xFFF44336; // red
        } else {
            color = 0xFFFFEB3B; // yellow
        }
        if (overspeedBlinks != null && overspeedColor == color) return; // same level
        startOverspeedBlink(color);
    }

    private void startOverspeedBlink(int color) {
        overspeedColor = color;
        if (overspeedBlinks != null) return; // already blinking, just color updated above
        overspeedBlinks = new Runnable() {
            @Override
            public void run() {
                overspeedBlinkOn = !overspeedBlinkOn;
                applyOverspeedBorder();
                mainHandler.postDelayed(this, 250);
            }
        };
        overspeedBlinkOn = true;
        applyOverspeedBorder();
        mainHandler.postDelayed(overspeedBlinks, 250);
    }

    private void stopOverspeedBlink() {
        if (overspeedBlinks != null) {
            mainHandler.removeCallbacks(overspeedBlinks);
            overspeedBlinks = null;
        }
        if (overspeedBlinkOn) {
            overspeedBlinkOn = false;
            restoreNormalBorder();
        }
        overspeedColor = 0;
    }

    private void applyOverspeedBorder() {
        if (overspeedBlinkOn && overspeedColor != 0) {
            int strokeW = scaledDp(5, overlayScale);
            if (panelBackground != null) {
                panelBackground.setStroke(strokeW, overspeedColor);
                if (panel != null) panel.invalidate();
            }
            if (clusterPanelBackground != null && clusterPanel != null) {
                float cs = clusterScale > 0 ? clusterScale : overlayScale;
                clusterPanelBackground.setStroke(scaledDp(5, cs), overspeedColor);
                clusterPanel.invalidate();
            }
        } else {
            restoreNormalBorder();
        }
    }

    private void restoreNormalBorder() {
        int opacity = MainActivity.getBackgroundOpacityPercent(this);
        if (panelBackground != null) {
            panelBackground.setStroke(scaledDp(1, overlayScale),
                    withAlpha(0xFFFFFFFF, MainActivity.strokeOpacityForBackground(opacity)));
            if (panel != null) panel.invalidate();
        }
        if (clusterPanelBackground != null && clusterPanel != null) {
            float cs = clusterScale > 0 ? clusterScale : overlayScale;
            clusterPanelBackground.setStroke(scaledDp(1, cs),
                    withAlpha(0xFFFFFFFF, MainActivity.strokeOpacityForBackground(opacity)));
            clusterPanel.invalidate();
        }
    }

    private void updateDynamicIslandLayout() {
        if (MainActivity.isCardUiEnabled(this)) {
            updateCardLayout();
            return;
        }
        if (!isDynamicIslandOrCard()) {
            return;
        }
        boolean isNav = MainActivity.isTurnVisible(this) && !inCruiseMode && currentTurnIcon > 0;
        boolean isCruise = inCruiseMode;

        // Only set visibility when state changes to avoid flickering
        if (navTurnBox != null) {
            int targetVis = isNav ? View.VISIBLE : View.GONE;
            if (navTurnBox.getVisibility() != targetVis) {
                navTurnBox.setVisibility(targetVis);
                // Ensure alternator starts when nav first becomes visible
                if (isNav) {
                    ensureFullModeAlternator();
                }
            }
        }
        if (clusterNavTurnBox != null) {
            int targetVis = isNav ? View.VISIBLE : View.GONE;
            if (clusterNavTurnBox.getVisibility() != targetVis) {
                clusterNavTurnBox.setVisibility(targetVis);
            }
        }
        // Cruise: only set parent visibility on mode change, update text always
        if (compactCruiseRoadText != null) {
            View cruiseParent = (View) compactCruiseRoadText.getParent();
            if (cruiseParent != null) {
                int targetVis = isCruise ? View.VISIBLE : View.GONE;
                if (cruiseParent.getVisibility() != targetVis) {
                    cruiseParent.setVisibility(targetVis);
                }
            }
            if (isCruise) {
                String road = !TextUtils.isEmpty(currentRoadName) ? currentRoadName : "";
                if (!TextUtils.isEmpty(road)) {
                    if (compactCruiseRoadText != null) compactCruiseRoadText.setText(road);
                }
                updateDynamicIslandCruiseDirectionText(compactCruiseDirText);
            }
        }
        if (clusterCompactCruiseRoadText != null) {
            View cruiseParent = (View) clusterCompactCruiseRoadText.getParent();
            if (cruiseParent != null) {
                int targetVis = isCruise ? View.VISIBLE : View.GONE;
                if (cruiseParent.getVisibility() != targetVis) {
                    cruiseParent.setVisibility(targetVis);
                }
            }
            if (isCruise) {
                String road = !TextUtils.isEmpty(currentRoadName) ? currentRoadName : "";
                if (!TextUtils.isEmpty(road)) {
                    if (clusterCompactCruiseRoadText != null) clusterCompactCruiseRoadText.setText(road);
                }
                updateDynamicIslandCruiseDirectionText(clusterCompactCruiseDirText);
            }
        }
    }

    private void updateDynamicIslandCruiseDirectionText(TextView view) {
        if (view == null) {
            return;
        }
        if (!TextUtils.isEmpty(currentHeadingSummary)) {
            view.setText("【 " + currentHeadingSummary + " 】");
        } else if (TextUtils.isEmpty(view.getText())) {
            view.setText("【 -- 】");
        }
        if (view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
    }

    private void startCompactBreathing() {
        // No-op: removed compact breathing
    }

    private void stopCompactBreathing() {
        if (navTurnIconView != null) {
            navTurnIconView.setAlpha(1f);
        }
        if (clusterNavTurnIconView != null) {
            clusterNavTurnIconView.setAlpha(1f);
        }
    }

    // --- Dynamic island alternator helpers ---

    private void ensureFullModeAlternator() {
        if (fullModeAlternator != null) {
            return; // Already running, don't restart
        }
        if (!MainActivity.isDynamicIslandUiEnabled(this)) {
            return;
        }
        if (navTurnBox == null || navTurnBox.getVisibility() != View.VISIBLE) {
            return;
        }
        startFullModeAlternator();
    }

    private void startFullModeAlternator() {
        stopFullModeAlternator();
        if (!MainActivity.isDynamicIslandUiEnabled(this)) {
            return;
        }
        if (navTurnBox == null || navTurnBox.getVisibility() != View.VISIBLE) {
            return;
        }
        fullModeShowEta = false;
        updateFullModeAlternatingDisplay();
        fullModeAlternator = new Runnable() {
            @Override
            public void run() {
                fullModeShowEta = !fullModeShowEta;
                updateFullModeAlternatingDisplay();
                long delay = fullModeShowEta ? FULL_MODE_ETA_MS : FULL_MODE_TURN_MS;
                mainHandler.postDelayed(this, delay);
            }
        };
        mainHandler.postDelayed(fullModeAlternator, FULL_MODE_TURN_MS);
    }

    private void stopFullModeAlternator() {
        if (fullModeAlternator != null) {
            mainHandler.removeCallbacks(fullModeAlternator);
            fullModeAlternator = null;
        }
        fullModeShowEta = false;
    }

    private void updateFullModeAlternatingDisplay() {
        if (fullModeTurnInfoCol == null || fullModeEtaInfoCol == null) {
            return;
        }
        if (navTurnBox == null || navTurnBox.getVisibility() != View.VISIBLE) {
            return;
        }

        // Simple toggle: respect fullModeShowEta state directly
        fullModeTurnInfoCol.setVisibility(fullModeShowEta ? View.GONE : View.VISIBLE);
        fullModeEtaInfoCol.setVisibility(fullModeShowEta ? View.VISIBLE : View.GONE);

        // Update cluster too
        if (fullModeClusterTurnInfoCol != null && fullModeClusterEtaInfoCol != null
                && clusterNavTurnBox != null && clusterNavTurnBox.getVisibility() == View.VISIBLE) {
            fullModeClusterTurnInfoCol.setVisibility(fullModeShowEta ? View.GONE : View.VISIBLE);
            fullModeClusterEtaInfoCol.setVisibility(fullModeShowEta ? View.VISIBLE : View.GONE);
        }
    }

    private void updateFullModeEtaInfo(String remainDistText, int remainSeconds) {
        if (!MainActivity.isDynamicIslandUiEnabled(this)) {
            return;
        }
        // Only update when valid data exists - don't overwrite with empty
        boolean hasDist = !TextUtils.isEmpty(remainDistText);
        boolean hasTime = remainSeconds > 0;

        if (hasDist) {
            String dist = remainDistText.replace("\u5343\u7c73", "km")
                    .replace("\u516c\u91cc", "km")
                    .replace("\u7c73", "m");
            String text = "\u4f59" + dist;
            if (fullModeEtaRemainDist != null) {
                fullModeEtaRemainDist.setText(text);
            }
            if (fullModeClusterEtaRemainDist != null) {
                fullModeClusterEtaRemainDist.setText(text);
            }
        }
        if (hasTime) {
            long arriveMillis = System.currentTimeMillis() + remainSeconds * 1000L;
            java.util.Calendar arrive = java.util.Calendar.getInstance();
            arrive.setTimeInMillis(arriveMillis);
            int hour = arrive.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = arrive.get(java.util.Calendar.MINUTE);
            String text = String.format(java.util.Locale.US, "%d:%02d\u5230\u8fbe", hour, minute);
            if (fullModeEtaArriveTime != null) {
                fullModeEtaArriveTime.setText(text);
            }
            if (fullModeClusterEtaArriveTime != null) {
                fullModeClusterEtaArriveTime.setText(text);
            }
        }
        // Ensure alternator is running when ETA data arrives during navigation
        if (hasDist && !inCruiseMode && currentTurnIcon > 0
                && navTurnBox != null && navTurnBox.getVisibility() == View.VISIBLE) {
            ensureFullModeAlternator();
        }
    }

    private void populateCompactWidgetRow(int speed, int cameraIndex,
                                          int cameraDist, int cameraType, int lightNum) {
        populateOneCompactWidgetRow(compactWidgetRow, this, overlayScale, speed, cameraIndex,
                cameraDist, cameraType, lightNum);
        if (clusterContext != null) {
            populateOneCompactWidgetRow(clusterCompactWidgetRow, clusterContext, clusterScale, speed,
                    cameraIndex, cameraDist, cameraType, lightNum);
        }
    }

    private void populateOneCompactWidgetRow(LinearLayout row, Context context, float scale, int speed,
                                             int cameraIndex, int cameraDist, int cameraType, int lightNum) {
        if (row == null) {
            return;
        }
        if (!MainActivity.isAlertVisible(this)) {
            row.setVisibility(View.GONE);
            return;
        }
        ensureCompactWidgetChildren(row, context, scale);
        boolean anyVisible = false;

        View speedBox = row.findViewWithTag("speed_box");
        if (speedBox instanceof FrameLayout) {
            if (speed > 0) {
                speedBox.setVisibility(View.VISIBLE);
                FrameLayout frame = (FrameLayout) speedBox;
                if (frame.getChildCount() > 1 && frame.getChildAt(1) instanceof TextView) {
                    ((TextView) frame.getChildAt(1)).setText(String.valueOf(speed));
                }
                anyVisible = true;
            } else {
                speedBox.setVisibility(View.GONE);
            }
        }

        View cameraBox = row.findViewWithTag("camera_box");
        if (cameraBox instanceof LinearLayout) {
            boolean hasCamera = cameraIndex != -1 && cameraDist >= 0;
            if (hasCamera) {
                cameraBox.setVisibility(View.VISIBLE);
                LinearLayout box = (LinearLayout) cameraBox;
                View iconFrame = box.findViewWithTag("camera_icon_frame");
                if (iconFrame instanceof FrameLayout) {
                    FrameLayout frame = (FrameLayout) iconFrame;
                    if (frame.getChildCount() > 0 && frame.getChildAt(0) instanceof ImageView) {
                        ((ImageView) frame.getChildAt(0)).setImageResource(edogIconResource(cameraType));
                    }
                    if (frame.getChildCount() > 1 && frame.getChildAt(1) instanceof TextView) {
                        TextView limit = (TextView) frame.getChildAt(1);
                        boolean speedCamera = isSpeedCameraType(cameraType) && speed > 0;
                        limit.setText(speedCamera ? String.valueOf(speed) : "");
                        limit.setVisibility(speedCamera ? View.VISIBLE : View.GONE);
                    }
                }
                if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                    ((TextView) box.getChildAt(1)).setText(formatDistanceCompact(cameraDist));
                }
                anyVisible = true;
            } else {
                cameraBox.setVisibility(View.GONE);
            }
        }

        View lightBox = row.findViewWithTag("light_box");
        if (lightBox instanceof LinearLayout) {
            if (lightNum > 0) {
                lightBox.setVisibility(View.VISIBLE);
                LinearLayout box = (LinearLayout) lightBox;
                if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
                    ((TextView) box.getChildAt(1)).setText(lightNum + "\u4e2a");
                }
                anyVisible = true;
            } else {
                lightBox.setVisibility(View.GONE);
            }
        }

        row.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
    }

    private void ensureCompactWidgetChildren(LinearLayout row, Context context, float scale) {
        if (row == null || row.getChildCount() > 0) {
            return;
        }
        int iconSize = scaledDp(20, scale);

        FrameLayout speedBox = new FrameLayout(context);
        speedBox.setTag("speed_box");
        ImageView speedIcon = new ImageView(context);
        speedIcon.setImageResource(R.drawable.widget_drawable_auto_ic_edog_limit_speed_loading);
        speedIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        speedBox.addView(speedIcon, new FrameLayout.LayoutParams(iconSize, iconSize));
        TextView speedText = new TextView(context);
        speedText.setTextColor(0xFFDC2626);
        speedText.setTextSize(scaledSp(9f, scale));
        speedText.setTypeface(Typeface.DEFAULT_BOLD);
        speedText.setGravity(Gravity.CENTER);
        speedBox.addView(speedText, new FrameLayout.LayoutParams(iconSize, iconSize));
        speedBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.setMargins(0, 0, scaledDp(3, scale), 0);
        row.addView(speedBox, slp);

        LinearLayout cameraBox = new LinearLayout(context);
        cameraBox.setTag("camera_box");
        cameraBox.setOrientation(LinearLayout.HORIZONTAL);
        cameraBox.setGravity(Gravity.CENTER_VERTICAL);
        cameraBox.setVisibility(View.GONE);
        FrameLayout cameraIconFrame = new FrameLayout(context);
        cameraIconFrame.setTag("camera_icon_frame");
        ImageView cameraIcon = new ImageView(context);
        cameraIcon.setImageResource(R.drawable.widget_drawable_auto_ic_edog_camera_loading);
        cameraIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cameraIconFrame.addView(cameraIcon, new FrameLayout.LayoutParams(iconSize, iconSize));
        TextView cameraSpeed = new TextView(context);
        cameraSpeed.setTextColor(0xFFDC2626);
        cameraSpeed.setTextSize(scaledSp(8f, scale));
        cameraSpeed.setTypeface(Typeface.DEFAULT_BOLD);
        cameraSpeed.setGravity(Gravity.CENTER);
        cameraSpeed.setVisibility(View.GONE);
        cameraIconFrame.addView(cameraSpeed, new FrameLayout.LayoutParams(iconSize, iconSize));
        cameraBox.addView(cameraIconFrame, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView distText = new TextView(context);
        distText.setTextColor(primaryTextColor());
        distText.setTextSize(scaledSp(9f, scale));
        distText.setTypeface(Typeface.DEFAULT_BOLD);
        distText.setSingleLine(true);
        cameraBox.addView(distText, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.setMargins(0, 0, scaledDp(4, scale), 0);
        row.addView(cameraBox, clp);

        LinearLayout lightBox = new LinearLayout(context);
        lightBox.setTag("light_box");
        lightBox.setOrientation(LinearLayout.HORIZONTAL);
        lightBox.setGravity(Gravity.CENTER_VERTICAL);
        lightBox.setVisibility(View.GONE);
        ImageView lightIcon = new ImageView(context);
        lightIcon.setImageResource(R.drawable.widget_drawable_auto_ic_edog_traffic_loading);
        lightIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lightBox.addView(lightIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        TextView lightText = new TextView(context);
        lightText.setTextColor(primaryTextColor());
        lightText.setTextSize(scaledSp(9f, scale));
        lightText.setTypeface(Typeface.DEFAULT_BOLD);
        lightText.setSingleLine(true);
        lightBox.addView(lightText, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.setMargins(0, 0, scaledDp(4, scale), 0);
        row.addView(lightBox, llp);

        FontManager.applyToViewTree(context, row);
    }

    private boolean isSpeedCameraType(int type) {
        return type == 0 || type == 2 || type == 3 || type == 7 || type == 10 || type == 11;
    }

    private void clearCompactWidgetRow() {
        if (compactWidgetRow != null) {
            compactWidgetRow.removeAllViews();
            compactWidgetRow.setVisibility(View.GONE);
        }
        if (clusterCompactWidgetRow != null) {
            clusterCompactWidgetRow.removeAllViews();
            clusterCompactWidgetRow.setVisibility(View.GONE);
        }
    }

    private String formatDistance(int meters) {
        if (meters >= 1000) {
            float km = meters / 1000f;
            return String.format(java.util.Locale.US, "%.1f\u516c\u91cc", km);
        }
        return meters + "\u7c73";
    }

    private String formatDistanceCompact(int meters) {
        if (meters >= 1000) {
            float km = meters / 1000f;
            if (km >= 10f) {
                return String.format(java.util.Locale.US, "%.0fkm", km);
            }
            return String.format(java.util.Locale.US, "%.1fkm", km);
        }
        return meters + "m";
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
            case 5:
                return "\u5e94\u6025\u8f66\u9053";
            case 6:
                return "\u975e\u673a\u52a8\u8f66\u9053";
            case 11:
                return "ETC\u6d4b\u901f";
            case 12:
                return "\u538b\u7ebf";
            case 13:
                return "\u4eba\u884c\u9053";
            case 14:
                return "\u76d1\u63a7";
            case 15:
                return "\u95ef\u7ea2\u706f";
            case 16:
                return "\u516c\u4ea4\u9053";
            case 17:
                return "\u5e94\u6025\u8f66\u9053";
            case 18:
                return "\u5b89\u5168\u5e26";
            case 19:
                return "\u624b\u673a";
            case 20:
                return "\u975e\u673a\u52a8\u8f66\u9053";
            case 21:
                return "\u8fdd\u505c";
            case 22:
                return "\u706f\u5149";
            case 23:
                return "\u76d1\u63a7";
            case 24:
                return "\u9e23\u7b1b";
            case 25:
                return "\u9006\u884c";
            case 26:
                return "\u94c1\u8def";
            case 27:
                return "\u4e0d\u6309\u5bfc\u5411\u8f66\u9053";
            case 28:
                return "\u8f66\u8ddd";
            case 29:
                return "HOV";
            case 30:
                return "\u8fdd\u7ae0\u6293\u62cd";
            default:
                return "\u7535\u5b50\u773c";
        }
    }

    private int edogIconResource(int type) {
        switch (type) {
            case 0:
                return R.drawable.widget_drawable_auto_ic_edog_limit_speed_loading;
            case 2:
            case 15:
                return R.drawable.widget_drawable_auto_ic_edog_traffic_loading;
            case 4:
            case 16:
                return R.drawable.widget_drawable_auto_ic_edog_bus_loading;
            case 5:
            case 17:
                return R.drawable.widget_drawable_auto_ic_edog_emergency_line_loading;
            case 6:
            case 20:
                return R.drawable.widget_drawable_auto_ic_edog_bicycle_lane_loading;
            case 11:
                return R.drawable.widget_drawable_auto_ic_edog_speed_etc_loading;
            case 12:
                return R.drawable.widget_drawable_auto_ic_edog_line_loading;
            case 13:
                return R.drawable.widget_drawable_auto_ic_edog_sidewalk_loading;
            case 18:
                return R.drawable.widget_drawable_auto_ic_edog_seatbelt_loading;
            case 19:
                return R.drawable.widget_drawable_auto_ic_edog_phone_loading;
            case 21:
                return R.drawable.widget_drawable_auto_ic_edog_parking_loading;
            case 22:
                return R.drawable.widget_drawable_auto_ic_edog_lamp;
            case 24:
                return R.drawable.widget_drawable_auto_ic_edog_speaker_loading;
            case 25:
                return R.drawable.widget_drawable_auto_ic_edog_reverse;
            case 26:
                return R.drawable.widget_drawable_auto_ic_edog_railway;
            case 27:
                return R.drawable.widget_drawable_auto_ic_edog_tail;
            case 28:
                return R.drawable.widget_drawable_auto_ic_edog_space;
            case 29:
                return R.drawable.widget_drawable_auto_ic_edog_hov;
            case 30:
                return R.drawable.widget_drawable_auto_ic_edog_recycle;
            case 1:
            case 3:
            case 14:
            case 23:
            default:
                return R.drawable.widget_drawable_auto_ic_edog_camera_loading;
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
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                ensureNotificationChannel(nm);
            }
            builder = createNotificationBuilderWithChannel();
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

    private void ensureNotificationChannel(NotificationManager notificationManager) {
        try {
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Constructor<?> ctor = channelClass.getConstructor(String.class, CharSequence.class, int.class);
            Object channel = ctor.newInstance(CHANNEL_ID, "AMap Companion", 2);
            notificationManager.getClass()
                    .getMethod("createNotificationChannel", channelClass)
                    .invoke(notificationManager, channel);
        } catch (Throwable ignored) {
        }
    }

    private Notification.Builder createNotificationBuilderWithChannel() {
        try {
            Constructor<Notification.Builder> ctor =
                    Notification.Builder.class.getConstructor(Context.class, String.class);
            return ctor.newInstance(this, CHANNEL_ID);
        } catch (Throwable ignored) {
            return new Notification.Builder(this);
        }
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
        float density = activeDensity > 0f ? activeDensity : getResources().getDisplayMetrics().density;
        return (int) (value * scale * density + 0.5f);
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
        long ttlMs;
    }
}
