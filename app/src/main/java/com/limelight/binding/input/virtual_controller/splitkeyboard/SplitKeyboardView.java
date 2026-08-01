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
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.splitkeyboard.SubDisplayKeyboardControlsSession.TrackpadMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SplitKeyboardView extends ViewGroup
        implements KeyboardStateController.Listener,
        SubDisplayKeyboardControlsSession.Listener {
    private static final int BACKGROUND_COLOR = 0xFFE3E4E8;
    private static final int TRACKPAD_KEY_COLOR = 0xFFFAFAFC;
    private static final int TRACKPAD_SHADOW_COLOR = 0x26000000;
    private static final int TRACKPAD_BORDER_COLOR = 0x18000000;
    private static final int TRACKPAD_BADGE_COLOR = 0xFFD0D1D5;
    private static final int TRACKPAD_ACCENT_COLOR = 0xFF3D78CC;
    private static final int TRACKPAD_TEXT_COLOR = 0xFF24262B;
    private static final int TRACKPAD_SECONDARY_TEXT_COLOR = 0xFF676B73;
    private static final int COMPATIBILITY_ACTIVE_COLOR = 0xFFB8CAE2;
    private static final int TRACKPAD_SCROLL_FACTOR = 5;

    private final List<KeyboardRowSpec> rows = SplitKeyboardLayout.createRows();
    private final List<KeyCapView> keyCaps = new ArrayList<>();
    private final Map<KeySpec, KeyCapView> viewsBySpec = new IdentityHashMap<>();
    private final Set<KeySpec> rightKeySpecs = Collections.newSetFromMap(
            new IdentityHashMap<>());
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private KeyboardStateController stateController;
    private SplitKeyboardPreferences preferences;
    private SubDisplayKeyboardControlsSession subDisplayControlsSession;
    private TrackpadMode trackpadMode = TrackpadMode.NONE;
    private int trackpadPointerId = -1;
    private float lastTrackpadX;
    private float lastTrackpadY;
    private CompatibilityToggleView compatibilityToggleView;
    private int compatibilityTogglePointerId = -1;
    private boolean compatibilityTogglePointerInside;

    public SplitKeyboardView(Context context) {
        this(context, null);
    }

    public SplitKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setMotionEventSplittingEnabled(true);
        setBackgroundColor(BACKGROUND_COLOR);
        setFocusable(false);
        setFocusableInTouchMode(false);
        for (KeyboardRowSpec row : rows) {
            rightKeySpecs.addAll(row.rightKeys);
            rightKeySpecs.addAll(row.navigationKeys);
        }
        createKeyCaps();
    }

    public void bind(KeyboardStateController controller,
                     SplitKeyboardPreferences preferences) {
        if (stateController != null) {
            stateController.setListener(null);
        }
        this.stateController = controller;
        this.preferences = preferences;
        controller.setListener(this);
        setCompatibilityToggleVisible(preferences.subDisplayMouseControls
                && preferences.mouseTouchCompatibility);
        if (subDisplayControlsSession != null) {
            subDisplayControlsSession.removeListener(this);
        }
        SubDisplayKeyboardControlsSession.getInstance()
                .setTouchCompatibilityEnabled(false);
        subDisplayControlsSession = preferences.subDisplayMouseControls
                ? SubDisplayKeyboardControlsSession.getInstance() : null;
        if (subDisplayControlsSession != null) {
            // The preference exposes the in-session button. Compatibility
            // itself always starts disabled and is controlled by that button.
            subDisplayControlsSession.addListener(this);
            trackpadMode = subDisplayControlsSession.getTrackpadMode();
        }
        else {
            trackpadMode = TrackpadMode.NONE;
        }
        setAlpha(preferences.opacityPercent / 100f);
        updateVisualStates();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }

        int keyGap = Math.max(1, Math.round(
                (preferences != null ? preferences.keyGapDp : 2) * density));
        int outerPadding = Math.max(keyGap, Math.round(Math.min(width, height) * 0.008f));
        int centerGapPercent = preferences != null ? preferences.centerGapPercent : 20;
        int centerGap = Math.round(width * centerGapPercent / 100f);
        centerGap = Math.max(Math.round(width * 0.12f),
                Math.min(Math.round(width * 0.30f), centerGap));

        int contentLeft = outerPadding;
        int contentRight = width - outerPadding;
        int contentWidth = Math.max(1, contentRight - contentLeft);
        int splitContentWidth = Math.max(1, contentWidth - centerGap);
        int leftWidth = splitContentWidth / 2;
        int leftStart = contentLeft;
        int leftEnd = leftStart + leftWidth;
        int rightStart = leftEnd + centerGap;
        int rightEnd = contentRight;
        float maxLeftUnits = 1f;
        float maxRightUnits = 1f;
        for (KeyboardRowSpec row : rows) {
            maxLeftUnits = Math.max(maxLeftUnits, totalUnits(row.leftKeys));
            maxRightUnits = Math.max(maxRightUnits, totalUnits(row.rightKeys));
        }
        int unitPitch = Math.max(keyGap + 1, (int) Math.floor(Math.min(
                (leftEnd - leftStart) / maxLeftUnits,
                (rightEnd - rightStart) / maxRightUnits)));

        float totalHeightWeight = 0f;
        for (KeyboardRowSpec row : rows) {
            totalHeightWeight += row.heightWeight;
        }
        int availableRowHeight = Math.max(1,
                height - outerPadding * 2 - keyGap * (rows.size() - 1));
        if (compatibilityToggleView != null) {
            int firstRowHeight = Math.max(1, Math.round(
                    availableRowHeight * rows.get(0).heightWeight / totalHeightWeight));
            int toggleInset = Math.max(keyGap, Math.round(centerGap * 0.08f));
            compatibilityToggleView.layout(
                    leftEnd + toggleInset, outerPadding,
                    rightStart - toggleInset, outerPadding + firstRowHeight);
        }
        float consumedWeight = 0f;
        int rowTop = outerPadding;

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            KeyboardRowSpec row = rows.get(rowIndex);
            consumedWeight += row.heightWeight;
            int rowBottom = rowIndex == rows.size() - 1
                    ? height - outerPadding
                    : outerPadding + Math.round(
                            availableRowHeight * consumedWeight / totalHeightWeight)
                            + keyGap * rowIndex;

            layoutFixedPitch(row.leftKeys, leftStart, unitPitch,
                    rowTop, rowBottom, keyGap);
            int rightGroupStart = rightEnd
                    - Math.round(totalUnits(row.rightKeys) * unitPitch);
            layoutFixedPitch(row.rightKeys, rightGroupStart, unitPitch,
                    rowTop, rowBottom, keyGap);

            if (row.centerKey != null) {
                int centerKeyLeft = leftStart
                        + Math.round(totalUnits(row.leftKeys) * unitPitch);
                int centerKeyRight = Math.max(centerKeyLeft + 1,
                        rightGroupStart - keyGap);
                KeyCapView centerView = viewsBySpec.get(row.centerKey);
                centerView.layout(centerKeyLeft, rowTop, centerKeyRight, rowBottom);
                centerView.setSplitSpaceGeometry(
                        Math.max(1, leftEnd - centerKeyLeft),
                        Math.max(1, rightStart - centerKeyLeft));
            }

            rowTop = rowBottom + keyGap;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (trackpadMode != TrackpadMode.NONE) {
            drawTrackpad(canvas);
        }
        if (stateController != null && !stateController.isTransportConnected()) {
            backgroundPaint.setColor(0xA6000000);
            canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
            backgroundPaint.setColor(Color.WHITE);
            backgroundPaint.setTextAlign(Paint.Align.CENTER);
            backgroundPaint.setTextSize(clamp(getHeight() * 0.055f,
                    12f * density, 22f * density));
            canvas.drawText("원격 연결 없음", getWidth() / 2f, getHeight() / 2f,
                    backgroundPaint);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (stateController == null) {
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handlePointerDownOrTrackpad(event, event.getActionIndex());
                return true;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                handlePointerUpOrTrackpad(event, event.getActionIndex());
                return true;

            case MotionEvent.ACTION_CANCEL:
                stateController.releaseAllPressedKeys(ReleaseReason.ACTION_CANCEL);
                resetTrackpadPointer();
                resetCompatibilityTogglePointer();
                updateVisualStates();
                return true;

            case MotionEvent.ACTION_MOVE:
                handleTrackpadMove(event);
                // Pointer-to-key assignment is pinned to the first key until release.
                return true;

            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (stateController != null) {
            stateController.releaseAllPressedKeys(ReleaseReason.VIEW_DETACHED);
            stateController.setListener(null);
        }
        if (subDisplayControlsSession != null) {
            subDisplayControlsSession.removeListener(this);
        }
        resetTrackpadPointer();
        resetCompatibilityTogglePointer();
        super.onDetachedFromWindow();
    }

    @Override
    public void onKeyboardStateChanged() {
        updateVisualStates();
    }

    @Override
    public void onSubDisplayKeyboardControlsChanged() {
        TrackpadMode newMode = subDisplayControlsSession != null
                ? subDisplayControlsSession.getTrackpadMode() : TrackpadMode.NONE;
        if (trackpadMode == TrackpadMode.NONE && newMode != TrackpadMode.NONE
                && stateController != null) {
            // Prevent a right-side key held while the pad appears from getting stuck.
            stateController.releasePressedKeys(rightKeySpecs, ReleaseReason.ACTION_CANCEL);
        }
        if (newMode == TrackpadMode.NONE) {
            resetTrackpadPointer();
        }
        trackpadMode = newMode;
        if (compatibilityToggleView != null && subDisplayControlsSession != null) {
            compatibilityToggleView.setActive(
                    subDisplayControlsSession.isTouchCompatibilityEnabled());
        }
        invalidate();
    }

    public void refreshConnectionState() {
        updateVisualStates();
        invalidate();
    }

    private void createKeyCaps() {
        for (KeyboardRowSpec row : rows) {
            addKeyCaps(row.leftKeys);
            addKeyCaps(row.rightKeys);
            addKeyCaps(row.navigationKeys);
            if (row.centerKey != null) {
                addKeyCap(row.centerKey);
            }
        }
    }

    private void setCompatibilityToggleVisible(boolean visible) {
        if (visible && compatibilityToggleView == null) {
            compatibilityToggleView = new CompatibilityToggleView(getContext());
            compatibilityToggleView.setOnClickListener(view -> {
                if (subDisplayControlsSession == null) {
                    return;
                }
                feedback();
                subDisplayControlsSession.setTouchCompatibilityEnabled(
                        !subDisplayControlsSession.isTouchCompatibilityEnabled());
            });
            addView(compatibilityToggleView);
        }
        else if (!visible && compatibilityToggleView != null) {
            resetCompatibilityTogglePointer();
            removeView(compatibilityToggleView);
            compatibilityToggleView = null;
        }
    }

    private void addKeyCaps(List<KeySpec> specs) {
        for (KeySpec spec : specs) {
            addKeyCap(spec);
        }
    }

    private KeyCapView addKeyCap(KeySpec spec) {
        KeyCapView view = new KeyCapView(getContext(), spec);
        view.setOnClickListener(v -> {
            if (stateController != null && isEffectiveKeyEnabled(spec)) {
                feedback();
                stateController.accessibilityTap(spec);
            }
        });
        keyCaps.add(view);
        viewsBySpec.put(spec, view);
        addView(view);
        return view;
    }

    private void layoutFixedPitch(List<KeySpec> specs, int start, int unitPitch,
                                  int top, int bottom, int gap) {
        if (specs.isEmpty()) {
            return;
        }
        float consumedUnits = 0f;
        int childLeft = start;
        for (KeySpec spec : specs) {
            consumedUnits += spec.widthWeight;
            int nextPitchLeft = start + Math.round(consumedUnits * unitPitch);
            int childRight = Math.max(childLeft + 1, nextPitchLeft - gap);
            KeyCapView child = viewsBySpec.get(spec);
            child.setSplitSpaceGeometry(0, 0);
            child.layout(childLeft, top, childRight, bottom);
            childLeft = nextPitchLeft;
        }
    }

    private static float totalUnits(List<KeySpec> specs) {
        float units = 0f;
        for (KeySpec spec : specs) {
            units += spec.widthWeight;
        }
        return units;
    }

    private void handlePointerDownOrTrackpad(MotionEvent event, int index) {
        int pointerId = event.getPointerId(index);
        if (compatibilityToggleView != null
                && compatibilityTogglePointerId == -1
                && isInsideCompatibilityToggle(event.getX(index), event.getY(index))) {
            compatibilityTogglePointerId = pointerId;
            compatibilityTogglePointerInside = true;
            compatibilityToggleView.setPressed(true);
            return;
        }
        if (trackpadMode != TrackpadMode.NONE && event.getX(index) >= getWidth() / 2f) {
            if (trackpadPointerId == -1) {
                trackpadPointerId = pointerId;
                lastTrackpadX = event.getX(index);
                lastTrackpadY = event.getY(index);
                feedback(false);
            }
            return;
        }
        KeyCapView keyCap = findKeyCap(event.getX(index), event.getY(index));
        if (keyCap == null) {
            return;
        }
        if (!isEffectiveKeyEnabled(keyCap.spec)) {
            performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }
        if (stateController.pointerDown(pointerId, keyCap.spec, event.getEventTime())) {
            feedback();
        }
    }

    private void handlePointerUpOrTrackpad(MotionEvent event, int index) {
        int pointerId = event.getPointerId(index);
        if (pointerId == compatibilityTogglePointerId) {
            boolean performClick = compatibilityTogglePointerInside
                    && isInsideCompatibilityToggle(event.getX(index), event.getY(index));
            resetCompatibilityTogglePointer();
            if (performClick && compatibilityToggleView != null) {
                compatibilityToggleView.performClick();
            }
            return;
        }
        if (pointerId == trackpadPointerId) {
            resetTrackpadPointer();
            return;
        }
        stateController.pointerUp(pointerId, event.getEventTime());
    }

    private void handleTrackpadMove(MotionEvent event) {
        if (compatibilityTogglePointerId != -1 && compatibilityToggleView != null) {
            int toggleIndex = event.findPointerIndex(compatibilityTogglePointerId);
            boolean inside = toggleIndex >= 0 && isInsideCompatibilityToggle(
                    event.getX(toggleIndex), event.getY(toggleIndex));
            if (compatibilityTogglePointerInside != inside) {
                compatibilityTogglePointerInside = inside;
                compatibilityToggleView.setPressed(inside);
            }
        }
        if (trackpadPointerId == -1 || subDisplayControlsSession == null) {
            return;
        }
        int index = event.findPointerIndex(trackpadPointerId);
        if (index < 0) {
            resetTrackpadPointer();
            return;
        }

        float x = event.getX(index);
        float y = event.getY(index);
        int deltaX = Math.round(x - lastTrackpadX);
        int deltaY = Math.round(y - lastTrackpadY);
        lastTrackpadX = x;
        lastTrackpadY = y;
        if (deltaX == 0 && deltaY == 0) {
            return;
        }

        if (trackpadMode == TrackpadMode.MOUSE) {
            subDisplayControlsSession.sendTrackpadMove(deltaX, deltaY);
        }
        else if (trackpadMode == TrackpadMode.SCROLL) {
            subDisplayControlsSession.sendTrackpadScroll(
                    deltaY * TRACKPAD_SCROLL_FACTOR,
                    deltaX * TRACKPAD_SCROLL_FACTOR);
        }
    }

    private void resetTrackpadPointer() {
        trackpadPointerId = -1;
        lastTrackpadX = 0;
        lastTrackpadY = 0;
    }

    private boolean isInsideCompatibilityToggle(float x, float y) {
        return compatibilityToggleView != null
                && x >= compatibilityToggleView.getLeft()
                && x < compatibilityToggleView.getRight()
                && y >= compatibilityToggleView.getTop()
                && y < compatibilityToggleView.getBottom();
    }

    private void resetCompatibilityTogglePointer() {
        compatibilityTogglePointerId = -1;
        compatibilityTogglePointerInside = false;
        if (compatibilityToggleView != null) {
            compatibilityToggleView.setPressed(false);
        }
    }

    private void drawTrackpad(Canvas canvas) {
        float left = getWidth() / 2f;
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(BACKGROUND_COLOR);
        canvas.drawRect(left, 0, getWidth(), getHeight(), backgroundPaint);

        float inset = Math.max(8f * density, getHeight() * 0.04f);
        float radius = clamp(getHeight() * 0.07f, 8f * density, 16f * density);
        RectF padRect = new RectF(left + inset, inset,
                getWidth() - inset, getHeight() - inset);

        RectF shadowRect = new RectF(padRect);
        shadowRect.offset(0, Math.max(1.5f, density * 1.4f));
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(TRACKPAD_SHADOW_COLOR);
        canvas.drawRoundRect(shadowRect, radius, radius, backgroundPaint);

        backgroundPaint.setColor(TRACKPAD_KEY_COLOR);
        canvas.drawRoundRect(padRect, radius, radius, backgroundPaint);

        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(Math.max(1.5f, density));
        backgroundPaint.setColor(TRACKPAD_BORDER_COLOR);
        canvas.drawRoundRect(padRect, radius, radius, backgroundPaint);

        float badgePaddingX = Math.max(9f * density, padRect.width() * 0.035f);
        float badgePaddingY = Math.max(5f * density, padRect.height() * 0.025f);
        String badge = trackpadMode == TrackpadMode.MOUSE ? "RT" : "RB";
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setTextSize(clamp(getHeight() * 0.045f,
                11f * density, 18f * density));
        float badgeTextWidth = backgroundPaint.measureText(badge);
        Paint.FontMetrics badgeMetrics = backgroundPaint.getFontMetrics();
        float badgeHeight = badgeMetrics.descent - badgeMetrics.ascent;
        RectF badgeRect = new RectF(padRect.left + badgePaddingX,
                padRect.top + badgePaddingY,
                padRect.left + badgePaddingX * 2f + badgeTextWidth,
                padRect.top + badgePaddingY * 2f + badgeHeight);
        backgroundPaint.setColor(TRACKPAD_BADGE_COLOR);
        canvas.drawRoundRect(badgeRect, 5f * density, 5f * density, backgroundPaint);
        backgroundPaint.setColor(TRACKPAD_ACCENT_COLOR);
        backgroundPaint.setTextAlign(Paint.Align.CENTER);
        float badgeBaseline = badgeRect.centerY()
                - (badgeMetrics.ascent + badgeMetrics.descent) / 2f;
        canvas.drawText(badge, badgeRect.centerX(), badgeBaseline, backgroundPaint);

        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setTextAlign(Paint.Align.CENTER);
        backgroundPaint.setTextSize(clamp(getHeight() * 0.07f,
                14f * density, 28f * density));
        backgroundPaint.setColor(TRACKPAD_TEXT_COLOR);
        String label = getResources().getString(trackpadMode == TrackpadMode.MOUSE
                ? R.string.split_keyboard_mouse_trackpad
                : R.string.split_keyboard_scroll_trackpad);
        Paint.FontMetrics metrics = backgroundPaint.getFontMetrics();
        float baseline = getHeight() * 0.45f
                - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, (left + getWidth()) / 2f, baseline, backgroundPaint);

        drawTrackpadDirectionIndicator(canvas,
                (left + getWidth()) / 2f,
                getHeight() * 0.67f,
                Math.min(padRect.width() * 0.16f, getHeight() * 0.105f),
                trackpadMode == TrackpadMode.SCROLL);
    }

    private void drawTrackpadDirectionIndicator(Canvas canvas, float centerX,
                                                 float centerY, float radius,
                                                 boolean drawArrowHeads) {
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeCap(Paint.Cap.ROUND);
        backgroundPaint.setStrokeWidth(Math.max(1.5f, density * 1.2f));
        backgroundPaint.setColor(drawArrowHeads
                ? TRACKPAD_ACCENT_COLOR : TRACKPAD_SECONDARY_TEXT_COLOR);
        canvas.drawLine(centerX - radius, centerY,
                centerX + radius, centerY, backgroundPaint);
        canvas.drawLine(centerX, centerY - radius,
                centerX, centerY + radius, backgroundPaint);

        float head = Math.max(4f * density, radius * 0.24f);
        if (drawArrowHeads) {
            drawArrowHead(canvas, centerX + radius, centerY, -head, -head);
            drawArrowHead(canvas, centerX + radius, centerY, -head, head);
            drawArrowHead(canvas, centerX - radius, centerY, head, -head);
            drawArrowHead(canvas, centerX - radius, centerY, head, head);
            drawArrowHead(canvas, centerX, centerY - radius, -head, head);
            drawArrowHead(canvas, centerX, centerY - radius, head, head);
            drawArrowHead(canvas, centerX, centerY + radius, -head, -head);
            drawArrowHead(canvas, centerX, centerY + radius, head, -head);
        }
        else {
            backgroundPaint.setStyle(Paint.Style.FILL);
            backgroundPaint.setColor(TRACKPAD_ACCENT_COLOR);
            canvas.drawCircle(centerX, centerY, Math.max(3f * density, radius * 0.12f),
                    backgroundPaint);
        }
        backgroundPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawArrowHead(Canvas canvas, float x, float y,
                               float deltaX, float deltaY) {
        canvas.drawLine(x, y, x + deltaX, y + deltaY, backgroundPaint);
    }

    private KeyCapView findKeyCap(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (!(child instanceof KeyCapView)) {
                continue;
            }
            if (child.getVisibility() == VISIBLE
                    && x >= child.getLeft() && x < child.getRight()
                    && y >= child.getTop() && y < child.getBottom()
                    && ((KeyCapView) child).containsLocalPoint(
                            x - child.getLeft(), y - child.getTop())) {
                return (KeyCapView) child;
            }
        }
        return null;
    }

    private boolean isEffectiveKeyEnabled(KeySpec spec) {
        if (stateController == null || !stateController.isTransportConnected()) {
            return false;
        }
        LogicalKey effective = spec.effectiveKey(stateController.isFnActive());
        return effective.transportSupported
                && (effective.androidKeyCode != android.view.KeyEvent.KEYCODE_UNKNOWN
                    || effective == LogicalKey.FN);
    }

    private void updateVisualStates() {
        if (stateController == null) {
            return;
        }
        boolean fnActive = stateController.isFnActive();
        for (KeyCapView keyCap : keyCaps) {
            KeySpec spec = keyCap.spec;
            LogicalKey effective = spec.effectiveKey(fnActive);
            boolean pressed = stateController.isPointerHolding(spec);
            boolean locked = stateController.isLocked(spec.logicalKey);
            boolean oneShot = stateController.isOneShot(spec.logicalKey);
            boolean enabled = isEffectiveKeyEnabled(spec);
            boolean shiftActive = stateController.isShiftActive();
            keyCap.setVisualState(pressed, locked, oneShot, enabled, shiftActive);

            String description = spec.accessibilityLabel;
            if (oneShot) {
                description += " 원샷 대기";
            }
            else if (locked) {
                description += " 토글 잠금";
            }
            else if (pressed) {
                description += " 누름";
            }
            if (fnActive && spec.fnMappedKey != null) {
                description += ", Fn " + SplitKeyboardLayout.labelFor(effective);
            }
            keyCap.setContentDescription(description);
        }
        invalidate();
    }

    private final class CompatibilityToggleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF buttonRect = new RectF();
        private boolean active;

        CompatibilityToggleView(Context context) {
            super(context);
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            updateContentDescription();
        }

        void setActive(boolean active) {
            if (this.active == active) {
                return;
            }
            this.active = active;
            updateContentDescription();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = Math.max(0.75f, density * (isPressed() ? 1.3f : 0.65f));
            float pressedOffset = isPressed() ? density : 0f;
            float radius = clamp(getHeight() * 0.17f, 5f * density, 11f * density);

            buttonRect.set(inset, inset + 1.5f * density,
                    getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(TRACKPAD_SHADOW_COLOR);
            canvas.drawRoundRect(buttonRect, radius, radius, paint);

            buttonRect.set(inset, inset + pressedOffset,
                    getWidth() - inset,
                    getHeight() - inset - 1.4f * density + pressedOffset);
            paint.setColor(active ? COMPATIBILITY_ACTIVE_COLOR
                    : isPressed() ? TRACKPAD_KEY_COLOR : TRACKPAD_BADGE_COLOR);
            canvas.drawRoundRect(buttonRect, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(active ? Math.max(1.5f, density)
                    : Math.max(0.6f, density * 0.45f));
            paint.setColor(active ? TRACKPAD_ACCENT_COLOR : TRACKPAD_BORDER_COLOR);
            canvas.drawRoundRect(buttonRect, radius, radius, paint);

            drawToggleText(canvas, getResources().getString(
                            R.string.split_keyboard_compatibility_mode),
                    getHeight() * 0.37f, 0.18f, TRACKPAD_TEXT_COLOR);
            drawToggleText(canvas, getResources().getString(active
                            ? R.string.split_keyboard_compatibility_on
                            : R.string.split_keyboard_compatibility_off),
                    getHeight() * 0.69f, 0.23f,
                    active ? TRACKPAD_ACCENT_COLOR : TRACKPAD_SECONDARY_TEXT_COLOR);
        }

        private void drawToggleText(Canvas canvas, String text, float centerY,
                                    float heightFraction, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
            paint.setTextSize(Math.max(8f * density, getHeight() * heightFraction));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, getWidth() / 2f, baseline, paint);
        }

        private void updateContentDescription() {
            setContentDescription(getResources().getString(
                    R.string.split_keyboard_compatibility_mode)
                    + " " + getResources().getString(active
                    ? R.string.split_keyboard_compatibility_on
                    : R.string.split_keyboard_compatibility_off));
        }
    }

    private void feedback() {
        feedback(true);
    }

    private void feedback(boolean includeHaptic) {
        if (preferences == null) {
            return;
        }
        if (includeHaptic && preferences.hapticEnabled) {
            Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            7, preferences.hapticStrength));
                }
                else {
                    vibrator.vibrate(7);
                }
            }
            else {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        }
        if (preferences.clickSoundEnabled) {
            playSoundEffect(SoundEffectConstants.CLICK);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private final class KeyCapView extends View {
        private static final int KEY_COLOR = 0xFFFAFAFC;
        private static final int ACTION_KEY_COLOR = 0xFFD0D1D5;
        private static final int KEY_PRESSED_COLOR = 0xFFC2CBD8;
        private static final int KEY_LOCKED_COLOR = 0xFFB8CAE2;
        private static final int KEY_DISABLED_COLOR = 0xFFD8D9DC;
        private static final int KEY_SHADOW_COLOR = 0x26000000;
        private static final int KEY_BORDER_COLOR = 0x18000000;
        private static final int KEY_ACCENT_COLOR = 0xFF3D78CC;
        private static final int KEY_TEXT_COLOR = 0xFF24262B;
        private static final int KEY_SECONDARY_TEXT_COLOR = 0xFF676B73;

        private final KeySpec spec;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF keyRect = new RectF();
        private boolean visuallyPressed;
        private boolean locked;
        private boolean oneShot;
        private boolean shiftActive;
        private float splitLeftEnd;
        private float splitRightStart;

        KeyCapView(Context context, KeySpec spec) {
            super(context);
            this.spec = spec;
            setWillNotDraw(false);
            setClickable(true);
            setFocusable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(spec.accessibilityLabel);
        }

        void setVisualState(boolean pressed, boolean locked, boolean oneShot,
                            boolean enabled, boolean shiftActive) {
            if (visuallyPressed == pressed && this.locked == locked
                    && this.oneShot == oneShot && isEnabled() == enabled
                    && this.shiftActive == shiftActive) {
                return;
            }
            visuallyPressed = pressed;
            this.locked = locked;
            this.oneShot = oneShot;
            this.shiftActive = shiftActive;
            setEnabled(enabled);
            invalidate();
        }

        void setSplitSpaceGeometry(int leftEnd, int rightStart) {
            float newLeftEnd = Math.max(0, leftEnd);
            float newRightStart = Math.max(0, rightStart);
            if (splitLeftEnd == newLeftEnd && splitRightStart == newRightStart) {
                return;
            }
            splitLeftEnd = newLeftEnd;
            splitRightStart = newRightStart;
            invalidate();
        }

        boolean containsLocalPoint(float x, float y) {
            if (x < 0 || x >= getWidth() || y < 0 || y >= getHeight()) {
                return false;
            }
            return !isSplitSpace()
                    || x < splitLeftEnd
                    || x >= splitRightStart;
        }

        private boolean isSplitSpace() {
            return spec.logicalKey == LogicalKey.SPACE
                    && splitLeftEnd > 0
                    && splitRightStart > splitLeftEnd;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (isSplitSpace()) {
                drawKeyBody(canvas, 0, splitLeftEnd, false);
                drawKeyBody(canvas, splitRightStart, getWidth(), true);
                drawSpaceIndicator(canvas, 0, splitLeftEnd);
                drawSpaceIndicator(canvas, splitRightStart, getWidth());
                return;
            }

            drawKeyBody(canvas, 0, getWidth(), true);
            drawLabels(canvas);
        }

        private void drawKeyBody(Canvas canvas, float left, float right,
                                 boolean drawLockIndicator) {
            if (right - left <= 1f) {
                return;
            }
            float inset = Math.max(0.75f, density * (visuallyPressed ? 1.3f : 0.65f));
            float pressedOffset = visuallyPressed ? density : 0f;
            float radius = clamp(getHeight() * 0.17f, 5f * density, 11f * density);

            keyRect.set(left + inset, inset + 1.5f * density,
                    right - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(KEY_SHADOW_COLOR);
            canvas.drawRoundRect(keyRect, radius, radius, paint);

            int background = locked ? KEY_LOCKED_COLOR
                    : visuallyPressed ? KEY_PRESSED_COLOR
                    : isActionKey() ? ACTION_KEY_COLOR : KEY_COLOR;
            if (!isEnabled()) {
                background = KEY_DISABLED_COLOR;
            }
            keyRect.set(left + inset, inset + pressedOffset,
                    right - inset, getHeight() - inset - 1.4f * density + pressedOffset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            canvas.drawRoundRect(keyRect, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(locked ? Math.max(1.5f, density) : Math.max(0.6f, density * 0.45f));
            paint.setColor(locked ? KEY_ACCENT_COLOR : KEY_BORDER_COLOR);
            canvas.drawRoundRect(keyRect, radius, radius, paint);

            if (locked && drawLockIndicator) {
                paint.setStyle(oneShot ? Paint.Style.STROKE : Paint.Style.FILL);
                paint.setStrokeWidth(Math.max(1.5f, density));
                paint.setColor(KEY_ACCENT_COLOR);
                canvas.drawCircle(right - radius * 0.8f, radius * 0.8f,
                        Math.max(2f, radius * 0.22f), paint);
            }
        }

        private void drawLabels(Canvas canvas) {
            boolean fnActive = stateController != null && stateController.isFnActive();
            if (fnActive && spec.fnMappedKey != null) {
                drawCentered(canvas, SplitKeyboardLayout.labelFor(spec.fnMappedKey),
                        0.23f, spec.fnMappedKey.transportSupported
                                ? KEY_ACCENT_COLOR : KEY_SECONDARY_TEXT_COLOR);
                return;
            }

            if (spec.logicalKey == LogicalKey.SPACE && spec.primaryLabel.isEmpty()) {
                drawSpaceIndicator(canvas, 0, getWidth());
                return;
            }

            if (spec.hangulLabels) {
                drawText(canvas, spec.primaryLabel, getWidth() * 0.18f,
                        getHeight() * 0.39f, Paint.Align.LEFT, 0.28f, KEY_TEXT_COLOR, true);
                if (preferences == null || preferences.showHangulLabels) {
                    drawText(canvas, spec.secondaryLabel, getWidth() * 0.78f,
                            getHeight() * 0.75f, Paint.Align.CENTER, 0.22f,
                            KEY_SECONDARY_TEXT_COLOR, false);
                }
                if (spec.tertiaryLabel != null
                        && (preferences == null || preferences.showShiftHangulLabels)) {
                    drawText(canvas, spec.tertiaryLabel, getWidth() * 0.78f,
                            getHeight() * 0.34f, Paint.Align.CENTER, 0.19f,
                            shiftActive ? KEY_ACCENT_COLOR : KEY_SECONDARY_TEXT_COLOR, false);
                }
                return;
            }

            if (spec.secondaryLabel != null) {
                if (spec.fnMappedKey != null) {
                    drawText(canvas, SplitKeyboardLayout.labelFor(spec.fnMappedKey),
                            getWidth() * 0.5f, getHeight() * 0.27f,
                            Paint.Align.CENTER, 0.17f, KEY_ACCENT_COLOR, false);
                    drawText(canvas, spec.primaryLabel + " " + spec.secondaryLabel,
                            getWidth() * 0.5f, getHeight() * 0.69f,
                            Paint.Align.CENTER, 0.22f,
                            shiftActive ? KEY_ACCENT_COLOR : KEY_TEXT_COLOR, true);
                    return;
                }
                drawText(canvas, spec.secondaryLabel, getWidth() * 0.5f,
                        getHeight() * 0.30f, Paint.Align.CENTER, 0.18f,
                        shiftActive ? KEY_ACCENT_COLOR : KEY_SECONDARY_TEXT_COLOR, false);
                drawText(canvas, spec.primaryLabel, getWidth() * 0.5f,
                        getHeight() * 0.69f, Paint.Align.CENTER, 0.27f,
                        KEY_TEXT_COLOR, true);
                return;
            }

            if (spec.fnMappedKey != null) {
                drawText(canvas, SplitKeyboardLayout.labelFor(spec.fnMappedKey),
                        getWidth() * 0.5f, getHeight() * 0.28f,
                        Paint.Align.CENTER, 0.17f, KEY_ACCENT_COLOR, false);
                drawText(canvas, spec.primaryLabel, getWidth() * 0.5f,
                        getHeight() * 0.69f, Paint.Align.CENTER, 0.22f,
                        isEnabled() ? KEY_TEXT_COLOR : KEY_SECONDARY_TEXT_COLOR, true);
                return;
            }

            drawCentered(canvas, spec.primaryLabel,
                    spec.logicalKey.name().startsWith("F") ? 0.24f : 0.23f,
                    isEnabled() ? KEY_TEXT_COLOR : KEY_SECONDARY_TEXT_COLOR);
        }

        private void drawSpaceIndicator(Canvas canvas, float left, float right) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(KEY_SECONDARY_TEXT_COLOR);
            float half = Math.min((right - left) * 0.14f, 26f * density);
            float center = (left + right) / 2f;
            float y = getHeight() * 0.62f;
            canvas.drawLine(center - half, y, center + half, y, paint);
        }

        private boolean isActionKey() {
            switch (spec.logicalKey) {
                case ESCAPE:
                case BACKSPACE:
                case TAB:
                case CAPS_LOCK:
                case ENTER:
                case HOME:
                case DELETE:
                case PAGE_UP:
                case PAGE_DOWN:
                case LEFT_SHIFT:
                case RIGHT_SHIFT:
                case LEFT_CTRL:
                case RIGHT_CTRL:
                case LEFT_ALT:
                case RIGHT_ALT:
                case LEFT_META:
                case RIGHT_META:
                case HANGUL_TOGGLE:
                case FN:
                case MENU:
                case ARROW_UP:
                case ARROW_DOWN:
                case ARROW_LEFT:
                case ARROW_RIGHT:
                    return true;
                default:
                    return false;
            }
        }

        private void drawCentered(Canvas canvas, String text, float heightFraction, int color) {
            drawText(canvas, text, getWidth() / 2f, getHeight() / 2f,
                    Paint.Align.CENTER, heightFraction, color, true);
        }

        private void drawText(Canvas canvas, String text, float x, float centerY,
                              Paint.Align align, float heightFraction, int color,
                              boolean mediumWeight) {
            if (text == null || text.isEmpty()) {
                return;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextAlign(align);
            paint.setTypeface(mediumWeight
                    ? android.graphics.Typeface.create("sans-serif-medium",
                        android.graphics.Typeface.NORMAL)
                    : android.graphics.Typeface.create("sans-serif",
                        android.graphics.Typeface.NORMAL));
            float textSize = clamp(getHeight() * heightFraction,
                    7f * density, 21f * density);
            paint.setTextSize(textSize);
            float available = Math.max(1f, getWidth() - 6f * density);
            if (paint.measureText(text) > available) {
                paint.setTextSize(Math.max(6f * density,
                        textSize * available / paint.measureText(text)));
            }
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(text, x, baseline, paint);
        }
    }
}
