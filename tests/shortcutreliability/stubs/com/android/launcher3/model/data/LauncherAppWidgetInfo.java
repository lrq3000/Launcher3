/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
import android.content.ComponentName;
public class LauncherAppWidgetInfo extends ItemInfo {
    public static final int FLAG_PROVIDER_NOT_READY = 1, FLAG_RESTORE_STARTED = 2, FLAG_UI_NOT_READY = 4;
    public int restoreStatus, installProgress;
    public ComponentName providerName;
    public boolean hasRestoreFlag(int flag) { return (restoreStatus & flag) != 0; }
}
