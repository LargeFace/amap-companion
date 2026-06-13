package com.autonavi.companion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

public class TmcProgressBar extends Drawable {
    private static final int COLOR_PASSED = 0xFF666666;
    private static final int COLOR_SMOOTH = 0xFF1ABF54;
    private static final int COLOR_SLOW = 0xFFFFD600;
    private static final int COLOR_CONGESTED = 0xFFFF1744;
    private static final int COLOR_SEVERE = 0xFFB71C1C;
    private static final int COLOR_BLUE = 0xFF2196F3;
    private static final int COLOR_CYAN = 0xFF007D5D;
    private static final int COLOR_BACKGROUND = 0xFF333333;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final Bitmap carBitmap;
    private float density;

    private int totalDistance;
    private int finishDistance;
    private int[] segmentStatuses;
    private int[] segmentDistances;
    private int segmentCount;
    private boolean hasData;
    private boolean capsuleInsetMode;
    private float horizontalInsetPx = -1f;
    private boolean drawEnabled = true;

    public TmcProgressBar(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        carBitmap = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.navigation_widget_icon_car_position_blue);
    }

    public boolean updateTmcData(String tmcJson) {
        if (TextUtils.isEmpty(tmcJson)) {
            clear();
            return false;
        }
        try {
            JSONObject root = new JSONObject(tmcJson);
            int total = root.optInt("total_distance", root.optInt("totalDistance", 0));
            int finished = root.optInt("finish_distance", root.optInt("finishDistance", 0));
            JSONArray tmcInfo = root.optJSONArray("tmc_info");
            if (tmcInfo == null) {
                tmcInfo = root.optJSONArray("tmcInfo");
            }
            if (total <= 0 || tmcInfo == null || tmcInfo.length() == 0) {
                clear();
                return false;
            }

            totalDistance = total;
            finishDistance = Math.max(0, Math.min(finished, total));
            segmentCount = tmcInfo.length();
            segmentStatuses = new int[segmentCount];
            segmentDistances = new int[segmentCount];
            int distanceSum = 0;
            for (int i = 0; i < segmentCount; i++) {
                JSONObject seg = tmcInfo.getJSONObject(i);
                segmentStatuses[i] = seg.optInt("tmc_status", seg.optInt("tmcStatus", 0));
                int dist = Math.max(0, seg.optInt("tmc_segment_distance",
                        seg.optInt("tmcSegmentDistance", 0)));
                segmentDistances[i] = dist;
                distanceSum += dist;
            }
            if (distanceSum <= 0) {
                clear();
                return false;
            }
            hasData = true;
            invalidateSelf();
            return true;
        } catch (Throwable ignored) {
            clear();
            return false;
        }
    }

    public void clear() {
        hasData = false;
        totalDistance = 0;
        finishDistance = 0;
        segmentCount = 0;
        segmentStatuses = null;
        segmentDistances = null;
        invalidateSelf();
    }

    public void setCapsuleInsetMode(boolean enabled) {
        capsuleInsetMode = enabled;
        invalidateSelf();
    }

    public void setHorizontalInsetPx(float insetPx) {
        horizontalInsetPx = Math.max(0f, insetPx);
        invalidateSelf();
    }

    /** Override the display density used for dp calculations (e.g. cluster display). */
    public void setDensityOverride(float d) {
        density = d;
        invalidateSelf();
    }

    /** When false, draw() is a no-op but data collection continues. */
    public void setDrawEnabled(boolean enabled) {
        if (drawEnabled != enabled) {
            drawEnabled = enabled;
            invalidateSelf();
        }
    }

    public boolean isDrawEnabled() {
        return drawEnabled;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!drawEnabled) return;
        Rect bounds = getBounds();
        int width = bounds.width();
        if (width <= 0 || !hasData) {
            return;
        }

        float iconSize = dp(8);
        float barHeight = dp(4);
        float inset = capsuleInsetMode ? bounds.height() * 0.5f
                : (horizontalInsetPx >= 0f ? horizontalInsetPx : dp(14));
        inset = Math.max(0f, Math.min(inset, Math.max(0f, width * 0.5f - dp(2))));
        float left = bounds.left + inset;
        float right = bounds.right - inset;
        if (right <= left) {
            left = bounds.left;
            right = bounds.right;
        }
        float barWidth = right - left;
        float barTop = bounds.top;
        float barBottom = barTop + barHeight;
        float iconTop = barTop + (barHeight - iconSize) * 0.5f;

        clipPath.reset();
        clipPath.addRoundRect(left, barTop, right, barBottom, barHeight * 0.5f, barHeight * 0.5f,
                Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        if (totalDistance <= 0 || segmentCount <= 0) {
            paint.setColor(COLOR_BACKGROUND);
            canvas.drawRect(left, barTop, right, barBottom, paint);
        } else {
            float x = left;
            int distanceSum = 0;
            for (int i = 0; i < segmentCount; i++) {
                distanceSum += segmentDistances[i];
            }
            int basis = distanceSum > 0 ? distanceSum : totalDistance;
            for (int i = 0; i < segmentCount; i++) {
                float segWidth = (segmentDistances[i] / (float) basis) * barWidth;
                float segRight = (i == segmentCount - 1) ? right : x + segWidth;
                if (segRight > x) {
                    paint.setColor(getStatusColor(segmentStatuses[i]));
                    canvas.drawRect(x, barTop, segRight, barBottom, paint);
                }
                x += segWidth;
            }
        }
        canvas.restore();

        if (hasData && carBitmap != null && totalDistance > 0) {
            float carX = left + (finishDistance / (float) totalDistance) * barWidth;
            float half = iconSize * 0.5f;
            carX = Math.max(left + half, Math.min(carX, right - half));
            RectF dst = new RectF(carX - half, iconTop, carX + half, iconTop + iconSize);
            canvas.drawBitmap(carBitmap, null, dst, paint);
        }
    }

    private int getStatusColor(int status) {
        switch (status) {
            case 10:
                return COLOR_PASSED;
            case 0:
                return COLOR_BLUE;
            case 1:
                return COLOR_SMOOTH;
            case 2:
                return COLOR_SLOW;
            case 3:
                return COLOR_CONGESTED;
            case 4:
                return COLOR_SEVERE;
            case 5:
                return COLOR_CYAN;
            default:
                return COLOR_BACKGROUND;
        }
    }

    private float dp(float value) {
        return value * density;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
