/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
public class ItemInstallQueue {
    public static int resumes;
    public static final int FLAG_LOADER_RUNNING = 2;
    public static final Owner INSTANCE = new Owner();
    public void resumeModelPush(int flag) { resumes++; }
    public static class Owner { public ItemInstallQueue get(Object c) { return new ItemInstallQueue(); } }
}
