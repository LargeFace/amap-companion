package com.autonavi.companion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class ClusterJoystickView extends View {
    public interface OnMoveListener {
        void onMove(int dx, int dy);
    }

    private static final long MOVE_INTERVAL_MS = 33L;
    private static final float DEAD_ZONE = 0.12f;

    private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final Runnable movementRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging) {
                movementScheduled = false;
                return;
            }
            int dx = Math.round(moveX * maxStepPx);
            int dy = Math.round(moveY * maxStepPx);
            if ((dx != 0 || dy != 0) && moveListener != null) {
                moveListener.onMove(dx, dy);
            }
            postDelayed(this, MOVE_INTERVAL_MS);
        }
    };

    private OnMoveListener moveListener;
    private float centerX;
    private float centerY;
    private float outerRadius;
    private float knobRadius;
    private float knobLimitRadius;
    private float knobOffsetX;
    private float knobOffsetY;
    private float moveX;
    private float moveY;
    private int maxStepPx;
    private boolean dragging;
    private boolean movementScheduled;
    private ValueAnimator returnAnimator;

    public ClusterJoystickView(Context context) {
        this(context, null);
    }

    public ClusterJoystickView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClusterJoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        maxStepPx = dp(9);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setClickable(true);
        setFocusable(true);
        setContentDescription("\u526f\u5c4f\u60ac\u6d6e\u7a97\u4f4d\u7f6e\u6447\u6746");

        outerStrokePaint.setStyle(Paint.Style.STROKE);
        outerStrokePaint.setStrokeWidth(dp(1.4f));
        outerStrokePaint.setColor(0x6638BDF8);

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dp(1f));
        guidePaint.setColor(0x335EEAD4);

        knobShadowPaint.setColor(0x66000000);
        knobShadowPaint.setShadowLayer(dp(7f), 0f, dp(2.5f), 0x66000000);

        knobHighlightPaint.setColor(0x88E0F2FE);
    }

    public void setOnMoveListener(OnMoveListener listener) {
        moveListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = dp(148);
        int width = resolveSize(desired, widthMeasureSpec);
        int height = resolveSize(desired, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h);
        centerX = w * 0.5f;
        centerY = h * 0.5f;
        outerRadius = size * 0.43f;
        knobRadius = size * 0.17f;
        knobLimitRadius = Math.max(0f, outerRadius - knobRadius - dp(3f));

        outerPaint.setShader(new RadialGradient(
                centerX - outerRadius * 0.28f,
                centerY - outerRadius * 0.35f,
                outerRadius * 1.35f,
                new int[]{0xFF475569, 0xFF1E293B, 0xFF0F172A},
                new float[]{0f, 0.54f, 1f},
                Shader.TileMode.CLAMP));
        knobPaint.setShader(new RadialGradient(
                centerX,
                centerY,
                knobRadius * 1.45f,
                new int[]{0xFFE0F2FE, 0xFF38BDF8, 0xFF2563EB},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(centerX, centerY, outerRadius, outerPaint);
        canvas.drawCircle(centerX, centerY, outerRadius - dp(0.7f), outerStrokePaint);
        canvas.drawCircle(centerX, centerY, outerRadius * 0.64f, guidePaint);
        canvas.drawLine(centerX - outerRadius * 0.45f, centerY,
                centerX + outerRadius * 0.45f, centerY, guidePaint);
        canvas.drawLine(centerX, centerY - outerRadius * 0.45f,
                centerX, centerY + outerRadius * 0.45f, guidePaint);

        canvas.save();
        canvas.translate(knobOffsetX, knobOffsetY);
        canvas.drawCircle(centerX, centerY, knobRadius, knobShadowPaint);
        canvas.drawCircle(centerX, centerY, knobRadius, knobPaint);
        canvas.drawCircle(centerX,
                centerY,
                knobRadius * 0.22f,
                knobHighlightPaint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                cancelReturnAnimation();
                dragging = true;
                updateKnob(event.getX(), event.getY());
                startMovementLoop();
                return true;
            case MotionEvent.ACTION_MOVE:
                updateKnob(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = false;
                moveX = 0f;
                moveY = 0f;
                animateKnobToCenter();
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        dragging = false;
        removeCallbacks(movementRunnable);
        cancelReturnAnimation();
        super.onDetachedFromWindow();
    }

    private void updateKnob(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance > knobLimitRadius && distance > 0f) {
            float scale = knobLimitRadius / distance;
            dx *= scale;
            dy *= scale;
            distance = knobLimitRadius;
        }

        knobOffsetX = dx;
        knobOffsetY = dy;
        if (knobLimitRadius > 0f && distance / knobLimitRadius >= DEAD_ZONE) {
            moveX = dx / knobLimitRadius;
            moveY = dy / knobLimitRadius;
        } else {
            moveX = 0f;
            moveY = 0f;
        }
        invalidate();
    }

    private void startMovementLoop() {
        if (movementScheduled) {
            return;
        }
        movementScheduled = true;
        post(movementRunnable);
    }

    private void animateKnobToCenter() {
        cancelReturnAnimation();
        final float startX = knobOffsetX;
        final float startY = knobOffsetY;
        returnAnimator = ValueAnimator.ofFloat(0f, 1f);
        returnAnimator.setDuration(180L);
        returnAnimator.setInterpolator(new DecelerateInterpolator());
        returnAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            knobOffsetX = startX * (1f - progress);
            knobOffsetY = startY * (1f - progress);
            invalidate();
        });
        returnAnimator.start();
    }

    private void cancelReturnAnimation() {
        if (returnAnimator != null) {
            returnAnimator.cancel();
            returnAnimator = null;
        }
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }
}
