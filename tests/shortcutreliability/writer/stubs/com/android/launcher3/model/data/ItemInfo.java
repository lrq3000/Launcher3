/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model.data;
import android.os.UserHandle;
import android.content.*;
import com.android.launcher3.util.ContentWriter;
public class ItemInfo {
    public static final int NO_ID = -1;
    public int id = NO_ID, itemType, cellX, cellY, rank, container, screenId, spanX = 1, spanY = 1;
    public UserHandle user;
    public CharSequence title = "Saved";
    public void onAddToDatabase(ContentWriter writer) { writer.put("itemType", itemType); }
    public Intent getIntent() { return new Intent(); }
    public String getTargetPackage() { return null; }
}
