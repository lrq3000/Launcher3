/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
public class ItemInfoWithIcon extends ItemInfo {
    public static final int FLAG_DISABLED_NOT_AVAILABLE = 1, FLAG_DISABLED_SUSPENDED = 2,
            FLAG_DISABLED_QUIET_USER = 4, FLAG_DISABLED_MASK = 255;
    public int runtimeStatusFlags;
    public Object bitmap = new Object();
    public boolean isArchived() { return false; }
    public CharSequence title = "Saved label";
}
