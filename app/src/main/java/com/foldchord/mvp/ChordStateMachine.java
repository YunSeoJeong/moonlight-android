package com.foldchord.mvp;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Applies Shift, text-layer, system-layer and edit-command state to chord events. */
public final class ChordStateMachine {
    static final long EDIT_MAX_ARRIVAL_MS = 45L;
    static final long SPACE_STABLE_MS = 35L;
    static final long BACKSPACE_STABLE_MS = 60L;
    static final long ENTER_STABLE_MS = 80L;
    static final long DOUBLE_TAP_MS = 350L;
    static final long ONE_SHOT_TIMEOUT_MS = 3_000L;
    static final long REPEAT_DELAY_MS = 500L;
    static final long REPEAT_INTERVAL_MS = 80L;

    private final Map<Modifier, ModifierState> modifierStates =
            new EnumMap<>(Modifier.class);
    private final Map<Modifier, Long> modifierChangedAt =
            new EnumMap<>(Modifier.class);

    private ChordMapper.Language language = ChordMapper.Language.ENGLISH;
    private ChordMapper.Layer layer = ChordMapper.Layer.BASE;

    public ChordStateMachine() {
        resetAll();
    }

    public ChordMapper.Result process(ChordInputCoordinator.ChordEvent event) {
        expireOneShots(event.endedAtMs);
        int mask = event.mask;

        if (event.thumbTap) {
            if (mask == ChordMapper.TL) {
                return applyModifierTap(Modifier.SHIFT, event.endedAtMs, "Shift");
            }
            return ChordMapper.Result.none("TR 제어키");
        }

        int fingerMask = mask & ChordMapper.FINGER_MASK;
        int thumbs = mask & ChordMapper.THUMB_MASK;
        if (event.systemLayer) {
            return processSystemCode(fingerMask, event);
        }
        if (thumbs == ChordMapper.THUMB_MASK) {
            return ChordMapper.Result.unknown("SYS 진입 유지 시간이 부족합니다");
        }
        if ((thumbs & ChordMapper.TL) != 0) {
            return ChordMapper.Result.unknown("TL은 탭한 뒤 문자를 입력하세요");
        }

        ChordMapper.Result editResult = resolveDirectEdit(event, thumbs, fingerMask);
        if (editResult != null) {
            return editResult;
        }

        boolean shortcut = isShortcutModifierActive();
        ChordMapper.Language mappedLanguage = shortcut
                ? ChordMapper.Language.ENGLISH : language;
        boolean shifted = isModifierActive(Modifier.SHIFT);
        ChordMapper.Layer mappedLayer = shortcut ? ChordMapper.Layer.BASE : layer;
        ChordMapper.Result result = ChordMapper.resolve(mask, mappedLanguage,
                shifted, mappedLayer);

        if (result.action == ChordMapper.Action.INSERT
                || result.action == ChordMapper.Action.COMPOSE_HANGUL) {
            int modifiers = activeModifierMask();
            consumeOneShotModifiers();
            if (shortcut && isEnglishShortcutCharacter(result.text)) {
                String keyName = result.text.substring(0, 1).toUpperCase(Locale.ROOT);
                return ChordMapper.Result.key(keyName, modifiers, 1,
                        describeModifiers(modifiers) + keyName);
            }
        }
        return result;
    }

    public ChordMapper.Language getLanguage() {
        return language;
    }

    public ChordMapper.Layer getLayer() {
        return layer;
    }

    public void toggleLanguage() {
        language = language == ChordMapper.Language.ENGLISH
                ? ChordMapper.Language.KOREAN
                : ChordMapper.Language.ENGLISH;
    }

    public boolean expireOneShots(long nowMs) {
        boolean changed = false;
        for (Modifier modifier : Modifier.values()) {
            if (modifierStates.get(modifier) == ModifierState.ONE_SHOT
                    && nowMs - modifierChangedAt.get(modifier) >= ONE_SHOT_TIMEOUT_MS) {
                modifierStates.put(modifier, ModifierState.OFF);
                changed = true;
            }
        }
        return changed;
    }

