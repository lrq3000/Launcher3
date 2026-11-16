/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
import android.os.UserHandle;
public class ItemInfo {
    public static final int NO_ID = -1;
    public int id = NO_ID, itemType, cellX, cellY, rank;
    public UserHandle user;
}
