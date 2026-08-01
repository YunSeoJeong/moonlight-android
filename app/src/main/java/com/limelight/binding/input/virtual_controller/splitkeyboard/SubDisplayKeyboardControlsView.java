package com.limelight.binding.input.virtual_controller.splitkeyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.Control;

import java.util.EnumMap;
import java.util.Map;

/** Four large shoulder-button controls for the secondary display. */
public final class SubDisplayKeyboardControlsView extends View
        implements SubDisplayKeyboardControlsSession.Listener {
    private static final int BACKGROUND_COLOR = 0xFFE3E4E8;
    private static final int BUTTON_COLOR = 0xFFFAFAFC;
    private static final int BUTTON_PRESSED_COLOR = 0xFFC2CBD8;
    private static final int BUTTON_SHADOW_COLOR = 0x26000000;
    private static final int BUTTON_BORDER_COLOR = 0x18000000;
    private static final int BUTTON_ACCENT_COLOR = 0xFF3D78CC;
    private static final int TEXT_COLOR = 0xFF24262B;
    private static final int SECONDARY_TEXT_COLOR = 0xFF676B73;
    private static final float REFERENCE_WIDTH = 2376f;
    private static final float REFERENCE_HEIGHT = 968f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Control, RectF> bounds = new EnumMap<>(Control.class);
    private final SubDisplayKeyboardControlsSession session =
            SubDisplayKeyboardControlsSession.getInstance();
    private final float density;
    private final boolean controlsUiVisible;
    private final boolean hapticEnabled;
    private final int hapticStrength;
    private final Runnable userActivityCallback;

    public SubDisplayKeyboardControlsView(Context context) {
        this(context, null, null);
    }

    public SubDisplayKeyboardControlsView(Context context, AttributeSet attrs) {
        this(context, attrs, null);
    }

    public SubDisplayKeyboardControlsView(Context context,
                                          Runnable userActivityCallback) {
        this(context, null, userActivityCallback);
    }

    private SubDisplayKeyboardControlsView(Context context, AttributeSet attrs,
                                           Runnable userActivityCallback) {
        super(context, attrs);
        this.userActivityCallback = userActivityCallback;
        density = getResources().getDisplayMetrics().density;
        SplitKeyboardPreferences preferences = new SplitKeyboardPreferences(context);
        controlsUiVisible = !preferences.hideSubDisplayControlsUi;
        hapticEnabled = preferences.hapticEnabled;
        hapticStrength = preferences.hapticStrength;
        setBackgroundColor(controlsUiVisible ? BACKGROUND_COLOR : Color.BLACK);
        setFocusable(false);
        setContentDescription("LB, LT, RB, RT");
        for (Control control : Control.values()) {
            bounds.put(control, new RectF());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        session.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        session.releaseOwner(this);
        session.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float scaleX = width / REFERENCE_WIDTH;
        float scaleY = height / REFERENCE_HEIGHT;

        setScaledBounds(Control.RB, 210f, 10f, 650f, 350f, scaleX, scaleY);
        setScaledBounds(Control.RT, 400f, 460f, 650f, 450f, scaleX, scaleY);
        setScaledBounds(Control.LB, 1530f, 10f, 650f, 350f, scaleX, scaleY);
        setScaledBounds(Control.LT, 1350f, 460f, 650f, 450f, scaleX, scaleY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!controlsUiVisible) {
            return;
        }
        for (Control control : Control.values()) {
            drawButton(canvas, control, bounds.get(control));
        }
    }

    private void drawButton(Canvas canvas, Control control, RectF rect) {
        float radius = Math.max(10f * density, Math.min(rect.width(), rect.height()) * 0.09f);
        boolean pressed = session.isPressed(control);
        float inset = Math.max(1f, density * (pressed ? 1.3f : 0.65f));
        float pressedOffset = pressed ? density : 0f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BUTTON_SHADOW_COLOR);
        RectF keyRect = new RectF(rect.left + inset,
                rect.top + inset + 1.5f * density,
                rect.right - inset,
                rect.bottom - inset);
        canvas.drawRoundRect(keyRect, radius, radius, paint);

        keyRect.set(rect.left + inset,
                rect.top + inset + pressedOffset,
                rect.right - inset,
                rect.bottom - inset - 1.4f * density + pressedOffset);
        paint.setColor(pressed ? BUTTON_PRESSED_COLOR : BUTTON_COLOR);
        canvas.drawRoundRect(keyRect, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(pressed ? Math.max(1.5f, density)
                : Math.max(0.6f, density * 0.45f));
        paint.setColor(pressed ? BUTTON_ACCENT_COLOR : BUTTON_BORDER_COLOR);
        canvas.drawRoundRect(keyRect, radius, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TEXT_COLOR);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(clamp(Math.min(rect.width(), rect.height()) * 0.25f,
                24f * density, 48f * density));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float labelBaseline = rect.centerY()
                - (metrics.ascent + metrics.descent) / 2f
                - Math.min(rect.height() * 0.10f, 18f * density)
                + pressedOffset;
        canvas.drawText(control.name(), rect.centerX(), labelBaseline, paint);

        paint.setColor(SECONDARY_TEXT_COLOR);
        paint.setTextSize(clamp(Math.min(rect.width(), rect.height()) * 0.085f,
                10f * density, 16f * density));
        metrics = paint.getFontMetrics();
        float actionBaseline = rect.centerY()
                - (metrics.ascent + metrics.descent) / 2f
                + Math.min(rect.height() * 0.19f, 34f * density)
                + pressedOffset;
        canvas.drawText(getActionLabel(control), rect.centerX(), actionBaseline, paint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (userActivityCallback != null) {
            userActivityCallback.run();
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                Control control = findControl(event.getX(index), event.getY(index));
                if (control != null && session.pointerDown(
                        this, event.getPointerId(index), control)) {
                    performControlHaptic();
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                session.pointerUp(this, event.getPointerId(event.getActionIndex()));
                return true;
            case MotionEvent.ACTION_CANCEL:
                session.releaseOwner(this);
                return true;
            default:
                return true;
        }
    }

    @Override
    public void onSubDisplayKeyboardControlsChanged() {
        invalidate();
    }

    private Control findControl(float x, float y) {
        for (Control control : Control.values()) {
            if (bounds.get(control).contains(x, y)) {
                return control;
            }
        }
        return null;
    }

    private void setBounds(Control control, float left, float top, float right, float bottom) {
        bounds.get(control).set(left, top, right, bottom);
    }

    private void setScaledBounds(Control control, float x, float y,
                                 float width, float height,
                                 float scaleX, float scaleY) {
        setBounds(control,
                x * scaleX,
                y * scaleY,
                (x + width) * scaleX,
                (y + height) * scaleY);
    }

    RectF getControlBoundsForTesting(Control control) {
        return new RectF(bounds.get(control));
    }

    private String getActionLabel(Control control) {
        switch (control) {
            case LB:
                return getResources().getString(
                        R.string.split_keyboard_sub_control_left_click);
            case LT:
                return getResources().getString(
                        R.string.split_keyboard_sub_control_right_click);
            case RB:
                return getResources().getString(
                        R.string.split_keyboard_sub_control_scroll);
            case RT:
            default:
                return getResources().getString(
                        R.string.split_keyboard_sub_control_trackpad);
        }
    }

    private void performControlHaptic() {
        if (!hapticEnabled) {
            return;
        }
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(7, hapticStrength));
            }
            else {
                vibrator.vibrate(7);
            }
        }
        else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