    public long getNextExpiryAtMs() {
        long earliest = Long.MAX_VALUE;
        for (Modifier modifier : Modifier.values()) {
            if (modifierStates.get(modifier) == ModifierState.ONE_SHOT) {
                earliest = Math.min(earliest,
                        modifierChangedAt.get(modifier) + ONE_SHOT_TIMEOUT_MS);
            }
        }
        return earliest;
    }

    public void resetAll() {
        layer = ChordMapper.Layer.BASE;
        for (Modifier modifier : Modifier.values()) {
            modifierStates.put(modifier, ModifierState.OFF);
            modifierChangedAt.put(modifier, Long.MIN_VALUE / 2);
        }
    }

    public String statusLabel() {
        StringBuilder label = new StringBuilder(
                language == ChordMapper.Language.KOREAN ? "한" : "EN");
        if (layer == ChordMapper.Layer.NUMBER) {
            label.append(" · NUM");
        } else if (layer == ChordMapper.Layer.SYMBOL) {
            label.append(" · SYM");
        }
        appendModifierStatus(label, Modifier.SHIFT, "⇧");
        appendModifierStatus(label, Modifier.CTRL, "CTRL");
        appendModifierStatus(label, Modifier.ALT, "ALT");
        appendModifierStatus(label, Modifier.META, "META");
        appendModifierStatus(label, Modifier.ALT_GR, "ALTGR");
        return label.toString();
    }

    ModifierState getModifierState(Modifier modifier) {
        return modifierStates.get(modifier);
    }

    private ChordMapper.Result resolveDirectEdit(ChordInputCoordinator.ChordEvent event,
                                                  int thumbs,
                                                  int fingerMask) {
        boolean isEditMask = fingerMask == ChordMapper.BACKSPACE_MASK
                || fingerMask == ChordMapper.SPACE_MASK
                || fingerMask == ChordMapper.ENTER_MASK;
        if (!isEditMask) {
            return null;
        }
        if (thumbs != 0) {
            return ChordMapper.Result.unknown("TR 보조 레이어에서 편집 코드는 사용할 수 없습니다");
        }
        long minimumStable = fingerMask == ChordMapper.SPACE_MASK
                ? SPACE_STABLE_MS
                : fingerMask == ChordMapper.BACKSPACE_MASK
                ? BACKSPACE_STABLE_MS : ENTER_STABLE_MS;
        if (!event.cleanStart
                || event.arrivalSpanMs > EDIT_MAX_ARRIVAL_MS
                || event.stableDurationMs < minimumStable) {
            return ChordMapper.Result.unknown("불안정한 편집 코드가 취소되었습니다");
        }
        ChordMapper.Result result = ChordMapper.resolve(fingerMask,
                language, false, layer);
        if (result.action == ChordMapper.Action.BACKSPACE) {
            return result.withRepeatCount(repeatCount(event.stableDurationMs));
        }
        return result;
    }

    private ChordMapper.Result processSystemCode(int fingerMask,
                                                  ChordInputCoordinator.ChordEvent event) {
        if (fingerMask == ChordMapper.BACKSPACE_MASK) {
            layer = layer == ChordMapper.Layer.NUMBER
                    ? ChordMapper.Layer.BASE : ChordMapper.Layer.NUMBER;
            return ChordMapper.Result.state(layer == ChordMapper.Layer.NUMBER
                    ? "숫자 레이어" : "기본 레이어");
        }
        if (fingerMask == ChordMapper.SPACE_MASK) {
            layer = layer == ChordMapper.Layer.SYMBOL
                    ? ChordMapper.Layer.BASE : ChordMapper.Layer.SYMBOL;
            return ChordMapper.Result.state(layer == ChordMapper.Layer.SYMBOL
                    ? "기호 레이어" : "기본 레이어");
        }
        if (fingerMask == ChordMapper.ENTER_MASK) {
            toggleLanguage();
            return ChordMapper.Result.action(ChordMapper.Action.TOGGLE_LANGUAGE, "한·영 전환");
        }
        if (fingerMask == ChordMapper.FINGER_MASK) {
            resetAll();
            return ChordMapper.Result.state("모든 수정키와 레이어 초기화");
        }

        Modifier modifier = modifierForSystemCode(fingerMask);
        if (modifier != null) {
            return applyModifierTap(modifier, event.endedAtMs, modifierLabel(modifier));
        }

        String keyName = systemKeyName(fingerMask);
        if (keyName == null) {
            return ChordMapper.Result.unknown("미등록 시스템 코드");
        }
        int modifiers = activeModifierMask();
        int repeatCount = isRepeatableSystemKey(keyName)
                ? repeatCount(event.stableDurationMs) : 1;
        consumeOneShotModifiers();
        return ChordMapper.Result.key(keyName, modifiers, repeatCount,
                describeModifiers(modifiers) + systemKeyLabel(keyName));
    }

