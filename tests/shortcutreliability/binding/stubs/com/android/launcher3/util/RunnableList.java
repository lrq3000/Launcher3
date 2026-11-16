/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class RunnableList {
    private final java.util.List<Runnable> tasks = new java.util.ArrayList<>(); private boolean destroyed;
    public void add(Runnable r) { if (destroyed) r.run(); else tasks.add(r); }
    public void executeAllAndDestroy() { destroyed = true; java.util.List<Runnable> copy = new java.util.ArrayList<>(tasks); tasks.clear(); copy.forEach(Runnable::run); }
}
