/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.content.*;
import android.os.UserHandle;
import com.android.launcher3.model.data.ItemInfo;
public class SerializedItemItem extends ItemInfo {
    private final Intent intent;
    public SerializedItemItem(int type, UserHandle user, Intent intent) { itemType = type; this.user = user; this.intent = intent; }
    public Intent getIntent() { return intent; }
    public String getTargetPackage() { return intent.getComponent().getPackageName(); }
}