    private ChordMapper.Result applyModifierTap(Modifier modifier, long nowMs, String label) {
        ModifierState current = modifierStates.get(modifier);
        long previousChangedAt = modifierChangedAt.get(modifier);
        ModifierState next;
        if (current == ModifierState.LOCKED) {
            next = ModifierState.OFF;
        } else if (current == ModifierState.ONE_SHOT
                && nowMs - previousChangedAt <= DOUBLE_TAP_MS) {
            next = ModifierState.LOCKED;
        } else {
            next = ModifierState.ONE_SHOT;
        }
        modifierStates.put(modifier, next);
        modifierChangedAt.put(modifier, nowMs);
        String suffix = next == ModifierState.LOCKED
                ? " Lock" : next == ModifierState.ONE_SHOT ? " One-shot" : " 해제";
        return ChordMapper.Result.state(label + suffix);
    }

    private boolean isModifierActive(Modifier modifier) {
        return modifierStates.get(modifier) != ModifierState.OFF;
    }

    private boolean isShortcutModifierActive() {
        return isModifierActive(Modifier.CTRL)
                || isModifierActive(Modifier.ALT)
                || isModifierActive(Modifier.META)
                || isModifierActive(Modifier.ALT_GR);
    }

    private int activeModifierMask() {
        int mask = 0;
        if (isModifierActive(Modifier.SHIFT)) {
            mask |= ChordMapper.MODIFIER_SHIFT;
        }
        if (isModifierActive(Modifier.CTRL)) {
            mask |= ChordMapper.MODIFIER_CTRL;
        }
        if (isModifierActive(Modifier.ALT)) {
            mask |= ChordMapper.MODIFIER_ALT;
        }
        if (isModifierActive(Modifier.META)) {
            mask |= ChordMapper.MODIFIER_META;
        }
        if (isModifierActive(Modifier.ALT_GR)) {
            mask |= ChordMapper.MODIFIER_ALT_GR;
        }
        return mask;
    }

    private void consumeOneShotModifiers() {
        for (Modifier modifier : Modifier.values()) {
            if (modifierStates.get(modifier) == ModifierState.ONE_SHOT) {
                modifierStates.put(modifier, ModifierState.OFF);
            }
        }
    }

    private void appendModifierStatus(StringBuilder label,
                                      Modifier modifier, String name) {
        ModifierState state = modifierStates.get(modifier);
        if (state == ModifierState.OFF) {
            return;
        }
        label.append(" · ").append(name);
        if (state == ModifierState.LOCKED) {
            label.append("LOCK");
        }
    }

    private static Modifier modifierForSystemCode(int fingerMask) {
        if (fingerMask == (ChordMapper.LI | ChordMapper.RI)) {
            return Modifier.SHIFT;
        }
        if (fingerMask == (ChordMapper.LM | ChordMapper.RM)) {
            return Modifier.CTRL;
        }
        if (fingerMask == (ChordMapper.LM | ChordMapper.RI)) {
            return Modifier.ALT;
        }
        if (fingerMask == (ChordMapper.LR | ChordMapper.RR)) {
            return Modifier.META;
        }
        if (fingerMask == (ChordMapper.LI | ChordMapper.RM)) {
            return Modifier.ALT_GR;
        }
        return null;
    }

