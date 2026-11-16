/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class LooperExecutor implements java.util.concurrent.Executor {
    private final java.util.Deque<Runnable> tasks = new java.util.ArrayDeque<>();
    public void execute(Runnable r) { tasks.add(r); }
    public void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
}
