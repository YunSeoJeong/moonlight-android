package com.limelight.binding.input.virtual_controller.splitkeyboard;

import java.util.Collections;
import java.util.List;

public final class KeyboardRowSpec {
    public final float heightWeight;
    public final List<KeySpec> leftKeys;
    public final List<KeySpec> rightKeys;
    public final List<KeySpec> navigationKeys;
    public final int navigationSlotCount;
    public final KeySpec centerKey;

    public KeyboardRowSpec(float heightWeight,
                           List<KeySpec> leftKeys,
                           List<KeySpec> rightKeys,
                           List<KeySpec> navigationKeys) {
        this(heightWeight, leftKeys, rightKeys, navigationKeys,
                navigationKeys == null ? 0 : navigationKeys.size(), null);
    }

    public KeyboardRowSpec(float heightWeight,
                           List<KeySpec> leftKeys,
                           List<KeySpec> rightKeys,
                           List<KeySpec> navigationKeys,
                           int navigationSlotCount) {
        this(heightWeight, leftKeys, rightKeys, navigationKeys,
                navigationSlotCount, null);
    }

    public KeyboardRowSpec(float heightWeight,
                           List<KeySpec> leftKeys,
                           List<KeySpec> rightKeys,
                           List<KeySpec> navigationKeys,
                           int navigationSlotCount,
                           KeySpec centerKey) {
        this.heightWeight = heightWeight;
        this.leftKeys = Collections.unmodifiableList(leftKeys);
        this.rightKeys = Collections.unmodifiableList(rightKeys);
        this.navigationKeys = navigationKeys == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(navigationKeys);
        this.navigationSlotCount = navigationSlotCount;
        this.centerKey = centerKey;
    }
}