    private static String systemKeyName(int fingerMask) {
        if (fingerMask == ChordMapper.LI) {
            return "DPAD_UP";
        }
        if (fingerMask == ChordMapper.LM) {
            return "DPAD_LEFT";
        }
        if (fingerMask == ChordMapper.LR) {
            return "DPAD_DOWN";
        }
        if (fingerMask == ChordMapper.RM) {
            return "DPAD_RIGHT";
        }
        if (fingerMask == ChordMapper.RI) {
            return "PAGE_UP";
        }
        if (fingerMask == ChordMapper.RR) {
            return "PAGE_DOWN";
        }
        if (fingerMask == (ChordMapper.LI | ChordMapper.LM)) {
            return "MOVE_HOME";
        }
        if (fingerMask == (ChordMapper.LM | ChordMapper.LR)) {
            return "MOVE_END";
        }
        if (fingerMask == (ChordMapper.RI | ChordMapper.RM)) {
            return "TAB";
        }
        if (fingerMask == (ChordMapper.RM | ChordMapper.RR)) {
            return "FORWARD_DEL";
        }
        if (fingerMask == (ChordMapper.LI | ChordMapper.RR)) {
            return "ESCAPE";
        }
        if (fingerMask == (ChordMapper.LR | ChordMapper.RI)) {
            return "MENU";
        }
        return null;
    }

    private static int repeatCount(long stableDurationMs) {
        if (stableDurationMs < REPEAT_DELAY_MS) {
            return 1;
        }
        return 2 + (int) ((stableDurationMs - REPEAT_DELAY_MS) / REPEAT_INTERVAL_MS);
    }

    private static boolean isRepeatableSystemKey(String keyName) {
        return keyName.startsWith("DPAD_")
                || "PAGE_UP".equals(keyName)
                || "PAGE_DOWN".equals(keyName);
    }

    private static boolean isEnglishShortcutCharacter(String text) {
        return text != null && text.length() == 1
                && Character.toLowerCase(text.charAt(0)) >= 'a'
                && Character.toLowerCase(text.charAt(0)) <= 'z';
    }

    private static String describeModifiers(int modifiers) {
        StringBuilder label = new StringBuilder();
        if ((modifiers & ChordMapper.MODIFIER_CTRL) != 0) {
            label.append("Ctrl+");
        }
        if ((modifiers & ChordMapper.MODIFIER_ALT) != 0) {
            label.append("Alt+");
        }
        if ((modifiers & ChordMapper.MODIFIER_META) != 0) {
            label.append("Meta+");
        }
        if ((modifiers & ChordMapper.MODIFIER_ALT_GR) != 0) {
            label.append("AltGr+");
        }
        if ((modifiers & ChordMapper.MODIFIER_SHIFT) != 0) {
            label.append("Shift+");
        }
        return label.toString();
    }

    private static String modifierLabel(Modifier modifier) {
        switch (modifier) {
            case SHIFT:
                return "Shift";
            case CTRL:
                return "Ctrl";
            case ALT:
                return "Alt";
            case META:
                return "Win·Meta";
            case ALT_GR:
                return "AltGr";
            default:
                throw new AssertionError(modifier);
        }
    }

    private static String systemKeyLabel(String keyName) {
        switch (keyName) {
            case "DPAD_UP":
                return "위쪽 화살표";
            case "DPAD_LEFT":
                return "왼쪽 화살표";
            case "DPAD_DOWN":
                return "아래쪽 화살표";
            case "DPAD_RIGHT":
                return "오른쪽 화살표";
            case "PAGE_UP":
                return "Page Up";
            case "PAGE_DOWN":
                return "Page Down";
            case "MOVE_HOME":
                return "Home";
            case "MOVE_END":
                return "End";
            case "TAB":
                return "Tab";
            case "FORWARD_DEL":
                return "Delete";
            case "ESCAPE":
                return "Escape";
            case "MENU":
                return "Context Menu";
            default:
                return keyName;
        }
    }

    enum Modifier {
        SHIFT,
        CTRL,
        ALT,
        META,
        ALT_GR
    }

    enum ModifierState {
        OFF,
        ONE_SHOT,
        LOCKED
    }
}
