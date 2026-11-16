/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import com.android.launcher3.model.data.*;
import android.os.UserHandle;
import java.util.*;
import java.util.function.Predicate;
public class BgDataModel {
    public final List<ItemInfo> itemsIdMap = new ArrayList<>();
    public final WidgetsModel widgetsModel = new WidgetsModel();
    public List<ItemInfo> updateAndCollectWorkspaceItemInfos(UserHandle user, Predicate<WorkspaceItemInfo> apps) {
        return updateAndCollectWorkspaceItemInfos(user, apps, widget -> false);
    }
    public List<ItemInfo> updateAndCollectWorkspaceItemInfos(UserHandle user,
            Predicate<WorkspaceItemInfo> apps, Predicate<LauncherAppWidgetInfo> widgets) {
        List<ItemInfo> updated = new ArrayList<>();
        for (ItemInfo item : new ArrayList<>(itemsIdMap)) {
            if (item.user == user && item instanceof WorkspaceItemInfo && apps.test((WorkspaceItemInfo) item)) updated.add(item);
        }
        return updated;
    }
    public static class WidgetsModel { public void update(Object key) {} }
}
