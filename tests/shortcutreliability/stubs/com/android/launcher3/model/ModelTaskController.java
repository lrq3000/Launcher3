/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.content.Context;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.*;
import java.util.*;
import java.util.function.Predicate;
public class ModelTaskController {
    private final Context context;
    private final LauncherModel model;
    private final BgDataModel data;
    public final IconCache icons = new IconCache();
    public final List<ItemInfo> bound = new ArrayList<>();
    public ModelTaskController(Context context, LauncherModel model, BgDataModel data) {
        this.context = context; this.model = model; this.data = data;
    }
    public Context getContext() { return context; }
    public LauncherModel getModel() { return model; }
    public IconCache getIconCache() { return icons; }
    public Writer getModelWriter() { return new Writer(); }
    public void bindApplicationsIfNeeded() {}
    public void bindUpdatedWidgets(BgDataModel model) {}
    public void bindUpdatedWorkspaceItems(Collection<? extends ItemInfo> items) { bound.addAll(items); }
    public void deleteAndBindComponentsRemoved(Predicate<ItemInfo> matcher, String reason) { data.itemsIdMap.removeIf(matcher); }
    public static class Writer { public void updateItemInDatabase(ItemInfo item) {} }
}
