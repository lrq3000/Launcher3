/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
import android.content.*;
import android.content.pm.LauncherActivityInfo;
public class AppInfo {
    public static Intent makeLaunchIntent(LauncherActivityInfo activity) {
        return new Intent().setComponent(activity.getComponentName());
    }
    public static void updateRuntimeFlagsForActivityTarget(WorkspaceItemInfo item,
            LauncherActivityInfo activity, Object user, Object api, Object helper, Object automation) {
        item.runtimeStatusFlags &= ~ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
    }
}
