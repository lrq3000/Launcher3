/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class Executors {
    public static final Worker MODEL_EXECUTOR = new Worker(false), MAIN_EXECUTOR = new Worker(true);
    public static class Worker implements java.util.concurrent.Executor {
        private final java.util.Deque<Runnable> tasks = new java.util.ArrayDeque<>();
        private final boolean defer;
        Worker(boolean defer) { this.defer = defer; }
        public void execute(Runnable task) { if (defer) tasks.add(task); else task.run(); }
        public void post(Runnable task) { tasks.add(task); }
        public void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }
}
