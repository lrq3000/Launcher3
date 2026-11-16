/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.shortcuts;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
public class ShortcutRequest {
    public static final int ALL = 1;
    public ShortcutRequest(Context context, UserHandle user) {}
    public ShortcutRequest forPackage(String name, String id) { return this; }
    public java.util.List<ShortcutInfo> query(int flags) { return java.util.List.of(); }
}
