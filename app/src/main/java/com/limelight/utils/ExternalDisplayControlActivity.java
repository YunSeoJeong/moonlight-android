package com.limelight.utils;

import static com.limelight.StartExternalDisplayControlReceiver.requestFocusToGameActivity;
import static com.limelight.utils.ServerHelper.getSecondaryDisplay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.limelight.Game;
import com.limelight.GameMenu;
import com.limelight.LimeLog;
import com.limelight.R;
import com.limelight.StartExternalDisplayControlReceiver;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardLayoutController;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.ExternalControllerView;

/**
 * A standalone Activity providing a full-screen touchpad controller for the secondary display.
 * It creates its own UI programmatically and hosts the GameMenu for in-game options.
 */
public class ExternalDisplayControlActivity extends AppCompatActivity implements View.OnKeyListener, KeyBoardLayoutController.ViewCallbacks {

    public static String EXTRA_LAUNCH_INTENT = "launchIntent";

    @SuppressLint("StaticFieldLeak")
    public static ExternalDisplayControlActivity instance;

    private PreferenceConfiguration prefConfig;

    private ExternalControllerView rootLayout;
    private ImageButton zoomButton;
    private KeyBoardLayoutController keyBoardLayoutController;
    private VirtualController virtualController;
    private Game boundGame;
    private final SparseArray<VirtualControllerElement> subVirtualTouchTargets = new SparseArray<>();
    private final SparseBooleanArray subForwardedTouchIds = new SparseBooleanArray();

    private boolean isKeyboardVisible = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int failCount = 0;
    private Runnable dimScreenRunnable;
    private float originalBrightness = -1f; // -1 = use system default
    private static final int INACTIVITY_TIMEOUT_MS = 10_000;


    private static final String NOTIFICATION_CHANNEL_ID = "secondary_screen_active_channel_id";
    public static final int SECONDARY_SCREEN_NOTIFICATION_ID = 1;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private GameMenu gameMenu;

    // --- Static Methods for External Control ---

    public static void closeExternalDisplayControl() {
        if (instance != null) {
            instance.finish();
        }
    }

    public static void toggleKeyboard() {
        if (instance != null) {
            instance._toggleKeyboard();
        }
    }

    public static void toggleFullKeyboard() {
        if (instance != null) {
            instance._toggleFullKeyboard();
        }
    }

    public static void toggleGameMenu() {
        if (instance != null) {
            instance.showGameMenu();
        }
    }

    // --- Activity Lifecycle ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance = this;
        prefConfig = PreferenceConfiguration.readPreferences(this);
        logLifecycleState("onCreate");

        if (!isGameInstanceAvailable()) {
            Intent gameIntent = getIntent().getParcelableExtra(EXTRA_LAUNCH_INTENT);
            if (gameIntent == null) {
                LimeLog.warning("ExternalDisplayControlActivity.onCreate: no Game instance and no launch intent");
                finish();
            } else {
                Display secondaryDisplay = getSecondaryDisplay(this);
                if (secondaryDisplay != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(secondaryDisplay.getDisplayId());
                    Toast.makeText(this,
                            getString(R.string.external_display_info,
                                    secondaryDisplay.getMode().getPhysicalWidth(),
                                    secondaryDisplay.getMode().getPhysicalHeight(),
                                    secondaryDisplay.getMode().getRefreshRate()),
                            Toast.LENGTH_LONG).show();

                    startActivity(gameIntent, options.toBundle());
                } else {
                    LimeLog.warning(getString(R.string.no_external_display));
                    startActivity(gameIntent);
                    finish();
                }
            }
        }

        initViews();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        logLifecycleState("onNewIntent");

