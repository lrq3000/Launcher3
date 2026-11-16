/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.icons;
import android.os.UserHandle;
import com.android.launcher3.model.data.WorkspaceItemInfo;
public class IconCache {
    public int purged;
    public void updateSessionCache(Object key, Object info) {}
    public void removeIconsForPkg(String name, UserHandle user) { purged++; }
    public void updateIconsForPkg(String name, UserHandle user) {}
    public boolean isDefaultIcon(Object bitmap, UserHandle user) { return bitmap == null; }
    public void getTitleAndIcon(WorkspaceItemInfo item, Object flag) { item.title = "Resolved label"; }
}
