package com.autonavi.companion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class LaneBarView extends View {
    private static final float FRAME_SCALE = 0.78f;
    private static final int STRAIGHT = 1;
    private static final int LEFT = 2;
    private static final int RIGHT = 3;
    private static final int U_LEFT = 4;
    private static final int U_RIGHT = 5;
    private static final int EXTEND = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect srcRect = new Rect();
    private final Path path = new Path();
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private final Map<String, Rect> iconBoundsCache = new HashMap<>();
    private final Rect commonLaneSourceBounds = new Rect();
    private int[] lanes = new int[]{15, 15, 15, 15};
    private boolean[] recommend = new boolean[]{true, true, true, true};
    private boolean cruiseLaneStyle = true;
    private float iconScaleMultiplier = 1f;
    private float frameScaleMultiplier = 1f;
    private boolean compactSpacing;
    private boolean showBackground = true;
    private boolean showDividers = true;
    private int customLaneSpacingDp = -1;
    private int customHeightDp = -1;
    private boolean hasCommonLaneSourceBounds;
    private boolean useCommonBitmapCrop;
    private int minCellCount = 3;

    public LaneBarView(Context context) {
        super(context);
        setMinimumHeight(dp(58));
        setVisibility(GONE);
    }

    public void setLaneData(int[] newLanes, boolean[] newRecommend) {
        if (newLanes == null || newLanes.length == 0) {
            setVisibility(GONE);
            return;
        }
        int count = Math.max(1, Math.min(newLanes.length, 8));
        lanes = Arrays.copyOf(newLanes, count);
        boolean hasRecommend = newRecommend != null && newRecommend.length > 0;
        boolean hasUnavailableCode = false;
        boolean hasComplexCode = false;
        for (int lane : lanes) {
            if (lane >= 0 && lane < 15) {
                hasUnavailableCode = true;
            }
            if (isComplexLane(lane)) {
                hasComplexCode = true;
            }
        }
        // If the protocol does not provide recommendation flags, treat it as cruise only
        // when all lane ids are simple available lanes. Navigation messages may omit
        // recommendation flags but still use 0-14 for unavailable lanes or 30-48 for
        // complex lanes, so those must keep the dark/active rendering.
        cruiseLaneStyle = !hasRecommend && !hasUnavailableCode && !hasComplexCode;
        if (newRecommend != null && newRecommend.length > 0) {
            recommend = new boolean[count];
            if (newRecommend.length == 1) {
                Arrays.fill(recommend, newRecommend[0]);
            } else {
                for (int i = 0; i < count; i++) {
                    recommend[i] = i < newRecommend.length ? newRecommend[i] : newRecommend[newRecommend.length - 1];
                }
            }
            boolean any = false;
            for (boolean value : recommend) {
                any |= value;
            }
            if (!any) {
                Arrays.fill(recommend, true);
            }
        } else {
            recommend = new boolean[count];
            Arrays.fill(recommend, true);
        }
        iconBoundsCache.clear();
        updateCommonLaneSourceBounds();
        setVisibility(VISIBLE);
        requestLayout();
        invalidate();
    }

    public void setScaleMultiplier(float multiplier) {
        iconScaleMultiplier = Math.max(0.5f, multiplier);
        invalidate();
    }

    public void setFrameScaleMultiplier(float multiplier) {
        frameScaleMultiplier = Math.max(0.5f, multiplier);
        requestLayout();
        invalidate();
    }

    public void setCompactSpacing(boolean compact) {
        compactSpacing = compact;
        iconBoundsCache.clear();
        updateCommonLaneSourceBounds();
        requestLayout();
        invalidate();
    }

    public void setUseCommonBitmapCrop(boolean use) {
        useCommonBitmapCrop = use;
        iconBoundsCache.clear();
        updateCommonLaneSourceBounds();
        invalidate();
    }

    public void setShowBackground(boolean show) {
        showBackground = show;
        invalidate();
    }

    public void setShowDividers(boolean show) {
        showDividers = show;
        invalidate();
    }

    public void setLaneSpacingDp(int dp) {
        customLaneSpacingDp = dp;
        requestLayout();
        invalidate();
    }

    public void setCustomHeightDp(int dp) {
        customHeightDp = dp;
        requestLayout();
        invalidate();
    }

    public void setMinCellCount(int min) {
        minCellCount = Math.max(1, min);
        requestLayout();
        invalidate();
    }

    public void setFallbackIcon(int icon) {
        setLaneData(new int[]{icon, 15, 15, 15}, new boolean[]{true, true, true, true});
    }

    public void hideLane() {
        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = Math.max(minCellCount, lanes == null ? 4 : lanes.length);
        int width;
        int height;
        if (useCommonBitmapCrop && customLaneSpacingDp >= 0) {
            int cellWidth = dp(27);
            int padding = dp(customLaneSpacingDp);
            width = cellWidth * count + padding;
            height = dp(customHeightDp >= 0 ? customHeightDp : (compactSpacing ? 50 : 58));
        } else {
            width = dp(compactSpacing ? 40 : 48) * count + dp(compactSpacing ? 8 : 12);
            height = dp(compactSpacing ? 50 : 58);
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (lanes == null || lanes.length == 0) {
            return;
        }

        if (!useCommonBitmapCrop) {
            rect.set(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF2878F0);
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);

            int count = lanes.length;
            float cell = getWidth() / (float) count;
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    paint.setColor(0x32FFFFFF);
                    paint.setStrokeWidth(dp(1));
                    float x = i * cell;
                    canvas.drawLine(x, dp(compactSpacing ? 6 : 8), x,
                            getHeight() - dp(compactSpacing ? 6 : 8), paint);
                }
                boolean laneRecommended = recommend == null || i >= recommend.length || recommend[i];
                LaneIcon icon = iconForLane(lanes[i]);
                if (laneRecommended && icon.hasEnabled()) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0x22FFFFFF);
                    rect.set(cell * i + dp(compactSpacing ? 2 : 4), dp(compactSpacing ? 4 : 5),
                            cell * (i + 1) - dp(compactSpacing ? 2 : 4),
                            getHeight() - dp(compactSpacing ? 4 : 5));
                    canvas.drawRoundRect(rect, dp(9), dp(9), paint);
                }
                drawLaneIcon(canvas, lanes[i], icon, cell * i, cell, laneRecommended);
            }
            return;
        }

        if (showBackground) {
            rect.set(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF2878F0);
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
        }

        int count = lanes.length;
        int visualCount = Math.max(minCellCount, count);
        float cell = getWidth() / (float) visualCount;
        float start = (visualCount - count) * cell / 2f;
        float dividerTop = showBackground ? dp(compactSpacing ? 6 : 8) : dp(2);
        float dividerBottom = getHeight() - (showBackground ? dp(compactSpacing ? 6 : 8) : dp(2));
        for (int i = 0; i < count; i++) {
            if (i > 0 && showDividers) {
                paint.setColor(0x32FFFFFF);
                paint.setStrokeWidth(dp(1));
                float x = start + i * cell;
                canvas.drawLine(x, dividerTop, x, dividerBottom, paint);
            }
            boolean laneRecommended = recommend == null || i >= recommend.length || recommend[i];
            LaneIcon icon = iconForLane(lanes[i]);
            if (showBackground && laneRecommended && icon.hasEnabled()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x22FFFFFF);
                float left = start + cell * i;
                rect.set(left + dp(compactSpacing ? 2 : 4), dp(compactSpacing ? 4 : 5),
                        left + cell - dp(compactSpacing ? 2 : 4), getHeight() - dp(compactSpacing ? 4 : 5));
                canvas.drawRoundRect(rect, dp(9), dp(9), paint);
            }
            float laneGap = compactSpacing ? dp(2) : 0f;
            drawLaneIcon(canvas, lanes[i], icon, start + cell * i + laneGap,
                    Math.max(1f, cell - laneGap * 2f), laneRecommended);
        }
    }

    private void drawLaneIcon(Canvas canvas, int laneCode, LaneIcon icon, float left, float width, boolean laneRecommended) {
        if (drawAmapLaneBitmap(canvas, laneCode, left, width, laneRecommended)) {
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(null);

        for (int pass = 0; pass < 2; pass++) {
            boolean drawActive = pass == 1;
            for (int i = 0; i < icon.directions.length; i++) {
                boolean active = i < icon.enabled.length && icon.enabled[i];
                if (active != drawActive) {
                    continue;
                }
                paint.setColor(active ? 0xFFFFFFFF : 0x88C7D8F4);
                paint.setStrokeWidth(active ? dp(5) : dp(4));
                drawDirection(canvas, icon.directions[i], left, width);
            }
        }
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
    }




    private boolean drawAmapLaneBitmap(Canvas canvas, int laneCode, float left, float width, boolean laneRecommended) {
        if (laneCode < 0 || laneCode > 48) {
            return false;
        }
        Bitmap bitmap = loadLaneBitmap("lane_pdf_" + laneCode);
        if (bitmap == null) {
            return false;
        }
        if (!useCommonBitmapCrop) {
            float iconHeight = Math.min(getHeight() - dp(4), dp(compactSpacing ? 42 : 48) * iconScaleMultiplier);
            float iconWidth = iconHeight * bitmap.getWidth() / (float) bitmap.getHeight();
            float maxWidth = width - dp(compactSpacing ? 1 : 2);
            if (iconWidth > maxWidth) {
                iconWidth = maxWidth;
                iconHeight = iconWidth * bitmap.getHeight() / (float) bitmap.getWidth();
            }
            rect.set(left + (width - iconWidth) / 2f, (getHeight() - iconHeight) / 2f,
                    left + (width + iconWidth) / 2f, (getHeight() + iconHeight) / 2f);

            paint.setFilterBitmap(true);
            paint.setAlpha(255);
            canvas.drawBitmap(bitmap, null, rect, paint);
            paint.setAlpha(255);
            return true;
        }

        Rect source = laneBitmapContentBounds("lane_pdf_" + laneCode, bitmap);
        float iconHeight = Math.min(getHeight() - dp(compactSpacing ? 1 : 4),
                dp(compactSpacing ? 50 : 48) * iconScaleMultiplier);
        float iconWidth = iconHeight * source.width() / (float) source.height();
        float maxWidth = showDividers ? (width - dp(compactSpacing ? 1 : 2)) : width;
        if (iconWidth > maxWidth) {
            iconWidth = maxWidth;
            iconHeight = iconWidth * source.height() / (float) source.width();
        }
        rect.set(left + (width - iconWidth) / 2f, (getHeight() - iconHeight) / 2f,
                left + (width + iconWidth) / 2f, (getHeight() + iconHeight) / 2f);

        paint.setFilterBitmap(true);
        paint.setAlpha(255);
        srcRect.set(source);
        canvas.drawBitmap(bitmap, srcRect, rect, paint);
        paint.setAlpha(255);
        return true;
    }

    private Rect laneBitmapContentBounds(String resourceName, Bitmap bitmap) {
        Rect cached = iconBoundsCache.get(resourceName);
        if (cached != null) {
            return cached;
        }
        Rect bounds;
        if (!useCommonBitmapCrop) {
            bounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        } else if (hasCommonLaneSourceBounds
                && commonLaneSourceBounds.right <= bitmap.getWidth()
                && commonLaneSourceBounds.bottom <= bitmap.getHeight()) {
            bounds = new Rect(commonLaneSourceBounds);
        } else {
            int[] insets = laneBitmapTransparentInsets(bitmap);
            if (insets == null) {
                bounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            } else {
                int insetY = Math.max(0, dp(compactSpacing ? 1 : 2));
                bounds = new Rect(insets[0], Math.max(0, insets[2] - insetY),
                        bitmap.getWidth() - insets[1],
                        Math.min(bitmap.getHeight(), bitmap.getHeight() - insets[3] + insetY));
            }
        }
        iconBoundsCache.put(resourceName, bounds);
        return bounds;
    }

    private void updateCommonLaneSourceBounds() {
        if (!useCommonBitmapCrop) {
            hasCommonLaneSourceBounds = false;
            commonLaneSourceBounds.setEmpty();
            return;
        }
        if (lanes == null || lanes.length == 0) {
            hasCommonLaneSourceBounds = false;
            return;
        }
        int minLeft = Integer.MAX_VALUE;
        int minRight = Integer.MAX_VALUE;
        int minTop = Integer.MAX_VALUE;
        int minBottom = Integer.MAX_VALUE;
        int width = -1;
        int height = -1;
        for (int lane : lanes) {
            if (lane < 0 || lane > 48) {
                continue;
            }
            Bitmap bitmap = loadLaneBitmap("lane_pdf_" + lane);
            if (bitmap == null) {
                continue;
            }
            int[] insets = laneBitmapTransparentInsets(bitmap);
            if (insets == null) {
                continue;
            }
            width = bitmap.getWidth();
            height = bitmap.getHeight();
            minLeft = Math.min(minLeft, insets[0]);
            minRight = Math.min(minRight, insets[1]);
            minTop = Math.min(minTop, insets[2]);
            minBottom = Math.min(minBottom, insets[3]);
        }
        if (width <= 0 || height <= 0 || minLeft == Integer.MAX_VALUE || minRight == Integer.MAX_VALUE) {
            hasCommonLaneSourceBounds = false;
            return;
        }
        int insetY = Math.max(0, dp(compactSpacing ? 1 : 2));
        commonLaneSourceBounds.set(
                Math.max(0, minLeft),
                Math.max(0, minTop - insetY),
                Math.min(width, width - minRight),
                Math.min(height, height - minBottom + insetY)
        );
        hasCommonLaneSourceBounds = !commonLaneSourceBounds.isEmpty();
    }

    private int[] laneBitmapTransparentInsets(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int left = width;
        int right = -1;
        int top = height;
        int bottom = -1;
        int[] pixels = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                if (((pixels[x] >>> 24) & 0xFF) > 8) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (right < left) {
            return null;
        }
        return new int[]{left, width - right - 1, top, height - bottom - 1};
    }

    private Bitmap loadLaneBitmap(String resourceName) {
        Bitmap cached = iconCache.get(resourceName);
        if (cached != null) {
            return cached;
        }
        int id = getResources().getIdentifier(resourceName, "drawable", getContext().getPackageName());
        if (id == 0) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), id);
        if (bitmap != null) {
            iconCache.put(resourceName, bitmap);
        }
        return bitmap;
    }

    private boolean isComplexLane(int lane) {
        return lane >= 30 && lane <= 48;
    }

    private void drawDirection(Canvas canvas, int direction, float left, float width) {
        drawDirection(canvas, direction, left, width, paint);
    }

    private void drawDirection(Canvas canvas, int direction, float left, float width, Paint p) {
        float cx = left + width / 2f;
        float bottom = getHeight() - dp(10);
        float splitY = getHeight() - dp(27);
        float top = dp(9);
        float leftX = left + Math.max(dp(9), width * 0.20f);
        float rightX = left + Math.min(width - dp(9), width * 0.80f);

        path.reset();
        path.moveTo(cx, bottom);
        if (direction == STRAIGHT) {
            path.lineTo(cx, top + dp(8));
            canvas.drawPath(path, p);
            drawArrowHead(canvas, cx, top + dp(8), 0f, -1f, p);
        } else if (direction == LEFT) {
            path.lineTo(cx, splitY);
            path.cubicTo(cx, splitY - dp(11), leftX + dp(9), top + dp(13), leftX, top + dp(11));
            canvas.drawPath(path, p);
            drawArrowHead(canvas, leftX, top + dp(11), -1f, -0.10f, p);
        } else if (direction == RIGHT) {
            path.lineTo(cx, splitY);
            path.cubicTo(cx, splitY - dp(11), rightX - dp(9), top + dp(13), rightX, top + dp(11));
            canvas.drawPath(path, p);
            drawArrowHead(canvas, rightX, top + dp(11), 1f, -0.10f, p);
        } else if (direction == U_LEFT) {
            path.lineTo(cx, splitY);
            path.cubicTo(cx, top + dp(6), leftX, top + dp(6), leftX, splitY + dp(9));
            canvas.drawPath(path, p);
            drawArrowHead(canvas, leftX, splitY + dp(9), 0f, 1f, p);
        } else if (direction == U_RIGHT) {
            path.lineTo(cx, splitY);
            path.cubicTo(cx, top + dp(6), rightX, top + dp(6), rightX, splitY + dp(9));
            canvas.drawPath(path, p);
            drawArrowHead(canvas, rightX, splitY + dp(9), 0f, 1f, p);
        } else {
            p.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
            path.lineTo(cx, top + dp(8));
            canvas.drawPath(path, p);
            p.setPathEffect(null);
        }
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float dx, float dy) {
        drawArrowHead(canvas, x, y, dx, dy, paint);
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float dx, float dy, Paint p) {
        float size = dp(7);
        Path arrow = path;
        arrow.reset();
        if (Math.abs(dx) > Math.abs(dy)) {
            arrow.moveTo(x, y);
            arrow.lineTo(x - dx * size, y - size * 0.72f);
            arrow.moveTo(x, y);
            arrow.lineTo(x - dx * size, y + size * 0.72f);
        } else {
            arrow.moveTo(x, y);
            arrow.lineTo(x - size * 0.72f, y - dy * size);
            arrow.moveTo(x, y);
            arrow.lineTo(x + size * 0.72f, y - dy * size);
        }
        canvas.drawPath(arrow, p);
    }

    private LaneIcon iconForLane(int lane) {
        switch (lane) {
            case 0:
                return icon(false, STRAIGHT);
            case 1:
                return icon(false, LEFT);
            case 2:
                return icon(false, STRAIGHT, LEFT);
            case 3:
                return icon(false, RIGHT);
            case 4:
                return icon(false, STRAIGHT, RIGHT);
            case 5:
                return icon(false, U_LEFT);
            case 6:
                return icon(false, LEFT, RIGHT);
            case 7:
                return icon(false, LEFT, STRAIGHT, RIGHT);
            case 8:
                return icon(false, U_RIGHT);
            case 9:
                return icon(false, U_LEFT, STRAIGHT);
            case 10:
                return icon(false, STRAIGHT, U_RIGHT);
            case 11:
                return icon(false, U_LEFT, LEFT);
            case 12:
                return icon(false, RIGHT, U_RIGHT);
            case 13:
            case 14:
                return icon(false, EXTEND, STRAIGHT);
            case 15:
                return icon(true, STRAIGHT);
            case 16:
                return icon(true, LEFT);
            case 17:
                return icon(true, LEFT, STRAIGHT);
            case 18:
                return icon(true, RIGHT);
            case 19:
                return icon(true, STRAIGHT, RIGHT);
            case 20:
                return icon(true, U_LEFT);
            case 21:
                return icon(true, LEFT, RIGHT);
            case 22:
                return icon(true, LEFT, STRAIGHT, RIGHT);
            case 23:
                return icon(true, U_RIGHT);
            case 24:
                return icon(true, U_LEFT, STRAIGHT);
            case 25:
                return icon(true, STRAIGHT, U_RIGHT);
            case 26:
                return icon(true, U_LEFT, LEFT);
            case 27:
                return icon(true, RIGHT, U_RIGHT);
            case 28:
            case 29:
                return icon(true, EXTEND, STRAIGHT);
            case 30:
                return complex(new int[]{STRAIGHT, LEFT}, new boolean[]{true, false});
            case 31:
                return complex(new int[]{STRAIGHT, LEFT}, new boolean[]{false, true});
            case 32:
                return complex(new int[]{STRAIGHT, RIGHT}, new boolean[]{true, false});
            case 33:
                return complex(new int[]{STRAIGHT, RIGHT}, new boolean[]{false, true});
            case 34:
                return complex(new int[]{LEFT, RIGHT}, new boolean[]{true, false});
            case 35:
                return complex(new int[]{LEFT, RIGHT}, new boolean[]{false, true});
            case 36:
                return complex(new int[]{LEFT, STRAIGHT, RIGHT}, new boolean[]{false, true, false});
            case 37:
                return complex(new int[]{LEFT, STRAIGHT, RIGHT}, new boolean[]{true, false, false});
            case 38:
                return complex(new int[]{LEFT, STRAIGHT, RIGHT}, new boolean[]{false, false, true});
            case 39:
                return complex(new int[]{U_LEFT, STRAIGHT}, new boolean[]{false, true});
            case 40:
                return complex(new int[]{U_LEFT, STRAIGHT}, new boolean[]{true, false});
            case 41:
                return complex(new int[]{STRAIGHT, U_RIGHT}, new boolean[]{true, false});
            case 42:
                return complex(new int[]{STRAIGHT, U_RIGHT}, new boolean[]{false, true});
            case 43:
                return complex(new int[]{LEFT, U_LEFT}, new boolean[]{true, false});
            case 44:
            case 48:
                return complex(new int[]{LEFT, U_LEFT}, new boolean[]{false, true});
            case 45:
                return complex(new int[]{RIGHT, U_RIGHT}, new boolean[]{true, false});
            case 46:
                return complex(new int[]{RIGHT, U_RIGHT}, new boolean[]{false, true});
            case 47:
                return complex(new int[]{EXTEND, LEFT, U_RIGHT}, new boolean[]{false, false, true});
            default:
                return icon(true, STRAIGHT);
        }
    }

    private LaneIcon icon(boolean enabled, int... directions) {
        boolean[] states = new boolean[directions.length];
        Arrays.fill(states, enabled);
        return new LaneIcon(directions, states);
    }

    private LaneIcon complex(int[] directions, boolean[] enabled) {
        return new LaneIcon(directions, enabled);
    }

    private int dp(int value) {
        return (int) (value * FRAME_SCALE * frameScaleMultiplier * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class LaneIcon {
        final int[] directions;
        final boolean[] enabled;

        LaneIcon(int[] directions, boolean[] enabled) {
            this.directions = directions;
            this.enabled = enabled;
        }

        boolean hasEnabled() {
            for (boolean value : enabled) {
                if (value) {
                    return true;
                }
            }
            return false;
        }
    }
}
