/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import com.android.launcher3.util.Singleton;
public class ItemInstallQueue {
    public static final Singleton<ItemInstallQueue> INSTANCE = new Singleton<>(new ItemInstallQueue());
    public void removeFromInstallQueue(java.util.Set<String> packages, android.os.UserHandle user) {}
}
