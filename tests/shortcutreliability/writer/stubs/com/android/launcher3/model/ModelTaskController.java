/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.content.Context;
import com.android.launcher3.model.data.ItemInfo;
import java.util.*;
public class ModelTaskController {
    private final Context context;
    private final IModelWriter writer;
    public final List<ItemInfo> bound = new ArrayList<>();
    public ModelTaskController(Context context, IModelWriter writer) { this.context = context; this.writer = writer; }
    public Context getContext() { return context; }
    public IModelWriter getModelWriter() { return writer; }
    public void scheduleCallbackTask(java.util.function.Consumer<Callback> callback) { callback.accept(bound::addAll); }
    public interface Callback { void bindItemsAdded(List<ItemInfo> items); }
}
