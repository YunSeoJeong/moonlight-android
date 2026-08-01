package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Local cursor for touch-compatible split-keyboard mouse input. */
final class SplitKeyboardCursorView extends View
        implements SubDisplayKeyboardControlsSession.Listener {
    private final SubDisplayKeyboardControlsSession session =
            SubDisplayKeyboardControlsSession.getInstance();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cursorPath = new Path();
    private final float density;

    SplitKeyboardCursorView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        session.addListener(this);
        updateCursorBounds();
    }

    @Override
    protected void onDetachedFromWindow() {
        session.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateCursorBounds();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!session.isTouchCompatibilityEnabled() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float x = session.getCursorX();
        float y = session.getCursorY();
        float size = Math.max(22f * density,
                Math.min(getWidth(), getHeight()) * 0.045f);
        buildCursorPath(x, y, size);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawPath(cursorPath, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1.5f * density, size * 0.055f));
        paint.setColor(Color.BLACK);
        canvas.drawPath(cursorPath, paint);
    }

    @Override
    public void onSubDisplayKeyboardControlsChanged() {
        invalidate();
    }

    private void updateCursorBounds() {
        if (getWidth() > 0 && getHeight() > 0) {
            session.setCursorBounds(getWidth(), getHeight());
        }
    }

    private void buildCursorPath(float x, float y, float size) {
        cursorPath.reset();
        cursorPath.moveTo(x, y);
        cursorPath.lineTo(x + size * 0.08f, y + size * 0.77f);
        cursorPath.lineTo(x + size * 0.27f, y + size * 0.58f);
        cursorPath.lineTo(x + size * 0.47f, y + size * 0.96f);
        cursorPath.lineTo(x + size * 0.64f, y + size * 0.87f);
        cursorPath.lineTo(x + size * 0.44f, y + size * 0.50f);
        cursorPath.lineTo(x + size * 0.72f, y + size * 0.47f);
        cursorPath.close();
    }
}
