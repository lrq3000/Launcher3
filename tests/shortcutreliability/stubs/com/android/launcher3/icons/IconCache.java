/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.icons;
import android.os.UserHandle;
import com.android.launcher3.model.data.WorkspaceItemInfo;
public class IconCache {
    public final java.util.Map<String, Object> durable;
    public java.util.List<android.content.pm.LauncherActivityInfo> registered = java.util.List.of();
    public IconCache() { this(new java.util.HashMap<>()); }
    public IconCache(java.util.Map<String, Object> durable) { this.durable = durable; }
    public int purged;
    public void updateSessionCache(Object key, Object info) {}
    public void removeIconsForPkg(String name, UserHandle user) {
        purged++;
        durable.keySet().removeIf(key -> key.startsWith(name + "/"));
    }
    public void updateIconsForPkg(String name, UserHandle user) {
        removeIconsForPkg(name, user);
        registered.forEach(activity -> durable.put(activity.getComponentName().flattenToString(), new Object()));
    }
    public boolean isDefaultIcon(Object bitmap, UserHandle user) { return bitmap == null; }
    public void getTitleAndIcon(WorkspaceItemInfo item, Object flag) { item.title = "Resolved label"; }
}
