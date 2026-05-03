package com.autonavi.amapclone.companion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.Arrays;

public class LaneBarView extends View {
    private static final String LEFT = "\u2190";
    private static final String STRAIGHT = "\u2191";
    private static final String RIGHT = "\u2192";
    private static final String U_LEFT = "\u21b6";
    private static final String U_RIGHT = "\u21b7";
    private static final String EXTEND = "\u2506";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private int[] lanes = new int[]{15, 15, 15, 15};
    private boolean[] recommend = new boolean[]{true, true, true, true};

    public LaneBarView(Context context) {
        super(context);
        setMinimumHeight(dp(54));
        setVisibility(GONE);
    }

    public void setLaneData(int[] newLanes, boolean[] newRecommend) {
        if (newLanes == null || newLanes.length == 0) {
            setVisibility(GONE);
            return;
        }
        int count = Math.max(1, Math.min(newLanes.length, 8));
        lanes = Arrays.copyOf(newLanes, count);
        if (newRecommend != null && newRecommend.length > 0) {
            recommend = Arrays.copyOf(newRecommend, count);
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
        setVisibility(VISIBLE);
        requestLayout();
        invalidate();
    }

    public void setFallbackIcon(int icon) {
        int[] fallback = new int[]{icon, 15, 15, 15};
        boolean[] selected = new boolean[]{true, true, true, true};
        setLaneData(fallback, selected);
    }

    public void hideLane() {
        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = Math.max(3, lanes == null ? 4 : lanes.length);
        int width = dp(44) * count + dp(14);
        int height = dp(54);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (lanes == null || lanes.length == 0) {
            return;
        }

        rect.set(0, 0, getWidth(), getHeight());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF0182A3D);
        canvas.drawRoundRect(rect, dp(10), dp(10), paint);

        int count = lanes.length;
        float cell = getWidth() / (float) count;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                paint.setColor(0x24FFFFFF);
                paint.setStrokeWidth(dp(1));
                float x = i * cell;
                canvas.drawLine(x, dp(8), x, getHeight() - dp(8), paint);
            }
            boolean laneRecommended = recommend == null || i >= recommend.length || recommend[i];
            LaneIcon icon = iconForLane(lanes[i]);
            if (laneRecommended && icon.hasEnabled()) {
                paint.setColor(0x1FFFFFFF);
                rect.set(cell * i + dp(3), dp(5), cell * (i + 1) - dp(3), getHeight() - dp(5));
                canvas.drawRoundRect(rect, dp(8), dp(8), paint);
            }
            drawLaneIcon(canvas, icon, cell * i, cell, laneRecommended);
        }
    }

    private void drawLaneIcon(Canvas canvas, LaneIcon icon, float left, float width, boolean laneRecommended) {
        String[] labels = icon.labels;
        boolean[] enabled = icon.enabled;
        if (labels.length == 0) {
            return;
        }
        if (icon.complex) {
            drawComplexLaneIcon(canvas, icon, left, width, laneRecommended);
            return;
        }

        float textSize = labels.length >= 3 ? dp(18) : dp(25);
        paint.setTextSize(textSize);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        float gap = labels.length >= 3
                ? Math.min(dp(16), width / (labels.length + 0.6f))
                : Math.min(dp(15), width / (labels.length + 0.8f));
        float center = left + width / 2f;
        float start = center - gap * (labels.length - 1) / 2f;
        for (int i = 0; i < labels.length; i++) {
            boolean active = i < enabled.length && enabled[i] && laneRecommended;
            paint.setColor(active ? 0xFFFFFFFF : 0xFF5F7890);
            canvas.drawText(labels[i], start + gap * i, baseline, paint);
        }
        paint.setFakeBoldText(false);
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
                return icon(false, STRAIGHT, LEFT, RIGHT);
            case 8:
                return icon(false, U_RIGHT);
            case 9:
                return icon(false, STRAIGHT, U_LEFT);
            case 10:
                return icon(false, STRAIGHT, U_RIGHT);
            case 11:
                return icon(false, LEFT, U_LEFT);
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
                return icon(true, RIGHT, STRAIGHT);
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
                return icon(true, U_RIGHT, STRAIGHT);
            case 26:
                return icon(true, U_LEFT, LEFT);
            case 27:
                return icon(true, U_RIGHT, RIGHT);
            case 28:
            case 29:
                return icon(true, EXTEND, STRAIGHT);
            case 30:
                return complex(lane, new String[]{STRAIGHT, LEFT}, new boolean[]{true, false});
            case 31:
                return complex(lane, new String[]{STRAIGHT, LEFT}, new boolean[]{false, true});
            case 32:
                return complex(lane, new String[]{STRAIGHT, RIGHT}, new boolean[]{true, false});
            case 33:
                return complex(lane, new String[]{STRAIGHT, RIGHT}, new boolean[]{false, true});
            case 34:
                return complex(lane, new String[]{LEFT, RIGHT}, new boolean[]{true, false});
            case 35:
                return complex(lane, new String[]{LEFT, RIGHT}, new boolean[]{false, true});
            case 36:
                return complex(lane, new String[]{LEFT, STRAIGHT, RIGHT}, new boolean[]{false, true, false});
            case 37:
                return complex(lane, new String[]{LEFT, STRAIGHT, RIGHT}, new boolean[]{true, false, false});
            case 38:
                return complex(lane, new String[]{LEFT, STRAIGHT, RIGHT}, new boolean[]{false, false, true});
            case 39:
                return complex(lane, new String[]{U_LEFT, STRAIGHT}, new boolean[]{false, true});
            case 40:
                return complex(lane, new String[]{U_LEFT, STRAIGHT}, new boolean[]{true, false});
            case 41:
                return complex(lane, new String[]{STRAIGHT, U_RIGHT}, new boolean[]{true, false});
            case 42:
                return complex(lane, new String[]{STRAIGHT, U_RIGHT}, new boolean[]{false, true});
            case 43:
                return complex(lane, new String[]{LEFT, U_LEFT}, new boolean[]{true, false});
            case 44:
            case 48:
                return complex(lane, new String[]{LEFT, U_LEFT}, new boolean[]{false, true});
            case 45:
                return complex(lane, new String[]{RIGHT, U_RIGHT}, new boolean[]{true, false});
            case 46:
                return complex(lane, new String[]{RIGHT, U_RIGHT}, new boolean[]{false, true});
            case 47:
                return complex(lane, new String[]{EXTEND, LEFT, U_RIGHT}, new boolean[]{false, false, true});
            default:
                return icon(true, STRAIGHT);
        }
    }

    private LaneIcon icon(boolean enabled, String... labels) {
        boolean[] states = new boolean[labels.length];
        Arrays.fill(states, enabled);
        return new LaneIcon(labels, states, false);
    }

    private LaneIcon complex(int lane, String[] labels, boolean[] enabled) {
        return new LaneIcon(labels, enabled, true);
    }

    private void drawComplexLaneIcon(Canvas canvas, LaneIcon icon, float left, float width, boolean laneRecommended) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(dp(3));
        for (int i = 0; i < icon.labels.length; i++) {
            if (i < icon.enabled.length && icon.enabled[i] && laneRecommended) {
                continue;
            }
            paint.setColor(0xFF5F7890);
            drawComplexBranch(canvas, icon.labels[i], left, width);
        }
        paint.setStrokeWidth(dp(4));
        for (int i = 0; i < icon.labels.length; i++) {
            if (i >= icon.enabled.length || !icon.enabled[i] || !laneRecommended) {
                continue;
            }
            paint.setColor(0xFFFFFFFF);
            drawComplexBranch(canvas, icon.labels[i], left, width);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawComplexBranch(Canvas canvas, String label, float left, float width) {
        float cx = left + width / 2f;
        float bottom = getHeight() - dp(10);
        float midY = getHeight() - dp(25);
        float top = dp(10);
        float leftX = left + Math.max(dp(8), width * 0.22f);
        float rightX = left + Math.min(width - dp(8), width * 0.78f);

        path.reset();
        path.moveTo(cx, bottom);
        if (STRAIGHT.equals(label)) {
            path.lineTo(cx, top + dp(5));
        } else if (LEFT.equals(label)) {
            path.lineTo(cx, midY);
            path.cubicTo(cx, midY - dp(10), leftX + dp(8), top + dp(12), leftX, top + dp(8));
        } else if (RIGHT.equals(label)) {
            path.lineTo(cx, midY);
            path.cubicTo(cx, midY - dp(10), rightX - dp(8), top + dp(12), rightX, top + dp(8));
        } else if (U_LEFT.equals(label)) {
            path.lineTo(cx, midY);
            path.cubicTo(cx, top + dp(7), leftX, top + dp(7), leftX, midY + dp(6));
        } else if (U_RIGHT.equals(label)) {
            path.lineTo(cx, midY);
            path.cubicTo(cx, top + dp(7), rightX, top + dp(7), rightX, midY + dp(6));
        } else {
            path.lineTo(cx, top + dp(5));
        }
        canvas.drawPath(path, paint);

        if (STRAIGHT.equals(label) || EXTEND.equals(label)) {
            drawArrowHead(canvas, cx, top + dp(5), 0f, -1f);
        } else if (LEFT.equals(label)) {
            drawArrowHead(canvas, leftX, top + dp(8), -1f, -0.15f);
        } else if (RIGHT.equals(label)) {
            drawArrowHead(canvas, rightX, top + dp(8), 1f, -0.15f);
        } else if (U_LEFT.equals(label)) {
            drawArrowHead(canvas, leftX, midY + dp(6), 0f, 1f);
        } else if (U_RIGHT.equals(label)) {
            drawArrowHead(canvas, rightX, midY + dp(6), 0f, 1f);
        }
    }

    private void drawArrowHead(Canvas canvas, float x, float y, float dx, float dy) {
        float size = dp(5);
        path.reset();
        if (Math.abs(dx) > Math.abs(dy)) {
            path.moveTo(x, y);
            path.lineTo(x - dx * size, y - size);
            path.moveTo(x, y);
            path.lineTo(x - dx * size, y + size);
        } else {
            path.moveTo(x, y);
            path.lineTo(x - size, y - dy * size);
            path.moveTo(x, y);
            path.lineTo(x + size, y - dy * size);
        }
        canvas.drawPath(path, paint);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class LaneIcon {
        final String[] labels;
        final boolean[] enabled;
        final boolean complex;

        LaneIcon(String[] labels, boolean[] enabled, boolean complex) {
            this.labels = labels;
            this.enabled = enabled;
            this.complex = complex;
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