        if (rootLayout != null) {
            rebindToCurrentGame();
        }
    }

    private void initViews() {
        if (Game.instance == null) {
            if (failCount > 10) {
                Toast.makeText(this, getString(R.string.no_game_instance), Toast.LENGTH_LONG).show();
                LimeLog.warning("ExternalDisplayControlActivity.initViews: giving up waiting for Game instance");
                finish();
            }
            // Wait for the intent to get started
            LimeLog.info("ExternalDisplayControlActivity.initViews: waiting for Game instance failCount=" +
                    failCount);
            handler.postDelayed(this::initViews, 500);
            failCount++;
            return;
        }

        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, insets) -> {
                boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                updateKeyboardVisibility(imeVisible || (keyBoardLayoutController != null && keyBoardLayoutController.isKeyboardVisible()));
                return androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets);
            });
        }

        rebindToCurrentGame();
        checkNotificationPermission();
        setupInactivityTimeoutForBrightness();
        requestFocusToGameActivity(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initTouchEventHandling() {
        // Intercept touch events on root layout
        rootLayout.setOnTouchListener((v, event) -> {
            handleUserActivity();
            boolean actionPointerWasVirtual = isActionPointerSubVirtualTouch(event);
            routeSubVirtualControllerTouch(event);
            forwardSubTouchToGame(v, event, actionPointerWasVirtual);
            return true;
        });
    }

    private boolean isActionPointerSubVirtualTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_POINTER_UP && action != MotionEvent.ACTION_UP) {
            return false;
        }
        return subVirtualTouchTargets.get(event.getPointerId(event.getActionIndex())) != null;
    }

    private boolean routeSubVirtualControllerTouch(MotionEvent event) {
        if (virtualController == null ||
                virtualController.getControllerMode() != VirtualController.ControllerMode.Active) {
            subVirtualTouchTargets.clear();
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int pointerIndex = event.getActionIndex();
                VirtualControllerElement target = findSubVirtualControllerElementAt(
                        event.getX(pointerIndex), event.getY(pointerIndex));
                if (target == null) {
                    return false;
                }

                int pointerId = event.getPointerId(pointerIndex);
                subVirtualTouchTargets.put(pointerId, target);
                dispatchSubVirtualPointerEvent(target, event, MotionEvent.ACTION_DOWN, pointerIndex);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                boolean handled = false;
                for (int i = subVirtualTouchTargets.size() - 1; i >= 0; i--) {
                    int pointerId = subVirtualTouchTargets.keyAt(i);
                    int pointerIndex = event.findPointerIndex(pointerId);
                    if (pointerIndex < 0) {
                        subVirtualTouchTargets.removeAt(i);
                        continue;
                    }

                    dispatchSubVirtualPointerEvent(subVirtualTouchTargets.valueAt(i),
                            event, MotionEvent.ACTION_MOVE, pointerIndex);
                    handled = true;
                }
                return handled;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                VirtualControllerElement target = subVirtualTouchTargets.get(pointerId);
                if (target == null) {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        subVirtualTouchTargets.clear();
                    }
                    return false;
                }

                dispatchSubVirtualPointerEvent(target, event, MotionEvent.ACTION_UP, pointerIndex);
                subVirtualTouchTargets.delete(pointerId);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    subVirtualTouchTargets.clear();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                boolean handled = false;
                for (int i = subVirtualTouchTargets.size() - 1; i >= 0; i--) {
                    int pointerId = subVirtualTouchTargets.keyAt(i);
                    int pointerIndex = event.findPointerIndex(pointerId);
                    if (pointerIndex >= 0) {
                        dispatchSubVirtualPointerEvent(subVirtualTouchTargets.valueAt(i),
                                event, MotionEvent.ACTION_CANCEL, pointerIndex);
                        handled = true;
                    }
                }
                subVirtualTouchTargets.clear();
                return handled;
            }
            default:
                return false;
        }
    }

    private VirtualControllerElement findSubVirtualControllerElementAt(float x, float y) {
        if (virtualController == null) {
            return null;
        }

        for (int i = virtualController.getElements().size() - 1; i >= 0; i--) {
            VirtualControllerElement element = virtualController.getElements().get(i);
            if (element.getVisibility() != View.VISIBLE) {
                continue;
            }

            float[] localPoint = getSubVirtualElementLocalPoint(element, x, y);
            float localX = localPoint[0];
            float localY = localPoint[1];
            if (localX >= 0 && localY >= 0 &&
                    localX < element.getWidth() && localY < element.getHeight()) {
                return element;
            }
        }

        return null;
    }

    private void dispatchSubVirtualPointerEvent(VirtualControllerElement target,
                                                MotionEvent sourceEvent,
                                                int action,
                                                int pointerIndex) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];

        properties[0] = new MotionEvent.PointerProperties();
        sourceEvent.getPointerProperties(pointerIndex, properties[0]);

        coords[0] = new MotionEvent.PointerCoords();
        sourceEvent.getPointerCoords(pointerIndex, coords[0]);
        float[] localPoint = getSubVirtualElementLocalPoint(target, coords[0].x, coords[0].y);
        coords[0].x = localPoint[0];
        coords[0].y = localPoint[1];

        MotionEvent pointerEvent = MotionEvent.obtain(
                sourceEvent.getDownTime(),
                sourceEvent.getEventTime(),
                action,
                1,
                properties,
                coords,
                sourceEvent.getMetaState(),
                sourceEvent.getButtonState(),
                sourceEvent.getXPrecision(),
                sourceEvent.getYPrecision(),
                sourceEvent.getDeviceId(),
                sourceEvent.getEdgeFlags(),
                sourceEvent.getSource(),
                sourceEvent.getFlags());

        try {
            target.onTouchEvent(pointerEvent);
        } finally {
            pointerEvent.recycle();
        }
    }

    private float[] getSubVirtualElementLocalPoint(View element, float rootX, float rootY) {
        if (rootLayout == null) {
            return new float[] { rootX - element.getX(), rootY - element.getY() };
        }

        int[] rootLocation = new int[2];
        int[] elementLocation = new int[2];
        rootLayout.getLocationOnScreen(rootLocation);
        element.getLocationOnScreen(elementLocation);
        return new float[] {
                rootLocation[0] + rootX - elementLocation[0],
                rootLocation[1] + rootY - elementLocation[1]
        };
    }

    private boolean forwardSubTouchToGame(View view, MotionEvent event, boolean actionPointerWasVirtual) {
        if (Game.instance == null) {
            subForwardedTouchIds.clear();
            return true;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                return forwardSubTouchDownToGame(view, event, actionPointerWasVirtual);
            case MotionEvent.ACTION_MOVE:
                return forwardSubTouchMoveToGame(view, event);
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                return forwardSubTouchUpToGame(view, event, actionPointerWasVirtual);
            case MotionEvent.ACTION_CANCEL:
                return forwardSubTouchCancelToGame(view, event);
            default:
                return Game.instance.handleMotionEvent(view, event);
        }
    }

    private boolean forwardSubTouchDownToGame(View view, MotionEvent event, boolean actionPointerWasVirtual) {
        int actionPointerId = event.getPointerId(event.getActionIndex());
        if (actionPointerWasVirtual || subVirtualTouchTargets.get(actionPointerId) != null) {
            return true;
        }

        boolean hadForwardedPointers = hasForwardedTouchPointers();
        int[] pointerIndices = getForwardablePointerIndices(event, false);
        int filteredActionIndex = indexOfPointerId(event, pointerIndices, actionPointerId);
        if (filteredActionIndex < 0) {
            return true;
        }

        int action = hadForwardedPointers
                ? MotionEvent.ACTION_POINTER_DOWN |
                (filteredActionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                : MotionEvent.ACTION_DOWN;
        boolean handled = dispatchFilteredSubTouchToGame(view, event, action, pointerIndices);
        subForwardedTouchIds.put(actionPointerId, true);
        return handled;
    }

    private boolean forwardSubTouchMoveToGame(View view, MotionEvent event) {
        int[] pointerIndices = getForwardablePointerIndices(event, true);
        if (pointerIndices.length == 0) {
            return true;
        }

        return dispatchFilteredSubTouchToGame(view, event, MotionEvent.ACTION_MOVE, pointerIndices);
    }

    private boolean forwardSubTouchUpToGame(View view, MotionEvent event, boolean actionPointerWasVirtual) {
        int actionPointerId = event.getPointerId(event.getActionIndex());
        if (actionPointerWasVirtual || !subForwardedTouchIds.get(actionPointerId)) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                subForwardedTouchIds.clear();
            }
            return true;
        }

        int[] pointerIndices = getForwardablePointerIndices(event, true);
        int filteredActionIndex = indexOfPointerId(event, pointerIndices, actionPointerId);
        if (filteredActionIndex < 0) {
            subForwardedTouchIds.delete(actionPointerId);
            return true;
        }

        int action = pointerIndices.length == 1
                ? MotionEvent.ACTION_UP
                : MotionEvent.ACTION_POINTER_UP |
                (filteredActionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        boolean handled = dispatchFilteredSubTouchToGame(view, event, action, pointerIndices);
        subForwardedTouchIds.delete(actionPointerId);
        if (action == MotionEvent.ACTION_UP) {
            subForwardedTouchIds.clear();
        }
        return handled;
    }

    private boolean forwardSubTouchCancelToGame(View view, MotionEvent event) {
        int[] pointerIndices = getForwardablePointerIndices(event, true);
        subForwardedTouchIds.clear();
        if (pointerIndices.length == 0) {
            return true;
        }

        return dispatchFilteredSubTouchToGame(view, event, MotionEvent.ACTION_CANCEL, pointerIndices);
    }

    private boolean dispatchFilteredSubTouchToGame(View view,
                                                   MotionEvent sourceEvent,
                                                   int action,
                                                   int[] pointerIndices) {
        MotionEvent filteredEvent = obtainFilteredSubTouchEvent(sourceEvent, action, pointerIndices);
        try {
            return Game.instance.handleMotionEvent(view, filteredEvent);
        } finally {
            filteredEvent.recycle();
        }
    }

    private MotionEvent obtainFilteredSubTouchEvent(MotionEvent sourceEvent,
                                                    int action,
                                                    int[] pointerIndices) {
        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerIndices.length];
        MotionEvent.PointerCoords[] coords =
                new MotionEvent.PointerCoords[pointerIndices.length];

        for (int i = 0; i < pointerIndices.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            sourceEvent.getPointerProperties(pointerIndices[i], properties[i]);

            coords[i] = new MotionEvent.PointerCoords();
            sourceEvent.getPointerCoords(pointerIndices[i], coords[i]);
        }

        return MotionEvent.obtain(
                sourceEvent.getDownTime(),
                sourceEvent.getEventTime(),
                action,
                pointerIndices.length,
                properties,
                coords,
                sourceEvent.getMetaState(),
                sourceEvent.getButtonState(),
                sourceEvent.getXPrecision(),
                sourceEvent.getYPrecision(),
                sourceEvent.getDeviceId(),
                sourceEvent.getEdgeFlags(),
                sourceEvent.getSource(),
                sourceEvent.getFlags());
    }

    private int[] getForwardablePointerIndices(MotionEvent event, boolean onlyForwarded) {
        int[] scratch = new int[event.getPointerCount()];
        int count = 0;
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pointerId = event.getPointerId(i);
            if (subVirtualTouchTargets.get(pointerId) != null) {
                continue;
            }
            if (onlyForwarded && !subForwardedTouchIds.get(pointerId)) {
                continue;
            }
            scratch[count++] = i;
        }

        int[] pointerIndices = new int[count];
        System.arraycopy(scratch, 0, pointerIndices, 0, count);
        return pointerIndices;
    }

    private int indexOfPointerId(MotionEvent event, int[] pointerIndices, int pointerId) {
        for (int i = 0; i < pointerIndices.length; i++) {
            if (event.getPointerId(pointerIndices[i]) == pointerId) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasForwardedTouchPointers() {
        for (int i = 0; i < subForwardedTouchIds.size(); i++) {
            if (subForwardedTouchIds.valueAt(i)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        logLifecycleState("onResume");
        if (!isGameInstanceAvailable() && gameMenu != null) {
            finish();
            return;
        }

        if (rootLayout != null && boundGame != Game.instance) {
            rebindToCurrentGame();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        logLifecycleState("onPause");
        if (!isGameInstanceAvailable()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        logLifecycleState("onDestroy before cleanup");
        super.onDestroy();
        Game game = boundGame != null ? boundGame : Game.instance;
        if (game != null) {
            game.resetVirtualControllerInputState(VirtualController.DISPLAY_TARGET_SUB);
        }
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public void onKeyboardControllerVisibilityChange(boolean visible) {
        updateKeyboardVisibility(visible);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupInactivityTimeoutForBrightness() {
        if (dimScreenRunnable != null) {
            handler.removeCallbacks(dimScreenRunnable);
        }

        // Save the original brightness
        WindowManager.LayoutParams layout = getWindow().getAttributes();
        originalBrightness = layout.screenBrightness;

        // Runnable to dim screen
        dimScreenRunnable = () -> {
            WindowManager.LayoutParams l = getWindow().getAttributes();
            l.screenBrightness = 0.0f;
            getWindow().setAttributes(l);
        };

        // Start the timer
        resetInactivityTimer();
    }

    private void updateKeyboardVisibility(boolean visible) {
        if (isKeyboardVisible != visible) {
            isKeyboardVisible = visible;
            if (isKeyboardVisible) {
                // Keyboard is visible, so prevent screen dimming
                handler.removeCallbacks(dimScreenRunnable);
                // and restore brightness
                restoreBrightnessIfNeeded();
            } else {
                // Keyboard is hidden, so resume inactivity timer
                resetInactivityTimer();
            }
        }
    }

    private void restoreBrightnessIfNeeded() {
        WindowManager.LayoutParams l = getWindow().getAttributes();
        if (l.screenBrightness == 0.0f) {
            l.screenBrightness = originalBrightness;
            getWindow().setAttributes(l);
        }
    }

    private void handleUserActivity() {
        // Restore brightness if dimmed
        restoreBrightnessIfNeeded();
        resetInactivityTimer();
    }

    private void resetInactivityTimer() {
        handler.removeCallbacks(dimScreenRunnable);
        if (!isKeyboardVisible) {
            handler.postDelayed(dimScreenRunnable, INACTIVITY_TIMEOUT_MS);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isGameInstanceAvailable()) {
            Game.instance.handleFocusChange(hasFocus);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (Game.instance != null && Game.instance.isKeyboardLayoutVisible()) {
            toggleFullKeyboard();
        } else if (gameMenu != null && !gameMenu.isMenuOpen() && Game.instance != null)
            Game.instance.onBackPressed();
        else {
            super.onBackPressed();
        }
    }

    // --- Initialization and UI Creation ---

    /**
     * Checks if the static Game.instance is alive. If not, finishes this Activity.
     */
    private boolean isGameInstanceAvailable() {
        return Game.instance != null;
    }

    private void logLifecycleState(String event) {
        LimeLog.info("ExternalDisplayControlActivity." + event +
                ": hasGame=" + (Game.instance != null) +
                " boundGame=" + (boundGame != null) +
                " dualScreenVirtualGamepad=" +
                (prefConfig != null && prefConfig.dualScreenVirtualGamepad) +
                " root=" + getRootLayoutSize() +
                " subElements=" + getSubVirtualControllerElementCount() +
                " displayId=" + getDisplayIdSafe() +
                " taskId=" + getTaskId());
    }

    private String getRootLayoutSize() {
        if (rootLayout == null) {
            return "null";
        }
        return rootLayout.getWidth() + "x" + rootLayout.getHeight();
    }

    private int getSubVirtualControllerElementCount() {
        return virtualController != null ? virtualController.getElements().size() : -1;
    }

    private int getDisplayIdSafe() {
        try {
            Display display = getWindowManager().getDefaultDisplay();
            return display != null ? display.getDisplayId() : -1;
        } catch (RuntimeException e) {
            LimeLog.warning("ExternalDisplayControlActivity: unable to query display id: " +
                    e.getMessage());
            return -1;
        }
    }

    /**
     * Initializes core components needed for this controller Activity.
     */
    private void initializeComponents() {
        this.gameMenu = new GameMenu(Game.instance, instance);
    }

    private void rebindToCurrentGame() {
        logLifecycleState("rebindToCurrentGame begin");
        if (!isGameInstanceAvailable()) {
            finish();
            return;
        }

        if (boundGame != null && boundGame != Game.instance) {
            LimeLog.info("ExternalDisplayControlActivity.rebindToCurrentGame: resetting previous bound Game sub input state");
            boundGame.resetVirtualControllerInputState(VirtualController.DISPLAY_TARGET_SUB);
        }

        boundGame = Game.instance;
        prefConfig = PreferenceConfiguration.readPreferences(this);
        virtualController = null;
        keyBoardLayoutController = null;
        isKeyboardVisible = false;

        initializeComponents();
        createProgrammaticUI();
        initTouchEventHandling();
        logLifecycleState("rebindToCurrentGame complete");
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        LimeLog.info("ExternalDisplayControlActivity.onConfigurationChanged: orientation=" +
                newConfig.orientation + " hasGame=" + (Game.instance != null) +
                " subElements=" + getSubVirtualControllerElementCount());
        if (Game.instance != null) {
            Game.instance.onConfigurationChanged(newConfig);
        }
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (Game.instance != null) {
            requestFocusToGameActivity(false);
            return Game.instance.onGenericMotionEvent(event);
        }
        return false;
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (Game.instance != null) {
            if (keyEvent.getDeviceId() >= 0) {
                requestFocusToGameActivity(false);
            }
            switch (keyEvent.getAction()) {
                case KeyEvent.ACTION_DOWN:
                    return Game.instance.handleKeyDown(keyEvent);
                case KeyEvent.ACTION_UP:
                    return Game.instance.handleKeyUp(keyEvent);
                case KeyEvent.ACTION_MULTIPLE:
                    return Game.instance.handleKeyMultiple(keyEvent);
                default:
                    return false;
            }
        }

        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Game.instance != null) {
            if (event.getDeviceId() >= 0) {
                requestFocusToGameActivity(false);
            }
            return Game.instance.onKeyDown(keyCode, event);
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Game.instance != null) {
            if (event.getDeviceId() >= 0) {
                requestFocusToGameActivity(false);
            }
            return Game.instance.onKeyUp(keyCode, event);
        }
        return false;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        if (Game.instance != null) {
            if (event.getDeviceId() >= 0) {
                requestFocusToGameActivity(false);
            }
            return Game.instance.onKeyMultiple(keyCode, repeatCount, event);
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createProgrammaticUI() {
        logLifecycleState("createProgrammaticUI begin");
        rootLayout = new ExternalControllerView(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.setMotionEventSplittingEnabled(true);
        rootLayout.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rootLayout.setFocusedByDefault(true);
        }

        rootLayout.setInputCallbacks(Game.instance);
        rootLayout.setCommitTextEnabled(prefConfig.enableCommitText);

        setContentView(rootLayout);

        // Top-left buttons
        LinearLayout topLeftButtons = createButtonContainer(Gravity.TOP | Gravity.START);
        topLeftButtons.setFocusable(false);
//        topLeftButtons.addView(createImageButton(R.drawable.ic_focus_secondary, v -> requestFocusToGameActivity(false)));
        zoomButton = createImageButton(R.drawable.ic_zoom_toggle, v -> toggleZoomMode(true));
        if (Game.instance != null && Game.instance.isZoomModeEnabled()) {
            zoomButton.setAlpha(1.0f);
        } else {
            zoomButton.setAlpha(0.5f);
        }
        topLeftButtons.addView(zoomButton);
        rootLayout.addView(topLeftButtons);

        // Top-center buttons
//        LinearLayout topCenterButtons = createButtonContainer(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
//        topCenterButtons.setFocusable(false);
//        rootLayout.addView(topCenterButtons);

        // Top-right buttons
        LinearLayout topRightButtons = createButtonContainer(Gravity.TOP | Gravity.END);
        topRightButtons.setFocusable(false);
        topRightButtons.addView(createImageButton(R.drawable.ic_menu_external, v -> showGameMenu()));
        topRightButtons.addView(createImageButton(R.drawable.ic_close_external, v -> finish()));
        rootLayout.addView(topRightButtons);

        // Bottom-left button: Android keyboard toggle
        LinearLayout bottomLeftButton = createButtonContainer(Gravity.BOTTOM | Gravity.START);
        bottomLeftButton.setFocusable(false);
        bottomLeftButton.addView(createImageButton(R.drawable.ic_android_keyboard, v -> _toggleKeyboard()));
        rootLayout.addView(bottomLeftButton);

        // Bottom-center buttons
//        LinearLayout bottomCenterButtons = createButtonContainer(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
//        bottomCenterButtons.setFocusable(false);
//        rootLayout.addView(bottomCenterButtons);

        // Bottom-right button: Custom keyboard toggle
        LinearLayout bottomRightButton = createButtonContainer(Gravity.BOTTOM | Gravity.END);
        bottomRightButton.setFocusable(false);
        bottomRightButton.addView(createImageButton(R.drawable.ic_fullscreen_keyboard, v -> _toggleFullKeyboard()));
        rootLayout.addView(bottomRightButton);

        if (prefConfig.dualScreenVirtualGamepad) {
            initVirtualController();
        }
        logLifecycleState("createProgrammaticUI complete");
    }

    /**
     * Toggles the visibility of the on-screen software keyboard.
     */
    private void _toggleKeyboard() {
        LimeLog.info("Toggling keyboard overlay on ExternalDisplayControlActivity");
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(0, 0);
    }

    private void initFullKeyboard(PreferenceConfiguration prefConfig) {
        keyBoardLayoutController = new KeyBoardLayoutController(rootLayout, this, prefConfig);
        keyBoardLayoutController.setViewCallbacks(this);
        keyBoardLayoutController.refreshLayout();
        keyBoardLayoutController.show();
    }

    private void initVirtualController() {
        if (Game.instance == null) {
            LimeLog.warning("ExternalDisplayControlActivity.initVirtualController: skipped because Game.instance is null");
            return;
        }

        LimeLog.info("ExternalDisplayControlActivity.initVirtualController: creating sub controller root=" +
                (rootLayout != null ? rootLayout.getWidth() + "x" + rootLayout.getHeight() : "null"));
        virtualController = new VirtualController(Game.instance.getControllerHandler(),
                rootLayout, null, this, VirtualController.DISPLAY_TARGET_SUB, false,
                Game.instance.getVirtualControllerInputStateSink());
        virtualController.refreshLayout();
        virtualController.show();
        LimeLog.info("ExternalDisplayControlActivity.initVirtualController: sub controller shown elements=" +
                virtualController.getElements().size());
        if (rootLayout != null) {
            rootLayout.post(() -> LimeLog.info(
                    "ExternalDisplayControlActivity.initVirtualController post-layout: root=" +
                            rootLayout.getWidth() + "x" + rootLayout.getHeight() +
                            " subElements=" + getSubVirtualControllerElementCount()));
        }
    }

    /**
     * Toggles the visibility of the full screen keyboard
     */
    private void _toggleFullKeyboard() {
        if (keyBoardLayoutController == null) {
            initFullKeyboard(prefConfig);
            return;
        }
        keyBoardLayoutController.toggleVisibility();
    }

    public void toggleZoomMode(boolean callGame) {
        if (Game.instance != null) {
            if (callGame) {
                Game.instance.toggleZoomMode();
            } else {
                if (Game.instance.isZoomModeEnabled()) {
                    zoomButton.setAlpha(1.0f);
                } else {
                    zoomButton.setAlpha(0.5f);
                }
            }
        }
    }

    // --- Public methods to interact with the GameMenu instance ---

    public void showGameMenu() {
        if (gameMenu != null) {
            gameMenu.showMenu(null);
        }
    }

    // --- UI Factory Methods ---

    private LinearLayout createButtonContainer(int gravity) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(gravity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        layout.setLayoutParams(params);
        return layout;
    }

    private ImageButton createImageButton(int imageResourceId, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(imageResourceId);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(56), dpToPx(56)));
        return button;
    }

    // --- Notification Management ---

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        showStickyNotification();
    }

    private void showStickyNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }

        Intent broadcastIntent = new Intent(this, StartExternalDisplayControlReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, broadcastIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.app_icon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true);

        Notification notification = notificationBuilder.build();

        notificationManager.notify(SECONDARY_SCREEN_NOTIFICATION_ID, notification);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showStickyNotification();
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- Utility Methods ---

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
