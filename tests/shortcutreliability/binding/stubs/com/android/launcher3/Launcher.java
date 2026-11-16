/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3;
import java.util.*;
import com.android.launcher3.model.data.*;
import com.android.launcher3.util.RunnableList;
public class Launcher {
    public int clears, finished;
    public final List<Integer> visible = new ArrayList<>();
    public final List<RunnableList> pending = new ArrayList<>();
    public Runnable onEntry = () -> {}, onInflate = () -> {};
    public boolean isDestroyed() { onEntry.run(); return false; }
    public Inflater getItemInflater() { return new Inflater(); }
    public void bindInflatedItems(List<android.util.Pair<ItemInfo,Object>> items, Object anim) { items.forEach(i -> visible.add(i.first.id)); }
    public void bindPredictedContainerInfo(PredictedContainerInfo item) {}
    public void bindComplete(int count, boolean sync) {}
    public class Inflater { public Object inflateItem(ItemInfo item, Object ignored) { onInflate.run(); return new Object(); } }
}
