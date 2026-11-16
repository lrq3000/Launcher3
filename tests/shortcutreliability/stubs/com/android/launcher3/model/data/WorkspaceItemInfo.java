/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
import android.content.*;
import android.content.pm.ShortcutInfo;
public class WorkspaceItemInfo extends ItemInfoWithIcon {
    public static final int FLAG_SUPPORTS_WEB_UI = 1, DEFAULT = 0;
    public Intent intent;
    public int status;
    public boolean isPromise() { return status != 0; }
    public boolean hasStatusFlag(int flag) { return (status & flag) != 0; }
    public boolean isArchived() { return false; }
    public String getDeepShortcutId() { return "id"; }
    public void updateFromDeepShortcutInfo(ShortcutInfo info, Context context) {}
    public ComponentName getTargetComponent() { return intent.getComponent(); }
    public String getTargetPackage() { return getTargetComponent().getPackageName(); }
    public Object getMatchingLookupFlag() { return null; }
}
