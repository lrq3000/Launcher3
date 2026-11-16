/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.content.Context;
import com.android.launcher3.model.data.ItemInfo;
import java.util.*;
public class BgDataModel {
    public enum ModificationSource { ModelTask }
    public interface Callbacks {}
    public int lastLoadId;
    public final Items itemsIdMap = new Items();
    public void addItems(Context c, List<? extends ItemInfo> items, ModificationSource source) { items.forEach(i -> itemsIdMap.values.put(i.id, i)); }
    public void updateItems(List<? extends ItemInfo> items, ModificationSource source) {}
    public void removeItems(Context c, List<? extends ItemInfo> items, ModificationSource source) { items.forEach(i -> itemsIdMap.values.remove(i.id)); }
    public void clear() { itemsIdMap.values.clear(); }
    public static class Items implements Iterable<ItemInfo> {
        public final Map<Integer, ItemInfo> values = new LinkedHashMap<>();
        public ItemInfo get(int id) { return values.get(id); }
        public void removeIf(java.util.function.Predicate<ItemInfo> predicate) { values.values().removeIf(predicate); }
        public Iterator<ItemInfo> iterator() { return new ArrayList<>(values.values()).iterator(); }
    }
}
