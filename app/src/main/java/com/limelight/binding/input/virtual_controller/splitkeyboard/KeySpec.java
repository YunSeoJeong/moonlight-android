package com.limelight.binding.input.virtual_controller.splitkeyboard;

public final class KeySpec {
    public final LogicalKey logicalKey;
    public final String primaryLabel;
    public final String secondaryLabel;
    public final String tertiaryLabel;
    public final float widthWeight;
    public final boolean repeatable;
    public final ModifierType modifierType;
    public final LogicalKey fnMappedKey;
    public final String accessibilityLabel;
    public final boolean hangulLabels;

    public KeySpec(LogicalKey logicalKey,
                   String primaryLabel,
                   String secondaryLabel,
                   String tertiaryLabel,
                   float widthWeight,
                   boolean repeatable,
                   ModifierType modifierType,
                   LogicalKey fnMappedKey,
                   String accessibilityLabel,
                   boolean hangulLabels) {
        this.logicalKey = logicalKey;
        this.primaryLabel = primaryLabel;
        this.secondaryLabel = secondaryLabel;
        this.tertiaryLabel = tertiaryLabel;
        this.widthWeight = widthWeight;
        this.repeatable = repeatable;
        this.modifierType = modifierType;
        this.fnMappedKey = fnMappedKey;
        this.accessibilityLabel = accessibilityLabel;
        this.hangulLabels = hangulLabels;
    }

    public LogicalKey effectiveKey(boolean fnActive) {
        return fnActive && fnMappedKey != null ? fnMappedKey : logicalKey;
    }
}
