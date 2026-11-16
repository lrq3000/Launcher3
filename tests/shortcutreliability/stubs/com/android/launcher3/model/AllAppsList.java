/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;
import java.util.*;
public class AllAppsList {
    public List<LauncherActivityInfo> activities = new ArrayList<>();
    public List<LauncherActivityInfo> updatePackage(Context context, String name, UserHandle user) { return activities; }
    public void removePackage(String name, UserHandle user) {}
    public void updateRuntimeFlags(Object matcher, Object op) {}
}
