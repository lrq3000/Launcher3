/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import com.android.launcher3.model.data.*;
import java.util.*;
public class BgDataModel {
    public WorkspaceData itemsIdMap;
    public int lastBindId, lastLoadId;
    public final List<FixedContainerItems> extraItems = new ArrayList<>();
    public final Widgets widgetsModel = new Widgets();
    public BgDataModel(WorkspaceData items) { itemsIdMap = items; }
    public StringCache getStringCache() { return new StringCache(); }
    public void notifyWidgetsUpdate(Object widgets) {}
    public static class Widgets { public Object getWidgetsByPackageItemForPicker() { return null; } }
    public static class FixedContainerItems {}
    public interface Callbacks {
        void bindCompleteModelAsync(WorkspaceData data, boolean sync);
        default void bindStringCache(StringCache c) {}
        default void bindExtraContainerItems(FixedContainerItems c) {}
        default void bindAllApplications(Object a,int f,Object map) {}
        default void bindAllWidgets(Object widgets) {}
    }
}
