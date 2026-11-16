/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
public class LauncherAppWidgetInfo extends ItemInfo {
    public static final int FLAG_PROVIDER_NOT_READY = 1, FLAG_RESTORE_STARTED = 2, FLAG_UI_NOT_READY = 4;
    public android.content.ComponentName providerName;
    public int restoreStatus, installProgress, appWidgetId;
    public boolean isCustomWidget() { return false; }
    public boolean isWidgetIdAllocated() { return false; }
    public boolean hasRestoreFlag(int flag) { return (restoreStatus & flag) != 0; }
}
